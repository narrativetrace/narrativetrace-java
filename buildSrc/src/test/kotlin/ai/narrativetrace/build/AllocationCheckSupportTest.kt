/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AllocationCheckSupportTest {

    @TempDir
    lateinit var dir: File

    private fun thresholds(content: String): File =
        dir.resolve("allocation-thresholds.properties").apply { writeText(content) }

    private fun results(vararg entries: String): File =
        dir.resolve("result.json").apply { writeText("[" + entries.joinToString(",") + "]") }

    private fun entry(benchmark: String, score: Double, allocation: Double?): String {
        val secondary = allocation?.let {
            """"secondaryMetrics":{"·gc.alloc.rate.norm":{"score":$it,"scoreUnit":"B/op"}}"""
        } ?: """"secondaryMetrics":{}"""
        return """{"benchmark":"$benchmark","mode":"avgt",
            "primaryMetric":{"score":$score,"scoreUnit":"ns/op"},$secondary}"""
    }

    @Test
    fun readThresholdsSkipsCommentsAndBlankLines() {
        val file = thresholds(
            """
            # a comment

            a.b.C.fast = 80
            a.b.C.slow=1680   # trailing comment
            """.trimIndent()
        )

        assertEquals(mapOf("a.b.C.fast" to 80L, "a.b.C.slow" to 1680L), AllocationCheckSupport.readThresholds(file))
    }

    @Test
    fun readThresholdsRejectsANonNumericThreshold() {
        val file = thresholds("a.b.C.fast = eighty")

        val failure = assertThrows(IllegalArgumentException::class.java) {
            AllocationCheckSupport.readThresholds(file)
        }
        assertTrue(failure.message!!.contains("a.b.C.fast"))
    }

    @Test
    fun readThresholdsRejectsALineWithNoSeparator() {
        val file = thresholds("a.b.C.fast 80")

        assertThrows(IllegalArgumentException::class.java) { AllocationCheckSupport.readThresholds(file) }
    }

    @Test
    fun readThresholdsRejectsAMissingFile() {
        assertThrows(IllegalArgumentException::class.java) {
            AllocationCheckSupport.readThresholds(dir.resolve("absent.properties"))
        }
    }

    @Test
    fun readingResultsPullsTheAllocationSecondaryMetric() {
        val scores = BenchmarkResultSupport.read(results(entry("a.b.C.fast", 20.5, 80.0)))

        assertEquals(1, scores.size)
        assertEquals("C.fast", scores[0].shortName)
        assertEquals(20.5, scores[0].score)
        assertEquals("ns/op", scores[0].unit)
        assertEquals(80.0, scores[0].allocationNorm)
    }

    @Test
    fun readingResultsLeavesAllocationNullWhenTheRunHadNoGcProfiler() {
        val scores = BenchmarkResultSupport.read(results(entry("a.b.C.fast", 20.5, null)))

        assertEquals(null, scores[0].allocationNorm)
    }

    @Test
    fun readingResultsRejectsAMissingFile() {
        assertThrows(IllegalArgumentException::class.java) {
            BenchmarkResultSupport.read(dir.resolve("absent.json"))
        }
    }

    @Test
    fun aBenchmarkInsideItsThresholdIsNoProblem() {
        val verdicts = AllocationCheckSupport.verdicts(
            mapOf("a.b.C.fast" to 80L),
            BenchmarkResultSupport.read(results(entry("a.b.C.fast", 20.0, 80.0)))
        )

        assertEquals(emptyList<String>(), AllocationCheckSupport.problems(verdicts))
    }

    @Test
    fun aBenchmarkOverItsThresholdIsReportedWithBothNumbers() {
        val verdicts = AllocationCheckSupport.verdicts(
            mapOf("a.b.C.fast" to 80L),
            BenchmarkResultSupport.read(results(entry("a.b.C.fast", 20.0, 81.0)))
        )

        assertEquals(listOf("C.fast: 81 B/op exceeds 80 B/op"), AllocationCheckSupport.problems(verdicts))
    }

    @Test
    fun aThresholdedBenchmarkTheRunNeverMeasuredIsAFailure() {
        val verdicts = AllocationCheckSupport.verdicts(
            mapOf("a.b.C.fast" to 80L),
            BenchmarkResultSupport.read(results(entry("a.b.C.other", 20.0, 8.0)))
        )

        assertEquals(1, AllocationCheckSupport.problems(verdicts).size)
        assertTrue(AllocationCheckSupport.problems(verdicts)[0].contains("not measured"))
    }

    @Test
    fun aMeasuredBenchmarkWithNoAllocationMetricIsAFailure() {
        val verdicts = AllocationCheckSupport.verdicts(
            mapOf("a.b.C.fast" to 80L),
            BenchmarkResultSupport.read(results(entry("a.b.C.fast", 20.0, null)))
        )

        assertTrue(AllocationCheckSupport.problems(verdicts)[0].contains("not measured"))
    }

    @Test
    fun theTableShowsEveryBenchmarkWhetherItPassedOrNot() {
        val verdicts = AllocationCheckSupport.verdicts(
            linkedMapOf("a.b.C.fast" to 80L, "a.b.C.slow" to 100L),
            BenchmarkResultSupport.read(entriesOf())
        )

        val table = AllocationCheckSupport.table(verdicts)

        assertTrue(table.contains("C.fast"))
        assertTrue(table.contains("C.slow"))
        assertTrue(table.contains("ok"))
        assertTrue(table.contains("OVER"))
    }

    private fun entriesOf(): File =
        results(entry("a.b.C.fast", 20.0, 80.0), entry("a.b.C.slow", 30.0, 101.0))

    @Test
    fun aFractionalReadingKeepsOneDecimalInTheReport() {
        val verdicts = AllocationCheckSupport.verdicts(
            mapOf("a.b.C.fast" to 80L),
            BenchmarkResultSupport.read(results(entry("a.b.C.fast", 20.0, 80.5)))
        )

        assertEquals(listOf("C.fast: 80.5 B/op exceeds 80 B/op"), AllocationCheckSupport.problems(verdicts))
    }
}
