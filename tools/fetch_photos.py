#!/usr/bin/env python3
"""
Fetches Danak's hero photos from Wikimedia Commons.

This runs in CI, not locally: the development environment cannot reach Commons, and a CI
job can. Each photo is printed into the job log as base64 between markers, and a local
script decodes the log into app/src/main/assets/content/images.

tools/photos.json drives it, one entry per Danak id:
  {"query": "..."}                         -> candidates mode: print 6 small previews
  {"query": "...", "pick": "File:X.jpg"}   -> final mode: print the chosen photo, cropped
                                              to 9:16 (optional "focus": 0..1 moves the
                                              crop window horizontally)

Only freely licensed files are used (CC0, public domain, CC BY, CC BY-SA), and every
printed photo carries its author and licence so the app can credit it.
"""
import argparse
import base64
import html
import io
import json
import re
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

API = "https://commons.wikimedia.org/w/api.php"
UA = "DanakPhotoFetch/1.0 (+https://github.com/30nap/Danak)"
FREE = re.compile(r"^(CC0|Public domain|PD\b|CC BY(-SA)? \d)", re.I)
OUT_W, OUT_H = 1080, 1920


def get(url, tries=4):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read()
        except Exception as e:  # rate limits and transient network errors
            if attempt == tries - 1:
                raise
            print(f"# retry {url[:80]}: {e}", file=sys.stderr)
            time.sleep(2 ** attempt * 2)


def api(params):
    params = {"format": "json", "formatversion": "2", **params}
    return json.loads(get(API + "?" + urllib.parse.urlencode(params)))


def plain(value):
    return html.unescape(re.sub(r"<[^>]+>", "", value or "")).strip()


def describe(page):
    info = page["imageinfo"][0]
    meta = info.get("extmetadata", {})
    return {
        "title": page["title"],
        "license": plain(meta.get("LicenseShortName", {}).get("value")),
        "artist": plain(meta.get("Artist", {}).get("value")) or "Unknown",
        "width": info["width"],
        "height": info["height"],
        "page": info.get("descriptionurl", ""),
        "thumb": info.get("thumburl"),
        "mime": info.get("mime", ""),
    }


def search(query, width, limit=15):
    data = api({
        "action": "query", "generator": "search", "gsrsearch": query,
        "gsrnamespace": 6, "gsrlimit": limit, "prop": "imageinfo",
        "iiprop": "url|size|extmetadata|mime", "iiurlwidth": width,
    })
    pages = sorted(data.get("query", {}).get("pages", []), key=lambda p: p.get("index", 0))
    return [describe(p) for p in pages if p.get("imageinfo")]


def usable(photo):
    return (photo["mime"] in ("image/jpeg", "image/png", "image/webp")
            and FREE.match(photo["license"])
            and photo["width"] >= 1200 and photo["height"] >= 900)


def emit(danak_id, kind, image, meta):
    buf = io.BytesIO()
    if kind == "candidate":
        image.save(buf, "JPEG", quality=72)
    else:
        image.save(buf, "WEBP", quality=78, method=6)
    print(f"=====META {danak_id} {kind} {json.dumps(meta, ensure_ascii=False)}")
    print(f"=====BEGIN {danak_id}.{kind}.{meta.get('n', 0)}=====")
    print(base64.b64encode(buf.getvalue()).decode())
    print("=====END=====")


def candidates(danak_id, query):
    seen, found = set(), []
    # Commons' curated "Quality images" first; plain search fills the rest.
    for q in (f'{query} incategory:"Quality_images"', query):
        for photo in search(q, 360):
            if photo["title"] not in seen and usable(photo):
                seen.add(photo["title"])
                found.append(photo)
        if len(found) >= 6:
            break
    if not found:
        print(f"=====NONE {danak_id} {query}")
    for n, photo in enumerate(found[:6]):
        img = Image.open(io.BytesIO(get(photo["thumb"]))).convert("RGB")
        img.thumbnail((360, 360))
        emit(danak_id, "candidate", img, {**photo, "n": n, "thumb": None})


def final(danak_id, pick, focus):
    data = api({"action": "query", "titles": pick, "prop": "imageinfo",
                "iiprop": "url|size|extmetadata|mime", "iiurlwidth": 2400})
    photo = describe(data["query"]["pages"][0])
    if not usable(photo):
        raise SystemExit(f"{danak_id}: {pick} is not usable ({photo['license']}, "
                         f"{photo['width']}x{photo['height']})")
    img = Image.open(io.BytesIO(get(photo["thumb"]))).convert("RGB")
    # Crop to 9:16 around the focus point, then scale down to the app's hero size.
    w, h = img.size
    target = 9 / 16
    if w / h > target:
        cw = int(h * target)
        left = int(max(0, min(w - cw, focus * w - cw / 2)))
        img = img.crop((left, 0, left + cw, h))
    else:
        ch = int(w / target)
        top = (h - ch) // 3  # a little above centre keeps horizons and faces in frame
        img = img.crop((0, top, w, top + ch))
    if img.width > OUT_W:
        img = img.resize((OUT_W, OUT_H), Image.LANCZOS)
    emit(danak_id, "final", img, {**photo, "thumb": None, "size": img.size})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shard", type=int, default=0)
    ap.add_argument("--shards", type=int, default=1)
    args = ap.parse_args()
    spec = json.load(open("tools/photos.json", encoding="utf-8"))
    for i, (danak_id, entry) in enumerate(spec.items()):
        if i % args.shards != args.shard:
            continue
        if "pick" in entry:
            final(danak_id, entry["pick"], entry.get("focus", 0.5))
        else:
            candidates(danak_id, entry["query"])
        time.sleep(0.5)


if __name__ == "__main__":
    main()
