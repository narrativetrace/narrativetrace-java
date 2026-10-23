/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Bounded map with TTL-based and capacity-based eviction.
 *
 * <p>INTENT: Provide a general-purpose "perishable" map for pipeline consumers that hold
 * intermediate state (e.g., active spans awaiting completion). Entries that exceed the configured
 * TTL or that are evicted due to capacity limits are passed to an eviction callback so the caller
 * can perform cleanup (e.g., ending orphaned OTel spans with error status).
 *
 * <p><b>@llmNote</b> Eviction is lazy — it happens on {@link #put}, not on a background timer. This
 * avoids thread management complexity. The map is thread-safe via {@link ConcurrentHashMap} but
 * eviction scans are not atomic across the whole map; under high concurrency the size may briefly
 * exceed capacity by a small margin.
 *
 * <p><b>@edgeCase</b> Putting the same key again overwrites the value and resets the insertion time
 * without triggering eviction for that key.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class PerishableMap<K, V> {

  private final ConcurrentHashMap<K, TimedEntry<V>> entries = new ConcurrentHashMap<>();
  private final int maxCapacity;
  private final long ttlNanos;
  private final Consumer<V> onEvict;
  private final LongSupplier clock;

  /**
   * Creates a perishable map with the given capacity, TTL, and eviction callback.
   *
   * @param maxCapacity maximum number of entries before oldest is evicted; must be positive
   * @param ttl maximum age of an entry before it is evicted; must not be null
   * @param onEvict callback invoked with the value of each evicted entry
   */
  public PerishableMap(int maxCapacity, Duration ttl, Consumer<V> onEvict) {
    this(maxCapacity, ttl, onEvict, System::nanoTime);
  }

  /**
   * Creates a perishable map with an injectable clock for testing.
   *
   * @param maxCapacity maximum number of entries before oldest is evicted; must be positive
   * @param ttl maximum age of an entry before it is evicted; must not be null
   * @param onEvict callback invoked with the value of each evicted entry
   * @param clock nano-time supplier for TTL calculations
   */
  PerishableMap(int maxCapacity, Duration ttl, Consumer<V> onEvict, LongSupplier clock) {
    if (maxCapacity < 1) {
      throw new IllegalArgumentException("maxCapacity must be positive, got " + maxCapacity);
    }
    if (ttl == null) {
      throw new IllegalArgumentException("ttl must not be null");
    }
    this.maxCapacity = maxCapacity;
    this.ttlNanos = ttl.toNanos();
    this.onEvict = onEvict;
    this.clock = clock;
  }

  /** Inserts or overwrites an entry, evicting expired and over-capacity entries first. */
  public void put(K key, V value) {
    evictExpired();
    evictOverCapacity();
    entries.put(key, new TimedEntry<>(value, clock.getAsLong()));
  }

  /** Returns the value for the given key, or {@code null} if absent. */
  public V get(K key) {
    var entry = entries.get(key);
    return entry != null ? entry.value() : null;
  }

  /** Removes and returns the value for the given key, or {@code null} if absent. */
  public V remove(K key) {
    var entry = entries.remove(key);
    return entry != null ? entry.value() : null;
  }

  /** Returns the number of entries currently in the map. */
  public int size() {
    return entries.size();
  }

  /**
   * Removes each expired entry FIRST and calls back only when this caller won the removal, so an
   * entry is evicted exactly once however many callers (or a re-entrant callback) scan it, and a
   * replacement put under the same key is never the one removed.
   */
  private void evictExpired() {
    long now = clock.getAsLong();
    for (var entry : entries.entrySet()) {
      var examined = entry.getValue();
      if (now - examined.createdNanos() > ttlNanos && entries.remove(entry.getKey(), examined)) {
        onEvict.accept(examined.value());
      }
    }
  }

  /** Same order as {@link #evictExpired}: the entry found oldest is removed, then called back. */
  private void evictOverCapacity() {
    while (entries.size() >= maxCapacity) {
      var oldest = findOldest();
      if (oldest == null) {
        break;
      }
      if (entries.remove(oldest.getKey(), oldest.getValue())) {
        onEvict.accept(oldest.getValue().value());
      }
    }
  }

  private Map.Entry<K, TimedEntry<V>> findOldest() {
    Map.Entry<K, TimedEntry<V>> oldest = null;
    long oldestTime = Long.MAX_VALUE;
    for (var entry : entries.entrySet()) {
      if (entry.getValue().createdNanos() < oldestTime) {
        oldestTime = entry.getValue().createdNanos();
        oldest = entry;
      }
    }
    return oldest;
  }

  /** Identity equality: a replacement entry never compares equal to the one it replaced. */
  private record TimedEntry<V>(V value, long createdNanos) {
    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }
}
