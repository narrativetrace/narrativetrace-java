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

/**
 * Semantic signature recorded for a traced invocation.
 *
 * <p>INTENT: This is what renderers read when they need a stable description of a call without
 * touching reflection or live objects.
 *
 * @param className Human-readable declaring type name used in output.
 * @param methodName Declared method name as captured by the proxy or agent.
 * @param parameters Eager parameter captures in call-site order.
 * @param narration Resolved {@code @Narrated} template, or {@code null} when no custom narration
 *     applies.
 * @param errorContext Resolved {@code @OnError} template attached to a failing exit, or {@code
 *     null}.
 * @param narrationTemplate Raw {@code @Narrated} template text with placeholders intact (e.g.
 *     {@code "Opening for {customerId}"}), or {@code null}. Travels to the canonical schema as
 *     {@code nt.narrationTemplate} (schema 1.1) so trace translation can render per-locale template
 *     variants and then fill placeholders from untouched values.
 * @param packageName Package of the declaring type (e.g. {@code "com.acme.billing"}), or {@code
 *     null} when the capture site cannot supply it. Captured because it is unrecoverable
 *     downstream: {@code className} is a display name, and package identity drives glossary
 *     bounded-context resolution. Never rendered into narrative message text.
 * @param returnType Declared return type as {@link Class#getTypeName()} renders it (e.g. {@code
 *     "java.lang.String"}, {@code "void"}, {@code "long[]"}), or {@code null} when the capture site
 *     cannot supply it. With parameter types this makes overloads distinguishable — identity that
 *     is unrecoverable downstream. Never rendered into narrative message text.
 * @param instanceId Identity hash of the receiver object in lowercase hex (e.g. {@code
 *     "1a2b3c4d"}), or {@code null} when instance capture is disabled ({@code
 *     narrativetrace.capture.instanceIds}, default off), the method is static, or the capture site
 *     cannot supply it. Never rendered into narrative message text.
 * @param source Source location, or {@code null} when disabled ({@code
 *     narrativetrace.capture.sourceLocation}, default off) or unknown. Never rendered into
 *     narrative message text.
 */
public record MethodSignature(
    String className,
    String methodName,
    List<ParameterCapture> parameters,
    String narration,
    String errorContext,
    String narrationTemplate,
    String packageName,
    String returnType,
    String instanceId,
    SourceLocation source) {

  public MethodSignature(String className, String methodName, List<ParameterCapture> parameters) {
    this(className, methodName, parameters, null, null, null, null, null, null, null);
  }

  /** Compatibility constructor for call sites that carry no raw narration template. */
  public MethodSignature(
      String className,
      String methodName,
      List<ParameterCapture> parameters,
      String narration,
      String errorContext) {
    this(className, methodName, parameters, narration, errorContext, null, null, null, null, null);
  }

  /** Compatibility constructor for call sites that carry no package name. */
  public MethodSignature(
      String className,
      String methodName,
      List<ParameterCapture> parameters,
      String narration,
      String errorContext,
      String narrationTemplate) {
    this(
        className,
        methodName,
        parameters,
        narration,
        errorContext,
        narrationTemplate,
        null,
        null,
        null,
        null);
  }

  /** Compatibility constructor for call sites that carry no return type. */
  public MethodSignature(
      String className,
      String methodName,
      List<ParameterCapture> parameters,
      String narration,
      String errorContext,
      String narrationTemplate,
      String packageName) {
    this(
        className,
        methodName,
        parameters,
        narration,
        errorContext,
        narrationTemplate,
        packageName,
        null,
        null,
        null);
  }

  /** Compatibility constructor for call sites that carry no source location. */
  public MethodSignature(
      String className,
      String methodName,
      List<ParameterCapture> parameters,
      String narration,
      String errorContext,
      String narrationTemplate,
      String packageName,
      String returnType,
      String instanceId) {
    this(
        className,
        methodName,
        parameters,
        narration,
        errorContext,
        narrationTemplate,
        packageName,
        returnType,
        instanceId,
        null);
  }

  /**
   * Copy with a different error context; every other field is preserved. Rebuild-by-constructor is
   * banned for partial updates — it silently drops fields added later (a shipped bug class: both
   * value suppression and error-context rebuilds lost {@code narrationTemplate}/{@code
   * packageName}).
   */
  public MethodSignature withErrorContext(String newErrorContext) {
    return new MethodSignature(
        className,
        methodName,
        parameters,
        narration,
        newErrorContext,
        narrationTemplate,
        packageName,
        returnType,
        instanceId,
        source);
  }

  /** Copy with different parameter captures; every other field is preserved. */
  public MethodSignature withParameters(List<ParameterCapture> newParameters) {
    return new MethodSignature(
        className,
        methodName,
        newParameters,
        narration,
        errorContext,
        narrationTemplate,
        packageName,
        returnType,
        instanceId,
        source);
  }
}
