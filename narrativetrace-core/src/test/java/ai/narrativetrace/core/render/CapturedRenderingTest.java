/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import org.junit.jupiter.api.Test;

/**
 * Focused coverage for {@link ValueRenderer#renderForCapture(Object)}: the seam that reports the
 * value-shape axis of redaction back to a capture site, so {@code ParameterCapture.redacted()} can
 * stay true whenever the whole value was withheld — not just when a name matched.
 *
 * <p><b>@llmNote</b> {@code CapturePathRedactionConformanceTest} in {@code
 * narrativetrace-security-tests} exercises the same seam end-to-end through a real traced proxy
 * call; this class pins the renderer's own contract in isolation, including the documented boundary
 * that a nested shape match does not promote to the parameter-level flag.
 */
class CapturedRenderingTest {

  private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2lnbmF0dXJl";

  private final ValueRenderer renderer = new ValueRenderer();

  @Test
  void flagsAJwtShapedStringUnderAnInnocuousParameterName() {
    var capture = renderer.renderForCapture(JWT);

    assertThat(capture.rendered()).isEqualTo(RedactionPolicy.MARKER);
    assertThat(capture.structured()).isEqualTo(new RenderedValue.StringVal(RedactionPolicy.MARKER));
    assertThat(capture.shapeRedacted()).isTrue();
  }

  @Test
  void doesNotFlagAnOrdinaryString() {
    var capture = renderer.renderForCapture("hello world");

    assertThat(capture.rendered()).isEqualTo("\"hello world\"");
    assertThat(capture.shapeRedacted()).isFalse();
  }

  record TokenHolder(String jwt, String label) {}

  @Test
  void doesNotFlagTheParameterWhenTheJwtIsNestedInsideADto() {
    var capture = renderer.renderForCapture(new TokenHolder(JWT, "primary"));

    assertThat(capture.rendered()).contains(RedactionPolicy.MARKER).doesNotContain(JWT);
    assertThat(capture.shapeRedacted())
        .as("a shape match on a nested field masks the field, not the whole parameter")
        .isFalse();
  }

  @Test
  void mirrorsRenderAndRenderStructuredForTheSameValue() {
    var capture = renderer.renderForCapture(42);

    assertThat(capture.rendered()).isEqualTo(renderer.render(42));
    assertThat(capture.structured()).isEqualTo(renderer.renderStructured(42));
    assertThat(capture.shapeRedacted()).isFalse();
  }

  @Test
  void doesNotFlagNull() {
    var capture = renderer.renderForCapture(null);

    assertThat(capture.shapeRedacted()).isFalse();
  }
}
