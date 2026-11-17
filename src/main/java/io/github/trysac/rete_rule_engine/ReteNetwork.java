package io.github.trysac.rete_rule_engine;

import io.github.trysac.rete_rule_engine.RuleAst.Action;
import io.github.trysac.rete_rule_engine.RuleAst.Condition;
import io.github.trysac.rete_rule_engine.RuleAst.Constraint;
import io.github.trysac.rete_rule_engine.RuleAst.LiteralOperand;
import io.github.trysac.rete_rule_engine.RuleAst.Op;
import io.github.trysac.rete_rule_engine.RuleAst.RuleDef;
import io.github.trysac.rete_rule_engine.RuleAst.VarOperand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Internal Rete network implementation containing shared alpha nodes, beta join nodes, negative
 * nodes, and terminal nodes.
 */
final class ReteNetwork {

  private final Map<AlphaKey, AlphaNode> alphaNodes = new LinkedHashMap<>();
  private final Map<String, List<AlphaNode>> alphaNodesByType = new HashMap<>();
  private final List<BetaNode> allBetaNodes = new ArrayList<>();
  private final List<NegativeNode> rootNegativeNodes = new ArrayList<>();
  private final Agenda agenda;

  ReteNetwork(RuleSet ruleSet, Agenda agenda) {
    this.agenda = agenda;
    build(ruleSet.rules());
  }

  NetworkStats stats() {
    return new NetworkStats(alphaNodes.size(), allBetaNodes.size());
  }

  void assertFact(FactHandle handle) {
    List<AlphaNode> nodes = alphaNodesByType.get(handle.fact().type());
    if (nodes != null) {
      for (AlphaNode alpha : nodes) {
        alpha.assertFact(handle);
      }
    }
  }

  void retractFact(FactHandle handle) {
    List<AlphaNode> nodes = alphaNodesByType.get(handle.fact().type());
    if (nodes != null) {
      for (AlphaNode alpha : nodes) {
        alpha.retractFact(handle);
      }
    }
  }

  private void build(List<RuleDef> rules) {
    Token emptyToken = new Token(List.of(), Map.of());
    for (RuleDef rule : rules) {
      buildRule(rule);
    }
    for (NegativeNode rootNeg : rootNegativeNodes) {
      rootNeg.leftAssert(emptyToken);
    }
  }

  private void buildRule(RuleDef rule) {
    TokenSource currentSource = null;
    Set<String> boundVarsSoFar = new HashSet<>();

    for (Condition cond : rule.conditions()) {
      CompiledPattern pattern = compilePattern(cond, boundVarsSoFar);
      AlphaNode alpha = getOrCreateAlpha(pattern.alphaKey());

      if (!cond.negated()) {
        JoinNode join = new JoinNode(alpha, pattern.betaTests(), pattern.newBindings());
        allBetaNodes.add(join);
        if (currentSource == null) {
          alpha.addRootChild(join);
        } else {
          currentSource.addChild(join);
          alpha.addRightChild(join);
        }
        currentSource = join;
        boundVarsSoFar.addAll(pattern.newBindings().keySet());
      } else {
        NegativeNode neg = new NegativeNode(alpha, pattern.betaTests(), pattern.localSelfTests());
        allBetaNodes.add(neg);
        alpha.addRightChild(neg);
        if (currentSource == null) {
          rootNegativeNodes.add(neg);
        } else {
          currentSource.addChild(neg);
        }
        currentSource = neg;
      }
    }

    TerminalNode terminal =
        new TerminalNode(
            rule.name(), rule.salience(), rule.declarationOrder(), rule.actions(), agenda);
    if (currentSource != null) {
      currentSource.addChild(terminal);
    }
  }

  private AlphaNode getOrCreateAlpha(AlphaKey key) {
    AlphaNode existing = alphaNodes.get(key);
    if (existing != null) {
      return existing;
    }
    AlphaNode created = new AlphaNode(key);
    alphaNodes.put(key, created);
    alphaNodesByType.computeIfAbsent(key.type(), k -> new ArrayList<>()).add(created);
    return created;
  }

