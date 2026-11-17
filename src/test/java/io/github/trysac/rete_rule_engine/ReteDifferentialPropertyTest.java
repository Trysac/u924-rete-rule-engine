package io.github.trysac.rete_rule_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Differential property test checking the Rete engine against the naive reference evaluator over
 * random rule sets and sequences of insert/retract/run operations.
 */
class ReteDifferentialPropertyTest {

  private static final List<String> TYPES = List.of("TypeA", "TypeB", "TypeC");
  private static final List<String> FIELDS = List.of("k", "v", "flag");
  private static final List<String> STR_VALS = List.of("s1", "s2", "s3");
  private static final List<Long> INT_VALS = List.of(1L, 2L, 3L);

  record GeneratedScenario(String dsl, List<Operation> operations) {}

  sealed interface Operation permits InsertOp, RetractOp, RunOp {}

  record InsertOp(Fact fact) implements Operation {}

  record RetractOp(int handleIndex) implements Operation {}

  record RunOp() implements Operation {}

  @Property(tries = 2000, seed = "924")
  void reteMatchesNaiveEvaluator(@ForAll("scenarios") GeneratedScenario scenario) {
    RuleSet ruleSet = RuleParser.parse(scenario.dsl());
    Session rete = new Session(ruleSet, 200);
    NaiveEvaluator naive = new NaiveEvaluator(ruleSet, 200);

    List<FactHandle> reteHandles = new ArrayList<>();
    List<FactHandle> naiveHandles = new ArrayList<>();

    for (Operation op : scenario.operations()) {
      if (op instanceof InsertOp(Fact fact)) {
        reteHandles.add(rete.insert(fact));
        naiveHandles.add(naive.insert(fact));
      } else if (op instanceof RetractOp(int idx)) {
        if (!reteHandles.isEmpty()) {
          int pos = Math.floorMod(idx, reteHandles.size());
          rete.retract(reteHandles.get(pos));
          naive.retract(naiveHandles.get(pos));
        }
      } else if (op instanceof RunOp) {
        rete.run();
        naive.run();
      }
    }

    rete.run();
    naive.run();

    assertEquals(naive.firings(), rete.firings());
    for (String type : TYPES) {
      assertEquals(naive.facts(type), rete.facts(type));
    }
  }

  @Provide
  Arbitrary<GeneratedScenario> scenarios() {
    Arbitrary<Integer> numRules = Arbitraries.integers().between(1, 5);
    Arbitrary<List<Operation>> ops = operationArbitrary().list().ofMinSize(1).ofMaxSize(30);
    return Combinators.combine(numRules.flatMap(this::programArbitrary), ops)
        .as(GeneratedScenario::new);
  }

  private Arbitrary<String> programArbitrary(int ruleCount) {
    List<Arbitrary<String>> ruleArbs = new ArrayList<>(ruleCount);
    for (int i = 0; i < ruleCount; i++) {
      ruleArbs.add(singleRuleArbitrary(i));
    }
    return Combinators.combine(ruleArbs).as(list -> String.join("\n", list));
  }

  private Arbitrary<String> singleRuleArbitrary(int idx) {
    return Arbitraries.integers()
        .between(1, 10_000)
        .map(
            seed -> {
              int s = seed;
              int salience = (s % 5) - 2;
              s /= 5;
              int condCount = 1 + (s % 3);
              s /= 3;

              StringBuilder sb = new StringBuilder();
              sb.append("rule \"r_")
                  .append(idx)
                  .append("\" salience ")
                  .append(salience)
                  .append(" when ");

              Set<String> boundVars = new HashSet<>();
              List<String> labels = new ArrayList<>();

              for (int c = 0; c < condCount; c++) {
                boolean isNot = (c > 0) && ((s % 3) == 0);
                s /= 3;
                String type = TYPES.get(s % TYPES.size());
                s /= TYPES.size();

                if (!isNot) {
                  String label = "l" + c;
                  labels.add(label);
                  sb.append('$').append(label).append(": ");
                } else {
                  sb.append("not ");
                }

                sb.append(type).append('(');
                List<String> constraints = new ArrayList<>();

                int kMode = s % 3;
                s /= 3;
                if (kMode == 0) {
                  constraints.add("k == \"" + STR_VALS.get(s % STR_VALS.size()) + "\"");
                  s /= STR_VALS.size();
                } else if (kMode == 1) {
                  constraints.add("k == ?x");
                  if (!isNot) {
                    boundVars.add("x");
                  }
                } else if (boundVars.contains("x")) {
                  constraints.add("k != ?x");
                } else {
                  constraints.add("k == ?x");
                  if (!isNot) {
                    boundVars.add("x");
                  }
                }

                int vMode = s % 4;
                s /= 4;
                long vLit = INT_VALS.get(s % INT_VALS.size());
                s /= INT_VALS.size();
                switch (vMode) {
                  case 0 -> constraints.add("v >= " + vLit);
                  case 1 -> constraints.add("v <= " + vLit);
                  case 2 -> {
                    constraints.add("v == ?v");
                    if (!isNot) {
                      boundVars.add("v");
                    }
                  }
                  default -> constraints.add("v == " + vLit);
                }

                if ((s & 1) == 1) {
                  constraints.add("flag == " + (((s >> 1) & 1) == 1));
                }
                s >>= 2;

                sb.append(String.join(", ", constraints)).append(") ");
              }

              sb.append("then ");
              boolean doRetract = !labels.isEmpty() && ((s % 3) == 0);
              s /= 3;
              if (doRetract) {
                String label = labels.get(s % labels.size());
                sb.append("retract $").append(label).append(' ');
              } else {
                String outType = TYPES.get(s % TYPES.size());
                s /= TYPES.size();
                String kExpr =
                    boundVars.contains("x") && ((s & 1) == 0)
                        ? "?x"
                        : "\"" + STR_VALS.get(s % STR_VALS.size()) + "\"";
                String vExpr =
                    boundVars.contains("v") && ((s & 2) == 0)
                        ? "?v"
                        : String.valueOf(INT_VALS.get(s % INT_VALS.size()));
                boolean flagVal = (s & 4) != 0;
                sb.append("insert ")
                    .append(outType)
                    .append("(k = ")
                    .append(kExpr)
                    .append(", v = ")
                    .append(vExpr)
                    .append(", flag = ")
                    .append(flagVal)
                    .append(") ");
              }
              sb.append("end");
              return sb.toString();
            });
  }

  private Arbitrary<Operation> operationArbitrary() {
    Arbitrary<Operation> insertArb =
        Combinators.combine(
                Arbitraries.of(TYPES),
                Arbitraries.of(STR_VALS),
                Arbitraries.of(INT_VALS),
                Arbitraries.of(true, false))
            .as(
                (t, k, v, f) -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put(FIELDS.get(0), k);
                  map.put(FIELDS.get(1), v);
                  map.put(FIELDS.get(2), f);
                  return new InsertOp(new Fact(t, map));
                });
    Arbitrary<Operation> retractArb = Arbitraries.integers().between(0, 29).map(RetractOp::new);
    Arbitrary<Operation> runArb = Arbitraries.just(new RunOp());
    return Arbitraries.frequencyOf(
        net.jqwik.api.Tuple.of(6, insertArb),
        net.jqwik.api.Tuple.of(2, retractArb),
        net.jqwik.api.Tuple.of(1, runArb));
  }
}
