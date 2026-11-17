package io.github.trysac.rete_rule_engine;

import io.github.trysac.rete_rule_engine.ReteNetwork.Token;
import io.github.trysac.rete_rule_engine.RuleAst.Action;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Manages pending rule activations with conflict resolution (salience, recency, declaration order)
 * and refraction.
 */
final class Agenda {

  private final TreeSet<Activation> ordered = new TreeSet<>();
  private final Map<ActivationKey, Activation> activeByKey = new HashMap<>();
  private final Set<RefractionKey> firedTuples = new HashSet<>();

  void addActivation(Activation activation) {
    RefractionKey refKey = activation.refractionKey();
    if (firedTuples.contains(refKey)) {
      return;
    }
    ActivationKey key = new ActivationKey(activation.ruleName(), activation.token());
    if (activeByKey.containsKey(key)) {
      return;
    }
    activeByKey.put(key, activation);
    ordered.add(activation);
  }

  void removeActivation(String ruleName, Token token) {
    ActivationKey key = new ActivationKey(ruleName, token);
    Activation removed = activeByKey.remove(key);
    if (removed != null) {
      ordered.remove(removed);
    }
  }

  Activation pollNext() {
    Activation first = ordered.pollFirst();
    if (first != null) {
      activeByKey.remove(new ActivationKey(first.ruleName(), first.token()));
      firedTuples.add(first.refractionKey());
    }
    return first;
  }

  int size() {
    return ordered.size();
  }

  boolean isEmpty() {
    return ordered.isEmpty();
  }

  private record ActivationKey(String ruleName, Token token) {}

  record RefractionKey(String ruleName, List<Long> factIds) {}

  static final class Activation implements Comparable<Activation> {
    private final String ruleName;
    private final int salience;
    private final int declarationOrder;
    private final Token token;
    private final List<Action> actions;
    private final List<Long> factIds;
    private final long[] recencyDesc;

    Activation(
        String ruleName, int salience, int declarationOrder, Token token, List<Action> actions) {
      this.ruleName = ruleName;
      this.salience = salience;
      this.declarationOrder = declarationOrder;
      this.token = token;
      this.actions = actions;

      List<FactHandle> handles = token.handles();
      List<Long> ids = new ArrayList<>(handles.size());
      long[] sorted = new long[handles.size()];
      for (int i = 0; i < handles.size(); i++) {
        long id = handles.get(i).id();
        ids.add(id);
        sorted[i] = id;
      }
      Arrays.sort(sorted);
      long[] desc = new long[sorted.length];
      for (int i = 0; i < sorted.length; i++) {
        desc[i] = sorted[sorted.length - 1 - i];
      }
      this.factIds = List.copyOf(ids);
      this.recencyDesc = desc;
    }

    String ruleName() {
      return ruleName;
    }

    Token token() {
      return token;
    }

    List<Action> actions() {
      return actions;
    }

    List<Long> factIds() {
      return factIds;
    }

    RefractionKey refractionKey() {
      return new RefractionKey(ruleName, factIds);
    }

    @Override
    public int compareTo(Activation other) {
      if (this == other) {
        return 0;
      }
      int cmp = Integer.compare(other.salience, this.salience);
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
      cmp = Integer.compare(this.declarationOrder, other.declarationOrder);
      if (cmp != 0) {
        return cmp;
      }
      for (int i = 0; i < Math.min(this.factIds.size(), other.factIds.size()); i++) {
        cmp = Long.compare(this.factIds.get(i), other.factIds.get(i));
        if (cmp != 0) {
          return cmp;
        }
      }
      cmp = Integer.compare(this.factIds.size(), other.factIds.size());
      if (cmp != 0) {
        return cmp;
      }
      return Integer.compare(this.token.hashCode(), other.token.hashCode());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Activation that)) {
        return false;
      }
      return declarationOrder == that.declarationOrder
          && Objects.equals(ruleName, that.ruleName)
          && Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ruleName, declarationOrder, token);
    }
  }
}
