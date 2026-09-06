/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.sample.nested

import ai.narrativetrace.micronaut.sample.Greeter

/** Implementation in a genuine sub-package of the base package. */
class NestedGreeter : Greeter {
    override fun greet(name: String): String = "Hello from below, $name"
}
