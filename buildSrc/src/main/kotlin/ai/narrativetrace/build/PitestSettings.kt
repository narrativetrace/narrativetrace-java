/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

/**
 * INTENT: the nightly runs PIT inside a memory ceiling; minion count is the knob. Each PIT minion
 * is a full JVM running beside the Gradle build daemon and the Kotlin compile daemons in the same
 * container, so a fixed `threads = 4` in every pitest block spends memory the nightly's cgroup does
 * not always have — a ceiling raise is not the fix (standing rule), asking for fewer minions is.
 *
 * [threads] is the one place that request is resolved, so both pitest blocks in the root build
 * script read the SAME rule and cannot drift.
 */
object PitestSettings {

    /** The Gradle property `threads` is read from — `-P$PROPERTY_NAME=<n>` overrides the default. */
    const val PROPERTY_NAME = "narrativetrace.pitest.threads"

    /**
     * The PIT minion-thread count: [default] when [propertyValue] is null or blank (nothing changes
     * for developers and CI, which never set the property); the parsed value when [propertyValue] is
     * a positive integer (what the nightly passes to stay inside its ceiling); otherwise an
     * [IllegalArgumentException] naming [PROPERTY_NAME] and the offending value, never a silent
     * fallback to [default] — a typo'd override must fail loud, not quietly run at the wrong
     * concurrency.
     */
    fun threads(propertyValue: String?, default: Int): Int {
        if (propertyValue == null || propertyValue.isBlank()) {
            return default
        }
        val parsed = propertyValue.trim().toIntOrNull()
        if (parsed == null || parsed <= 0) {
            throw IllegalArgumentException(
                "$PROPERTY_NAME must be a positive integer, was '$propertyValue'"
            )
        }
        return parsed
    }
}
