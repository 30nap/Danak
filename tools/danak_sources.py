#!/usr/bin/env python3
"""
Danak source ingestion: turns an approved source into an immutable, auditable snapshot of
exactly the text a later step may read.

    python3 tools/danak_sources.py wikipedia fa "اثر زیگارنیک"
    python3 tools/danak_sources.py wikipedia en https://en.wikipedia.org/wiki/Placebo
    python3 tools/danak_sources.py url https://example.org/some/article
    python3 tools/danak_sources.py batch sources/requests.txt
    python3 tools/danak_sources.py list
    python3 tools/danak_sources.py show <snapshotId>
    python3 tools/danak_sources.py validate
    python3 tools/danak_sources.py check-immutable --base <git-ref>

Needs: pip install jsonschema pyyaml

Only sources listed in content/sources.yml can be ingested, one explicit page at a time;
nothing is crawled or discovered. Every request goes through SafeFetcher: https only, the
host must be allowlisted, every address it resolves to must be public, the connection is
pinned to the address that was checked, redirects are re-checked hop by hop, and responses
are capped in size and time. Source text is data: it is extracted and normalised
deterministically, never interpreted.
"""
import argparse
import datetime
import hashlib
import html.parser
import http.client
import ipaddress
import json
import pathlib
import re
import socket
import ssl
import subprocess
import sys
import unicodedata
import urllib.parse

import jsonschema
import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES_FILE = ROOT / "content" / "sources.yml"
SNAPSHOT_DIR = ROOT / "sources" / "snapshots"
SCHEMA_FILE = ROOT / "schema" / "source-snapshot-v1.schema.json"

USER_AGENT = "DanakIngest/1.0 (+https://github.com/30nap/Danak; source snapshots for review)"
TIMEOUT_SECONDS = 20
MAX_REDIRECTS = 3
LIMITS = {"json": 2 * 1024 * 1024, "wiki-html": 12 * 1024 * 1024, "web-html": 3 * 1024 * 1024}
HTML_TYPES = ("text/html", "application/xhtml+xml")

WIKIPEDIA_EXTRACTOR = "wikipedia-parsoid/1"
WEB_EXTRACTOR = "web-html/1"


class IngestError(Exception):
    """Ingestion refused. [code] is stable, for tests and logs."""

    def __init__(self, code, message):
        super().__init__(f"[{code}] {message}")
        self.code = code


# ============================================================================ policy

class Policy:
    """content/sources.yml, read-only. The allowlist and what each source permits."""

    def __init__(self, path=SOURCES_FILE):
        data = yaml.safe_load(pathlib.Path(path).read_text(encoding="utf-8")) or {}
        self.sources = data.get("text") or []

    def source_for_host(self, host):
        return next((s for s in self.sources if host in (s.get("hosts") or [])), None)

    def wikipedia(self, language):
        return next(
            (s for s in self.sources if s.get("ingest") == "wikipedia" and s.get("language") == language),
            None,
        )

    def all_hosts(self):
        return {h for s in self.sources for h in (s.get("hosts") or [])}


# ============================================================================ network

class Response:
    def __init__(self, url, status, headers, body):
        self.url, self.status, self.headers, self.body = url, status, headers, body

    @property
    def content_type(self):
        return self.headers.get("content-type", "").split(";")[0].strip().lower()


def system_resolver(host):
    """Every address the host resolves to (the check must pass for all of them)."""
    try:
        infos = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
    except socket.gaierror as e:
        raise IngestError("network", f"cannot resolve {host}: {e}")
    return sorted({info[4][0] for info in infos})


def pinned_https_transport(ip, host, target, headers, max_bytes):
    """One HTTPS request to [ip], with TLS verified for [host]. Connecting to the address
    that was checked (not resolving again) closes the DNS-rebinding gap."""

    class PinnedConnection(http.client.HTTPSConnection):
        def connect(self):
            sock = socket.create_connection((ip, 443), timeout=TIMEOUT_SECONDS)
            self.sock = self._context.wrap_socket(sock, server_hostname=host)

    connection = PinnedConnection(host, 443, timeout=TIMEOUT_SECONDS, context=ssl.create_default_context())
    try:
        connection.request("GET", target, headers=headers)
        response = connection.getresponse()
        body = read_limited(response, max_bytes, f"{host}{target}")
        return response.status, {k.lower(): v for k, v in response.getheaders()}, body
    except (OSError, http.client.HTTPException) as e:
        raise IngestError("network", f"{host}{target}: {e}")
    finally:
        connection.close()


