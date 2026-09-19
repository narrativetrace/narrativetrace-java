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

/**
 * Decides whether a {@link Number}'s own {@code toString()} may be read at all — the numeric face
 * of the one origin decision, so a number's text is trusted identically wherever a number is
 * printed.
 *
 * <p>INTENT: {@link ValueRenderer} and {@code TemplateParser} both fast-path a {@code Number} as a
 * flat scalar. {@code Number} is a public abstract class, so extending it says nothing about what
 * an instance holds: a subclass is an ordinary composite that happens to have a numeric base, free
 * to carry a deny-listed field and print it from a {@code toString()} its author wrote years before
 * anyone traced the class. A fast path that reads that text has only the value-shape scan and the
 * length cap in front of it — no field name for the deny-list to match, no {@code @NotTraced} to
 * honor, none of the caps a walked composite obeys. So the only numbers whose own text is read are
 * the platform's numeric leaf types, matched by the EXACT class, which IS its origin; every other
 * {@code Number} is walked field by field like any other composite, on both rendering paths alike.
 *
 * <p><b>@llmNote</b> The list is {@link PlatformTypes#isStatelessLeaf}'s, asked here rather than
 * restated: the boxed primitives, {@link BigInteger} and {@link BigDecimal} — neither of them
 * {@code final}, which is why assignability would be no test at all — and the single-cell atomics
 * and adders. Sanitizing the text was the answer this replaced, and it was only ever half of one:
 * an escape can make a forged line harmless, but nothing about it can make a printed secret
 * unprinted.
 *
 * <p>Public because both {@link ValueRenderer} and {@code ai.narrativetrace.core.template.
 * TemplateParser} apply the identical scalar test and must not drift on the answer.
 *
 * <p><b>@llmNote</b> The rule was settled at the commercial audit tier's own boundary first, and
 * that tier applies the identical one. Two implementations of "may this number's text be read"
 * would drift, and a drifted answer is a forged record on whichever side fell behind.
 */
public final class ScalarTrust {

  private ScalarTrust() {}

  /**
   * Whether {@code number}'s own {@code toString()} may be read — true for the platform's numeric
   * leaf types, matched by exact class, false for every other {@link Number} subclass, which is a
   * composite and whose state is read by walking it instead.
   */
  public static boolean isTrustedNumeric(Number number) {
    return PlatformTypes.isStatelessLeaf(number.getClass());
  }
}
