package io.github.trysac.rete_rule_engine;

/**
 * Structural metrics of the compiled Rete network.
 *
 * @param alphaNodeCount number of distinct shared alpha nodes in the network
 * @param betaNodeCount number of beta join and negative nodes in the network
 */
public record NetworkStats(int alphaNodeCount, int betaNodeCount) {}
