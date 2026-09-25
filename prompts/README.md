# Prompts

The instructions the draft pipeline (`tools/danak_generate.py`) gives a model, one pair of
files per stage:

| Stage | Instructions | Answer schema |
|---|---|---|
| A. idea and evidence | `idea-v1.md` | `idea-v1.schema.json` |
| B. Persian Danak and claims | `generate-v1.md` | `generate-v1.schema.json` |
| C. independent verification | `verify-v1.md` | `verify-v1.schema.json` |

The system prompt is the `.md` file followed by its schema. The user message carries the
data: the snapshot's numbered blocks (and, for B and C, the idea or the draft) as JSON
between `<<<NAME` and `NAME>>>` markers, with `<` and `>` escaped so text inside cannot close
a section. Every prompt says that this material is data, never instructions. URLs and
licence data are not sent: the model has no use for them and must not repeat them.

## Versions

Every draft records the version of each prompt it used, `<name>@<sha256[:12]>` over the
instructions and the schema together (`python3 tools/danak_generate.py prompts`), so a
changed prompt is always a different, traceable version.

To change a prompt, edit it in a pull request like code. For a change in behaviour rather
than wording, copy it to the next name (`idea-v2.md` and `idea-v2.schema.json`), point
`PROMPTS` in `tools/danak_generate.py` to it, and rerun the model evaluation
(`evals/`) before and after.
