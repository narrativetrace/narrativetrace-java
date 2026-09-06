/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.util.Arrays;

/**
 * The instrumentation-site table: one {@link AgentMethodMetadata} per instrumented method, found by
 * the {@code int} id the emitted call site carries.
 *
 * <p>INTENT: A method's constants belong to the site that was instrumented, not to any name or
 * signature that another site could share. An id is minted once per method per transform and never
 * reused, so two classes with the same qualified name defined by different loaders hold two ids and
 * cannot read each other's names, redaction flags or templates.
 *
 * <p><b>@llmNote</b> Registration runs on whatever thread loads a class; lookup runs on every
 * application thread that calls the instrumented method. The volatile write of {@code size} is what
 * publishes both the new entry and, after a growth, the array holding it — a reader that sees
 * {@code id < size} has already seen everything the registering thread wrote before that store.
 * Growth copies, so a reader holding the old array still reads a complete one.
 *
 * <p><b>@edgeCase</b> An id this registry never minted — bytecode instrumented by a differently
 * loaded copy of the agent — reads as absent rather than as some other method. That degrades to "no
 * trace", which is the only safe answer; narrating a call with another method's parameter names
 * would not be.
 */
final class MethodMetadataRegistry {

  private static final int INITIAL_CAPACITY = 64;

  private volatile AgentMethodMetadata[] entries = new AgentMethodMetadata[INITIAL_CAPACITY];
  private volatile int size;

  /**
   * Records one instrumented method and returns the id its call site will carry.
   *
   * @throws IllegalArgumentException if {@code metadata} is null
   */
  synchronized int register(AgentMethodMetadata metadata) {
    if (metadata == null) {
      throw new IllegalArgumentException("metadata is required");
    }
    int id = size;
    if (id == entries.length) {
      entries = Arrays.copyOf(entries, entries.length * 2);
    }
    entries[id] = metadata;
    size = id + 1;
    assert get(id) == metadata // NOPMD - identity IS what registration promises
        : "postcondition: a minted id resolves to the metadata it was minted for";
    assert invariant() : "invariant violated after register";
    return id;
  }

  /** The metadata behind {@code id}, or {@code null} when this registry never minted it. */
  AgentMethodMetadata get(int id) {
    int known = size;
    if (id < 0 || id >= known) {
      return null;
    }
    return entries[id];
  }

  /** How many methods have been registered — the next id, by construction. */
  int size() {
    return size;
  }

  /** Every id below {@code size} resolves, and {@code size} never runs past the array. */
  boolean invariant() {
    var snapshot = entries;
    int known = size;
    if (known < 0 || known > snapshot.length) {
      return false;
    }
    for (int i = 0; i < known; i++) {
      if (snapshot[i] == null) {
        return false;
      }
    }
    return true;
  }
}
