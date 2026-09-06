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
import ai.narrativetrace.api.event.RenderedValue;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.junit.jupiter.api.Test;

/**
 * Pins the single-payload wrappers the renderer opens rather than delegating to {@code toString()}.
 *
 * <p>INTENT: A wrapper is not a value. {@link Optional}, {@link AtomicReference} and friends each
 * declare a {@code toString()} that prints their payload's {@code toString()} — so a wrapper the
 * renderer treats as an opaque scalar hands the payload straight to the log with no redaction, no
 * truncation, no cycle detection and no {@code @NarrativeSummary}. Every rule this renderer applies
 * must survive being one wrapper deep.
 *
 * <p><b>@llmNote</b> The flat and structured paths are asserted side by side on purpose: they are
 * parallel implementations of one decision tree, and a wrapper fixed on one of them only is the
 * same leak with a smaller blast radius.
 */
class ValueRendererWrapperTest {

  private final ValueRenderer renderer = new ValueRenderer();

  /** A record whose second component is redacted — the shape the dogfood report leaked. */
  record Card(String number, @NotTraced String cvv) {}

  private static Card card() {
    return new Card("4111", "123");
  }

  @Test
  void aRedactedComponentInsideAnOptionalStaysRedacted() {
    var result = renderer.render(Optional.of(card()));

    assertThat(result).isEqualTo("Card(number: \"4111\", cvv: [REDACTED])");
    assertThat(result).doesNotContain("123");
  }

  @Test
  void aRedactedComponentInsideAnOptionalStaysRedactedInStructuredOutput() {
    var result = renderer.renderStructured(Optional.of(card()));

    assertThat(result).isInstanceOf(RenderedValue.ObjectVal.class);
    var fields = ((RenderedValue.ObjectVal) result).fields();
    assertThat(fields.get("cvv")).isEqualTo(new RenderedValue.StringVal("[REDACTED]"));
    assertThat(result.toString()).doesNotContain("123");
  }

  @Test
  void aDenyListedFieldNameInsideAnOptionalStaysRedacted() {
    var result = renderer.render(Optional.of(new Account()));

    assertThat(result).contains("password: [REDACTED]").doesNotContain("hunter2");
  }

  @Test
  void anEmptyOptionalRendersAsTheAbsentMarker() {
    assertThat(renderer.render(Optional.empty())).isEqualTo("<empty>");
  }

  @Test
  void anEmptyOptionalRendersAsTheAbsentMarkerInStructuredOutput() {
    assertThat(renderer.renderStructured(Optional.empty()))
        .isEqualTo(new RenderedValue.StringVal("<empty>"));
  }

  @Test
  void anOptionalIsTransparentSoItsPayloadRendersExactlyAsItWouldAlone() {
    assertThat(renderer.render(Optional.of("hi"))).isEqualTo(renderer.render("hi"));
    assertThat(renderer.renderStructured(Optional.of("hi")))
        .isEqualTo(renderer.renderStructured("hi"));
  }

  @Test
  void thePrimitiveOptionalsRenderTheirValueRatherThanTheirContainer() {
    assertThat(renderer.render(OptionalInt.of(42))).isEqualTo("42");
    assertThat(renderer.render(OptionalLong.of(42L))).isEqualTo("42");
    assertThat(renderer.render(OptionalDouble.of(1.5))).isEqualTo("1.5");
  }

  @Test
  void theEmptyPrimitiveOptionalsUseTheSameAbsentMarker() {
    assertThat(renderer.render(OptionalInt.empty())).isEqualTo("<empty>");
    assertThat(renderer.render(OptionalLong.empty())).isEqualTo("<empty>");
    assertThat(renderer.render(OptionalDouble.empty())).isEqualTo("<empty>");
  }

