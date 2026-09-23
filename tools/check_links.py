#!/usr/bin/env python3
"""
Checks that every Danak source link resolves, so a typo or a renamed article can never
ship. With --suggest-fa it also asks Wikipedia for the Persian edition of each English
article and prints it, which is how the Persian source links were chosen.

Usage: python3 tools/check_links.py [--suggest-fa]
"""
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

CONTENT = "app/src/main/java/ir/danak/app/data/MockDanaks.kt"
USER_AGENT = "DanakLinkCheck/1.0 (+https://github.com/30nap/Danak)"


def fetch(url):
    """GET with retries on rate limiting and transient network errors."""
    parts = urllib.parse.urlsplit(url)
    safe = urllib.parse.urlunsplit(parts._replace(path=urllib.parse.quote(parts.path, safe="/%'()_-.,")))
    last = None
    for attempt in range(4):
        try:
            req = urllib.request.Request(safe, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=20) as resp:
                return resp.status, resp.read()
        except urllib.error.HTTPError as e:
            last = e.code
            if e.code not in (429, 500, 502, 503, 504):
                return e.code, b""
        except urllib.error.URLError as e:
            last = str(e.reason)
        time.sleep(2 ** attempt)
    return last, b""


def persian_title(english_url):
    title = urllib.parse.unquote(english_url.rsplit("/wiki/", 1)[1])
    api = "https://en.wikipedia.org/w/api.php?" + urllib.parse.urlencode({
        "action": "query", "titles": title, "prop": "langlinks", "lllang": "fa",
        "redirects": 1, "format": "json",
    })
    status, body = fetch(api)
    if status != 200:
        return None
    for page in json.loads(body)["query"]["pages"].values():
        for link in page.get("langlinks", []):
            return link["*"]
    return None


def main():
    source = open(CONTENT, encoding="utf-8").read()
    pairs = re.findall(r'id = "([^"]+)".*?sourceUrl = "([^"]+)"', source, re.S)
    # Photo credit pages are links the app opens too.
    pairs += [(f"{i} (photo)", u) for i, u in
              re.findall(r'id = "([^"]+)".*?pageUrl = "([^"]+)"', source, re.S)]
    suggest = "--suggest-fa" in sys.argv
    failures = []
    for danak_id, url in pairs:
        status, _ = fetch(url)
        ok = status == 200
        print(f"{'OK  ' if ok else 'FAIL'} {status} {danak_id:<20} {url}")
        if not ok:
            failures.append(url)
        if suggest and "en.wikipedia.org/wiki/" in url and "(photo)" not in danak_id:
            fa = persian_title(url)
            if fa:
                print(f"SUGGEST {danak_id} https://fa.wikipedia.org/wiki/{fa.replace(' ', '_')}")
            else:
                print(f"SUGGEST {danak_id} (no Persian article)")
        time.sleep(0.3)
    print(f"\n{len(pairs) - len(failures)}/{len(pairs)} links OK")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
