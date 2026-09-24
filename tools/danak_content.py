#!/usr/bin/env python3
"""
Danak content tool: validates content/, builds the static site GitHub Pages serves, and
checks that the pack bundled in the app is an exact copy of reviewed content.

    python3 tools/danak_content.py validate      [--content DIR]
    python3 tools/danak_content.py build --out DIR [--content DIR]
    python3 tools/danak_content.py check-bundle  [--content DIR] [--bundle DIR]

Needs: pip install jsonschema pyyaml

Everything under content/ is treated as untrusted input: file names, paths, URLs and text
are all checked, symlinks are refused, and the build writes only files it generates itself
into a directory that must not exist yet — nothing is copied wholesale, so nothing else in
the repository can end up published.
"""
import argparse
import hashlib
import json
import pathlib
import re
import sys
import unicodedata
import urllib.parse

import jsonschema
import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCHEMA_DIR = ROOT / "schema"
DEFAULT_CONTENT = ROOT / "content"
DEFAULT_BUNDLE = ROOT / "app" / "src" / "main" / "assets" / "content"

DANAK_FILE = re.compile(r"^(\d{4})-([a-z0-9]+(?:_[a-z0-9]+)*)\.json$")
IMAGE_SRC = re.compile(r"^images/([a-z0-9]+(?:_[a-z0-9]+)*)\.(webp|jpg|png)$")
MAX_JSON_BYTES = 64 * 1024
MAX_IMAGE_BYTES = 1536 * 1024
IMAGE_MAGIC = {
    "webp": lambda b: b[:4] == b"RIFF" and b[8:12] == b"WEBP",
    "jpg": lambda b: b[:3] == b"\xff\xd8\xff",
    "png": lambda b: b[:8] == b"\x89PNG\r\n\x1a\n",
}


class Problem:
    def __init__(self, code, where, message):
        self.code, self.where, self.message = code, where, message

    def __str__(self):
        return f"[{self.code}] {self.where}: {self.message}"


# --------------------------------------------------------------------------- loading

def load_schema(name):
    return json.loads((SCHEMA_DIR / name).read_text(encoding="utf-8"))


def load_sources(content, problems):
    """Reads sources.yml and checks its shape: a typo here must not quietly switch off the
    trust checks that depend on it."""
    data = yaml.safe_load((content / "sources.yml").read_text(encoding="utf-8")) or {}
    text, images = data.get("text") or [], data.get("images") or []
    for i, s in enumerate(text):
        missing = [k for k in ("id", "publisher", "hosts", "license", "licenseUrl", "derivatives") if not s.get(k)]
        if missing:
            problems.append(Problem("sources", f"sources.yml text[{i}]", f"missing {', '.join(missing)}"))
        if s.get("derivatives") not in (None, "allowed", "share-alike", "none"):
            problems.append(Problem("sources", f"sources.yml text[{i}]", "derivatives must be allowed, share-alike or none"))
    for i, s in enumerate(images):
        missing = [k for k in ("id", "hosts", "pageUrlPrefix", "licenses") if not s.get(k)]
        if missing:
            problems.append(Problem("sources", f"sources.yml images[{i}]", f"missing {', '.join(missing)}"))
        elif not str(s["pageUrlPrefix"]).startswith("https://"):
            problems.append(Problem("sources", f"sources.yml images[{i}]", "pageUrlPrefix must be https"))
    hosts = [h for s in text for h in s.get("hosts", [])]
    for host in sorted({h for h in hosts if hosts.count(h) > 1}):
        problems.append(Problem("sources", "sources.yml", f"host {host} belongs to two sources"))
    return text, images


def is_inside(path, parent):
    try:
        path.resolve().relative_to(parent.resolve())
        return True
    except ValueError:
        return False


