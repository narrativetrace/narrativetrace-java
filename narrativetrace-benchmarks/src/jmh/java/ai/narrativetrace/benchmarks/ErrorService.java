/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.benchmarks;

import ai.narrativetrace.api.annotation.OnError;

public interface ErrorService {
  @OnError(exception = RuntimeException.class, value = "Failed for {input}")
  String execute(String input);
}
