# Security notes

## Threat model

`io.github.trysac:rete-rule-engine` is a pure library without I/O or external network calls. 

### Defended

- **Untrusted Input**: The parser enforces a strict source limit of 65,536 characters and throws `RuleException` for invalid syntax or logic, preventing resource exhaustion from massive rule sets.
- **Infinite Loops**: `Session` bounds execution to 10,000 firings, throwing `firing_limit` when exceeded.
- **Supply Chain**: The project uses no external dependencies beyond the standard library.

### Not defended

- Denial of Service via memory exhaustion: The engine holds state in memory (Alpha/Beta networks and Agenda). Extremely large sets of facts or rules may cause `OutOfMemoryError`.
- Logic flaws in user-provided rules: The engine executes exactly what is parsed; it does not validate rule correctness or termination for user logic.