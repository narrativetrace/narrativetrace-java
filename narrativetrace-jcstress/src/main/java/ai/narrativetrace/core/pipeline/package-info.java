/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Concurrency specifications for the low-level event pipeline.
 *
 * <p>INTENT: this module complements ordinary unit tests with {@code jcstress} scenarios that
 * exercise publication, polling, overwrite, and multi-producer races in {@code
 * ai.narrativetrace.core.pipeline}. The classes in this package are executable specifications for
 * memory-visibility and loss-tolerance guarantees that are difficult to prove with deterministic
 * tests alone.
 *
 * <p>Read this package when you need to understand which concurrent outcomes are acceptable,
 * forbidden, or merely interesting during ring-buffer contention.
 *
 * <p>Typical workflow:
 *
 * <ol>
 *   <li>Read the scenario Javadoc to understand the concurrency contract being asserted.
 *   <li>Inspect the {@code @Outcome} matrix to see which observations are acceptable.
 *   <li>Treat forbidden outcomes as evidence of a publication or corruption bug.
 * </ol>
 *
 * <p><b>@layer</b> Verification
 *
 * <p><b>@pattern</b> Lock-free ring-buffer validation
 *
 * <p><b>@llmNote</b> The package intentionally documents weak guarantees too. Some scenarios permit
 * event loss under overwrite pressure, but none permit corrupted event payloads or permanent loss
 * in single-event publication races.
 *
 * <p><b>@edgeCase</b> A scenario may classify an outcome as {@code ACCEPTABLE_INTERESTING} when the
 * implementation is intentionally best-effort rather than linearizable under overflow.
 */
package ai.narrativetrace.core.pipeline;
