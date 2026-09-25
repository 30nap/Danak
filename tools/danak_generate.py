#!/usr/bin/env python3
"""
Danak draft generation: one approved source snapshot in, one reviewable Danak draft out.

    python3 tools/danak_generate.py run <snapshotId> [--script FILE] [--out DIR]
    python3 tools/danak_generate.py validate-drafts [DIR]
    python3 tools/danak_generate.py eval --cases evals/phase5-cases.yml --out DIR [--script-dir DIR]
    python3 tools/danak_generate.py prompts

Needs: pip install jsonschema pyyaml (plus the provider's own package, e.g. anthropic).
The provider comes from the environment: python3 tools/danak_ai.py lists the variables.

Pipeline, for one snapshot:

    snapshot  → integrity and CURRENT source policy (not the usableForDanak stored in it)
    stage A   → the model picks one idea and cites the blocks that support it, or declines
    stage B   → the model writes the Persian Danak and lists every factual claim with evidence
    checks    → deterministic: evidence exists verbatim, numbers are backed by evidence,
                no provenance, URLs or ids from the model, every text location covered
    stage C   → a separate call judges every claim against the snapshot
    checks    → deterministic Danak validation (schema, Persian rules, source metadata)
    artifact  → drafts/<snapshotId>/draft.json and review.md, for a person to decide on

The rule the checks enforce: the model may transform and explain the snapshot's evidence,
never invent it. Source URL, publisher, licence, revision, Wikidata id, concept key and
reading time never come from the model. Nothing here publishes anything: a draft becomes a
Danak only when a person copies it into content/ and it passes the usual review.
"""
import argparse
import datetime
import hashlib
import json
import math
import os
import pathlib
import re
import sys
import time
import unicodedata
import urllib.parse

import jsonschema
import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import danak_ai  # noqa: E402
import danak_content  # noqa: E402
import danak_sources  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
SNAPSHOT_DIR = ROOT / "sources" / "snapshots"
DRAFT_DIR = ROOT / "drafts"
PROMPT_DIR = ROOT / "prompts"
CONTENT_DIR = ROOT / "content"
SOURCES_FILE = CONTENT_DIR / "sources.yml"
DRAFT_SCHEMA = ROOT / "schema" / "danak-draft-v1.schema.json"

PIPELINE = "danak-generate/1"
PROMPTS = {"idea": "idea-v1", "generate": "generate-v1", "verify": "verify-v1"}
WORDS_PER_MINUTE = 200
MAX_EXCERPT = 500
MIN_EXCERPT = 8

# The model may write Persian text and nothing else. These are the fields the pipeline owns.
PROVENANCE_FIELDS = {
    "id", "source", "url", "sourceUrl", "publisher", "license", "licenseUrl", "conceptKey",
    "readingSeconds", "image", "snapshotId", "snapshotSha256", "revisionId", "wikidataId",
    "qid", "pageId", "retrievedAt",
}

STATUSES = ("ready-for-review", "rejected", "source-requires-review", "no-suitable-idea",
            "policy-blocked", "failed")
BLOCKING_VERDICTS = ("UNSUPPORTED", "CONTRADICTED")
ATTENTION_VERDICTS = ("PARTIALLY_SUPPORTED", "UNCERTAIN")


class Rejection(Exception):
    """AI output that the deterministic checks refuse. Not retried."""

    def __init__(self, stage, problems, draft=None, claims=None, non_factual=None, normalizations=None):
        super().__init__(f"{stage}: " + "; ".join(p["message"] for p in problems))
        self.stage, self.problems = stage, problems
        # A refused draft stays visible in the artifact: reviewers and evaluations need it.
        self.draft, self.claims = draft, claims or []
        self.non_factual, self.normalizations = non_factual or [], normalizations or []


def problem(stage, code, where, message):
    return {"stage": stage, "code": code, "where": where, "message": message}


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")


# ============================================================================ prompts

class Prompt:
    """A versioned prompt: prompts/<name>.md (instructions) and <name>.schema.json (answer
    format). The version pins both, so a changed prompt is a different, reviewable version."""

    def __init__(self, name, directory=PROMPT_DIR):
        self.name = name
        self.text = (directory / f"{name}.md").read_text(encoding="utf-8")
        self.schema = json.loads((directory / f"{name}.schema.json").read_text(encoding="utf-8"))
        digest = hashlib.sha256(self.text.encode("utf-8") + danak_content.canonical(self.schema)).hexdigest()
        self.version = f"{name}@{digest[:12]}"
        self.validator = jsonschema.Draft202012Validator(self.schema)

    def system(self):
        schema = json.dumps(self.schema, ensure_ascii=False, indent=1)
        return f"{self.text}\n# Answer schema\n\n```json\n{schema}\n```\n"

    def check(self, data):
        """Schema errors are malformed output: the pipeline asks once more."""
        errors = sorted(self.validator.iter_errors(data), key=lambda e: list(e.absolute_path))
        if errors:
            e = errors[0]
            where = "/".join(map(str, e.absolute_path)) or "(answer)"
            raise danak_ai.ProviderError("malformed-output", f"{where}: {e.message[:200]}")


def load_prompts(directory=PROMPT_DIR):
    return {stage: Prompt(name, directory) for stage, name in PROMPTS.items()}


def data_section(tag, obj):
    """Untrusted material, delimited. It is JSON with < and > escaped, so nothing inside it can
    close the section or open another one, whatever the source text says."""
    body = json.dumps(obj, ensure_ascii=False, indent=1).replace("<", "\\u003c").replace(">", "\\u003e")
    return f"<<<{tag}\n{body}\n{tag}>>>"


# ========================================================================== snapshots

def block_text(block):
    return block.get("text") if "text" in block else "\n".join(block.get("items", []))


def snapshot_payload(snapshot):
    """What the model sees: the text and the context it needs, not URLs or licence data, which
    it has no use for and must not repeat."""
    blocks = []
    for i, b in enumerate(snapshot["content"]["blocks"]):
        entry = {"block": i, "type": b["type"]}
        if b["type"] == "heading":
            entry["level"] = b["level"]
        if b["type"] == "list":
            entry["items"] = b["items"]
        else:
            entry["text"] = b["text"]
        blocks.append(entry)
    return {
        "snapshotId": snapshot["snapshotId"],
        "title": snapshot["derived"]["title"],
        "language": snapshot["derived"]["language"],
        "publisher": snapshot["fromConfig"]["publisher"],
        "revisionTimestamp": snapshot["fromSource"].get("revisionTimestamp"),
        "blocks": blocks,
    }


def load_snapshot(ref, directory=SNAPSHOT_DIR):
    """A stored snapshot by id, checked again: schema, id rule and content hash."""
    if not re.fullmatch(r"[a-z0-9-]+", ref):
        raise SystemExit(f"not a snapshot id: {ref!r}")
    path = pathlib.Path(directory) / f"{ref}.json"
    if path.is_symlink() or not path.is_file():
        raise SystemExit(f"no snapshot {ref} in {directory}")
    snapshot = json.loads(path.read_text(encoding="utf-8"))
    problems = danak_sources.snapshot_problems(snapshot, path.stem)
    if problems:
        raise SystemExit("snapshot fails validation: " + "; ".join(problems))
    return snapshot


