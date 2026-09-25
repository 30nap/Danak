# Danak drafts

AI-written Danak drafts, each waiting for a person to decide on it. Nothing here is
published: the site build reads only `content/`, and a draft becomes a Danak only when a
person copies it there and it passes the normal content review.

```
drafts/<snapshotId>/
├── draft.json   the review artifact (schema/danak-draft-v1.schema.json)
└── review.md    the same, for reading: "should this Danak be published?"
```

Drafts may be regenerated (a new run replaces the files; Git keeps the history). The
snapshots they cite never change.

## How a draft is made

`tools/danak_generate.py run <snapshotId>`, for one snapshot from `sources/snapshots/`:

| Step | What happens | Who |
|---|---|---|
| Policy | the snapshot is re-checked (schema, hash) and judged against `content/sources.yml` **as it is now**, not the `usableForDanak` stored at ingestion; an article already published is refused | code |
| A. Idea | one idea, 2–6 evidence excerpts copied verbatim from numbered blocks, and warning signs in the source; or `NO_SUITABLE_IDEA` / `SOURCE_REQUIRES_REVIEW` | model |
| B. Draft | the Persian Danak (category, title, summary, sections, key takeaway) and every factual claim with its location and verbatim evidence | model |
| Checks | evidence exists verbatim in the cited block; every number appears in evidence; every text location is covered by a claim; no links; no provenance fields; no evidence from blocks that read like instructions to an AI; mechanical Persian fixes, recorded | code |
| C. Verify | a separate call judges each claim against the snapshot: SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED, CONTRADICTED or UNCERTAIN, with reasons and evidence, and lists factual statements no claim covers | model (may be a different one) |
| Validation | Danak schema v1 (image pending), Persian rules, reading time, source metadata, concept key, source policy | code |

The model never writes the source URL, publisher, licence, revision, Wikidata id, concept
key (`conceptKey` is the snapshot's Wikidata id), reading time or id.

## Statuses

| Status | Meaning |
|---|---|
| `ready-for-review` | every automatic check passed; PARTIALLY_SUPPORTED and UNCERTAIN claims and source warnings are listed for the reviewer |
| `rejected` | a blocking problem: invented or missing evidence, an unsupported, contradicted or unclaimed statement, a failed validation rule |
| `source-requires-review` | the model found warning signs in the source (or flagged the very blocks its idea rests on) and stopped |
| `no-suitable-idea` | nothing in the snapshot is worth a Danak |
| `policy-blocked` | the current policy does not allow a Danak from this snapshot; no model was called |
| `failed` | the provider gave no usable answer (after retries) |

A ready draft is not an approved one. `usableForDanak` and SUPPORTED both mean "backed by
this snapshot", never "true": Phase 4 found wrong statements in Persian Wikipedia.

## Publishing a draft (by hand)

1. Read `review.md`, especially sections F (claims) and G (warnings); check the Danak
   against the evidence.
2. Choose the permanent `id`, a photo (`image.src`, `image.credit`) and the feed position.
3. Save the `danak` object from `draft.json` as `content/danaks/NNNN-<id>.json` and the photo
   as `content/images/<id>.webp`.
4. `python3 tools/danak_content.py validate`, then a pull request as for any content.

## Commands

```bash
pip install jsonschema pyyaml            # plus: pip install anthropic, for that provider
export DANAK_AI_PROVIDER=anthropic DANAK_AI_MODEL=claude-opus-5 ANTHROPIC_API_KEY=…
python3 tools/danak_generate.py run wikipedia-fa-6629-r44188691
python3 tools/danak_generate.py validate-drafts        # every draft still matches its snapshot
python3 tools/danak_generate.py prompts                # prompt versions

# a dry run with scripted answers, no model and no key:
DANAK_AI_PROVIDER=fake python3 tools/danak_generate.py run wikipedia-en-2678638-r1374122898 \
    --script tests/generate/fixtures/dunbar.json --out /tmp/drafts
```

Provider settings (environment only):

| Variable | |
|---|---|
| `DANAK_AI_PROVIDER` | `anthropic`, `openai-compatible` or `fake` |
| `DANAK_AI_MODEL` | model id |
| `ANTHROPIC_API_KEY` | key for `anthropic` |
| `DANAK_AI_EFFORT` | optional, `anthropic`: `low` … `max` |
| `DANAK_AI_BASE_URL` | `openai-compatible`: `https://…/v1`, or `http://localhost…/v1` for a local server |
| `DANAK_AI_API_KEY` | `openai-compatible`; unset for a local server without auth |
| `DANAK_AI_JSON_MODE` | `openai-compatible`: `json_schema` (default), `json_object` or `none` |
| `DANAK_AI_TIMEOUT` | seconds per request (180) |
| `DANAK_VERIFIER_*` | the same names for stage C; each falls back to `DANAK_AI_*` |

Keys come only from the environment (locally) or GitHub Actions secrets (CI). They are
never written to prompts, drafts, logs or the app; the Android app never calls a model.