  @Test
  void thePrimitiveOptionalsRenderTheirValueInStructuredOutputToo() {
    assertThat(renderer.renderStructured(OptionalInt.of(42)))
        .isEqualTo(new RenderedValue.LongVal(42));
    assertThat(renderer.renderStructured(OptionalLong.of(42L)))
        .isEqualTo(new RenderedValue.LongVal(42));
    assertThat(renderer.renderStructured(OptionalDouble.of(1.5)))
        .isEqualTo(new RenderedValue.DoubleVal(1.5));
    assertThat(renderer.renderStructured(OptionalInt.empty()))
        .isEqualTo(new RenderedValue.StringVal("<empty>"));
  }

  @Test
  void aRedactedComponentInsideAnAtomicReferenceStaysRedacted() {
    var result = renderer.render(new AtomicReference<>(card()));

    assertThat(result).isEqualTo("Card(number: \"4111\", cvv: [REDACTED])");
    assertThat(result).doesNotContain("123");
  }

  @Test
  void aRedactedComponentInsideAnAtomicReferenceStaysRedactedInStructuredOutput() {
    var result = renderer.renderStructured(new AtomicReference<>(card()));

    assertThat(result).isInstanceOf(RenderedValue.ObjectVal.class);
    assertThat(result.toString()).doesNotContain("123");
  }

  @Test
  void anAtomicReferenceHoldingNullRendersAsNull() {
    assertThat(renderer.render(new AtomicReference<>(null))).isEqualTo("null");
    assertThat(renderer.renderStructured(new AtomicReference<>(null)))
        .isEqualTo(new RenderedValue.NullVal());
  }

  @Test
  void anAtomicReferenceHoldingItselfCollapsesToAnIdentityMarkerInsteadOfRecursing() {
    var ref = new AtomicReference<Object>();
    ref.set(ref);

    assertThat(renderer.render(ref)).startsWith("<AtomicReference@");
    assertThat(renderer.renderStructured(ref).toString()).contains("<AtomicReference@");
  }

  @Test
  void anOptionalHoldingAnAtomicReferenceHoldingARecordStaysRedactedAtEveryLevel() {
    var nested = Optional.of(new AtomicReference<>(Optional.of(card())));

    assertThat(renderer.render(nested)).isEqualTo("Card(number: \"4111\", cvv: [REDACTED])");
    assertThat(renderer.renderStructured(nested).toString()).doesNotContain("123");
  }

  @Test
  void aWrappedValueStillHonoursNarrativeSummary() {
    assertThat(renderer.render(Optional.of(new Summarized())))
        .isEqualTo(renderer.render(new Summarized()));
  }

  @Test
  void aWrappedStringIsStillTruncatedToTheConfiguredLength() {
    var renderer = new ValueRenderer(5, 5, 5);

    assertThat(renderer.render(Optional.of("abcdefghij"))).isEqualTo("\"abcde…\"");
  }

  @Test
  void aCollectionOfOptionalsRendersEachPayloadRatherThanEachContainer() {
    var result = renderer.render(java.util.List.of(Optional.of(card()), Optional.empty()));

    assertThat(result).isEqualTo("[Card(number: \"4111\", cvv: [REDACTED]), <empty>]");
  }

  @Test
  void aRedactedComponentInsideAnAtomicReferenceArrayStaysRedacted() {
    var result = renderer.render(new AtomicReferenceArray<>(new Object[] {card()}));

    assertThat(result).isEqualTo("[Card(number: \"4111\", cvv: [REDACTED])]");
    assertThat(result).doesNotContain("123");
  }

  @Test
  void aRedactedComponentInsideAnAtomicReferenceArrayStaysRedactedInStructuredOutput() {
    var result = renderer.renderStructured(new AtomicReferenceArray<>(new Object[] {card()}));

    assertThat(result).isInstanceOf(RenderedValue.ListVal.class);
    assertThat(result.toString()).contains("[REDACTED]").doesNotContain("123");
  }

  @Test
  void anAtomicReferenceArrayIsIndistinguishableFromTheArrayHoldingTheSameElements() {
    var elements = new Object[] {card(), "hi", null};

    assertThat(renderer.render(new AtomicReferenceArray<>(elements)))
        .isEqualTo(renderer.render(elements));
    assertThat(renderer.renderStructured(new AtomicReferenceArray<>(elements)))
        .isEqualTo(renderer.renderStructured(elements));
  }

