package io.github.trysac.rete_rule_engine;

import java.util.List;
import java.util.Objects;

/**
 * Record of a single rule firing, capturing the rule name and the positive condition fact IDs in
 * pattern order.
 *
 * @param rule the name of the rule that fired
 * @param factIds the list of fact IDs that matched the positive patterns of the rule
 */
public record Firing(String rule, List<Long> factIds) {

  /**
   * Creates a firing record with an unmodifiable copy of fact IDs.
   *
   * @param rule the rule name
   * @param factIds the matched fact IDs
   */
  public Firing {
    Objects.requireNonNull(rule, "rule");
    Objects.requireNonNull(factIds, "factIds");
    factIds = List.copyOf(factIds);
  }
}