def read_limited(response, max_bytes, label):
    """The body, refused as soon as it is known to be over [max_bytes]: from the declared
    length if there is one, and by reading one byte past the limit in any case."""
    declared = response.getheader("Content-Length")
    if declared and declared.isdigit() and int(declared) > max_bytes:
        raise IngestError("too-large", f"{label}: {declared} bytes declared")
    body = response.read(max_bytes + 1)
    if len(body) > max_bytes:
        raise IngestError("too-large", f"{label}: more than {max_bytes} bytes")
    return body


def public_address(ip):
    address = ipaddress.ip_address(ip)
    if isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped:
        address = address.ipv4_mapped
    return address.is_global and not (
        address.is_private or address.is_loopback or address.is_link_local
        or address.is_multicast or address.is_reserved or address.is_unspecified
    )


class SafeFetcher:
    """GETs allowlisted https URLs only. [resolver] and [transport] are swappable so tests
    run without any network."""

    def __init__(self, allowed_hosts, resolver=system_resolver, transport=pinned_https_transport):
        self.allowed_hosts = set(allowed_hosts)
        self.resolver = resolver
        self.transport = transport

    def check_url(self, url):
        """Returns (host, target) for a URL that may be requested, or raises."""
        parts = urllib.parse.urlsplit(url)
        if parts.scheme != "https":
            raise IngestError("not-https", f"{url}: only https is allowed")
        if parts.username or parts.password:
            raise IngestError("credentials", f"{url}: credentials in the URL")
        try:
            port = parts.port
        except ValueError:
            raise IngestError("bad-url", f"{url}: bad port")
        if port not in (None, 443):
            raise IngestError("port", f"{url}: only the default https port")
        host = (parts.hostname or "").lower()
        if not host:
            raise IngestError("bad-url", f"{url}: no host")
        try:
            ipaddress.ip_address(host)
            raise IngestError("ip-literal", f"{url}: addresses are not sources")
        except ValueError:
            pass
        if host not in self.allowed_hosts:
            raise IngestError("not-allowlisted", f"{host} is not a host in sources.yml")
        target = urllib.parse.urlunsplit(("", "", parts.path or "/", parts.query, ""))
        return host, target

    def get(self, url, accept, max_bytes):
        for _ in range(MAX_REDIRECTS + 1):
            host, target = self.check_url(url)
            addresses = self.resolver(host)
            if not addresses:
                raise IngestError("network", f"{host} has no address")
            for ip in addresses:
                if not public_address(ip):
                    raise IngestError("private-address", f"{host} resolves to {ip}")
            headers = {"User-Agent": USER_AGENT, "Accept": ", ".join(accept), "Accept-Encoding": "identity"}
            status, response_headers, body = self.transport(addresses[0], host, target, headers, max_bytes)
            if status in (301, 302, 303, 307, 308):
                location = response_headers.get("location")
                if not location:
                    raise IngestError("http", f"{url}: redirect without a location")
                url = urllib.parse.urljoin(url, location)  # re-checked on the next turn
                continue
            if status != 200:
                raise IngestError("http", f"{url}: HTTP {status}")
            response = Response(url, status, response_headers, body)
            if response.content_type not in accept:
                raise IngestError("content-type", f"{url}: {response.content_type or 'no content type'}")
            return response
        raise IngestError("redirects", f"more than {MAX_REDIRECTS} redirects")


# ============================================================================ html

VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}


class Node:
    __slots__ = ("tag", "attrs", "children", "parent")

    def __init__(self, tag, attrs=None, parent=None):
        self.tag, self.attrs, self.children, self.parent = tag, dict(attrs or {}), [], parent

    def classes(self):
        return set((self.attrs.get("class") or "").split())

    def find_all(self, tag):
        for child in self.children:
            if isinstance(child, Node):
                if child.tag == tag:
                    yield child
                yield from child.find_all(tag)


# Start tags that close an open <p>, as browsers do, so unclosed paragraphs stay separate.
CLOSES_P = {
    "address", "article", "aside", "blockquote", "div", "dl", "fieldset", "figure", "footer", "form",
    "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "main", "nav", "ol", "p", "pre", "section",
    "table", "ul",
}


