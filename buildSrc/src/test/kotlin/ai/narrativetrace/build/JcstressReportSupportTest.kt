/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JcstressReportSupportTest {

    @TempDir
    lateinit var dir: File

    /** One row of jcstress's real `index.html` summary table: a link, a verdict cell, and an empty spacer cell carrying the SAME class — the shape that broke a naive per-cell count. */
    private fun classRow(name: String, passed: Boolean): String {
        val cls = if (passed) "passed" else "failed"
        val verdict = if (passed) "PASSED" else "FAILED"
        return """
            <tr><td>&nbsp;&nbsp;&nbsp;<a href="$name.html">$name</a></td>
            <td>10<sup>2</sup></td><td class="$cls">$verdict</td></tr>
            <tr><td class="$cls"></td></tr>
        """.trimIndent()
    }

    @Test
    fun `no index html means no report found`() {
        val summary = JcstressReportSupport.summarize(dir)

        assertFalse(summary.reportFound)
        assertFalse(summary.overallPassed)
        assertEquals(0, summary.testsPassed)
    }

    // Regression: jcstress's per-test-class HTML file carries NO per-class pass/fail marker at all
    // (only unrelated per-JVM-flags outcome rows) — the per-class verdict lives ONLY in index.html's
    // own summary table. A version of this reader that scanned the per-test files instead always
    // returned 0/0, found running against a real jcstress report (2026-09-09).
    @Test
    fun `counts passed and failed classes from index html's own summary table, not per-file HTML`() {
        File(dir, "index.html").writeText(
            """<p class="endResult passed">PASSED</p>""" +
                classRow("ai.A", passed = true) +
                classRow("ai.B", passed = true) +
                classRow("ai.C", passed = false)
        )
        // A per-test-class file with no pass/fail marker of its own (the real shape) must be ignored.
        File(dir, "ai.A.html").writeText("""<html><body><h1>ai.A</h1><table><tr><td bgColor='green '>OK</td></tr></table></body></html>""")

        val summary = JcstressReportSupport.summarize(dir)

        assertTrue(summary.reportFound)
        assertTrue(summary.overallPassed)
        assertEquals(2, summary.testsPassed)
        assertEquals(1, summary.testsFailed)
    }

    @Test
    fun `the empty spacer cell sharing the same class is never double-counted`() {
        File(dir, "index.html").writeText(
            """<p class="endResult passed">PASSED</p>""" + classRow("ai.A", passed = true)
        )

        val summary = JcstressReportSupport.summarize(dir)

        assertEquals(1, summary.testsPassed)
    }

    @Test
    fun `an overall-failed index is reported as such`() {
        File(dir, "index.html").writeText("""<p class="endResult failed">FAILED</p>""")

        val summary = JcstressReportSupport.summarize(dir)

        assertFalse(summary.overallPassed)
    }
}
