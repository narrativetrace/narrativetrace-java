/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http.sample

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/** Implementation behind [Greeter]; both sit in a base package the smoke test instruments. */
@Singleton
@Requires(env = ["embedded-smoke"])
class SampleGreeter : Greeter {
    override fun greet(name: String): String = "hello $name"
}
