/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.api.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The corners of the event model that the runtime's own tests used to reach into it for.
 *
 * <p>INTENT: The model is published as a contract now, so its behaviour has to be pinned where the
 * contract lives. Every case here was previously covered only indirectly, from a core test walking
 * a captured tree — which proved the runtime works, not that the contract holds.
 */
class EventModelCoverageTest {

  private static final SpanContext SPAN =
      SpanContext.builder(TraceId.generate(), SpanId.generate()).build();

  @Test
  void spanIdOfReadsEitherSideOfASpan() {
    var signature = new MethodSignature("Svc", "call", List.of());
    var enter = new TraceEvent.EnterEvent(SPAN, 1L, signature);
    var exit = new TraceEvent.ExitEvent(SPAN, 2L, new TraceOutcome.Returned("\"ok\""), null);

    assertThat(TraceEvent.spanIdOf(enter)).isEqualTo(SPAN.spanId());
    assertThat(TraceEvent.spanIdOf(exit)).isEqualTo(SPAN.spanId());
  }

  @Test
  void groupLifecycleEventsBelongToNoSpan() {
    assertThat(TraceEvent.spanIdOf(new TraceEvent.ForkCreatedEvent("g", 1L))).isNull();
    assertThat(TraceEvent.spanIdOf(new TraceEvent.MergeEvent("g", 2, 1L))).isNull();
    assertThat(TraceEvent.spanIdOf(new TraceEvent.FireAndForgetEvent("g", 1L))).isNull();
  }

  @Test
  void anEventWithoutASpanContextHasNoSpanId() {
    var signature = new MethodSignature("Svc", "call", List.of());

    assertThat(TraceEvent.spanIdOf(new TraceEvent.EnterEvent(null, 1L, signature))).isNull();
  }

  @Test
  void spanContextRejectsMissingIdentity() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SpanContext.builder(null, SpanId.generate()).build())
        .withMessageContaining("traceId");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SpanContext.builder(TraceId.generate(), null).build())
        .withMessageContaining("spanId");
  }

  @Test
  void sampledReadsBitZeroOfTheFlagsByte() {
    var traceId = TraceId.generate();
    var spanId = SpanId.generate();

    assertThat(SpanContext.builder(traceId, spanId).traceFlags(1).build().sampled()).isTrue();
    assertThat(SpanContext.builder(traceId, spanId).traceFlags(0).build().sampled()).isFalse();
    assertThat(SpanContext.builder(traceId, spanId).traceFlags(0xfe).build().sampled()).isFalse();
  }

  @Test
  void builderCarriesTheTraceAnchorAndTheResourceIdentity() {
    var anchor = new TraceAnchor(1_700_000_000_000L, 42L);
    var resource = new ResourceIdentity("host-1", 4242L, "17.0.20");

    var span =
        SpanContext.builder(TraceId.generate(), SpanId.generate())
            .traceAnchor(anchor)
            .resourceIdentity(resource)
            .build();

    assertThat(span.traceAnchor()).isEqualTo(anchor);
    assertThat(span.hostName()).isEqualTo("host-1");
    assertThat(span.processPid()).isEqualTo(4242L);
    assertThat(span.runtimeVersion()).isEqualTo("17.0.20");
  }

  @Test
  void aNullResourceIdentityLeavesTheResourceFieldsUnset() {
    var span =
        SpanContext.builder(TraceId.generate(), SpanId.generate()).resourceIdentity(null).build();

    assertThat(span.hostName()).isNull();
    assertThat(span.processPid()).isNull();
    assertThat(span.runtimeVersion()).isNull();
  }

  @Test
  void detectedResourceIdentityIsResolvedOnceAndCarriesTheRuntimeVersion() {
    var current = ResourceIdentity.current();

    assertThat(current).isSameAs(ResourceIdentity.current());
    assertThat(current.runtimeVersion()).isNotBlank();
  }

  @Test
  void aParameterAlwaysHasAValueEvenWhenNoneWasRendered() {
    var capture = new ParameterCapture("orderId", null, false, null, "java.lang.String");

    assertThat(capture.renderedValue())
        .as("null would serialize as a JSON null where the schema requires a string")
        .isEmpty();
  }

  @Test
  void withoutValuesClearsBothValueChannelsAndKeepsEverythingElse() {
    var capture =
        new ParameterCapture(
            "orderId", "\"ORD-1\"", true, new RenderedValue.StringVal("ORD-1"), "java.lang.String");

    var suppressed = capture.withoutValues();

    assertThat(suppressed.renderedValue()).isEmpty();
    assertThat(suppressed.structuredValue()).isNull();
    assertThat(suppressed.name()).isEqualTo("orderId");
    assertThat(suppressed.redacted()).isTrue();
    assertThat(suppressed.type()).isEqualTo("java.lang.String");
  }

  @Test
  void everyStructuredValueVariantIsConstructible() {
    assertThat(new RenderedValue.StringVal("s").value()).isEqualTo("s");
    assertThat(new RenderedValue.BooleanVal(true).value()).isTrue();
    assertThat(new RenderedValue.InstantVal(7L).epochMillis()).isEqualTo(7L);
    assertThat(new RenderedValue.ListVal(List.of(new RenderedValue.NullVal())).elements())
        .hasSize(1);
    assertThat(
            new RenderedValue.ObjectVal("Order", Map.of("id", new RenderedValue.StringVal("1")))
                .fields())
        .containsKey("id");
    assertThat(new RenderedValue.NullVal()).isEqualTo(new RenderedValue.NullVal());
  }

  @Test
  void signatureCopiesPreserveEveryFieldTheyDoNotChange() {
    var original =
        new MethodSignature(
            "OrderService",
            "place",
            List.of(new ParameterCapture("id", "\"1\"", false)),
            "narration",
            "error context",
            "template {id}",
            "com.acme",
            "java.lang.String",
            "instance-7");

    var withError = original.withErrorContext("new context");
    var withParams = original.withParameters(List.of());

    assertThat(withError.errorContext()).isEqualTo("new context");
    assertThat(withError.narrationTemplate()).isEqualTo("template {id}");
    assertThat(withError.packageName()).isEqualTo("com.acme");
    assertThat(withError.returnType()).isEqualTo("java.lang.String");
    assertThat(withError.instanceId()).isEqualTo("instance-7");
    assertThat(withParams.parameters()).isEmpty();
    assertThat(withParams.narrationTemplate()).isEqualTo("template {id}");
    assertThat(withParams.instanceId()).isEqualTo("instance-7");
  }

  @Test
  void durationMillisTruncatesTowardsZero() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "call", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_999_999L);

    assertThat(node.durationMillis()).isEqualTo(1L);
  }

  @Test
  void threadAndSourceIdentityAreValues() {
    assertThat(new ThreadInfo("main", 1L, false))
        .isEqualTo(new ThreadInfo("main", 1L, false))
        .hasSameHashCodeAs(new ThreadInfo("main", 1L, false));
    assertThat(new SourceLocation("Order.java", 42))
        .isEqualTo(new SourceLocation("Order.java", 42))
        .hasToString("SourceLocation[file=Order.java, line=42]");
  }
}
