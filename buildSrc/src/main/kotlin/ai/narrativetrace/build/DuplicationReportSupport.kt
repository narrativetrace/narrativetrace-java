/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import groovy.json.JsonSlurper
import net.sourceforge.pmd.cpd.CPDConfiguration
import net.sourceforge.pmd.cpd.CPDReport
import net.sourceforge.pmd.cpd.CpdAnalysis
import net.sourceforge.pmd.cpd.Match
import net.sourceforge.pmd.cpd.XMLRenderer
import java.io.File
import java.nio.charset.StandardCharsets

/** One occurrence of a duplicated block, relative to the repository root (`module/src/.../File.java`). */
data class DuplicationOccurrence(val file: String, val startLine: Int, val endLine: Int)

/** One CPD match: an identical token sequence found at every one of [occurrences]. */
data class DuplicationCluster(val tokens: Int, val lines: Int, val occurrences: List<DuplicationOccurrence>)

/** One source tree's scan (main or test): CPD's own token count plus this build's clusters/percent. */
data class DuplicationTreeResult(
    val tokensTotal: Int,
    val tokensDuplicated: Int,
    val percent: Double,
    val clusters: List<DuplicationCluster>
)

/** The whole `duplication.json` document: the family-wide schema every NarrativeTrace runtime emits. */
data class DuplicationScanResult(
    val minTokens: Int,
    val main: DuplicationTreeResult,
    val test: DuplicationTreeResult
)

/**
 * INTENT: Backs the root `duplicationReport` task — runs PMD's CPD (Copy/Paste Detector, part of the
 * PMD distribution this build already depends on for `pmdMain`/`pmdTest`) over a source tree via the
 * `net.sourceforge.pmd.cpd` API directly (`CpdAnalysis`), the same "library, not a plugin" idiom
 * `JDependReportSupport` already uses for JDepend. Identifiers and literals are ignored — CPD then
 * finds *structural* duplication (the same shape with different names/values), not merely pasted
 * text — and results are ignored below a token floor: see documentation/duplication.md for the
 * rationale.
 *
 * The whole PMD/CPD invocation ([runCpd]) is thin, untested glue over the library, matching the
 * `PmdViolationSupport`/`JDependReportSupport` convention of testing the pure decision logic
 * ([aggregate], [writeJson], [readJson], [summaryLine]) rather than a live third-party tool run — the
 * real tool is proven by the actual gate, not a unit test that would just re-run it.
 */
object DuplicationReportSupport {

    const val TOOL = "pmd-cpd"
    const val LANGUAGE = "java"

    /**
     * Runs CPD over [sourceDirs] (non-existent directories are skipped, not an error — a Kotlin-only
     * module or one with no test sources contributes nothing) at [minTokens], with identifiers and
     * literals ignored. Writes PMD's own CPD XML to [xmlOutput] unmodified, and returns the
     * normalised result [writeJson] serialises to `duplication.json`.
     */
    fun runCpd(sourceDirs: List<File>, minTokens: Int, rootDir: File, xmlOutput: File): DuplicationTreeResult {
        xmlOutput.parentFile?.mkdirs()
        val existingDirs = sourceDirs.filter { it.isDirectory }
        if (existingDirs.isEmpty()) {
            xmlOutput.writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<pmd-cpd/>\n")
            return DuplicationTreeResult(0, 0, 0.0, emptyList())
        }

        val configuration = CPDConfiguration()
        configuration.setOnlyRecognizeLanguage(configuration.languageRegistry.getLanguageById(LANGUAGE))
        configuration.minimumTileSize = minTokens
        // Called as methods, not Kotlin `var` properties: CPDConfiguration also declares private
        // fields named `ignoreIdentifiers`/`ignoreLiterals`, and Kotlin's Java-property synthesis
        // resolves the property syntax to those (inaccessible) fields rather than the public
        // isX()/setX() accessor pair in this case.
        configuration.setIgnoreIdentifiers(true)
        configuration.setIgnoreLiterals(true)
        configuration.sourceEncoding = StandardCharsets.UTF_8
        configuration.collectFilesRecursively(true)
        configuration.inputPathList = existingDirs.map { it.toPath() }

        var cpdReport: CPDReport? = null
        CpdAnalysis.create(configuration).use { analysis -> analysis.performAnalysis { r -> cpdReport = r } }
        val report = cpdReport ?: throw IllegalStateException("CPD produced no report for $existingDirs")

        xmlOutput.writer(StandardCharsets.UTF_8).use { writer -> XMLRenderer().render(report, writer) }

        val tokensTotal = report.numberOfTokensPerFile.values.sum()
        val clusters = report.matches.map { match -> toCluster(match, rootDir) }
        // CPD's matches routinely overlap (a long clone subsumes shorter ones inside it, or the same
        // lines appear in several different pairs) — begin/end token indices are one running count
        // across the whole scanned corpus (CPD tokenizes every file into one shared list before
        // matching), so every mark of every match contributes one span in that single coordinate
        // space, and the union below counts each token position once no matter how many matches
        // cover it.
        val spans = report.matches.flatMap { match -> match.map { it.beginTokenIndex until it.endTokenIndex } }
        val tokensDuplicated = countCoveredPositions(spans)
        return aggregate(tokensTotal, tokensDuplicated, clusters)
    }

