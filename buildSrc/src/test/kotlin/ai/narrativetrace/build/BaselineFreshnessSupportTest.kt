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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate

class BaselineFreshnessSupportTest {

    @TempDir
    lateinit var dir: File

    private val today = LocalDate.of(2026, 8, 31)

    private fun baseline(content: String): File =
        dir.resolve("baseline.txt").apply { writeText(content) }

    private fun dated(date: String) = baseline(
        """
        # Benchmark baseline — $date
        # JDK 17, 1 fork, 3 warmup × 1s, 5 iterations × 1s
        # Commit: bbeec28 (allocation baseline run)
        #
        Benchmark  Mode  Cnt  Score  Units
        """.trimIndent()
    )

    @Test
    fun readsTheDateFromAnEmDashHeader() {
        assertEquals(LocalDate.of(2026, 2, 25), BaselineFreshnessSupport.recordedDate(dated("2026-02-25").readText()))
    }

    @Test
    fun readsTheDateFromAnAsciiHyphenHeader() {
        val text = "# Benchmark baseline - 2026-02-25\n"

        assertEquals(LocalDate.of(2026, 2, 25), BaselineFreshnessSupport.recordedDate(text))
    }

    @Test
    fun readsTheCommitFromTheHeader() {
        assertEquals("bbeec28", BaselineFreshnessSupport.recordedCommit(dated("2026-02-25").readText()))
    }

    @Test
    fun aHeaderWithNoDateReadsAsUnknown() {
        assertNull(BaselineFreshnessSupport.recordedDate("# Benchmark baseline\nBenchmark Mode\n"))
    }

    @Test
    fun anImpossibleDateReadsAsUnknownRatherThanThrowing() {
        assertNull(BaselineFreshnessSupport.recordedDate("# Benchmark baseline — 2026-02-31\n"))
    }

    @Test
    fun aBaselineInsideTheLimitIsNotStale() {
        assertFalse(BaselineFreshnessSupport.isStale(dated("2026-08-01"), today))
    }

    @Test
    fun aBaselineExactlyAtTheLimitIsNotStale() {
        assertFalse(BaselineFreshnessSupport.isStale(dated("2026-06-02"), today))
    }

    @Test
    fun aBaselineOneDayPastTheLimitIsStale() {
        assertTrue(BaselineFreshnessSupport.isStale(dated("2026-06-01"), today))
    }

    @Test
    fun aMissingBaselineIsStale() {
        assertTrue(BaselineFreshnessSupport.isStale(dir.resolve("absent.txt"), today))
    }

    @Test
    fun anUndatedBaselineIsStale() {
        assertTrue(BaselineFreshnessSupport.isStale(baseline("# Benchmark baseline\n"), today))
    }

    @Test
    fun theStaleReportCarriesTheAgeTheDateTheCommitAndTheCommand() {
        val report = BaselineFreshnessSupport.report(dated("2026-02-25"), today)

        assertTrue(report.contains("WARNING"))
        assertTrue(report.contains("187 days old"))
        assertTrue(report.contains("2026-02-25"))
        assertTrue(report.contains("bbeec28"))
        assertTrue(report.contains(BaselineFreshnessSupport.REFRESH_COMMAND))
    }

    @Test
    fun theFreshReportStatesTheAgeAndDoesNotWarn() {
        val report = BaselineFreshnessSupport.report(dated("2026-08-01"), today)

        assertTrue(report.contains("30 days old"))
        assertFalse(report.contains("WARNING"))
    }

    @Test
    fun aMissingBaselineReportsTheCommandToBuildOne() {
        val report = BaselineFreshnessSupport.report(dir.resolve("absent.txt"), today)

        assertTrue(report.contains("WARNING"))
        assertTrue(report.contains(BaselineFreshnessSupport.REFRESH_COMMAND))
    }

    @Test
    fun anUndatedBaselineReportsThatItsAgeIsUnknown() {
        val report = BaselineFreshnessSupport.report(baseline("# Benchmark baseline\n"), today)

        assertTrue(report.contains("WARNING"))
        assertTrue(report.contains("age is unknown"))
    }

    @Test
    fun aBaselineWithNoCommitHeaderStillReports() {
        val report = BaselineFreshnessSupport.report(baseline("# Benchmark baseline — 2026-08-15\n"), today)

        assertTrue(report.contains("16 days old"))
        assertFalse(report.contains("commit"))
    }
}
