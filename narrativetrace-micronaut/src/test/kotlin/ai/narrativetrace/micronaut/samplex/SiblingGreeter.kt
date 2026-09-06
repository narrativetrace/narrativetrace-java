/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.samplex

import ai.narrativetrace.micronaut.sample.Greeter

/** Contract in a sibling package that merely shares the base package's prefix. */
interface SiblingContract {
    fun ping(): String
}

/** Implementation in a sibling package that merely shares the base package's prefix. */
class SiblingGreeter :
    Greeter,
    SiblingContract {
    override fun greet(name: String): String = "Hello from next door, $name"

    override fun ping(): String = "pong"
}
