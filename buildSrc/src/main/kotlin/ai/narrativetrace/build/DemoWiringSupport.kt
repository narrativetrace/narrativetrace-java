/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/**
 * INTENT: Backs the root `demoWiringCheck` task — keeps the demo launcher's per-scenario wiring
 * notes in step with the scenarios the examples actually print.
 *
 * `demo.sh` explains, for every scenario, how that scenario's trace is configured (Spring AOP vs.
 * `NarrativeTraceProxy`, which annotations are in play). That prose lives in
 * `narrativetrace-examples/demo/wiring.awk`, keyed by the scenario's header text — deliberately
 * outside the example sources, so the examples stay reference-grade code. The cost of that split is
 * drift: renaming a scenario, adding one, or deleting one leaves the launcher silently wrong. This
 * check closes that hole in both directions.
 */
object DemoWiringSupport {

    /** Header literals such as `logger.info("=== Scenario 2: Out of Stock ===\n")`. */
    private val INLINE_HEADER = Regex("""===\s+(.+?)\s+===""")

    /** Titles supplied at runtime, as in ecommerce's `beginTrace("Scenario 5: Out of Stock")`. */
    private val TRACE_LABEL = Regex("""beginTrace\("(.+?)"\)""")

    /** Table entries of the form `wiring["Scenario 5: Out of Stock"] = "..."`. */
    private val WIRING_KEY = Regex("""wiring\["(.+?)"]""")

    private const val WIRING_TABLE = "narrativetrace-examples/demo/wiring.awk"

    /** Locales the demo localizes its chrome into; each needs a full wiring table. */
    private val CHROME_LOCALES = listOf("es", "zh-CN")

    private fun localeTable(locale: String) = "narrativetrace-examples/demo/wiring-$locale.awk"

    fun scenarioTitles(repoRoot: File): Set<String> =
        exampleSources(repoRoot).flatMap { titlesIn(it.readText()) }.toSet()

    fun wiringKeys(repoRoot: File): Set<String> =
        WIRING_KEY.findAll(repoRoot.resolve(WIRING_TABLE).readText())
            .map { it.groupValues[1] }
            .toSet()

    private fun titlesIn(source: String): List<String> =
        listOf(INLINE_HEADER, TRACE_LABEL)
            .flatMap { pattern -> pattern.findAll(source).map { it.groupValues[1] } }
            // A brace means a parameterized header ("=== {} ==="); a quote means the header is
            // built by string concatenation ("=== " + scenario + " ==="). Neither is a literal
            // title — the real one is captured where the label string itself is written.
            .filterNot { it.contains("{") || it.contains("\"") }

    /**
     * Verifies the wiring table against the examples; returns problems sorted, empty when the two
     * agree. A scenario the launcher cannot explain is the failure that matters: the demo would
     * print the header and silently skip the "how this trace is configured" note.
     */
    fun check(repoRoot: File): List<String> {
        if (!repoRoot.resolve(WIRING_TABLE).isFile) {
            return listOf("$WIRING_TABLE is missing — the demo launcher explains nothing without it")
        }
        val sources = exampleSources(repoRoot).map { it.readText() }
        val keys = wiringKeys(repoRoot)
        val unexplained =
            sources.flatMap { titlesIn(it) }.toSet().filterNot { it in keys }.map {
                "scenario \"$it\" has no wiring note — add wiring[\"$it\"] to $WIRING_TABLE"
            }
        val orphaned =
            keys.filterNot { key -> sources.any { it.contains(key) } }.map {
                "wiring note \"$it\" matches no example scenario — renamed or deleted?"
            }
        return (unexplained + orphaned + localeProblems(repoRoot, keys)).sorted()
    }

    /**
     * The demo's translated mode prints wiring notes from a per-locale table; every base note must
     * have a translation and no locale note may outlive its base key. Same drift argument as the
     * base check, once per chrome locale.
     */
    private fun localeProblems(repoRoot: File, baseKeys: Set<String>): List<String> =
        CHROME_LOCALES.flatMap { locale ->
            val table = repoRoot.resolve(localeTable(locale))
            if (!table.isFile) {
                return@flatMap listOf(
                    "${localeTable(locale)} is missing — the demo's '$locale' run explains nothing without it"
                )
            }
            val localeKeys = WIRING_KEY.findAll(table.readText()).map { it.groupValues[1] }.toSet()
            val untranslated =
                baseKeys.filterNot { it in localeKeys }.map {
                    "wiring note \"$it\" has no translation in ${localeTable(locale)}"
                }
            val stale =
                localeKeys.filterNot { it in baseKeys }.map {
                    "wiring note \"$it\" in ${localeTable(locale)} has no base note — renamed or deleted?"
                }
            untranslated + stale
        }

    private fun exampleSources(repoRoot: File): List<File> =
        repoRoot.resolve("narrativetrace-examples").walkTopDown()
            .onEnter { it.name != "build" }
            .filter { it.isFile && it.extension in setOf("java", "kt") }
            .toList()
}
