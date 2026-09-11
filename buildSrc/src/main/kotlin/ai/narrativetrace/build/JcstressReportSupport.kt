/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/** What a jcstress run's own HTML report says: how many test classes ended passed vs failed. */
data class JcstressSummary(
    val testsPassed: Int,
    val testsFailed: Int,
    val overallPassed: Boolean,
    val reportFound: Boolean,
)

/**
 * INTENT: jcstress writes no JSON or plain-text summary (only a binary results blob, one HTML file
 * per test class, and an `index.html` master table); this reads `index.html`, the only file that
 * carries a per-class PASSED/FAILED verdict at all.
 *
 * A per-test-class HTML file (e.g. `ai.narrativetrace.core.pipeline.LossAccountingTest.html`) has
 * NO per-class pass/fail marker of its own — its `.endResult` CSS class is declared but never
 * applied in the body; only the *outcome-row* colouring (`bgColor='green '`, text `OK`) appears
 * there, one row per JVM-flags/scheduling combination, which is a much finer grain than "did this
 * test class pass." `index.html`'s own summary table is where the per-class verdict lives:
 * `<td class="passed">PASSED</td>` (each row also carries an empty spacer `<td class="passed">`
 * cell right after, which the trailing `<` in the pattern excludes from the count) — found by
 * running this against a real report (2026-09-09): the per-file scan below always returned 0/0.
 */
object JcstressReportSupport {

    private val OVERALL_PASSED = Regex("""class="endResult passed"""")
    private val CLASS_ROW_PASSED = Regex("""class="passed">PASSED<""")
    private val CLASS_ROW_FAILED = Regex("""class="failed">FAILED<""")

    fun summarize(reportDir: File): JcstressSummary {
        val index = File(reportDir, "index.html")
        if (!index.isFile) return JcstressSummary(0, 0, overallPassed = false, reportFound = false)

        val text = index.readText()
        val overallPassed = OVERALL_PASSED.containsMatchIn(text)
        val passed = CLASS_ROW_PASSED.findAll(text).count()
        val failed = CLASS_ROW_FAILED.findAll(text).count()
        return JcstressSummary(passed, failed, overallPassed, reportFound = true)
    }
}
