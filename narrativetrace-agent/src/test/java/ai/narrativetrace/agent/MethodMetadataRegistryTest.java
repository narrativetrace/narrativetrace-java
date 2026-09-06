/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The instrumentation-site table, on its own.
 *
 * <p>INTENT: The table is what makes the hoist sound. It hands out one id per registration and
 * never renumbers, so nothing a caller could hold — a name, a descriptor, a signature two loaders
 * agree on — can reach another method's constants. These tests pin that, the growth past the
 * initial capacity, and the "an id I did not mint is absent, not somebody else's" reading.
 */
class MethodMetadataRegistryTest {

  private MethodMetadataRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new MethodMetadataRegistry();
    assertThat(registry.invariant()).isTrue();
  }

  private static AgentMethodMetadata metadataFor(String methodName) {
    return AgentMethodMetadata.bare("com.acme.Service", methodName, "()V");
  }

  @Test
  void aRegisteredMethodIsFoundUnderTheIdItWasGiven() {
    var metadata = metadataFor("run");

    assertThat(registry.get(registry.register(metadata))).isSameAs(metadata);
  }

  @Test
  void everyRegistrationGetsItsOwnIdEvenForIdenticalConstants() {
    int first = registry.register(metadataFor("run"));
    int second = registry.register(metadataFor("run"));

    assertThat(second).isNotEqualTo(first);
    assertThat(registry.size()).isEqualTo(2);
  }

  @Test
  void idsAreHandedOutInOrderFromZero() {
    assertThat(registry.register(metadataFor("a"))).isZero();
    assertThat(registry.register(metadataFor("b"))).isEqualTo(1);
  }

  @Test
  void growingPastTheInitialCapacityKeepsEveryEarlierEntryReadable() {
    var first = metadataFor("first");
    int firstId = registry.register(first);
    for (int i = 0; i < 500; i++) {
      registry.register(metadataFor("filler" + i));
    }
    var last = metadataFor("last");
    int lastId = registry.register(last);

    assertThat(registry.get(firstId)).isSameAs(first);
    assertThat(registry.get(lastId)).isSameAs(last);
    assertThat(registry.size()).isEqualTo(502);
    assertThat(registry.invariant()).isTrue();
  }

  @Test
  void anIdThisRegistryNeverMintedIsAbsent() {
    registry.register(metadataFor("run"));

    assertThat(registry.get(1)).isNull();
    assertThat(registry.get(Integer.MAX_VALUE)).isNull();
    assertThat(registry.get(-1)).isNull();
    assertThat(registry.get(Integer.MIN_VALUE)).isNull();
  }

  @Test
  void anEmptyRegistryHasNothingUnderAnyId() {
    assertThat(registry.get(0)).isNull();
    assertThat(registry.size()).isZero();
  }

  @Test
  void registeringNothingIsRejectedRatherThanStored() {
    assertThatThrownBy(() -> registry.register(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("metadata");
    assertThat(registry.size()).isZero();
    assertThat(registry.invariant()).isTrue();
  }

  @Test
  void concurrentRegistrationsGetDistinctIdsThatAllResolve() throws Exception {
    var threads = new Thread[4];
    var ids = new int[4][50];
    for (int t = 0; t < threads.length; t++) {
      final int index = t;
      threads[t] =
          new Thread(
              () -> {
                for (int i = 0; i < 50; i++) {
                  ids[index][i] = registry.register(metadataFor("m" + index + "-" + i));
                }
              });
    }
    for (var thread : threads) thread.start();
    for (var thread : threads) thread.join();

    var seen = new java.util.HashSet<Integer>();
    for (var perThread : ids) {
      for (int id : perThread) {
        assertThat(registry.get(id)).isNotNull();
        assertThat(seen.add(id)).isTrue();
      }
    }
    assertThat(registry.size()).isEqualTo(200);
    assertThat(registry.invariant()).isTrue();
  }
}
