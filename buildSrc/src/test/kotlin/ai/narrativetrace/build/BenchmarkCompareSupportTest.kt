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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BenchmarkCompareSupportTest {

    @TempDir
    lateinit var dir: File

    private val bands = mapOf("default" to 25.0, "allocation.default" to 5.0)

    private fun baselineFile(content: String): File =
        dir.resolve("baseline.txt").apply { writeText(content) }

    private fun score(benchmark: String, value: Double) =
        BenchmarkScore("ai.narrativetrace.benchmarks.$benchmark", "avgt", value, "ns/op", null)

    @Test
    fun readsScoreRowsAndIgnoresComments() {
        val file = baselineFile(
            """
            # Benchmark baseline — 2026-08-31
            # Commit: abc1234
            Benchmark                              Mode  Cnt      Score      Error  Units
            ProxyOverheadBenchmark.directCall      avgt    5     35.286 ±    1.618  ns/op
            NestingDepthBenchmark.nesting_depth_50 avgt    5  39013.126 ± 3659.051  ns/op
            """.trimIndent()
        )

        val baseline = BenchmarkCompareSupport.readBaseline(file)

        assertEquals(2, baseline.size)
        assertEquals(35.286 to "ns/op", baseline["ProxyOverheadBenchmark.directCall"])
        assertEquals(39013.126 to "ns/op", baseline["NestingDepthBenchmark.nesting_depth_50"])
    }

    @Test
    fun theHeaderRowIsNotMistakenForABenchmark() {
        val file = baselineFile("Benchmark  Mode  Cnt  Score  Error  Units\n")

        assertEquals(emptyMap<String, Pair<Double, String>>(), BenchmarkCompareSupport.readBaseline(file))
    }

    @Test
    fun readingAMissingBaselineFails() {
        assertThrows(IllegalArgumentException::class.java) {
            BenchmarkCompareSupport.readBaseline(dir.resolve("absent.txt"))
        }
    }

    @Test
    fun readingMissingTolerancesFails() {
        assertThrows(IllegalArgumentException::class.java) {
            BenchmarkCompareSupport.readTolerances(dir.resolve("absent.properties"))
        }
    }

    @Test
    fun readsTolerancesAndSkipsComments() {
        val file = dir.resolve("t.properties").apply {
            writeText("# a comment\ndefault = 25\nA.b = 60  # inline\n")
        }

        assertEquals(mapOf("default" to 25.0, "A.b" to 60.0), BenchmarkCompareSupport.readTolerances(file))
    }

    @Test
    fun aNonNumericToleranceIsRejected() {
        val file = dir.resolve("t.properties").apply { writeText("default = loose\n") }

        assertThrows(IllegalArgumentException::class.java) { BenchmarkCompareSupport.readTolerances(file) }
    }

    @Test
    fun aSpecificBandOverridesTheDefault() {
        val tolerances = mapOf("default" to 25.0, "A.b" to 60.0)

        assertEquals(60.0, BenchmarkCompareSupport.toleranceFor(tolerances, "A.b", allocation = false))
        assertEquals(25.0, BenchmarkCompareSupport.toleranceFor(tolerances, "A.other", allocation = false))
    }

    @Test
    fun allocationUsesItsOwnDefaultBand() {
        assertEquals(5.0, BenchmarkCompareSupport.toleranceFor(bands, "A.b", allocation = true))
    }

    @Test
    fun allocationFallsBackToTheGeneralDefaultWhenItHasNoneOfItsOwn() {
        assertEquals(25.0, BenchmarkCompareSupport.toleranceFor(mapOf("default" to 25.0), "A.b", allocation = true))
    }

    @Test
    fun noBandAndNoDefaultIsAConfigurationError() {
        assertThrows(IllegalArgumentException::class.java) {
            BenchmarkCompareSupport.toleranceFor(emptyMap(), "A.b", allocation = false)
        }
    }

    @Test
    fun aReadingInsideTheBandIsNotARegression() {
        val comparisons = BenchmarkCompareSupport.compare(
            mapOf("C.m" to (100.0 to "ns/op")),
            listOf(score("C.m", 124.0)),
            bands
        )

        assertFalse(comparisons[0].regressed)
        assertEquals(emptyList<String>(), BenchmarkCompareSupport.problems(comparisons, emptyList()))
    }

    @Test
    fun aReadingPastTheBandIsARegression() {
        val comparisons = BenchmarkCompareSupport.compare(
            mapOf("C.m" to (100.0 to "ns/op")),
            listOf(score("C.m", 126.0)),
            bands
        )

        assertTrue(comparisons[0].regressed)
        assertTrue(BenchmarkCompareSupport.problems(comparisons, emptyList())[0].contains("outside the 25% band"))
    }

    @Test
    fun beingFasterNeverFails() {
        val comparisons = BenchmarkCompareSupport.compare(
            mapOf("C.m" to (100.0 to "ns/op")),
            listOf(score("C.m", 1.0)),
            bands
        )

        assertFalse(comparisons[0].regressed)
    }

    @Test
    fun aZeroBaselineNeverRegresses() {
        val comparisons = BenchmarkCompareSupport.compare(
            mapOf("C.m" to (0.0 to "B/op")),
            listOf(score("C.m", 5.0)),
            bands
        )

        assertFalse(comparisons[0].regressed)
    }

    @Test
    fun aBenchmarkTheRunNeverProducedIsReportedAsMissing() {
        val baseline = mapOf("C.m" to (100.0 to "ns/op"), "C.gone" to (10.0 to "ns/op"))
        val current = listOf(score("C.m", 100.0))

        val missing = BenchmarkCompareSupport.missing(baseline, current)

        assertEquals(listOf("C.gone"), missing)
        assertEquals(
            listOf("C.gone: in the baseline, not in this run"),
            BenchmarkCompareSupport.problems(BenchmarkCompareSupport.compare(baseline, current, bands), missing)
        )
    }

    @Test
    fun aBenchmarkNewInThisRunIsNotAProblem() {
        val baseline = mapOf("C.m" to (100.0 to "ns/op"))
        val current = listOf(score("C.m", 100.0), score("C.brandNew", 5.0))

        assertEquals(emptyList<String>(), BenchmarkCompareSupport.missing(baseline, current))
        assertEquals(1, BenchmarkCompareSupport.compare(baseline, current, bands).size)
    }

    @Test
    fun theTableShowsRegressedOkAndMissingRows() {
        val baseline = mapOf("C.ok" to (100.0 to "ns/op"), "C.bad" to (100.0 to "ns/op"), "C.gone" to (1.0 to "ns/op"))
        val current = listOf(score("C.ok", 100.0), score("C.bad", 300.0))
        val comparisons = BenchmarkCompareSupport.compare(baseline, current, bands)

        val table = BenchmarkCompareSupport.table(comparisons, BenchmarkCompareSupport.missing(baseline, current))

        assertTrue(table.contains("REGRESSED"))
        assertTrue(table.contains("MISSING"))
        assertTrue(table.contains("ok"))
    }
}
