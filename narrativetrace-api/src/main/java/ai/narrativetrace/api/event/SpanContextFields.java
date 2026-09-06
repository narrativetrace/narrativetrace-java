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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Classifies each {@link SpanContext} field by {@link AttributeTier}.
 *
 * <p>INTENT: Exporters, MDC bridges, and OTel mappers query this utility to decide which fields to
 * emit at which lifecycle point. Field names match the {@link SpanContext} record component names.
 *
 * <p><b>@llmNote</b> The classification is static and exhaustive — every SpanContext field has
 * exactly one tier. Unknown field names throw {@link IllegalArgumentException}. Use {@link #all()}
 * to enumerate all fields with their tiers.
 */
public final class SpanContextFields {

  private static final Map<String, AttributeTier> FIELDS;

  static {
    var map = new LinkedHashMap<String, AttributeTier>();
    // Resource tier — service identity, set once per service lifetime
    map.put("serviceName", AttributeTier.RESOURCE);
    map.put("serviceVersion", AttributeTier.RESOURCE);
    map.put("environment", AttributeTier.RESOURCE);
    // Resource tier — auto-detected process identity (narrativetrace.capture.resource)
    map.put("hostName", AttributeTier.RESOURCE);
    map.put("processPid", AttributeTier.RESOURCE);
    map.put("runtimeVersion", AttributeTier.RESOURCE);
    // Trace tier — request/user identity, set once per request on root span
    map.put("httpMethod", AttributeTier.TRACE);
    map.put("httpRoute", AttributeTier.TRACE);
    map.put("clientIp", AttributeTier.TRACE);
    map.put("enduserId", AttributeTier.TRACE);
    map.put("sessionId", AttributeTier.TRACE);
    map.put("tenantId", AttributeTier.TRACE);
    // Trace tier — story/chapter identity, set once per request from root method
    map.put("storyId", AttributeTier.TRACE);
    map.put("chapterId", AttributeTier.TRACE);
    // Trace tier — wall-clock anchor, captured once at local trace start
    map.put("traceAnchor", AttributeTier.TRACE);
    // Span tier — per-operation identity, every span carries these
    map.put("traceId", AttributeTier.SPAN);
    map.put("spanId", AttributeTier.SPAN);
    map.put("parentSpanId", AttributeTier.SPAN);
    map.put("traceFlags", AttributeTier.SPAN);
    map.put("traceState", AttributeTier.SPAN);
    map.put("spanName", AttributeTier.SPAN);
    FIELDS = Collections.unmodifiableMap(map);
  }

  private SpanContextFields() {}

  /**
   * Returns the tier for the given {@link SpanContext} field name.
   *
   * @param fieldName a SpanContext record component name (e.g. {@code "httpRoute"}, {@code
   *     "traceId"})
   * @return the corresponding {@link AttributeTier}
   * @throws IllegalArgumentException if the field name is not a known SpanContext field
   */
  public static AttributeTier tier(String fieldName) {
    var tier = FIELDS.get(fieldName);
    if (tier == null) {
      throw new IllegalArgumentException("Unknown SpanContext field: " + fieldName);
    }
    return tier;
  }

  /**
   * Returns an unmodifiable map of all {@link SpanContext} field names to their tiers.
   *
   * @return all 21 field classifications
   */
  public static Map<String, AttributeTier> all() {
    return FIELDS;
  }
}
