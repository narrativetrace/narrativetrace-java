/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.shop;

/** The worker-side service: every handed-over task calls it with a value naming its request. */
public interface Inventory {

  String reserve(String ref);
}
