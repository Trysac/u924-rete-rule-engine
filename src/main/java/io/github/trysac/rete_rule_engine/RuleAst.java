package io.github.trysac.rete_rule_engine;

import java.util.List;

/** Internal AST definitions for parsed rules. */
final class RuleAst {

  private RuleAst() {}

  enum Op {
    EQ("=="),
    NE("!="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">=");

    private final String symbol;

    Op(String symbol) {
      this.symbol = symbol;
    }

    String symbol() {
      return symbol;
    }

    boolean isOrdering() {
      return this == LT || this == LE || this == GT || this == GE;
    }

    boolean evaluate(Object left, Object right) {
      if (left == null || right == null) {
        return false;
      }
      return switch (this) {
        case EQ -> left.equals(right);
        case NE -> {
          if (!left.getClass().equals(right.getClass())) {
            yield true;
          }
          yield !left.equals(right);
        }
        case LT -> {
          if (left instanceof Long l && right instanceof Long r) {
            yield l < r;
          }
          throw new RuleException(
              "invalid_value", "Ordering operator '<' is only supported on Long values");
        }
        case LE -> {
          if (left instanceof Long l && right instanceof Long r) {
            yield l <= r;
          }
          throw new RuleException(
              "invalid_value", "Ordering operator '<=' is only supported on Long values");
        }
        case GT -> {
          if (left instanceof Long l && right instanceof Long r) {
            yield l > r;
          }
          throw new RuleException(
              "invalid_value", "Ordering operator '>' is only supported on Long values");
        }
        case GE -> {
          if (left instanceof Long l && right instanceof Long r) {
            yield l >= r;
          }
          throw new RuleException(
              "invalid_value", "Ordering operator '>=' is only supported on Long values");
        }
      };
    }
  }

  sealed interface Operand permits LiteralOperand, VarOperand {}

  record LiteralOperand(Object value) implements Operand {}

  record VarOperand(String name) implements Operand {}

  record Constraint(String field, Op op, Operand operand) {}

  record Condition(boolean negated, String label, String type, List<Constraint> constraints) {}

  sealed interface Action permits InsertAction, RetractAction {}

  record FieldAssignment(String field, Operand value) {}

  record InsertAction(String type, List<FieldAssignment> assignments) implements Action {}

  record RetractAction(String label, int conditionIndex) implements Action {}

  record RuleDef(
      String name,
      int salience,
      int declarationOrder,
      List<Condition> conditions,
      List<Action> actions) {}
}
