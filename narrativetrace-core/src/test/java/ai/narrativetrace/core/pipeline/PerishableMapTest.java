/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PerishableMapTest {

  @Test
  void putAndGetReturnStoredValue() {
    var map = new PerishableMap<String, String>(10, Duration.ofHours(1), v -> {});
    map.put("key", "value");
    assertThat(map.get("key")).isEqualTo("value");
  }

  @Test
  void removeReturnsAndDeletesEntry() {
    var map = new PerishableMap<String, String>(10, Duration.ofHours(1), v -> {});
    map.put("key", "value");
    assertThat(map.remove("key")).isEqualTo("value");
    assertThat(map.get("key")).isNull();
  }

  @Test
  void removeReturnsNullForMissingKey() {
    var map = new PerishableMap<String, String>(10, Duration.ofHours(1), v -> {});
    assertThat(map.remove("missing")).isNull();
  }

  @Test
  void getReturnsNullForMissingKey() {
    var map = new PerishableMap<String, String>(10, Duration.ofHours(1), v -> {});
    assertThat(map.get("missing")).isNull();
  }

  @Test
  void sizeReflectsEntryCount() {
    var map = new PerishableMap<String, String>(10, Duration.ofHours(1), v -> {});
    assertThat(map.size()).isZero();
    map.put("a", "1");
    map.put("b", "2");
    assertThat(map.size()).isEqualTo(2);
    map.remove("a");
    assertThat(map.size()).isEqualTo(1);
  }

  @Test
  void evictsOldestWhenCapacityExceeded() {
    var evicted = new ArrayList<String>();
    var map = new PerishableMap<String, String>(2, Duration.ofHours(1), evicted::add);
    map.put("first", "1");
    map.put("second", "2");
    map.put("third", "3");

    assertThat(evicted).containsExactly("1");
    assertThat(map.size()).isEqualTo(2);
    assertThat(map.get("first")).isNull();
    assertThat(map.get("second")).isEqualTo("2");
    assertThat(map.get("third")).isEqualTo("3");
  }

  @Test
  void evictsMultipleWhenCapacityIsOne() {
    var evicted = new ArrayList<String>();
    var map = new PerishableMap<String, String>(1, Duration.ofHours(1), evicted::add);
    map.put("a", "1");
    map.put("b", "2");
    map.put("c", "3");

    assertThat(evicted).containsExactly("1", "2");
    assertThat(map.size()).isEqualTo(1);
    assertThat(map.get("c")).isEqualTo("3");
  }

  @Test
  void evictsExpiredEntriesOnPut() {
    var evicted = new ArrayList<String>();
    var clock = new AtomicLong(0L);
    var map =
        new PerishableMap<String, String>(10, Duration.ofNanos(100), evicted::add, clock::get);
    clock.set(0);
    map.put("old", "stale");
    clock.set(200);
    map.put("new", "fresh");

    assertThat(evicted).containsExactly("stale");
    assertThat(map.get("old")).isNull();
    assertThat(map.get("new")).isEqualTo("fresh");
  }

  @Test
  void nonExpiredEntriesSurvive() {
    var evicted = new ArrayList<String>();
    var clock = new AtomicLong(0L);
    var map =
        new PerishableMap<String, String>(10, Duration.ofNanos(100), evicted::add, clock::get);
    clock.set(0);
    map.put("young", "alive");
    clock.set(50);
    map.put("trigger", "check");

    assertThat(evicted).isEmpty();
    assertThat(map.get("young")).isEqualTo("alive");
  }

  @Test
  void ttlAndCapacityEvictionCombined() {
    var evicted = new ArrayList<String>();
    var clock = new AtomicLong(0L);
    // Capacity 2, TTL 100ns
    var map = new PerishableMap<String, String>(2, Duration.ofNanos(100), evicted::add, clock::get);
    clock.set(0);
    map.put("a", "1");
    clock.set(50);
    map.put("b", "2");
    clock.set(150);
    // "a" is expired (age 150 > 100), "b" is not (age 100 = boundary, not expired)
    map.put("c", "3");

    assertThat(evicted).containsExactly("1");
    assertThat(map.get("a")).isNull();
    assertThat(map.get("b")).isEqualTo("2");
    assertThat(map.get("c")).isEqualTo("3");
  }

  @Test
  void evictionCallbackReceivesValue() {
    var evicted = new ArrayList<String>();
    var map = new PerishableMap<String, String>(1, Duration.ofHours(1), evicted::add);
    map.put("victim", "important-data");
    map.put("new", "replaces");

    assertThat(evicted).containsExactly("important-data");
  }

  @Test
  void putOverwritesExistingKeyWithoutEviction() {
    var evicted = new ArrayList<String>();
    var map = new PerishableMap<String, String>(2, Duration.ofHours(1), evicted::add);
    map.put("key", "v1");
    map.put("key", "v2");

    assertThat(evicted).isEmpty();
    assertThat(map.get("key")).isEqualTo("v2");
    assertThat(map.size()).isEqualTo(1);
  }

  @Test
  void zeroCapacityThrows() {
    assertThatThrownBy(() -> new PerishableMap<>(0, Duration.ofHours(1), v -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullTtlThrows() {
    assertThatThrownBy(() -> new PerishableMap<>(10, null, v -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
