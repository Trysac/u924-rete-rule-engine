# rete-rule-engine

Business rules written as nested if statements are re-evaluated against every fact on each change, becoming slow and hard to audit. Larger rule platforms add unnecessary runtime complexity. `rete-rule-engine` provides a dependency-free, Rete-based forward-chaining rule engine that maintains partial matches between changes, fires rules in a deterministic order, and is validated by differential testing against a naive evaluator.

## Usage

```java
RuleSet rules = RuleParser.parse(
    "rule \"adult\" when $p: Person(age >= 18, name == ?n) then insert Adult(name = ?n) end");
Session session = new Session(rules);
session.insert(Fact.of("Person", "name", "Ann", "age", 20L));
int fired = session.run();
List<Fact> adults = session.facts("Adult");
```

## API Summary

- **Compilation**: `RuleSet RuleParser.parse(String source)` parses rules into a set. Source length is limited to 65,536 characters.
- **Execution**: `Session` handles state. Use `insert(Fact fact)` to add data and `run()` to fire rules until the agenda is empty. A safety limit of 10,000 firings prevents infinite loops.
- **Data**: `Fact` holds immutable data (String, Long, Boolean). `Firing` records executed rules.
- **Errors**: `RuleException` provides machine-readable codes: `syntax`, `unbound_variable`, `duplicate_rule`, `unknown_type_in_action`, `invalid_value`, `input_too_large`, `firing_limit`.
- **Limits**: 
  - `MAX_SOURCE_LENGTH = 65,536`.
  - Max firings per `run()`: 10,000.

## Why not Drools or Easy Rules?

A dependency-free, readable Rete implementation with refraction and differential property testing. Drools is a heavy platform; Easy Rules lacks a Rete network.

## Security

See [docs/SECURITY.md](docs/SECURITY.md).