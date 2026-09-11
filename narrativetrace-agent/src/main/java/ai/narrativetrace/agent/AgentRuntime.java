/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SourceLocation;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.NoopNarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ai.narrativetrace.core.render.ValueRenderer;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime support class invoked from injected bytecode.
 *
 * <p>INTENT: Instrumented methods call into this class instead of reaching directly into core APIs.
 * It owns the shared agent {@link NarrativeContext}, eager value rendering, and runtime template
 * resolution.
 *
 * <p><b>@llmNote</b> The agent stores the previous scoped parent returned by {@code beginScope} and
 * restores it on exit. This is how bytecode instrumentation preserves parentage without passing
 * span ids through user code.
 *
 * <p><b>@sideEffects</b> Every hook injected into user bytecode is <em>total</em>: it catches
 * {@link Throwable} and returns as if it had done nothing. This is not defensive style, it is the
 * only correct shape. {@code enterMethod} is emitted <em>before</em> the synthetic try range opens
 * ({@link NarrativeMethodVisitor#onMethodEnter()}), so a throw from it skips the instrumented
 * method body entirely — a hostile parameter could turn "the method ran" into "the method never
 * ran". The exit hooks sit inside that range, so a throw from one of them is rethrown at the caller
 * in place of the business result. Observability failure must never become application failure, and
 * {@code Error} subclasses ({@code AssertionError}, {@code LinkageError}) are realistic failure
 * modes for user {@code toString()}/{@code iterator()} implementations.
 */
public final class AgentRuntime {

  private static final ValueRenderer VALUE_RENDERER = new ValueRenderer();
  private static final MethodMetadataRegistry METHODS = new MethodMetadataRegistry();

  /**
   * The context every injected hook reads — off until {@link #initialize} says otherwise.
   *
   * <p><b>@llmNote</b> This field's initial value must not build a pipeline. It used to be {@code
   * new ThreadLocalNarrativeContext()}, and that default was constructed during this class's static
   * initialisation — which is triggered by {@link #initialize} itself, one statement before the
   * configured context replaces it. So every agent attach built two pipelines and threw the first
   * away, and the discarded one narrated under the <em>default</em> logger name whatever the attach
   * had asked for: a host that said {@code loggerName=} to switch narration off still resolved
   * SLF4J and printed its no-provider warning, and a host that named its own logger briefly had two
   * rings and a publisher for one JVM. An uninitialised agent tracing nothing is also the more
   * honest reading — the transformer is registered after {@link #initialize}, so no instrumented
   * method can reach these hooks before a real context is in place.
   */
  private static volatile NarrativeContext context = NoopNarrativeContext.INSTANCE;

  private AgentRuntime() {}

  /**
   * Records one instrumented method's constants and returns the id its call site will carry.
   *
   * <p>INTENT: Called once per method, from {@link NarrativeMethodVisitor} while the class is being
   * transformed — never from a call path. Everything a call would otherwise rebuild (the capture
   * names, the redaction flags, the split class name, the declared types parsed out of the
   * descriptor) is computed here instead, so an active call marshals only its argument values.
   */
  static int registerMethod(AgentMethodMetadata metadata) {
    return METHODS.register(metadata);
  }

  /** The metadata behind an id, or {@code null} when this runtime never minted it. */
  static AgentMethodMetadata methodMetadata(int metadataId) {
    return METHODS.get(metadataId);
  }

  public static void initialize(AgentConfig config) {
    var traceConfig = new NarrativeTraceConfig(config.level());
    context =
        new ThreadLocalNarrativeContext(
            traceConfig, PipelineBootstrap.createDefault(config.loggerName()));
  }

  public static void setContext(NarrativeContext ctx) {
    context = ctx;
  }

  public static NarrativeContext getContext() {
    return context;
  }

  /**
   * Whether tracing is on — the gate every instrumented method reads before it marshals anything.
   *
   * <p>INTENT: The injected call site cannot pay for a capture it will not make. This is emitted
   * first in every instrumented method ({@link NarrativeMethodVisitor}), and the argument array,
   * the boxing and the enter call all sit behind it.
   *
   * <p><b>@llmNote</b> Total, and it has to be: the gate is emitted <em>before</em> the synthetic
   * try range opens, so a throw here would propagate in place of the instrumented method's body. A
   * context that will not answer is treated as off — the same rule the proxy applies. Keep this a
   * plain boolean read; anything heavier belongs behind the gate, not in it.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // tracing may never fail the method
  public static boolean isActive() {
    try {
      return context.isActive();
    } catch (Throwable t) { // NOPMD - a context that cannot answer is not tracing
      return false;
    }
  }

  /**
   * The entry point current instrumentation emits: the per-method constants arrive as an id into
   * the instrumentation-site table, and only the argument values are marshalled per call.
   *
   * <p><b>@llmNote</b> An id this runtime never minted answers {@code null} — no trace, no throw.
   * That is the only safe reading: the alternative to "this call is not described" would be "this
   * call is described with some other method's parameter names".
   *
   * @param metadataId the id {@link #registerMethod} minted for this call site
   * @param paramValues the argument values, in declaration order
   * @param instance {@code this} of the instrumented method, {@code null} for a static one
   * @return the previous scoped parent to restore on exit, or {@code null}
   */
  public static String enterMethod(int metadataId, Object[] paramValues, Object instance) {
    try {
      var metadata = METHODS.get(metadataId);
      return metadata == null ? null : enterRegistered(metadata, paramValues, instance);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - tracing may not fail the method
      return null;
    }
  }

  private static String enterRegistered(
      AgentMethodMetadata metadata, Object[] paramValues, Object instance) {
    if (!context.isActive()) return null;
    var names = metadata.parameterNames();
    var redacted = metadata.redacted();
    var narration = resolveNarration(metadata.narratedTemplate(), names, paramValues, redacted);
    var captures =
        buildCaptures(
            names,
            metadata.parameterTypes(),
            paramValues,
            redacted,
            context.capturesParameterValues());
    SpanId spanId =
        context.enterMethod(
            new MethodSignature(
                metadata.simpleClassName(),
                metadata.methodName(),
                List.copyOf(captures),
                narration,
                null,
                metadata.narratedTemplate(),
                metadata.packageName(),
                metadata.returnType(),
                instanceIdOf(instance),
                bakedSourceLocation(metadata.sourceLocation())));
    SpanId prev = context.beginScope(spanId);
    return prev != null ? prev.value() : null;
  }

  /** The location instrumentation baked in, or {@code null} when the context does not want it. */
  private static SourceLocation bakedSourceLocation(SourceLocation baked) {
    return baked != null && context.capturesSourceLocation() ? baked : null;
  }

  public static String enterMethod(String className, String methodName) {
    return enterMethod(className, methodName, (String) null);
  }

  /**
   * Bare entry point for methods instrumented without parameter capture. {@code methodDescriptor}
   * is the JVM method descriptor baked in at instrumentation time (e.g. {@code "()V"}), used only
   * to record the declared return type; {@code null} when the emitting bytecode predates it.
   */
  public static String enterMethod(String className, String methodName, String methodDescriptor) {
    return enterMethod(className, methodName, methodDescriptor, null);
  }

  /** Bare entry point with the receiver object ({@code null} for static methods). */
  public static String enterMethod(
      String className, String methodName, String methodDescriptor, Object instance) {
    try {
      return enterBare(className, methodName, methodDescriptor, instance);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - tracing may not fail the method
      return null;
    }
  }

  private static String enterBare(
      String className, String methodName, String methodDescriptor, Object instance) {
    SpanId spanId =
        context.enterMethod(
            new MethodSignature(
                AgentMethodMetadata.simpleName(className),
                methodName,
                List.of(),
                null,
                null,
                null,
                AgentMethodMetadata.packageName(className),
                returnTypeOf(methodDescriptor),
                instanceIdOf(instance)));
    SpanId prev = context.beginScope(spanId);
    return prev != null ? prev.value() : null;
  }

  /** Declared return type from a JVM method descriptor, or {@code null} without a descriptor. */
  private static String returnTypeOf(String methodDescriptor) {
    return methodDescriptor != null ? MethodDescriptorTypes.returnType(methodDescriptor) : null;
  }

  /** Declared parameter types from a JVM method descriptor, or {@code null} without one. */
  private static String[] parameterTypesOf(String methodDescriptor) {
    return methodDescriptor != null ? MethodDescriptorTypes.parameterTypes(methodDescriptor) : null;
  }

  public static String enterMethod(
      String className,
      String methodName,
      String[] paramNames,
      Object[] paramValues,
      boolean[] redacted,
      String narratedTemplate) {
    return enterMethod(
        className, methodName, paramNames, paramValues, redacted, narratedTemplate, null);
  }

  /**
   * Full entry point for instrumented methods. {@code methodDescriptor} is the JVM method
   * descriptor baked in at instrumentation time; declared parameter and return types are derived
   * from it here (not in bytecode) so instrumented output stays aligned with the proxy path, which
   * records {@link Class#getTypeName()} forms. {@code null} when the emitting bytecode predates it.
   */
  public static String enterMethod(
      String className,
      String methodName,
      String[] paramNames,
      Object[] paramValues,
      boolean[] redacted,
      String narratedTemplate,
      String methodDescriptor) {
    return enterMethod(
        className,
        methodName,
        paramNames,
        paramValues,
        redacted,
        narratedTemplate,
        methodDescriptor,
        null);
  }

  /**
   * Full entry point with the receiver object. {@code instance} is {@code this} of the instrumented
   * method ({@code null} for static methods or bytecode predating it); its identity hash is
   * recorded only when the context enables {@code narrativetrace.capture.instanceIds}.
   */
  public static String enterMethod(
      String className,
      String methodName,
      String[] paramNames,
      Object[] paramValues,
      boolean[] redacted,
      String narratedTemplate,
      String methodDescriptor,
      Object instance) {
    return enterMethod(
        className,
        methodName,
        paramNames,
        paramValues,
        redacted,
        narratedTemplate,
        methodDescriptor,
        instance,
        null,
        -1);
  }

  /**
   * Full entry point with baked source location: the instrumented method's own source file and
   * first line number ({@code -1} when the class carries no line info). Recorded only when the
   * context enables {@code narrativetrace.capture.sourceLocation}. Note the asymmetry with the
   * proxy path, which records the caller's frame instead (interfaces have no line info).
   */
  public static String enterMethod(
      String className,
      String methodName,
      String[] paramNames,
      Object[] paramValues,
      boolean[] redacted,
      String narratedTemplate,
      String methodDescriptor,
      Object instance,
      String sourceFile,
      int sourceLine) {
    try {
      return enterFull(
          className,
          methodName,
          paramNames,
          paramValues,
          redacted,
          narratedTemplate,
          methodDescriptor,
          instance,
          sourceFile,
          sourceLine);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - tracing may not fail the method
      return null;
    }
  }

  private static String enterFull(
      String className,
      String methodName,
      String[] paramNames,
      Object[] paramValues,
      boolean[] redacted,
      String narratedTemplate,
      String methodDescriptor,
      Object instance,
      String sourceFile,
      int sourceLine) {
    return enterRegistered(
        inlineMetadata(
            className,
            methodName,
            paramNames,
            redacted,
            narratedTemplate,
            methodDescriptor,
            sourceFile,
            sourceLine),
        paramValues,
        instance);
  }

  /**
   * The constants older instrumentation passes inline, read as the record the current call site
   * reaches by id. Building it costs one record per call — which is exactly what "these constants
   * are not registered anywhere" means, and why current instrumentation does not take this path.
   */
  private static AgentMethodMetadata inlineMetadata(
      String className,
      String methodName,
      String[] paramNames,
      boolean[] redacted,
      String narratedTemplate,
      String methodDescriptor,
      String sourceFile,
      int sourceLine) {
    return new AgentMethodMetadata(
        AgentMethodMetadata.simpleName(className),
        AgentMethodMetadata.packageName(className),
        methodName,
        paramNames,
        parameterTypesOf(methodDescriptor),
        redacted,
        returnTypeOf(methodDescriptor),
        narratedTemplate,
        sourceLocationOf(sourceFile, sourceLine),
        null);
  }

  /** The location the caller describes, or {@code null} when there is none to describe. */
  private static SourceLocation sourceLocationOf(String sourceFile, int sourceLine) {
    if (sourceFile == null && sourceLine < 0) {
      return null;
    }
    return new SourceLocation(sourceFile, sourceLine >= 0 ? sourceLine : null);
  }

  /** Identity hash of the receiver, or {@code null} when disabled or absent. */
  private static String instanceIdOf(Object instance) {
    if (instance == null || !context.capturesInstanceIds()) {
      return null;
    }
    return Integer.toHexString(System.identityHashCode(instance));
  }

  public static void endScope(String previousScope) {
    try {
      context.endScope(previousScope != null ? SpanId.of(previousScope) : null);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - tracing may not fail the method
      // Observability failure must never become an application failure
    }
  }

  private static List<ParameterCapture> buildCaptures(
      String[] paramNames,
      String[] paramTypes,
      Object[] paramValues,
      boolean[] redacted,
      boolean renderValues) {
    var captures = new ArrayList<ParameterCapture>(paramNames.length);
    for (int i = 0; i < paramNames.length; i++) {
      var type = paramTypes != null ? paramTypes[i] : null;
      if (redacted[i]) {
        captures.add(new ParameterCapture(paramNames[i], "[REDACTED]", true, null, type));
      } else if (!renderValues) {
        captures.add(new ParameterCapture(paramNames[i], "", false, null, type));
      } else {
        var capture = VALUE_RENDERER.renderForCapture(paramValues[i]);
        captures.add(
            new ParameterCapture(
                paramNames[i],
                capture.rendered(),
                capture.shapeRedacted(),
                capture.structured(),
                type));
      }
    }
    return captures;
  }

  private static String resolveNarration(
      String template, String[] paramNames, Object[] paramValues, boolean[] redacted) {
    if (template == null) {
      return null;
    }
    var valueMap = new java.util.LinkedHashMap<String, Object>();
    for (int i = 0; i < paramNames.length; i++) {
      valueMap.put(paramNames[i], redacted[i] ? "[REDACTED]" : paramValues[i]);
    }
    return ai.narrativetrace.core.template.TemplateParser.resolve(template, valueMap);
  }

  /** Exit hook for void methods: completion without a rendered value ({@code null} = void). */
  public static void exitMethodVoid() {
    try {
      if (!context.isActive()) return;
      context.exitMethodWithReturn(null, null, null);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - tracing may not fail the method
      // Observability failure must never become an application failure
    }
  }

  public static void exitMethodWithReturn(Object returnValue) {
    try {
      if (!context.isActive()) return;
      var rendered = VALUE_RENDERER.render(returnValue);
      var structured = VALUE_RENDERER.renderStructured(returnValue);
      context.exitMethodWithReturn(rendered, structured, null);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - tracing may not fail the method
      // The business return value must reach the caller whatever rendering did
    }
  }

  public static void exitMethodWithException(Throwable exception) {
    exitMethodWithException(exception, null);
  }

  public static void exitMethodWithException(Throwable exception, String errorContext) {
    try {
      context.exitMethodWithException(exception, errorContext);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - tracing may not replace the failure
      // The business exception is already on its way out; recording it is best-effort
    }
  }

  /**
   * The {@code @OnError} narration for an escaping exception, resolved from the call site's
   * registered templates. The counterpart of {@link #enterMethod(int, Object[], Object)}: the
   * templates and the parameter names are constants of the method, so only the values travel.
   */
  public static String resolveErrorContext(
      Throwable exception, int metadataId, Object[] paramValues) {
    try {
      var metadata = METHODS.get(metadataId);
      if (metadata == null || metadata.onErrors() == null) {
        return null;
      }
      var bestTemplate = findBestTemplate(exception, metadata.onErrors());
      return bestTemplate == null
          ? null
          : resolveNarration(
              bestTemplate, metadata.parameterNames(), paramValues, metadata.redacted());
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - tracing may not replace the failure
      return null;
    }
  }

  public static String resolveErrorContext(
      Throwable exception,
      String[] templates,
      String[] exceptionDescriptors,
      String[] paramNames,
      Object[] paramValues,
      boolean[] redacted) {
    try {
      var bestTemplate = findBestTemplate(exception, entries(templates, exceptionDescriptors));
      return bestTemplate == null
          ? null
          : resolveNarration(bestTemplate, paramNames, paramValues, redacted);
    } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - tracing may not replace the failure
      return null;
    }
  }

  /** The parallel arrays older instrumentation emits, read as the entries they describe. */
  private static MethodMetadata.OnErrorEntry[] entries(
      String[] templates, String[] exceptionDescriptors) {
    var onErrors = new MethodMetadata.OnErrorEntry[templates.length];
    for (int i = 0; i < templates.length; i++) {
      onErrors[i] = new MethodMetadata.OnErrorEntry(templates[i], exceptionDescriptors[i]);
    }
    return onErrors;
  }

  /** The most specific {@code @OnError} entry the exception is an instance of. */
  private static String findBestTemplate(
      Throwable exception, MethodMetadata.OnErrorEntry[] onErrors) {
    String bestTemplate = null;
    Class<?> bestExceptionType = null;
    for (var entry : onErrors) {
      var exceptionType = descriptorToClass(entry.exceptionDescriptor());
      if (exceptionType != null && exceptionType.isInstance(exception)) {
        if (bestExceptionType == null || bestExceptionType.isAssignableFrom(exceptionType)) {
          bestTemplate = entry.template();
          bestExceptionType = exceptionType;
        }
      }
    }
    return bestTemplate;
  }

  /**
   * Resolves an exception type named in a method descriptor, for {@code @OnError} template choice.
   *
   * <p><b>@edgeCase</b> Catches {@link Throwable}, not just {@link ClassNotFoundException}. This
   * runs on the application thread while an exception is already propagating out of an instrumented
   * method, and it loads (and therefore <em>initialises</em>) an arbitrary class named in bytecode
   * the agent did not write. A static initialiser that throws, a class compiled for a newer JVM, a
   * missing transitive supertype — each raises an {@link Error}, and each would replace the
   * application's own exception with a trace-machinery one. "This type cannot be resolved, so no
   * template matches it" is the correct answer to all of them.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // an arbitrary class may fail to initialise
  private static Class<?> descriptorToClass(String descriptor) {
    // Convert "Ljava/lang/IllegalArgumentException;" to "java.lang.IllegalArgumentException"
    if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
      var className = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
      try {
        return Class.forName(className);
      } catch (Throwable unresolvable) { // NOPMD
        return null;
      }
    }
    return null;
  }
}
