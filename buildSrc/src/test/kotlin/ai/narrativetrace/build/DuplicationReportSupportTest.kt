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

class DuplicationReportSupportTest {

    @TempDir
    lateinit var tempDir: File

    private fun cluster(tokens: Int, occurrenceCount: Int) = DuplicationCluster(
        tokens = tokens,
        lines = tokens / 4,
        occurrences = (1..occurrenceCount).map {
            DuplicationOccurrence("module$it/src/main/java/Thing.java", 10 * it, 10 * it + tokens)
        }
    )

    // ---- countCoveredPositions (the overlap-safe union CPD's raw matches need) ----------------

    @Test
    fun countCoveredPositionsOfDisjointSpansIsTheirSum() {
        assertEquals(20, DuplicationReportSupport.countCoveredPositions(listOf(0 until 10, 20 until 30)))
    }

    @Test
    fun countCoveredPositionsOfOverlappingSpansCountsTheUnionOnceOnly() {
        // 0..14 and 10..24 overlap on 10..14 — union is 0..24, 25 positions, not 15+15=30.
        assertEquals(25, DuplicationReportSupport.countCoveredPositions(listOf(0 until 15, 10 until 25)))
    }

    @Test
    fun countCoveredPositionsOfANestedSpanCountsOnlyTheOuterOne() {
        // A cluster fully inside another (a long clone containing a shorter one) contributes nothing new.
        assertEquals(100, DuplicationReportSupport.countCoveredPositions(listOf(0 until 100, 10 until 40)))
    }

    @Test
    fun countCoveredPositionsOfManyOverlappingCopiesStillBoundsToTheUnion() {
        // This is the exact failure mode the first scan hit: a file with many near-identical entries
        // produces many pairwise matches over almost the same span, which a naive per-match sum
        // multiplies far past the corpus size. The union never can.
        val spans = (1..50).map { 0 until 60 }
        assertEquals(60, DuplicationReportSupport.countCoveredPositions(spans))
    }

    @Test
    fun countCoveredPositionsOfNoSpansIsZero() {
        assertEquals(0, DuplicationReportSupport.countCoveredPositions(emptyList()))
    }

    // ---- aggregate -----------------------------------------------------------------------------

    @Test
    fun aggregateComputesPercentAndSortsByTokensDescending() {
        val result = DuplicationReportSupport.aggregate(
            tokensTotal = 1000,
            tokensDuplicated = 360,
            clusters = listOf(cluster(60, 2), cluster(120, 2))
        )
        assertEquals(1000, result.tokensTotal)
        assertEquals(360, result.tokensDuplicated)
        assertEquals(36.0, result.percent, 0.001)
        assertEquals(listOf(120, 60), result.clusters.map { it.tokens })
    }

    @Test
    fun aggregateNeverExceedsOneHundredPercentEvenWithHeavilyOverlappingClusters() {
        // tokensDuplicated is capped by the corpus size once it comes from countCoveredPositions —
        // this asserts aggregate() honours whatever union count it is given, however large the raw
        // cluster list looks (10 overlapping 60-token clusters, but only 60 real positions).
        val result = DuplicationReportSupport.aggregate(
            tokensTotal = 60,
            tokensDuplicated = 60,
            clusters = (1..10).map { cluster(60, 2) }
        )
        assertEquals(100.0, result.percent, 0.001)
    }

    @Test
    fun aggregateOfZeroTokensIsZeroPercentNotDivideByZero() {
        val result = DuplicationReportSupport.aggregate(tokensTotal = 0, tokensDuplicated = 0, clusters = emptyList())
        assertEquals(0.0, result.percent, 0.001)
        assertTrue(result.clusters.isEmpty())
    }

    @Test
    fun aggregateRoundsPercentToOneDecimal() {
        val result = DuplicationReportSupport.aggregate(tokensTotal = 3, tokensDuplicated = 1, clusters = listOf(cluster(1, 1)))
        // 1/3 * 100 = 33.333... -> 33.3
        assertEquals(33.3, result.percent, 0.0001)
    }

    // ---- writeJson / readJson --------------------------------------------------------------------

    @Test
    fun writeJsonThenReadJsonRoundTrips() {
        val scan = DuplicationScanResult(
            minTokens = 60,
            main = DuplicationReportSupport.aggregate(1000, 284, listOf(cluster(142, 2))),
            test = DuplicationReportSupport.aggregate(2000, 280, listOf(cluster(80, 2), cluster(60, 2)))
        )
        val file = tempDir.resolve("duplication.json")
        DuplicationReportSupport.writeJson(scan, file)

        val text = file.readText()
        assertTrue(text.contains("\"tool\":\"pmd-cpd\""))
        assertTrue(text.contains("\"language\":\"java\""))
        assertTrue(text.contains("\"minTokens\":60"))

        val roundTripped = DuplicationReportSupport.readJson(file)
        assertEquals(scan.minTokens, roundTripped.minTokens)
        assertEquals(scan.main.tokensTotal, roundTripped.main.tokensTotal)
        assertEquals(scan.main.percent, roundTripped.main.percent, 0.001)
        assertEquals(scan.main.clusters.map { it.tokens }, roundTripped.main.clusters.map { it.tokens })
        assertEquals(
            scan.main.clusters.first().occurrences,
            roundTripped.main.clusters.first().occurrences
        )
        assertEquals(scan.test.clusters.size, roundTripped.test.clusters.size)
    }

    @Test
    fun writeJsonEscapesQuotesAndBackslashesInPaths() {
        val cluster = DuplicationCluster(
            tokens = 61,
            lines = 10,
            occurrences = listOf(DuplicationOccurrence("weird\"path\\file.java", 1, 5))
        )
        val scan = DuplicationScanResult(
            minTokens = 60,
            main = DuplicationReportSupport.aggregate(100, 61, listOf(cluster)),
            test = DuplicationReportSupport.aggregate(0, 0, emptyList())
        )
        val file = tempDir.resolve("duplication.json")
        DuplicationReportSupport.writeJson(scan, file)

        val roundTripped = DuplicationReportSupport.readJson(file)
        assertEquals("weird\"path\\file.java", roundTripped.main.clusters.first().occurrences.first().file)
    }

    // ---- summaryLine ---------------------------------------------------------------------------

    @Test
    fun summaryLineNamesTheLargestMainClusterAndMarksTestUngated() {
        val scan = DuplicationScanResult(
            minTokens = 60,
            main = DuplicationReportSupport.aggregate(1000, 284, listOf(cluster(142, 2))),
            test = DuplicationReportSupport.aggregate(1000, 120, listOf(cluster(60, 2)))
        )
        val line = DuplicationReportSupport.summaryLine(scan)
        assertTrue(line.startsWith("duplication: main "))
        assertTrue(line.contains("142 tokens"))
        assertTrue(line.contains("module1/src/main/java/Thing.java:10"))
        assertTrue(line.contains("reported, not gated"))
    }

    @Test
    fun summaryLineHandlesNoClustersAtAll() {
        val scan = DuplicationScanResult(
            minTokens = 60,
            main = DuplicationReportSupport.aggregate(1000, 0, emptyList()),
            test = DuplicationReportSupport.aggregate(1000, 0, emptyList())
        )
        val line = DuplicationReportSupport.summaryLine(scan)
        assertTrue(line.contains("no clusters"))
        assertTrue(line.contains("0.0% of tokens in 0 clusters"))
    }
}
