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

import java.util.List;
import java.util.Map;

/**
 * Structured value representation preserving original Java types.
 *
 * <p>INTENT: Alongside the flat string rendering ({@code ValueRenderer.render}), this sealed
 * hierarchy preserves type information for downstream consumers that benefit from it — primarily
 * OTel exporters that can emit typed span attributes (long, double, boolean) and structured object
 * fields as dot-separated keys.
 *
 * <p>Existing renderers (Markdown, prose, logs) continue using the flat string form. The structured
 * form is an optional companion captured at the same boundary (proxy/agent), produced by the
 * runtime's {@code ValueRenderer.renderStructured(Object)}.
 */
public sealed interface RenderedValue {

  /** A string value. Used for actual strings, enums, characters, and custom toString fallbacks. */
  record StringVal(String value) implements RenderedValue {}

  /** An integral numeric value (int, long, short, byte). */
  record LongVal(long value) implements RenderedValue {}

  /** A floating-point numeric value (float, double, BigDecimal). */
  record DoubleVal(double value) implements RenderedValue {}

  /** A boolean value. */
  record BooleanVal(boolean value) implements RenderedValue {}

  /**
   * A timestamp as epoch milliseconds.
   *
   * <p>Distinguished from {@link LongVal} so OTel exporters can emit numeric range queries (e.g.
   * "orders where createdAt > now - 1h") and Pro annotations can map to semantic timestamp
   * attributes.
   */
  record InstantVal(long epochMillis) implements RenderedValue {}

  /**
   * A structured object with named fields, preserving POJO/record field types.
   *
   * @param typeName the simple class name of the original object
   * @param fields ordered map of field name to structured value
   */
  record ObjectVal(String typeName, Map<String, RenderedValue> fields) implements RenderedValue {}

  /**
   * An ordered collection of values (from arrays, lists, sets).
   *
   * @param elements the structured elements, subject to the same size limits as string rendering
   */
  record ListVal(List<RenderedValue> elements) implements RenderedValue {}

  /** A null value. */
  record NullVal() implements RenderedValue {}
}
