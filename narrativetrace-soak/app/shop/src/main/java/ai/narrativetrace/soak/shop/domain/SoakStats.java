/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

/**
 * {@code GET /soak/stats} shape — what P2's oracles read every 30s (see {@code compose.yaml}'s
 * sampler service).
 *
 * @param droppedEventCount {@code DualPathPipeline}'s process-wide drop count, read through the
 *     public {@code NarrativeContext#traceLoss()} API (never zero under load-shedding pressure)
 * @param bufferFill the buffered path's current fill level — {@code null}: not exposed by any
 *     public API, so P1 reports its absence honestly rather than reaching into pipeline internals
 * @param heapUsedAfterLastGcBytes summed {@code MemoryPoolMXBean.getCollectionUsage()} across every
 *     pool that reports one — usage as of the most recent collection, not a live sample
 * @param liveThreadCount current JVM thread count
 * @param openFileDescriptorCount {@code -1} when the JVM/OS does not expose this (non-Unix)
 * @param uptimeMillis JVM uptime
 * @param requestCount total requests served
 * @param exceptionCount edge + business + poison, combined
 * @param edgeRejectionCount 400s that never reached the domain (Bean Validation failures)
 * @param businessFailureCount reported domain outcomes (unknown customer, out-of-stock, payment
 *     declined, order not found, already cancelled)
 * @param poisonExceptionCount exceptions the deliberately open quantity gate caused
 */
public record SoakStats(
    long droppedEventCount,
    Long bufferFill,
    long heapUsedAfterLastGcBytes,
    int liveThreadCount,
    long openFileDescriptorCount,
    long uptimeMillis,
    long requestCount,
    long exceptionCount,
    long edgeRejectionCount,
    long businessFailureCount,
    long poisonExceptionCount) {}
