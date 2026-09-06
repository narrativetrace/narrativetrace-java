/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.benchmarks.services;

import ai.narrativetrace.benchmarks.PlainService;

public class PlainServiceImpl implements PlainService {
  @Override
  public String execute(String input) {
    return "result:" + input;
  }
}
