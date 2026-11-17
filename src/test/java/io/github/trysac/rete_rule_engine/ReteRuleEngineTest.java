package io.github.trysac.rete_rule_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Acceptance and edge-case tests for the Rete rule engine. */
class ReteRuleEngineTest {

  @Test
  void adultRuleInsertsDerivedFact() {
    String source =
        "rule \"adult\" when $p: Person(age >= 18, name == ?n) then insert Adult(name = ?n) end";
    RuleSet rules = RuleParser.parse(source);
    Session session = new Session(rules);

    session.insert(Fact.of("Person", "name", "Ann", "age", 20L));

    assertEquals(1, session.run());
    List<Fact> adults = session.facts("Adult");
    assertEquals(1, adults.size());
    assertEquals("Ann", adults.get(0).fields().get("name"));
  }

  @Test
  void joinAndNotConditionsBehaveAccordingToSpec() {
    String joinSource =
        "rule \"gold_order\" when Order(customer == ?c) Customer(id == ?c, tier == \"gold\") "
            + "then insert GoldOrder(customer = ?c) end";
    Session joinSession = new Session(RuleParser.parse(joinSource));
    joinSession.insert(Fact.of("Customer", "id", "c1", "tier", "gold"));
    joinSession.insert(Fact.of("Customer", "id", "c2", "tier", "silver"));
    joinSession.insert(Fact.of("Order", "id", 1L, "customer", "c1"));
    joinSession.insert(Fact.of("Order", "id", 2L, "customer", "c1"));
    joinSession.insert(Fact.of("Order", "id", 3L, "customer", "c2"));

    assertEquals(2, joinSession.run());
    assertEquals(2, joinSession.facts("GoldOrder").size());

    String notSource =
        "rule \"no_orders\" when Customer(id == ?c) not Order(customer == ?c) "
            + "then insert IdleCustomer(id = ?c) end";
    Session notSession1 = new Session(RuleParser.parse(notSource));
    notSession1.insert(Fact.of("Customer", "id", "c1"));
    notSession1.insert(Fact.of("Customer", "id", "c2"));
    notSession1.insert(Fact.of("Order", "customer", "c1"));

    assertEquals(1, notSession1.run());
    assertEquals(List.of(Fact.of("IdleCustomer", "id", "c2")), notSession1.facts("IdleCustomer"));

    Session notSession2 = new Session(RuleParser.parse(notSource));
    notSession2.insert(Fact.of("Customer", "id", "c1"));
    notSession2.insert(Fact.of("Customer", "id", "c2"));
    notSession2.insert(Fact.of("Order", "customer", "c1"));
    notSession2.insert(Fact.of("Order", "customer", "c2"));

    assertEquals(0, notSession2.run());
    assertTrue(notSession2.facts("IdleCustomer").isEmpty());
  }

  @Test
  void conflictResolutionOrdersBySalienceRecencyAndDeclarationAndRefracts() {
    String salienceRules =
        """
        rule "low" salience 0 when Item(id == ?x) then insert Log(rule = "low") end
        rule "high" salience 10 when Item(id == ?x) then insert Log(rule = "high") end
        rule "mid" salience 5 when Item(id == ?x) then insert Log(rule = "mid") end
        """;
    Session session = new Session(RuleParser.parse(salienceRules));
    session.insert(Fact.of("Item", "id", 1L));
    assertEquals(3, session.run());
    List<String> firedRules = session.firings().stream().map(Firing::rule).toList();
    assertEquals(List.of("high", "mid", "low"), firedRules);
    // Refraction: calling run() again or inserting another unrelated fact does not re-fire
    assertEquals(0, session.run());

    String tieBreakerRules =
        """
        rule "first" salience 5 when Item(id == ?x) then insert Seen(id = ?x) end
        rule "second" salience 5 when Item(id == ?x) then insert Seen(id = ?x) end
        """;
    Session tieSession = new Session(RuleParser.parse(tieBreakerRules));
    FactHandle h1 = tieSession.insert(Fact.of("Item", "id", 100L));
    FactHandle h2 = tieSession.insert(Fact.of("Item", "id", 200L));
    assertEquals(1L, h1.id());
    assertEquals(2L, h2.id());
    assertEquals(4, tieSession.run());
    assertEquals(
        List.of(
            new Firing("first", List.of(2L)),
            new Firing("second", List.of(2L)),
            new Firing("first", List.of(1L)),
            new Firing("second", List.of(1L))),
        tieSession.firings());
  }

