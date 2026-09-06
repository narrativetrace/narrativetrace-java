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

import java.util.Map;
import org.junit.jupiter.api.Test;

class SpanContextFieldsTest {

  @Test
  void serviceNameIsResource() {
    assertThat(SpanContextFields.tier("serviceName")).isEqualTo(AttributeTier.RESOURCE);
  }

  @Test
  void serviceVersionIsResource() {
    assertThat(SpanContextFields.tier("serviceVersion")).isEqualTo(AttributeTier.RESOURCE);
  }

  @Test
  void environmentIsResource() {
    assertThat(SpanContextFields.tier("environment")).isEqualTo(AttributeTier.RESOURCE);
  }

  @Test
  void httpMethodIsTrace() {
    assertThat(SpanContextFields.tier("httpMethod")).isEqualTo(AttributeTier.TRACE);
  }

  @Test
  void httpRouteIsTrace() {
    assertThat(SpanContextFields.tier("httpRoute")).isEqualTo(AttributeTier.TRACE);
  }

  @Test
  void clientIpIsTrace() {
    assertThat(SpanContextFields.tier("clientIp")).isEqualTo(AttributeTier.TRACE);
  }

  @Test
  void enduserIdIsTrace() {
    assertThat(SpanContextFields.tier("enduserId")).isEqualTo(AttributeTier.TRACE);
  }

  @Test
  void sessionIdIsTrace() {
    assertThat(SpanContextFields.tier("sessionId")).isEqualTo(AttributeTier.TRACE);
  }

  @Test
  void tenantIdIsTrace() {
    assertThat(SpanContextFields.tier("tenantId")).isEqualTo(AttributeTier.TRACE);
  }

  @Test
  void traceIdIsSpan() {
    assertThat(SpanContextFields.tier("traceId")).isEqualTo(AttributeTier.SPAN);
  }

  @Test
  void spanIdIsSpan() {
    assertThat(SpanContextFields.tier("spanId")).isEqualTo(AttributeTier.SPAN);
  }

  @Test
  void parentSpanIdIsSpan() {
    assertThat(SpanContextFields.tier("parentSpanId")).isEqualTo(AttributeTier.SPAN);
  }

  @Test
  void traceFlagsIsSpan() {
    assertThat(SpanContextFields.tier("traceFlags")).isEqualTo(AttributeTier.SPAN);
  }

  @Test
  void traceStateIsSpan() {
    assertThat(SpanContextFields.tier("traceState")).isEqualTo(AttributeTier.SPAN);
  }

  @Test
  void spanNameIsSpan() {
    assertThat(SpanContextFields.tier("spanName")).isEqualTo(AttributeTier.SPAN);
  }

  @Test
  void storyIdIsTrace() {
    assertThat(SpanContextFields.tier("storyId")).isEqualTo(AttributeTier.TRACE);
  }

  @Test
  void chapterIdIsTrace() {
    assertThat(SpanContextFields.tier("chapterId")).isEqualTo(AttributeTier.TRACE);
  }

  @Test
  void allSeventeenFieldsClassified() {
    assertThat(SpanContextFields.all()).hasSize(21);
  }

  @Test
  void unknownFieldThrows() {
    assertThatThrownBy(() -> SpanContextFields.tier("nonExistent"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nonExistent");
  }

  @Test
  void resourceFieldsContainsServiceAndProcessIdentity() {
    Map<String, AttributeTier> all = SpanContextFields.all();
    long resourceCount = all.values().stream().filter(t -> t == AttributeTier.RESOURCE).count();
    assertThat(resourceCount).isEqualTo(6);
    assertThat(SpanContextFields.tier("hostName")).isEqualTo(AttributeTier.RESOURCE);
    assertThat(SpanContextFields.tier("processPid")).isEqualTo(AttributeTier.RESOURCE);
    assertThat(SpanContextFields.tier("runtimeVersion")).isEqualTo(AttributeTier.RESOURCE);
  }

  @Test
  void traceFieldsContainsExactlyNineFields() {
    Map<String, AttributeTier> all = SpanContextFields.all();
    long traceCount = all.values().stream().filter(t -> t == AttributeTier.TRACE).count();
    assertThat(traceCount).isEqualTo(9);
    assertThat(SpanContextFields.tier("traceAnchor")).isEqualTo(AttributeTier.TRACE);
  }

  @Test
  void spanFieldsContainsExactlySixFields() {
    Map<String, AttributeTier> all = SpanContextFields.all();
    long spanCount = all.values().stream().filter(t -> t == AttributeTier.SPAN).count();
    assertThat(spanCount).isEqualTo(6);
  }
}
