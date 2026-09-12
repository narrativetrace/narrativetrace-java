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

class DuplicationCheckSupportTest {

    @TempDir
    lateinit var tempDir: File

    private fun cluster(tokens: Int, vararg files: String) = DuplicationCluster(
        tokens = tokens,
        lines = tokens / 4,
        occurrences = files.map { DuplicationOccurrence(it, 10, 10 + tokens) }
    )

    private fun tree(percent: Double, clusters: List<DuplicationCluster>) =
        DuplicationTreeResult(tokensTotal = 1000, tokensDuplicated = 0, percent = percent, clusters = clusters)

    // ---- baseline.properties -----------------------------------------------------------------

    @Test
    fun readBaselineParsesAllFields() {
        val file = tempDir.resolve("baseline.properties")
        file.writeText(
            """
            recorded=2026-09-12
            commit=2026-09-12-first-scan
            main.percent=3.1
            main.largestCluster=142
            """.trimIndent()
        )
        val baseline = DuplicationCheckSupport.readBaseline(file)
        assertEquals(3.1, baseline.mainPercent, 0.001)
        assertEquals(142, baseline.mainLargestCluster)
        assertEquals("2026-09-12", baseline.recorded)
        assertEquals("2026-09-12-first-scan", baseline.commit)
    }

    @Test
    fun readBaselineMissingFileThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            DuplicationCheckSupport.readBaseline(tempDir.resolve("missing.properties"))
        }
    }

    @Test
    fun readBaselineMissingRequiredKeyThrows() {
        val file = tempDir.resolve("baseline.properties")
        file.writeText("recorded=2026-09-12\ncommit=abc\nmain.percent=3.1\n")
        assertThrows(IllegalArgumentException::class.java) { DuplicationCheckSupport.readBaseline(file) }
    }

    // ---- exemptions.txt ------------------------------------------------------------------------

    @Test
    fun readExemptionsParsesReasonedPairs() {
        val file = tempDir.resolve("exemptions.txt")
        file.writeText(
            """
            # deliberate flat/structured renderer twins
            narrativetrace-clarity/src/main/java/**/ValueRenderer.java :: narrativetrace-clarity/src/main/java/**/StructuredValueRenderer.java

            # hostile corpus builders share fixture scaffolding on purpose
            narrativetrace-security-tests/**/*CorpusBuilder.java :: narrativetrace-security-tests/**/*CorpusBuilder.java
            """.trimIndent()
        )
        val exemptions = DuplicationCheckSupport.readExemptions(file)
        assertEquals(2, exemptions.size)
        assertEquals("deliberate flat/structured renderer twins", exemptions[0].reason)
        assertTrue(exemptions[0].globA.endsWith("ValueRenderer.java"))
        assertTrue(exemptions[0].globB.endsWith("StructuredValueRenderer.java"))
    }

    @Test
    fun readExemptionsMissingFileReturnsEmpty() {
        assertTrue(DuplicationCheckSupport.readExemptions(tempDir.resolve("missing.txt")).isEmpty())
    }

    @Test
    fun readExemptionsPairWithoutReasonThrows() {
        val file = tempDir.resolve("exemptions.txt")
        file.writeText("a/File.java :: b/File.java\n")
        assertThrows(IllegalArgumentException::class.java) { DuplicationCheckSupport.readExemptions(file) }
    }

    @Test
    fun readExemptionsMalformedPairThrows() {
        val file = tempDir.resolve("exemptions.txt")
        file.writeText("# reason\nonly-one-glob.java\n")
        assertThrows(IllegalArgumentException::class.java) { DuplicationCheckSupport.readExemptions(file) }
    }

    @Test
    fun readExemptionsConcatenatesMultiLineReason() {
        val file = tempDir.resolve("exemptions.txt")
        file.writeText("# first line\n# second line\na/File.java :: b/File.java\n")
        val exemptions = DuplicationCheckSupport.readExemptions(file)
        assertEquals("first line second line", exemptions[0].reason)
    }

    // ---- isExempt --------------------------------------------------------------------------------

    @Test
    fun isExemptMatchesEitherOrderOfThePair() {
        val exemption = DuplicationExemption("a/*.java", "b/*.java", "twins")
        val clusterAB = cluster(80, "a/X.java", "b/Y.java")
        val clusterBA = cluster(80, "b/Y.java", "a/X.java")
        assertTrue(DuplicationCheckSupport.isExempt(clusterAB, listOf(exemption)))
        assertTrue(DuplicationCheckSupport.isExempt(clusterBA, listOf(exemption)))
    }

    @Test
    fun isExemptFalseWhenNoOccurrenceMatches() {
        val exemption = DuplicationExemption("a/*.java", "b/*.java", "twins")
        val cluster = cluster(80, "c/X.java", "d/Y.java")
        assertFalse(DuplicationCheckSupport.isExempt(cluster, listOf(exemption)))
    }

    @Test
    fun isExemptFalseForAThreeWayClusterWithOneUncoveredOccurrence() {
        val exemption = DuplicationExemption("a/*.java", "b/*.java", "twins")
        val cluster = cluster(80, "a/X.java", "b/Y.java", "c/Z.java")
        assertFalse(DuplicationCheckSupport.isExempt(cluster, listOf(exemption)))
    }

    // ---- decide ------------------------------------------------------------------------------

    private val baseline = DuplicationBaseline(mainPercent = 3.0, mainLargestCluster = 100, recorded = "x", commit = "y")

    @Test
    fun decidePassesWhenWithinToleranceAndNoLargerCluster() {
        val result = DuplicationCheckSupport.decide(
            main = tree(3.2, listOf(cluster(90, "a/X.java", "b/Y.java"))),
            baseline = baseline,
            exemptions = emptyList()
        )
        assertTrue(result.passed)
    }

    @Test
    fun decideFailsWhenPercentRisesPastTolerance() {
        val result = DuplicationCheckSupport.decide(
            main = tree(3.4, emptyList()),
            baseline = baseline,
            exemptions = emptyList()
        )
        assertFalse(result.passed)
        assertTrue(result.message.contains("rose to"))
    }

    @Test
    fun decidePassesAtExactlyTheTolerance() {
        val result = DuplicationCheckSupport.decide(
            main = tree(3.3, emptyList()),
            baseline = baseline,
            exemptions = emptyList()
        )
        assertTrue(result.passed)
    }

    @Test
    fun decideFailsOnANewClusterLargerThanBaseline() {
        val result = DuplicationCheckSupport.decide(
            main = tree(3.0, listOf(cluster(150, "a/X.java", "b/Y.java"))),
            baseline = baseline,
            exemptions = emptyList()
        )
        assertFalse(result.passed)
        assertTrue(result.message.contains("new cluster 150 tokens"))
    }

    @Test
    fun decidePassesWhenTheLargerClusterIsExempted() {
        val result = DuplicationCheckSupport.decide(
            main = tree(3.0, listOf(cluster(150, "a/X.java", "b/Y.java"))),
            baseline = baseline,
            exemptions = listOf(DuplicationExemption("a/*.java", "b/*.java", "deliberate twins"))
        )
        assertTrue(result.passed)
    }

    @Test
    fun decideNeverConsidersTestTree() {
        // decide() only ever receives the main tree — the signature itself enforces "tests never
        // gate"; this test documents that a huge test-tree percentage cannot even be passed in.
        val result = DuplicationCheckSupport.decide(
            main = tree(3.0, emptyList()),
            baseline = baseline,
            exemptions = emptyList()
        )
        assertTrue(result.passed)
    }
}
