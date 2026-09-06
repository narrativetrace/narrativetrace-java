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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SpanContextTest {

  private static final String VALID_TRACE_ID = "0123456789abcdef0123456789abcdef";
  private static final String VALID_SPAN_ID = "0123456789abcdef";
  private static final String VALID_PARENT_SPAN_ID = "fedcba9876543210";

  @Test
  void validSpanContextPreservesAllFields() {
    var ctx =
        new SpanContext(
            TraceId.of(VALID_TRACE_ID),
            SpanId.of(VALID_SPAN_ID),
            SpanId.of(VALID_PARENT_SPAN_ID),
            1,
            "congo=t61rcWkgMzE",
            "order-service",
            "1.2.3",
            "production",
            "POST",
            HttpRoute.of("/api/orders"),
            ClientIp.of("client-ip-1"),
            EnduserId.of("user-42"),
            SessionId.of("sess-abc"),
            TenantId.of("tenant-1"),
            "placeOrder",
            "OrderService.placeOrder",
            "OrderService.placeOrder:PaymentService.charge");

    assertThat(ctx.traceId()).isEqualTo(TraceId.of(VALID_TRACE_ID));
    assertThat(ctx.spanId()).isEqualTo(SpanId.of(VALID_SPAN_ID));
    assertThat(ctx.parentSpanId()).isEqualTo(SpanId.of(VALID_PARENT_SPAN_ID));
    assertThat(ctx.traceFlags()).isEqualTo(1);
    assertThat(ctx.traceState()).isEqualTo("congo=t61rcWkgMzE");
    assertThat(ctx.serviceName()).isEqualTo("order-service");
    assertThat(ctx.serviceVersion()).isEqualTo("1.2.3");
    assertThat(ctx.environment()).isEqualTo("production");
    assertThat(ctx.httpMethod()).isEqualTo("POST");
    assertThat(ctx.httpRoute()).hasToString("/api/orders");
    assertThat(ctx.clientIp()).hasToString("client-ip-1");
    assertThat(ctx.enduserId()).hasToString("user-42");
    assertThat(ctx.sessionId()).hasToString("sess-abc");
    assertThat(ctx.tenantId()).hasToString("tenant-1");
    assertThat(ctx.spanName()).isEqualTo("placeOrder");
    assertThat(ctx.storyId()).isEqualTo("OrderService.placeOrder");
    assertThat(ctx.chapterId()).isEqualTo("OrderService.placeOrder:PaymentService.charge");
  }

  @Test
  void traceIdMustBe32LowercaseHex() {
    assertThatThrownBy(() -> new TraceId("short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traceId");
    assertThatThrownBy(() -> new TraceId("0123456789ABCDEF0123456789abcdef"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TraceId(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void spanIdMustBe16LowercaseHex() {
    assertThatThrownBy(() -> new SpanId("short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("spanId");
    assertThatThrownBy(() -> new SpanId("0123456789ABCDEF"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SpanId(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parentSpanIdNullForRoot() {
    var ctx = SpanContext.builder(TraceId.of(VALID_TRACE_ID), SpanId.of(VALID_SPAN_ID)).build();
    assertThat(ctx.parentSpanId()).isNull();
  }

  @Test
  void invalidParentSpanIdRejected() {
    assertThatThrownBy(() -> new SpanId("short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("spanId");
    assertThatThrownBy(() -> new SpanId("0123456789ABCDEF"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void builderCreatesMinimalSpanContext() {
    var ctx = SpanContext.builder(TraceId.of(VALID_TRACE_ID), SpanId.of(VALID_SPAN_ID)).build();

    assertThat(ctx.traceId()).isEqualTo(TraceId.of(VALID_TRACE_ID));
    assertThat(ctx.spanId()).isEqualTo(SpanId.of(VALID_SPAN_ID));
    assertThat(ctx.parentSpanId()).isNull();
    assertThat(ctx.traceFlags()).isZero();
    assertThat(ctx.traceState()).isNull();
    assertThat(ctx.serviceName()).isNull();
    assertThat(ctx.serviceVersion()).isNull();
    assertThat(ctx.environment()).isNull();
    assertThat(ctx.httpMethod()).isNull();
    assertThat(ctx.httpRoute()).isNull();
    assertThat(ctx.clientIp()).isNull();
    assertThat(ctx.enduserId()).isNull();
    assertThat(ctx.sessionId()).isNull();
    assertThat(ctx.tenantId()).isNull();
    assertThat(ctx.spanName()).isNull();
    assertThat(ctx.storyId()).isNull();
    assertThat(ctx.chapterId()).isNull();
  }

  @Test
  void builderSetsOptionalFields() {
    var ctx =
        SpanContext.builder(TraceId.of(VALID_TRACE_ID), SpanId.of(VALID_SPAN_ID))
            .parentSpanId(SpanId.of(VALID_PARENT_SPAN_ID))
            .traceFlags(1)
            .traceState("congo=t61rcWkgMzE")
            .serviceName("order-service")
            .serviceVersion("1.2.3")
            .environment("production")
            .httpMethod("POST")
            .httpRoute(HttpRoute.of("/api/orders"))
            .clientIp(ClientIp.of("client-ip-1"))
            .enduserId(EnduserId.of("user-42"))
            .sessionId(SessionId.of("sess-abc"))
            .tenantId(TenantId.of("tenant-1"))
            .spanName("placeOrder")
            .storyId("OrderService.placeOrder")
            .chapterId("OrderService.placeOrder:PaymentService.charge")
            .build();

    assertThat(ctx.parentSpanId()).isEqualTo(SpanId.of(VALID_PARENT_SPAN_ID));
    assertThat(ctx.traceFlags()).isEqualTo(1);
    assertThat(ctx.traceState()).isEqualTo("congo=t61rcWkgMzE");
    assertThat(ctx.serviceName()).isEqualTo("order-service");
    assertThat(ctx.serviceVersion()).isEqualTo("1.2.3");
    assertThat(ctx.environment()).isEqualTo("production");
    assertThat(ctx.httpMethod()).isEqualTo("POST");
    assertThat(ctx.httpRoute()).hasToString("/api/orders");
    assertThat(ctx.clientIp()).hasToString("client-ip-1");
    assertThat(ctx.enduserId()).hasToString("user-42");
    assertThat(ctx.sessionId()).hasToString("sess-abc");
    assertThat(ctx.tenantId()).hasToString("tenant-1");
    assertThat(ctx.spanName()).isEqualTo("placeOrder");
    assertThat(ctx.storyId()).isEqualTo("OrderService.placeOrder");
    assertThat(ctx.chapterId()).isEqualTo("OrderService.placeOrder:PaymentService.charge");
  }
}
