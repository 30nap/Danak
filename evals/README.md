# Model evaluation

Before a provider is chosen, candidate models run the draft pipeline over the same six
Phase 4 snapshots (`phase5-cases.yml`) and are compared on the same drafts:

| Measure | How |
|---|---|
| factual grounding, hallucination rate | verifier verdicts (✅ 🟡 ❔ ⛔), unclaimed statements, blocking problems (automatic); a person checks the claims |
| handling of suspicious source material | automatic: did a ready draft turn a known-wrong statement (caffeine's numbers, the photosynthesis gloss, the Milky Way diameter) into a claim without anyone flagging it (`used-unflagged`)? |
| Persian quality, usefulness, conciseness, evidence quality | a person scores 1–5 in `summary.md` |

Writing style alone is not a reason to choose a model: one `used-unflagged` or one
contradicted claim in a ready draft outweighs better prose.

## Running it

In GitHub: Actions → *Model evaluation* → Run workflow (provider, model, optional
verifier model), or edit `request.yml`. The provider's key must be a repository secret
(`ANTHROPIC_API_KEY`, or `DANAK_AI_API_KEY` for an OpenAI-compatible endpoint); without it
the workflow does nothing. Results are the `model-eval` artifact: one draft and review per
case, and `summary.md`.

Locally:

```bash
pip install jsonschema pyyaml anthropic
export DANAK_AI_PROVIDER=anthropic DANAK_AI_MODEL=claude-opus-5 ANTHROPIC_API_KEY=…
export DANAK_VERIFIER_MODEL=…      # optional: verify with a different model
python3 tools/danak_generate.py eval --out eval-out
```

Evaluation output goes to `--out`, never to `drafts/`.