  private CompiledPattern compilePattern(Condition cond, Set<String> boundVarsBefore) {
    List<ConstantTest> constantTests = new ArrayList<>();
    List<BetaTest> betaTests = new ArrayList<>();
    List<SelfTest> selfTests = new ArrayList<>();
    Map<String, String> newBindings = new LinkedHashMap<>();

    for (Constraint c : cond.constraints()) {
      if (c.operand() instanceof LiteralOperand(Object literal)) {
        constantTests.add(new ConstantTest(c.field(), c.op(), literal));
      } else if (c.operand() instanceof VarOperand(String varName)) {
        if (boundVarsBefore.contains(varName)) {
          betaTests.add(new BetaTest(c.field(), c.op(), varName));
        } else if (newBindings.containsKey(varName)) {
          String firstField = newBindings.get(varName);
          selfTests.add(new SelfTest(c.field(), c.op(), firstField));
        } else {
          newBindings.put(varName, c.field());
        }
      }
    }

    List<ConstantTest> canonicalConstants = new ArrayList<>(constantTests);
    canonicalConstants.sort(ConstantTest::compareTo);

    List<SelfTest> alphaSelfTests = cond.negated() ? List.of() : List.copyOf(selfTests);
    List<SelfTest> localNegSelfTests = cond.negated() ? List.copyOf(selfTests) : List.of();

    AlphaKey alphaKey = new AlphaKey(cond.type(), List.copyOf(canonicalConstants), alphaSelfTests);
    return new CompiledPattern(
        alphaKey,
        List.copyOf(betaTests),
        localNegSelfTests,
        Collections.unmodifiableMap(newBindings));
  }

  record ConstantTest(String field, Op op, Object value) implements Comparable<ConstantTest> {
    boolean matches(Fact fact) {
      Object actual = fact.fields().get(field);
      if (actual == null) {
        return false;
      }
      return op.evaluate(actual, value);
    }

    @Override
    public int compareTo(ConstantTest other) {
      int cmp = this.field.compareTo(other.field);
      if (cmp != 0) {
        return cmp;
      }
      cmp = this.op.compareTo(other.op);
      if (cmp != 0) {
        return cmp;
      }
      return String.valueOf(this.value).compareTo(String.valueOf(other.value));
    }
  }

  record SelfTest(String field, Op op, String otherField) {
    boolean matches(Fact fact) {
      Object left = fact.fields().get(field);
      Object right = fact.fields().get(otherField);
      if (left == null || right == null) {
        return false;
      }
      return op.evaluate(left, right);
    }
  }

  record BetaTest(String rightField, Op op, String boundVar) {
    boolean isEquality() {
      return op == Op.EQ;
    }

    boolean matches(Token leftToken, Fact rightFact) {
      Object rightVal = rightFact.fields().get(rightField);
      Object leftVal = leftToken.bindings().get(boundVar);
      if (rightVal == null || leftVal == null) {
        return false;
      }
      return op.evaluate(rightVal, leftVal);
    }
  }

  record AlphaKey(String type, List<ConstantTest> constantTests, List<SelfTest> selfTests) {}

  private record CompiledPattern(
      AlphaKey alphaKey,
      List<BetaTest> betaTests,
      List<SelfTest> localSelfTests,
      Map<String, String> newBindings) {}

  /** Represents a tuple of matched facts and variable bindings flowing through the beta network. */
  static final class Token {
    private final List<FactHandle> handles;
    private final Map<String, Object> bindings;
    private final int hash;

    Token(List<FactHandle> handles, Map<String, Object> bindings) {
      this.handles = List.copyOf(handles);
      this.bindings = Collections.unmodifiableMap(bindings);
      this.hash = Objects.hash(this.handles, this.bindings);
    }

    List<FactHandle> handles() {
      return handles;
    }

    Map<String, Object> bindings() {
      return bindings;
    }

