/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.fixtures;

/**
 * Every near miss of the scanner's three skip rules, in one class.
 *
 * <p>Each member here shares a prefix, a name or a shape with something the scanner is supposed to
 * drop, and is vocabulary someone chose: {@code copy} with nothing to copy, a {@code componentN}
 * that takes an argument, a sibling of the {@code componentN} pattern, and an {@code equals} whose
 * parameter type is not {@code Object}. Only {@code copy(String)} and {@code component2()} have the
 * synthesized shape.
 */
public class SkipRuleNearMissFixture {

  private final String title;

  public SkipRuleNearMissFixture(String title) {
    this.title = title;
  }

  /** The synthesized shape: arguments, and the declaring type back. Skipped. */
  public SkipRuleNearMissFixture copy(String title) {
    return new SkipRuleNearMissFixture(title);
  }

  /** The near miss: a {@code copy} taking nothing copies nothing of its own. */
  public String copy() {
    return title;
  }

  /** The near miss: a name that merely begins with {@code copy}. */
  public String copyFrom(SkipRuleNearMissFixture other) {
    return other.title;
  }

  /** The synthesized shape: {@code componentN}, no arguments. Skipped. */
  public String component2() {
    return title;
  }

  /** The near miss: the pattern is {@code componentN} with no arguments, and this takes one. */
  public String component1(int index) {
    return title.substring(index);
  }

  /** The near miss: a sibling sharing the prefix, which {@code component\d+} does not match. */
  public String component1x() {
    return title;
  }

  /** The near miss: {@code Object} declares {@code equals(Object)}, not this. */
  @SuppressWarnings("PMD.SuspiciousEqualsMethodName") // the near miss IS the fixture's point
  public boolean equals(SkipRuleNearMissFixture other) {
    return other != null && title.equals(other.title);
  }

  /** Object's own, overridden deliberately — still not vocabulary. */
  @Override
  public String toString() {
    return title;
  }

  /** Not part of the narrative surface. */
  String packagePrivateMember() {
    return title;
  }
}