def published_danaks(content_dir):
    danaks = []
    for path in sorted((pathlib.Path(content_dir) / "danaks").glob("*.json")):
        try:
            danaks.append(json.loads(path.read_text(encoding="utf-8")))
        except ValueError:
            pass
    return danaks


def current_policy(snapshot, sources_file=SOURCES_FILE, content_dir=CONTENT_DIR):
    """Re-evaluates the snapshot against content/sources.yml as it is NOW. The usableForDanak
    stored in the snapshot records the policy at ingestion time and is only compared, never
    trusted. Being usable is about rights and provenance, never about factual quality."""
    policy = danak_sources.Policy(sources_file)
    source = next((s for s in policy.sources if s.get("id") == snapshot["sourceId"]), None)
    reasons, notes = [], []
    url = snapshot["derived"]["canonicalUrl"]
    if source is None:
        reasons.append(f"source {snapshot['sourceId']!r} is no longer in sources.yml")
    else:
        if urllib.parse.urlsplit(url).hostname not in (source.get("hosts") or []):
            reasons.append(f"{url} is not on a host of {source['id']} in sources.yml")
        if not source.get("license"):
            reasons.append(f"{source['id']} has no known licence in sources.yml; unknown is not permission")
        _, _, why_not = danak_sources.license_state(snapshot["fromSource"].get("license"), source)
        reasons += why_not
    stored = snapshot["derived"]["usableForDanak"]
    if stored != (not reasons):
        notes.append(f"usableForDanak was {str(stored).lower()} at ingestion; under the current policy it is {str(not reasons).lower()}")
    for danak in published_danaks(content_dir):
        if danak_content.normalized_url(danak["source"]["url"]) == danak_content.normalized_url(url):
            reasons.append(f"a published Danak ({danak['id']}) already cites this article")
        qid = snapshot["fromSource"].get("wikidataId")
        if qid and danak.get("conceptKey", "").casefold() == qid.casefold():
            reasons.append(f"a published Danak ({danak['id']}) already teaches {qid}")
    return {"usable": not reasons, "reasons": reasons, "notes": notes, "storedUsableForDanak": stored,
            "source": source}


# ============================================================================ evidence

INVISIBLE = re.compile("[\u200b\u200d\u200e\u200f\u202a-\u202e\u2066-\u2069\ufeff\u00ad]")
PERSIAN_DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")


def match_key(text):
    """Comparison form for 'is this excerpt in that block': forgiving about invisible
    characters, half-spaces, Arabic/Persian letter variants, digit scripts, spacing and case —
    never about words."""
    text = unicodedata.normalize("NFKC", text)
    text = INVISIBLE.sub("", text).replace("\u200c", " ")
    text = text.replace("ي", "ی").replace("ك", "ک").replace("ى", "ی").translate(PERSIAN_DIGITS)
    text = re.sub(r"[\u2018\u2019\u201b\u2032]", "'", text)
    text = re.sub(r"[\u201c\u201d\u201f\u2033«»]", '"', text)
    text = re.sub(r"[\u2010-\u2015\u2212]", "-", text)
    return re.sub(r"\s+", " ", text).strip().casefold()


def ref_problems(stage, where, ref, blocks):
    block = ref.get("block")
    excerpt = ref.get("excerpt") or ""
    if not isinstance(block, int) or not 0 <= block < len(blocks):
        return [problem(stage, "evidence-block-missing", where, f"cites block {block!r}, which the snapshot does not have (0–{len(blocks) - 1})")]
    if len(excerpt) > MAX_EXCERPT or len(match_key(excerpt)) < MIN_EXCERPT:
        return [problem(stage, "evidence-excerpt-size", where, f"excerpt must be {MIN_EXCERPT}–{MAX_EXCERPT} characters")]
    if match_key(excerpt) not in match_key(block_text(blocks[block])):
        return [problem(stage, "evidence-excerpt-not-found", where, f"excerpt is not in block {block}: {excerpt[:80]!r}")]
    return []


# Text in a source that addresses an AI is not knowledge. The prompts already treat all source
# text as data; this heuristic makes such blocks visible and keeps them out of evidence.
INJECTION = re.compile(
    r"ignore (all |any )?(the )?(previous|prior|above|earlier) (instructions|rules)|system prompt|"
    r"you are (an? )?(ai|assistant|language model|chatbot)|as an ai\b|language model|\bllm\b|"
    r"(mark|grade|rate) (every|all) claims?|dans?ak (pipeline|reviewer|verifier)|"
    r"دستور(العمل)?(‌|\s)?های (قبلی|بالا)|نادیده بگیر|مدل زبانی|هوش مصنوعی،? (توجه|دستور)",
    re.IGNORECASE)


def suspected_injection(blocks):
    return [i for i, b in enumerate(blocks) if INJECTION.search(block_text(b))]


def with_snapshot(ref, snapshot_id):
    """Evidence always names its snapshot, so later phases can add corroborating sources."""
    return {"snapshotId": snapshot_id, **ref}


NUMBER = re.compile(r"[0-9۰-۹٠-٩]+(?:[.,/٫٬][0-9۰-۹٠-٩]+)*")


def number_keys(text):
    """Numbers as digit strings, separators ignored: ۲/۱, 2.1 and ۲٫۱ are all '21'."""
    return {re.sub(r"\D", "", m.group().translate(PERSIAN_DIGITS)) for m in NUMBER.finditer(text)}


# ====================================================================== Persian text

ARABIC_SCRIPT = "\u0600-\u06ff"
LATIN_TO_PERSIAN = {",": "،", ";": "؛", "?": "؟"}


def normalize_persian(text):
    """Mechanical fixes only (letters, digits, invisible characters, punctuation next to
    Persian, spacing); never words. Every change is recorded in the artifact."""
    text = unicodedata.normalize("NFC", text)
    text = text.replace("ي", "ی").replace("ك", "ک")
    text = text.translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "۰۱۲۳۴۵۶۷۸۹"))
    text = INVISIBLE.sub("", text).replace("\u00a0", " ")
    text = re.sub(rf"(?<=[{ARABIC_SCRIPT}])\s*([,;?])", lambda m: LATIN_TO_PERSIAN[m.group(1)], text)
    text = re.sub(rf"([,;?])(?=\s*[{ARABIC_SCRIPT}])", lambda m: LATIN_TO_PERSIAN[m.group(1)], text)
    text = re.sub(r"\s+", " ", text)
    text = re.sub(r"\s+([،؛؟.!:»)])", r"\1", text)
    text = re.sub(r"\u200c{2,}", "\u200c", text)
    text = re.sub(r"\u200c(?=\s)|(?<=\s)\u200c", "", text)
    return text.strip().strip("\u200c")