def list_danak_files(content, problems):
    """Returns [(number, id, path)] in feed order; reports anything unexpected."""
    found = []
    danak_dir = content / "danaks"
    for path in sorted(danak_dir.iterdir()):
        rel = f"danaks/{path.name}"
        if path.is_symlink() or not path.is_file():
            problems.append(Problem("unsafe-path", rel, "only regular files are allowed here"))
            continue
        match = DANAK_FILE.match(path.name)
        if not match:
            problems.append(Problem("filename", rel, "expected NNNN-<id>.json"))
            continue
        found.append((match.group(1), match.group(2), path))
    numbers = [n for n, _, _ in found]
    for number in sorted({n for n in numbers if numbers.count(n) > 1}):
        problems.append(Problem("filename", f"danaks/{number}-*", "two files share this position"))
    return found


# ------------------------------------------------------------------------ text rules

ARABIC_SCRIPT = re.compile(r"[؀-ۿ]")
# Arabic yeh and kaf look like Persian ی and ک but are different letters to search, sort and
# the keyboard; Arabic-Indic digits likewise are not Persian digits.
WRONG_LETTERS = {"ي": "ي (Arabic yeh; use ی)", "ك": "ك (Arabic kaf; use ک)"}
INVISIBLE = {
    "​": "zero-width space", "‍": "zero-width joiner", "﻿": "byte-order mark",
    "‎": "left-to-right mark", "‏": "right-to-left mark", "­": "soft hyphen",
    " ": "no-break space", " ": "line separator", " ": "paragraph separator",
}
BIDI_CONTROLS = re.compile(r"[‪-‮⁦-⁩]")
ZWNJ_MISUSE = re.compile(r"(^‌|‌$|‌\s|\s‌|‌‌)")
LATIN_PUNCT_IN_PERSIAN = re.compile(r"[؀-ۿ][,;?]|[,;?][؀-ۿ]")
SPACE_BEFORE_PUNCT = re.compile(r"\s[،؛؟.!:»)]")
SENTENCE_END = ".؟!»"


def text_problems(text, field):
    """Plain, checkable rules only; no attempt at judging style."""
    found = []
    if text != text.strip():
        found.append("leading or trailing whitespace")
    if "  " in text:
        found.append("double space")
    if re.search(r"[\t\r\n]", text):
        found.append("line break or tab inside a single paragraph")
    for ch, name in {**WRONG_LETTERS, **INVISIBLE}.items():
        if ch in text:
            found.append(f"contains {name}")
    if re.search(r"[٠-٩]", text):
        found.append("Arabic-Indic digits (use Persian ۰-۹)")
    if BIDI_CONTROLS.search(text):
        found.append("bidirectional control character")
    if any(unicodedata.category(c) == "Cc" for c in text):
        found.append("control character")
    if ZWNJ_MISUSE.search(text):
        found.append("half-space (ZWNJ) at a word edge or doubled")
    if LATIN_PUNCT_IN_PERSIAN.search(text):
        found.append("Latin , ; or ? next to Persian text (use ، ؛ ؟)")
    if SPACE_BEFORE_PUNCT.search(text):
        found.append("space before punctuation")
    letters = [c for c in text if c.isalpha()]
    if letters and sum(bool(ARABIC_SCRIPT.match(c)) for c in letters) / len(letters) < 0.6:
        found.append("not mainly Persian")
    if field == "title" and text[-1:] in ".،؛:":
        found.append("title ends with punctuation other than ؟")
    if field in ("summary", "keyTakeaway", "body") and text[-1:] not in SENTENCE_END:
        found.append("does not end a sentence (. ؟ ! »)")
    return found


# ------------------------------------------------------------------------- URL rules

def https_url_problem(url):
    parts = urllib.parse.urlsplit(url)
    if parts.scheme != "https":
        return "not https"
    if not parts.hostname or parts.username or parts.password or parts.port:
        return "needs a plain host (no credentials or port)"
    if not re.fullmatch(r"[a-z0-9.-]+", parts.hostname):
        return "host must be a plain ASCII domain"
    return None


