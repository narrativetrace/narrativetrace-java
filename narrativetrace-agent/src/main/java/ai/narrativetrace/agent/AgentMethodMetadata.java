/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import ai.narrativetrace.api.event.SourceLocation;
import ai.narrativetrace.core.render.RedactionPolicy;

/**
 * Everything about one instrumented method that never changes between calls.
 *
 * <p>INTENT: The injected call site used to rebuild all of this on every invocation — a {@code
 * String[]} of parameter names and a {@code boolean[]} of redaction flags in bytecode, then, inside
 * {@link AgentRuntime}, two substrings of the qualified class name and a descriptor parse for the
 * declared parameter and return types. None of it depends on the call. It is computed once, while
 * the method is being instrumented, and the emitted bytecode carries an {@code int} id instead.
 *
 * <p><b>@llmNote</b> This is the agent's counterpart of the proxy's {@code ProxyMethodMetadata},
 * and it is keyed differently on purpose. The proxy caches by {@code Method}, which compares by
 * {@code equals} — two {@code Method} objects for the same method are equal, and that aliasing is
 * what once made a hoist unsound there. Here the key is the instrumentation site itself: one id is
 * minted per method per transform, so two classes with the same name defined by different loaders
 * hold two ids and two records, and nothing can alias.
 *
 * @param simpleClassName class name as narration records it, split at instrumentation time
 * @param packageName package of the instrumented class, or {@code null} for the default package
 * @param parameterNames capture names, {@code argN} where the class file names no parameter, and
 *     empty for a method whose parameters cannot be named at all
 * @param parameterTypes declared parameter types in {@link Class#getTypeName()} form, parallel to
 *     {@code parameterNames}
 * @param redacted which parameters carry {@code @NotTraced}, parallel to {@code parameterNames}
 * @param returnType declared return type in {@link Class#getTypeName()} form
 * @param narratedTemplate the {@code @Narrated} template, or {@code null}
 * @param sourceLocation the baked source location, or {@code null} when the class carries none;
 *     recorded only when the context asks for it
 * @param onErrors the {@code @OnError} entries the catch handler resolves against, or {@code null}
 */
record AgentMethodMetadata(
    String simpleClassName,
    String packageName,
    String methodName,
    String[] parameterNames,
    String[] parameterTypes,
    boolean[] redacted,
    String returnType,
    String narratedTemplate,
    SourceLocation sourceLocation,
    MethodMetadata.OnErrorEntry[] onErrors) {

  private static final String[] NO_NAMES = new String[0];
  private static final boolean[] NO_FLAGS = new boolean[0];

  /** The record for a method whose parameters the class file can name. */
  static AgentMethodMetadata of(
      String qualifiedClassName, String methodName, String descriptor, MethodMetadata collected) {
    var names = captureNames(collected.parameterNames());
    return new AgentMethodMetadata(
        simpleName(qualifiedClassName),
        packageName(qualifiedClassName),
        methodName,
        names,
        MethodDescriptorTypes.parameterTypes(descriptor),
        redactedByAnnotationOrName(names, collected.redacted()),
        MethodDescriptorTypes.returnType(descriptor),
        collected.narratedTemplate(),
        sourceLocation(collected),
        collected.onErrors());
  }

  /**
   * Widens the annotation flags with the name deny-list, once per instrumented method.
   *
   * <p>INTENT: {@code @NotTraced} used to be the only thing that redacted a parameter, so a method
   * taking {@code String password} printed it in full — the deny-list reached fields and record
   * components but was never asked about a parameter name. This is where it is asked.
   *
   * <p><b>@llmNote</b> Here rather than in a renderer, for two reasons. The cheap one: this record
   * is built once, at instrumentation time, and the hot path only ever reads the resulting {@code
   * boolean[]} — so the lookup costs nothing per traced call, however large the vocabulary grows.
   * The important one: a value redacted at capture never enters the {@link
   * ai.narrativetrace.api.event.TraceEvent} at all, so it cannot reach the audit emitter, the
   * buffered consumer, or any listener attached through the pipeline SPI. Redacting in a renderer
   * would leave the cleartext travelling through all of them.
   *
   * @param names capture names, parallel to the flags
   * @param annotated which parameters carry {@code @NotTraced}
   * @return flags widened with every name the deny-list denies
   */
  private static boolean[] redactedByAnnotationOrName(String[] names, boolean[] annotated) {
    if (names.length == 0) {
      return annotated;
    }
    var widened = new boolean[names.length];
    for (int i = 0; i < names.length; i++) {
      var wasAnnotated = i < annotated.length && annotated[i];
      widened[i] = RedactionPolicy.DEFAULT.isRedacted(names[i], wasAnnotated);
    }
    return widened;
  }

  /**
   * The record for a method the class file names no parameter of — no {@code MethodParameters}
   * attribute and no debug information.
   *
   * <p><b>@edgeCase</b> It records no parameters at all, and no narration either. Both follow the
   * call site this replaced: with no names there is nothing to label a value with and nothing to
   * interpolate a template against, so the values were never marshalled and the template was never
   * resolved. Handing the template an empty value map here would start emitting narrations full of
   * unresolved placeholders where there used to be none.
   */
  static AgentMethodMetadata bare(String qualifiedClassName, String methodName, String descriptor) {
    return new AgentMethodMetadata(
        simpleName(qualifiedClassName),
        packageName(qualifiedClassName),
        methodName,
        NO_NAMES,
        NO_NAMES,
        NO_FLAGS,
        MethodDescriptorTypes.returnType(descriptor),
        null,
        null,
        null);
  }

  /** Names to capture under, with {@code argN} standing in wherever the class file named none. */
  private static String[] captureNames(String[] declared) {
    var names = new String[declared.length];
    for (int i = 0; i < declared.length; i++) {
      names[i] = declared[i] != null ? declared[i] : "arg" + i;
    }
    return names;
  }

  private static SourceLocation sourceLocation(MethodMetadata collected) {
    if (collected.sourceFile() == null && collected.lineNumber() == null) {
      return null;
    }
    return new SourceLocation(collected.sourceFile(), collected.lineNumber());
  }

  /**
   * Simple class name from the qualified name the instrumentation holds. Splitting here rather than
   * in the runtime keeps agent output aligned with the proxy path, which records simple names — and
   * costs the substring once per method instead of once per call.
   */
  static String simpleName(String qualifiedName) {
    int lastDot = qualifiedName.lastIndexOf('.');
    return lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1);
  }

  /** Package of the qualified name, or {@code null} for the default package. */
  static String packageName(String qualifiedName) {
    int lastDot = qualifiedName.lastIndexOf('.');
    return lastDot < 0 ? null : qualifiedName.substring(0, lastDot);
  }
}
