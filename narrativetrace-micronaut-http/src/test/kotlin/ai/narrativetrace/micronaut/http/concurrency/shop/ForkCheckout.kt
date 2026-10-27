/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.concurrency.shop

import ai.narrativetrace.core.context.ForkGroup
import ai.narrativetrace.core.context.NarrativeContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.concurrent.Callable

/** Hand-over by ForkGroup: wrap each task, join, merge under the submitting span. */
@Singleton
@Requires(env = ["mt-matrix"], property = "mt.handover", value = "fork")
class ForkCheckout(
    private val context: NarrativeContext,
    private val inventory: Inventory,
) : Checkout {
    private val pool = Workers.pool(32)

    override fun place(
        requestId: String,
        skus: List<String>,
    ): String {
        val group = ForkGroup.create(context)
        val pending =
            skus.map { sku ->
                val task = Callable { inventory.reserve("$requestId:$sku") }
                pool.submit(group.wrap(task))
            }
        Workers.awaitAll(pending)
        group.merge()
        return requestId
    }
}