class TreeBuilder(html.parser.HTMLParser):
    """A forgiving DOM: unknown or unclosed tags never fail. Like a browser, a block start
    closes an open <p>, and a new <li>, <dt> or <dd> closes the previous one."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Node("#root")
        self.current = self.root

    def close_open(self, tags, stop_at):
        node = self.current
        while node is not None and node.tag not in stop_at:
            if node.tag in tags:
                self.current = node.parent
                return
            node = node.parent

    def handle_starttag(self, tag, attrs):
        if tag in CLOSES_P:
            self.close_open({"p"}, stop_at={"#root", "body", "div", "section", "article", "main", "td", "li", "blockquote"})
        if tag == "li":
            self.close_open({"li"}, stop_at={"ul", "ol", "#root"})
        if tag in ("dt", "dd"):
            self.close_open({"dt", "dd"}, stop_at={"dl", "#root"})
        node = Node(tag, [(k, v or "") for k, v in attrs], self.current)
        self.current.children.append(node)
        if tag not in VOID:
            self.current = node

    def handle_startendtag(self, tag, attrs):
        self.current.children.append(Node(tag, [(k, v or "") for k, v in attrs], self.current))

    def handle_endtag(self, tag):
        node = self.current
        while node is not None and node.tag != tag:
            node = node.parent
        if node is not None and node.parent is not None:
            self.current = node.parent

    def handle_data(self, data):
        self.current.children.append(data)


def parse_html(text):
    builder = TreeBuilder()
    builder.feed(text)
    builder.close()
    return builder.root


# Characters that change how text looks or reads without being text: zero-width space,
# joiner, marks and overrides of direction, BOM, soft hyphen. The half-space (ZWNJ, U+200C)
# is part of Persian spelling and is kept.
INVISIBLE = re.compile("[​‍‎‏‪-‮⁦-⁩﻿­]")
SPACES = re.compile(r"[\s   ]+")


def normalize_text(text):
    """Deterministic and idempotent: NFC, invisible controls removed, whitespace collapsed.
    No spelling, punctuation or wording is changed."""
    text = unicodedata.normalize("NFC", text)
    text = INVISIBLE.sub("", text)
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Cc" or ch in "\t\n\r")
    return SPACES.sub(" ", text).strip()


HEADINGS = {"h1": 1, "h2": 2, "h3": 3, "h4": 4, "h5": 5, "h6": 6}

# Never article text, anywhere.
DROP_TAGS = {
    "script", "style", "noscript", "template", "svg", "canvas", "iframe", "object", "embed",
    "form", "button", "input", "select", "textarea", "nav", "link", "meta", "head",
    "table", "figure", "img", "video", "audio", "picture", "map", "dialog",
}
WEB_DROP_TAGS = DROP_TAGS | {"header", "footer", "aside"}
WEB_DROP_ROLES = {"navigation", "banner", "contentinfo", "complementary", "search", "dialog", "alert"}
WEB_DROP_WORDS = re.compile(
    r"cookie|consent|gdpr|banner|subscribe|newsletter|share|social|related|recommend|promo|"
    r"advert|\bads?\b|comment|breadcrumb|menu|\bnav|footer|sidebar|popup|modal|paywall"
)
WIKI_DROP_CLASSES = {
    "mw-ref", "reference", "mw-references-wrap", "references", "reflist", "navbox", "vertical-navbox",
    "hatnote", "mw-editsection", "noprint", "metadata", "ambox", "mbox-small", "sistersitebox",
    "infobox", "thumb", "gallery", "mw-empty-elt", "shortdescription", "toc", "portal", "sidebar",
    "mw-cite-backlink", "catlinks",
}
STOP_HEADINGS = {
    # Persian
    "منابع", "پانویس", "پانوشت", "پانویس‌ها", "جستارهای وابسته", "پیوند به بیرون", "پیوند به‌بیرون",
    "کتاب‌شناسی", "یادداشت‌ها", "برای مطالعهٔ بیشتر", "مطالعهٔ بیشتر", "برای مطالعه بیشتر",
    "ارجاعات", "پی‌نوشت", "پی‌نوشت‌ها", "منبع", "منابع و پانویس", "یادداشت",
    # English
    "references", "notes", "see also", "external links", "further reading", "bibliography",
    "sources", "citations", "footnotes", "notes and references", "works cited", "general references",
}


def dropped(node, mode):
    if node.tag in (WEB_DROP_TAGS if mode == "web" else DROP_TAGS):
        return True
    style = node.attrs.get("style", "").replace(" ", "").lower()
    if "display:none" in style and "mwe-math-element" not in node.classes():
        return True
    if node.attrs.get("hidden") is not None or node.attrs.get("aria-hidden") == "true":
        return True
    if mode == "wikipedia":
        return bool(node.classes() & WIKI_DROP_CLASSES) or node.attrs.get("typeof", "").startswith("mw:Extension/ref")
    role = node.attrs.get("role", "").lower()
    if role in WEB_DROP_ROLES:
        return True
    marker = " ".join([node.attrs.get("class", ""), node.attrs.get("id", "")]).lower()
    return bool(marker) and bool(WEB_DROP_WORDS.search(marker))


def inline_text(node, mode):
    """The text of an inline run, as a reader sees it."""
    if isinstance(node, str):
        return node
    if "mwe-math-element" in node.classes():
        # Wikipedia maths: keep the formula's own text, not its rendering.
        math = next(node.find_all("math"), None)
        formula = (math.attrs.get("alttext") if math else "") or ""
        formula = re.sub(r"^\{\\displaystyle\s*(.*)\}$", r"\1", formula.strip(), flags=re.S)
        return formula
    if dropped(node, mode):
        return ""
    if node.tag == "br":
        return " "
    return "".join(inline_text(child, mode) for child in node.children)


INLINE_TAGS = {
    "a", "span", "b", "i", "em", "strong", "small", "big", "sup", "sub", "font", "code", "abbr",
    "cite", "q", "time", "mark", "u", "s", "bdi", "bdo", "kbd", "var", "data", "label", "wbr", "br",
}
TEXT_BLOCKS = {"p": "paragraph", "blockquote": "quote", "pre": "paragraph", "dd": "paragraph", "dt": "paragraph"}


def has_block_inside(node):
    return any(isinstance(c, Node) and (c.tag not in INLINE_TAGS or has_block_inside(c)) for c in node.children)


def extract_blocks(root, mode):
    """Headings, paragraphs, lists and quotes in document order. Tables, figures, references,
    navigation and site chrome are left out, as are reference-type sections (References,
    See also, …) to their end. Text is only ever normalised, never reworded."""
    blocks = []
    skip_below = None  # level of a heading whose section is being skipped
    pending = []       # inline text waiting to become one paragraph

    def emit(block):
        if skip_below is None:
            blocks.append(block)

    def flush():
        text = normalize_text("".join(pending))
        pending.clear()
        if text:
            emit({"type": "paragraph", "text": text})

    def emit_list(node):
        items = []
        for li in node.children:
            if isinstance(li, Node) and li.tag == "li" and not dropped(li, mode):
                own = [c for c in li.children if not (isinstance(c, Node) and c.tag in ("ul", "ol"))]
                text = normalize_text("".join(inline_text(c, mode) for c in own))
                if text:
                    items.append(text)
        if items:
            emit({"type": "list", "ordered": node.tag == "ol", "items": items})
        # Nested lists follow as lists of their own.
        for li in node.children:
            if isinstance(li, Node):
                for nested in li.children:
                    if isinstance(nested, Node) and nested.tag in ("ul", "ol") and not dropped(nested, mode):
                        emit_list(nested)

    def walk(node):
        nonlocal skip_below
        for child in node.children:
            if isinstance(child, str):
                pending.append(child)
                continue
            if dropped(child, mode):
                continue
            tag = child.tag
            if tag in INLINE_TAGS and not has_block_inside(child):
                pending.append(inline_text(child, mode))
                continue
            flush()
            if tag in HEADINGS:
                level = HEADINGS[tag]
                text = normalize_text(inline_text(child, mode))
                if skip_below is not None and level <= skip_below:
                    skip_below = None
                if level >= 2 and text.lower() in STOP_HEADINGS:
                    skip_below = level
                elif text:
                    emit({"type": "heading", "level": level, "text": text})
            elif tag in TEXT_BLOCKS:
                text = normalize_text(inline_text(child, mode))
                if text:
                    emit({"type": TEXT_BLOCKS[tag], "text": text})
            elif tag in ("ul", "ol"):
                emit_list(child)
            else:
                walk(child)
                flush()
        flush()

    walk(root)
    return blocks


def content_sha256(blocks):
    canonical = json.dumps(blocks, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


# ============================================================================ snapshots

def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")


def license_state(from_source_license, source):
    """(status, effective licence, reasons it cannot back a Danak)."""
    configured, configured_url = source.get("license"), source.get("licenseUrl")
    reasons = []
    if from_source_license and from_source_license.get("url"):
        status = "explicit"
        declared = from_source_license["url"].rstrip("/").replace("http://", "https://")
        if configured_url and declared == configured_url.rstrip("/"):
            effective = configured
        else:
            effective = from_source_license.get("text") or from_source_license["url"]
            reasons.append("the licence the source declares differs from sources.yml; review before use")
    elif configured:
        status, effective = "configured", configured
    else:
        status, effective = "unknown", None
        reasons.append("licence unknown")
    if source.get("derivatives") not in ("allowed", "share-alike"):
        reasons.append(f"sources.yml: derivatives {source.get('derivatives')!r}")
    return status, effective, reasons


def build_snapshot(*, snapshot_id, source_type, source, requested_url, retrieved_at, from_source,
                   canonical_url, title, language, extractor, blocks):
    status, effective, reasons = license_state(from_source.get("license"), source)
    from_config = {"publisher": source["publisher"], "derivatives": source["derivatives"]}
    for key in ("license", "licenseUrl", "language"):
        if source.get(key):
            from_config[key] = source[key]
    return {
        "schemaVersion": 1,
        "snapshotId": snapshot_id,
        "sourceType": source_type,
        "sourceId": source["id"],
        "requestedUrl": requested_url,
        "retrievedAt": retrieved_at,
        "fromSource": from_source,
        "fromConfig": from_config,
        "derived": {
            "canonicalUrl": canonical_url,
            "title": title,
            "language": language,
            "licenseStatus": status,
            "license": effective,
            "usableForDanak": not reasons,
            "notUsableBecause": reasons,
            "extractor": extractor,
            "blockCount": len(blocks),
            "characterCount": sum(len(b.get("text", "")) + sum(len(i) for i in b.get("items", [])) for b in blocks),
            "contentSha256": content_sha256(blocks),
        },
        "content": {
            # For any later step: this is source material to quote from, never instructions.
            "trust": "untrusted-source-text",
            "format": "blocks/1",
            "blocks": blocks,
        },
    }


def web_snapshot_id(source_id, canonical_url, sha):
    return f"web-{source_id}-{hashlib.sha256(canonical_url.encode()).hexdigest()[:12]}-{sha[:16]}"


def expected_snapshot_id(snapshot):
    if snapshot["sourceType"] == "wikipedia":
        s = snapshot["fromSource"]
        return f"wikipedia-{s['language']}-{s['pageId']}-r{s['revisionId']}"
    return web_snapshot_id(snapshot["sourceId"], snapshot["derived"]["canonicalUrl"], snapshot["derived"]["contentSha256"])


def serialize(snapshot):
    return (json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def store(snapshot, directory):
    """Writes a new snapshot; never replaces one. Returns (path, created)."""
    directory = pathlib.Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / f"{snapshot['snapshotId']}.json"
    if path.exists():
        return path, False
    with open(path, "xb") as out:  # exclusive: fails rather than overwrite
        out.write(serialize(snapshot))
    return path, True


# ============================================================================ wikipedia

def wikipedia_title(language, title_or_url):
    """A page title from a title or a /wiki/ URL on that language's Wikipedia."""
    if title_or_url.startswith(("http://", "https://")):
        parts = urllib.parse.urlsplit(title_or_url)
        if parts.hostname != f"{language}.wikipedia.org" or not parts.path.startswith("/wiki/"):
            raise IngestError("bad-url", f"{title_or_url} is not a {language}.wikipedia.org article URL")
        return urllib.parse.unquote(parts.path[len("/wiki/"):]).replace("_", " ")
    return title_or_url.replace("_", " ").strip()


