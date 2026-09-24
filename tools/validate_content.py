#!/usr/bin/env python3
"""
Validates a Danak content pack against schema/danak-v1.schema.json and checks that the
photos it points to are there (and that no photo is left over).

Usage: python3 tools/validate_content.py [pack_dir]   (default: the pack bundled in the app)
Needs: pip install jsonschema
"""
import json
import pathlib
import sys

import jsonschema

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCHEMA = ROOT / "schema" / "danak-v1.schema.json"
DEFAULT_PACK = ROOT / "app" / "src" / "main" / "assets" / "content"


def main():
    pack_dir = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PACK
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    pack = json.loads((pack_dir / "content.json").read_text(encoding="utf-8"))

    errors = []
    validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
    for error in validator.iter_errors(pack):
        where = "/".join(str(p) for p in error.absolute_path) or "(root)"
        errors.append(f"schema: {where}: {error.message}")

    danaks = pack.get("danaks", [])
    ids = [d.get("id") for d in danaks]
    errors += [f"duplicate id: {i}" for i in sorted({i for i in ids if ids.count(i) > 1})]

    referenced = {d["image"]["src"] for d in danaks if not d.get("image", {}).get("src", "https://").startswith("https://")}
    errors += [f"missing photo: {src}" for src in sorted(referenced) if not (pack_dir / src).is_file()]
    on_disk = {f"images/{p.name}" for p in (pack_dir / "images").glob("*")}
    errors += [f"unused photo: {src}" for src in sorted(on_disk - referenced)]

    for e in errors:
        print("FAIL", e)
    print(f"{len(danaks)} danaks, {len(errors)} problem(s)")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
