package io.github.trysac.rete_rule_engine;

import io.github.trysac.rete_rule_engine.RuleAst.Action;
import io.github.trysac.rete_rule_engine.RuleAst.Condition;
import io.github.trysac.rete_rule_engine.RuleAst.Constraint;
import io.github.trysac.rete_rule_engine.RuleAst.FieldAssignment;
import io.github.trysac.rete_rule_engine.RuleAst.InsertAction;
import io.github.trysac.rete_rule_engine.RuleAst.LiteralOperand;
import io.github.trysac.rete_rule_engine.RuleAst.Op;
import io.github.trysac.rete_rule_engine.RuleAst.Operand;
import io.github.trysac.rete_rule_engine.RuleAst.RetractAction;
import io.github.trysac.rete_rule_engine.RuleAst.RuleDef;
import io.github.trysac.rete_rule_engine.RuleAst.VarOperand;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Parser for the Rete rule DSL.
 *
 * <p>Enforces a maximum source length of {@link #MAX_SOURCE_LENGTH} characters.
 */
public final class RuleParser {

  /** Maximum allowed character length for a rule source string. */
  public static final int MAX_SOURCE_LENGTH = 65_536;

  private RuleParser() {}

  /**
   * Parses a DSL source string into a validated {@link RuleSet}.
   *
   * @param source the DSL source text
   * @return the compiled {@link RuleSet}
   * @throws NullPointerException if {@code source} is null
   * @throws RuleException if the input exceeds {@link #MAX_SOURCE_LENGTH}, has a syntax error,
   *     contains duplicate rule names, references unbound variables, or violates type constraints
   */
  public static RuleSet parse(String source) {
    Objects.requireNonNull(source, "source");
    if (source.length() > MAX_SOURCE_LENGTH) {
      throw new RuleException(
          "input_too_large",
          "Source length " + source.length() + " exceeds maximum " + MAX_SOURCE_LENGTH);
    }
    List<Token> tokens = tokenize(source);
    ParserState state = new ParserState(tokens);
    List<RuleDef> rules = state.parseProgram();
    return new RuleSet(rules);
  }

  private enum TokenKind {
    IDENT,
    STRING,
    INT,
    LABEL,
    VAR,
    LPAREN,
    RPAREN,
    COMMA,
    COLON,
    ASSIGN,
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE,
    EOF
  }

  private record Token(TokenKind kind, String text, Object value, int line, int column) {}

  private static List<Token> tokenize(String src) {
    List<Token> tokens = new ArrayList<>();
    int n = src.length();
    int i = 0;
    int line = 1;
    int col = 1;

    while (i < n) {
      char c = src.charAt(i);
      if (c == '\n') {
        i++;
        line++;
        col = 1;
        continue;
      }
      if (c == '\r') {
        i++;
        if (i < n && src.charAt(i) == '\n') {
          i++;
        }
        line++;
        col = 1;
        continue;
      }
      if (Character.isWhitespace(c)) {
        i++;
        col++;
        continue;
      }
      if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
        while (i < n && src.charAt(i) != '\n' && src.charAt(i) != '\r') {
          i++;
          col++;
        }
        continue;
      }

      int startLine = line;
      int startCol = col;

      if (c == '(') {
        tokens.add(new Token(TokenKind.LPAREN, "(", null, startLine, startCol));
        i++;
        col++;
        continue;
      }
      if (c == ')') {
        tokens.add(new Token(TokenKind.RPAREN, ")", null, startLine, startCol));
        i++;
        col++;
        continue;
      }
      if (c == ',') {
        tokens.add(new Token(TokenKind.COMMA, ",", null, startLine, startCol));
        i++;
        col++;
        continue;
      }
      if (c == ':') {
        tokens.add(new Token(TokenKind.COLON, ":", null, startLine, startCol));
        i++;
        col++;
        continue;
      }
      if (c == '=') {
        if (i + 1 < n && src.charAt(i + 1) == '=') {
          tokens.add(new Token(TokenKind.EQ, "==", null, startLine, startCol));
          i += 2;
          col += 2;
        } else {
          tokens.add(new Token(TokenKind.ASSIGN, "=", null, startLine, startCol));
          i++;
          col++;
        }
        continue;
      }
      if (c == '!') {
        if (i + 1 < n && src.charAt(i + 1) == '=') {
          tokens.add(new Token(TokenKind.NE, "!=", null, startLine, startCol));
          i += 2;
          col += 2;
          continue;
        }
        throw new RuleException("syntax", "Unexpected character '!'", startLine, startCol);
      }
      if (c == '<') {
        if (i + 1 < n && src.charAt(i + 1) == '=') {
          tokens.add(new Token(TokenKind.LE, "<=", null, startLine, startCol));
          i += 2;
          col += 2;
        } else {
          tokens.add(new Token(TokenKind.LT, "<", null, startLine, startCol));
          i++;
          col++;
        }
        continue;
      }
      if (c == '>') {
        if (i + 1 < n && src.charAt(i + 1) == '=') {
          tokens.add(new Token(TokenKind.GE, ">=", null, startLine, startCol));
          i += 2;
          col += 2;
        } else {
          tokens.add(new Token(TokenKind.GT, ">", null, startLine, startCol));
          i++;
          col++;
        }
        continue;
      }
      if (c == '"') {
        i++;
        col++;
        StringBuilder sb = new StringBuilder();
        boolean closed = false;
        while (i < n) {
          char ch = src.charAt(i);
          if (ch == '"') {
            i++;
            col++;
            closed = true;
            break;
          }
          if (ch == '\\') {
            i++;
            col++;
            if (i >= n) {
              throw new RuleException("syntax", "Unterminated string literal", startLine, startCol);
            }
            char esc = src.charAt(i);
            switch (esc) {
              case '"' -> sb.append('"');
              case '\\' -> sb.append('\\');
              case 'n' -> sb.append('\n');
              case 'r' -> sb.append('\r');
              case 't' -> sb.append('\t');
              default ->
                  throw new RuleException(
                      "syntax", "Invalid escape sequence '\\" + esc + "'", line, col);
            }
            i++;
            col++;
            continue;
          }
          if (ch == '\n') {
            sb.append('\n');
            i++;
            line++;
            col = 1;
            continue;
          }
          if (ch == '\r') {
            i++;
            if (i < n && src.charAt(i) == '\n') {
              i++;
            }
            sb.append('\n');
            line++;
            col = 1;
            continue;
          }
          sb.append(ch);
          i++;
          col++;
        }
        if (!closed) {
          throw new RuleException("syntax", "Unterminated string literal", startLine, startCol);
        }
        String val = sb.toString();
        tokens.add(new Token(TokenKind.STRING, val, val, startLine, startCol));
        continue;
      }
      if (c == '$' || c == '?') {
        TokenKind kind = c == '$' ? TokenKind.LABEL : TokenKind.VAR;
        i++;
        col++;
        int idStart = i;
        while (i < n && isIdentPart(src.charAt(i))) {
          i++;
          col++;
        }
        if (i == idStart) {
          throw new RuleException(
              "syntax", "Expected identifier after '" + c + "'", startLine, startCol);
        }
        String name = src.substring(idStart, i);
        tokens.add(new Token(kind, c + name, name, startLine, startCol));
        continue;
      }
      if (c == '-' || Character.isDigit(c)) {
        int numStart = i;
        if (c == '-') {
          i++;
          col++;
          if (i >= n || !Character.isDigit(src.charAt(i))) {
            throw new RuleException("syntax", "Expected digit after '-'", startLine, startCol);
          }
        }
        while (i < n && Character.isDigit(src.charAt(i))) {
          i++;
          col++;
        }
        if (i < n && isIdentStart(src.charAt(i))) {
          throw new RuleException("syntax", "Invalid number token", startLine, startCol);
        }
        String raw = src.substring(numStart, i);
        try {
          long val = Long.parseLong(raw);
          tokens.add(new Token(TokenKind.INT, raw, val, startLine, startCol));
        } catch (NumberFormatException ex) {
          throw new RuleException(
              "syntax", "Integer literal out of range: " + raw, startLine, startCol);
        }
        continue;
      }
      if (isIdentStart(c)) {
        int idStart = i;
        while (i < n && isIdentPart(src.charAt(i))) {
          i++;
          col++;
        }
        String ident = src.substring(idStart, i);
        tokens.add(new Token(TokenKind.IDENT, ident, ident, startLine, startCol));
        continue;
      }
      throw new RuleException("syntax", "Unexpected character '" + c + "'", startLine, startCol);
    }

    tokens.add(new Token(TokenKind.EOF, "<eof>", null, line, col));
    return tokens;
  }

  private static boolean isIdentStart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
  }

  private static boolean isIdentPart(char c) {
    return isIdentStart(c) || (c >= '0' && c <= '9');
  }

  private static final class ParserState {
    private final List<Token> tokens;
    private int pos;

    ParserState(List<Token> tokens) {
      this.tokens = tokens;
      this.pos = 0;
    }

    List<RuleDef> parseProgram() {
      List<RuleDef> rules = new ArrayList<>();
      Set<String> ruleNames = new HashSet<>();
      int order = 0;
      while (!check(TokenKind.EOF)) {
        RuleDef rule = parseRule(order++, ruleNames);
        rules.add(rule);
      }
      return rules;
    }

    private RuleDef parseRule(int declarationOrder, Set<String> ruleNames) {
      expectKeyword("rule");
      Token nameTok = expect(TokenKind.STRING, "Expected rule name string after 'rule'");
      String ruleName = (String) nameTok.value();
      if (ruleName.isEmpty()) {
        throw new RuleException(
            "syntax", "Rule name must not be empty", nameTok.line(), nameTok.column());
      }
      if (!ruleNames.add(ruleName)) {
        throw new RuleException(
            "duplicate_rule",
            "Duplicate rule name '" + ruleName + "'",
            nameTok.line(),
            nameTok.column());
      }

      int salience = 0;
      if (matchKeyword("salience")) {
        Token salTok = expect(TokenKind.INT, "Expected integer after 'salience'");
        long salLong = (Long) salTok.value();
        if (salLong < Integer.MIN_VALUE || salLong > Integer.MAX_VALUE) {
          throw new RuleException(
              "invalid_value",
              "Salience out of integer range: " + salLong,
              salTok.line(),
              salTok.column());
        }
        salience = (int) salLong;
      }

      expectKeyword("when");

      List<Condition> conditions = new ArrayList<>();
      Map<String, Integer> labelToConditionIndex = new HashMap<>();
      Set<String> boundVars = new HashSet<>();

      if (checkKeyword("then") || checkKeyword("end") || check(TokenKind.EOF)) {
        Token t = peek();
        throw new RuleException(
            "syntax", "Expected at least one condition after 'when'", t.line(), t.column());
      }

      while (!checkKeyword("then") && !check(TokenKind.EOF) && !checkKeyword("end")) {
        Condition cond = parseCondition(conditions.size(), labelToConditionIndex, boundVars);
        conditions.add(cond);
      }

      expectKeyword("then");

      if (checkKeyword("end") || check(TokenKind.EOF)) {
        Token t = peek();
        throw new RuleException(
            "syntax", "Expected at least one action after 'then'", t.line(), t.column());
      }

      List<Action> actions = new ArrayList<>();
      while (!checkKeyword("end") && !check(TokenKind.EOF)) {
        actions.add(parseAction(labelToConditionIndex, boundVars));
      }

      expectKeyword("end");
      return new RuleDef(ruleName, salience, declarationOrder, conditions, actions);
    }

    private Condition parseCondition(
        int condIndex, Map<String, Integer> labelToConditionIndex, Set<String> boundVars) {
      String label = null;
      boolean negated = false;

      if (check(TokenKind.LABEL)) {
        Token labelTok = advance();
        label = (String) labelTok.value();
        expect(TokenKind.COLON, "Expected ':' after label $" + label);
        if (checkKeyword("not")) {
          Token notTok = peek();
          throw new RuleException(
              "syntax",
              "Cannot bind label $" + label + " to a negated 'not' condition",
              notTok.line(),
              notTok.column());
        }
        if (labelToConditionIndex.containsKey(label)) {
          throw new RuleException(
              "syntax", "Duplicate condition label $" + label, labelTok.line(), labelTok.column());
        }
        labelToConditionIndex.put(label, condIndex);
      } else if (matchKeyword("not")) {
        negated = true;
      }

      Token typeTok = expectTypeIdentifier("Expected fact type name in condition");
      String type = typeTok.text();

      expect(TokenKind.LPAREN, "Expected '(' after fact type " + type);
      List<Constraint> constraints = new ArrayList<>();
      Set<String> localVarsBoundInNot = new HashSet<>();

      if (!check(TokenKind.RPAREN)) {
        do {
          Constraint c = parseConstraint(negated, boundVars, localVarsBoundInNot);
          constraints.add(c);
        } while (match(TokenKind.COMMA));
      }
      expect(TokenKind.RPAREN, "Expected ')' after condition constraints");

      return new Condition(negated, label, type, constraints);
    }

    private Constraint parseConstraint(
        boolean inNot, Set<String> boundVars, Set<String> localVarsBoundInNot) {
      Token fieldTok = expectIdentifier("Expected field name in constraint");
      String field = fieldTok.text();
      Op op = parseOp();
      Token operandTok = peek();
      Operand operand = parseOperand();

      if (operand instanceof LiteralOperand(Object val)) {
        if (op.isOrdering() && !(val instanceof Long)) {
          throw new RuleException(
              "invalid_value",
              "Ordering operator '" + op.symbol() + "' requires a Long literal",
              operandTok.line(),
              operandTok.column());
        }
      } else if (operand instanceof VarOperand(String varName)) {
        if (op == Op.EQ) {
          if (!boundVars.contains(varName) && !localVarsBoundInNot.contains(varName)) {
            if (inNot) {
              localVarsBoundInNot.add(varName);
            } else {
              boundVars.add(varName);
            }
          }
        } else {
          if (!boundVars.contains(varName) && !localVarsBoundInNot.contains(varName)) {
            throw new RuleException(
                "unbound_variable",
                "Variable ?" + varName + " used with '" + op.symbol() + "' before being bound",
                operandTok.line(),
                operandTok.column());
          }
        }
      }

      return new Constraint(field, op, operand);
    }

    private Action parseAction(Map<String, Integer> labelToConditionIndex, Set<String> boundVars) {
      Token tok = peek();
      if (matchKeyword("insert")) {
        Token typeTok = expectTypeIdentifier("Expected fact type name after 'insert'");
        String type = typeTok.text();
        expect(TokenKind.LPAREN, "Expected '(' after type " + type + " in insert action");
        List<FieldAssignment> assignments = new ArrayList<>();
        Set<String> seenFields = new HashSet<>();
        if (!check(TokenKind.RPAREN)) {
          do {
            Token fieldTok = expectIdentifier("Expected field name in insert action");
            String field = fieldTok.text();
            if (!seenFields.add(field)) {
              throw new RuleException(
                  "syntax",
                  "Duplicate field '" + field + "' in insert action",
                  fieldTok.line(),
                  fieldTok.column());
            }
            expect(TokenKind.ASSIGN, "Expected '=' after field '" + field + "' in insert action");
            Token valTok = peek();
            Operand operand = parseOperand();
            if (operand instanceof VarOperand(String varName) && !boundVars.contains(varName)) {
              throw new RuleException(
                  "unbound_variable",
                  "Variable ?" + varName + " in action is not bound by any positive condition",
                  valTok.line(),
                  valTok.column());
            }
            assignments.add(new FieldAssignment(field, operand));
          } while (match(TokenKind.COMMA));
        }
        expect(TokenKind.RPAREN, "Expected ')' at end of insert action");
        return new InsertAction(type, assignments);
      }
      if (matchKeyword("retract")) {
        Token targetTok = peek();
        if (check(TokenKind.LABEL)) {
          advance();
          String label = (String) targetTok.value();
          Integer idx = labelToConditionIndex.get(label);
          if (idx == null) {
            throw new RuleException(
                "unbound_variable",
                "Unknown condition label $" + label + " in retract action",
                targetTok.line(),
                targetTok.column());
          }
          return new RetractAction(label, idx);
        }
        if (check(TokenKind.VAR)) {
          advance();
          String varName = (String) targetTok.value();
          throw new RuleException(
              "unbound_variable",
              "Cannot retract variable ?" + varName + "; expected a condition label",
              targetTok.line(),
              targetTok.column());
        }
        throw new RuleException(
            "syntax",
            "Expected condition label (e.g. $p) after 'retract'",
            targetTok.line(),
            targetTok.column());
      }
      throw new RuleException(
          "syntax",
          "Expected 'insert' or 'retract' action, got '" + tok.text() + "'",
          tok.line(),
          tok.column());
    }

    private Op parseOp() {
      Token tok = peek();
      return switch (tok.kind()) {
        case EQ -> {
          advance();
          yield Op.EQ;
        }
        case NE -> {
          advance();
          yield Op.NE;
        }
        case LT -> {
          advance();
          yield Op.LT;
        }
        case LE -> {
          advance();
          yield Op.LE;
        }
        case GT -> {
          advance();
          yield Op.GT;
        }
        case GE -> {
          advance();
          yield Op.GE;
        }
        default ->
            throw new RuleException(
                "syntax",
                "Expected comparison operator (==, !=, <, <=, >, >=), got '" + tok.text() + "'",
                tok.line(),
                tok.column());
      };
    }

    private Operand parseOperand() {
      Token tok = peek();
      if (tok.kind() == TokenKind.STRING || tok.kind() == TokenKind.INT) {
        advance();
        return new LiteralOperand(tok.value());
      }
      if (tok.kind() == TokenKind.IDENT) {
        if ("true".equals(tok.text())) {
          advance();
          return new LiteralOperand(Boolean.TRUE);
        }
        if ("false".equals(tok.text())) {
          advance();
          return new LiteralOperand(Boolean.FALSE);
        }
        throw new RuleException(
            "invalid_value", "Invalid literal '" + tok.text() + "'", tok.line(), tok.column());
      }
      if (tok.kind() == TokenKind.VAR) {
        advance();
        return new VarOperand((String) tok.value());
      }
      throw new RuleException(
          "syntax",
          "Expected literal or ?variable, got '" + tok.text() + "'",
          tok.line(),
          tok.column());
    }

    private Token expectTypeIdentifier(String message) {
      Token tok = expectIdentifier(message);
      String text = tok.text();
      if (isReservedKeyword(text)) {
        throw new RuleException(
            "syntax",
            "Unexpected reserved keyword '" + text + "' where a type name was expected",
            tok.line(),
            tok.column());
      }
      if (!Character.isUpperCase(text.charAt(0))) {
        throw new RuleException(
            "unknown_type_in_action",
            "Fact type name '" + text + "' must start with an uppercase letter",
            tok.line(),
            tok.column());
      }
      return tok;
    }

    private Token expectIdentifier(String message) {
      Token tok = expect(TokenKind.IDENT, message);
      if (isReservedKeyword(tok.text())) {
        throw new RuleException(
            "syntax", "Unexpected keyword '" + tok.text() + "'", tok.line(), tok.column());
      }
      return tok;
    }

    private static boolean isReservedKeyword(String s) {
      return switch (s) {
        case "salience", "when", "then", "end", "not", "insert", "retract", "true", "false" -> true;
        default -> false;
      };
    }

    private void expectKeyword(String kw) {
      Token tok = peek();
      if (tok.kind() == TokenKind.IDENT && kw.equals(tok.text())) {
        advance();
        return;
      }
      throw new RuleException(
          "syntax", "Expected '" + kw + "', got '" + tok.text() + "'", tok.line(), tok.column());
    }

    private boolean checkKeyword(String kw) {
      Token tok = peek();
      return tok.kind() == TokenKind.IDENT && kw.equals(tok.text());
    }

    private boolean matchKeyword(String kw) {
      if (checkKeyword(kw)) {
        advance();
        return true;
      }
      return false;
    }

    private Token expect(TokenKind kind, String message) {
      Token tok = peek();
      if (tok.kind() == kind) {
        return advance();
      }
      throw new RuleException(
          "syntax", message + ", got '" + tok.text() + "'", tok.line(), tok.column());
    }

    private boolean check(TokenKind kind) {
      return peek().kind() == kind;
    }

    private boolean match(TokenKind kind) {
      if (check(kind)) {
        advance();
        return true;
      }
      return false;
    }

    private Token peek() {
      return tokens.get(pos);
    }

    private Token advance() {
      Token tok = tokens.get(pos);
      if (pos < tokens.size() - 1) {
        pos++;
      }
      return tok;
    }
  }
}
