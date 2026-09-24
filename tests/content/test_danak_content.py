"""
Tests for tools/danak_content.py.

fixtures/valid is a small, complete content tree that must pass. Each file in
fixtures/invalid describes one way to break it (edits, file writes, deletions, symlinks)
and the problem code the validator must report for it.

Run: python3 -m unittest discover -s tests/content -v
"""
import functools
import hashlib
import http.server
import json
import os
import pathlib
import shutil
import sys
import tempfile
import threading
import unittest

import jsonschema

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import danak_content as dc  # noqa: E402

FIXTURES = pathlib.Path(__file__).resolve().parent / "fixtures"
VALID = FIXTURES / "valid"
INVALID = sorted((FIXTURES / "invalid").glob("*.json"))


def apply_case(tree, case):
    """Applies one invalid fixture to a copy of the valid tree."""
    for rel, edits in case.get("edit", {}).items():
        path = tree / rel
        doc = json.loads(path.read_text(encoding="utf-8"))
        for dotted, value in edits.items():
            *parents, last = dotted.split(".")
            node = doc
            for key in parents:
                node = node[int(key)] if isinstance(node, list) else node[key]
            if value is None:
                del node[last]
            elif isinstance(node, list):
                node[int(last)] = value
            else:
                node[last] = value
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
    for rel, text in case.get("write", {}).items():
        path = tree / rel
        if text.startswith("@copy:"):
            shutil.copyfile(tree / text[len("@copy:"):], path)
        else:
            path.write_text(text, encoding="utf-8")
    for rel in case.get("delete", []):
        (tree / rel).unlink()
    for rel, target in case.get("symlink", {}).items():
        (tree / rel).unlink(missing_ok=True)
        os.symlink(target, tree / rel)


class TempTree(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.tree = self.tmp / "content"
        shutil.copytree(VALID, self.tree, symlinks=True)

    def tearDown(self):
        shutil.rmtree(self.tmp)


class ValidationTest(TempTree):

    def test_valid_fixture_passes(self):
        danaks, problems = dc.validate(self.tree)
        self.assertEqual([], [str(p) for p in problems])
        self.assertEqual(["alpha", "beta"], [d["id"] for d in danaks])

    def test_every_invalid_fixture_is_rejected_with_its_code(self):
        self.assertGreaterEqual(len(INVALID), 20)
        for fixture in INVALID:
            case = json.loads(fixture.read_text(encoding="utf-8"))
            with self.subTest(fixture.stem):
                tree = self.tmp / fixture.stem
                shutil.copytree(VALID, tree, symlinks=True)
                apply_case(tree, case)
                _, problems = dc.validate(tree)
                codes = {p.code for p in problems}
                self.assertIn(case["expect"], codes, f"{case['description']} -> {[str(p) for p in problems]}")
                # And nothing broken may be published.
                with self.assertRaises(SystemExit):
                    dc.build(tree, self.tmp / f"{fixture.stem}-site")
                self.assertFalse((self.tmp / f"{fixture.stem}-site" / "v1" / "index.json").exists())

    def test_repository_content_passes(self):
        danaks, problems = dc.validate(dc.DEFAULT_CONTENT)
        self.assertEqual([], [str(p) for p in problems])
        self.assertGreaterEqual(len(danaks), 32)


class TextRulesTest(unittest.TestCase):

    def test_clean_persian_with_technical_terms_passes(self):
        self.assertEqual([], dc.text_problems("در Git، شاخه فقط اشاره‌گری به یک کامیت است.", "summary"))
        self.assertEqual([], dc.text_problems("چرا ۰٫۱ + ۰٫۲ در کامپیوتر برابر ۰٫۳ نیست؟", "title"))

    def test_each_rule_fires(self):
        cases = {
            " فاصله در آغاز.": "leading or trailing whitespace",
            "کلمهٔ عربي.": "Arabic yeh",
            "عدد ٣ عربی.": "Arabic-Indic digits",
            "خط اول\nخط دوم.": "line break",
            "نیم‌‌فاصلهٔ دوتایی.": "half-space",
            "چرا این طور است?": "Latin",
            "پیش از نقطه .": "space before punctuation",
        }
        for text, expected in cases.items():
            with self.subTest(text):
                self.assertTrue(any(expected in p for p in dc.text_problems(text, "summary")), dc.text_problems(text, "summary"))

    def test_title_may_end_with_question_mark_but_not_a_period(self):
        self.assertEqual([], dc.text_problems("چرا آسمان آبی است؟", "title"))
        self.assertTrue(dc.text_problems("آسمان آبی است.", "title"))


class BuildTest(TempTree):

    def build(self, name="site"):
        out = self.tmp / name
        dc.build(self.tree, out)
        return out

    def test_published_structure(self):
        out = self.build()
        files = sorted(str(p.relative_to(out)) for p in out.rglob("*") if p.is_file())
        index = json.loads((out / "v1" / "index.json").read_text(encoding="utf-8"))
        jsonschema.validate(index, dc.load_schema("index-v1.schema.json"))
        self.assertEqual(["alpha", "beta"], [e["id"] for e in index["danaks"]])

        expected = {"v1/index.json", "schema/danak-v1.schema.json", "schema/index-v1.schema.json"}
        for entry in index["danaks"]:
            body = (out / "v1" / entry["path"]).read_bytes()
            self.assertEqual(entry["sha256"], hashlib.sha256(body).hexdigest())
            danak = json.loads(body)
            image = danak["image"]["src"]
            self.assertRegex(image, rf"^images/{entry['id']}_[0-9a-f]{{8}}\.webp$")
            source_image = (self.tree / "images" / f"{entry['id']}.webp").read_bytes()
            self.assertEqual(source_image, (out / "v1" / image).read_bytes())
            expected |= {f"v1/{entry['path']}", f"v1/{image}"}
        # Only generated files: sources.yml, danak sources and anything else stay private.
        self.assertEqual(sorted(expected), files)

    def test_build_is_deterministic(self):
        a, b = self.build("a"), self.build("b")
        read = lambda root: {str(p.relative_to(root)): p.read_bytes() for p in root.rglob("*") if p.is_file()}
        self.assertEqual(read(a), read(b))

    def test_an_edit_changes_only_that_danaks_file(self):
        before = json.loads((self.build("before") / "v1" / "index.json").read_text())
        path = self.tree / "danaks" / "0002-beta.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["readingSeconds"] = 45
        path.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
        after = json.loads((self.build("after") / "v1" / "index.json").read_text())
        self.assertEqual(before["danaks"][0], after["danaks"][0])
        self.assertNotEqual(before["danaks"][1]["sha256"], after["danaks"][1]["sha256"])

    def test_build_refuses_existing_or_overlapping_output(self):
        (self.tmp / "exists").mkdir()
        with self.assertRaises(SystemExit):
            dc.build(self.tree, self.tmp / "exists")
        with self.assertRaises(SystemExit):
            dc.build(self.tree, self.tree / "site")
        with self.assertRaises(SystemExit):
            dc.build(self.tree, dc.ROOT)


class VerifySiteTest(TempTree):
    """verify-site against a real HTTP server serving a freshly built site."""

    def serve(self, root):
        handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(root))
        handler.log_message = lambda *args: None
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.shutdown)
        return f"http://127.0.0.1:{server.server_address[1]}"

    def test_a_correct_site_verifies(self):
        site = self.tmp / "site"
        dc.build(self.tree, site)
        count, problems = dc.verify_site(self.serve(site))
        self.assertEqual((2, []), (count, [str(p) for p in problems]))

    def test_a_tampered_file_is_caught(self):
        site = self.tmp / "site"
        dc.build(self.tree, site)
        target = next((site / "v1" / "content").glob("beta_*.json"))
        target.write_bytes(target.read_bytes().replace("خط".encode(), "خـط".encode(), 1))
        _, problems = dc.verify_site(self.serve(site))
        self.assertIn("site", {p.code for p in problems})


