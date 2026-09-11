/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.function.Supplier;

/**
 * Central capture API used by instrumentation to record traced method lifecycles.
 *
 * <p>INTENT: Proxies, agents, and integration layers call this interface while business code runs.
 * Application code should rarely invoke it directly except when bridging execution across threads
 * or setting request metadata.
 *
 * <p>A context accepts enter and exit notifications, tracks parent-child span relationships, and
 * exposes the accumulated result as an immutable {@link TraceTree}. Argument and return values are
 * already rendered to strings before they reach this layer.
 *
 * <p><b>@pattern</b> Capture facade backed by append-only {@code TraceEvent}s and later tree
 * assembly.
 *
 * <p><b>@llmNote</b> Treat every successful {@link #enterMethod(MethodSignature)} call as requiring
 * exactly one matching exit call. If a frame is detached for asynchronous completion, finish it
 * with the span-id overloads. All span id parameters and return values use the typed {@link SpanId}
 * record instead of raw strings, preventing accidental swaps with trace ids or other identifiers.
 *
 * <p><b>@llmNote</b> The context is the ownership boundary for trace capture. Do not reach into
 * renderer-specific or exporter-specific state to record events; route everything through this API.
 *
 * <p>Example:
 *
 * <pre>{@code
 * var renderer = new ValueRenderer();
 * SpanId spanId = context.enterMethod(signature);
 * try {
 *   var value = target.call();
 *   context.exitMethodWithReturn(renderer.render(value), spanId);
 *   return value;
 * } catch (Throwable t) {
 *   context.exitMethodWithException(t, null, spanId);
 *   throw t;
 * }
 * }</pre>
 *
 * @see ThreadLocalNarrativeContext
 * @see ContextSnapshot
 */
public interface NarrativeContext {

  /**
   * Returns whether this context is actively recording traces.
   *
   * <p>When {@code false}, proxies and agents can skip capture entirely for zero-overhead
   * passthrough. The default implementation returns {@code true}.
   *
   * @return {@code true} if tracing is active
   */
  default boolean isActive() {
    return true;
  }

  /**
   * Returns whether captured parameter values are retained at the current level.
   *
   * <p>When {@code false}, proxies and agents can skip the (potentially expensive) value rendering
   * for parameters entirely, since the context would discard the rendered values anyway. This is a
   * pure performance hint: capture remains correct if a caller ignores it and renders regardless.
   * The default implementation returns {@code true}.
   *
   * @return {@code true} if parameter values will be retained, {@code false} if they will be
   *     suppressed
   */
  default boolean capturesParameterValues() {
    return true;
  }

  /**
   * Returns whether capture sites should record per-object instance ids ({@code
   * narrativetrace.capture.instanceIds}, default off).
   *
   * <p>The default implementation returns {@code false}.
   *
   * @return {@code true} if instance identity should be captured
   */
  default boolean capturesInstanceIds() {
    return false;
  }

  /**
   * Returns whether capture sites should record source location ({@code
   * narrativetrace.capture.sourceLocation}, default off — the proxy pays a stack walk per call, the
   * agent reads line numbers for free at instrumentation time).
   *
   * <p>The default implementation returns {@code false}.
   *
   * @return {@code true} if source location should be captured
   */
  default boolean capturesSourceLocation() {
    return false;
  }

  /**
   * Records entry into a method.
   *
   * <p>Pushes a new frame onto the call stack. The proxy or agent must call a corresponding exit
   * method ({@link #exitMethodWithReturn} or {@link #exitMethodWithException}) for every {@code
   * enterMethod} call.
   *
   * @param signature the method being entered, including class name, method name, captured
   *     parameters, and any annotation-based narration
   * @return the {@link SpanId} identifying this frame, or {@code null} if tracing is disabled
   */
  SpanId enterMethod(MethodSignature signature);

