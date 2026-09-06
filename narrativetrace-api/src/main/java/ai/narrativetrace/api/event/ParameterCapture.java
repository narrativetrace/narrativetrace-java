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
 * Eager capture of one parameter name and value.
 *
 * <p>INTENT: This is the handoff point between live JVM objects and the immutable trace model.
 * Values are rendered once at capture time so later renderers do not need reflection or object
 * access.
 *
 * @param name Parameter name as discovered from bytecode metadata or instrumentation.
 * @param renderedValue Pre-rendered textual value. Strings include quotes, scalars use plain text,
 *     and non-{@code DETAIL} levels may suppress the value as an empty string.
 * @param redacted Whether the parameter was marked with {@code @NotTraced} and should be rendered
 *     as redacted downstream.
 * @param structuredValue Optional type-preserving representation for OTel typed attributes. Null
 *     when not captured (manual construction, test fixtures) or when the parameter is redacted.
 * @param type Declared parameter type as {@link Class#getTypeName()} renders it (e.g. {@code
 *     "java.lang.String"}, {@code "int"}, {@code "long[]"}), or {@code null} when the capture site
 *     cannot supply it. Captured because overload identity is unrecoverable downstream. Never
 *     rendered into narrative message text.
 */
public record ParameterCapture(
    String name,
    String renderedValue,
    boolean redacted,
    RenderedValue structuredValue,
    String type) {

  /**
   * Normalizes a null rendered value to the empty string.
   *
   * <p>INTENT: "No rendered value" has exactly one representation. Every capture site already
   * writes {@code ""} for that case (see {@link #withoutValues()}); a null slipping in from a
   * third-party {@code ValueRenderer} used to reach the Markdown escaper and abort the whole trace
   * write, and would serialize as a JSON {@code null} where the canonical schema requires a string.
   *
   * <p><b>@llmNote</b> Unlike {@code TraceOutcome.Returned.renderedValue}, where null carries the
   * void-completion contract, a parameter always has a value — so normalizing here loses nothing.
   */
  public ParameterCapture(
      String name,
      String renderedValue,
      boolean redacted,
      RenderedValue structuredValue,
      String type) {
    this.name = name;
    this.renderedValue = renderedValue == null ? "" : renderedValue;
    this.redacted = redacted;
    this.structuredValue = structuredValue;
    this.type = type;
  }

  /** Backwards-compatible constructor for code that does not capture structured values. */
  public ParameterCapture(String name, String renderedValue, boolean redacted) {
    this(name, renderedValue, redacted, null, null);
  }

  /** Backwards-compatible constructor for code that does not capture declared types. */
  public ParameterCapture(
      String name, String renderedValue, boolean redacted, RenderedValue structuredValue) {
    this(name, renderedValue, redacted, structuredValue, null);
  }

  /**
   * Copy with the value channels cleared (rendered value empty, structured value dropped); name,
   * redaction flag, and declared type are preserved. Used by value suppression at non-DETAIL levels
   * — rebuild-by-constructor would silently drop fields added later.
   */
  public ParameterCapture withoutValues() {
    return new ParameterCapture(name, "", redacted, null, type);
  }
}