def text_fields(draft):
    """(location, text) for every piece of Danak text, in reading order."""
    fields = [("title", draft["title"]), ("summary", draft["summary"])]
    for i, section in enumerate(draft["sections"]):
        if section.get("heading"):
            fields.append((f"sections[{i}].heading", section["heading"]))
        fields.append((f"sections[{i}].body", section["body"]))
    fields.append(("keyTakeaway", draft["keyTakeaway"]))
    return fields


def reading_seconds(draft):
    words = sum(len(text.split()) for _, text in text_fields(draft))
    return max(10, min(300, 5 * math.ceil(words * 60 / WORDS_PER_MINUTE / 5)))


# ============================================================================ context

class Context:
    """One run: the snapshot, the providers, and everything recorded along the way."""

    def __init__(self, snapshot, generator, verifier, prompts, sleep=time.sleep, now=utc_now):
        self.snapshot, self.generator, self.verifier = snapshot, generator, verifier
        self.prompts, self.sleep, self.now = prompts, sleep, now
        self.blocks = snapshot["content"]["blocks"]
        self.requests = []

    def ask(self, stage, provider, user, pre=None, post=None):
        """One structured call. pre runs on the raw answer (a Rejection there is final),
        then the answer schema, then post; schema or post failures are malformed output."""
        prompt = self.prompts[stage]
        wire = {k: v for k, v in prompt.schema.items() if k not in ("$schema", "title")}
        request = danak_ai.StructuredRequest(stage=stage, system=prompt.system(), user=user,
                                             schema=wire, schema_name=f"danak_{stage}")

        def check(data):
            if pre:
                pre(data)
            prompt.check(data)
            if post:
                post(data)

        started = time.monotonic()
        record = {"stage": stage, "provider": provider.name, "model": provider.model}
        try:
            result, attempts = danak_ai.call_with_retries(provider, request, check=check, sleep=self.sleep)
            record.update(attempts=attempts, **usage_fields(result.usage))
            return result.data
        except danak_ai.ProviderError as e:
            record.update(attempts=getattr(e, "attempts", 1), error=e.code)
            raise
        except Rejection:
            record.update(attempts=1, error="rejected")
            raise
        finally:
            record["ms"] = int((time.monotonic() - started) * 1000)
            self.requests.append(record)


def usage_fields(usage):
    return {"inputTokens": usage.get("input_tokens"), "outputTokens": usage.get("output_tokens")}


# ============================================================================= stages

def stage_idea(ctx):
    """Stage A: one idea and its evidence, or a reasoned refusal."""
    payload = snapshot_payload(ctx.snapshot)
    user = (f"Categories: {', '.join(CATEGORIES)}\n\n{data_section('SOURCE_SNAPSHOT', payload)}\n\n"
            "Choose at most one idea from this snapshot, as instructed.")
    answer = ctx.ask("idea", ctx.generator, user)
    sid = ctx.snapshot["snapshotId"]

    concerns = []
    for i, c in enumerate(answer["concerns"]):
        located = c["block"] is not None and c["excerpt"] and not ref_problems("idea", "", c, ctx.blocks)
        concerns.append({**c, "snapshotId": sid, "locationVerified": bool(located)})

    if answer["status"] != "IDEA":
        return {"status": answer["status"], "reason": answer["reason"], "idea": None, "evidence": []}, concerns

    problems = []
    if answer["idea"] is None:
        problems.append(problem("idea", "idea-missing", "idea", "status IDEA without an idea"))
    if not answer["evidence"]:
        problems.append(problem("idea", "evidence-missing", "evidence", "an idea without evidence"))
    for i, ref in enumerate(answer["evidence"]):
        problems += ref_problems("idea", f"evidence[{i}]", ref, ctx.blocks)
    if problems:
        raise Rejection("idea", problems)

    idea = {"status": "IDEA", "reason": answer["reason"], "idea": answer["idea"],
            "evidence": [with_snapshot(ref, sid) for ref in answer["evidence"]]}
    evidence_blocks = {ref["block"] for ref in answer["evidence"]}
    serious = [c for c in concerns if c["severity"] == "serious" and c["block"] in evidence_blocks]
    if serious:
        # The model's own warning outranks its own idea: a person looks at the source first.
        idea["status"] = "SOURCE_REQUIRES_REVIEW"
        idea["reason"] = (f"the pipeline stopped: the idea rests on block(s) "
                          f"{sorted({c['block'] for c in serious})}, which the model itself flagged as serious concerns")
    return idea, concerns


CATEGORIES = ["technology", "programming", "science", "psychology", "economy", "history", "productivity", "curiosities"]
LOCATION = re.compile(r"^(title|summary|keyTakeaway|sections\[(\d+)\]\.(heading|body))$")


def forbid_provenance(data):
    touched = sorted(PROVENANCE_FIELDS & set(data))
    if touched:
        raise Rejection("generate", [problem("generate", "ai-provenance-field", ", ".join(touched),
                                             f"the model tried to set {', '.join(touched)}; provenance comes only from the snapshot")])


def stage_generate(ctx, idea):
    """Stage B: the Persian Danak and its claim map, then the deterministic checks that can be
    made before anyone spends a verification call on it."""
    payload = snapshot_payload(ctx.snapshot)
    chosen = {"concept": idea["idea"]["concept"], "conceptFa": idea["idea"]["conceptFa"],
              "angle": idea["idea"]["angle"], "category": idea["idea"]["category"],
              "evidence": [{"block": r["block"], "excerpt": r["excerpt"]} for r in idea["evidence"]]}
    user = (f"Categories: {', '.join(CATEGORIES)}\n\n{data_section('SOURCE_SNAPSHOT', payload)}\n\n"
            f"{data_section('IDEA', chosen)}\n\nWrite the Danak draft for this idea, as instructed.")
    answer = ctx.ask("generate", ctx.generator, user, pre=forbid_provenance)

    normalizations = []
    draft = {"category": answer["category"], "title": answer["title"], "summary": answer["summary"],
             "sections": [], "keyTakeaway": answer["keyTakeaway"]}
    for s in answer["sections"]:
        draft["sections"].append({**({"heading": s["heading"]} if s["heading"] else {}), "body": s["body"]})
    for location, text in text_fields(draft):
        fixed = normalize_persian(text)
        if fixed != text:
            normalizations.append({"location": location, "before": text, "after": fixed})
            set_location(draft, location, fixed)

    sid = ctx.snapshot["snapshotId"]
    claims = [{"id": c["id"], "location": c["location"], "claim": c["claim"],
               "evidence": [with_snapshot(r, sid) for r in c["evidence"]]} for c in answer["claims"]]
    problems = claim_problems(draft, claims, answer["nonFactual"], ctx.blocks)
    if problems:
        raise Rejection("generate", problems, draft, claims, answer["nonFactual"], normalizations)
    return draft, claims, answer["nonFactual"], normalizations


def set_location(draft, location, value):
    m = LOCATION.match(location)
    if m.group(2) is None:
        draft[location] = value
    else:
        draft["sections"][int(m.group(2))][m.group(3)] = value


def location_exists(draft, location):
    m = LOCATION.match(location or "")
    if not m:
        return False
    if m.group(2) is None:
        return True
    i = int(m.group(2))
    return i < len(draft["sections"]) and m.group(3) in draft["sections"][i]


