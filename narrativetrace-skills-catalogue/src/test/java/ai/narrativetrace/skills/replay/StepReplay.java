/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.replay;

/** One replayed step's outcome: whether anything ran, and whether it exited clean. */
public record StepReplay(String stepTitle, boolean ran, boolean ok, String detail) {}
