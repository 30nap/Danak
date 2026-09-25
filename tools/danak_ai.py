#!/usr/bin/env python3
"""
The AI provider boundary for Danak's content pipeline.

The pipeline (tools/danak_generate.py) talks to one small interface:

    provider.generate_structured(StructuredRequest) -> StructuredResult

and never to a vendor SDK directly. A provider only moves text: it sends a system prompt, a
user message and the JSON Schema the answer must follow, and returns the parsed JSON object.
Everything that decides whether an answer is acceptable lives in the pipeline, so every
provider — a hosted API, a local open-weight model, or the scripted fake the tests use — is
held to the same checks.

Providers:
    fake                a scripted provider for tests and dry runs; no network
    anthropic           Claude through the official `anthropic` SDK (pip install anthropic)
    openai-compatible   any server speaking the OpenAI chat-completions protocol: hosted APIs,
                        routers, or a local model server (llama.cpp, vLLM, Ollama)

Configuration comes from the environment only (see provider_from_env). An API key is read
when the provider is built and is never written to prompts, artifacts or logs.
"""
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_TIMEOUT_SECONDS = 180
DEFAULT_MAX_TOKENS = 16000
TRANSIENT = {"timeout", "rate-limited", "server", "network"}


class ProviderError(Exception):
    """code: timeout | rate-limited | server | network (transient, may be retried) or
    auth | bad-request | refused | truncated | malformed-output | config (not retried,
    except malformed-output, which the pipeline retries once)."""

    def __init__(self, code, message):
        super().__init__(f"{code}: {message}")
        self.code, self.message = code, message

    @property
    def transient(self):
        return self.code in TRANSIENT


class StructuredRequest:
    def __init__(self, *, stage, system, user, schema, schema_name, max_tokens=DEFAULT_MAX_TOKENS):
        self.stage, self.system, self.user = stage, system, user
        self.schema, self.schema_name, self.max_tokens = schema, schema_name, max_tokens


class StructuredResult:
    def __init__(self, data, raw, usage=None):
        self.data, self.raw, self.usage = data, raw, usage or {}


def parse_json_object(text):
    """The answer as a JSON object. Tolerates a Markdown code fence around it (some models add
    one even when told not to) but nothing else: prose around the JSON is malformed output."""
    stripped = text.strip()
    fence = re.fullmatch(r"```(?:json)?\s*(.*?)\s*```", stripped, re.DOTALL)
    if fence:
        stripped = fence.group(1)
    try:
        data = json.loads(stripped)
    except json.JSONDecodeError as e:
        raise ProviderError("malformed-output", f"not JSON ({e.msg} at {e.pos})") from None
    if not isinstance(data, dict):
        raise ProviderError("malformed-output", "the answer is not a JSON object")
    return data


class AiProvider:
    """Subclasses implement complete(); generate_structured() is the same for all."""

    name = "abstract"

    def __init__(self, model):
        self.model = model

    def describe(self):
        """What goes into generation metadata: never credentials."""
        return {"provider": self.name, "model": self.model}

    def complete(self, request):
        """Returns (answer text, usage dict)."""
        raise NotImplementedError

    def generate_structured(self, request):
        text, usage = self.complete(request)
        return StructuredResult(parse_json_object(text), text, usage)


# ------------------------------------------------------------------------------ fake