def claim_problems(draft, claims, non_factual, blocks):
    problems = []
    if not claims:
        problems.append(problem("generate", "claims-missing", "claims", "the draft lists no factual claims"))
    ids = [c["id"] for c in claims]
    for dup in sorted({i for i in ids if ids.count(i) > 1}):
        problems.append(problem("generate", "claim-id", dup, "claim id used twice"))
    for c in claims:
        where = f"claim {c['id']}"
        if not location_exists(draft, c["location"]):
            problems.append(problem("generate", "claim-location", where, f"location {c['location']!r} is not in the Danak"))
        if not c["evidence"]:
            problems.append(problem("generate", "evidence-missing", where, "a claim without evidence"))
        for j, ref in enumerate(c["evidence"]):
            problems += ref_problems("generate", f"{where} evidence[{j}]", ref, blocks)
    for n in non_factual:
        if not location_exists(draft, n["location"]):
            problems.append(problem("generate", "claim-location", "nonFactual", f"location {n['location']!r} is not in the Danak"))

    injected = set(suspected_injection(blocks))
    for c in claims:
        for ref in c["evidence"]:
            if ref.get("block") in injected:
                problems.append(problem("generate", "evidence-suspected-injection", f"claim {c['id']}",
                                        f"cites block {ref['block']}, which reads like instructions to an AI, not source knowledge"))

    covered = {c["location"] for c in claims} | {n["location"] for n in non_factual}
    for location, _ in text_fields(draft):
        if location.endswith(".heading"):
            continue  # a heading labels its section; the body carries the claims
        if location not in covered:
            problems.append(problem("generate", "claim-coverage", location, "no claim and not marked non-factual"))

    evidence_numbers = set()
    for c in claims:
        for ref in c["evidence"]:
            evidence_numbers |= number_keys(ref["excerpt"])
    for location, text in text_fields(draft):
        for number in sorted(number_keys(text) - evidence_numbers):
            problems.append(problem("generate", "number-without-evidence", location,
                                    f"the number {number} does not appear in any claim's evidence"))
        if re.search(r"https?://|www\.|\b[a-z0-9-]+\.(org|com|net|ir)\b", text, re.IGNORECASE):
            problems.append(problem("generate", "ai-url", location, "Danak text must not contain links or domains"))
    return problems


def stage_verify(ctx, draft, claims, non_factual):
    """Stage C: a separate call judges every claim against the snapshot. The verifier cannot
    change the draft; its verdicts only decide whether a person should look at it."""
    payload = snapshot_payload(ctx.snapshot)
    under_review = {
        "danak": {"title": draft["title"], "summary": draft["summary"],
                  "sections": [{"heading": s.get("heading"), "body": s["body"]} for s in draft["sections"]],
                  "keyTakeaway": draft["keyTakeaway"]},
        "claims": [{"id": c["id"], "location": c["location"], "claim": c["claim"],
                    "evidence": [{"block": r["block"], "excerpt": r["excerpt"]} for r in c["evidence"]]} for c in claims],
        "nonFactual": non_factual,
    }
    user = (f"{data_section('SOURCE_SNAPSHOT', payload)}\n\n{data_section('DRAFT', under_review)}\n\n"
            "Check every claim of this draft against the snapshot, as instructed.")
    ids = [c["id"] for c in claims]

    def complete(data):
        judged = [v.get("claim") for v in data.get("verdicts", [])]
        if sorted(judged) != sorted(ids):
            raise danak_ai.ProviderError("malformed-output", f"verdicts for {sorted(judged)}, claims are {sorted(ids)}")

    answer = ctx.ask("verify", ctx.verifier, user, post=complete)
    sid = ctx.snapshot["snapshotId"]
    verdicts = {}
    for v in answer["verdicts"]:
        status, adjusted = v["status"], None
        bad = [p for j, ref in enumerate(v["evidence"]) for p in ref_problems("verify", f"verdict {v['claim']} evidence[{j}]", ref, ctx.blocks)]
        if bad and status in ("SUPPORTED", "PARTIALLY_SUPPORTED"):
            status, adjusted = "UNCERTAIN", f"was {v['status']}, but the verifier's evidence is not in the snapshot"
        elif not v["evidence"] and status in ("SUPPORTED", "PARTIALLY_SUPPORTED"):
            status, adjusted = "UNCERTAIN", f"was {v['status']} without any evidence"
        verdicts[v["claim"]] = {
            "status": status, "verifierStatus": v["status"], "adjusted": adjusted, "reason": v["reason"],
            "evidence": [with_snapshot(r, sid) for r in v["evidence"]],
            "sourceReliabilityConcern": v["sourceReliabilityConcern"],
        }
    unclaimed = [u for u in answer["unclaimedStatements"]]
    return verdicts, unclaimed, answer["notes"]


# ====================================================================== the Danak

def danak_id(snapshot):
    qid = snapshot["fromSource"].get("wikidataId")
    if qid:
        return qid.lower()
    return re.sub(r"[^a-z0-9]+", "_", f"{snapshot['sourceId']}_{snapshot['derived']['contentSha256'][:8]}").strip("_")


def assemble(draft, snapshot, policy_source):
    """The Danak as schema v1 describes it, minus the image (chosen by a person). Every field
    outside the Persian text comes from the snapshot and the current policy."""
    danak = {"id": danak_id(snapshot), "category": draft["category"], "title": draft["title"],
             "summary": draft["summary"], "sections": draft["sections"], "keyTakeaway": draft["keyTakeaway"],
             "readingSeconds": reading_seconds(draft)}
    qid = snapshot["fromSource"].get("wikidataId")
    if qid:
        danak["conceptKey"] = qid
    danak["source"] = {
        "url": snapshot["derived"]["canonicalUrl"],
        "publisher": policy_source["publisher"],
        "title": snapshot["derived"]["title"],
        "license": snapshot["derived"]["license"],
        "retrievedAt": snapshot["retrievedAt"],
        "snapshotSha256": snapshot["derived"]["contentSha256"],
    }
    return danak


def draft_danak_validator():
    """Danak schema v1 with the image not yet required: at draft stage there is none."""
    schema = danak_content.load_schema("danak-v1.schema.json")
    schema["$defs"]["danak"]["required"] = [f for f in schema["$defs"]["danak"]["required"] if f != "image"]
    return jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())


