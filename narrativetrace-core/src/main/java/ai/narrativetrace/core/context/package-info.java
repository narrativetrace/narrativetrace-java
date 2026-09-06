/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Trace context management, cross-thread propagation, and concurrency helpers.
 *
 * <p>{@link ai.narrativetrace.core.context.NarrativeContext} is the capture-facing API used by the
 * proxy and agent. Implementations record method entry and exit as append-only events and expose
 * completed traces as immutable trees. {@link
 * ai.narrativetrace.core.context.ThreadLocalNarrativeContext} is the default implementation: it
 * keeps per-thread stack state in a {@link ThreadLocal} while sharing one event pipeline across the
 * context instance.
 *
 * <p>{@link ai.narrativetrace.core.context.ContextSnapshot} carries the current trace id and parent
 * span into another thread. Activating a snapshot creates a fresh local stack for that thread, so
 * concurrent work can be recorded independently and later stitched back together.
 *
 * <h2>Concurrency coordination</h2>
 *
 * <p>{@link ai.narrativetrace.core.context.ForkGroup} and {@link
 * ai.narrativetrace.core.context.FireAndForgetGroup} coordinate concurrent work inside one logical
 * trace. Both rely on {@link ai.narrativetrace.core.context.NarrativeContext#snapshot() snapshot}
 * for propagation and on {@link ai.narrativetrace.core.context.NarrativeContext#emitTraceNode
 * emitTraceNode} when replaying collected child trees back into the event stream.
 *
 * <p>Concurrent siblings are linked by a shared {@code groupId} on their {@link
 * ai.narrativetrace.api.event.ConcurrencyInfo}. Renderers use that metadata to group them visually:
 *
 * <pre>{@code
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │ Parent.orchestrate()                                                            │
 * │─────────────────────────────────────────────────────────────────────────────────│
 * │ ├── InventoryService.reserve()    concurrency: null                             │
 * │ ├── DiscountService.calculate()   concurrency: {groupId:"fork-1", FORK_JOIN}    │
 * │ ├── ShippingService.estimate()    concurrency: {groupId:"fork-1", FORK_JOIN}    │
 * │ └── PaymentService.charge()       concurrency: null                             │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 * }</pre>
 *
 * <p><b>Fork-join</b> ({@link ai.narrativetrace.core.context.ForkGroup}): wrapped tasks capture
 * their own local roots, and the parent later calls {@code merge()} to replay those roots as
 * awaited concurrent children tagged with {@code FORK_JOIN}.
 *
 * <p><b>Fire-and-forget</b> ({@link ai.narrativetrace.core.context.FireAndForgetGroup}): a
 * synthetic launcher node is inserted into the parent immediately. Child roots remain separate but
 * carry the same {@code groupId}, so renderers can present them as background work:
 *
 * <pre>{@code
 * ┌──────────────────────────────┐    ┌──────────────────────────────┐
 * │ Parent tree                  │    │ Child tree (separate)        │
 * │──────────────────────────────│    │──────────────────────────────│
 * │ ├── OrderService.placeOrder()│    │ └── NotificationService      │
 * │ └── launcher {groupId:"fanf-1"}───►|     .send()                  │
 * │                              │    │     {groupId:"fanf-1"}       │
 * └──────────────────────────────┘    └──────────────────────────────┘
 * }</pre>
 *
 * <p>The {@code groupId} is the only structural link between concurrent nodes. No live object graph
 * is shared across threads; coordination happens through immutable trace nodes and event replay.
 *
 * <p>This package has zero external dependencies.
 */
package ai.narrativetrace.core.context;
