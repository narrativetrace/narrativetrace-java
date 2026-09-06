/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/** One document's structural shape: heading levels in order, fenced code blocks verbatim, table shapes. */
data class StructureProfile(
    val headingLevels: List<Int>,
    val codeBlocks: List<String>,
    val tables: List<Pair<Int, Int>>,
)

/** Structural problems between a translation and its source: hard [failures] and softer [warnings]. */
data class StructureComparison(val failures: List<String>, val warnings: List<String>)

/**
 * INTENT: Backs the structure-parity half of `translationCheck` — a translation may say anything it
 * wants in prose, but its mechanical skeleton (heading tree, code sample count, links, table shapes)
 * must mirror its English source. A drifted heading tree, a vanished code sample, a table missing a
 * row, or a link that no longer resolves is usually a translation that fell behind a source edit —
 * those are hard [StructureComparison.failures].
 *
 * @llmNote A fenced code block's *content* is deliberately only a [StructureComparison.warnings]
 * signal, not a failure, once its block *count* already matches its source. Real translations in
 * this repository legitimately vary code-block content in three ways the i18n terminology
 * conventions sanction or that plain observation of the shipped `es`/`zh-CN` guides confirms:
 * comments are translated, `<placeholder>` labels inside illustrative syntax are localized, and a
 * long comment can rewrap onto a different number of lines in the target language. A demonstration
 * command can even legitimately change a literal argument (`--lang es` vs `--lang zh-CN`) when the
 * block exists to
 * show the reader that exact command. None of that is machine-distinguishable from a real drift with
 * dependency-free line parsing, so content drift is surfaced for a human to read, never used to fail
 * the build; only the block *count* (a whole example gained or lost) is a hard failure.
 */
object TranslationStructureSupport {

