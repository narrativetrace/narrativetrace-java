/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.concurrency.shop

import ai.narrativetrace.core.context.FireAndForgetGroup
import ai.narrativetrace.core.context.NarrativeContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.function.Supplier

/**
 * Hand-over by FireAndForgetGroup: the launcher node stays in the request's trace, the background
 * work is read from the group afterwards. The checkout waits for the worker only so a test can read
 * the group deterministically; the export never depends on it.
 */
@Singleton
@Requires(env = ["mt-matrix"], property = "mt.handover", value = "fire-and-forget")
class FireAndForgetCheckout(
    private val context: NarrativeContext,
    private val inventory: Inventory,
    private val groups: LaunchedGroups,
) : Checkout {
    private val pool = Workers.pool(32)

    override fun place(
        requestId: String,
        skus: List<String>,
    ): String {
        val group = FireAndForgetGroup.create(context, "Checkout")
        groups.byRequest[requestId] = group
        val pending =
            skus.map { sku ->
                val wrapped = group.wrap(Supplier { inventory.reserve("$requestId:$sku") }) // on the request thread
                pool.submit { wrapped.get() }
            }
        Workers.awaitAll(pending)
        return requestId
    }
}

/** Where launched groups are kept for the test to read; a plain class, so the bean listener leaves it alone. */
@Singleton
@Requires(env = ["mt-matrix"])
class LaunchedGroups {
    val byRequest = java.util.concurrent.ConcurrentHashMap<String, FireAndForgetGroup>()
}