  @Test
  void anEmptyAtomicReferenceArrayRendersAsAnEmptyList() {
    assertThat(renderer.render(new AtomicReferenceArray<>(0))).isEqualTo("[]");
    assertThat(renderer.renderStructured(new AtomicReferenceArray<>(0)))
        .isEqualTo(new RenderedValue.ListVal(java.util.List.of()));
  }

  @Test
  void anAtomicReferenceArrayLongerThanTheLimitIsTruncatedLikeAnArray() {
    var bounded = new ValueRenderer(200, 2, 5);
    var array = new AtomicReferenceArray<>(new Object[] {1, 2, 3, 4});

    assertThat(bounded.render(array)).isEqualTo("[1, 2, ... (4 total)]");
    assertThat(((RenderedValue.ListVal) bounded.renderStructured(array)).elements()).hasSize(2);
  }

  @Test
  void anAtomicReferenceArrayHoldingItselfCollapsesToAnIdentityMarkerInsteadOfRecursing() {
    var array = new AtomicReferenceArray<Object>(1);
    array.set(0, array);

    assertThat(renderer.render(array))
        .isEqualTo("[<AtomicReferenceArray@" + identity(array) + ">]");
    assertThat(renderer.renderStructured(array).toString()).contains("<AtomicReferenceArray@");
  }

  @Test
  void aRedactedComponentInsideAStandaloneEntryStaysRedacted() {
    var result = renderer.render(Map.entry("card", card()));

    assertThat(result).isEqualTo("card=Card(number: \"4111\", cvv: [REDACTED])");
    assertThat(result).doesNotContain("123");
  }

  /**
   * The value half is deliberately not asserted: the deny-list matches the <em>rendered</em> key
   * text, which here contains {@code cvv}, so the value is over-redacted as well. That is the map
   * path's long-standing behavior, and a standalone entry must not differ from it.
   */
  @Test
  void aRedactedComponentInsideAStandaloneEntryKeyStaysRedacted() {
    var result = renderer.render(Map.entry(card(), "paid"));

    assertThat(result).startsWith("Card(number: \"4111\", cvv: [REDACTED])=").doesNotContain("123");
  }

  @Test
  void aStandaloneEntryIsIndistinguishableFromTheSameEntryInsideAMap() {
    var entry = Map.entry("card", card());

    assertThat("{" + renderer.render(entry) + "}").isEqualTo(renderer.render(Map.ofEntries(entry)));
    assertThat(renderer.renderStructured(entry))
        .isEqualTo(renderer.renderStructured(Map.ofEntries(entry)));
  }

  @Test
  void aStandaloneEntryWithADenyListedKeyRedactsItsValue() {
    var result = renderer.render(Map.entry("password", "hunter2"));

    assertThat(result).isEqualTo("password=[REDACTED]").doesNotContain("hunter2");
  }

  @Test
  void aStandaloneEntryWithANullValueRendersTheNullRatherThanTheRawPair() {
    var entry = new AbstractMap.SimpleEntry<String, Object>("card", null);

    assertThat(renderer.render(entry)).isEqualTo("card=null");
    assertThat(renderer.renderStructured(entry).toString()).contains("NullVal");
  }

  @Test
  void aStandaloneEntryHoldingItselfCollapsesToAnIdentityMarkerInsteadOfRecursing() {
    var entry = new AbstractMap.SimpleEntry<String, Object>("self", null);
    entry.setValue(entry);

    assertThat(renderer.render(entry)).isEqualTo("self=<SimpleEntry@" + identity(entry) + ">");
    assertThat(renderer.renderStructured(entry).toString()).contains("<SimpleEntry@");
  }

  private static String identity(Object value) {
    return Integer.toHexString(System.identityHashCode(value));
  }

  static class Account {
    final String username = "jsmith";
    final String password = "hunter2";
  }

  static class Summarized {
    @ai.narrativetrace.api.annotation.NarrativeSummary
    public String summary() {
      return "summarized";
    }
  }
}
