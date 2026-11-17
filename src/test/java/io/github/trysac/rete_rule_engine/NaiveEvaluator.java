package io.github.trysac.rete_rule_engine;

import io.github.trysac.rete_rule_engine.RuleAst.Action;
import io.github.trysac.rete_rule_engine.RuleAst.Condition;
import io.github.trysac.rete_rule_engine.RuleAst.Constraint;
import io.github.trysac.rete_rule_engine.RuleAst.FieldAssignment;
import io.github.trysac.rete_rule_engine.RuleAst.InsertAction;
import io.github.trysac.rete_rule_engine.RuleAst.LiteralOperand;
import io.github.trysac.rete_rule_engine.RuleAst.Op;
import io.github.trysac.rete_rule_engine.RuleAst.RetractAction;
import io.github.trysac.rete_rule_engine.RuleAst.RuleDef;
import io.github.trysac.rete_rule_engine.RuleAst.VarOperand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deliberately naive reference evaluator used as a test oracle.
 *
 * <p>Re-matches every rule against all facts in working memory from scratch on every cycle,
 * applying salience, recency, declaration-order conflict resolution and refraction.
 */
final class NaiveEvaluator {

  private final List<RuleDef> rules;
  private final int maxFirings;
  private final Map<Long, FactHandle> workingMemory = new LinkedHashMap<>();
  private final Set<FiredKey> fired = new HashSet<>();
  private final List<Firing> firings = new ArrayList<>();
  private long nextId = 1L;

  NaiveEvaluator(RuleSet ruleSet) {
    this(ruleSet, Session.DEFAULT_MAX_FIRINGS);
  }

  NaiveEvaluator(RuleSet ruleSet, int maxFirings) {
    this.rules = ruleSet.rules();
    this.maxFirings = maxFirings;
  }

  FactHandle insert(Fact fact) {
    long id = nextId++;
    FactHandle handle = new FactHandle(id, fact);
    workingMemory.put(id, handle);
    return handle;
  }

  void retract(FactHandle handle) {
    workingMemory.remove(handle.id());
  }

  int run() {
    int count = 0;
    while (true) {
      List<Candidate> allCandidates = computeCandidates();
      if (allCandidates.isEmpty()) {
        break;
      }
      if (count >= maxFirings) {
        throw new RuleException("firing_limit", "Naive firing limit exceeded");
      }
      Collections.sort(allCandidates);
      Candidate winner = allCandidates.get(0);
      fired.add(new FiredKey(winner.rule.name(), winner.factIds));
      firings.add(new Firing(winner.rule.name(), winner.factIds));
      count++;
      executeActions(winner);
    }
    return count;
  }

  List<Firing> firings() {
    return Collections.unmodifiableList(firings);
  }

  List<Fact> facts(String type) {
    List<Fact> out = new ArrayList<>();
    for (FactHandle fh : workingMemory.values()) {
      if (fh.fact().type().equals(type)) {
        out.add(fh.fact());
      }
    }
    return out;
  }