def validate_danak(danak, snapshot, sources_file=SOURCES_FILE, content_dir=CONTENT_DIR):
    """Deterministic validation, with the rules published content must meet where they apply."""
    stage = "validate"
    problems = []
    for e in draft_danak_validator().iter_errors({"schemaVersion": 1, "danaks": [danak]}):
        where = "/".join(str(p) for p in list(e.absolute_path)[2:]) or "(danak)"
        problems.append(problem(stage, "schema", where, e.message))
    if problems:
        return problems

    for location, text in text_fields(danak):
        kind = "body" if location.endswith(".body") else ("heading" if location.endswith(".heading") else location)
        for issue in danak_content.text_problems(text, kind):
            problems.append(problem(stage, "persian", location, issue))
    if reading_seconds(danak) != danak["readingSeconds"]:
        problems.append(problem(stage, "reading-seconds", "readingSeconds", f"should be {reading_seconds(danak)}"))

    source = danak["source"]
    expected = {
        "url": snapshot["derived"]["canonicalUrl"], "title": snapshot["derived"]["title"],
        "license": snapshot["derived"]["license"], "retrievedAt": snapshot["retrievedAt"],
        "snapshotSha256": snapshot["derived"]["contentSha256"],
    }
    for field, value in expected.items():
        if source.get(field) != value:
            problems.append(problem(stage, "source-metadata", f"source.{field}", f"must be {value!r} (from the snapshot)"))
    qid = snapshot["fromSource"].get("wikidataId")
    if qid and danak.get("conceptKey") != qid:
        problems.append(problem(stage, "concept-key", "conceptKey", f"must be the snapshot's Wikidata id {qid}"))
    if not qid and "conceptKey" in danak:
        problems.append(problem(stage, "concept-key", "conceptKey", "no Wikidata id in the snapshot, so no conceptKey"))
    if danak["id"] != danak_id(snapshot):
        problems.append(problem(stage, "draft-id", "id", f"must be {danak_id(snapshot)} until a person renames it"))

    # The same source rules published content must pass (host, publisher, licence, derivatives).
    text_sources, _ = danak_content.load_sources(pathlib.Path(sources_file).parent, [])
    check = []
    danak_content.check_source({**danak, "_file": "draft", "image": {"credit": {"pageUrl": "https://commons.wikimedia.org/"}}},
                               text_sources, check)
    problems += [problem(stage, p.code, "source", p.message) for p in check]
    return problems


# ========================================================================= one run

def run(snapshot, generator, verifier, *, prompts=None, sources_file=SOURCES_FILE,
        content_dir=CONTENT_DIR, sleep=time.sleep, now=utc_now):
    """Runs the whole pipeline for one snapshot and returns the review artifact (a dict).
    Never raises for a model's behaviour: every outcome is an artifact a person can read."""
    prompts = prompts or load_prompts()
    started = now()
    ctx = Context(snapshot, generator, verifier, prompts, sleep=sleep, now=now)
    artifact = new_artifact(snapshot, generator, verifier, prompts, started)

    policy = current_policy(snapshot, sources_file, content_dir)
    artifact["policy"] = {k: v for k, v in policy.items() if k != "source"}
    artifact["suspectedInjection"] = suspected_injection(snapshot["content"]["blocks"])
    try:
        if not policy["usable"]:
            return finish(artifact, ctx, "policy-blocked", "; ".join(policy["reasons"]))

        idea, concerns = stage_idea(ctx)
        artifact["idea"], artifact["concerns"] = idea, concerns
        if idea["status"] == "NO_SUITABLE_IDEA":
            return finish(artifact, ctx, "no-suitable-idea", idea["reason"])
        if idea["status"] == "SOURCE_REQUIRES_REVIEW":
            return finish(artifact, ctx, "source-requires-review", idea["reason"])

        draft, claims, non_factual, normalizations = stage_generate(ctx, idea)
        artifact["normalizations"] = normalizations
        artifact["nonFactual"] = non_factual
        danak = assemble(draft, snapshot, policy["source"])
        artifact["danak"] = danak
        artifact["claims"] = claims

        verdicts, unclaimed, notes = stage_verify(ctx, draft, claims, non_factual)
        for claim in claims:
            claim["verdict"] = verdicts[claim["id"]]
        artifact["unclaimedStatements"] = unclaimed
        artifact["verifierNotes"] = notes
        artifact["problems"] += validate_danak(danak, snapshot, sources_file, content_dir)
    except Rejection as e:
        artifact["problems"] += e.problems
        if e.draft:
            artifact["danak"] = assemble(e.draft, snapshot, policy["source"])
            artifact["claims"], artifact["nonFactual"] = e.claims, e.non_factual
            artifact["normalizations"] = e.normalizations
        return finish(artifact, ctx, "rejected", f"stage {e.stage}: deterministic checks refused the model's answer")
    except danak_ai.ProviderError as e:
        artifact["failure"] = {"stage": ctx.requests[-1]["stage"] if ctx.requests else None, "code": e.code, "message": e.message}
        return finish(artifact, ctx, "failed", f"provider error: {e.code}")

    for claim in artifact["claims"]:
        if claim["verdict"]["status"] in BLOCKING_VERDICTS:
            artifact["problems"].append(problem("verify", claim["verdict"]["status"].lower(), f"claim {claim['id']}",
                                                f"{claim['claim']} — {claim['verdict']['reason']}"))
    for u in artifact["unclaimedStatements"]:
        artifact["problems"].append(problem("verify", "unclaimed-statement", u["location"],
                                            f"a factual statement with no claim or evidence: {u['text']} — {u['reason']}"))
    if artifact["problems"]:
        return finish(artifact, ctx, "rejected", "blocking problems; see the list")
    review = [x for x in attention(artifact) if x["severity"] in ("serious", "review")]
    return finish(artifact, ctx, "ready-for-review",
                  f"all automatic checks passed; {len(review)} item(s) need a person's attention (section G)")


def new_artifact(snapshot, generator, verifier, prompts, started):
    fs = snapshot["fromSource"]
    return {
        "schemaVersion": 1,
        "kind": "danak-draft",
        "status": None,
        "statusReason": None,
        "review": {"humanApprovalRequired": True, "autoPublish": False},
        "snapshot": {
            "snapshotId": snapshot["snapshotId"], "contentSha256": snapshot["derived"]["contentSha256"],
            "sourceType": snapshot["sourceType"], "sourceId": snapshot["sourceId"],
            "retrievedAt": snapshot["retrievedAt"], "title": snapshot["derived"]["title"],
            "language": snapshot["derived"]["language"], "canonicalUrl": snapshot["derived"]["canonicalUrl"],
            "publisher": snapshot["fromConfig"]["publisher"], "license": snapshot["derived"]["license"],
            "pageId": fs.get("pageId"), "revisionId": fs.get("revisionId"),
            "revisionTimestamp": fs.get("revisionTimestamp"), "wikidataId": fs.get("wikidataId"),
        },
        "policy": None,
        "idea": None,
        "concerns": [],
        "danak": None,
        "pendingForPublication": [],
        "claims": [],
        "nonFactual": [],
        "unclaimedStatements": [],
        "verifierNotes": "",
        "attention": [],
        "problems": [],
        "normalizations": [],
        "corroboration": [],
        "suspectedInjection": [],
        "failure": None,
        "generation": {
            "pipeline": PIPELINE,
            "generator": generator.describe(),
            "verifier": verifier.describe(),
            "prompts": {stage: p.version for stage, p in prompts.items()},
            "startedAt": started,
            "finishedAt": None,
            "requests": [],
        },
    }