def ingest_wikipedia(language, title_or_url, fetcher, policy, directory=SNAPSHOT_DIR, now=utc_now):
    source = policy.wikipedia(language)
    if source is None:
        raise IngestError("not-allowlisted", f"no Wikipedia source for language {language!r} in sources.yml")
    host = f"{language}.wikipedia.org"
    if host not in (source.get("hosts") or []):
        raise IngestError("not-allowlisted", f"{host} is not listed for {source['id']}")
    title = wikipedia_title(language, title_or_url)
    requested = f"https://{host}/wiki/{urllib.parse.quote(title.replace(' ', '_'))}"

    query = urllib.parse.urlencode({
        "action": "query", "format": "json", "formatversion": "2", "redirects": "1",
        "titles": title, "prop": "info|revisions|pageprops", "inprop": "url",
        "rvprop": "ids|timestamp", "ppprop": "wikibase_item|disambiguation",
        "meta": "siteinfo", "siprop": "rightsinfo",
    })
    info = fetcher.get(f"https://{host}/w/api.php?{query}", ("application/json",), LIMITS["json"])
    try:
        data = json.loads(info.body)
        page = data["query"]["pages"][0]
    except (ValueError, KeyError, IndexError, TypeError):
        raise IngestError("bad-response", "the Wikipedia API answer is not what was expected")
    if page.get("missing") or page.get("invalid"):
        raise IngestError("missing", f"{language}.wikipedia.org has no page {title!r}")
    if page.get("ns") != 0:
        raise IngestError("not-article", f"{page.get('title')!r} is not an article")
    if "disambiguation" in (page.get("pageprops") or {}):
        raise IngestError("not-article", f"{page.get('title')!r} is a disambiguation page")
    revisions = page.get("revisions") or []
    if not page.get("title") or not revisions or not isinstance(page.get("pageid"), int):
        raise IngestError("missing-title", "the API returned no title, page id or revision")
    revision = revisions[0]
    rights = (data.get("query") or {}).get("rightsinfo") or {}

    from_source = {
        "title": page["title"],
        "language": page.get("pagelanguage") or language,
        "pageId": page["pageid"],
        "revisionId": revision["revid"],
        "revisionTimestamp": revision.get("timestamp"),
        "canonicalUrl": page.get("canonicalurl"),
        "wikidataId": (page.get("pageprops") or {}).get("wikibase_item"),
        "license": {"url": rights["url"], "text": rights.get("text")} if rights.get("url") else None,
    }
    snapshot_id = f"wikipedia-{from_source['language']}-{from_source['pageId']}-r{from_source['revisionId']}"
    existing = pathlib.Path(directory) / f"{snapshot_id}.json"
    if existing.exists():
        # Same page, same revision: the evidence is already on file.
        return existing, False

    content = fetcher.get(
        f"https://{host}/w/rest.php/v1/revision/{revision['revid']}/html", HTML_TYPES, LIMITS["wiki-html"])
    from_source["contentType"] = content.headers.get("content-type")
    blocks = extract_blocks(parse_html(content.body.decode("utf-8", errors="replace")), "wikipedia")
    if not any(b["type"] in ("paragraph", "list", "quote") for b in blocks):
        raise IngestError("no-text", f"no article text extracted from {page['title']!r}")

    canonical = from_source["canonicalUrl"] or requested
    snapshot = build_snapshot(
        snapshot_id=snapshot_id, source_type="wikipedia", source=source, requested_url=requested,
        retrieved_at=now(), from_source=from_source, canonical_url=canonical, title=page["title"],
        language=from_source["language"], extractor=WIKIPEDIA_EXTRACTOR, blocks=blocks,
    )
    return store(snapshot, directory)