  private void executeActions(Candidate winner) {
    for (Action action : winner.rule.actions()) {
      if (action instanceof InsertAction(String type, List<FieldAssignment> assignments)) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldAssignment fa : assignments) {
          Object val;
          if (fa.value() instanceof LiteralOperand(Object lit)) {
            val = lit;
          } else if (fa.value() instanceof VarOperand(String varName)) {
            val = winner.bindings.get(varName);
          } else {
            throw new AssertionError();
          }
          fields.put(fa.field(), val);
        }
        insert(new Fact(type, fields));
      } else if (action instanceof RetractAction(String ignored, int condIdx)) {
        FactHandle handle = winner.handles.get(condIdx);
        retract(handle);
      }
    }
  }

  private List<Candidate> computeCandidates() {
    List<Candidate> candidates = new ArrayList<>();
    List<FactHandle> allFacts = new ArrayList<>(workingMemory.values());
    for (RuleDef rule : rules) {
      matchConditions(rule, 0, new ArrayList<>(), new HashMap<>(), allFacts, candidates);
    }
    return candidates;
  }

  private void matchConditions(
      RuleDef rule,
      int condIdx,
      List<FactHandle> currentHandles,
      Map<String, Object> currentBindings,
      List<FactHandle> allFacts,
      List<Candidate> out) {
    if (condIdx == rule.conditions().size()) {
      List<Long> ids = currentHandles.stream().map(FactHandle::id).toList();
      FiredKey fk = new FiredKey(rule.name(), ids);
      if (!fired.contains(fk)) {
        out.add(new Candidate(rule, List.copyOf(currentHandles), ids, Map.copyOf(currentBindings)));
      }
      return;
    }

    Condition cond = rule.conditions().get(condIdx);
    if (!cond.negated()) {
      for (FactHandle fh : allFacts) {
        if (!fh.fact().type().equals(cond.type())) {
          continue;
        }
        Map<String, Object> nextBindings = new HashMap<>(currentBindings);
        if (matchesConstraints(fh.fact(), cond.constraints(), nextBindings)) {
          currentHandles.add(fh);
          matchConditions(rule, condIdx + 1, currentHandles, nextBindings, allFacts, out);
          currentHandles.remove(currentHandles.size() - 1);
        }
      }
    } else {
      boolean anyMatch = false;
      for (FactHandle fh : allFacts) {
        if (!fh.fact().type().equals(cond.type())) {
          continue;
        }
        Map<String, Object> tempBindings = new HashMap<>(currentBindings);
        if (matchesConstraints(fh.fact(), cond.constraints(), tempBindings)) {
          anyMatch = true;
          break;
        }
      }
      if (!anyMatch) {
        matchConditions(rule, condIdx + 1, currentHandles, currentBindings, allFacts, out);
      }
    }
  }

  private static boolean matchesConstraints(
      Fact fact, List<Constraint> constraints, Map<String, Object> bindings) {
    for (Constraint c : constraints) {
      Object fieldVal = fact.fields().get(c.field());
      if (fieldVal == null) {
        return false;
      }
      if (c.operand() instanceof LiteralOperand(Object lit)) {
        if (!c.op().evaluate(fieldVal, lit)) {
          return false;
        }
      } else if (c.operand() instanceof VarOperand(String varName)) {
        if (bindings.containsKey(varName)) {
          Object boundVal = bindings.get(varName);
          if (!c.op().evaluate(fieldVal, boundVal)) {
            return false;
          }
        } else {
          if (c.op() != Op.EQ) {
            return false;
          }
          bindings.put(varName, fieldVal);
        }
      }
    }
    return true;
  }

  private record FiredKey(String ruleName, List<Long> factIds) {}

  private static final class Candidate implements Comparable<Candidate> {
    private final RuleDef rule;
    private final List<FactHandle> handles;
    private final List<Long> factIds;
    private final Map<String, Object> bindings;
    private final long[] recencyDesc;

    Candidate(
        RuleDef rule, List<FactHandle> handles, List<Long> factIds, Map<String, Object> bindings) {
      this.rule = rule;
      this.handles = handles;
      this.factIds = factIds;
      this.bindings = bindings;
      long[] sorted = new long[factIds.size()];
      for (int i = 0; i < factIds.size(); i++) {
        sorted[i] = factIds.get(i);
      }
      Arrays.sort(sorted);
      long[] desc = new long[sorted.length];
      for (int i = 0; i < sorted.length; i++) {
        desc[i] = sorted[sorted.length - 1 - i];
      }
      this.recencyDesc = desc;
    }

    @Override
    public int compareTo(Candidate other) {
      int cmp = Integer.compare(other.rule.salience(), this.rule.salience());
      if (cmp != 0) {
        return cmp;
      }
      int minLen = Math.min(this.recencyDesc.length, other.recencyDesc.length);
      for (int i = 0; i < minLen; i++) {
        cmp = Long.compare(other.recencyDesc[i], this.recencyDesc[i]);
        if (cmp != 0) {
          return cmp;
        }
      }
      cmp = Integer.compare(other.recencyDesc.length, this.recencyDesc.length);
      if (cmp != 0) {
        return cmp;
      }
      cmp = Integer.compare(this.rule.declarationOrder(), other.rule.declarationOrder());
      if (cmp != 0) {
        return cmp;
      }
      for (int i = 0; i < Math.min(this.factIds.size(), other.factIds.size()); i++) {
        cmp = Long.compare(this.factIds.get(i), other.factIds.get(i));
        if (cmp != 0) {
          return cmp;
        }
      }
      return Integer.compare(this.factIds.size(), other.factIds.size());
    }
  }
}