class FakeProvider(AiProvider):
    """Answers from a script: {stage: answer or [answers, one per call]}. An answer is a JSON
    object (returned as its JSON text), a raw string (returned as is, to test malformed output),
    or {"$error": code} (raised as ProviderError). Records every request it receives."""

    name = "fake"

    def __init__(self, script, model="scripted"):
        super().__init__(model)
        self.script = {stage: list(a) if isinstance(a, list) else [a] for stage, a in script.items()}
        self.requests = []

    def complete(self, request):
        self.requests.append(request)
        answers = self.script.get(request.stage)
        if not answers:
            raise ProviderError("config", f"the fake provider has no answer for stage {request.stage!r}")
        answer = answers.pop(0) if len(answers) > 1 else answers[0]
        if isinstance(answer, dict) and "$error" in answer:
            raise ProviderError(answer["$error"], "scripted failure")
        text = answer if isinstance(answer, str) else json.dumps(answer, ensure_ascii=False)
        return text, {"input_tokens": len(request.system + request.user) // 4, "output_tokens": len(text) // 4}


# ---------------------------------------------------------------- shared HTTP pieces

def endpoint_problem(url):
    """https, or plain http only to this machine (a local model server)."""
    parts = urllib.parse.urlsplit(url)
    if parts.username or parts.password:
        return "must not contain credentials"
    if parts.scheme == "https" and parts.hostname:
        return None
    if parts.scheme == "http" and parts.hostname in ("localhost", "127.0.0.1", "::1"):
        return None
    return "must be https (or http to localhost for a local model server)"


class NoRedirects(urllib.request.HTTPRedirectHandler):
    """A redirect would resend the Authorization header to wherever it points."""

    def redirect_request(self, *args, **kwargs):
        return None


def urllib_transport(url, headers, body, timeout):
    """POST body to url; returns (status, response text). Redirects are not followed."""
    opener = urllib.request.build_opener(NoRedirects)
    request = urllib.request.Request(url, data=body, headers=headers, method="POST")
    try:
        with opener.open(request, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except TimeoutError:
        raise ProviderError("timeout", f"no answer within {timeout}s") from None
    except urllib.error.URLError as e:
        if isinstance(e.reason, TimeoutError):
            raise ProviderError("timeout", f"no answer within {timeout}s") from None
        raise ProviderError("network", str(e.reason)) from None


def status_error(status, text, secret):
    detail = scrub(text[:300], secret)
    if status == 429:
        return ProviderError("rate-limited", detail)
    if status in (401, 403):
        return ProviderError("auth", f"HTTP {status}")
    if status in (408, 504):
        return ProviderError("timeout", f"HTTP {status}")
    if status >= 500:
        return ProviderError("server", f"HTTP {status}: {detail}")
    return ProviderError("bad-request", f"HTTP {status}: {detail}")


def scrub(text, secret):
    return text.replace(secret, "[redacted]") if secret else text


# --------------------------------------------------------------- openai-compatible

class OpenAICompatibleProvider(AiProvider):
    """POST {base_url}/chat/completions. json_mode: json_schema (strict structured output),
    json_object (valid JSON, schema only in the prompt) or none, for servers without either."""

    name = "openai-compatible"

    def __init__(self, model, base_url, api_key=None, json_mode="json_schema",
                 timeout=DEFAULT_TIMEOUT_SECONDS, transport=urllib_transport):
        super().__init__(model)
        problem = endpoint_problem(base_url)
        if problem:
            raise ProviderError("config", f"base URL {problem}")
        if json_mode not in ("json_schema", "json_object", "none"):
            raise ProviderError("config", "json mode must be json_schema, json_object or none")
        self.base_url = base_url.rstrip("/")
        self._key, self.json_mode, self.timeout, self.transport = api_key, json_mode, timeout, transport

    def describe(self):
        host = urllib.parse.urlsplit(self.base_url).hostname
        return {"provider": self.name, "model": self.model, "endpoint": host, "jsonMode": self.json_mode}

    def complete(self, request):
        body = {
            "model": self.model,
            "max_tokens": request.max_tokens,
            "messages": [{"role": "system", "content": request.system},
                         {"role": "user", "content": request.user}],
        }
        if self.json_mode == "json_schema":
            body["response_format"] = {"type": "json_schema", "json_schema": {
                "name": request.schema_name, "schema": request.schema, "strict": True}}
        elif self.json_mode == "json_object":
            body["response_format"] = {"type": "json_object"}
        headers = {"Content-Type": "application/json"}
        if self._key:
            headers["Authorization"] = f"Bearer {self._key}"
        status, text = self.transport(f"{self.base_url}/chat/completions", headers,
                                      json.dumps(body).encode("utf-8"), self.timeout)
        if status != 200:
            raise status_error(status, text, self._key)
        try:
            answer = json.loads(text)
            choice = answer["choices"][0]
            content = choice["message"].get("content")
        except (ValueError, KeyError, IndexError, TypeError):
            raise ProviderError("malformed-output", "the response is not a chat completion") from None
        if choice.get("finish_reason") == "length":
            raise ProviderError("truncated", "the answer hit max_tokens")
        if choice["message"].get("refusal") or not content:
            raise ProviderError("refused", "the model returned no answer")
        usage = answer.get("usage") or {}
        return content, {"input_tokens": usage.get("prompt_tokens"), "output_tokens": usage.get("completion_tokens")}


# ----------------------------------------------------------------------- anthropic

def anthropic_schema(schema):
    """Structured outputs accept a subset of JSON Schema: no length or numeric bounds. Those
    are checked again, deterministically, after the answer arrives."""
    if isinstance(schema, dict):
        if isinstance(schema.get("type"), list):  # "type": ["string", "null"] as anyOf
            rest = {k: v for k, v in schema.items() if k != "type"}
            return {"anyOf": [anthropic_schema({**rest, "type": t}) for t in schema["type"]]}
        return {k: anthropic_schema(v) for k, v in schema.items()
                if k not in ("minLength", "maxLength", "minimum", "maximum", "minItems", "maxItems", "pattern")}
    if isinstance(schema, list):
        return [anthropic_schema(v) for v in schema]
    return schema


class AnthropicProvider(AiProvider):
    """Claude through the official SDK, with structured output (output_config.format)."""

    name = "anthropic"

    def __init__(self, model, api_key=None, effort=None, timeout=DEFAULT_TIMEOUT_SECONDS, client=None):
        super().__init__(model)
        self.effort = effort
        try:
            import anthropic
            self._sdk = anthropic
        except ImportError:
            self._sdk = None
        if client is None:
            if self._sdk is None:
                raise ProviderError("config", "pip install anthropic to use this provider")
            # Retries are the pipeline's (call_with_retries), the same for every provider,
            # so the SDK's own are switched off.
            client = anthropic.Anthropic(api_key=api_key, timeout=timeout, max_retries=0)
        self.client = client

    def describe(self):
        return {"provider": self.name, "model": self.model, "effort": self.effort}

    def complete(self, request):
        params = {
            "model": self.model,
            "max_tokens": request.max_tokens,
            "system": request.system,
            "messages": [{"role": "user", "content": request.user}],
            "output_config": {"format": {"type": "json_schema", "schema": anthropic_schema(request.schema)}},
        }
        if self.effort:
            params["output_config"]["effort"] = self.effort
        anthropic = self._sdk
        if anthropic is None:  # an injected test client; there are no SDK errors to map
            response = self.client.messages.create(**params)
            return self._answer(response)
        try:
            response = self.client.messages.create(**params)
        except anthropic.APITimeoutError:
            raise ProviderError("timeout", "no answer in time") from None
        except anthropic.RateLimitError:
            raise ProviderError("rate-limited", "HTTP 429") from None
        except (anthropic.AuthenticationError, anthropic.PermissionDeniedError) as e:
            raise ProviderError("auth", f"HTTP {e.status_code}") from None
        except anthropic.APIStatusError as e:
            code = "server" if e.status_code >= 500 else "bad-request"
            raise ProviderError(code, f"HTTP {e.status_code}: {e.message[:300]}") from None
        except anthropic.APIConnectionError:
            raise ProviderError("network", "could not reach the API") from None
        return self._answer(response)

    @staticmethod
    def _answer(response):
        if response.stop_reason == "refusal":
            raise ProviderError("refused", "the model declined the request")
        if response.stop_reason == "max_tokens":
            raise ProviderError("truncated", "the answer hit max_tokens")
        text = next((b.text for b in response.content if b.type == "text"), "")
        usage = {"input_tokens": response.usage.input_tokens, "output_tokens": response.usage.output_tokens}
        return text, usage


# ------------------------------------------------------------------- configuration

ENVIRONMENT = """\
DANAK_AI_PROVIDER      fake | anthropic | openai-compatible
DANAK_AI_MODEL         the provider's model id
ANTHROPIC_API_KEY      key for provider anthropic
DANAK_AI_EFFORT        optional, anthropic only: low | medium | high | xhigh | max
DANAK_AI_BASE_URL      openai-compatible only, e.g. https://api.example.com/v1 or http://localhost:8080/v1
DANAK_AI_API_KEY       openai-compatible only; leave unset for a local server without auth
DANAK_AI_JSON_MODE     openai-compatible only: json_schema (default) | json_object | none
DANAK_AI_TIMEOUT       seconds per request (default 180)"""


def provider_from_env(prefix="DANAK_AI", env=None, fake_script=None):
    """Builds the provider the environment names. prefix lets the verifier use a different
    model: DANAK_VERIFIER_PROVIDER / _MODEL / _BASE_URL / ... fall back to DANAK_AI_*."""
    env = os.environ if env is None else env

    def get(name, default=None):
        return env.get(f"{prefix}_{name}") or env.get(f"DANAK_AI_{name}") or default

    kind = get("PROVIDER")
    timeout = int(get("TIMEOUT", DEFAULT_TIMEOUT_SECONDS))
    if kind == "fake":
        if fake_script is None:
            raise ProviderError("config", "the fake provider needs a script (--script)")
        return FakeProvider(fake_script)
    model = get("MODEL")
    if not kind or not model:
        raise ProviderError("config", f"set {prefix}_PROVIDER and {prefix}_MODEL\n{ENVIRONMENT}")
    if kind == "anthropic":
        key = env.get("ANTHROPIC_API_KEY")
        if not key:
            raise ProviderError("config", "ANTHROPIC_API_KEY is not set")
        return AnthropicProvider(model, api_key=key, effort=get("EFFORT"), timeout=timeout)
    if kind == "openai-compatible":
        base_url = get("BASE_URL")
        if not base_url:
            raise ProviderError("config", f"set {prefix}_BASE_URL")
        return OpenAICompatibleProvider(model, base_url, api_key=get("API_KEY"),
                                        json_mode=get("JSON_MODE", "json_schema"), timeout=timeout)
    raise ProviderError("config", f"unknown provider {kind!r}\n{ENVIRONMENT}")


def call_with_retries(provider, request, *, check=None, attempts=3, malformed_retries=1, sleep=time.sleep):
    """Transient failures are retried with backoff (2 s, 4 s); a malformed answer (not JSON, or
    rejected by check with ProviderError("malformed-output")) is asked for once more. Returns
    (result, attempts used); raises the last ProviderError. Anything else check raises
    propagates at once."""
    attempt, malformed, transient = 0, 0, 0
    while True:
        attempt += 1
        try:
            result = provider.generate_structured(request)
            if check:
                check(result.data)
            return result, attempt
        except ProviderError as e:
            if e.code == "malformed-output" and malformed < malformed_retries:
                malformed += 1
                continue
            if e.transient and transient < attempts - 1:
                transient += 1
                sleep(2 ** transient)
                continue
            e.attempts = attempt
            raise
