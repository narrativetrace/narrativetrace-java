/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.NotTraced;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

class ValueRendererPropertyTest {

  private final ValueRenderer renderer = new ValueRenderer();

  @Property
  void renderNeverReturnsNull(
      @ForAll @IntRange(min = Integer.MIN_VALUE, max = Integer.MAX_VALUE) int value) {
    assertThat(renderer.render(value)).isNotNull();
  }

  @Property
  void stringRenderingAlwaysWrapsInQuotes(@ForAll @StringLength(max = 100) String input) {
    var result = renderer.render(input);
    assertThat(result).startsWith("\"");
    assertThat(result).endsWith("\"");
  }

  @Property
  void longStringsAreTruncated(@ForAll @StringLength(min = 201, max = 500) String input) {
    var result = renderer.render(input);
    assertThat(result).contains("\u2026\"");
    // Truncated output should be shorter than rendering the full string
    assertThat(result.length()).isLessThan(input.length() + 3);
  }

  @Property
  void nullAlwaysRendersAsNull() {
    assertThat(renderer.render(null)).isEqualTo("null");
  }

  @Property
  void booleansRenderAsLiteralStrings(@ForAll boolean value) {
    assertThat(renderer.render(value)).isEqualTo(String.valueOf(value));
  }

  @Property
  void charsRenderWithoutCrashing(@ForAll @CharRange(from = '\u0000', to = '\uffff') char value) {
    assertThat(renderer.render(value)).isNotNull().isNotEmpty();
  }

  @Property
  void enumsRenderAsTheirName(@ForAll TimeUnit unit) {
    assertThat(renderer.render(unit)).isEqualTo(unit.name());
  }

  /** A record whose second component is redacted — the payload the wrappers must never expose. */
  record Card(String number, @NotTraced String cvv) {}

  private static final String SECRET = "cvv-901";

  /**
   * The bug class, not the reported instance: redaction survives <em>any</em> stack of the
   * containers this renderer opens, in either direction and to any depth. The reported defect was
   * one {@code Optional} deep; a fix that only handled depth one would pass an example test.
   */
  @Property
  void aRedactedComponentNeverEscapesThroughAnyStackOfContainers(
      @ForAll @IntRange(min = 0, max = 6) int depth, @ForAll long shape) {
    Object value = new Card("4111", SECRET);
    var bits = shape;
    for (var i = 0; i < depth; i++) {
      value = wrap(value, Math.floorMod(bits, CONTAINER_KINDS));
      bits /= CONTAINER_KINDS;
    }

    assertThat(renderer.render(value)).contains("[REDACTED]").doesNotContain(SECRET);
    assertThat(renderer.renderStructured(value).toString()).doesNotContain(SECRET);
  }

  /** Every container the renderer opens rather than printing: see {@link #wrap}. */
  private static final int CONTAINER_KINDS = 5;

  private static Object wrap(Object value, long kind) {
    if (kind == 0) {
      return Optional.of(value);
    }
    if (kind == 1) {
      return new AtomicReference<>(value);
    }
    if (kind == 2) {
      return new AtomicReferenceArray<>(new Object[] {value});
    }
    if (kind == 3) {
      return Map.entry("held", value);
    }
    return List.of(value);
  }

  /** A completed future is the wrapper the renderer already opened; it must stay opened. */
  @Property
  void aRedactedComponentNeverEscapesThroughACompletedFuture(@ForAll boolean wrapInOptional) {
    Object payload = new Card("4111", SECRET);
    var value = CompletableFuture.completedFuture(wrapInOptional ? Optional.of(payload) : payload);

    assertThat(renderer.render(value)).contains("[REDACTED]").doesNotContain(SECRET);
  }
}
