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
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Hand-over by Reactor with Micrometer context propagation: the work runs on another scheduler's
 * threads and the trace follows through the registered NarrativeTrace accessor.
 */
@Singleton
@Requires(env = ["mt-matrix"], property = "mt.handover", value = "reactor")
class ReactorCheckout(
    private val inventory: Inventory,
) : Checkout {
    private val scheduler = Schedulers.fromExecutorService(Workers.pool(32))

    override fun place(
        requestId: String,
        skus: List<String>,
    ): String {
        Flux
            .fromIterable(skus)
            .publishOn(scheduler)
            .map { sku -> inventory.reserve("$requestId:$sku") }
            .collectList()
            .toFuture()
            .get(30, TimeUnit.SECONDS)
        return requestId
    }
}
