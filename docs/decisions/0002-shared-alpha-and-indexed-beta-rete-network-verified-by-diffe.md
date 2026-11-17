# Shared Alpha and Indexed Beta Rete Network Verified by Differential Property Testing

Status: accepted (generated, pending human review)

## Context

Evaluating production rules via nested conditionals or naive loops re-scans working memory on every change, while full rule platforms introduce heavy dependencies and complex build toolchains.

## Decision

Build a zero-dependency Rete network with canonicalized shared alpha nodes, hash-indexed beta join nodes, blocker-set negative nodes, and an agenda supporting salience, recency, declaration order, and refraction, verified over 2,000 random rule programs against a naive reference evaluator.

## Consequences

Partial matches are maintained incrementally across fact insertions and retractions with deterministic firing order and mathematically pinned semantics.
