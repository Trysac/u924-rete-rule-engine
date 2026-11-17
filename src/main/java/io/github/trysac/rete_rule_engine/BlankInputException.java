package io.github.trysac.rete_rule_engine;

/** Thrown when a text input is empty or contains only whitespace. */
public final class BlankInputException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception with the standard message {@code "input is blank"}. */
  public BlankInputException() {
    super("input is blank");
  }
}