    private fun toCluster(match: Match, rootDir: File): DuplicationCluster {
        val rootPath = rootDir.toPath()
        val occurrences = match.map { mark ->
            val location = mark.location
            val absolute = File(location.fileId.absolutePath).toPath()
            val relative =
                if (absolute.startsWith(rootPath)) rootPath.relativize(absolute).toString() else absolute.toString()
            DuplicationOccurrence(relative.replace('\\', '/'), location.startLine, location.endLine)
        }
        return DuplicationCluster(match.tokenCount, match.lineCount, occurrences)
    }

    /**
     * Aggregates already-deduplicated totals and raw clusters into the tree-level result. Pure (no
     * I/O, no PMD types) — this, and [countCoveredPositions] below, is what the test suite exercises
     * directly, independent of running CPD.
     *
     * [tokensDuplicated] must already be a union count (see [countCoveredPositions]), not a per-
     * cluster sum: CPD's matches overlap routinely (a long clone contains shorter ones, the same
     * lines appear in several pairs), and summing `tokens * occurrences` over every cluster double-
     * and triple-counts those positions — verified on this repository's own first scan, where the
     * naive sum reported over 300% duplication. CPD's own output has no built-in percentage; a union
     * over token positions is this build's definition of one, applied identically to every run so
     * the ratchet in `duplicationCheck` always compares like with like, and so the number can never
     * exceed 100%.
     */
    fun aggregate(tokensTotal: Int, tokensDuplicated: Int, clusters: List<DuplicationCluster>): DuplicationTreeResult {
        val sorted = clusters.sortedByDescending { it.tokens }
        val percent = if (tokensTotal == 0) 0.0 else tokensDuplicated.toDouble() / tokensTotal * 100
        return DuplicationTreeResult(tokensTotal, tokensDuplicated, roundToOneDecimal(percent), sorted)
    }

    /**
     * How many distinct integer positions the half-open [spans] cover, counting a position once no
     * matter how many spans include it — the union, not the sum. `runCpd` feeds this CPD's own
     * token-index ranges (one shared coordinate space across the whole scanned corpus) to turn a
     * pile of overlapping matches into a duplication count that is bounded by the corpus size.
     */
    fun countCoveredPositions(spans: List<IntRange>): Int {
        if (spans.isEmpty()) return 0
        val sorted = spans.sortedBy { it.first }
        var covered = 0
        var currentStart = sorted[0].first
        var currentEnd = sorted[0].last + 1
        for (span in sorted.drop(1)) {
            val start = span.first
            val end = span.last + 1
            if (start > currentEnd) {
                covered += currentEnd - currentStart
                currentStart = start
                currentEnd = end
            } else if (end > currentEnd) {
                currentEnd = end
            }
        }
        return covered + (currentEnd - currentStart)
    }