def normalized_url(url):
    parts = urllib.parse.urlsplit(url)
    path = urllib.parse.unquote(parts.path).rstrip("/")
    return f"{parts.hostname}{path}".casefold()


def source_for(url, text_sources):
    host = urllib.parse.urlsplit(url).hostname
    return next((s for s in text_sources if host in s.get("hosts", [])), None)


# ----------------------------------------------------------------------- validation

def validate(content):
    """Returns (danaks in feed order, problems). Danaks are the parsed JSON objects."""
    content = pathlib.Path(content)
    problems = []
    schema = load_schema("danak-v1.schema.json")
    validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
    text_sources, image_sources = load_sources(content, problems)

    danaks = []
    for _, file_id, path in list_danak_files(content, problems):
        rel = f"danaks/{path.name}"
        raw = path.read_bytes()
        if len(raw) > MAX_JSON_BYTES:
            problems.append(Problem("json", rel, f"larger than {MAX_JSON_BYTES} bytes"))
            continue
        try:
            danak = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            problems.append(Problem("json", rel, f"not valid UTF-8 JSON: {e}"))
            continue
        # A single Danak is checked as a one-item pack, so the pack schema is the only one.
        errors = list(validator.iter_errors({"schemaVersion": 1, "danaks": [danak]}))
        for e in errors:
            where = "/".join(str(p) for p in list(e.absolute_path)[2:]) or "(danak)"
            problems.append(Problem("schema", f"{rel} {where}", e.message))
        if errors or not isinstance(danak, dict):
            continue
        if danak["id"] != file_id:
            problems.append(Problem("filename", rel, f"file is named for {file_id!r} but id is {danak['id']!r}"))
        danak["_file"] = rel
        danaks.append(danak)

    check_duplicates(danaks, problems)
    referenced = set()
    for danak in danaks:
        check_source(danak, text_sources, problems)
        check_image(danak, content, image_sources, problems, referenced)
        check_text(danak, problems)

    image_dir = content / "images"
    for path in sorted(image_dir.iterdir()):
        rel = f"images/{path.name}"
        if path.is_symlink() or not path.is_file():
            problems.append(Problem("unsafe-path", rel, "only regular files are allowed here"))
        elif rel not in referenced:
            problems.append(Problem("orphan-image", rel, "no Danak uses this image"))

    for danak in danaks:
        danak.pop("_file")
    return danaks, problems


def check_duplicates(danaks, problems):
    def dupes(key):
        seen = {}
        for d in danaks:
            value = key(d)
            if value is not None:
                seen.setdefault(value, []).append(d["_file"])
        return {v: files for v, files in seen.items() if len(files) > 1}

    for value, files in dupes(lambda d: d["id"]).items():
        problems.append(Problem("duplicate-id", ", ".join(files), f"id {value!r} is used more than once"))
    for value, files in dupes(lambda d: d.get("conceptKey", "").strip().casefold() or None).items():
        problems.append(Problem("duplicate-concept", ", ".join(files), f"conceptKey {value!r} is taught twice"))
    for value, files in dupes(lambda d: normalized_url(d["source"]["url"])).items():
        problems.append(Problem("duplicate-source", ", ".join(files), f"source {value!r} is cited twice"))


def check_source(danak, text_sources, problems):
    rel, source = danak["_file"], danak["source"]
    for field, url in (("source.url", source["url"]), ("image.credit.pageUrl", danak["image"]["credit"]["pageUrl"])):
        problem = https_url_problem(url)
        if problem:
            problems.append(Problem("insecure-url", f"{rel} {field}", f"{url!r}: {problem}"))
    trusted = source_for(source["url"], text_sources)
    if trusted is None:
        problems.append(Problem("untrusted-source", rel, f"{source['url']!r} is not a host in sources.yml"))
        return
    if trusted.get("derivatives") not in ("allowed", "share-alike"):
        problems.append(Problem("license", rel, f"{trusted['id']} does not allow rewritten content"))
    if source["publisher"] != trusted["publisher"]:
        problems.append(Problem("source-publisher", rel, f"publisher should be {trusted['publisher']!r}"))
    if trusted.get("license") and source.get("license") != trusted["license"]:
        problems.append(Problem("license", rel, f"source licence must be {trusted['license']!r} (from sources.yml)"))


