"""
Tests for tools/danak_sources.py. No test touches the network: a fake resolver stands in
for DNS and a fake transport for HTTPS, serving the files in fixtures/.

Run: python3 -m unittest discover -s tests/sources -v
"""
import json
import pathlib
import shutil
import sys
import tempfile
import unittest

import jsonschema

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import danak_sources as ds  # noqa: E402

FIXTURES = pathlib.Path(__file__).resolve().parent / "fixtures"
PUBLIC_IP = "93.184.216.34"
FIXED_TIME = lambda: "2026-09-25T08:00:00Z"  # noqa: E731


def fixture(name):
    return (FIXTURES / name).read_bytes()


class FakeNet:
    """DNS and HTTPS in memory. routes: (host, path) -> (status, headers, body); the query
    string is ignored for matching but recorded."""

    def __init__(self):
        self.dns = {}
        self.routes = {}
        self.requests = []
        self.fail = None  # exception to raise from the transport

    def resolver(self, host):
        if host not in self.dns:
            raise ds.IngestError("network", f"cannot resolve {host}")
        return self.dns[host]

    def transport(self, ip, host, target, headers, max_bytes):
        self.requests.append((ip, host, target))
        if self.fail:
            raise self.fail
        path = target.split("?")[0]
        status, response_headers, body = self.routes.get((host, path), (404, {}, b""))
        if len(body) > max_bytes:
            raise ds.IngestError("too-large", f"{host}{target}")
        return status, response_headers, body

    def serve(self, host, path, body, content_type, status=200, ip=PUBLIC_IP, headers=None):
        self.dns.setdefault(host, [ip])
        self.routes[(host, path)] = (status, {"content-type": content_type, **(headers or {})}, body)


class IngestTestCase(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.policy = ds.Policy(FIXTURES / "sources.yml")
        self.net = FakeNet()
        self.fetcher = ds.SafeFetcher(self.policy.all_hosts(), self.net.resolver, self.net.transport)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def wiki(self, lang, query_file, revision, html_file):
        host = f"{lang}.wikipedia.org"
        self.net.serve(host, "/w/api.php", fixture(f"wikipedia/{query_file}"), "application/json; charset=utf-8")
        self.net.serve(host, f"/w/rest.php/v1/revision/{revision}/html", fixture(f"wikipedia/{html_file}"),
                       'text/html; charset=utf-8; profile="https://www.mediawiki.org/wiki/Specs/HTML/2.8.0"')

    def ingest_wiki(self, lang, title):
        return ds.ingest_wikipedia(lang, title, self.fetcher, self.policy, self.tmp, now=FIXED_TIME)

    def ingest_url(self, url):
        return ds.ingest_url(url, self.fetcher, self.policy, self.tmp, now=FIXED_TIME)

    def load(self, path):
        return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))

    def assertRefused(self, code, call):
        with self.assertRaises(ds.IngestError) as caught:
            call()
        self.assertEqual(code, caught.exception.code, str(caught.exception))
        self.assertEqual([], list(self.tmp.glob("*.json")), "nothing may be stored")


