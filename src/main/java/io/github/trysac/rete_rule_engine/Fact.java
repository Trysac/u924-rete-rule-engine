package io.github.trysac.rete_rule_engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable typed fact consisting of a type name and a map of field values.
 *
 * <p>Field values are restricted to {@link String}, {@link Long}, or {@link Boolean}.
 *
 * @param type the fact type identifier
 * @param fields the immutable map of field names to values
 */
public record Fact(String type, Map<String, Object> fields) {

  /**
   * Validates and copies the fact fields.
   *
   * @param type the fact type name
   * @param fields the field map
   * @throws RuleException with code {@code "invalid_value"} if type or any field is invalid
   */
  public Fact {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(fields, "fields");
    if (type.isBlank()) {
      throw new RuleException("invalid_value", "Fact type must not be blank");
    }
    Map<String, Object> copy = new LinkedHashMap<>(fields.size());
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (key == null || key.isBlank()) {
        throw new RuleException("invalid_value", "Field name must not be null or blank");
      }
      validateValue(key, value);
      copy.put(key, value);
    }
    fields = Collections.unmodifiableMap(copy);
  }

  /**
   * Creates a fact from alternating field name and value arguments.
   *
   * @param type the fact type name
   * @param keyValues alternating {@link String} field names and {@link String}, {@link Long}, or
   *     {@link Boolean} values
   * @return the constructed {@link Fact}
   * @throws RuleException with code {@code "invalid_value"} if keys or values are invalid
   */
  public static Fact of(String type, Object... keyValues) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(keyValues, "keyValues");
    if ((keyValues.length & 1) != 0) {
      throw new RuleException(
          "invalid_value", "Key-value arguments must have an even length, got " + keyValues.length);
    }
    Map<String, Object> map = new LinkedHashMap<>(keyValues.length / 2);
    for (int i = 0; i < keyValues.length; i += 2) {
      Object rawKey = keyValues[i];
      if (!(rawKey instanceof String key) || key.isBlank()) {
        throw new RuleException(
            "invalid_value", "Field key at index " + i + " must be a non-blank String");
      }
      if (map.containsKey(key)) {
        throw new RuleException("invalid_value", "Duplicate field key: " + key);
      }
      Object value = keyValues[i + 1];
      validateValue(key, value);
      map.put(key, value);
    }
    return new Fact(type, map);
  }

  static void validateValue(String key, Object value) {
    if (!(value instanceof String || value instanceof Long || value instanceof Boolean)) {
      String kind = value == null ? "null" : value.getClass().getSimpleName();
      throw new RuleException(
          "invalid_value", "Field '" + key + "' must be String, Long, or Boolean, but got " + kind);
    }
  }
}