def check_image(danak, content, image_sources, problems, referenced):
    rel, image = danak["_file"], danak["image"]
    src = image["src"]
    if src.startswith("https://"):
        problems.append(Problem("remote-image", rel, "content must ship its own copy of the image"))
        return
    match = IMAGE_SRC.match(src)
    if not match:
        problems.append(Problem("unsafe-path", rel, f"image path {src!r} must be images/<id>.<webp|jpg|png>"))
        return
    if match.group(1) != danak["id"]:
        problems.append(Problem("image-path", rel, f"image should be named after the Danak: images/{danak['id']}.{match.group(2)}"))
    path = content / src
    referenced.add(src)
    if path.is_symlink() or not is_inside(path, content / "images"):
        problems.append(Problem("unsafe-path", rel, f"{src} is a link or leaves images/"))
        return
    if not path.is_file():
        problems.append(Problem("missing-image", rel, f"{src} does not exist"))
        return
    data = path.read_bytes()
    if len(data) > MAX_IMAGE_BYTES:
        problems.append(Problem("image-size", rel, f"{src} is {len(data) // 1024} KB (max {MAX_IMAGE_BYTES // 1024})"))
    if not IMAGE_MAGIC[match.group(2)](data):
        problems.append(Problem("image-format", rel, f"{src} is not a real .{match.group(2)} file"))

    credit = image["credit"]
    host = urllib.parse.urlsplit(credit["pageUrl"]).hostname
    allowed = next((s for s in image_sources if host in s.get("hosts", [])), None)
    if allowed is None or not credit["pageUrl"].startswith(allowed.get("pageUrlPrefix", "https://")):
        problems.append(Problem("image-license", rel, f"image page {credit['pageUrl']!r} is not an allowed image source"))
    elif not any(re.fullmatch(p, credit["license"], re.IGNORECASE) for p in allowed.get("licenses", [])):
        problems.append(Problem("image-license", rel, f"licence {credit['license']!r} is not allowed for {allowed['id']}"))


def check_text(danak, problems):
    rel = danak["_file"]
    fields = [("title", danak["title"]), ("summary", danak["summary"]), ("keyTakeaway", danak["keyTakeaway"])]
    for i, section in enumerate(danak["sections"]):
        if "heading" in section:
            fields.append((f"sections/{i}/heading", section["heading"]))
        fields.append((f"sections/{i}/body", section["body"]))
    for name, text in fields:
        kind = "body" if name.endswith("/body") else name
        for issue in text_problems(text, kind):
            problems.append(Problem("persian", f"{rel} {name}", issue))


# ---------------------------------------------------------------------------- build