    Token extend(FactHandle handle, Map<String, String> newVarFields) {
      List<FactHandle> nextHandles = new ArrayList<>(handles.size() + 1);
      nextHandles.addAll(handles);
      nextHandles.add(handle);

      Map<String, Object> nextBindings;
      if (newVarFields.isEmpty()) {
        nextBindings = bindings;
      } else {
        nextBindings = new HashMap<>(bindings.size() + newVarFields.size());
        nextBindings.putAll(bindings);
        for (Map.Entry<String, String> entry : newVarFields.entrySet()) {
          Object val = handle.fact().fields().get(entry.getValue());
          nextBindings.put(entry.getKey(), val);
        }
      }
      return new Token(nextHandles, nextBindings);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Token that)) {
        return false;
      }
      return hash == that.hash && handles.equals(that.handles) && bindings.equals(that.bindings);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  interface TokenSink {
    void leftAssert(Token token);

    void leftRetract(Token token);
  }

  interface RightSink {
    void rightAssert(FactHandle handle);

    void rightRetract(FactHandle handle);
  }

  interface TokenSource {
    void addChild(TokenSink sink);
  }

  interface BetaNode {}

  static final class AlphaNode {
    private final AlphaKey key;
    private final Set<FactHandle> memory = new LinkedHashSet<>();
    private final List<JoinNode> rootChildren = new ArrayList<>();
    private final List<RightSink> rightChildren = new ArrayList<>();

    AlphaNode(AlphaKey key) {
      this.key = key;
    }

    Set<FactHandle> memory() {
      return memory;
    }

    void addRootChild(JoinNode join) {
      rootChildren.add(join);
    }

    void addRightChild(RightSink sink) {
      rightChildren.add(sink);
    }

    void assertFact(FactHandle handle) {
      Fact fact = handle.fact();
      for (ConstantTest ct : key.constantTests()) {
        if (!ct.matches(fact)) {
          return;
        }
      }
      for (SelfTest st : key.selfTests()) {
        if (!st.matches(fact)) {
          return;
        }
      }
      if (!memory.add(handle)) {
        return;
      }
      for (JoinNode root : rootChildren) {
        root.rootAssert(handle);
      }
      for (RightSink child : rightChildren) {
        child.rightAssert(handle);
      }
    }

    void retractFact(FactHandle handle) {
      if (!memory.remove(handle)) {
        return;
      }
      for (JoinNode root : rootChildren) {
        root.rootRetract(handle);
      }
      for (RightSink child : rightChildren) {
        child.rightRetract(handle);
      }
    }
  }

  static final class JoinKey {
    private final Object[] values;
    private final int hash;

