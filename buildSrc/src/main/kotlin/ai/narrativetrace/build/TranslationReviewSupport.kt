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
 * INTENT: Backs the `| reviewed:` half of the translation platform — a translation can be
 * structurally in sync with its source (what `translationCheck`'s other halves verify) and still be
 * a machine translation nobody fluent has read. The header's optional `| reviewed: <date|->` clause
 * is how a native-speaker pass gets recorded; this class reads it back.
 *
 * @llmNote Deliberately warn-only everywhere in this file — per-commit review status is a nudge
 * (`summaryLine`), not a gate. Gating publish on it is an explicit owner decision this platform does
 * not make (see `documentation/i18n/manifest.json`'s introduction in planning history).
 */
object TranslationReviewSupport {

    /** Every discovered translation with no `| reviewed:` date — absent and `| reviewed: -` both count. */
    fun unreviewed(repoRoot: File): List<String> =
        TranslationCheckSupport.translatedFiles(repoRoot)
            .filter { isUnreviewed(it) }
            .map { repoRoot.toPath().relativize(it.toPath()).toString().replace('\\', '/') }
            .sorted()

    private fun isUnreviewed(file: File): Boolean {
        val header = TranslationCheckSupport.parseHeader(file.bufferedReader().use { it.readLine() }.orEmpty())
        return header != null && header.reviewed == null
    }

    /** The one-line, warn-only summary `translationCheck` prints every build. */
    fun summaryLine(repoRoot: File): String {
        val count = unreviewed(repoRoot).size
        return "translationCheck: $count translated document(s) unreviewed " +
            "(run ./gradlew translationStatus for the full list)"
    }

    /** The human dashboard `translationStatus` prints: every language's coverage and review counts. */
    fun statusReport(repoRoot: File, manifest: I18nManifest?): String {
        if (manifest == null) {
            return "translationStatus: no manifest at ${I18nManifestSupport.MANIFEST_RELATIVE_PATH}"
        }
        val lines = mutableListOf("translationStatus:")
        manifest.languages.forEach { lines += languageLine(repoRoot, manifest, it) }
        return lines.joinToString("\n")
    }

    private fun languageLine(repoRoot: File, manifest: I18nManifest, language: I18nLanguage): String {
        val total = manifest.documents.size
        val translated = manifest.documents.count { it.translations.containsKey(language.code) }
        val unreviewedInLanguage = unreviewed(repoRoot).count { it.contains("/${language.code}/") }
        return "  ${language.code} (${language.status.label}): $translated/$total translated, " +
            "$unreviewedInLanguage unreviewed"
    }
}