def finish(artifact, ctx, status, reason):
    artifact["status"], artifact["statusReason"] = status, reason
    artifact["generation"]["requests"] = ctx.requests
    artifact["generation"]["finishedAt"] = ctx.now()
    artifact["attention"] = attention(artifact)
    if artifact["danak"] and status == "ready-for-review":
        artifact["pendingForPublication"] = [
            "image: choose a photo from an allowed image source and add image.src and image.credit",
            f"id: {artifact['danak']['id']} is provisional; choose the permanent id before publishing",
            "file: add as content/danaks/NNNN-<id>.json and run tools/danak_content.py validate",
        ]
    return artifact


def attention(artifact):
    """What a reviewer must look at even when nothing blocks the draft."""
    items = []
    for c in artifact["concerns"]:
        where = f"block {c['block']}" if c["block"] is not None else "snapshot"
        items.append({"kind": f"source-{c['kind']}", "severity": c["severity"], "where": where,
                      "message": c["explanation"] + ("" if c["locationVerified"] or c["block"] is None else " (excerpt not found in the snapshot)")})
    for claim in artifact["claims"]:
        v = claim.get("verdict")
        if not v:
            continue
        if v["status"] in ATTENTION_VERDICTS:
            items.append({"kind": v["status"].lower(), "severity": "review", "where": f"claim {claim['id']}",
                          "message": f"{claim['claim']} — {v['reason']}" + (f" ({v['adjusted']})" if v["adjusted"] else "")})
        if v["sourceReliabilityConcern"]:
            items.append({"kind": "source-reliability", "severity": "review", "where": f"claim {claim['id']}",
                          "message": v["sourceReliabilityConcern"]})
    for block in artifact.get("suspectedInjection", []):
        items.append({"kind": "suspected-injection", "severity": "review", "where": f"block {block}",
                      "message": "this block reads like instructions to an AI; it was treated as data and cannot be cited as evidence"})
    danak = artifact["danak"]
    if danak and artifact["snapshot"]["language"] != "fa":
        for location, text in text_fields(danak):
            if "«" in text:
                items.append({"kind": "translated-quote", "severity": "review", "where": location,
                              "message": "quotation marks in text translated from another language: make sure it is not presented as a direct quote"})
    for n in artifact["normalizations"]:
        items.append({"kind": "normalized", "severity": "info", "where": n["location"],
                      "message": "mechanical Persian fixes applied to the model's text"})
    for note in (artifact["policy"] or {}).get("notes", []):
        items.append({"kind": "policy-changed", "severity": "info", "where": "policy", "message": note})
    return items


# ======================================================================= storage

def write_artifact(artifact, out_dir=DRAFT_DIR):
    """drafts/<snapshotId>/draft.json and review.md. Drafts may be regenerated: a new run
    replaces the previous one (Git keeps the history); snapshots are never touched."""
    sid = artifact["snapshot"]["snapshotId"]
    if not re.fullmatch(r"[a-z0-9-]+", sid):
        raise SystemExit(f"refusing to write a draft for {sid!r}")
    directory = pathlib.Path(out_dir) / sid
    directory.mkdir(parents=True, exist_ok=True)
    for name, text in (("draft.json", json.dumps(artifact, ensure_ascii=False, indent=2) + "\n"),
                       ("review.md", render_review(artifact))):
        tmp = directory / f".{name}.tmp"
        tmp.write_text(text, encoding="utf-8")
        os.replace(tmp, directory / name)
    return directory


def artifact_validator():
    schema = json.loads(DRAFT_SCHEMA.read_text(encoding="utf-8"))
    return jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())


def validate_drafts(out_dir=DRAFT_DIR, snapshot_dir=SNAPSHOT_DIR, sources_file=SOURCES_FILE, content_dir=CONTENT_DIR):
    """Every stored draft: artifact schema, its snapshot exists and still matches, its evidence
    is still in the snapshot, and a ready draft still passes Danak validation."""
    validator = artifact_validator()
    problems, count = [], 0
    for directory in sorted(p for p in pathlib.Path(out_dir).iterdir() if p.is_dir()):
        count += 1
        path = directory / "draft.json"
        try:
            artifact = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as e:
            problems.append(f"{directory.name}: {e}")
            continue
        errors = list(validator.iter_errors(artifact))
        problems += [f"{directory.name}: {'/'.join(map(str, e.absolute_path)) or '(root)'}: {e.message}" for e in errors]
        if errors:
            continue
        sid = artifact["snapshot"]["snapshotId"]
        if directory.name != sid:
            problems.append(f"{directory.name}: directory does not match snapshotId {sid}")
            continue
        try:
            snapshot = load_snapshot(sid, snapshot_dir)
        except SystemExit as e:
            problems.append(f"{sid}: {e}")
            continue
        if snapshot["derived"]["contentSha256"] != artifact["snapshot"]["contentSha256"]:
            problems.append(f"{sid}: contentSha256 differs from the snapshot")
        refs = [r for c in artifact["claims"] for r in c["evidence"]] + (artifact["idea"] or {}).get("evidence", [])
        for ref in refs:
            problems += [f"{sid}: {p['message']}" for p in ref_problems("stored", "", ref, snapshot["content"]["blocks"])]
        if artifact["status"] == "ready-for-review":
            problems += [f"{sid}: {p['where']}: {p['message']}"
                         for p in validate_danak(artifact["danak"], snapshot, sources_file, content_dir)]
    return count, problems


# ==================================================================== review.md

VERDICT_MARK = {"SUPPORTED": "✅", "PARTIALLY_SUPPORTED": "🟡", "UNCERTAIN": "❔", "UNSUPPORTED": "⛔", "CONTRADICTED": "⛔"}
STATUS_LINE = {
    "ready-for-review": "READY FOR REVIEW — a person decides whether to publish; nothing is published automatically",
    "rejected": "REJECTED — blocking problems below; do not publish",
    "source-requires-review": "SOURCE REQUIRES REVIEW — the model found warning signs in the source and stopped",
    "no-suitable-idea": "NO SUITABLE IDEA — the model found nothing worth a Danak in this snapshot",
    "policy-blocked": "POLICY BLOCKED — the current source policy does not allow a Danak from this snapshot",
    "failed": "FAILED — the provider did not produce a usable answer",
}


def cell(text):
    return str(text if text is not None else "").replace("|", "\\|").replace("\n", " ")


