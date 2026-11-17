# Architecture

## Overview

`rete-rule-engine` compiles declarative rules written in a compact DSL into a shared Rete discrimination network:

1. **Parser (`RuleParser`)**: Tokenizes and parses rules into an immutable `RuleSet`, validating source size (`<= 65,536` characters), unique rule names, variable binding order, and type names.
2. **Alpha Network (`ReteNetwork.AlphaNode`)**: Deduplicates `(type, constantTests, selfTests)` across all rules so identical patterns share a single alpha memory.
3. **Beta Network (`JoinNode`, `NegativeNode`, `TerminalNode`)**:
   - `JoinNode` maintains left (tuple) and right (fact) memories indexed by equality join variables.
   - `NegativeNode` tracks blocking right-hand facts per left tuple and propagates the tuple only when its blocker set is empty.
   - `TerminalNode` pushes matches onto and retracts matches from the `Agenda`.
4. **Agenda (`Agenda` & `Session`)**: Orders activations by salience (descending), sorted tuple fact-ID recency (descending), and rule declaration order (ascending), while enforcing refraction and a configurable `maxFirings` bound.