  @Test
  void retractBeforeRunClearsAgendaAndIsIdempotent() {
    String source = "rule \"r\" when Person(name == ?n) then insert Greeting(who = ?n) end";
    Session session = new Session(RuleParser.parse(source));
    FactHandle handle = session.insert(Fact.of("Person", "name", "Bob"));
    assertEquals(1, session.agendaSize());

    session.retract(handle);
    assertEquals(0, session.agendaSize());
    // Retracting an already retracted handle is a no-op
    session.retract(handle);
    assertEquals(0, session.run());
  }

  @Test
  void alphaNodeSharingMergesIdenticalPatternsAcrossRules() {
    String source =
        """
        rule "r1" when Person(age >= 18) then insert Check(v = 1) end
        rule "r2" when Person(age >= 18) then insert Check(v = 2) end
        """;
    Session session = new Session(RuleParser.parse(source));
    assertEquals(1, session.network().alphaNodeCount());
    assertEquals(2, session.network().betaNodeCount());
  }

  @Test
  void edgeCasesVariableOnlyInNotSelfMatchingRuleTwoLabelsAndEmptyProgram() {
    // Empty program
    RuleSet emptyRules = RuleParser.parse("// just a comment\n   ");
    assertEquals(0, emptyRules.size());
    Session emptySession = new Session(emptyRules);
    emptySession.insert(Fact.of("Any", "k", "v"));
    assertEquals(0, emptySession.run());

    // Variable bound in a 'not' pattern only (with intra-fact join inside 'not')
    String notOnlyVar =
        "rule \"alert_if_no_self_loop\" when Node(id == ?n) not Edge(from == ?x, to == ?x) "
            + "then insert Clean(node = ?n) end";
    Session notVarSession = new Session(RuleParser.parse(notOnlyVar));
    notVarSession.insert(Fact.of("Node", "id", 1L));
    notVarSession.insert(Fact.of("Edge", "from", 1L, "to", 2L));
    assertEquals(1, notVarSession.run());

    // Self-matching rule that stops via refraction when condition filters new fact
    String selfMatch =
        "rule \"promote\" when Emp(role == \"jr\", name == ?n) "
            + "then insert Emp(role = \"sr\", name = ?n) end";
    Session selfSession = new Session(RuleParser.parse(selfMatch));
    selfSession.insert(Fact.of("Emp", "role", "jr", "name", "Kim"));
    assertEquals(1, selfSession.run());
    assertEquals(2, selfSession.facts("Emp").size());

    // Two labels on the same type with retract action
    String twoLabels =
        "rule \"dedup\" when $a: Pair(k == ?k, v == ?v1) $b: Pair(k == ?k, v == ?v2) "
            + "then retract $b end";
    Session twoLabelsSession = new Session(RuleParser.parse(twoLabels));
    twoLabelsSession.insert(Fact.of("Pair", "k", "x", "v", 1L));
    twoLabelsSession.insert(Fact.of("Pair", "k", "x", "v", 2L));
    assertTrue(twoLabelsSession.run() >= 1);
  }

