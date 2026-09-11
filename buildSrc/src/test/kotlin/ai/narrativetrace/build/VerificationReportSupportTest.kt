/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

class VerificationReportSupportTest {

    @TempDir
    lateinit var dir: File

    private fun sampleRun(categories: List<CategoryResult>) = VerificationRun(
        runtime = "java",
        version = "0.2.0",
        commit = "abc1234",
        host = "test-host-x86_64",
        startedAt = Instant.parse("2026-09-09T00:00:00Z"),
        endedAt = Instant.parse("2026-09-09T01:00:00Z"),
        categories = categories,
    )

    private fun row(
        category: VerificationCategory,
        status: VerificationStatus,
        tool: String = "Some Tool 1.0",
        metrics: Map<String, Any?> = mapOf("tests_passed" to 5),
        note: String? = null,
    ) = CategoryResult(category, tool, status, metrics, durationSeconds = 12.5, note = note)

    @Test
    fun `overall status is passed when no category failed`() {
        val run = sampleRun(listOf(row(VerificationCategory.UNIT_TESTS, VerificationStatus.PASSED)))
        assertEquals("passed", run.overallStatus)
    }

    @Test
    fun `overall status is failed when any category failed`() {
        val run = sampleRun(
            listOf(
                row(VerificationCategory.UNIT_TESTS, VerificationStatus.PASSED),
                row(VerificationCategory.MUTATION, VerificationStatus.FAILED),
            )
        )
        assertEquals("failed", run.overallStatus)
    }

    @Test
    fun `not-implemented and skipped rows never taint the overall verdict`() {
        val run = sampleRun(
            listOf(
                row(VerificationCategory.UNIT_TESTS, VerificationStatus.PASSED),
                row(VerificationCategory.CLARITY, VerificationStatus.NOT_IMPLEMENTED),
                row(VerificationCategory.SECRETS, VerificationStatus.SKIPPED),
            )
        )
        assertEquals("passed", run.overallStatus)
    }

    @Test
    fun `writeJson then readJson round-trips every field, including a null note`() {
        val run = sampleRun(
            listOf(
                row(
                    VerificationCategory.MUTATION, VerificationStatus.PASSED,
                    tool = "Pitest 1.17.4",
                    metrics = mapOf("mutants_killed" to 100, "mutants_survived" to 5, "mutation_score" to 95.2),
                    note = null,
                )
            )
        )
        val file = File(dir, "report.json")

        VerificationReportSupport.writeJson(run, file)
        val read = VerificationReportSupport.readJson(file)

        assertEquals("java", read["runtime"])
        assertEquals("passed", read["overall_status"])
        @Suppress("UNCHECKED_CAST") val categories = read["categories"] as List<Map<String, Any?>>
        assertEquals(1, categories.size)
        assertEquals("mutation", categories.single()["category"])
        assertEquals("Pitest 1.17.4", categories.single()["tool"])
        assertEquals(null, categories.single()["note"])
    }

    @Test
    fun `a category not on the fixed vocabulary is not representable (compile-time, by enum)`() {
        // The enum itself is the guard: VerificationCategory has exactly the §35E vocabulary,
        // so a caller cannot construct a row for an unlisted category name.
        assertEquals(21, VerificationCategory.values().size)
    }

    @Test
    fun `renderMarkdown reads the just-written JSON rather than recomputing anything`() {
        val run = sampleRun(
            listOf(
                row(VerificationCategory.UNIT_TESTS, VerificationStatus.PASSED, metrics = mapOf("tests_passed" to 42)),
                row(VerificationCategory.CLARITY, VerificationStatus.NOT_IMPLEMENTED, metrics = emptyMap(), note = "no clarity self-gate for java"),
            )
        )
        val file = File(dir, "report.json")
        VerificationReportSupport.writeJson(run, file)

        val markdown = VerificationReportSupport.renderMarkdown(file)

        assertTrue(markdown.contains("unit-tests"))
        assertTrue(markdown.contains("tests_passed=42"))
        assertTrue(markdown.contains("not-implemented"))
        assertTrue(markdown.contains("no clarity self-gate for java"))
        assertTrue(markdown.contains("**overall status: passed**"))
    }

    // Regression: JsonSlurper hands back a BigDecimal for a JSON number, never a Kotlin Double —
    // `as? Double` silently missed every row and rendered every duration as 0.0. Found running a
    // real verifyAll report through this method (2026-09-09); every row of that report's table
    // read "0.0" despite the underlying JSON carrying the real, non-zero durations throughout.
    @Test
    fun `renderMarkdown prints the real duration, not zero, for every numeric type JsonSlurper returns`() {
        val run = sampleRun(
            listOf(row(VerificationCategory.MUTATION, VerificationStatus.PASSED))
        ) // row()'s durationSeconds default is 12.5
        val file = File(dir, "report.json")
        VerificationReportSupport.writeJson(run, file)

        val markdown = VerificationReportSupport.renderMarkdown(file)

        assertTrue(markdown.contains("| 12.5 |"), "expected the real duration 12.5, not 0.0:\n$markdown")
        assertTrue(!markdown.contains("| 0.0 |"))
    }
}