class WikipediaTest(IngestTestCase):

    def test_persian_article(self):
        self.wiki("fa", "fa-query.json", 38000001, "fa-revision.html")
        path, created = self.ingest_wiki("fa", "اثر_زیگارنیک")
        self.assertTrue(created)
        snap = self.load(path)
        self.assertEqual("wikipedia-fa-1234567-r38000001", snap["snapshotId"])
        self.assertEqual(path.name, snap["snapshotId"] + ".json")
        src = snap["fromSource"]
        self.assertEqual(("اثر زیگارنیک", "fa", 1234567, 38000001, "2026-07-30T08:15:00Z", "Q194318"),
                         (src["title"], src["language"], src["pageId"], src["revisionId"], src["revisionTimestamp"], src["wikidataId"]))
        self.assertEqual("https://creativecommons.org/licenses/by-sa/4.0/", src["license"]["url"])
        self.assertEqual({"publisher": "ویکی‌پدیا", "derivatives": "share-alike", "license": "CC BY-SA 4.0",
                          "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/", "language": "fa"}, snap["fromConfig"])
        d = snap["derived"]
        self.assertEqual(("explicit", "CC BY-SA 4.0", True, []), (d["licenseStatus"], d["license"], d["usableForDanak"], d["notUsableBecause"]))
        self.assertEqual(src["canonicalUrl"], d["canonicalUrl"])
        self.assertEqual("untrusted-source-text", snap["content"]["trust"])

        text = json.dumps(snap["content"]["blocks"], ensure_ascii=False)
        self.assertIn("بلوما زایگارنیک در دههٔ ۱۹۲۰", text)          # whitespace collapsed
        self.assertIn("R_{u}/R_{c}\\\\approx 2", text)                # maths kept as its own text
        for chrome in ("[۱]", "ابهام‌زدایی", "infobox", "نام", "بلوما زایگارنیک\"", "اوسیانکینا",
                       "۱۹۲۷", "روان‌شناسی شناختی", "منابع", "جستارهای وابسته"):
            self.assertNotIn(chrome, text, chrome)
        self.assertEqual(
            [("paragraph",), ("heading", "آزمایش"), ("paragraph",), ("list",), ("list",), ("paragraph",)],
            [(b["type"], b["text"]) if b["type"] == "heading" else (b["type"],) for b in snap["content"]["blocks"]],
        )

    def test_english_article_through_a_redirect_title(self):
        self.wiki("en", "en-query.json", 1300000001, "en-revision.html")
        path, _ = self.ingest_wiki("en", "https://en.wikipedia.org/wiki/Placebo_effect")
        snap = self.load(path)
        self.assertEqual(("Placebo", "en", 24536, 1300000001, "Q269829"),
                         tuple(snap["fromSource"][k] for k in ("title", "language", "pageId", "revisionId", "wikidataId")))
        self.assertEqual("https://en.wikipedia.org/wiki/Placebo_effect", snap["requestedUrl"])
        blocks = snap["content"]["blocks"]
        self.assertEqual(["paragraph", "heading", "paragraph", "heading", "list", "quote"], [b["type"] for b in blocks])
        text = json.dumps(blocks)
        for chrome in ("[1]", "therapeutic value\"", "not prose", "Nocebo", "See also", "References", "External links"):
            self.assertNotIn(chrome, text.replace("no therapeutic value.", ""), chrome)
        self.assertTrue(blocks[4]["ordered"])

    def test_the_same_revision_is_not_stored_twice(self):
        self.wiki("fa", "fa-query.json", 38000001, "fa-revision.html")
        first, created = self.ingest_wiki("fa", "اثر زیگارنیک")
        before = first.read_bytes()
        again, created_again = self.ingest_wiki("fa", "اثر زیگارنیک")
        self.assertEqual((first, True, False), (again, created, created_again))
        self.assertEqual(before, again.read_bytes())
        self.assertEqual(1, len(list(self.tmp.glob("*.json"))))
        # Known revision: the article HTML is not even downloaded again.
        self.assertEqual(1, sum(1 for r in self.net.requests if "/revision/" in r[2]))

    def test_a_new_revision_is_a_new_snapshot_and_the_old_one_stays(self):
        self.wiki("fa", "fa-query.json", 38000001, "fa-revision.html")
        old, _ = self.ingest_wiki("fa", "اثر زیگارنیک")
        old_bytes = old.read_bytes()
        self.wiki("fa", "fa-query-rev2.json", 38000777, "fa-revision.html")
        new, created = self.ingest_wiki("fa", "اثر زیگارنیک")
        self.assertTrue(created)
        self.assertEqual("wikipedia-fa-1234567-r38000777.json", new.name)
        self.assertEqual(old_bytes, old.read_bytes())
        self.assertEqual(2, len(list(self.tmp.glob("*.json"))))

    def test_missing_page_and_disambiguation_are_refused(self):
        missing = {"batchcomplete": True, "query": {"pages": [{"ns": 0, "title": "X", "missing": True}]}}
        self.net.serve("fa.wikipedia.org", "/w/api.php", json.dumps(missing).encode(), "application/json")
        self.assertRefused("missing", lambda: self.ingest_wiki("fa", "X"))
        disambiguation = json.loads(fixture("wikipedia/fa-query.json"))
        disambiguation["query"]["pages"][0]["pageprops"]["disambiguation"] = ""
        self.net.serve("fa.wikipedia.org", "/w/api.php", json.dumps(disambiguation).encode(), "application/json")
        self.assertRefused("not-article", lambda: self.ingest_wiki("fa", "X"))

    def test_an_api_answer_without_title_is_refused(self):
        data = json.loads(fixture("wikipedia/fa-query.json"))
        del data["query"]["pages"][0]["title"]
        self.net.serve("fa.wikipedia.org", "/w/api.php", json.dumps(data).encode(), "application/json")
        self.assertRefused("missing-title", lambda: self.ingest_wiki("fa", "اثر زیگارنیک"))

    def test_only_configured_wikipedias(self):
        self.assertRefused("not-allowlisted", lambda: ds.ingest_wikipedia("de", "X", self.fetcher, self.policy, self.tmp))
        self.assertRefused("bad-url", lambda: self.ingest_wiki("fa", "https://en.wikipedia.org/wiki/Placebo"))


