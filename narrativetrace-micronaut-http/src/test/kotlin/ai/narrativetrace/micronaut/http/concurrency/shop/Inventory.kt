/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.concurrency.shop

/** The worker-side service: every handed-over task calls it with a value naming its request. */
interface Inventory {
    fun reserve(ref: String): String
}
