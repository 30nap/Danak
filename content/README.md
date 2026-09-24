# Danak content

The reviewed, published Danaks. Everything here is data: the Android app bundles a copy,
and GitHub Pages serves it as static files.

## Layout

```
content/
├── sources.yml              trusted sources and their licences
├── danaks/NNNN-<id>.json    one Danak per file; NNNN is its position in the feed
└── images/<id>.webp         its hero photo, named after the Danak
```

Each `danaks/` file is one Danak as defined by
[`schema/danak-v1.schema.json`](../schema/danak-v1.schema.json) (`#/$defs/danak`), with
`image.src` set to `images/<id>.webp`. A new Danak takes the next free number; adding one
touches only its own two files.

## Adding or changing a Danak

1. Add or edit `danaks/NNNN-<id>.json` and `images/<id>.webp`.
2. Validate:
   ```bash
   pip install jsonschema pyyaml
   python3 tools/danak_content.py validate
   ```
3. Open a pull request. The *Content* workflow validates it; merging to the default branch
   publishes it.

A new source host must first be added to `sources.yml`, in its own reviewed change.

## What is checked

`tools/danak_content.py validate` rejects:

| Area | Rule |
|---|---|
| Structure | file names `NNNN-<id>.json` with unique positions and `id` matching the name; only regular files (no symlinks); JSON under 64 KB |
| Schema | every field against Danak schema v1: required fields, category enum, lengths, patterns |
| Duplicates | the same `id`, the same `conceptKey` (case-insensitive), or the same source article twice |
| Sources | https only, no credentials or ports; host must be listed in `sources.yml`; `publisher` and `license` must match that entry; sources that forbid derivatives cannot back a Danak |
| Images | local only, at `images/<id>.<webp\|jpg\|png>`, inside `images/`, present, real image bytes, at most 1.5 MB; credit page on an allowed image host under an allowed licence; no unused images |
| Persian text | Persian ی/ک (not Arabic ي/ك), no Arabic-Indic digits, no hidden characters (zero-width space, joiner, BOM, direction marks and overrides, no-break space), half-spaces not at word edges or doubled, no double spaces or line breaks, Persian ، ؛ ؟ instead of Latin , ; ? next to Persian text, no space before punctuation, mainly Persian letters, titles not ending in a period, sentences ending in `. ؟ ! »` |

`check-bundle` checks that every Danak bundled in the app
(`app/src/main/assets/content/`) exists here unchanged, with the same image, in the same
order.

## Published structure

`python3 tools/danak_content.py build --out _site` produces:

```
_site/
├── v1/
│   ├── index.json                   every Danak in feed order: id, path, sha256
│   ├── content/<id>_<hash8>.json    one Danak (schema v1), named by the hash of its bytes
│   └── images/<id>_<hash8>.webp     its photo, named by the hash of the image
└── schema/
    ├── danak-v1.schema.json
    └── index-v1.schema.json
```

Content and image files are named by their hash, so a published file never changes: an
edit publishes a new file and a new `index.json` entry. A client fetches `index.json`,
downloads only entries whose `sha256` it does not have, verifies each file against that
hash, and resolves `image.src` against the `v1/` directory. The index format is
[`schema/index-v1.schema.json`](../schema/index-v1.schema.json).

The build is deterministic (the same content produces the same bytes), writes only files
it generates, and refuses to write into an existing directory.