class WebTest(IngestTestCase):

    def serve_article(self, host="news.example.org", path="/science/sleep", body=None, **kw):
        self.net.serve(host, path, body if body is not None else fixture("web/article.html"), "text/html; charset=utf-8", **kw)

    def test_an_allowlisted_article(self):
        self.serve_article()
        path, created = self.ingest_url("https://news.example.org/science/sleep?utm=x")
        snap = self.load(path)
        self.assertTrue(created)
        self.assertEqual("web", snap["sourceType"])
        self.assertEqual("trusted-news", snap["sourceId"])
        self.assertEqual("چرا خواب برای حافظه مهم است", snap["fromSource"]["title"])  # og:title
        self.assertEqual("fa", snap["fromSource"]["language"])
        self.assertEqual("https://news.example.org/science/sleep", snap["derived"]["canonicalUrl"])
        self.assertEqual(("explicit", "CC BY 4.0", True), tuple(snap["derived"][k] for k in ("licenseStatus", "license", "usableForDanak")))
        text = json.dumps(snap["content"]["blocks"], ensure_ascii=False)
        for chrome in ("کوکی", "پذیرش", "خانه", "بیشتر بخوانید", "اشتراک‌گذاری", "©", "track"):
            self.assertNotIn(chrome, text, chrome)
        self.assertIn("مغز خاطره‌های روز را بازپخش می‌کند", text)

    def test_the_same_page_and_content_is_stored_once(self):
        self.serve_article()
        first, _ = self.ingest_url("https://news.example.org/science/sleep")
        second, created = self.ingest_url("https://news.example.org/science/sleep")
        self.assertEqual((first, False), (second, created))

    def test_changed_content_is_a_new_snapshot(self):
        self.serve_article()
        first, _ = self.ingest_url("https://news.example.org/science/sleep")
        self.serve_article(body=fixture("web/article.html").replace("خواب عمیق".encode(), "خواب سبک".encode()))
        second, created = self.ingest_url("https://news.example.org/science/sleep")
        self.assertTrue(created)
        self.assertNotEqual(first, second)

    def test_unknown_licence_is_recorded_and_blocks_use(self):
        body = fixture("web/article.html").replace(b'<link rel="license" href="https://creativecommons.org/licenses/by/4.0/">', b"")
        body = body.replace(b"https://news.example.org/science/sleep", b"https://blog.example.net/post")
        self.serve_article("blog.example.net", "/post", body)
        snap = self.load(self.ingest_url("https://blog.example.net/post")[0])
        self.assertEqual(("unknown", None, False), tuple(snap["derived"][k] for k in ("licenseStatus", "license", "usableForDanak")))
        self.assertIn("licence unknown", snap["derived"]["notUsableBecause"])

    def test_no_derivatives_is_stored_but_marked_unusable(self):
        body = fixture("web/article.html").replace(b'<link rel="license" href="https://creativecommons.org/licenses/by/4.0/">', b"")
        self.serve_article("press.example.com", "/a", body)
        snap = self.load(self.ingest_url("https://press.example.com/a")[0])
        self.assertEqual("configured", snap["derived"]["licenseStatus"])
        self.assertFalse(snap["derived"]["usableForDanak"])
        self.assertIn("sources.yml: derivatives 'none'", snap["derived"]["notUsableBecause"])

    def test_a_declared_licence_that_differs_from_policy_needs_review(self):
        body = fixture("web/article.html").replace(b"licenses/by/4.0", b"licenses/by-nc/4.0")
        self.serve_article(body=body)
        snap = self.load(self.ingest_url("https://news.example.org/science/sleep")[0])
        self.assertEqual("explicit", snap["derived"]["licenseStatus"])
        self.assertFalse(snap["derived"]["usableForDanak"])

    def test_malformed_html_still_yields_its_paragraphs(self):
        self.serve_article(body=fixture("web/malformed.html"))
        snap = self.load(self.ingest_url("https://news.example.org/science/sleep")[0])
        self.assertEqual(["بند نخست که بسته نشده", "متن آزاد داخل div با پررنگ و کج که درست بسته نشده است", "بند سوم"],
                         [b["text"] for b in snap["content"]["blocks"]])

    def test_a_page_without_title_or_text_is_refused(self):
        self.serve_article(body=fixture("web/no-title.html"))
        self.assertRefused("missing-title", lambda: self.ingest_url("https://news.example.org/science/sleep"))
        self.serve_article(body=b"<html><head><title>t</title></head><body><nav>menu</nav></body></html>")
        self.assertRefused("no-text", lambda: self.ingest_url("https://news.example.org/science/sleep"))

    def test_a_canonical_link_to_another_host_is_ignored(self):
        body = fixture("web/article.html").replace(b"https://news.example.org/science/sleep", b"https://evil.example/x")
        self.serve_article(body=body)
        snap = self.load(self.ingest_url("https://news.example.org/science/sleep")[0])
        self.assertEqual("https://news.example.org/science/sleep", snap["derived"]["canonicalUrl"])
        self.assertFalse(any(r[1] == "evil.example" for r in self.net.requests))

    def test_wikipedia_urls_go_through_the_wikipedia_path(self):
        self.assertRefused("use-wikipedia", lambda: self.ingest_url("https://fa.wikipedia.org/wiki/X"))


