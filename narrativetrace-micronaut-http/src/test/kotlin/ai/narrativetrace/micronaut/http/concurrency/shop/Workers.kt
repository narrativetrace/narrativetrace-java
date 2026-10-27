/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.concurrency.shop

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Named pool threads, so a trace can show which work ran on a worker and not on the request's thread. */
object Workers {
    const val PREFIX = "ntmt-worker-"

    fun pool(threads: Int): ExecutorService {
        val count = AtomicInteger()
        return Executors.newFixedThreadPool(threads) { r ->
            Thread(r, PREFIX + count.incrementAndGet()).also { it.isDaemon = true }
        }
    }

    fun awaitAll(futures: List<Future<*>>) = futures.forEach { it.get(30, TimeUnit.SECONDS) }
}