  /**
   * Detaches a frame from the active call stack without completing it. The frame stays alive for
   * later completion via spanId-based exit. Used by the proxy for {@code CompletableFuture}
   * returns.
   *
   * @param spanId the frame spanId returned by {@link #enterMethod}
   */
  void detachFrame(SpanId spanId);

  /**
   * Records a normal method return.
   *
   * @param renderedReturnValue the return value pre-rendered to a String via {@code ValueRenderer},
   *     or {@code null} for void methods
   */
  void exitMethodWithReturn(String renderedReturnValue);

  /**
   * Records a normal method return for a specific frame identified by spanId.
   *
   * @param renderedReturnValue the return value pre-rendered to a String via {@code ValueRenderer},
   *     or {@code null} for void methods
   * @param spanId the frame {@link SpanId} returned by {@link #enterMethod}, or {@code null} for
   *     LIFO
   */
  void exitMethodWithReturn(String renderedReturnValue, SpanId spanId);

  /**
   * Records a normal method return with an optional structured value for typed OTel export.
   *
   * @param renderedReturnValue the return value pre-rendered to a String
   * @param structuredReturnValue the structured value for typed attributes, or {@code null}
   * @param spanId the frame {@link SpanId}, or {@code null} for LIFO
   */
  default void exitMethodWithReturn(
      String renderedReturnValue, RenderedValue structuredReturnValue, SpanId spanId) {
    exitMethodWithReturn(renderedReturnValue, spanId);
  }

  /**
   * Records a method exit via exception.
   *
   * @param exception the thrown exception
   * @param errorContext the resolved {@code @OnError} template, or {@code null} if no matching
   *     template was found
   */
  void exitMethodWithException(Throwable exception, String errorContext);