    JoinKey(Object[] values) {
      this.values = values;
      this.hash = Arrays.hashCode(values);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof JoinKey that)) {
        return false;
      }
      return hash == that.hash && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  static final class JoinNode implements BetaNode, TokenSink, RightSink, TokenSource {
    private final List<BetaTest> equalityTests;
    private final List<BetaTest> nonEqualityTests;
    private final Map<String, String> newBindings;
    private final List<TokenSink> children = new ArrayList<>();

    private final Set<Token> leftMemory = new LinkedHashSet<>();
    private final Set<FactHandle> rightMemory = new LinkedHashSet<>();
    private final Map<JoinKey, Set<Token>> leftIndex = new HashMap<>();
    private final Map<JoinKey, Set<FactHandle>> rightIndex = new HashMap<>();

    JoinNode(AlphaNode alpha, List<BetaTest> betaTests, Map<String, String> newBindings) {
      Objects.requireNonNull(alpha, "alpha");
      this.newBindings = newBindings;
      List<BetaTest> eq = new ArrayList<>();
      List<BetaTest> neq = new ArrayList<>();
      for (BetaTest bt : betaTests) {
        if (bt.isEquality()) {
          eq.add(bt);
        } else {
          neq.add(bt);
        }
      }
      this.equalityTests = List.copyOf(eq);
      this.nonEqualityTests = List.copyOf(neq);
    }

    @Override
    public void addChild(TokenSink sink) {
      children.add(sink);
    }

    void rootAssert(FactHandle handle) {
      if (!satisfiesNewBindingsPresent(handle.fact())) {
        return;
      }
      Token token = new Token(List.of(), Map.of()).extend(handle, newBindings);
      for (TokenSink child : children) {
        child.leftAssert(token);
      }
    }

    void rootRetract(FactHandle handle) {
      if (!satisfiesNewBindingsPresent(handle.fact())) {
        return;
      }
      Token token = new Token(List.of(), Map.of()).extend(handle, newBindings);
      for (TokenSink child : children) {
        child.leftRetract(token);
      }
    }

    @Override
    public void leftAssert(Token token) {
      if (!leftMemory.add(token)) {
        return;
      }
      JoinKey key = computeLeftKey(token);
      if (key == null) {
        return;
      }
      if (!equalityTests.isEmpty()) {
        leftIndex.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(token);
      }
      Iterable<FactHandle> candidates =
          equalityTests.isEmpty()
              ? rightMemory
              : rightIndex.getOrDefault(key, Collections.emptySet());
      for (FactHandle handle : candidates) {
        if (matchesNonEquality(token, handle.fact())) {
          Token joined = token.extend(handle, newBindings);
          for (TokenSink child : children) {
            child.leftAssert(joined);
          }
        }
      }
    }

    @Override
    public void leftRetract(Token token) {
      if (!leftMemory.remove(token)) {
        return;
      }
      JoinKey key = computeLeftKey(token);
      if (key == null) {
        return;
      }
      if (!equalityTests.isEmpty()) {
        Set<Token> bucket = leftIndex.get(key);
        if (bucket != null) {
          bucket.remove(token);
          if (bucket.isEmpty()) {
            leftIndex.remove(key);
          }
        }
      }
      Iterable<FactHandle> candidates =
          equalityTests.isEmpty()
              ? rightMemory
              : rightIndex.getOrDefault(key, Collections.emptySet());
      for (FactHandle handle : candidates) {
        if (matchesNonEquality(token, handle.fact())) {
          Token joined = token.extend(handle, newBindings);
          for (TokenSink child : children) {
            child.leftRetract(joined);
          }
        }
      }
    }

    @Override
    public void rightAssert(FactHandle handle) {
      if (!satisfiesNewBindingsPresent(handle.fact())) {
        return;
      }
      JoinKey key = computeRightKey(handle.fact());
      if (key == null) {
        return;
      }
      if (!rightMemory.add(handle)) {
        return;
      }
      if (!equalityTests.isEmpty()) {
        rightIndex.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(handle);
      }
      Iterable<Token> candidates =
          equalityTests.isEmpty()
              ? leftMemory
              : leftIndex.getOrDefault(key, Collections.emptySet());
      for (Token token : candidates) {
        if (matchesNonEquality(token, handle.fact())) {
          Token joined = token.extend(handle, newBindings);
          for (TokenSink child : children) {
            child.leftAssert(joined);
          }
        }
      }
    }

    @Override
    public void rightRetract(FactHandle handle) {
      if (!rightMemory.remove(handle)) {
        return;
      }
      JoinKey key = computeRightKey(handle.fact());
      if (key == null) {
        return;
      }
      if (!equalityTests.isEmpty()) {
        Set<FactHandle> bucket = rightIndex.get(key);
        if (bucket != null) {
          bucket.remove(handle);
          if (bucket.isEmpty()) {
            rightIndex.remove(key);
          }
        }
      }
      Iterable<Token> candidates =
          equalityTests.isEmpty()
              ? leftMemory
              : leftIndex.getOrDefault(key, Collections.emptySet());
      for (Token token : candidates) {
        if (matchesNonEquality(token, handle.fact())) {
          Token joined = token.extend(handle, newBindings);
          for (TokenSink child : children) {
            child.leftRetract(joined);
          }
        }
      }
    }

    private boolean satisfiesNewBindingsPresent(Fact fact) {
      for (String field : newBindings.values()) {
        if (!fact.fields().containsKey(field)) {
          return false;
        }
      }
      return true;
    }

    private JoinKey computeLeftKey(Token token) {
      if (equalityTests.isEmpty()) {
        return new JoinKey(new Object[0]);
      }
      Object[] vals = new Object[equalityTests.size()];
      for (int i = 0; i < equalityTests.size(); i++) {
        Object val = token.bindings().get(equalityTests.get(i).boundVar());
        if (val == null) {
          return null;
        }
        vals[i] = val;
      }
      return new JoinKey(vals);
    }

    private JoinKey computeRightKey(Fact fact) {
      if (equalityTests.isEmpty()) {
        return new JoinKey(new Object[0]);
      }
      Object[] vals = new Object[equalityTests.size()];
      for (int i = 0; i < equalityTests.size(); i++) {
        Object val = fact.fields().get(equalityTests.get(i).rightField());
        if (val == null) {
          return null;
        }
        vals[i] = val;
      }
      return new JoinKey(vals);
    }

    private boolean matchesNonEquality(Token token, Fact fact) {
      for (BetaTest bt : nonEqualityTests) {
        if (!bt.matches(token, fact)) {
          return false;
        }
      }
      return true;
    }
  }

