package io.github.trysac.rete_rule_engine;

import java.util.Objects;

/**
 * Exception thrown for rule parsing, validation, and runtime execution errors.
 *
 * <p>Provides a stable machine-readable {@link #code()} along with 1-based {@link #line()} and
 * {@link #column()} numbers for parse errors (or {@code 0} when not a parse location error).
 */
public final class RuleException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final String code;
  private final int line;
  private final int column;

  /**
   * Creates a new rule exception without source coordinates.
   *
   * @param code the error code
   * @param message the detail message
   */
  public RuleException(String code, String message) {
    this(code, message, 0, 0);
  }

  /**
   * Creates a new rule exception with source coordinates.
   *
   * @param code the error code
   * @param message the detail message
   * @param line the 1-based line number, or 0 if not applicable
   * @param column the 1-based column number, or 0 if not applicable
   */
  public RuleException(String code, String message, int line, int column) {
    super(truncate(Objects.requireNonNull(message, "message")));
    this.code = Objects.requireNonNull(code, "code");
    this.line = line;
    this.column = column;
  }

  private static String truncate(String msg) {
    if (msg.length() <= 200) {
      return msg;
    }
    return msg.substring(0, 197) + "...";
  }

  /**
   * Returns the machine-readable error code.
   *
   * @return the error code (such as {@code "syntax"}, {@code "unbound_variable"}, {@code
   *     "duplicate_rule"}, {@code "unknown_type_in_action"}, {@code "invalid_value"}, {@code
   *     "input_too_large"}, or {@code "firing_limit"})
   */
  public String code() {
    return code;
  }

  /**
   * Returns the 1-based line number of the error, or 0 when not a parse error.
   *
   * @return the line number
   */
  public int line() {
    return line;
  }

  /**
   * Returns the 1-based column number of the error, or 0 when not a parse error.
   *
   * @return the column number
   */
  public int column() {
    return column;
  }
}