# ============================================================================ web

def meta(root, **match):
    for node in root.find_all("meta"):
        if all(node.attrs.get(k, "").lower() == v for k, v in match.items()):
            return node.attrs.get("content")
    return None


def link_href(root, rel):
    for node in root.find_all("link"):
        if rel in node.attrs.get("rel", "").lower().split():
            return node.attrs.get("href")
    return None


def main_content(root):
    """The article: an <article> (the one with most text), else <main>, else role=main,
    else the body."""
    def text_length(node):
        return len(normalize_text(inline_text(node, "web")))

    articles = list(root.find_all("article"))
    if articles:
        return max(articles, key=text_length)
    for node in root.find_all("main"):
        return node
    stack = [root]
    while stack:
        node = stack.pop()
        for child in node.children:
            if isinstance(child, Node):
                if child.attrs.get("role") == "main":
                    return child
                stack.append(child)
    return next(root.find_all("body"), root)


def ingest_url(url, fetcher, policy, directory=SNAPSHOT_DIR, now=utc_now):
    host = (urllib.parse.urlsplit(url).hostname or "").lower()
    source = policy.source_for_host(host)
    if source is None:
        # The fetcher refuses it too; this gives the clearer message.
        raise IngestError("not-allowlisted", f"{host or url} is not a host in sources.yml")
    if source.get("ingest") == "wikipedia":
        raise IngestError("use-wikipedia", f"{host} is ingested with the wikipedia command")
    response = fetcher.get(url, HTML_TYPES, LIMITS["web-html"])
    charset = re.search(r"charset=([\w-]+)", response.headers.get("content-type", ""))
    text = response.body.decode(charset.group(1) if charset else "utf-8", errors="replace")
    root = parse_html(text)

    title_node = next(root.find_all("title"), None)
    h1 = next(main_content(root).find_all("h1"), None)
    title = normalize_text(
        meta(root, property="og:title") or (inline_text(title_node, "web") if title_node else "")
        or (inline_text(h1, "web") if h1 else "")
    )
    if not title:
        raise IngestError("missing-title", f"{url}: no title")

    html_node = next(root.find_all("html"), None)
    declared_language = (html_node.attrs.get("lang") if html_node else None) or None
    license_url = link_href(root, "license")
    canonical_declared = link_href(root, "canonical")
    canonical = response.url
    if canonical_declared:
        candidate = urllib.parse.urljoin(response.url, canonical_declared)
        try:
            candidate_host, _ = fetcher.check_url(candidate)
            if policy.source_for_host(candidate_host) is source:
                canonical = candidate
        except IngestError:
            pass  # a canonical link that points elsewhere is ignored, not followed

    blocks = extract_blocks(main_content(root), "web")
    if not any(b["type"] in ("paragraph", "list", "quote") for b in blocks):
        raise IngestError("no-text", f"{url}: no article text")
    from_source = {
        "title": title,
        "language": declared_language,
        "canonicalUrl": urllib.parse.urljoin(response.url, canonical_declared) if canonical_declared else None,
        "finalUrl": response.url,
        "contentType": response.headers.get("content-type"),
        "license": {"url": urllib.parse.urljoin(response.url, license_url), "text": None} if license_url else None,
    }
    sha = content_sha256(blocks)
    snapshot = build_snapshot(
        snapshot_id=web_snapshot_id(source["id"], canonical, sha), source_type="web", source=source,
        requested_url=url, retrieved_at=now(), from_source=from_source, canonical_url=canonical,
        title=title, language=declared_language or source.get("language"), extractor=WEB_EXTRACTOR,
        blocks=blocks,
    )
    return store(snapshot, directory)