class BundleCheckTest(TempTree):

    def make_bundle(self, ids=("alpha", "beta")):
        bundle = self.tmp / "bundle"
        (bundle / "images").mkdir(parents=True)
        danaks, _ = dc.validate(self.tree)
        chosen = [d for d in danaks if d["id"] in ids]
        for d in chosen:
            shutil.copyfile(self.tree / d["image"]["src"], bundle / d["image"]["src"])
        (bundle / "content.json").write_text(json.dumps({"schemaVersion": 1, "danaks": chosen}, ensure_ascii=False), encoding="utf-8")
        return bundle

    def rewrite(self, bundle, change):
        pack = json.loads((bundle / "content.json").read_text(encoding="utf-8"))
        change(pack)
        (bundle / "content.json").write_text(json.dumps(pack, ensure_ascii=False), encoding="utf-8")

    def test_exact_subset_passes(self):
        self.assertEqual([], dc.check_bundle(self.tree, self.make_bundle(ids=("beta",))))

    def test_edited_text_is_drift(self):
        bundle = self.make_bundle()
        self.rewrite(bundle, lambda p: p["danaks"][0].update(title="عنوانی که بازبینی نشده است؟"))
        self.assertIn("bundle-drift", {p.code for p in dc.check_bundle(self.tree, bundle)})

    def test_swapped_image_is_drift(self):
        bundle = self.make_bundle()
        shutil.copyfile(bundle / "images" / "alpha.webp", bundle / "images" / "beta.webp")
        self.assertIn("bundle-drift", {p.code for p in dc.check_bundle(self.tree, bundle)})

    def test_reordered_bundle_is_drift(self):
        bundle = self.make_bundle()
        self.rewrite(bundle, lambda p: p["danaks"].reverse())
        self.assertIn("bundle-drift", {p.code for p in dc.check_bundle(self.tree, bundle)})

    def test_unsafe_bundled_image_path_is_refused(self):
        bundle = self.make_bundle()
        self.rewrite(bundle, lambda p: p["danaks"][0]["image"].update(src="../../../etc/passwd"))
        self.assertIn("unsafe-path", {p.code for p in dc.check_bundle(self.tree, bundle)})

    def test_repository_bundle_matches_content(self):
        self.assertEqual([], [str(p) for p in dc.check_bundle(dc.DEFAULT_CONTENT, dc.DEFAULT_BUNDLE)])


if __name__ == "__main__":
    unittest.main()