  /**
   * Records a method exit via exception for a specific frame identified by spanId.
   *
   * @param exception the thrown exception
   * @param errorContext the resolved {@code @OnError} template, or {@code null} if no matching
   *     template was found
   * @param spanId the frame {@link SpanId} returned by {@link #enterMethod}, or {@code null} for
   *     LIFO
   */
  void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId);

  /**
   * Returns the immutable trace tree accumulated since the last {@link #reset()}.
   *
   * <p><b>@llmNote</b> Thread-scoped by design: implementations backed by per-thread state (notably
   * {@link ThreadLocalNarrativeContext}) return only the <em>calling</em> thread's trace, so
   * concurrent requests sharing one context instance stay isolated. Calling {@code captureTrace()}
   * from a thread other than the one that recorded the events yields an empty tree — this is not
   * data loss. Cross-thread flows must either capture on the recording thread itself or graft the
   * forked work into the observing thread's trace via {@link ContextSnapshot#wrap}, as {@link
   * ForkGroup} and {@link FireAndForgetGroup} do.
   *
   * @return the captured trace tree, scoped to the calling thread
   */
  TraceTree captureTrace();

  /**
   * Returns the trace tree visible from the current execution scope.
   *
   * <p>INTENT: concurrency helpers use this to collect the work performed inside an activated
   * snapshot before replaying it elsewhere. Implementations may return the same result as {@link
   * #captureTrace()} when they do not distinguish local from shared state.
   *
   * <p>Like {@link #captureTrace()}, the result is scoped to the calling thread's execution scope —
   * see the thread-scoping note there.
   *
   * @return the trace tree for the current scope
   */
  default TraceTree captureLocalTrace() {
    return captureTrace();
  }

  /**
   * Forgets everything the current execution scope recorded, after the caller has taken its copy.
   *
   * <p>INTENT: The other half of {@link #captureLocalTrace()}, for the concurrency helpers that own
   * their children's presentation. {@link ForkGroup} re-emits the collected roots under the fork's
   * parent span and {@link FireAndForgetGroup} hands them out through {@code childRoots()}; in both
   * cases the copied {@link ai.narrativetrace.api.event.TraceNode}s are the surviving record, and
   * the raw worker spans behind them are duplicates that no request will ever clear — a worker
   * scope activated without adoption belongs to no thread's reportable set.
   *
   * <p><b>@sideEffects</b> Scoped exactly like {@link #captureLocalTrace()}: it discards the
   * calling thread's scope and nothing else, so a concurrent request sharing this context is
   * untouched. It is not loss — the narrative keeps the copy — so nothing is counted. That premise
   * holds only when the caller really has taken its copy; the helpers therefore go through {@link
   * #collectLocalTrace()}, whose implementation can count what the copy could not contain.
   *
   * <p>The default is a no-op, for implementations that retain nothing to discard.
   */
  default void discardLocalTrace() {
    // nothing retained, nothing to discard
  }

  /**
   * Captures the current execution scope's trace and then forgets the scope — collection, as one
   * operation.
   *
   * <p>INTENT: The concurrency helpers end every worker scope with exactly this
   * capture-then-discard pair, and keeping the pair together is a contract, not a convenience: the
   * capture is the last one the scope will ever get, so an implementation can treat it as a barrier
   * for the scope's own events and count as loss whatever it still could not see — see {@link
   * ThreadLocalNarrativeContext#collectLocalTrace()}. A call site that splits the pair forfeits
   * that accounting.
   *
   * @return the trace tree the scope produced; the caller's copy is the surviving record
   */
  default TraceTree collectLocalTrace() {
    var tree = captureLocalTrace();
    discardLocalTrace();
    return tree;
  }

  /**
   * Clears all accumulated trace state. Call between tests or requests.
   *
   * <p>Thread-scoped implementations clear only the calling thread's state, and a thread that holds
   * none — one that never entered a traced method — is left untouched rather than given empty state
   * to remove.
   */
  void reset();

  /**
   * Creates a snapshot for cross-thread trace propagation.
   *
   * <p>The returned {@link ContextSnapshot} can be passed to another thread and {@linkplain
   * ContextSnapshot#activate() activated} to continue the trace there.
   *
   * <p><b>@llmNote</b> The snapshot captures the <em>effective</em> parent span id, resolved via
   * the same priority order used by {@code enterMethod}: scoped parent &rarr; active top-of-stack
   * &rarr; previously propagated snapshot parent. This means a detached frame whose span id was
   * passed to {@link #beginScope} will be correctly propagated as the parent even though it is no
   * longer on the active stack.
   *
   * @return a snapshot of the current context state
   */
  ContextSnapshot snapshot();

  /**
   * Sets the scoped parent to the given spanId. Any subsequent {@link #enterMethod} calls will see
   * {@code spanId} as their parent until {@link #endScope} is called. Returns the previous scope
   * value for restoration.
   *
   * <p>The default implementation is a no-op. Only {@link ThreadLocalNarrativeContext} provides
   * actual scope management.
   *
   * @param spanId the parent {@link SpanId} to scope
   * @return the previous scope value, or {@code null} if no scope was active
   */
  default SpanId beginScope(SpanId spanId) {
    return null;
  }

  /**
   * Restores the scoped parent to the value returned by a previous {@link #beginScope} call.
   *
   * <p>The default implementation is a no-op.
   *
   * @param previousScope the value returned by the corresponding {@link #beginScope}
   */
  default void endScope(SpanId previousScope) {}

  /**
   * Runs the given function with the specified spanId as the scoped parent. Any {@link
   * #enterMethod} calls within {@code fn} will see {@code spanId} as their parent, regardless of
   * the active stack position.
   *
   * <p>The default implementation simply calls {@code fn.get()} without scope management. Only
   * {@link ThreadLocalNarrativeContext} provides actual scoping.
   *
   * <p><b>@llmNote</b> THIS METHOD IS THE ONE PLACE WHERE THE NON-POISONING GUARANTEE STOPS, and an
   * implementer needs to know it. Everywhere else, tracing is total on the caller's thread: the
   * proxy and the agent catch {@link Throwable} around every hook, so the application's own result
   * or exception always wins. Here the implementation *wraps* the business call rather than being
   * called beside it, so a throw from this method — including from its own {@code finally}, after
   * {@code fn} has already returned a value — becomes the call's outcome. The proxy cannot re-run
   * the target (that would repeat its side effects) and will not invent a return value, so it
   * propagates.
   *
   * <p>The guarantee, stated exactly: it covers the contexts this library ships ({@link
   * ThreadLocalNarrativeContext}, {@code NoopNarrativeContext}) and every tracing hook on every
   * interception path. It does not cover a third-party override of this method, which is host code
   * running inside the call. An override must therefore be total itself: do the scoping in a {@code
   * try}/{@code finally} whose {@code finally} cannot throw, and let {@code fn}'s outcome through
   * unchanged. Decided 2026-09-01 and pinned by {@code NoPoisonContractTest}; a contained scope API
   * whose begin/end failures are swallowed is queued work, not today's contract.
   *
   * @param spanId the parent {@link SpanId} to scope
   * @param fn the function to run under the scoped parent
   * @param <T> the return type
   * @return the result of {@code fn}
   */
  default <T> T runScoped(SpanId spanId, Supplier<T> fn) {
    return fn.get();
  }

  /**
   * Returns the {@link SpanId} at the top of the active call stack, or {@code null} if the stack is
   * empty. Used by concurrency groups to capture the parent spanId at fork time.
   *
   * @return the current active {@link SpanId}, or {@code null} if no frame is open
   */
  default SpanId currentSpanId() {
    return null;
  }

  /**
   * Emits a pre-built {@link TraceNode} as enter/exit event pairs into the event trail. Recurses
   * into children. Used by concurrency groups to replay forked trace trees into the parent's event
   * stream.
   *
   * @param node the trace node to emit
   * @param parentSpanId the parent {@link SpanId} for the emitted enter event, or {@code null} for
   *     root
   */
  default void emitTraceNode(TraceNode node, SpanId parentSpanId) {}

  /**
   * Lifecycle callback fired when a {@link ForkGroup} is created.
   *
   * @param groupId the unique fork group identifier
   */
  default void onForkCreated(String groupId) {}

  /**
   * Lifecycle callback fired when a {@link ForkGroup} merges its collected children.
   *
   * @param groupId the fork group identifier
   * @param members the merged trace nodes
   */
  default void onMerge(String groupId, java.util.List<TraceNode> members) {}

  /**
   * Lifecycle callback fired when a {@link FireAndForgetGroup} is created.
   *
   * @param groupId the unique fire-and-forget group identifier
   */
  default void onFireAndForgetLaunched(String groupId) {}

  /**
   * Sets request-scoped fields stamped onto every subsequently created {@link
   * ai.narrativetrace.api.event.SpanContext}.
   *
   * <p><b>@sideEffects</b> Affects future spans only; existing spans keep the values they were
   * created with.
   *
   * @param httpMethod Semantic HTTP verb such as {@code GET} or {@code POST}.
   * @param httpRoute Concrete route or route template associated with the request.
   * @param clientIp Client address to attach to later spans, or {@code null} when unavailable.
   */
  default void setRequestContext(
      String httpMethod,
      ai.narrativetrace.api.event.HttpRoute httpRoute,
      ai.narrativetrace.api.event.ClientIp clientIp) {}

  /**
   * Sets user identity fields stamped onto every subsequently created {@link
   * ai.narrativetrace.api.event.SpanContext}.
   *
   * <p><b>@sideEffects</b> Affects future spans only; existing spans are unchanged.
   *
   * @param enduserId Stable user identifier for the current request, or {@code null}.
   * @param sessionId Session correlation identifier, or {@code null}.
   * @param tenantId Tenant or account scope, or {@code null}.
   */
  default void setUserContext(
      ai.narrativetrace.api.event.EnduserId enduserId,
      ai.narrativetrace.api.event.SessionId sessionId,
      ai.narrativetrace.api.event.TenantId tenantId) {}

  /**
   * Adopts a W3C trace context that arrived on an inbound request, so this service continues the
   * caller's trace instead of starting its own.
   *
   * <p>INTENT: HTTP filters call this once per request, before any traced method runs. It is
   * ADR-014's adopt rung across a process boundary — the in-process equivalent is {@link
   * ContextSnapshot}.
   *
   * <p><b>@sideEffects</b> Replaces the current trace's id, sampling flags and root-level parent
   * span for the calling thread. Existing spans keep the values they were created with.
   *
   * <p><b>@llmNote</b> Passing {@code null} is the documented no-op for "no header, or a header
   * that did not parse", so a filter can call {@code adoptTraceparent(Traceparent.parse(header))}
   * unconditionally and a stranger's malformed header degrades to a fresh local trace. Only the
   * root-level span takes the adopted span as its parent; nested calls keep their local caller.
   *
   * @param traceparent Parsed inbound header, or {@code null} to adopt nothing.
   */
  default void adoptTraceparent(Traceparent traceparent) {}

  /**
   * Returns the W3C trace context an outbound call made from this scope should carry.
   *
   * <p>INTENT: HTTP clients call this and, when it is non-null, send {@code
   * Traceparent.HEADER_NAME: value.format()} — the mirror image of {@link
   * #adoptTraceparent(Traceparent)}, and what makes a Java service a good citizen in someone else's
   * trace rather than only a good host in its own.
   *
   * <p><b>@edgeCase</b> Returns {@code null} when nothing can be correlated — no span is open and
   * no remote parent was adopted — because a {@code traceparent} without a real parent id would
   * point the next service at a span that never existed.
   *
   * @return The header value to send, or {@code null} when there is nothing to propagate.
   */
  default Traceparent outboundTraceparent() {
    return null;
  }

  /**
   * Returns the {@link TraceId} for the current trace, generating one eagerly if none exists yet.
   *
   * <p>INTENT: Filters and MDC bridges call this before the request chain runs to obtain the
   * traceId for persistent MDC population. The returned id is guaranteed to match the traceId in
   * any {@code SpanContext} created by subsequent {@link #enterMethod} calls.
   *
   * <p>The default implementation generates a new traceId on each call. Override for
   * implementations that maintain trace state across calls.
   *
   * @return the current trace's {@link TraceId}, never {@code null}
   */
  default TraceId traceId() {
    return SpanIdGenerator.traceId();
  }

  /**
   * Returns the story identifier derived from the first root-level method call, or {@code null} if
   * no root method has been entered yet.
   *
   * <p>INTENT: Exporters and MDC bridges use this to populate {@code nt.storyId} on every emitted
   * entry and chapter. The value is set lazily on the first root-level {@link
   * #enterMethod(MethodSignature)} and remains stable for the lifetime of the trace.
   *
   * @return the story id (e.g. {@code "OrderService.placeOrder"}), or {@code null}
   */
  default String storyId() {
    return null;
  }

  /**
   * Returns the chapter identifier for this service's contribution to the story, or {@code null} if
   * no root method has been entered yet.
   *
   * <p>For the root service, this equals {@link #storyId()}. For downstream services, it includes
   * the service's own root method (e.g. {@code "OrderService.placeOrder:PaymentService.charge"}).
   *
   * @return the chapter id, or {@code null}
   */
  /**
   * What the trace this thread would capture is missing: buffer drops (process-wide) and adoption
   * refusals (this thread's). {@link TraceLoss#none()} unless the best-effort path shed something.
   *
   * <p><b>@llmNote</b> Read it around a scenario — the counters only grow, so {@link
   * TraceLoss#since(TraceLoss)} against a reading taken before the scenario attributes the loss to
   * it. A non-empty result means the captured tree is an incomplete view of a run the synchronous
   * log still narrated in full.
   */
  default TraceLoss traceLoss() {
    return TraceLoss.none();
  }

  default String chapterId() {
    return null;
  }
}
