package io.github.trysac.rete_rule_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;

/** Property-based tests for {@link Text}. */
class TextPropertiesTest {

  @Property
  void normalizeIsIdempotent(@ForAll @AlphaChars @NotBlank String input) {
    String once = Text.normalize(input);
    assertEquals(once, Text.normalize(once));
  }
}
