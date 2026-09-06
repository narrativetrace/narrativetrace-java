/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.sample

/** The traced boundary of the smoke sample: an interface the bean listener is willing to wrap. */
interface Greeter {
    fun greet(name: String): String
}
