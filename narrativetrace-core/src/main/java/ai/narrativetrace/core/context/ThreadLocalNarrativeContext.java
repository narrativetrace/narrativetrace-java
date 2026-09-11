/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.ClientIp;
import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.EnduserId;
import ai.narrativetrace.api.event.HttpRoute;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.ResourceIdentity;
import ai.narrativetrace.api.event.ServiceIdentity;
import ai.narrativetrace.api.event.SessionId;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TenantId;
import ai.narrativetrace.api.event.ThreadInfo;
import ai.narrativetrace.api.event.TraceAnchor;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.pipeline.EventPipeline;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ai.narrativetrace.core.tree.TraceTreeBuilder;
import ai.narrativetrace.core.tree.TreeWalk;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Default {@link NarrativeContext} backed by a shared event pipeline and per-thread stack state.
 *
 * <p>INTENT: Use this in almost all applications unless you are replacing the capture backend
 * entirely. It is the implementation behind span parenting, snapshot activation, detached frames,
 * request metadata stamping, and trace-tree capture.
 *
 * <p>All events flow through one {@link EventPipeline}. Parent resolution happens from the current
 * thread's {@link TraceStack}, while tree capture later filters the shared event stream down to the
 * span ids known to the current scope.
 *
 * <p><b>@llmNote</b> Capture is thread-scoped: {@link #captureTrace()} and the package-private
 * {@code events()} return only spans known to the <em>calling</em> thread — work recorded on other
 * threads is invisible unless it was grafted in (via {@link ContextSnapshot#wrap}, {@link
 * ForkGroup}, or {@link FireAndForgetGroup}) or captured on the recording thread itself. This
 * isolation is deliberate; it keeps concurrent requests sharing one context from seeing each
 * other's traces.
 *
 * <p>The {@link ai.narrativetrace.api.config.TracingLevel} determines which events survive into the
 * resulting tree:
 *
 * <ul>
 *   <li>{@code OFF} — nothing captured, {@link #isActive()} returns {@code false}
 *   <li>{@code ERRORS} — only thrown and incomplete paths retained
 *   <li>{@code SUMMARY} — roots plus leaf calls retained, parameter values suppressed
 *   <li>{@code NARRATIVE} — all calls retained with parameter values suppressed
 *   <li>{@code DETAIL} — all calls retained with eager rendered parameter values
 * </ul>
 *
 * <p><b>@sideEffects</b> {@link #reset()} is thread-scoped: it clears the calling thread's stack
 * (including its request/user metadata) and removes the span contexts and stored events that stack
 * could report — the ones it created and the ones its async workers handed over. Other in-flight
 * requests sharing this context are never affected, so one filter-managed singleton safely serves
 * concurrent requests. A thread that holds no stack has nothing to clear, and {@code reset()} does
 * nothing for it.
 *
 * <p><b>@llmNote</b> Snapshot activation creates a fresh child stack that inherits the trace id and
 * the <em>effective</em> parent span id (scoped parent &rarr; active top-of-stack &rarr; snapshot
 * parent). It does not share the active frame list with the originating thread. This ensures that a
 * detached frame whose span id was set via {@code beginScope} is correctly propagated even when the
 * active stack is empty at snapshot time.
 *
 * <pre>{@code
 * var config = new NarrativeTraceConfig(TracingLevel.DETAIL);
 * var context = new ThreadLocalNarrativeContext(config);
 * var proxy = NarrativeTraceProxy.trace(target, MyService.class, context);
 *
 * context.setRequestContext("POST", "/orders/{id}", "203.0.113.7");
 * context.setUserContext("user-42", "sess-99", "tenant-a");
 * }</pre>
 *
 * @see NarrativeContext
 * @see NarrativeTraceConfig
 */
public final class ThreadLocalNarrativeContext implements NarrativeContext {

  private final NarrativeTraceConfig config;
  private final EventPipeline pipeline;
  private final ServiceIdentity serviceIdentity;
  private final ConcurrentHashMap<SpanId, SpanContext> spanContextMap = new ConcurrentHashMap<>();

  /**
   * How many extra flushes a capture will spend waiting for in-flight publishes.
   *
   * <p>These retries exist for pipelines whose {@code flush()} drains incrementally (see {@code
   * CapturePendingEventTest}'s shape) — the default pipeline's flush is itself a bounded barrier
   * for everything published before it, so it normally leaves nothing for the loop to do. The count
   * bounds contention, not time: a straggler descheduled mid-publication is the barrier's job, with
   * an explicit deadline, and a capture that gives up anyway is followed by the collect path
   * counting what it could not see.
   */
  private static final int DRAIN_ATTEMPTS = 64;

  /**
   * Signature recorded at enter, keyed by span, so exits can republish it on the {@link
   * TraceEvent.ExitEvent}. Same lifecycle as {@link #spanContextMap}: populated on enter, removed
   * when the exit publishes, swept by {@link #reset()} for spans that never exited.
   */
  private final ConcurrentHashMap<SpanId, MethodSignature> spanSignatureMap =
      new ConcurrentHashMap<>();

  /**
   * Spans dropped because the request that owned them had already ended, process-wide since start.
   *
   * <p>Context-level rather than per-stack, and it has to be: the discard happens on a worker
   * thread whose own stack is being thrown away, and the origin's stack is gone — there is no
   * per-request counter left for anyone to read. It rides out through {@link #traceLoss()} beside
   * the pipeline's process-wide drop count, which is the same kind of number.
   */
  private final AtomicLong discardedSpans = new AtomicLong();

  /**
   * Scopes, and the spans inside them, that a collect had to give up on: their events were still in
   * flight when the bounded drain wait was exhausted, and the discard that ends every collect then
   * dropped them unseen — no capture ever contained them, and none ever can.
   *
   * <p>Context-level for the same reason {@link #discardedSpans} is: the worker's own stack is
   * being thrown away by the same collect, so the count must ride somewhere the origin thread can
   * still read. Reported through {@link #traceLoss()} inside the refused-scope/-span numbers — the
   * reader-facing meaning is identical to the adoption cap's refusals: an async subtree absent from
   * the tree, counted, never silent.
   */
  private final AtomicLong uncollectedScopes = new AtomicLong();

  /** Spans lost to those exhausted collects — the number a reader of the trace is missing. */
  private final AtomicLong uncollectedSpans = new AtomicLong();

  /**
   * The per-thread stack, created on demand by {@link #stack()} and never by the holder itself.
   *
   * <p><b>@llmNote</b> Deliberately not {@code ThreadLocal.withInitial}: with an initial supplier,
   * every read materialised a stack, and {@link #reset()} — whose whole job is to remove one —
   * constructed an {@code ArrayList}, a {@code ConcurrentHashMap} key set and two {@code
   * AtomicLong} s (~344 B) on a thread that had never traced, only to throw them away. Read through
   * {@link #stack()} to materialise, or {@code stackHolder.get()} directly to ask whether the
   * thread has one at all.
   */
  private final ThreadLocal<TraceStack> stackHolder = new ThreadLocal<>();

  /** Creates a context with the default configuration, {@link TracingLevel#DETAIL}. */
  public ThreadLocalNarrativeContext() {
    this(new NarrativeTraceConfig());
  }

  /**
   * Creates a context with the given configuration.
   *
   * @param config the tracing configuration (level can be changed at runtime)
   */
  public ThreadLocalNarrativeContext(NarrativeTraceConfig config) {
    this(config, defaultPipeline());
  }

  /**
   * Creates a context with the given configuration and event pipeline.
   *
   * <p>INTENT: Use this to choose the pipeline topology explicitly. The pipeline is used exactly as
   * given — the context never wraps or completes it. A pipeline that retains nothing (e.g. {@code
   * new DualPathPipeline(listener)}, the narration-only topology) makes {@link #captureTrace()}
   * legitimately empty; compose a {@link ai.narrativetrace.core.pipeline.RetainingConsumer} when
   * capture is needed, or use {@code new DualPathPipeline()} for the default capture topology.
   *
   * @param config the tracing configuration (level can be changed at runtime)
   * @param pipeline Pipeline for event routing, used as given.
   */
  public ThreadLocalNarrativeContext(NarrativeTraceConfig config, EventPipeline pipeline) {
    this(config, pipeline, null);
  }

  /**
   * Creates a context with the given configuration, event pipeline, and service identity.
   *
   * <p><b>@sideEffects</b> The supplied identity is copied onto every subsequently created span.
   *
   * @param config the tracing configuration (level can be changed at runtime)
   * @param pipeline pipeline for event routing
   * @param serviceIdentity Service metadata stamped on every created {@link SpanContext}, or {@code
   *     null} when not needed.
   */
  public ThreadLocalNarrativeContext(
      NarrativeTraceConfig config, EventPipeline pipeline, ServiceIdentity serviceIdentity) {
    this.config = config;
    this.pipeline = pipeline;
    this.serviceIdentity = serviceIdentity;
  }

  /**
   * Builds the pipeline this deployment configured, through the single composition root.
   *
   * <p>INTENT: A context created without an explicit pipeline should behave the way the deployment
   * asked for — narrating when the slf4j module is present, carrying discovered extensions, and
   * honouring a configured topology — instead of getting a bare dual-path that every integration
   * then had to re-assemble by hand.
   */
  private static EventPipeline defaultPipeline() {
    return PipelineBootstrap.createDefault();
  }

  /**
   * The calling thread's stack, created on first use.
   *
   * <p>The single materialising accessor: every path that is about to record, read or parent
   * something goes through here, so "does this thread have a stack yet?" is asked in exactly one
   * other place — {@link #reset()}, which must not create what it is there to remove.
   */
  private TraceStack stack() {
    var stack = stackHolder.get();
    if (stack == null) {
      stack = new TraceStack();
      stackHolder.set(stack);
    }
    return stack;
  }

  @Override
  public boolean isActive() {
    return config.level().isEnabled(TracingLevel.ERRORS);
  }

  @Override
  public boolean capturesParameterValues() {
    return config.level().isEnabled(TracingLevel.DETAIL);
  }

  @Override
  public boolean capturesInstanceIds() {
    return config.captureInstanceIds();
  }

  @Override
  public boolean capturesSourceLocation() {
    return config.captureSourceLocation();
  }

  @Override
  public SpanId enterMethod(MethodSignature signature) {
    if (!config.level().isEnabled(TracingLevel.ERRORS)) {
      return null;
    }
    if (!config.level().isEnabled(TracingLevel.DETAIL)) {
      signature = suppressParameterValues(signature);
    }
    var stack = stack();
    SpanId parentSpanId = resolveParentSpanId(stack);
    if (parentSpanId == null) {
      stack.deriveStoryIdIfAbsent(signature.className(), signature.methodName());
    }
    var spanContext = createSpanContext(stack, parentSpanId);
    spanContextMap.put(spanContext.spanId(), spanContext);
    spanSignatureMap.put(spanContext.spanId(), signature);
    var threadInfo = currentThreadInfo();
    pipeline.publish(
        new TraceEvent.EnterEvent(
            spanContext, System.nanoTime(), signature, asyncTagFor(stack, threadInfo), threadInfo));
    stack.pushActive(spanContext.spanId());
    return spanContext.spanId();
  }

  /**
   * Tags the first span a thread opens under a propagated snapshot as {@link ConcurrencyKind#ASYNC}
   * — and only that span, since everything below it is ordinary sequential work on the same thread.
   *
   * <p>INTENT: This is what makes async work render the way fork members always have: under a
   * marker, members sorted, order not asserted. The scheduler decides which task starts first, so
   * capture order is not behaviour and a structural baseline must not pin it. The group is keyed by
   * the parent span so every async child of one call renders as one group; work propagated with no
   * parent span groups per trace instead.
   */
  private static ConcurrencyInfo asyncTagFor(TraceStack stack, ThreadInfo thread) {
    if (!stack.fromSnapshot() || stack.peekActive() != null) {
      return null;
    }
    var parent = stack.snapshotParentSpanId();
    var key = parent != null ? parent.value() : String.valueOf(stack.traceId());
    return new ConcurrencyInfo(
        "async-" + key,
        thread.threadName(),
        thread.threadId(),
        thread.virtual(),
        ConcurrencyKind.ASYNC);
  }

  /** Identity of the executing thread, captured on every enter (free at the capture site). */
  private static ThreadInfo currentThreadInfo() {
    var thread = Thread.currentThread();
    return new ThreadInfo(thread.getName(), thread.getId(), ConcurrencySupport.isVirtual(thread));
  }

  /** Thread identity recorded when grafted work originally ran, or {@code null} without it. */
  private static ThreadInfo threadInfoFrom(ConcurrencyInfo concurrency) {
    if (concurrency == null || concurrency.threadName() == null) {
      return null;
    }
    return new ThreadInfo(concurrency.threadName(), concurrency.threadId(), concurrency.virtual());
  }

  /**
   * Package-private: returns the pipeline events for testing. Thread-scoped like {@link
   * #captureTrace()} — only events whose span ids are known to the calling thread are returned, so
   * call this on the recording thread (or after grafting) for cross-thread flows.
   */
  List<TraceEvent> events() {
    drainPublishedEvents();
    return reportableEvents();
  }

  /**
   * Package-private test seam: how many span contexts this context is still holding, across every
   * thread. Retention is invisible from the public surface — {@link #captureTrace()} filters to the
   * calling thread — so the async end-of-life contract has no other way to assert that a finished
   * request left nothing behind.
   */
  int retainedSpanCount() {
    return spanContextMap.size();
  }

  /**
   * Drains the pipeline, waiting briefly for publishes that are still in flight.
   *
   * <p>INTENT: A ring drain stops at the first slot a producer has claimed but not finished
   * writing, so under concurrent publishing one {@code flush()} can return with the <em>calling
   * thread's own</em> events still outstanding — and a capture taken then silently omits them. The
   * fork helper made that visible: collecting 512 workers on 8 threads lost 17 and 21 of them in
   * two runs out of eight, with the deficit exactly the number of collections that saw no events at
   * all.
   *
   * <p><b>@edgeCase</b> The wait is bounded and busy: an attempt costs a spin, and a pipeline that
   * never empties because other threads keep publishing costs {@link #DRAIN_ATTEMPTS} of them and
   * then proceeds with what it has. Zero attempts for the common case of a pipeline that drains on
   * the first flush — which is every single-threaded capture, and, since the default pipeline's
   * flush became a bounded barrier for the caller's own events, every capture whose stragglers
   * resolve within that barrier's deadline. A capture can still come up short — the barrier's
   * deadline is explicit, not infinite — which is why {@link #collectLocalTrace()} counts what the
   * final capture of a scope could not see.
   */
  private void drainPublishedEvents() {
    pipeline.flush();
    for (int attempt = 0; attempt < DRAIN_ATTEMPTS && !pipeline.drained(); attempt++) {
      Thread.onSpinWait();
      pipeline.flush();
    }
  }

  private SpanContext createSpanContext(TraceStack stack, SpanId parentSpanId) {
    TraceId traceId = stack.traceId();
    if (traceId == null) {
      traceId = SpanIdGenerator.traceId();
      stack.setTraceId(traceId);
    }
    SpanId spanId = SpanIdGenerator.spanId();
    var metadata = stack.metadata();
    var builder =
        SpanContext.builder(traceId, spanId)
            .parentSpanId(parentSpanId)
            .httpMethod(metadata.httpMethod())
            .httpRoute(metadata.httpRoute())
            .clientIp(metadata.clientIp())
            .enduserId(metadata.enduserId())
            .sessionId(metadata.sessionId())
            .tenantId(metadata.tenantId())
            .storyId(stack.storyId())
            .chapterId(stack.chapterId())
            .traceFlags(stack.traceFlags())
            .traceAnchor(stack.traceAnchor())
            .resourceIdentity(config.captureResource() ? ResourceIdentity.current() : null);
    if (serviceIdentity != null) {
      builder
          .serviceName(serviceIdentity.serviceName())
          .serviceVersion(serviceIdentity.serviceVersion())
          .environment(serviceIdentity.environment());
    }
    return builder.build();
  }

  private SpanId resolveParentSpanId(TraceStack stack) {
    SpanId scoped = stack.scopedParentSpanId();
    if (scoped != null) return scoped;
    SpanId active = stack.peekActive();
    if (active != null) return active;
    SpanId snapshot = stack.snapshotParentSpanId();
    if (snapshot != null) return snapshot;
    // Last rung: an inbound traceparent parents this service's root span, and nothing deeper.
    return stack.remoteParentSpanId();
  }

  @Override
  public SpanId beginScope(SpanId spanId) {
    var stack = stack();
    SpanId prev = stack.scopedParentSpanId();
    stack.setScopedParentSpanId(spanId);
    return prev;
  }

  @Override
  public void endScope(SpanId previousScope) {
    stack().setScopedParentSpanId(previousScope);
  }

  @Override
  public <T> T runScoped(SpanId spanId, Supplier<T> fn) {
    SpanId prev = beginScope(spanId);
    try {
      return fn.get();
    } finally {
      endScope(prev);
    }
  }

  /**
   * Replaces parameter values with empty strings; every other signature field is preserved.
   *
   * <p><b>@edgeCase</b> A signature with no parameters is returned as it is. There is nothing to
   * suppress, and rebuilding a ten-field record around an empty list through a stream cost ~400 B
   * on every enter below {@code DETAIL} — which is why the committed baseline had {@code
   * _NARRATIVE}, {@code _SUMMARY} and {@code _ERRORS} allocating *more* than {@code _DETAIL}, a
   * result that reads as nonsense until you find this method.
   */
  private static MethodSignature suppressParameterValues(MethodSignature signature) {
    var parameters = signature.parameters();
    if (parameters.isEmpty()) {
      return signature;
    }
    var suppressed = new ArrayList<ParameterCapture>(parameters.size());
    for (var parameter : parameters) {
      suppressed.add(parameter.withoutValues());
    }
    return signature.withParameters(Collections.unmodifiableList(suppressed));
  }

  @Override
  public void detachFrame(SpanId spanId) {
    stack().detach(spanId);
  }

  @Override
  public void exitMethodWithReturn(String renderedReturnValue) {
    var traceStack = stack();
    if (traceStack.isEmpty()) return;
    SpanId spanId = traceStack.popActive();
    var spanContext = spanContextMap.get(spanId);
    pipeline.publish(
        new TraceEvent.ExitEvent(
            spanContext,
            System.nanoTime(),
            new TraceOutcome.Returned(renderedReturnValue),
            null,
            spanSignatureMap.remove(spanId)));
  }

  @Override
  public void exitMethodWithReturn(String renderedReturnValue, SpanId spanId) {
    if (spanId == null) {
      exitMethodWithReturn(renderedReturnValue);
      return;
    }
    appendExitEvent(spanId, new TraceOutcome.Returned(renderedReturnValue), null);
    stack().detach(spanId);
  }

  @Override
  public void exitMethodWithReturn(
      String renderedReturnValue, RenderedValue structuredReturnValue, SpanId spanId) {
    if (spanId == null) {
      var traceStack = stack();
      if (traceStack.isEmpty()) return;
      SpanId popped = traceStack.popActive();
      var spanContext = spanContextMap.get(popped);
      pipeline.publish(
          new TraceEvent.ExitEvent(
              spanContext,
              System.nanoTime(),
              new TraceOutcome.Returned(renderedReturnValue, structuredReturnValue),
              null,
              spanSignatureMap.remove(popped)));
      return;
    }
    appendExitEvent(
        spanId, new TraceOutcome.Returned(renderedReturnValue, structuredReturnValue), null);
    stack().detach(spanId);
  }

  @Override
  public void exitMethodWithException(Throwable exception, String errorContext) {
    var traceStack = stack();
    if (traceStack.isEmpty()) return;
    SpanId spanId = traceStack.popActive();
    var spanContext = spanContextMap.get(spanId);
    pipeline.publish(
        new TraceEvent.ExitEvent(
            spanContext,
            System.nanoTime(),
            new TraceOutcome.Threw(exception),
            errorContext,
            spanSignatureMap.remove(spanId)));
  }

  @Override
  public void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId) {
    if (spanId == null) {
      exitMethodWithException(exception, errorContext);
      return;
    }
    appendExitEvent(spanId, new TraceOutcome.Threw(exception), errorContext);
    stack().detach(spanId);
  }

  private void appendExitEvent(SpanId spanId, TraceOutcome outcome, String errorContext) {
    var spanContext = spanContextMap.get(spanId);
    if (spanContext == null) {
      return;
    }
    pipeline.publish(
        new TraceEvent.ExitEvent(
            spanContext,
            System.nanoTime(),
            outcome,
            errorContext,
            spanSignatureMap.remove(spanId)));
  }

  /**
   * Emits {@code node} and every descendant as enter/exit event pairs, bounded and cycle-safe via
   * {@link TreeWalk}.
   *
   * <p><b>@edgeCase</b> {@code node} is public API's own input — {@link
   * NarrativeContext#emitTraceNode} takes any {@link TraceNode}, and that record's {@code children}
   * field is undefended, so a hand-built or replayed node reaching this method is not guaranteed
   * acyclic. Unlike a rendered artifact, an infinite recursion here would hang or crash the traced
   * application itself, not just corrupt an output file — the same bound every renderer in {@code
   * core}/{@code clarity}/ {@code diagrams} already has is required here too. A node beyond {@link
   * TreeWalk#MAX_DEPTH} or already on the current path still gets its own enter/exit event pair,
   * exactly as a leaf would; the walk simply never descends into its children.
   */
  @Override
  public void emitTraceNode(TraceNode node, SpanId parentSpanId) {
    Deque<SpanContext> chain = new ArrayDeque<>();
    TreeWalk.walk(
        node,
        TraceNode::children,
        (n, depth) -> enterEmittedNode(n, parentSpanId, chain),
        (n, depth) -> exitEmittedNode(n, chain),
        (n, depth, reason) -> {
          enterEmittedNode(n, parentSpanId, chain);
          exitEmittedNode(n, chain);
        });
  }

  private void enterEmittedNode(TraceNode node, SpanId rootParentSpanId, Deque<SpanContext> chain) {
    var stack = stack();
    var parentSpanId = chain.isEmpty() ? rootParentSpanId : chain.peek().spanId();
    var spanContext = createSpanContext(stack, parentSpanId);
    spanContextMap.put(spanContext.spanId(), spanContext);
    // Grafted nodes were executed on their original thread; ConcurrencyInfo recorded it there.
    // Capturing the emitting thread here would misattribute the work.
    var enter =
        new TraceEvent.EnterEvent(
            spanContext,
            node.startTimeNanos(),
            node.signature(),
            node.concurrency(),
            threadInfoFrom(node.concurrency()));
    stack.pushActive(spanContext.spanId());
    pipeline.publish(enter);
    chain.push(spanContext);
  }

  private void exitEmittedNode(TraceNode node, Deque<SpanContext> chain) {
    var spanContext = chain.pop();
    long exitTimestamp = node.startTimeNanos() + node.durationNanos();
    var exit =
        new TraceEvent.ExitEvent(
            spanContext,
            exitTimestamp,
            node.outcome(),
            node.signature().errorContext(),
            node.signature());
    pipeline.publish(exit);
    stack().detach(spanContext.spanId());
  }

  @Override
  public SpanId currentSpanId() {
    return stack().peekActive();
  }

  @Override
  public void onForkCreated(String groupId) {
    pipeline.publish(new TraceEvent.ForkCreatedEvent(groupId, System.nanoTime()));
  }

  @Override
  public void onMerge(String groupId, java.util.List<TraceNode> members) {
    pipeline.publish(new TraceEvent.MergeEvent(groupId, members.size(), System.nanoTime()));
  }

  @Override
  public void onFireAndForgetLaunched(String groupId) {
    pipeline.publish(new TraceEvent.FireAndForgetEvent(groupId, System.nanoTime()));
  }

  /**
   * Captures the calling thread's trace, draining the pipeline first.
   *
   * <p><b>@sideEffects</b> The {@code flush()} runs at every level, {@code OFF} included, and it is
   * where a drain-at-{@code OFF} caller should keep looking: in the default pipeline nothing else
   * empties the ring, so capture's flush is the only drain. What {@code OFF} skips is everything
   * <em>after</em> it — the span-id snapshot, the event copy and filter, and the loss reading —
   * because none of them can produce a node. A caller that wants the ring drained without a tree
   * should call {@link EventPipeline#flush()} and say so.
   *
   * <p><b>@edgeCase</b> The tree returned at {@code OFF} carries no {@link TraceLoss}: loss says a
   * narrative is missing something, and there is no narrative here.
   */
  @Override
  public TraceTree captureTrace() {
    drainPublishedEvents();
    if (!isActive()) {
      return TraceTreeBuilder.build(List.of(), config.level(), assignedTraceId(), TraceLoss.none());
    }
    // The tree carries the trace id this stack was assigned, so an exporter can name the trace
    // even for a tree whose nodes carry no span context. Read without generating: a thread that
    // traced nothing must not acquire an identity by being asked for one.
    // The loss reading rides on the tree so every renderer can say the narrative is incomplete;
    // without it the fact would live only in a console summary the reader of a trace file never
    // sees.
    return TraceTreeBuilder.build(
        reportableEvents(), config.level(), assignedTraceId(), traceLoss());
  }

  /**
   * The already-drained pipeline view, filtered to the spans the calling thread may report.
   *
   * <p>Callers drain first ({@link #drainPublishedEvents()}); this method only snapshots and
   * filters, so a capture and the accounting beside it read one list, not two racing ones.
   */
  private List<TraceEvent> reportableEvents() {
    var ids = knownSpanIds();
    return pipeline.events().stream().filter(e -> hasKnownSpanId(ids, e)).toList();
  }

  /**
   * The trace id this thread was already assigned, or {@code null} — never one it was not.
   *
   * <p>Reads the holder rather than {@link #stack()} for the same reason it does not generate:
   * asking a thread that never traced for its trace id must not hand it a stack either.
   */
  private TraceId assignedTraceId() {
    var stack = stackHolder.get();
    return stack == null ? null : stack.traceId();
  }

  private static boolean hasKnownSpanId(Set<SpanId> ids, TraceEvent event) {
    SpanId spanId = TraceEvent.spanIdOf(event);
    return spanId != null && ids.contains(spanId);
  }

  /**
   * Every span this thread may report, as a snapshot: exactly {@link TraceStack#reportableSpanIds}
   * — the spans it created, the ones worker threads handed over at scope close, and the ones
   * workers have published under a snapshot scope that is still open. Helpers that publish their
   * children themselves activate with {@link ContextSnapshot#activateWithoutAdoption()}, so nothing
   * lands here twice.
   *
   * <p><b>@llmNote</b> One copy, not three. This method used to restate the union that {@code
   * TraceStack} already computes: {@code Set.copyOf} of the live concurrent set (271 B for a single
   * element, because copying a {@code ConcurrentHashMap} key-set view walks a {@code Traverser}),
   * then a {@code HashSet} of that, then {@code Set.copyOf} of the result — 776 B of the 1,938 B a
   * one-node {@code captureTrace()} allocated (report D3). Delegating leaves one copy, made where
   * the union is defined, and removes a second definition of "reportable" that could drift from the
   * first.
   *
   * <p><b>@edgeCase</b> The result stays a <em>snapshot</em>, deliberately: capture iterates it
   * while worker threads may still be publishing into the live sets behind it, and the unmodifiable
   * wrapper is over a fresh {@code HashSet}, never over a live view.
   */
  private Set<SpanId> knownSpanIds() {
    return Collections.unmodifiableSet(stack().reportableSpanIds());
  }

  @Override
  public TraceLoss traceLoss() {
    var stack = stack();
    // Exhausted collects join the adoption cap's refusals: both are whole async subtrees absent
    // from the tree, and the reader asking "is my narrative complete?" needs their sum.
    return new TraceLoss(
        pipeline.droppedEventCount(),
        stack.refusedScopeCount() + uncollectedScopes.get(),
        stack.refusedSpanCount() + uncollectedSpans.get(),
        discardedSpans.get());
  }

  @Override
  public TraceTree captureLocalTrace() {
    return captureTrace();
  }

  /**
   * Captures the calling scope's trace, counts what the capture could not see, then discards the
   * scope.
   *
   * <p>INTENT: This is the concurrency helpers' collect step, and the one capture after which the
   * scope's raw events are unconditionally gone — so it is where "not visible yet" hardens from
   * latency into loss. The drain wait is bounded, and when it is exhausted under a stalled
   * publisher, a span whose events were all still in flight would otherwise vanish whole: the
   * capture returns no node for it, the discard tombstones its events, and no counter anywhere
   * records that a child existed. Counting those spans through the refusal channel keeps the family
   * rule — loss under pressure is acceptable, silent loss never is.
   *
   * <p><b>@edgeCase</b> A span with <em>some</em> events visible is not counted: it appears in the
   * capture (possibly with an {@link ai.narrativetrace.api.event.TraceOutcome.Incomplete} outcome),
   * so its loss mode is visible in the narrative itself. Only a span with <em>no</em> visible event
   * is silent, and only the caller's own spans qualify — the calling thread's publishes happened
   * before this call, so "still invisible" is proof of an exhausted drain rather than of another
   * thread's ongoing work.
   */
  @Override
  public TraceTree collectLocalTrace() {
    drainPublishedEvents();
    var tree = collectableTree();
    discardLocalTrace();
    return tree;
  }

  /** The collect-step capture: identical to {@link #captureTrace()} plus the unseen accounting. */
  private TraceTree collectableTree() {
    if (!isActive()) {
      return TraceTreeBuilder.build(List.of(), config.level(), assignedTraceId(), TraceLoss.none());
    }
    var visible = reportableEvents();
    countOwnSpansUnseen(visible);
    return TraceTreeBuilder.build(visible, config.level(), assignedTraceId(), traceLoss());
  }

  /**
   * Counts, as refused loss, the caller's own spans with no event in the capture it just took.
   *
   * <p>After a fully drained capture this counts nothing: every span in the calling thread's
   * created set published its enter before being recorded there, on the same thread, so at least
   * one event per span is visible once the drain has caught up. Tracing levels do not change that —
   * pruning happens at tree build, never at the store.
   */
  private void countOwnSpansUnseen(List<TraceEvent> visible) {
    var stack = stackHolder.get();
    if (stack == null) {
      return;
    }
    var own = stack.knownSpanIds();
    if (own.isEmpty()) {
      return;
    }
    var seen = new HashSet<SpanId>();
    for (var event : visible) {
      var spanId = TraceEvent.spanIdOf(event);
      if (spanId != null) {
        seen.add(spanId);
      }
    }
    long unseen = own.stream().filter(spanId -> !seen.contains(spanId)).count();
    if (unseen > 0) {
      uncollectedScopes.incrementAndGet();
      uncollectedSpans.addAndGet(unseen);
    }
  }

  /**
   * Drops the calling scope's spans and events, leaving the caller's copy as the record.
   *
   * <p><b>@edgeCase</b> A thread with no stack has nothing to discard and is not given one — the
   * same rule {@link #reset()} follows, and for the same reason: this runs on worker threads.
   */
  @Override
  public void discardLocalTrace() {
    var stack = stackHolder.get();
    if (stack == null) {
      return;
    }
    discardSpans(stack.reportableSpanIds());
  }

  @Override
  public void setRequestContext(String httpMethod, HttpRoute httpRoute, ClientIp clientIp) {
    var stack = stack();
    stack.setMetadata(stack.metadata().withRequest(httpMethod, httpRoute, clientIp));
  }

  @Override
  public void setUserContext(EnduserId enduserId, SessionId sessionId, TenantId tenantId) {
    var stack = stack();
    stack.setMetadata(stack.metadata().withUser(enduserId, sessionId, tenantId));
  }

  @Override
  public void adoptTraceparent(Traceparent traceparent) {
    if (traceparent == null) {
      return;
    }
    var stack = stack();
    stack.setTraceId(traceparent.traceId());
    stack.setRemoteParentSpanId(traceparent.parentSpanId());
    stack.setTraceFlags(traceparent.traceFlags());
  }

  @Override
  public Traceparent outboundTraceparent() {
    var stack = stack();
    TraceId traceId = stack.traceId();
    SpanId parentSpanId = resolveParentSpanId(stack);
    if (traceId == null || parentSpanId == null) {
      return null;
    }
    return new Traceparent(traceId, parentSpanId, stack.traceFlags());
  }

  @Override
  public TraceId traceId() {
    var stack = stack();
    TraceId id = stack.traceId();
    if (id == null) {
      id = SpanIdGenerator.traceId();
      stack.setTraceId(id);
    }
    return id;
  }

  @Override
  public String storyId() {
    return stack().storyId();
  }

  @Override
  public String chapterId() {
    return stack().chapterId();
  }

  /**
   * Clears the calling thread's trace state, and does nothing at all when it has none.
   *
   * <p><b>@sideEffects</b> A thread that never traced holds no stack, so there is nothing to clear
   * and this returns without creating one. That makes {@code reset()} in a {@code finally} free on
   * the requests that never entered a traced method — an early-returning health check, a filter
   * ordered before the traced beans — where it used to build a whole {@code TraceStack} and throw
   * it away.
   *
   * <p><b>@edgeCase</b> What it clears is exactly what the request could <em>report</em> — {@link
   * TraceStack#reportableSpanIds()}, so the spans this thread created plus the ones its async
   * workers handed over — and not merely the ones it created. Clearing only the owned set left an
   * adopted worker's spans and events in the shared maps and the event store for the life of the
   * process: a request that correctly waited for its async work still leaked it, once per request,
   * in exactly the long-running servlet or Micronaut singleton this method exists for. Reporting
   * and clearing now read the same set, so nothing can be visible to a request and survive it.
   *
   * <p>The stack is <em>closed</em> before it is swept, and closing comes first for a reason: a
   * worker still holding a snapshot of it can then tell that its request has ended and discard its
   * own spans, and no adoption can slip in between the sweep and the removal.
   */
  @Override
  public void reset() {
    var stack = stackHolder.get();
    if (stack == null) {
      return;
    }
    stack.close();
    var reportableSpanIds = stack.reportableSpanIds();
    stackHolder.remove();
    discardSpans(reportableSpanIds);
  }

  /**
   * Removes every trace of the given spans: their contexts, their pending signatures, and their
   * retained events.
   *
   * <p>INTENT: One definition of "these spans are over", used by {@link #reset()} at the end of a
   * request and by the async paths that discard work no live request can report. Other traces are
   * untouched — the maps and the store are shared by every in-flight request.
   *
   * @param spanIds Spans to forget; an empty set does nothing, including no flush.
   */
  private void discardSpans(Set<SpanId> spanIds) {
    if (spanIds.isEmpty()) {
      return;
    }
    spanContextMap.keySet().removeAll(spanIds);
    spanSignatureMap.keySet().removeAll(spanIds);
    pipeline.flush();
    pipeline.clearSpans(spanIds);
  }

  /**
   * Discards spans no live request can report, and counts them as loss.
   *
   * <p>INTENT: The end of the line for async work that outlived its request — a worker whose origin
   * stack was closed by {@code reset()} or collected outright. Deterministic by construction: it
   * does not matter whether the garbage collector has got to the origin yet, only whether the
   * request is over.
   *
   * @param spanIds Spans nobody will ever report; removed and counted. An empty set counts nothing
   *     and does nothing — {@link #discardSpans} owns that guard.
   */
  private void discardOrphanedSpans(Set<SpanId> spanIds) {
    discardedSpans.addAndGet(spanIds.size());
    discardSpans(spanIds);
  }

  @Override
  public ContextSnapshot snapshot() {
    var stack = stack();
    return new ThreadLocalContextSnapshot(
        this, stack.traceId(), resolveParentSpanId(stack), stack.metadata(), stack);
  }

  TraceStack swapStack(TraceStack replacement) {
    var previous = stack();
    stackHolder.set(replacement);
    return previous;
  }

  private static final class ThreadLocalContextSnapshot implements ContextSnapshot {
    private final ThreadLocalNarrativeContext context;
    private final TraceId traceId;
    private final SpanId parentSpanId;
    private final RequestMetadata metadata;

    /**
     * The stack this snapshot was taken from, weakly held: a pending snapshot must never keep a
     * finished request's stack alive, and a stack that is gone simply adopts nothing.
     */
    private final WeakReference<TraceStack> origin;

    ThreadLocalContextSnapshot(
        ThreadLocalNarrativeContext context,
        TraceId traceId,
        SpanId parentSpanId,
        RequestMetadata metadata,
        TraceStack origin) {
      this.context = context;
      this.traceId = traceId;
      this.parentSpanId = parentSpanId;
      this.metadata = metadata;
      this.origin = new WeakReference<>(origin);
    }

    @Override
    public ContextScope activate() {
      return activate(true);
    }

    @Override
    public ContextScope activateWithoutAdoption() {
      return activate(false);
    }

    private ContextScope activate(boolean adopt) {
      var childStack = new TraceStack();
      childStack.markFromSnapshot();
      if (traceId != null) {
        childStack.setTraceId(traceId);
      }
      if (parentSpanId != null) {
        childStack.setSnapshotParentSpanId(parentSpanId);
      }
      childStack.setMetadata(metadata);
      var registration = adopt ? registerLiveChild(origin.get(), childStack) : null;
      var previousStack = context.swapStack(childStack);
      return () -> {
        context.swapStack(previousStack);
        if (adopt) {
          TraceStack originStack = origin.get();
          handOver(originStack, childStack);
          unregisterLiveChild(originStack, registration);
        }
      };
    }

    /**
     * Hands the child's work to the origin, or ends it — one or the other, never neither.
     *
     * <p>INTENT: This is where async work finds out whether anyone is still listening. A request
     * that has reset has closed its stack, and a request whose thread is long gone has had its
     * stack collected; in both cases the child's spans are unreportable from the moment they were
     * published, and keeping them in the shared maps and the event store is a leak that grows with
     * every late completion. The adoption cap says the same thing for a different reason.
     *
     * <p>What moves is the child's <em>reportable</em> set, not merely the spans it created: a
     * worker that dispatched further async work has already adopted its own grandchildren, and a
     * hand-over that dropped them would end the chain one hop from the origin — the only thread
     * anyone captures on. What the child can answer for is exactly what it passes up, or exactly
     * what is discarded.
     *
     * <p><b>@edgeCase</b> The refusal path is <em>not</em> counted twice: {@link TraceStack#adopt}
     * has already recorded it as a refused scope, which is the reader-facing number ("this subtree
     * is missing"). {@link #discardOrphanedSpans} counts only what no counter has seen — work whose
     * request ended before it did.
     */
    private void handOver(TraceStack originStack, TraceStack childStack) {
      var childSpanIds = childStack.reportableSpanIds();
      if (originStack == null || originStack.isClosed()) {
        context.discardOrphanedSpans(childSpanIds);
      } else if (!originStack.adopt(childSpanIds)) {
        context.discardSpans(childSpanIds);
      }
    }

    /**
     * Announces the child stack to the thread that took the snapshot, before any span can be
     * published into it, so the origin's {@code captureTrace()} sees the worker's calls from the
     * moment they are published rather than from scope close.
     *
     * <p><b>@llmNote</b> Scope close is too late: a framework can hand control back to the caller
     * while the scope is still open. Spring completes an {@code @Async} method's {@code
     * CompletableFuture} inside the decorated task, so {@code future.get()} returns — and the
     * caller captures — before the {@code TaskDecorator}'s scope closes and adopts.
     *
     * <p>Skipped when the origin stack is closed or gone (reset, or the thread finished), exactly
     * as {@link #handOver} is: an orphan must not resurrect a request that has ended.
     */
    private static WeakReference<TraceStack> registerLiveChild(
        TraceStack origin, TraceStack childStack) {
      return origin == null || origin.isClosed() ? null : origin.registerLiveChild(childStack);
    }

    /**
     * Drops the live registration once adoption has taken the same spans over, in that order: the
     * two sets are identical, so a capture racing this close sees the worker's calls through one or
     * the other, never through neither.
     *
     * <p><b>@edgeCase</b> Writing in this order is only half of that. The reader has to union in
     * the <em>opposite</em> order — live children first, then adopted — or it can straddle the
     * hand-over and see neither; see {@link TraceStack#reportableSpanIds()}, which is where the
     * other half lives.
     */
    private static void unregisterLiveChild(
        TraceStack origin, WeakReference<TraceStack> registration) {
      if (origin != null) {
        origin.unregisterLiveChild(registration);
      }
    }
  }

  /**
   * Immutable per-request metadata stamped onto every span created by the owning thread.
   *
   * <p>INTENT: Thread-scoped by design — concurrent requests each stamp their own copy, and {@link
   * ContextSnapshot} carries it to worker threads so async children inherit their request's
   * identity, never a concurrent request's.
   */
  record RequestMetadata(
      String httpMethod,
      HttpRoute httpRoute,
      ClientIp clientIp,
      EnduserId enduserId,
      SessionId sessionId,
      TenantId tenantId) {

    static final RequestMetadata EMPTY = new RequestMetadata(null, null, null, null, null, null);

    RequestMetadata withRequest(String httpMethod, HttpRoute httpRoute, ClientIp clientIp) {
      return new RequestMetadata(httpMethod, httpRoute, clientIp, enduserId, sessionId, tenantId);
    }

    RequestMetadata withUser(EnduserId enduserId, SessionId sessionId, TenantId tenantId) {
      return new RequestMetadata(httpMethod, httpRoute, clientIp, enduserId, sessionId, tenantId);
    }
  }

  static final class TraceStack {

    /**
     * Ceiling on spans adopted from worker threads.
     *
     * <p>The set holds references to {@link SpanId} instances the events already retain, so an
     * entry costs one {@code ConcurrentHashMap} node plus its table slot — ≈38 bytes, ≈385 KB at
     * this ceiling, against an event ring of 2^18 slots whose entries are orders of magnitude
     * larger. The bound is not about that memory: a request-scoped stack is dropped whole by {@code
     * reset()}. It exists for the thread that never resets — a daemon loop dispatching async work
     * for the life of the process — where an unbounded set is a genuine leak.
     */
    private static final int MAX_ADOPTED_SPANS = 10_000;

    /** W3C trace-flags with bit 0 set — a trace this process is recording. */
    private static final int SAMPLED = 1;

    private final int maxAdoptedSpans;
    private final AtomicLong refusedScopes = new AtomicLong();
    private final AtomicLong refusedSpans = new AtomicLong();

    TraceStack() {
      this(MAX_ADOPTED_SPANS);
    }

    /** Test seam: the cap is a boundary condition, reachable in a unit test only by lowering it. */
    TraceStack(int maxAdoptedSpans) {
      this.maxAdoptedSpans = maxAdoptedSpans;
    }

    private final List<SpanId> activeStack = new ArrayList<>();

    /**
     * Every span this stack created. Concurrent because the origin thread reads a live child's copy
     * of this set at capture time, while the worker that owns the child is still pushing into it.
     */
    private final Set<SpanId> knownSpanIds = ConcurrentHashMap.newKeySet();

    /** Written by worker threads at scope close, read here — hence concurrent, hence lazy. */
    private volatile Set<SpanId> adoptedSpanIds;

    /**
     * Stacks currently activated from a snapshot of this one, weakly held: a worker whose scope
     * never closes must not keep its stack alive through this registry, and a cleared entry simply
     * contributes nothing. Written by worker threads at activation, read here at capture — hence
     * concurrent, hence lazy: most stacks never spawn one.
     */
    private volatile Set<WeakReference<TraceStack>> liveChildren;

    /**
     * Whether the request that owns this stack has ended.
     *
     * <p>Set once by {@link #close()}, from {@code reset()}. It is what makes async end-of-life
     * deterministic: a weak reference only says whether the garbage collector has got here yet,
     * which is not a request lifecycle, so a worker finishing after {@code reset()} used to adopt
     * into a stack nobody would ever capture from and leave its spans in the shared maps forever.
     */
    private volatile boolean closed;

    private TraceId traceId;
    private TraceAnchor traceAnchor;
    private int traceFlags = SAMPLED;
    private SpanId scopedParentSpanId;
    private SpanId snapshotParentSpanId;
    private SpanId remoteParentSpanId;
    private boolean fromSnapshot;
    private String storyId;
    private String chapterId;
    private RequestMetadata metadata = RequestMetadata.EMPTY;

    TraceId traceId() {
      return traceId;
    }

    void setTraceId(TraceId traceId) {
      if (traceId == null) {
        throw new IllegalArgumentException("TraceId is required");
      }
      this.traceId = traceId;
      // Anchored where the trace (or its local segment, for adopted/propagated ids) starts.
      // Any self-consistent millis/nanos pair maps correctly; anchoring near the events keeps
      // NTP drift bounded by trace duration instead of process uptime.
      if (traceAnchor == null) {
        traceAnchor = TraceAnchor.now();
      }
    }

    TraceAnchor traceAnchor() {
      return traceAnchor;
    }

    /**
     * W3C trace-flags for this trace. Defaults to sampled: NarrativeTrace recorded the span, so
     * telling a downstream service otherwise would ask it to drop work we kept. An adopted {@code
     * traceparent} overwrites it — upstream owns the sampling decision it made.
     */
    int traceFlags() {
      return traceFlags;
    }

    void setTraceFlags(int traceFlags) {
      this.traceFlags = traceFlags;
    }

    /** Parent span from an inbound {@code traceparent}; parents this service's root span only. */
    SpanId remoteParentSpanId() {
      return remoteParentSpanId;
    }

    void setRemoteParentSpanId(SpanId spanId) {
      this.remoteParentSpanId = spanId;
    }

    RequestMetadata metadata() {
      return metadata;
    }

    void setMetadata(RequestMetadata metadata) {
      this.metadata = metadata;
    }

    String storyId() {
      return storyId;
    }

    String chapterId() {
      return chapterId;
    }

    /**
     * Sets storyId and chapterId lazily from the first root-level enter. Only sets once —
     * subsequent root enters do not overwrite.
     */
    void deriveStoryIdIfAbsent(String className, String methodName) {
      if (className == null || className.isBlank()) {
        throw new IllegalArgumentException("Class name is required");
      }
      if (methodName == null || methodName.isBlank()) {
        throw new IllegalArgumentException("Method name is required");
      }

      if (storyId == null) {
        storyId = className + "." + methodName;
        chapterId = storyId;

        assert storyId != null : "Postcondition: storyId must be set after deriveStoryIdIfAbsent()";
        assert chapterId != null
            : "Postcondition: chapterId must be set after deriveStoryIdIfAbsent()";
        assert chapterId.equals(storyId)
            : "Postcondition: chapterId must equal storyId after first derivation";
      }
    }

    /**
     * Takes over the spans a worker thread created under a snapshot of this stack. Called from that
     * worker thread, so the set is concurrent and created on first use — most stacks never adopt.
     *
     * <p>All-or-nothing by design: a batch that would cross the cap is refused whole and counted.
     * Adopting a prefix of it would strand children whose parent stayed out, and {@code
     * TraceTreeBuilder} promotes a parentless node to a root — so the artifact would assert a call
     * graph that never happened, differently on every run (the batch arrives as a hash-ordered
     * set). An incomplete trace is honest; a wrong-shaped one is not.
     *
     * @param spanIds the worker's reportable spans
     * @return {@code true} when this stack took them over, {@code false} when it refused — the
     *     caller must then discard them, because nothing else can report them. A closed stack
     *     refuses everything, and is the only refusal that is not counted here: the request ended
     *     rather than overflowed, and the context counts that as a discard instead.
     */
    synchronized boolean adopt(Set<SpanId> spanIds) {
      if (spanIds.isEmpty()) {
        return true;
      }
      if (closed) {
        return false;
      }
      if (adoptedSpanIds == null) {
        adoptedSpanIds = ConcurrentHashMap.newKeySet();
      }
      if (adoptedSpanIds.size() + spanIds.size() > maxAdoptedSpans) {
        refusedScopes.incrementAndGet();
        refusedSpans.addAndGet(spanIds.size());
        return false;
      }
      adoptedSpanIds.addAll(spanIds);
      return true;
    }

    /**
     * Ends this stack's life: the request is over, and nothing more may be adopted into it.
     *
     * <p>Idempotent, and deliberately one-way — a stack is never reopened, because the thread that
     * reset gets a fresh one on its next traced call.
     */
    void close() {
      closed = true;
    }

    /** Whether {@link #close()} has run, i.e. the owning request has ended. */
    boolean isClosed() {
      return closed;
    }

    /**
     * Publishes a worker's stack to this one for the lifetime of its scope, so its spans are
     * visible here as soon as they are published instead of only at scope close.
     *
     * <p>Bounded by the same ceiling as adoption, and for the same reason — the thread that never
     * resets. A refused registration loses nothing: the worker's spans still arrive through {@link
     * #adopt}, just at scope close, so it is not counted as a refusal.
     *
     * @param child the freshly activated child stack, never this stack
     * @return the handle to hand back to {@link #unregisterLiveChild}, or {@code null} if refused
     */
    @SuppressWarnings(
        "PMD.CompareObjectsWithEquals") // identity is the point: no stack adopts itself
    synchronized WeakReference<TraceStack> registerLiveChild(TraceStack child) {
      if (child == null) {
        throw new IllegalArgumentException("Child stack is required");
      }
      if (child == this) {
        throw new IllegalArgumentException("A stack cannot be its own live child");
      }
      if (liveChildren == null) {
        liveChildren = ConcurrentHashMap.newKeySet();
      }
      if (liveChildren.size() >= maxAdoptedSpans) {
        return null;
      }
      var registration = new WeakReference<>(child);
      liveChildren.add(registration);
      return registration;
    }

    /** Ends a live registration. A {@code null} handle is a registration the cap refused. */
    void unregisterLiveChild(WeakReference<TraceStack> registration) {
      var children = liveChildren;
      if (registration != null && children != null) {
        children.remove(registration);
      }
    }

    /**
     * Everything this stack can answer for right now: the spans it created, the ones it has already
     * adopted from finished children, and everything its live children can answer for.
     *
     * <p>This is the one definition both directions use — {@link #liveChildSpanIds} reads it of a
     * live child, and scope close hands exactly it over — so a call can never be visible while a
     * scope is open and absent after it closes.
     *
     * <p><b>@threadSafety</b> The live children are read <em>before</em> the adopted spans, and
     * that order is load-bearing: it is the mirror of the order scope close writes in, which is
     * adopt and then unregister. A reader that unions in the same direction as the writer can miss
     * both — it reads the adopted set before the hand-over adds to it, then reads the registry
     * after the hand-over removed the child — and the worker's whole subtree drops out of a
     * capture. Reading in the opposite direction makes that impossible: observing the child gone
     * from the registry means the adoption that preceded its removal is already visible to the read
     * that follows. Measured at 0.12% of samples before the order was fixed (2026-09-01).
     */
    Set<SpanId> reportableSpanIds() {
      var ids = new HashSet<>(knownSpanIds);
      ids.addAll(liveChildSpanIds());
      ids.addAll(adoptedSpanIds());
      return ids;
    }

    /**
     * What every live child can answer for, transitively — a chain of async hops reaches the origin
     * whole, rather than stopping at the first worker that dispatched further work.
     *
     * <p>Cleared registrations are pruned as they are found: a worker that dies without closing its
     * scope must not hold a slot against the ceiling forever.
     *
     * <p><b>@llmNote</b> The mutual recursion with {@link #reportableSpanIds} cannot cycle: a live
     * child is always a stack freshly created by {@code activate()} and registered with exactly one
     * origin, so the registry is a forest of new nodes and never contains a back edge. Depth is the
     * nesting depth of *simultaneously open* async scopes, which is bounded by live threads.
     */
    Set<SpanId> liveChildSpanIds() {
      var children = liveChildren;
      if (children == null) {
        return Set.of();
      }
      var ids = new HashSet<SpanId>();
      for (var registration : children) {
        TraceStack child = registration.get();
        if (child == null) {
          children.remove(registration);
        } else {
          ids.addAll(child.reportableSpanIds());
        }
      }
      return ids;
    }

    /** Worker scopes whose spans were refused whole because the cap had no room for them. */
    long refusedScopeCount() {
      return refusedScopes.get();
    }

    /** Spans lost to those refusals — the number a reader of the trace is missing. */
    long refusedSpanCount() {
      return refusedSpans.get();
    }

    Set<SpanId> adoptedSpanIds() {
      var adopted = adoptedSpanIds;
      return adopted == null ? Set.of() : adopted;
    }

    SpanId scopedParentSpanId() {
      return scopedParentSpanId;
    }

    void setScopedParentSpanId(SpanId spanId) {
      this.scopedParentSpanId = spanId;
    }

    SpanId snapshotParentSpanId() {
      return snapshotParentSpanId;
    }

    /** True for a stack created by activating a snapshot — the async boundary, parent or not. */
    boolean fromSnapshot() {
      return fromSnapshot;
    }

    void markFromSnapshot() {
      this.fromSnapshot = true;
    }

    void setSnapshotParentSpanId(SpanId spanId) {
      this.snapshotParentSpanId = spanId;
    }

    SpanId peekActive() {
      return activeStack.isEmpty() ? null : activeStack.get(activeStack.size() - 1);
    }

    void pushActive(SpanId spanId) {
      if (spanId == null) {
        throw new IllegalArgumentException("SpanId is required");
      }

      int oldSize = activeStack.size();

      activeStack.add(spanId);
      knownSpanIds.add(spanId);

      assert activeStack.size() == oldSize + 1
          : "Postcondition: activeStack size must increase by one after pushActive()";
      assert knownSpanIds.contains(spanId)
          : "Postcondition: pushed spanId must be in knownSpanIds after pushActive()";
    }

    boolean isEmpty() {
      return activeStack.isEmpty();
    }

    void detach(SpanId spanId) {
      activeStack.remove(spanId);
    }

    SpanId popActive() {
      if (activeStack.isEmpty()) {
        throw new IllegalStateException("Cannot pop from empty stack");
      }
      return activeStack.remove(activeStack.size() - 1);
    }

    Set<SpanId> knownSpanIds() {
      return Set.copyOf(knownSpanIds);
    }

    // ---------------------------------------------------------------
    // Invariant — checked by JUnit extension at setUp/tearDown
    // ---------------------------------------------------------------

    boolean invariant() {
      // Category 1 — collections must not be null
      if (activeStack == null) return false;
      if (knownSpanIds == null) return false;

      // Category 1 — no null elements in collections
      if (activeStack.stream().anyMatch(s -> s == null)) return false;
      if (knownSpanIds.stream().anyMatch(s -> s == null)) return false;

      // Category 3 — every active span must be in the known set (accumulated superset)
      if (!knownSpanIds.containsAll(activeStack)) return false;

      // Category 4 — known spans must be at least as large as active stack
      if (knownSpanIds.size() < activeStack.size()) return false;

      // Live children carry no state of this stack's own: registrations are non-null and never
      // this stack (registerLiveChild guards both), and cleared ones are pruned when read.

      // Category 5 — storyId and chapterId are set together or both null
      if (storyId == null && chapterId != null) return false;
      if (storyId != null && chapterId == null) return false;

      // Category 5 — when set, chapterId must equal storyId (derived together)
      if (storyId != null && !chapterId.equals(storyId)) return false;

      return true;
    }
  }
}