# ============================================================================ validation

def validate(directory=SNAPSHOT_DIR):
    """Every stored snapshot: schema, file name, id rule and content hash."""
    schema = json.loads(SCHEMA_FILE.read_text(encoding="utf-8"))
    validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
    problems, count = [], 0
    for path in sorted(pathlib.Path(directory).glob("*")):
        count += 1
        if path.is_symlink() or not path.is_file() or path.suffix != ".json":
            problems.append(f"{path.name}: only snapshot .json files belong here")
            continue
        try:
            snapshot = json.loads(path.read_text(encoding="utf-8"))
        except (UnicodeDecodeError, ValueError) as e:
            problems.append(f"{path.name}: not JSON ({e})")
            continue
        errors = list(validator.iter_errors(snapshot))
        problems += [f"{path.name}: {'/'.join(map(str, e.absolute_path)) or '(root)'}: {e.message}" for e in errors]
        if errors:
            continue
        if path.stem != snapshot["snapshotId"]:
            problems.append(f"{path.name}: file name does not match snapshotId")
        if content_sha256(snapshot["content"]["blocks"]) != snapshot["derived"]["contentSha256"]:
            problems.append(f"{path.name}: content does not match contentSha256")
        if expected_snapshot_id(snapshot) != snapshot["snapshotId"]:
            problems.append(f"{path.name}: snapshotId does not follow from its metadata")
        if snapshot["derived"]["usableForDanak"] == bool(snapshot["derived"]["notUsableBecause"]):
            problems.append(f"{path.name}: usableForDanak disagrees with notUsableBecause")
    return count, problems


