/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reactive-streams subscriber that stores incoming trace events.
 *
 * <p>INTENT: Use this in tests or stream-driven integrations that already expose a {@link
 * Flow.Publisher} of {@code TraceEvent}s and need deterministic waiting utilities.
 */
public final class EventStoreSubscriber implements Flow.Subscriber<TraceEvent> {

  private final EventStore store = new EventStore();
  private final AtomicInteger eventCount = new AtomicInteger();
  private final List<AwaitEntry> awaiters = new CopyOnWriteArrayList<>();
  private final CompletableFuture<Void> completeFuture = new CompletableFuture<>();

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    subscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(TraceEvent event) {
    store.add(event);
    int count = eventCount.incrementAndGet();
    for (var entry : awaiters) {
      if (count >= entry.target) {
        entry.future.complete(null);
      }
    }
  }

  @Override
  public void onError(Throwable throwable) {}

  @Override
  public void onComplete() {
    completeFuture.complete(null);
  }

  public List<TraceEvent> events() {
    return store.events();
  }

  /** Returns a future that completes when the upstream publisher signals {@code onComplete}. */
  public CompletableFuture<Void> awaitComplete() {
    return completeFuture;
  }

  /**
   * Returns a future that completes once at least {@code count} events have been observed.
   *
   * <p><b>@edgeCase</b> If the threshold was already reached, the returned future is already
   * completed.
   */
  public CompletableFuture<Void> awaitEvents(int count) {
    if (eventCount.get() >= count) {
      return CompletableFuture.completedFuture(null);
    }
    var entry = new AwaitEntry(count);
    awaiters.add(entry);
    if (eventCount.get() >= count) {
      entry.future.complete(null);
    }
    return entry.future;
  }

  private static final class AwaitEntry {
    final int target;
    final CompletableFuture<Void> future = new CompletableFuture<>();

    AwaitEntry(int target) {
      this.target = target;
    }
  }
}
