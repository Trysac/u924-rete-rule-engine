package io.github.trysac.rete_rule_engine;

import java.util.List;

/** An immutable compiled set of rules parsed by {@link RuleParser}. */
public final class RuleSet {

  private final List<RuleAst.RuleDef> rules;

  RuleSet(List<RuleAst.RuleDef> rules) {
    this.rules = List.copyOf(rules);
  }

  List<RuleAst.RuleDef> rules() {
    return rules;
  }

  /**
   * Returns the number of rules in this rule set.
   *
   * @return the rule count
   */
  public int size() {
    return rules.size();
  }
}
