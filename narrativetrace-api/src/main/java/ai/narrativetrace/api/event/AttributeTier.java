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

/**
 * Classifies {@link SpanContext} fields by lifecycle and ownership.
 *
 * <p>INTENT: Exporters, MDC bridges, and OTel mappers query this enum to decide which fields to
 * emit at which point — resource-level once per service, trace-level once per request on the root
 * span, span-level per operation.
 *
 * <p><b>@llmNote</b> This classification aligns with OpenTelemetry's attribute model. Resource
 * attributes map to OTel Resource, trace-level to root span attributes, span-level to all span
 * attributes. Internally, {@link SpanContext} still carries all fields on every span for debugging
 * convenience; the optimization is at the export boundary.
 *
 * @see SpanContextFields
 */
public enum AttributeTier {

  /** Set once per service lifetime: service name, version, environment. */
  RESOURCE,

  /**
   * Set once per request at the entry point: HTTP method, route, client IP, end-user identity,
   * session, tenant. Lives on the root span only in exports.
   */
  TRACE,

  /**
   * Set per operation: trace id, span id, parent span id, trace flags, trace state, span name.
   * Every span carries these.
   */
  SPAN
}