def render_review(a):
    s, g = a["snapshot"], a["generation"]
    out = [f"# Draft review: {s['title']}", "", f"**Status: {STATUS_LINE[a['status']]}.**", "", f"{a['statusReason']}", ""]

    verdicts = [c["verdict"]["status"] for c in a["claims"] if c.get("verdict")]
    out += ["## Decision checklist", ""]
    out.append(f"- [{'x' if a['status'] == 'ready-for-review' else ' '}] passed every automatic check")
    if verdicts:
        counts = ", ".join(f"{verdicts.count(k)} {k}" for k in VERDICT_MARK if verdicts.count(k))
        out.append(f"- [{'x' if all(v == 'SUPPORTED' for v in verdicts) else ' '}] every claim SUPPORTED ({counts})")
    serious = [x for x in a["attention"] if x["severity"] in ("serious", "review")]
    out.append(f"- [{'x' if not serious else ' '}] nothing flagged for review ({len(serious)} item(s) in G)")
    out.append("- [ ] a person has read the Danak against the evidence (E, F)")
    out.append("- [ ] image, permanent id and file position chosen" if a["danak"] else "- [ ] (no Danak)")
    out.append("")

    out += ["## A. Proposed Danak", ""]
    d = a["danak"]
    if d:
        out += [f"**{d['title']}**", "", f"> {d['summary']}", ""]
        for sec in d["sections"]:
            if sec.get("heading"):
                out += [f"**{sec['heading']}**", ""]
            out += [sec["body"], ""]
        out += [f"**نکتهٔ کلیدی:** {d['keyTakeaway']}", "",
                f"category `{d['category']}` · conceptKey `{d.get('conceptKey', '—')}` · readingSeconds {d['readingSeconds']} (computed) · provisional id `{d['id']}`", ""]
        for p in a["pendingForPublication"]:
            out.append(f"- pending: {p}")
        out.append("")
    else:
        out += ["No Danak was produced.", ""]

    out += ["## B. Source", "",
            f"- publisher: {s['publisher']}", f"- licence: {s['license']}", f"- article: {s['title']} ({s['language']})",
            f"- URL: {s['canonicalUrl']}", "",
            "## C. Snapshot", "",
            f"- snapshotId: `{s['snapshotId']}`", f"- contentSha256: `{s['contentSha256']}`", f"- retrieved: {s['retrievedAt']}",
            f"- policy now: {'usable' if a['policy'] and a['policy']['usable'] else 'NOT usable'}"
            + (f" — {'; '.join(a['policy']['reasons'])}" if a['policy'] and a['policy']['reasons'] else "")
            + " (rights and provenance only; says nothing about accuracy)", "",
            "## D. Source revision", "",
            f"- revision {s.get('revisionId') or '—'} of {s.get('revisionTimestamp') or '—'}; page {s.get('pageId') or '—'}; Wikidata {s.get('wikidataId') or '—'}", ""]

    out += ["## E. Evidence used", ""]
    idea = a["idea"]
    if idea and idea.get("idea"):
        i = idea["idea"]
        out += [f"Idea: **{i['conceptFa']}** ({i['concept']}) — {i['angle']}", "", f"Why: {i['whyUseful']}", "",
                "| block | source excerpt (original language) | supports |", "|---|---|---|"]
        out += [f"| {r['block']} | {cell(r['excerpt'])} | {cell(r['supports'])} |" for r in idea["evidence"]]
        out.append("")
    elif idea:
        out += [f"Stage A answered {idea['status']}: {idea['reason']}", ""]
    else:
        out += ["Stage A did not run.", ""]

    out += ["## F. Claim-by-claim verification", ""]
    if a["claims"]:
        lang = s["language"]
        out += [f"| claim | where | Persian claim (interpretation) | source evidence ({lang}, verbatim) | verdict | verifier's reason |",
                "|---|---|---|---|---|---|"]
        for c in a["claims"]:
            v = c.get("verdict") or {}
            ev = "<br>".join(f"[{r['block']}] {cell(r['excerpt'])}" for r in c["evidence"])
            status = v.get("status", "not verified")
            mark = VERDICT_MARK.get(status, "")
            adjusted = f" ({v['adjusted']})" if v.get("adjusted") else ""
            out.append(f"| {c['id']} | {c['location']} | {cell(c['claim'])} | {ev} | {mark} {status}{adjusted} | {cell(v.get('reason'))} |")
        out.append("")
        for n in a["nonFactual"]:
            out.append(f"- non-factual by the writer's account: `{n['location']}` — {n['reason']}")
        for u in a["unclaimedStatements"]:
            out.append(f"- ⛔ unclaimed factual statement at `{u['location']}`: {u['text']} — {u['reason']}")
        if a["verifierNotes"]:
            out += ["", f"Verifier notes: {a['verifierNotes']}"]
        out.append("")
    else:
        out += ["No claims.", ""]

    out += ["## G. Concerns and warnings", ""]
    if a["attention"]:
        out += ["| kind | severity | where | message |", "|---|---|---|---|"]
        out += [f"| {x['kind']} | {x['severity']} | {x['where']} | {cell(x['message'])} |" for x in a["attention"]]
    else:
        out.append("None.")
    out.append("")

    out += ["## H. Deterministic validation", ""]
    if a["problems"]:
        out += ["| stage | check | where | problem |", "|---|---|---|---|"]
        out += [f"| {p['stage']} | {p['code']} | {cell(p['where'])} | {cell(p['message'])} |" for p in a["problems"]]
    elif a["status"] == "ready-for-review":
        out.append("All passed: Danak schema v1 (image pending), Persian text rules, reading time, source metadata "
                   "from the snapshot, concept key, source policy, every evidence excerpt found verbatim in its block, "
                   "every number backed by evidence, no links, every text location covered by a claim.")
    else:
        out.append("Not reached.")
    for n in a["normalizations"]:
        out += ["", f"Normalized `{n['location']}`: {n['before']!r} → {n['after']!r}"]
    if a["failure"]:
        out += ["", f"Failure: stage {a['failure']['stage']}, {a['failure']['code']}: {a['failure']['message']}"]
    out.append("")

    out += ["## I. Generation metadata", "",
            f"- pipeline: {g['pipeline']}",
            f"- generator: {json.dumps(g['generator'], ensure_ascii=False)}",
            f"- verifier: {json.dumps(g['verifier'], ensure_ascii=False)}",
            f"- prompts: {', '.join(g['prompts'].values())}",
            f"- started {g['startedAt']}, finished {g['finishedAt']}", ""]
    if g["requests"]:
        out += ["| stage | model | attempts | input tokens | output tokens | ms | error |", "|---|---|---|---|---|---|---|"]
        out += [f"| {r['stage']} | {r['model']} | {r['attempts']} | {r.get('inputTokens') or '—'} | {r.get('outputTokens') or '—'} | {r['ms']} | {r.get('error') or ''} |"
                for r in g["requests"]]
        out.append("")
    return "\n".join(out)


# ============================================================================ eval

