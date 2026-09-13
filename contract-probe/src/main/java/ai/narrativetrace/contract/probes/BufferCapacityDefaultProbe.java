/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import ai.narrativetrace.core.pipeline.BufferedEventConsumer;

/**
 * {@code reflectable-default}: no execution needed at all — the published public constant IS the
 * documented default, read directly.
 */
public final class BufferCapacityDefaultProbe {

  private BufferCapacityDefaultProbe() {}

  public static String observe() {
    return String.valueOf(BufferedEventConsumer.DEFAULT_CAPACITY);
  }
}
