/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

/**
 * Decides whether a {@link Number}'s own {@code toString()} is safe to emit unsanitized.
 *
 * <p>INTENT: {@link ValueRenderer} and {@code TemplateParser} both fast-path a {@code Number} as a
 * flat scalar, printing its {@code toString()} directly — a {@code String} on the same path is
 * sanitized and length-capped first. {@code Number} is a public abstract class; any caller can
 * extend it and return anything at all from {@code toString()}, including a raw line break or
 * Markdown/JSON structure, and that text used to reach every renderer unsanitized. The eight JDK
 * types this class trusts are the only ones whose {@code toString()} is fixed by the platform, not
 * by application code.
 *
 * <p><b>@llmNote</b> Matched by exact class, not {@code instanceof}. {@link BigInteger} and {@link
 * BigDecimal} are not final, so a subclass overriding {@code toString()} would otherwise inherit
 * the trusted path — the very hole this class exists to close. The other six are all {@code final}
 * (Byte, Short, Integer, Long, Float, Double), so {@code instanceof} alone would already be exact
 * for them; the exact-class check costs nothing extra and keeps one rule for all eight.
 *
 * <p>Public because both {@link ValueRenderer} and {@code ai.narrativetrace.core.template.
 * TemplateParser} apply the identical scalar test and must not drift on the answer.
 *
 * <p><b>@llmNote</b> The rule was settled at the commercial audit tier's own boundary first, and
 * that tier applies the identical one. Two implementations of "is this number's text safe" would
 * drift, and a drifted answer is a forged record on whichever side fell behind.
 */
public final class ScalarTrust {

  private static final Set<Class<?>> JDK_SAFE_NUMERIC_TYPES =
      Set.of(
          Byte.class,
          Short.class,
          Integer.class,
          Long.class,
          Float.class,
          Double.class,
          BigInteger.class,
          BigDecimal.class);

  private ScalarTrust() {}

  /**
   * Whether {@code number}'s own {@code toString()} is guaranteed to contain no forged structure —
   * true for the six boxed primitives plus {@link BigInteger}/{@link BigDecimal}, false for any
   * other {@link Number} subclass, whose {@code toString()} is application code.
   */
  public static boolean isTrustedNumeric(Number number) {
    return JDK_SAFE_NUMERIC_TYPES.contains(number.getClass());
  }
}