class SecurityTest(IngestTestCase):
    """What SafeFetcher must refuse before anything is stored."""

    def test_refusals(self):
        self.net.serve("news.example.org", "/a", fixture("web/article.html"), "text/html")
        cases = {
            "not-https": "http://news.example.org/a",
            "not-allowlisted": "https://unknown.example/a",
            "credentials": "https://user:pw@news.example.org/a",
            "port": "https://news.example.org:8443/a",
            "ip-literal": "https://93.184.216.34/a",
        }
        for code, url in cases.items():
            with self.subTest(code):
                self.assertRefused(code, lambda: self.fetcher.get(url, ds.HTML_TYPES, 1000))
        # localhost is not in the allowlist, and neither is any IP literal.
        self.assertRefused("not-allowlisted", lambda: self.ingest_url("https://localhost/a"))
        self.assertRefused("ip-literal", lambda: self.fetcher.get("https://[::1]/a", ds.HTML_TYPES, 1000))

    def test_allowlisted_hosts_that_resolve_to_private_addresses(self):
        for ip in ("127.0.0.1", "10.1.2.3", "192.168.1.1", "172.16.0.9", "169.254.169.254", "::1", "fd00::1", "::ffff:10.0.0.1", "0.0.0.0"):
            with self.subTest(ip):
                self.net.dns["news.example.org"] = [PUBLIC_IP, ip]  # one bad address is enough
                self.assertRefused("private-address", lambda: self.ingest_url("https://news.example.org/a"))
        self.assertEqual([], self.net.requests, "no connection is ever opened")

    def test_redirects_are_checked_hop_by_hop(self):
        self.net.serve("news.example.org", "/start", b"", "text/html", status=302, headers={"location": "https://evil.example/x"})
        self.assertRefused("not-allowlisted", lambda: self.ingest_url("https://news.example.org/start"))
        self.net.serve("news.example.org", "/start", b"", "text/html", status=301, headers={"location": "https://cdn.example.org/x"})
        self.net.dns["cdn.example.org"] = ["192.168.1.10"]
        self.assertRefused("private-address", lambda: self.ingest_url("https://news.example.org/start"))
        self.net.serve("news.example.org", "/start", b"", "text/html", status=302, headers={"location": "http://news.example.org/a"})
        self.assertRefused("not-https", lambda: self.ingest_url("https://news.example.org/start"))
        self.net.serve("news.example.org", "/loop", b"", "text/html", status=302, headers={"location": "/loop"})
        self.assertRefused("redirects", lambda: self.ingest_url("https://news.example.org/loop"))

    def test_an_allowed_redirect_is_followed(self):
        self.net.serve("news.example.org", "/old", b"", "text/html", status=301, headers={"location": "/science/sleep"})
        self.net.serve("news.example.org", "/science/sleep", fixture("web/article.html"), "text/html")
        path, created = self.ingest_url("https://news.example.org/old")
        self.assertTrue(created)
        self.assertEqual("https://news.example.org/science/sleep", self.load(path)["fromSource"]["finalUrl"])

    def test_oversized_wrong_type_timeout_and_http_errors(self):
        self.net.serve("news.example.org", "/big", b"<p>" + b"x" * (ds.LIMITS["web-html"] + 1), "text/html")
        self.assertRefused("too-large", lambda: self.ingest_url("https://news.example.org/big"))
        self.net.serve("news.example.org", "/pdf", b"%PDF-1.7", "application/pdf")
        self.assertRefused("content-type", lambda: self.ingest_url("https://news.example.org/pdf"))
        self.net.serve("news.example.org", "/gone", b"", "text/html", status=404)
        self.assertRefused("http", lambda: self.ingest_url("https://news.example.org/gone"))
        self.net.fail = ds.IngestError("network", "timed out")
        self.assertRefused("network", lambda: self.ingest_url("https://news.example.org/a"))

    def test_the_real_transport_reads_no_further_than_the_limit(self):
        class Body:
            def __init__(self, data, length=None):
                self.data, self.length, self.read_sizes = data, length, []

            def getheader(self, name):
                return self.length if name == "Content-Length" else None

            def read(self, n):
                self.read_sizes.append(n)
                return self.data[:n]

        self.assertEqual(b"abc", ds.read_limited(Body(b"abc", "3"), 10, "x"))
        with self.assertRaises(ds.IngestError):  # announced too big: nothing is read
            ds.read_limited(Body(b"", "11"), 10, "x")
        body = Body(b"y" * 1000)                  # unannounced: at most limit + 1 bytes are read
        with self.assertRaises(ds.IngestError):
            ds.read_limited(body, 10, "x")
        self.assertEqual([11], body.read_sizes)


