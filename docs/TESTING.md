# Testing

## Tests

- **Unit Tests**: `AgendaTest`, `FactTest`, `RuleParserTest`, `ReteNetworkTest`, `SessionTest` cover internal logic for nodes, Agenda sorting, rule parsing, and fact handling.
- **Property Testing**: `jqwik` property-based tests generate random programs and fact streams. These results are compared against a naive evaluator (the oracle) in `src/test/java/` that re-evaluates all rules against all facts on every cycle to ensure consistency.

## Coverage

- Policies require line coverage to remain at or above 80%.
- Tests are deterministic, require no network, and contain no timing dependencies.