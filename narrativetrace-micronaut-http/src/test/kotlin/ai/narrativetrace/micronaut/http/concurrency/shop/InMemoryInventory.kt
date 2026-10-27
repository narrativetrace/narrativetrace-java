/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.concurrency.shop

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@Singleton
@Requires(env = ["mt-matrix"])
class InMemoryInventory : Inventory {
    override fun reserve(ref: String): String = "reserved $ref on ${Thread.currentThread().name}"
}
