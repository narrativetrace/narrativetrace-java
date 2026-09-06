/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.annotation.OnError;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.ValueRenderer;
import ai.narrativetrace.core.template.TemplateParser;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for JDK dynamic proxies that capture traced interface invocations.
 *
 * <p>INTENT: Use this when you control object creation and the target exposes one or more
 * interfaces. It gives you tracing without bytecode weaving or framework-specific integration.
 *
 * <p>The proxy intercepts every interface call, resolves annotation templates against raw
 * arguments, records parameter and return values through the supplied {@link NarrativeContext}, and
 * delegates to the real implementation.
 *
 * <pre>{@code
 * var context = new ThreadLocalNarrativeContext();
 * OrderService traced = NarrativeTraceProxy.trace(realService, OrderService.class, context);
 * traced.placeOrder("item-1", 3);  // automatically traced
 * TraceTree tree = context.captureTrace();
 * }</pre>
 *
 * <p><b>@preferOver</b> The agent when you only need interface-based tracing and can wrap the
 * target directly.
 *
 * <p><b>@llmNote</b> Non-public interface methods are invoked with {@link
 * Method#setAccessible(boolean)} enabled, checked exceptions are unwrapped from {@link
 * InvocationTargetException}, and {@link CompletableFuture} results use deferred-exit semantics:
 * the frame is detached and the span is completed when the future resolves. For plain {@link
 * CompletableFuture} return types, the proxy returns a wrapper stage that propagates cancellation
 * to the underlying future and guarantees that joining the returned stage implies the trace has
 * been recorded. For {@link CompletableFuture} subclass return types, the original instance is
 * returned unchanged (so the declared return type is satisfied); the trace callback is a
 * side-effect only.
 *
 * <p>Parameter values are eagerly serialized via the shared {@code VALUE_RENDERER} at capture time.
 * {@code @Narrated} and {@code @OnError} templates are resolved against raw objects before
 * serialization. Parameters annotated with {@link NotTraced @NotTraced} are recorded as redacted.
 *
 * <p>Two factory methods are provided: {@link #trace(Object, Class, NarrativeContext)} for a single
 * interface, and {@link #trace(Object, Class[], NarrativeContext)} for targets that implement
 * multiple interfaces.
 *
 * <p><b>@sideEffects</b> Tracing never decides what a call returns or throws. Metadata lookup,
 * signature building, parameter rendering, trace entry, return rendering and both exits are guarded
 * against {@link Throwable}; the target's own result or exception always wins. If entry fails the
 * target is invoked raw and no exit is recorded, so a half-open frame is never closed with a
 * fabricated outcome.
 *
 * <p><b>@edgeCase</b> One boundary cannot be made total, and deliberately is not: if {@link
 * NarrativeContext#runScoped} itself throws, the target may or may not have run, so re-invoking it
 * could duplicate side effects and returning a value would invent one. The span is closed
 * best-effort and the throwable propagates. The target's own throwable is carried out of {@code
 * runScoped} in a private marker so it is never confused with that case.
 *
 * <p>That boundary is a statement about *whose* code it is, not a gap in the guarantee. {@code
 * runScoped} is the only seam on this path that wraps the business call instead of being invoked
 * beside it, so an implementation of it runs *inside* the call — host code, like the target itself.
 * The guarantee above therefore reads: tracing never poisons host execution, where "tracing" is
 * every hook this library invokes plus the contexts it ships ({@code ThreadLocalNarrativeContext},
 * {@code NoopNarrativeContext}), whose {@code runScoped} restores a field in its {@code finally}
 * and cannot throw. A third-party {@code runScoped} that throws — even from a {@code finally},
 * after the target returned successfully — decides the call's outcome, and an implementer is told
 * so on {@link NarrativeContext#runScoped}. Decided 2026-09-01, pinned by {@code
 * NoPoisonContractTest} in {@code ai.narrativetrace.proxy.totality}.
 *
 * <p><b>@edgeCase</b> {@code equals}, {@code hashCode} and {@code toString} are answered before
 * tracing is considered and never appear in a trace. {@code equals} is proxy identity and {@code
 * hashCode} is the proxy's identity hash, which is the only pair that can be consistent with each
 * other: delegating {@code equals} to the target made {@code proxy.equals(proxy)} false, and a
 * non-reflexive object breaks every set, map and cache it is put in. The cost is that a proxy no
 * longer hashes to the same bucket as its target — that is the trade every proxy library makes.
 * {@code toString} still delegates, guarded, and falls back to a stable description.
 *
 * <p>Requires the {@code -parameters} compiler flag for meaningful parameter names.
 *
 * @see ai.narrativetrace.core.context.NarrativeContext
 * @see ai.narrativetrace.api.annotation.Narrated
 * @see ai.narrativetrace.api.annotation.NotTraced
 */
public final class NarrativeTraceProxy {

  private static final ValueRenderer VALUE_RENDERER = new ValueRenderer();
  private static final ConcurrentHashMap<Method, ProxyMethodMetadata> METHOD_CACHE =
      new ConcurrentHashMap<>();

  private NarrativeTraceProxy() {}

  /**
   * Everything about a method that never changes between calls, including the opened {@link Method}
   * instance the proxy invokes through.
   *
   * @param accessible the method with {@code setAccessible(true)} already applied — see {@link
   *     #computeMetadata}
   */
  record ProxyMethodMetadata(
      Method accessible,
      String[] paramNames,
      String[] paramTypes,
      String returnType,
      boolean[] redacted,
      String narratedTemplate,
      OnError[] onErrors) {}

  /**
   * Opens the method for reflective invocation and reads everything about it that never changes.
   *
   * <p><b>@llmNote</b> {@code setAccessible} used to run on every invocation, inactive path
   * included, and {@code Method.checkCanSetAccessible} is not free — JDK 9+ re-runs the caller
   * check every time. Accessibility is a property of the method, not of the call, so it is computed
   * here with the parameter names and the annotations. Two consequences the handler depends on: the
   * cache lookup happens BEFORE the {@code isActive()} gate (a non-public interface must work with
   * tracing off), and every invocation goes through {@link ProxyMethodMetadata#accessible} rather
   * than through the {@code Method} the handler was passed.
   *
   * <p><b>@edgeCase</b> That second point is not a detail. {@code setAccessible} sets a flag on one
   * {@code Method} <em>instance</em>, while {@code METHOD_CACHE} is keyed by {@code equals} — and
   * two {@code Method} objects for the same method are equal. A second proxy over the same
   * interface therefore hits the cache, never runs {@code setAccessible} on its own instance, and
   * invoking through it throws {@code IllegalAccessException} for a non-public interface. Invoking
   * through the cached, opened instance is what makes the hoist sound; {@code
   * NarrativeTraceGraphCrossPackageTest} is where it is caught.
   */
  static ProxyMethodMetadata computeMetadata(Method method) {
    method.setAccessible(true); // NOPMD - non-public interfaces are invoked reflectively
    var parameters = method.getParameters();
    var paramNames = new String[parameters.length];
    var paramTypes = new String[parameters.length];
    var redacted = new boolean[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      paramNames[i] = parameters[i].getName();
      paramTypes[i] = parameters[i].getType().getTypeName();
      redacted[i] = parameters[i].isAnnotationPresent(NotTraced.class);
    }
    var narrated = method.getAnnotation(Narrated.class);
    var narratedTemplate = narrated != null ? narrated.value() : null;
    var onErrors = method.getAnnotationsByType(OnError.class);
    return new ProxyMethodMetadata(
        method,
        paramNames,
        paramTypes,
        method.getReturnType().getTypeName(),
        redacted,
        narratedTemplate,
        onErrors);
  }

  /**
   * Wraps the target in a tracing proxy for one interface.
   *
   * @param target Concrete implementation that should receive the real method invocation.
   * @param interfaceType Interface the proxy should expose. This must be the interface callers use.
   * @param context Shared trace context that will receive enter and exit events.
   * @param <T> the interface type
   * @return Proxy implementing {@code interfaceType} and delegating to {@code target}.
   */
  @SuppressWarnings("unchecked")
  public static <T> T trace(T target, Class<T> interfaceType, NarrativeContext context) {
    return (T)
        Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[] {interfaceType},
            createHandler(target, new FixedIdentity(interfaceType), context));
  }

  /**
   * Wraps the target in a tracing proxy for multiple interfaces.
   *
   * @param target Concrete implementation that should receive the real method invocation.
   * @param interfaces Interfaces the proxy should expose. At least one is required.
   * @param context Shared trace context that will receive enter and exit events.
   * @return Proxy implementing all requested interfaces.
   * @throws IllegalArgumentException if {@code interfaces} is empty
   */
  public static Object trace(Object target, Class<?>[] interfaces, NarrativeContext context) {
    if (interfaces.length == 0) {
      throw new IllegalArgumentException("At least one interface is required");
    }
    return Proxy.newProxyInstance(
        interfaces[0].getClassLoader(), interfaces, createHandler(target, null, context));
  }

  /**
   * Declared identity of the wrapped interface for the single-interface entry point: simple name
   * for readable narration, package name for downstream bounded-context resolution. The
   * multi-interface entry point passes {@code null} and identity falls back to each method's
   * declaring class.
   */
  private record FixedIdentity(String className, String packageName) {
    FixedIdentity(Class<?> interfaceType) {
      this(interfaceType.getSimpleName(), interfaceType.getPackageName());
    }
  }

  private static InvocationHandler createHandler(
      Object target, FixedIdentity fixedIdentity, NarrativeContext context) {
    return (proxy, method, args) -> {
      if (method.getDeclaringClass() == Object.class) {
        return objectMethod(proxy, target, method, args);
      }
      var meta = metadataFor(method);
      var invocable = meta != null ? meta.accessible : method;
      if (meta == null || !tracingActive(context)) {
        return invokeRaw(invocable, target, args);
      }
      var safeArgs = args != null ? args : new Object[0];
      var signature = signatureFor(context, target, method, meta, fixedIdentity, safeArgs);
      return signature == null
          ? invokeRaw(invocable, target, args)
          : invokeTraced(context, meta, target, method, args, safeArgs, signature);
    };
  }

  /**
   * Answers the three {@link Object} methods a JDK proxy routes here.
   *
   * <p><b>@llmNote</b> Only {@code equals}, {@code hashCode} and {@code toString} can arrive: the
   * rest of {@code Object} is either final or not public, so {@link Proxy} never dispatches it. The
   * final {@code return} is therefore {@code toString}'s, not a fallback.
   */
  @SuppressWarnings("PMD.CompareObjectsWithEquals") // identity IS the semantics being defined
  private static Object objectMethod(Object proxy, Object target, Method method, Object[] args) {
    if ("equals".equals(method.getName())) {
      return args != null && args.length == 1 && proxy == args[0]; // NOPMD
    }
    if ("hashCode".equals(method.getName())) {
      return System.identityHashCode(proxy);
    }
    return describe(target);
  }

  /**
   * The target's own description, or a stable one naming it when the target will not produce one.
   *
   * <p><b>@edgeCase</b> Delegating keeps a proxy looking like what it wraps, which is what every
   * log line printing a wrapped bean expects. It is guarded because {@code toString()} is user code
   * and this method is on the path of every {@code "" + bean} in the application.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // a rogue toString() may throw Error
  private static String describe(Object target) {
    try {
      var text = target.toString();
      if (text != null) {
        return text;
      }
    } catch (Throwable t) { // NOPMD
      // fall through to the stable description
    }
    return "NarrativeTraceProxy@"
        + Integer.toHexString(System.identityHashCode(target))
        + "["
        + target.getClass().getName()
        + "]";
  }

  /**
   * The cached metadata for this method, or {@code null} when it could not be computed.
   *
   * <p><b>@edgeCase</b> {@code null} means the proxy invokes through the {@link Method} it was
   * handed instead of an opened copy, and traces nothing. That is the right degradation: a method
   * the runtime refuses to open ({@code InaccessibleObjectException} under a strict module
   * configuration) is one tracing cannot describe, but the call itself may still succeed.
   *
   * <p>Package-private for the same reason {@link #computeMetadata} is: a JDK proxy only ever
   * dispatches interface methods, so the refusal this guards cannot be staged through the public
   * factory methods.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // tracing setup may never fail the call
  static ProxyMethodMetadata metadataFor(Method method) {
    try {
      return METHOD_CACHE.computeIfAbsent(method, NarrativeTraceProxy::computeMetadata);
    } catch (Throwable t) { // NOPMD
      return null;
    }
  }

  /** Whether tracing is on. A context that will not answer is treated as off. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // tracing setup may never fail the call
  private static boolean tracingActive(NarrativeContext context) {
    try {
      return context.isActive();
    } catch (Throwable t) { // NOPMD
      return false;
    }
  }

  /**
   * The signature to record, or {@code null} when building it failed — parameter rendering,
   * template resolution and the context's own capture questions all run here, and any of them can
   * be user code. A call that cannot be described is still a call that must happen.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // capture may never fail the call
  private static MethodSignature signatureFor(
      NarrativeContext context,
      Object target,
      Method method,
      ProxyMethodMetadata meta,
      FixedIdentity fixedIdentity,
      Object[] safeArgs) {
    try {
      var instanceId =
          context.capturesInstanceIds()
              ? Integer.toHexString(System.identityHashCode(target))
              : null;
      var source = context.capturesSourceLocation() ? CallSiteLocator.callerLocation() : null;
      return buildSignature(
          method,
          meta,
          safeArgs,
          fixedIdentity,
          context.capturesParameterValues(),
          instanceId,
          source);
    } catch (Throwable t) { // NOPMD
      return null;
    }
  }

  /**
   * Runs the target with tracing around it, letting the target decide the outcome.
   *
   * <p><b>@edgeCase</b> Entry failing is not the same as the target failing: with no span open
   * there is nothing to close, so the target is invoked raw and no exit is recorded.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // tracing may never decide the outcome
  private static Object invokeTraced(
      NarrativeContext context,
      ProxyMethodMetadata meta,
      Object target,
      Method method,
      Object[] args,
      Object[] safeArgs,
      MethodSignature signature)
      throws Throwable {
    SpanId spanId;
    try {
      spanId = context.enterMethod(signature);
    } catch (Throwable t) { // NOPMD
      return invokeRaw(meta.accessible, target, args);
    }
    try {
      return recordReturn(
          context, method, invokeScoped(context, spanId, meta.accessible, target, args), spanId);
    } catch (Throwable t) { // NOPMD
      throw exitWithException(context, meta, safeArgs, unwrapBusiness(t), spanId);
    }
  }

  /** The target's own throwable, or the scoping failure itself when the target never spoke. */
  private static Throwable unwrapBusiness(Throwable thrown) {
    return thrown instanceof BusinessFailure failure ? failure.getCause() : thrown;
  }

  /**
   * Records the return, then hands the caller its own value back whatever recording did.
   *
   * <p><b>@edgeCase</b> A void method returns {@code null} either way; anything else returns the
   * value the target produced, including a {@link CompletableFuture} whose deferred-exit wiring
   * failed — the original future is still the caller's.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // recording may never replace the result
  private static Object recordReturn(
      NarrativeContext context, Method method, Object result, SpanId spanId) {
    var isVoid = method.getReturnType() == void.class;
    try {
      if (isVoid) {
        context.exitMethodWithReturn(null, null, spanId);
        return null;
      }
      return handleResult(context, result, spanId);
    } catch (Throwable t) { // NOPMD
      return isVoid ? null : result;
    }
  }

  private static MethodSignature buildSignature(
      Method method,
      ProxyMethodMetadata meta,
      Object[] safeArgs,
      FixedIdentity fixedIdentity,
      boolean renderValues,
      String instanceId,
      ai.narrativetrace.api.event.SourceLocation source) {
    var narration = resolveNarration(meta, safeArgs);
    var captures =
        ParameterNameResolver.resolve(
            meta.paramNames,
            meta.paramTypes,
            meta.redacted,
            safeArgs,
            VALUE_RENDERER,
            renderValues);
    var className =
        fixedIdentity != null
            ? fixedIdentity.className()
            : method.getDeclaringClass().getSimpleName();
    var packageName =
        fixedIdentity != null
            ? fixedIdentity.packageName()
            : method.getDeclaringClass().getPackageName();
    return new MethodSignature(
        className,
        method.getName(),
        captures,
        narration,
        null,
        meta.narratedTemplate,
        packageName,
        meta.returnType,
        instanceId,
        source);
  }

  /**
   * Carries the target's own throwable out of {@link NarrativeContext#runScoped}, so a failure the
   * business method raised is never confused with one the scoping raised.
   *
   * <p><b>@llmNote</b> Allocated only when the target throws — the call has already allocated an
   * exception by then — and it carries neither message, stack trace nor suppression, so it costs
   * one header and one reference.
   */
  private static final class BusinessFailure extends RuntimeException {
    BusinessFailure(Throwable cause) {
      super(null, cause, false, false);
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable") // the target's Errors are the target's outcome
  private static Object invokeScoped(
      NarrativeContext context, SpanId spanId, Method method, Object target, Object[] args) {
    return context.runScoped(
        spanId,
        () -> {
          try {
            return method.invoke(target, args);
          } catch (InvocationTargetException ite) {
            throw new BusinessFailure(ite.getCause() != null ? ite.getCause() : ite);
          } catch (Throwable ex) { // NOPMD
            throw new BusinessFailure(ex);
          }
        });
  }

  private static Object handleResult(NarrativeContext context, Object result, SpanId spanId) {
    if (result instanceof CompletableFuture<?> future) {
      if (future.getClass() == CompletableFuture.class) {
        return exitDeferredWrapped(context, future, spanId);
      }
      exitDeferredSideEffect(context, future, spanId);
      return result;
    }
    var rendered = VALUE_RENDERER.render(result);
    var structured = VALUE_RENDERER.renderStructured(result);
    context.exitMethodWithReturn(rendered, structured, spanId);
    return result;
  }

  /**
   * Deferred-exit for plain {@link CompletableFuture} return types. Returns a wrapper stage that:
   * completes only after the trace callback has run (guaranteeing ordering for callers that join
   * the returned future), and propagates cancellation back to the underlying future.
   *
   * <p><b>@edgeCase</b> Wrapper completion is guaranteed via {@code finally} even when exit
   * recording throws — a trace-recording failure must never leave the application's {@code join()}
   * waiting forever, and an exceptional business outcome always reaches the caller unchanged.
   */
  @SuppressWarnings("unchecked")
  private static <T> CompletableFuture<T> exitDeferredWrapped(
      NarrativeContext context, CompletableFuture<?> future, SpanId spanId) {
    detachFrameSafely(context, spanId);
    var wrapper = new CompletableFuture<T>();
    future.whenComplete(
        (val, err) -> {
          try {
            recordDeferredExit(context, val, err, spanId);
          } finally {
            // The caller's join()/get() must never hang because trace recording failed
            if (err != null) {
              wrapper.completeExceptionally(err);
            } else {
              wrapper.complete((T) val);
            }
          }
        });
    wrapper.whenComplete(
        (v, e) -> {
          if (wrapper.isCancelled()) future.cancel(true);
        });
    return wrapper;
  }

  /**
   * Records one deferred exit, best-effort.
   *
   * <p>INTENT: The single body both deferred paths use, so the wrapped and side-effect forms cannot
   * drift on what a completion records. It runs on whatever thread completed the future — often an
   * application pool thread with no idea it is inside tracing — so it may not throw there either.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // recording may never disturb the completion
  private static void recordDeferredExit(
      NarrativeContext context, Object value, Throwable failure, SpanId spanId) {
    try {
      if (failure != null) {
        context.exitMethodWithException(unwrapCompletion(failure), null, spanId);
      } else {
        context.exitMethodWithReturn(
            VALUE_RENDERER.render(value), VALUE_RENDERER.renderStructured(value), spanId);
      }
    } catch (Throwable t) { // NOPMD
      // The future's own outcome is the caller's; recording it is best-effort
    }
  }

  /** The business failure inside a {@link CompletionException} wrapper, or the failure itself. */
  private static Throwable unwrapCompletion(Throwable failure) {
    if (failure instanceof CompletionException ce && ce.getCause() != null) {
      return ce.getCause();
    }
    return failure;
  }

  /**
   * Detaches the frame so the deferred exit can close it later, best-effort.
   *
   * <p><b>@edgeCase</b> A context that refuses to detach leaves the frame where it is; the callback
   * still runs and the caller still gets its future. Failing the call because bookkeeping failed is
   * the outcome this whole boundary exists to prevent.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // bookkeeping may never fail the call
  private static void detachFrameSafely(NarrativeContext context, SpanId spanId) {
    try {
      context.detachFrame(spanId);
    } catch (Throwable t) { // NOPMD
      // Best-effort — the deferred exit still runs, and the caller still gets its future
    }
  }

  /**
   * Deferred-exit for {@link CompletableFuture} subclass return types. The original instance must
   * be returned unchanged so the caller's declared return type is satisfied. The trace callback is
   * attached as a side-effect only; callers joining the returned future do not get a happens-before
   * guarantee on the trace being recorded.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // a subclass may override whenComplete to throw
  private static <T> void exitDeferredSideEffect(
      NarrativeContext context, CompletableFuture<T> future, SpanId spanId) {
    detachFrameSafely(context, spanId);
    try {
      future.whenComplete((val, err) -> recordDeferredExit(context, val, err, spanId));
    } catch (Throwable t) { // NOPMD
      // A future that refuses a callback is simply not traced on completion — it is still returned
    }
  }

  /**
   * Records the failure best-effort and returns the throwable the caller must see — always the one
   * that arrived, never one the recording produced.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // recording may never replace the failure
  private static Throwable exitWithException(
      NarrativeContext context,
      ProxyMethodMetadata meta,
      Object[] safeArgs,
      Throwable cause,
      SpanId spanId) {
    try {
      context.exitMethodWithException(cause, resolveErrorContext(meta, safeArgs, cause), spanId);
    } catch (Throwable t) { // NOPMD
      // The business throwable is already on its way out; recording it is best-effort
    }
    return cause;
  }

  private static Object invokeRaw(Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException ite) {
      throw ite.getCause();
    }
  }

  private static String resolveNarration(ProxyMethodMetadata meta, Object[] args) {
    if (meta.narratedTemplate == null) {
      return null;
    }
    return TemplateParser.resolve(
        meta.narratedTemplate, buildValueMap(meta.paramNames, meta.redacted, args));
  }

  private static String resolveErrorContext(
      ProxyMethodMetadata meta, Object[] args, Throwable exception) {
    var match = findMostSpecificOnError(meta.onErrors, exception);
    if (match == null) {
      return null;
    }
    return TemplateParser.resolve(
        match.value(), buildValueMap(meta.paramNames, meta.redacted, args));
  }

  private static OnError findMostSpecificOnError(OnError[] onErrors, Throwable exception) {
    OnError best = null;
    for (var annotation : onErrors) {
      if (annotation.exception().isInstance(exception)
          && (best == null || best.exception().isAssignableFrom(annotation.exception()))) {
        best = annotation;
      }
    }
    return best;
  }

  private static Map<String, Object> buildValueMap(
      String[] paramNames, boolean[] redacted, Object[] args) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < paramNames.length; i++) {
      map.put(paramNames[i], redacted[i] ? "[REDACTED]" : args[i]);
    }
    return map;
  }
}
