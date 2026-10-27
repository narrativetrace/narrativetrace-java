/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.shop;

/** The traced work a worker thread does; the returned text carries the request's own value. */
public class InMemoryInventory implements Inventory {

  @Override
  public String reserve(String ref) {
    return "reserved " + ref + " on " + Thread.currentThread().getName();
  }
}
