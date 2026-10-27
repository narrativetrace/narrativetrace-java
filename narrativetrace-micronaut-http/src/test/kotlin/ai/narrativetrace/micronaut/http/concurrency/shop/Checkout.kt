/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.concurrency.shop

/**
 * The request-side service: called on the request's thread, it hands its work to others. The span it
 * opens is the "submitting span" every worker's calls must nest under.
 */
interface Checkout {
    fun place(
        requestId: String,
        skus: List<String>,
    ): String
}
