package io.github.trysac.rete_rule_engine;

import io.github.trysac.rete_rule_engine.Agenda.Activation;
import io.github.trysac.rete_rule_engine.RuleAst.Action;
import io.github.trysac.rete_rule_engine.RuleAst.FieldAssignment;
import io.github.trysac.rete_rule_engine.RuleAst.InsertAction;
import io.github.trysac.rete_rule_engine.RuleAst.LiteralOperand;
import io.github.trysac.rete_rule_engine.RuleAst.RetractAction;
import io.github.trysac.rete_rule_engine.RuleAst.VarOperand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stateful working-memory session backed by a compiled Rete network.
 *
 * <p>Supports inserting and retracting immutable {@link Fact} instances and firing activated rules
 * until the agenda is empty or {@code maxFirings} is reached.
 */
public final class Session {

  /** Default maximum number of rule firings allowed per {@link #run()} invocation. */
  public static final int DEFAULT_MAX_FIRINGS = 10_000;

  /** Maximum number of facts allowed in working memory simultaneously. */
  public static final int MAX_WORKING_MEMORY_FACTS = 100_000;

  private final int maxFirings;
  private final Agenda agenda;
  private final ReteNetwork network;
  private final Map<Long, FactHandle> workingMemory = new LinkedHashMap<>();
  private final List<Firing> firingsHistory = new ArrayList<>();
  private long nextFactId = 1L;

  /**
   * Creates a new session for the given {@link RuleSet} with the default firing limit of {@code
   * 10,000}.
   *
   * @param rules the compiled rule set
   * @throws NullPointerException if {@code rules} is null
   */
  public Session(RuleSet rules) {
    this(rules, DEFAULT_MAX_FIRINGS);
  }

  /**
   * Creates a new session for the given {@link RuleSet} and custom maximum firing limit.
   *
   * @param rules the compiled rule set
   * @param maxFirings maximum number of firings allowed in {@link #run()}
   * @throws NullPointerException if {@code rules} is null
   * @throws RuleException with code {@code "invalid_value"} if {@code maxFirings <= 0}
   */
  public Session(RuleSet rules, int maxFirings) {
    Objects.requireNonNull(rules, "rules");
    if (maxFirings <= 0) {
      throw new RuleException("invalid_value", "maxFirings must be positive, got " + maxFirings);
    }
    this.maxFirings = maxFirings;
    this.agenda = new Agenda();
    this.network = new ReteNetwork(rules, this.agenda);
  }

  /**
   * Inserts an immutable {@link Fact} into working memory and propagates it through the Rete
   * network.
   *
   * @param fact the fact to insert
   * @return the assigned {@link FactHandle} with a monotonically increasing ID
   * @throws NullPointerException if {@code fact} is null
   * @throws RuleException with code {@code "input_too_large"} if working memory exceeds capacity
   */
  public FactHandle insert(Fact fact) {
    Objects.requireNonNull(fact, "fact");
    if (workingMemory.size() >= MAX_WORKING_MEMORY_FACTS) {
      throw new RuleException(
          "input_too_large",
          "Working memory capacity of " + MAX_WORKING_MEMORY_FACTS + " facts exceeded");
    }
    long id = nextFactId++;
    FactHandle handle = new FactHandle(id, fact);
    workingMemory.put(id, handle);
    network.assertFact(handle);
    return handle;
  }

  /**
   * Retracts the fact associated with the given {@link FactHandle} from working memory and removes
   * any pending activations that depended on it.
   *
   * <p>Retracting a handle that was already retracted is a no-op.
   *
   * @param handle the fact handle to retract
   * @throws NullPointerException if {@code handle} is null
   */
  public void retract(FactHandle handle) {
    Objects.requireNonNull(handle, "handle");
    FactHandle removed = workingMemory.remove(handle.id());
    if (removed != null) {
      network.retractFact(removed);
    }
  }

  /**
   * Fires rules on the agenda in conflict-resolution order until the agenda is empty.
   *
   * @return the number of rule firings executed during this call
   * @throws RuleException with code {@code "firing_limit"} if more than {@code maxFirings} rules
   *     fire
   */
  public int run() {
    int firedInThisRun = 0;
    while (!agenda.isEmpty()) {
      if (firedInThisRun >= maxFirings) {
        throw new RuleException("firing_limit", "Rule firing limit of " + maxFirings + " exceeded");
      }
      Activation activation = agenda.pollNext();
      if (activation == null) {
        break;
      }
      firedInThisRun++;
      firingsHistory.add(new Firing(activation.ruleName(), activation.factIds()));
      executeActions(activation);
    }
    return firedInThisRun;
  }

  private void executeActions(Activation activation) {
    Map<String, Object> bindings = activation.token().bindings();
    List<FactHandle> matchedHandles = activation.token().handles();
    for (Action action : activation.actions()) {
      if (action instanceof InsertAction(String type, List<FieldAssignment> assignments)) {
        Map<String, Object> fields = new LinkedHashMap<>(assignments.size());
        for (FieldAssignment fa : assignments) {
          Object val;
          if (fa.value() instanceof LiteralOperand(Object lit)) {
            val = lit;
          } else if (fa.value() instanceof VarOperand(String varName)) {
            val = bindings.get(varName);
          } else {
            throw new AssertionError("Unexpected operand type");
          }
          fields.put(fa.field(), val);
        }
        insert(new Fact(type, fields));
      } else if (action instanceof RetractAction(String ignoredLabel, int condIndex)) {
        FactHandle target = matchedHandles.get(condIndex);
        retract(target);
      }
    }
  }

  /**
   * Returns the chronological list of all rule firings recorded in this session.
   *
   * @return unmodifiable list of {@link Firing} records
   */
  public List<Firing> firings() {
    return Collections.unmodifiableList(firingsHistory);
  }

  /**
   * Returns all facts currently in working memory of the specified type, in insertion order.
   *
   * @param type the fact type name
   * @return unmodifiable list of matching {@link Fact} instances
   * @throws NullPointerException if {@code type} is null
   */
  public List<Fact> facts(String type) {
    Objects.requireNonNull(type, "type");
    List<Fact> result = new ArrayList<>();
    for (FactHandle handle : workingMemory.values()) {
      if (type.equals(handle.fact().type())) {
        result.add(handle.fact());
      }
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Returns the current number of pending rule activations on the agenda.
   *
   * @return the agenda size
   */
  public int agendaSize() {
    return agenda.size();
  }

  /**
   * Returns the structural statistics of the compiled Rete network.
   *
   * @return the {@link NetworkStats}
   */
  public NetworkStats network() {
    return network.stats();
  }
}