class DeterminismTest(unittest.TestCase):

    def test_normalisation_is_deterministic_and_idempotent(self):
        raw = "  متن با   فاصله​های پنهان‮ و\tنیم‌فاصله\n"
        once = ds.normalize_text(raw)
        self.assertEqual("متن با فاصلههای پنهان و نیم‌فاصله", once)
        self.assertEqual(once, ds.normalize_text(once))
        self.assertIn("‌", once)  # the half-space is Persian spelling, kept

    def test_the_hash_depends_only_on_the_blocks(self):
        root = lambda: ds.parse_html(fixture("wikipedia/fa-revision.html").decode())  # noqa: E731
        a, b = ds.extract_blocks(root(), "wikipedia"), ds.extract_blocks(root(), "wikipedia")
        self.assertEqual(ds.content_sha256(a), ds.content_sha256(b))
        self.assertNotEqual(ds.content_sha256(a), ds.content_sha256(a[:-1]))


class ValidationTest(IngestTestCase):

    def make(self):
        self.wiki("fa", "fa-query.json", 38000001, "fa-revision.html")
        self.wiki("en", "en-query.json", 1300000001, "en-revision.html")
        self.net.serve("news.example.org", "/science/sleep", fixture("web/article.html"), "text/html")
        paths = [self.ingest_wiki("fa", "اثر زیگارنیک")[0], self.ingest_wiki("en", "Placebo")[0],
                 self.ingest_url("https://news.example.org/science/sleep")[0]]
        return paths

    def test_stored_snapshots_validate(self):
        self.make()
        count, problems = ds.validate(self.tmp)
        self.assertEqual((3, []), (count, problems))

    def test_tampering_is_caught(self):
        fa, en, web = self.make()
        snap = self.load(fa)
        snap["content"]["blocks"][0]["text"] += " (افزوده)"
        fa.write_text(json.dumps(snap, ensure_ascii=False), encoding="utf-8")
        snap = self.load(en)
        del snap["derived"]["license"]
        en.write_text(json.dumps(snap), encoding="utf-8")
        web.rename(web.with_name("web-trusted-news-000000000000-0000000000000000.json"))
        _, problems = ds.validate(self.tmp)
        joined = "\n".join(problems)
        self.assertIn("content does not match contentSha256", joined)
        self.assertIn("'license' is a required property", joined)
        self.assertIn("file name does not match snapshotId", joined)

    def test_the_schema_itself_is_valid(self):
        jsonschema.Draft202012Validator.check_schema(json.loads(ds.SCHEMA_FILE.read_text(encoding="utf-8")))

    def test_repository_snapshots_validate(self):
        count, problems = ds.validate(ds.SNAPSHOT_DIR)
        self.assertEqual([], problems)


class PublishingIsolationTest(unittest.TestCase):
    """Snapshots are audit evidence, never app content: the site build cannot pick them up."""

    def test_the_site_build_never_contains_snapshots(self):
        sys.path.insert(0, str(ROOT / "tools"))
        import danak_content as dc
        tmp = pathlib.Path(tempfile.mkdtemp())
        try:
            dc.build(dc.DEFAULT_CONTENT, tmp / "site")
            published = [str(p.relative_to(tmp / "site")) for p in (tmp / "site").rglob("*") if p.is_file()]
            self.assertTrue(published)
            self.assertFalse([p for p in published if "snapshot" in p or p.startswith("sources")])
            self.assertTrue(all(p.startswith(("v1/", "schema/danak-v1", "schema/index-v1")) for p in published))
        finally:
            shutil.rmtree(tmp)


if __name__ == "__main__":
    unittest.main()