    private fun roundToOneDecimal(value: Double): Double = Math.round(value * 10) / 10.0

    fun writeJson(scan: DuplicationScanResult, file: File) {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"tool\":\"$TOOL\",")
        sb.append("\"language\":\"$LANGUAGE\",")
        sb.append("\"minTokens\":${scan.minTokens},")
        sb.append("\"main\":")
        appendTree(sb, scan.main)
        sb.append(",")
        sb.append("\"test\":")
        appendTree(sb, scan.test)
        sb.append("}")
        file.parentFile?.mkdirs()
        file.writeText(sb.toString())
    }

    private fun appendTree(sb: StringBuilder, tree: DuplicationTreeResult) {
        sb.append("{")
        sb.append("\"tokensTotal\":${tree.tokensTotal},")
        sb.append("\"tokensDuplicated\":${tree.tokensDuplicated},")
        sb.append("\"percent\":${tree.percent},")
        sb.append("\"clusters\":[")
        tree.clusters.forEachIndexed { i, cluster ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"tokens\":${cluster.tokens},")
            sb.append("\"lines\":${cluster.lines},")
            sb.append("\"occurrences\":[")
            cluster.occurrences.forEachIndexed { j, occurrence ->
                if (j > 0) sb.append(",")
                sb.append(
                    "{\"file\":\"${jsonEscape(occurrence.file)}\"," +
                        "\"startLine\":${occurrence.startLine},\"endLine\":${occurrence.endLine}}"
                )
            }
            sb.append("]}")
        }
        sb.append("]}")
    }

    private fun jsonEscape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    @Suppress("UNCHECKED_CAST")
    fun readJson(file: File): DuplicationScanResult {
        val root = JsonSlurper().parseText(file.readText()) as Map<String, Any?>
        val minTokens = (root["minTokens"] as Number).toInt()
        return DuplicationScanResult(minTokens, readTree(root, "main"), readTree(root, "test"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun readTree(root: Map<String, Any?>, key: String): DuplicationTreeResult {
        val tree = root[key] as Map<String, Any?>
        val clusters = (tree["clusters"] as List<Map<String, Any?>>).map { cluster ->
            val occurrences = (cluster["occurrences"] as List<Map<String, Any?>>).map { occurrence ->
                DuplicationOccurrence(
                    file = occurrence["file"] as String,
                    startLine = (occurrence["startLine"] as Number).toInt(),
                    endLine = (occurrence["endLine"] as Number).toInt()
                )
            }
            DuplicationCluster(
                tokens = (cluster["tokens"] as Number).toInt(),
                lines = (cluster["lines"] as Number).toInt(),
                occurrences = occurrences
            )
        }
        return DuplicationTreeResult(
            tokensTotal = (tree["tokensTotal"] as Number).toInt(),
            tokensDuplicated = (tree["tokensDuplicated"] as Number).toInt(),
            percent = (tree["percent"] as Number).toDouble(),
            clusters = clusters
        )
    }

    /**
     * The one Gradle-log summary line: main's percent/cluster count/largest cluster (the thing
     * `duplicationCheck` acts on), then test's (reported only — see documentation/duplication.md).
     */
    fun summaryLine(scan: DuplicationScanResult): String {
        val main = scan.main
        val test = scan.test
        val largest = main.clusters.maxByOrNull { it.tokens }
        val largestDesc = if (largest == null) {
            "no clusters"
        } else {
            "largest ${largest.tokens} tokens " + largest.occurrences.joinToString(" ↔ ") {
                "${it.file}:${it.startLine}"
            }
        }
        return "duplication: main ${formatPercent(main.percent)}% of tokens in ${main.clusters.size} " +
            "clusters ($largestDesc) · test ${formatPercent(test.percent)}% in ${test.clusters.size} " +
            "clusters (reported, not gated)"
    }

    private fun formatPercent(value: Double): String = "%.1f".format(value)
}