def check_immutable(base, directory=SNAPSHOT_DIR):
    """Snapshots may be added, never changed, renamed or deleted."""
    relative = pathlib.Path(directory).resolve().relative_to(ROOT)
    out = subprocess.run(
        ["git", "diff", "--name-status", "--no-renames", base, "--", str(relative)],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout
    return [line for line in out.splitlines() if line and not line.startswith("A\t")]


# ============================================================================ cli

def default_fetcher(policy):
    return SafeFetcher(policy.all_hosts())


def run_request(kind, args, fetcher, policy, directory):
    if kind == "wikipedia" and len(args) == 2:
        return ingest_wikipedia(args[0], args[1], fetcher, policy, directory)
    if kind == "url" and len(args) == 1:
        return ingest_url(args[0], fetcher, policy, directory)
    raise IngestError("bad-request", f"cannot understand: {kind} {' '.join(args)}")


def describe(path):
    snapshot = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    d, s = snapshot["derived"], snapshot["fromSource"]
    extra = f" rev {s['revisionId']} {s.get('wikidataId') or 'no QID'}" if snapshot["sourceType"] == "wikipedia" else ""
    return (f"{snapshot['snapshotId']}  [{d['language']}] {d['title']}{extra}  "
            f"licence={d['licenseStatus']}:{d['license']}  usable={d['usableForDanak']}  "
            f"{d['blockCount']} blocks, {d['characterCount']} chars")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Danak source snapshots")
    parser.add_argument("--dir", default=str(SNAPSHOT_DIR))
    sub = parser.add_subparsers(dest="command", required=True)
    wiki = sub.add_parser("wikipedia", help="snapshot one Wikipedia article")
    wiki.add_argument("language", choices=["fa", "en"])
    wiki.add_argument("page", help="title or https://<lang>.wikipedia.org/wiki/... URL")
    url = sub.add_parser("url", help="snapshot one page of an allowlisted web source")
    url.add_argument("url")
    batch = sub.add_parser("batch", help="snapshot every request in a file")
    batch.add_argument("file")
    sub.add_parser("list")
    show = sub.add_parser("show")
    show.add_argument("snapshot_id")
    sub.add_parser("validate")
    immutable = sub.add_parser("check-immutable")
    immutable.add_argument("--base", required=True)
    args = parser.parse_args(argv)
    directory = pathlib.Path(args.dir)

    if args.command in ("wikipedia", "url", "batch"):
        policy = Policy()
        fetcher = default_fetcher(policy)
        if args.command == "batch":
            requests = []
            for line in pathlib.Path(args.file).read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#"):
                    kind, _, rest = line.partition(" ")
                    requests.append((kind, rest.split(" ", 1) if kind == "wikipedia" else [rest.strip()]))
        else:
            requests = [(args.command, [args.language, args.page] if args.command == "wikipedia" else [args.url])]
        failed = 0
        for kind, request_args in requests:
            try:
                path, created = run_request(kind, [a.strip() for a in request_args], fetcher, policy, directory)
                print(("NEW  " if created else "SAME ") + describe(path))
            except IngestError as e:
                failed += 1
                print(f"FAIL {kind} {' '.join(request_args)}: {e}")
        return 1 if failed else 0
    if args.command == "list":
        for path in sorted(directory.glob("*.json")):
            print(describe(path))
        return 0
    if args.command == "show":
        snapshot = json.loads((directory / f"{args.snapshot_id}.json").read_text(encoding="utf-8"))
        print(describe(directory / f"{args.snapshot_id}.json"))
        print(json.dumps({k: snapshot[k] for k in ("fromSource", "fromConfig")}, ensure_ascii=False, indent=2))
        for block in snapshot["content"]["blocks"]:
            if block["type"] == "heading":
                print("\n" + "#" * block["level"] + " " + block["text"])
            elif block["type"] == "list":
                print("\n".join(("1. " if block["ordered"] else "- ") + item for item in block["items"]))
            else:
                print(("> " if block["type"] == "quote" else "") + block["text"])
        return 0
    if args.command == "validate":
        count, problems = validate(directory)
        for p in problems:
            print("FAIL", p)
        print(f"{count} snapshots, {len(problems)} problem(s)")
        return 1 if problems else 0
    if args.command == "check-immutable":
        changed = check_immutable(args.base, directory)
        for line in changed:
            print("FAIL changed or removed snapshot:", line)
        print(f"{len(changed)} existing snapshot(s) changed")
        return 1 if changed else 0
    return 2


if __name__ == "__main__":
    sys.exit(main())
