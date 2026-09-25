# Source snapshots

Immutable, auditable records of exactly what a trusted source said when it was fetched.
They are the only material a later step may write a Danak from, so for any Danak the
question "what exactly did the writer see?" has a file as its answer.

Snapshots are pipeline evidence, not app content: nothing here is published. The site
build (`tools/danak_content.py build`) reads only `content/`.

## Layout

```
sources/
├── requests.txt              what to ingest next (edit it to run the Ingest sources workflow)
└── snapshots/<snapshotId>.json
```

`snapshotId` is also the file name and is derived from what makes a source unique:

| Source | snapshotId | A new snapshot when… |
|---|---|---|
| Wikipedia | `wikipedia-<lang>-<pageId>-r<revisionId>` | the article has a new revision |
| Web page | `web-<sourceId>-<sha256(canonicalUrl)[:12]>-<contentSha256[:16]>` | the extracted text changes |

Ingesting something already on file stores nothing. A snapshot file is written once
(exclusive create) and CI rejects any change, rename or deletion of an existing one.

## What a snapshot holds

The format is [`schema/source-snapshot-v1.schema.json`](../schema/source-snapshot-v1.schema.json).
Metadata is split by where it came from:

- `fromSource` — what the source or its API said: title, language, canonical URL,
  Wikipedia page id, revision id and timestamp, Wikidata id (QID), the licence it declares.
  `null` means the source did not say.
- `fromConfig` — the `content/sources.yml` entry at ingestion time: publisher, licence,
  what derivatives the licence allows.
- `derived` — what the tool computed: the effective title, URL and language, the licence
  status, whether the snapshot may back a Danak (and why not), and `contentSha256`.

`content.blocks` is the extracted text as headings, paragraphs, lists and quotes, marked
`"trust": "untrusted-source-text"`: it is material to quote and check against, never
instructions.

### Licence status

| `licenseStatus` | Meaning |
|---|---|
| `explicit` | the source declared a licence (Wikipedia: the API's rights info; web: `<link rel="license">`) |
| `configured` | the source said nothing; `sources.yml` names a licence for it |
| `unknown` | neither; valid metadata, never permission |

`usableForDanak` is false when the licence is unknown, when what the source declares differs
from `sources.yml`, or when `sources.yml` says derivatives are not allowed. Such snapshots
may be kept for review; they cannot back a Danak.

## Extraction

- **Wikipedia**: the MediaWiki Action API gives the page id, the current revision, the
  Wikidata id and the site licence; the article is the Parsoid HTML of exactly that revision
  (`/w/rest.php/v1/revision/<id>/html`). Citations, infoboxes and other tables, figures,
  hatnotes, navigation boxes and maintenance notices are dropped, and so are reference-type
  sections (References, See also, External links, منابع, پانویس, جستارهای وابسته, …).
  Formulas keep their own text.
- **Web**: one page by explicit URL, never crawled. The text comes from the page's
  `<article>` (or `<main>`), without navigation, headers, footers, cookie banners, share and
  "related" blocks, forms, scripts or media.
- **Normalisation** (both): Unicode NFC, invisible and direction-control characters removed
  (the Persian half-space is kept), whitespace collapsed. Nothing is reworded; the same
  input always gives the same blocks and the same hash.

## Network safety

Every request is https to a host listed in `content/sources.yml`, without credentials or a
non-default port. Every address the host resolves to must be public (no loopback, private,
link-local or reserved ranges), and the connection is made to the address that was checked.
Redirects (at most 3) are checked again hop by hop. Responses are capped (2 MB for API
JSON, 12 MB for Wikipedia HTML, 3 MB for web pages), time out after 20 s, and must have the
expected content type. No JavaScript is run.

## Adding a source

```bash
pip install jsonschema pyyaml

# A Wikipedia article (fa or en), by title or URL
python3 tools/danak_sources.py wikipedia fa "اثر زیگارنیک"
python3 tools/danak_sources.py wikipedia en https://en.wikipedia.org/wiki/Placebo

# A page from another approved source: first add the source to content/sources.yml
# (id, ingest: web, publisher, hosts, derivatives, and license/licenseUrl if known), then
python3 tools/danak_sources.py url https://example.org/path/to/article

python3 tools/danak_sources.py list                 # what is on file
python3 tools/danak_sources.py show <snapshotId>    # metadata and the extracted text
python3 tools/danak_sources.py validate             # schema, ids and hashes
```

Without direct access to the sites, add lines to `sources/requests.txt` and push: the
*Ingest sources* workflow snapshots them and uploads the new files as an artifact (and
prints them into its log), ready to be committed.
