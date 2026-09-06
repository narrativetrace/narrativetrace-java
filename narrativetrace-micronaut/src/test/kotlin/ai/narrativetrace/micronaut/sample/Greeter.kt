/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.sample

/** Contract inside the base package used by the package-boundary tests. */
interface Greeter {
    fun greet(name: String): String
}

/** Implementation whose package IS the base package. */
class SampleGreeter : Greeter {
    override fun greet(name: String): String = "Hello, $name"
}

/** Implementation inside the base package exposing only a sibling package's contract. */
class OutwardFacing : ai.narrativetrace.micronaut.samplex.SiblingContract {
    override fun ping(): String = "pong"
}
