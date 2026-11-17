# rete-rule-engine

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![CI](https://github.com/Trysac/u924-rete-rule-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/Trysac/u924-rete-rule-engine/actions/workflows/ci.yml)

Forward-chaining production rule engine built on a Rete network with a small rule DSL, salience and recency conflict resolution, checked against a naive evaluator

## Problem

Describe in two or three sentences the concrete problem this utility solves, who hits it, and what
they do today without it. Replace this paragraph when the first real feature lands.

## Install

Maven coordinates `io.github.trysac:rete-rule-engine`. Until it is published to Maven Central, build
it from a release tag with `mvn install`, then declare:

```xml
<dependency>
  <groupId>io.github.trysac</groupId>
  <artifactId>rete-rule-engine</artifactId>
  <version>0.1.0</version>
</dependency>
```

Requires Java 21 or newer. No dependencies outside the standard library.

## Usage

```java
import io.github.trysac.rete_rule_engine.Text;

// normalize trims surrounding whitespace and lower-cases the text.
String name = Text.normalize("  Hello World "); // "hello world"

// Blank input is rejected explicitly instead of returning an empty string.
Text.normalize("   "); // throws BlankInputException ("input is blank")
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md) and [decisions](docs/decisions/)
- [Testing](docs/TESTING.md) and [security](docs/SECURITY.md)
- [Project record](docs/PROJECT.md) and [stack](docs/STACK.md)
- [Changelog](CHANGELOG.md) and [contributing guide](CONTRIBUTING.md)

## License

[MIT](LICENSE) (c) 2026 Miguel Gomez
