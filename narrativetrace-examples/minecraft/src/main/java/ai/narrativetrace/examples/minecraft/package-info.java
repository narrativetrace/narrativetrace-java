/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Tutorial example: comparing refactored and unrefactored game-domain traces.
 *
 * <p>This package exists to make one point obvious: tracing gets dramatically more useful when code
 * names communicate intent. Both Minecraft scenarios perform comparable work, but one uses
 * domain-rich names while the other hides the same behavior behind generic labels.
 *
 * <p>Start with {@link ai.narrativetrace.examples.minecraft.MinecraftExample}. It runs the
 * refactored and unrefactored versions back to back so developers can compare the resulting traces
 * directly.
 *
 * <p>INTENT: Use this package as a naming-and-observability teaching aid, not as a gameplay sample.
 */
package ai.narrativetrace.examples.minecraft;