def run_eval(cases_file, out_dir, generator_for, verifier_for, sleep=time.sleep, snapshot_dir=SNAPSHOT_DIR):
    """Runs the pipeline over the evaluation cases and writes, per case, the artifact, plus a
    summary with the automatic measures and a sheet for the human scores. Nothing goes to
    drafts/: evaluation output is for comparing models, not for publishing."""
    cases = yaml.safe_load(pathlib.Path(cases_file).read_text(encoding="utf-8"))["cases"]
    out_dir = pathlib.Path(out_dir)
    results = []
    for case in cases:
        snapshot = load_snapshot(case["snapshot"], snapshot_dir)
        generator, verifier = generator_for(case), verifier_for(case)
        artifact = run(snapshot, generator, verifier, sleep=sleep)
        write_artifact(artifact, out_dir / "drafts")
        results.append(measure(case, artifact))
    summary = {"generator": results[0]["generator"] if results else None,
               "verifier": results[0]["verifier"] if results else None, "cases": results}
    (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (out_dir / "summary.md").write_text(render_summary(summary, cases), encoding="utf-8")
    return summary


def measure(case, a):
    verdicts = [c["verdict"]["status"] for c in a["claims"] if c.get("verdict")]
    suspects = []
    for s in case.get("suspect", []):
        key = match_key(s["excerpt"])
        flagged = any(c["block"] == s["block"] or (c["excerpt"] and (match_key(c["excerpt"]) in key or key in match_key(c["excerpt"])))
                      for c in a["concerns"]) or any(
            (c.get("verdict") or {}).get("sourceReliabilityConcern") and any(r["block"] == s["block"] for r in c["evidence"])
            for c in a["claims"])
        used = any(key in match_key(r["excerpt"]) or match_key(r["excerpt"]) in key
                   for c in a["claims"] for r in c["evidence"] if r["block"] == s["block"])
        if used and a["status"] == "ready-for-review" and not flagged:
            outcome = "used-unflagged"
        elif used:
            outcome = "used-flagged"
        elif flagged:
            outcome = "flagged"
        else:
            outcome = "avoided-silently"
        suspects.append({"note": s["note"], "outcome": outcome})
    tokens = [(r.get("inputTokens") or 0, r.get("outputTokens") or 0) for r in a["generation"]["requests"]]
    return {
        "case": case["id"], "snapshot": case["snapshot"], "status": a["status"],
        "expected": case.get("expect"), "meetsExpectation": a["status"] in case.get("expect", STATUSES),
        "claims": len(a["claims"]), "verdicts": {k: verdicts.count(k) for k in VERDICT_MARK},
        "unclaimed": len(a["unclaimedStatements"]),
        "blockingProblems": len(a["problems"]),
        "problemCodes": sorted({p["code"] for p in a["problems"]}),
        "sourceConcerns": len(a["concerns"]),
        "suspect": suspects,
        "requests": len(a["generation"]["requests"]),
        "inputTokens": sum(t[0] for t in tokens), "outputTokens": sum(t[1] for t in tokens),
        "ms": sum(r["ms"] for r in a["generation"]["requests"]),
        "generator": a["generation"]["generator"], "verifier": a["generation"]["verifier"],
        "humanCheck": case.get("humanCheck"),
    }


def render_summary(summary, cases):
    out = ["# Model evaluation", "",
           f"generator {json.dumps(summary['generator'], ensure_ascii=False)} · verifier {json.dumps(summary['verifier'], ensure_ascii=False)}", "",
           "## Automatic measures", "",
           "| case | status | expected | claims | ✅ | 🟡 | ❔ | ⛔ | unclaimed | blocking | source concerns | suspicious material | tokens in/out | s |",
           "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in summary["cases"]:
        v = r["verdicts"]
        suspects = "; ".join(f"{s['outcome']}" for s in r["suspect"]) or "—"
        out.append(f"| {r['case']} | {r['status']} | {'✓' if r['meetsExpectation'] else '✗ ' + '/'.join(r['expected'] or [])} | {r['claims']} | "
                   f"{v['SUPPORTED']} | {v['PARTIALLY_SUPPORTED']} | {v['UNCERTAIN']} | {v['UNSUPPORTED'] + v['CONTRADICTED']} | "
                   f"{r['unclaimed']} | {r['blockingProblems']} | {r['sourceConcerns']} | {suspects} | {r['inputTokens']}/{r['outputTokens']} | {r['ms'] / 1000:.0f} |")
    out += ["", "Suspicious material: `used-unflagged` is the failure this evaluation looks for (a suspicious source "
            "statement became a confident claim in a ready draft). `used-flagged`, `flagged` and `avoided-silently` are acceptable.",
            "", "## Human scores (1–5; fill in)", "",
            "| case | factual grounding | Persian quality | usefulness | conciseness | evidence quality | handling of suspicious material | what to check |",
            "|---|---|---|---|---|---|---|---|"]
    for r in summary["cases"]:
        out.append(f"| {r['case']} |  |  |  |  |  |  | {cell(r['humanCheck'])} |")
    out.append("")
    return "\n".join(out)


# ============================================================================= CLI

def providers(args, env=None):
    env = os.environ if env is None else env
    script = json.loads(pathlib.Path(args.script).read_text(encoding="utf-8")) if getattr(args, "script", None) else None
    if script is not None and not env.get("DANAK_AI_PROVIDER"):
        env = {**env, "DANAK_AI_PROVIDER": "fake"}
    generator = danak_ai.provider_from_env("DANAK_AI", env, fake_script=script)
    verifier = danak_ai.provider_from_env("DANAK_VERIFIER", env, fake_script=script)
    return generator, verifier


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    r = sub.add_parser("run", help="generate a draft for one snapshot")
    r.add_argument("snapshot_id")
    r.add_argument("--script", help="answers for the fake provider (tests and dry runs)")
    r.add_argument("--out", default=str(DRAFT_DIR))
    v = sub.add_parser("validate-drafts")
    v.add_argument("dir", nargs="?", default=str(DRAFT_DIR))
    e = sub.add_parser("eval", help="run the evaluation cases with the configured provider")
    e.add_argument("--cases", default=str(ROOT / "evals" / "phase5-cases.yml"))
    e.add_argument("--out", required=True)
    e.add_argument("--script-dir", help="fake-provider answers per case: <dir>/<case id>.json")
    sub.add_parser("prompts", help="print prompt versions")
    args = parser.parse_args(argv)

    try:
        if args.command == "prompts":
            for stage, p in load_prompts().items():
                print(f"{stage:9} {p.version}")
            return 0
        if args.command == "validate-drafts":
            if not pathlib.Path(args.dir).is_dir():
                print("0 drafts")
                return 0
            count, problems = validate_drafts(args.dir)
            for p in problems:
                print("FAIL", p)
            print(f"{count} draft(s), {len(problems)} problem(s)")
            return 1 if problems else 0
        if args.command == "run":
            snapshot = load_snapshot(args.snapshot_id)
            generator, verifier = providers(args)
            artifact = run(snapshot, generator, verifier)
            directory = write_artifact(artifact, args.out)
            print(f"{artifact['status']}: {artifact['statusReason']}")
            print(f"wrote {directory}/draft.json and review.md")
            return 0
        if args.command == "eval":
            def for_case(case, prefix):
                if args.script_dir:
                    script = json.loads((pathlib.Path(args.script_dir) / f"{case['id']}.json").read_text(encoding="utf-8"))
                    return danak_ai.FakeProvider(script)
                return danak_ai.provider_from_env(prefix)
            summary = run_eval(args.cases, args.out, lambda c: for_case(c, "DANAK_AI"), lambda c: for_case(c, "DANAK_VERIFIER"))
            for r in summary["cases"]:
                print(f"{r['case']:28} {r['status']:24} claims={r['claims']} verdicts={r['verdicts']} suspect={[s['outcome'] for s in r['suspect']]}")
            print(f"wrote {args.out}/summary.md")
            return 0
    except danak_ai.ProviderError as e:
        print(f"provider configuration: {e.message}", file=sys.stderr)
        return 2
    return 1


if __name__ == "__main__":
    sys.exit(main())
