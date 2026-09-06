/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/**
 * INTENT: Backs the index/menu-integrity half of `translationCheck` — every language index under
 * `documentation/` (the English `README.md` and each translated sibling, e.g. `LEAME.md`) carries a
 * top-of-page language menu and, for the translated siblings, a table of exactly the documents that
 * language has translated. Both are hand-edited prose that drifts silently: a menu that still says
 * plain "Português" after the Portuguese index ships, or an index row for a guide that was renamed
 * without updating the link, are the two failure modes this check exists to catch.
 *
 * @llmNote An `in-progress` language with no index file yet is not a failure — it has not launched.
 * A `complete` language is a public claim of full parity, so its index must exist and match exactly.
 */
object TranslationIndexSupport {

    private const val ENGLISH_INDEX = "documentation/README.md"
    private const val ENGLISH_NAME = "README.md"
    private val ROW_LINK = Regex("""\[[^\]]*]\(([^)]+)\)""")

    /** The menu line: the first non-blank line after the document's H1, or `null` when there is no H1. */
    fun menuLine(text: String): String? {
        val lines = text.lines()
        val h1 = lines.indexOfFirst { it.trimStart().startsWith("# ") }
        if (h1 == -1) {
            return null
        }
        return lines.drop(h1 + 1).firstOrNull { it.isNotBlank() }?.trim()
    }

    /**
     * The menu this repository's convention prescribes: an English link first — always a link, even
     * from `documentation/README.md`'s own menu, matching the shipped convention — then every
     * manifest language in declared order. The [currentCode] language (`null` for English) renders
     * bold and unlinked, every other language with an existing index renders as a link to it, and
     * every language with no index yet is plain text — matching `documentation/README.md` and
     * `documentation/LEAME.md` today.
     */
    fun expectedMenu(manifest: I18nManifest, repoRoot: File, currentCode: String?): String {
        val segments = mutableListOf("[English]($ENGLISH_NAME)")
        manifest.languages.forEach { segments += languageSegment(repoRoot, it, currentCode) }
        return segments.joinToString(" | ")
    }

    private fun languageSegment(repoRoot: File, language: I18nLanguage, currentCode: String?): String {
        if (language.code == currentCode) {
            return "**${language.displayName}**"
        }
        val indexExists = repoRoot.resolve(language.index).isFile
        return if (indexExists) "[${language.displayName}](${File(language.index).name})" else language.displayName
    }

    /** The link targets named in this index's document table rows — the menu line's own links are excluded. */
    fun documentTargets(text: String): List<String> =
        text.lines()
            .filter { it.trimStart().startsWith("|") && !isSeparatorRow(it) }
            .flatMap { line -> ROW_LINK.findAll(line).map { it.groupValues[1] }.toList() }

    private fun isSeparatorRow(line: String): Boolean =
        line.isNotBlank() && line.all { it in "|-: \t" } && line.contains('-')

    /** Problems in [language]'s own sibling index: existence, menu line, row set, and row link targets. */
    fun checkLanguageIndex(repoRoot: File, manifest: I18nManifest, language: I18nLanguage): List<String> {
        val indexFile = repoRoot.resolve(language.index)
        if (!indexFile.isFile) {
            return if (language.status == I18nStatus.COMPLETE) {
                listOf("${language.index}: missing — '${language.code}' is declared complete but has no sibling index")
            } else {
                emptyList()
            }
        }
        val text = indexFile.readText()
        val problems = mutableListOf<String>()
        checkMenu(language.index, text, expectedMenu(manifest, repoRoot, language.code))?.let { problems += it }
        problems += checkRows(indexFile, language.index, text, manifest, language)
        return problems
    }

    private fun checkMenu(label: String, text: String, expected: String): String? {
        val actual = menuLine(text)
        return if (actual != expected) "$label: language menu is '$actual', expected '$expected'" else null
    }

    private fun checkRows(indexFile: File, label: String, text: String, manifest: I18nManifest, language: I18nLanguage): List<String> {
        val dirName = File(language.directory).name
        val expected = manifest.documents.mapNotNull { it.translations[language.code]?.let { f -> "$dirName/$f" } }.toSet()
        val actual = documentTargets(text).toSet()
        val problems = mutableListOf<String>()
        (expected - actual).sorted().forEach { problems += "$label: missing an index row for '$it'" }
        (actual - expected).sorted().forEach { problems += "$label: index row '$it' is not a manifest document for '${language.code}'" }
        actual.sorted().filterNot { indexFile.parentFile.resolve(it).isFile }
            .forEach { problems += "$label: row target '$it' does not resolve" }
        return problems
    }

    /** Problems in the English `documentation/README.md` menu — its document rows are not manifest-scoped. */
    fun checkEnglishIndexMenu(repoRoot: File, manifest: I18nManifest): List<String> {
        val indexFile = repoRoot.resolve(ENGLISH_INDEX)
        if (!indexFile.isFile) {
            return listOf("$ENGLISH_INDEX: missing")
        }
        val expected = expectedMenu(manifest, repoRoot, null)
        val actual = menuLine(indexFile.readText())
        return if (actual != expected) listOf("$ENGLISH_INDEX: language menu is '$actual', expected '$expected'") else emptyList()
    }

    /** Every index/menu problem: the English menu plus every declared language's own index. */
    fun checkAll(repoRoot: File, manifest: I18nManifest): List<String> =
        checkEnglishIndexMenu(repoRoot, manifest) +
            manifest.languages.flatMap { checkLanguageIndex(repoRoot, manifest, it) }
}
