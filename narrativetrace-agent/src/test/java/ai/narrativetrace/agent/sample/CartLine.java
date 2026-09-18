/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

/**
 * Fixture record whose accessors are woven like any other method when this class is transformed.
 *
 * <p>Used by the render-reentrancy tests: a record passed as a parameter or returned by a woven
 * method has its components read reflectively by {@code ValueRenderer} at capture time — and when
 * this class is itself woven, those reflective reads execute instrumented bytecode.
 */
public record CartLine(String productId, int quantity) {}