  @Test
  void errorCodesAndLocationsMatchSpecification() {
    // 1. Unterminated rule gives 'syntax' with line 1 and a column
    RuleException syntaxErr =
        assertThrows(
            RuleException.class,
            () -> RuleParser.parse("rule \"unterminated\" when Person(age >= 18) then"));
    assertEquals("syntax", syntaxErr.code());
    assertEquals(1, syntaxErr.line());
    assertTrue(syntaxErr.column() > 0);

    // 2. Unbound variable in action gives 'unbound_variable'
    RuleException unboundErr =
        assertThrows(
            RuleException.class,
            () ->
                RuleParser.parse(
                    "rule \"bad\" when Person(age >= 18) then insert Out(v = ?x) end"));
    assertEquals("unbound_variable", unboundErr.code());

    // 3. Variable bound only in 'not' used in action gives 'unbound_variable'
    RuleException unboundFromNot =
        assertThrows(
            RuleException.class,
            () ->
                RuleParser.parse(
                    "rule \"bad_not\" when Person(age >= 18) not Ban(reason == ?r) "
                        + "then insert Out(v = ?r) end"));
    assertEquals("unbound_variable", unboundFromNot.code());

    // 4. Source of 65,537 characters gives 'input_too_large'
    String huge = " ".repeat(65_537);
    RuleException sizeErr = assertThrows(RuleException.class, () -> RuleParser.parse(huge));
    assertEquals("input_too_large", sizeErr.code());
    assertEquals(0, sizeErr.line());
    assertEquals(0, sizeErr.column());

    // 5. Infinite loop gives 'firing_limit' after 10,000 firings
    String loopSource = "rule \"loop\" when Tick(n == ?n) then insert Tick(n = ?n) end";
    Session loopSession = new Session(RuleParser.parse(loopSource));
    loopSession.insert(Fact.of("Tick", "n", 1L));
    RuleException limitErr = assertThrows(RuleException.class, loopSession::run);
    assertEquals("firing_limit", limitErr.code());
    assertEquals(10_000, loopSession.firings().size());

    // 6. Duplicate rule name gives 'duplicate_rule'
    RuleException dupErr =
        assertThrows(
            RuleException.class,
            () ->
                RuleParser.parse(
                    "rule \"dup\" when A() then insert B() end "
                        + "rule \"dup\" when A() then insert B() end"));
    assertEquals("duplicate_rule", dupErr.code());

    // 7. Unknown/invalid type name in action gives 'unknown_type_in_action'
    RuleException typeErr =
        assertThrows(
            RuleException.class,
            () -> RuleParser.parse("rule \"t\" when A() then insert badType(x = 1) end"));
    assertEquals("unknown_type_in_action", typeErr.code());

    // 8. Invalid value in Fact.of or ordering operator on non-Long gives 'invalid_value'
    RuleException valErr =
        assertThrows(RuleException.class, () -> Fact.of("Person", "age", 20)); // Integer, not Long
    assertEquals("invalid_value", valErr.code());

    RuleException orderErr =
        assertThrows(
            RuleException.class,
            () -> RuleParser.parse("rule \"ord\" when A(name > \"abc\") then insert B() end"));
    assertEquals("invalid_value", orderErr.code());
  }

  @Test
  void allOperatorsAndLiteralsAndCommentsAreSupported() {
    String dsl =
        """
        // Check all comparison operators and literal types
        rule "ops" salience -5
        when
          $m: Metric(active == true, disabled != true, score > -10, score >= 0, score < 100, score <= 50, tag == "ok\n\t\\\"", self == ?s, self == ?s)
        then
          insert Result(flag = false, tag = "done")
          retract $m
        end
        """;
    Session session = new Session(RuleParser.parse(dsl));
    FactHandle fh =
        session.insert(
            Fact.of(
                "Metric",
                "active",
                true,
                "disabled",
                false,
                "score",
                50L,
                "tag",
                "ok\n\t\"",
                "self",
                "same"));
    assertEquals("FactHandle[1: Metric]", fh.toString());
    assertEquals(fh, fh);
    assertNotEquals(fh, "not-a-handle");

    assertEquals(1, session.run());
    assertTrue(session.facts("Metric").isEmpty());
    assertEquals(1, session.facts("Result").size());
  }

  @Test
  void validationErrorsOnFactAndParserEdgeCases() {
    assertThrows(RuleException.class, () -> new Fact("  ", Map.of()));
    assertThrows(RuleException.class, () -> new Fact("A", Map.of("", 1L)));
    assertThrows(RuleException.class, () -> Fact.of("A", "onlyKey"));
    assertThrows(RuleException.class, () -> Fact.of("A", 123L, "val"));
    assertThrows(RuleException.class, () -> Fact.of("A", "k", 1L, "k", 2L));
    assertThrows(RuleException.class, () -> new Session(RuleParser.parse(""), 0));

    // Unbound variable used with != before binding
    RuleException neUnbound =
        assertThrows(
            RuleException.class,
            () -> RuleParser.parse("rule \"r\" when A(x != ?u) then insert B() end"));
    assertEquals("unbound_variable", neUnbound.code());

    // Retracting unknown label
    RuleException unknownLabel =
        assertThrows(
            RuleException.class,
            () -> RuleParser.parse("rule \"r\" when $a: A() then retract $b end"));
    assertEquals("unbound_variable", unknownLabel.code());

    // Retracting a variable instead of a label
    RuleException retractVar =
        assertThrows(
            RuleException.class,
            () -> RuleParser.parse("rule \"r\" when A(x == ?v) then retract ?v end"));
    assertEquals("unbound_variable", retractVar.code());

    // Root negative node (rule with only a 'not' condition)
    String rootNot =
        "rule \"init\" when not Booted(done == true) then insert Booted(done = true) end";
    Session s = new Session(RuleParser.parse(rootNot));
    assertEquals(1, s.run());
    assertEquals(1, s.facts("Booted").size());
    assertFalse(s.firings().isEmpty());
  }
}