  static final class NegativeNode implements BetaNode, TokenSink, RightSink, TokenSource {
    private final List<BetaTest> betaTests;
    private final List<SelfTest> localSelfTests;
    private final List<TokenSink> children = new ArrayList<>();
    private final Map<Token, Set<FactHandle>> blockersByToken = new LinkedHashMap<>();
    private final Set<FactHandle> rightMemory = new LinkedHashSet<>();

    NegativeNode(AlphaNode alpha, List<BetaTest> betaTests, List<SelfTest> localSelfTests) {
      Objects.requireNonNull(alpha, "alpha");
      this.betaTests = betaTests;
      this.localSelfTests = localSelfTests;
    }

    @Override
    public void addChild(TokenSink sink) {
      children.add(sink);
    }

    @Override
    public void leftAssert(Token token) {
      if (blockersByToken.containsKey(token)) {
        return;
      }
      Set<FactHandle> blockers = new LinkedHashSet<>();
      for (FactHandle right : rightMemory) {
        if (matches(token, right.fact())) {
          blockers.add(right);
        }
      }
      blockersByToken.put(token, blockers);
      if (blockers.isEmpty()) {
        for (TokenSink child : children) {
          child.leftAssert(token);
        }
      }
    }

    @Override
    public void leftRetract(Token token) {
      Set<FactHandle> blockers = blockersByToken.remove(token);
      if (blockers != null && blockers.isEmpty()) {
        for (TokenSink child : children) {
          child.leftRetract(token);
        }
      }
    }

    @Override
    public void rightAssert(FactHandle handle) {
      for (SelfTest st : localSelfTests) {
        if (!st.matches(handle.fact())) {
          return;
        }
      }
      if (!rightMemory.add(handle)) {
        return;
      }
      for (Map.Entry<Token, Set<FactHandle>> entry : blockersByToken.entrySet()) {
        Token token = entry.getKey();
        if (matches(token, handle.fact())) {
          Set<FactHandle> blockers = entry.getValue();
          boolean wasEmpty = blockers.isEmpty();
          blockers.add(handle);
          if (wasEmpty) {
            for (TokenSink child : children) {
              child.leftRetract(token);
            }
          }
        }
      }
    }

    @Override
    public void rightRetract(FactHandle handle) {
      if (!rightMemory.remove(handle)) {
        return;
      }
      for (Map.Entry<Token, Set<FactHandle>> entry : blockersByToken.entrySet()) {
        Token token = entry.getKey();
        Set<FactHandle> blockers = entry.getValue();
        if (blockers.remove(handle) && blockers.isEmpty()) {
          for (TokenSink child : children) {
            child.leftAssert(token);
          }
        }
      }
    }

    private boolean matches(Token token, Fact fact) {
      for (SelfTest st : localSelfTests) {
        if (!st.matches(fact)) {
          return false;
        }
      }
      for (BetaTest bt : betaTests) {
        if (!bt.matches(token, fact)) {
          return false;
        }
      }
      return true;
    }
  }

  static final class TerminalNode implements TokenSink {
    private final String ruleName;
    private final int salience;
    private final int declarationOrder;
    private final List<Action> actions;
    private final Agenda agenda;

    TerminalNode(
        String ruleName, int salience, int declarationOrder, List<Action> actions, Agenda agenda) {
      this.ruleName = ruleName;
      this.salience = salience;
      this.declarationOrder = declarationOrder;
      this.actions = actions;
      this.agenda = agenda;
    }

    @Override
    public void leftAssert(Token token) {
      agenda.addActivation(
          new Agenda.Activation(ruleName, salience, declarationOrder, token, actions));
    }

    @Override
    public void leftRetract(Token token) {
      agenda.removeActivation(ruleName, token);
    }
  }
}