def canonical(obj):
    """Deterministic bytes: the same content always publishes the same files."""
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def build(content, out):
    content, out = pathlib.Path(content), pathlib.Path(out)
    if out.exists():
        raise SystemExit(f"refusing to build into {out}: it already exists (remove it first)")
    if is_inside(out, content) or is_inside(ROOT, out):
        raise SystemExit(f"refusing to build into {out}: it overlaps the repository or content")
    danaks, problems = validate(content)
    if problems:
        for p in problems:
            print("FAIL", p)
        raise SystemExit(f"not building: {len(problems)} problem(s)")

    v1 = out / "v1"
    (v1 / "content").mkdir(parents=True)
    (v1 / "images").mkdir()
    index = {"schemaVersion": 1, "danaks": []}
    for danak in danaks:
        image = (content / danak["image"]["src"]).read_bytes()
        ext = danak["image"]["src"].rsplit(".", 1)[1]
        image_name = f"{danak['id']}_{hashlib.sha256(image).hexdigest()[:8]}.{ext}"
        (v1 / "images" / image_name).write_bytes(image)

        published = json.loads(json.dumps(danak))
        published["image"]["src"] = f"images/{image_name}"
        body = canonical(published)
        digest = hashlib.sha256(body).hexdigest()
        path = f"content/{danak['id']}_{digest[:8]}.json"
        (v1 / path).write_bytes(body)
        index["danaks"].append({"id": danak["id"], "path": path, "sha256": digest})
    (v1 / "index.json").write_bytes(canonical(index))

    # The schemas are published at the URLs in their $id, so the contract is readable too.
    (out / "schema").mkdir()
    for name in ("danak-v1.schema.json", "index-v1.schema.json"):
        (out / "schema" / name).write_bytes((SCHEMA_DIR / name).read_bytes())

    self_check(out)
    print(f"built {len(danaks)} danaks into {out}")


def self_check(out):
    """Re-reads what was written, exactly as a client would, before anything is deployed."""
    v1 = out / "v1"
    index = json.loads((v1 / "index.json").read_text(encoding="utf-8"))
    jsonschema.validate(index, load_schema("index-v1.schema.json"))
    pack_validator = jsonschema.Draft202012Validator(load_schema("danak-v1.schema.json"))
    for entry in index["danaks"]:
        body = (v1 / entry["path"]).read_bytes()
        assert hashlib.sha256(body).hexdigest() == entry["sha256"], entry["path"]
        danak = json.loads(body)
        pack_validator.validate({"schemaVersion": 1, "danaks": [danak]})
        assert (v1 / danak["image"]["src"]).is_file(), danak["image"]["src"]


# --------------------------------------------------------------------- bundle check

def check_bundle(content, bundle):
    """The app's bundled pack must be reviewed content, unchanged: every bundled Danak must
    exist in content/ with identical JSON and image, in the same relative order."""
    danaks, problems = validate(content)
    by_id = {d["id"]: d for d in danaks}
    order = [d["id"] for d in danaks]
    pack = json.loads((pathlib.Path(bundle) / "content.json").read_text(encoding="utf-8"))
    positions = []
    for entry in pack["danaks"]:
        source = by_id.get(entry["id"])
        if source is None:
            problems.append(Problem("bundle-drift", entry["id"], "bundled but not in content/"))
            continue
        if canonical(entry) != canonical(source):
            problems.append(Problem("bundle-drift", entry["id"], "bundled JSON differs from content/"))
        if not IMAGE_SRC.match(entry["image"]["src"]):
            problems.append(Problem("unsafe-path", entry["id"], "bundled image path is not images/<name>.<ext>"))
            continue
        bundled_image = pathlib.Path(bundle) / entry["image"]["src"]
        if bundled_image.read_bytes() != (pathlib.Path(content) / source["image"]["src"]).read_bytes():
            problems.append(Problem("bundle-drift", entry["id"], "bundled image differs from content/"))
        positions.append(order.index(entry["id"]))
    if positions != sorted(positions):
        problems.append(Problem("bundle-drift", "content.json", "bundled order differs from content/ order"))
    return problems


# ---------------------------------------------------------------------------- main

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("command", choices=["validate", "build", "check-bundle"])
    parser.add_argument("--content", default=str(DEFAULT_CONTENT))
    parser.add_argument("--bundle", default=str(DEFAULT_BUNDLE))
    parser.add_argument("--out")
    args = parser.parse_args(argv)

    if args.command == "build":
        if not args.out:
            parser.error("build needs --out")
        build(args.content, args.out)
        return 0
    if args.command == "validate":
        danaks, problems = validate(args.content)
    else:
        problems = check_bundle(args.content, args.bundle)
        danaks = None
    for p in problems:
        print("FAIL", p)
    print(f"{'' if danaks is None else f'{len(danaks)} danaks, '}{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
