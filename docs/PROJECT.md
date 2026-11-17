# Project record

## Objective

To provide a compact, dependency-free, forward-chaining production rule engine built on a Rete network, offering deterministic conflict resolution (salience, recency, declaration order) and refraction, verified by differential property testing.

## Scope

- Forward-chaining Rete network.
- Rule DSL with salience, conditions, constraints, and insert/retract actions.
- Conflict resolution using salience, recency (fact IDs), and declaration order.
- Refraction: preventing re-firing on the same tuple of facts.
- Public API: `RuleSet`, `RuleParser`, `Session`, `Fact`, `Firing`, `NetworkStats`, `RuleException`.

## Out of scope

- In-place modification.
- Accumulate, collect, or exists patterns.
- OR-patterns.
- Truth maintenance (logical insert).
- Arithmetic expressions in rule conditions.
- Calling Java methods from rules.
- Rule compilation to bytecode.
- Persistence of sessions.

## Effort metrics

| Metric | Value |
|---|---|
| Tokens (input / output) | 3267845 / 37610 |
| Hours (human) | pending measurement |
| Cost (USD) | 1.2589 |

## Factory run u924 (measured from API usage)

| Date (UTC) | Phase | Model | Batch | Input | Output | Cache write | Cache read | Cost (USD) |
|---|---|---|---|---|---|---|---|---|
| 2026-11-17 | generation | google/gemini-4-argon | yes | 7374 | 31636 | 0 | 0 | 0.6622 |
| 2026-11-17 | fix-1 | google/gemini-3.8-flash | no | 79437 | 620 | 0 | 377179 | 0.0902 |
| 2026-11-17 | fix-2 | google/gemini-3.8-flash | no | 61621 | 420 | 0 | 518458 | 0.0867 |
| 2026-11-17 | fix-3 | google/gemini-3.8-flash | no | 11123 | 53 | 0 | 81331 | 0.0146 |
| 2026-11-17 | fix-4 | google/gemini-3.8-flash | no | 39108 | 271 | 0 | 268017 | 0.0504 |
| 2026-11-17 | fix-1 | google/gemini-3.8-flash | no | 93808 | 1199 | 0 | 319178 | 0.0988 |
| 2026-11-17 | audit | google/gemini-3.8-flash | no | 201740 | 2144 | 0 | 1186939 | 0.2484 |
| 2026-11-17 | docs | google/gemini-3.1-flash-lite | no | 22532 | 1267 | 0 | 0 | 0.0075 |

Total: $1.2589. Prices: vertex line, as measured by its adapter. Providers serve the current version of each model id, so the date tells which version answered; versions, launch stages and price sources per line are in the factory's model register (docs/MODELS.md).