    private val HEADING = Regex("""^(#{1,6})\s""")
    private val LINK_TARGET = Regex("""\[[^\]]*]\(([^)]+)\)""")
    private val EXTERNAL_SCHEME = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):""")

    /** Parses [text] into its structural shape, skipping anything inside a fenced code block. */
    fun profile(text: String): StructureProfile {
        val headingLevels = mutableListOf<Int>()
        val codeBlocks = mutableListOf<String>()
        val tables = mutableListOf<Pair<Int, Int>>()
        var fence: MutableList<String>? = null
        var table = mutableListOf<String>()
        for (line in text.lines()) {
            fence = when {
                fence != null && isFenceMarker(line) -> {
                    codeBlocks += fence.joinToString("\n")
                    null
                }
                fence != null -> fence.also { it += line }
                isFenceMarker(line) -> mutableListOf(line)
                isTableLine(line) -> { table += line; null }
                else -> {
                    flushTable(table, tables)
                    table = mutableListOf()
                    headingLevel(line)?.let { headingLevels += it }
                    null
                }
            }
        }
        flushTable(table, tables)
        return StructureProfile(headingLevels, codeBlocks, tables)
    }

    private fun isFenceMarker(line: String): Boolean = line.trimStart().startsWith("```")

    private fun headingLevel(line: String): Int? = HEADING.find(line)?.groupValues?.get(1)?.length

    private fun isTableLine(line: String): Boolean = line.trimStart().startsWith("|")

    private fun isSeparatorLine(line: String): Boolean =
        line.isNotBlank() && line.all { it in "|-: \t" } && line.contains('-')

    private fun flushTable(lines: List<String>, into: MutableList<Pair<Int, Int>>) {
        if (lines.size < 2 || !isSeparatorLine(lines[1])) {
            return
        }
        into += (lines.size - 2) to columnCount(lines[0])
    }

    private fun columnCount(headerLine: String): Int =
        headerLine.trim().removePrefix("|").removeSuffix("|").split("|").size

    /** Structural differences between [translation] (labeled [translationLabel]) and its [source]. */
    fun compare(source: StructureProfile, translation: StructureProfile, sourceLabel: String, translationLabel: String): StructureComparison {
        val failures = mutableListOf<String>()
        failures += compareHeadings(source.headingLevels, translation.headingLevels, sourceLabel, translationLabel)
        failures += compareTables(source.tables, translation.tables, sourceLabel, translationLabel)
        val (codeFailures, codeWarnings) = compareCodeBlocks(source.codeBlocks, translation.codeBlocks, sourceLabel, translationLabel)
        failures += codeFailures
        return StructureComparison(failures, codeWarnings)
    }

    private fun compareHeadings(source: List<Int>, translation: List<Int>, sourceLabel: String, translationLabel: String): List<String> {
        if (source.size != translation.size) {
            return listOf(
                "$translationLabel: heading count ${translation.size} vs $sourceLabel's ${source.size}"
            )
        }
        return source.indices.filter { source[it] != translation[it] }.map { i ->
            "$translationLabel: heading ${i + 1} is level ${translation[i]}, " +
                "$sourceLabel's heading ${i + 1} is level ${source[i]}"
        }
    }

    /** Block *count* mismatches are failures (a whole example vanished); content drift is a warning — see the class doc. */
    private fun compareCodeBlocks(source: List<String>, translation: List<String>, sourceLabel: String, translationLabel: String): Pair<List<String>, List<String>> {
        if (source.size != translation.size) {
            val failure = "$translationLabel: code block count ${translation.size} vs $sourceLabel's ${source.size}"
            return listOf(failure) to emptyList()
        }
        val warnings = source.indices.filter { source[it] != translation[it] }.map { i ->
            "$translationLabel: code block ${i + 1} differs from $sourceLabel — verify by hand " +
                "(translated comments, localized placeholders and per-language example values are expected)"
        }
        return emptyList<String>() to warnings
    }

    private fun compareTables(source: List<Pair<Int, Int>>, translation: List<Pair<Int, Int>>, sourceLabel: String, translationLabel: String): List<String> {
        if (source.size != translation.size) {
            return listOf("$translationLabel: table count ${translation.size} vs $sourceLabel's ${source.size}")
        }
        return source.indices.mapNotNull { i -> tableShapeMismatch(source[i], translation[i], i, sourceLabel, translationLabel) }
    }

    private fun tableShapeMismatch(source: Pair<Int, Int>, translation: Pair<Int, Int>, index: Int, sourceLabel: String, translationLabel: String): String? {
        if (source == translation) {
            return null
        }
        val parts = mutableListOf<String>()
        if (source.first != translation.first) parts += "rows ${translation.first} vs ${source.first}"
        if (source.second != translation.second) parts += "columns ${translation.second} vs ${source.second}"
        return "$translationLabel: table ${index + 1} shape mismatches $sourceLabel (${parts.joinToString(", ")})"
    }

    /** Relative links in [file] that do not resolve to a file or directory on disk; external/anchor links are skipped. */
    fun brokenLinks(file: File, repoRoot: File): List<String> {
        val label = repoRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/')
        val nonFenced = withoutFencedLines(file.readText())
        return LINK_TARGET.findAll(nonFenced)
            .map { it.groupValues[1].substringBefore('#').trim() }
            .filter { it.isNotEmpty() && !EXTERNAL_SCHEME.containsMatchIn(it) }
            .filterNot { val target = file.parentFile.resolve(it); target.isFile || target.isDirectory }
            .distinct()
            .map { "$label: link target '$it' does not resolve" }
            .toList()
    }

    private fun withoutFencedLines(text: String): String {
        var fenced = false
        return text.lines().filter { line ->
            val marker = isFenceMarker(line)
            if (marker) fenced = !fenced
            !fenced && !marker
        }.joinToString("\n")
    }

    /** Runs structure-parity across every discovered translation with a resolvable header and source. */
    fun checkAll(repoRoot: File): StructureComparison {
        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        TranslationCheckSupport.translatedFiles(repoRoot).forEach { file ->
            val (fileFailures, fileWarnings) = checkOne(repoRoot, file)
            failures += fileFailures
            warnings += fileWarnings
        }
        return StructureComparison(failures.sorted(), warnings.sorted())
    }

    private fun checkOne(repoRoot: File, file: File): StructureComparison {
        val header = TranslationCheckSupport.parseHeader(file.bufferedReader().use { it.readLine() }.orEmpty())
            ?: return StructureComparison(emptyList(), emptyList())
        val source = repoRoot.resolve(header.sourcePath)
        if (!source.isFile) {
            return StructureComparison(emptyList(), emptyList())
        }
        val label = repoRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/')
        val structural = compare(profile(source.readText()), profile(file.readText()), header.sourcePath, label)
        return StructureComparison(structural.failures + brokenLinks(file, repoRoot), structural.warnings)
    }
}
