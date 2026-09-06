/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import groovy.json.JsonSlurper
import java.io.File

/** Translation status a language declares in the manifest — see [I18nManifestSupport]. */
enum class I18nStatus(val label: String) {
    COMPLETE("complete"),
    IN_PROGRESS("in-progress");

    companion object {
        /** Parses the manifest's `status` string; throws on any value other than `complete`/`in-progress`. */
        fun parse(raw: String): I18nStatus =
            values().find { it.label == raw } ?: throw IllegalArgumentException(
                "documentation/i18n/manifest.json: unknown language status '$raw' " +
                    "(expected 'complete' or 'in-progress')"
            )
    }
}

/** One language's coordinates in the manifest — directory, sibling index, root README, declared status. */
data class I18nLanguage(
    val code: String,
    val displayName: String,
    val directory: String,
    val index: String,
    val rootReadme: String,
    val status: I18nStatus,
)

/** One user document's English source and the native filename each language has translated it to, if any. */
data class I18nDocument(val source: String, val translations: Map<String, String>)

/** The parsed manifest: every declared language plus the document set translation coverage is measured against. */
data class I18nManifest(val sourceLanguage: String, val languages: List<I18nLanguage>, val documents: List<I18nDocument>) {
    /** The declared language with this code, or `null` when the manifest does not declare it. */
    fun language(code: String): I18nLanguage? = languages.find { it.code == code }
}

/**
 * INTENT: Loads `documentation/i18n/manifest.json` — the machine-readable declaration of which
 * languages this repository translates into, where each language's files live, and which English
 * documents are in scope for translation. Every other i18n check (completeness, index/menu
 * integrity, the review dashboard) reads this manifest rather than re-deriving the same facts.
 *
 * The manifest lives under `documentation/` (not in a private working-notes directory) so it
 * survives the public snapshot — `documentation/i18n/` is already carved out of
 * `TranslationCheckSupport`'s language-directory scan, so adding this file here does not make
 * `translationCheck` mistake it for a translated document.
 *
 * @llmNote Uses `groovy.json.JsonSlurper` (already on the Gradle/buildSrc classpath) rather than
 * adding a JSON dependency — the same choice already made by `JDependReportSupport` and
 * `BenchmarkResultSupport` in this package.
 */
object I18nManifestSupport {

    /** Where the manifest lives, relative to the repository root. */
    const val MANIFEST_RELATIVE_PATH = "documentation/i18n/manifest.json"

    /**
     * Parses the manifest at [repoRoot]/[MANIFEST_RELATIVE_PATH], or `null` when the file is absent —
     * callers degrade gracefully rather than failing the build over a manifest that has not been
     * introduced yet.
     *
     * @llmNote A present-but-malformed manifest is a configuration error, not a graceful-degradation
     * case: this throws (fail fast and loud), it does not return `null`.
     */
    fun loadOrNull(repoRoot: File): I18nManifest? {
        val file = repoRoot.resolve(MANIFEST_RELATIVE_PATH)
        if (!file.isFile) {
            return null
        }
        val root = JsonSlurper().parseText(file.readText()) as Map<*, *>
        return I18nManifest(
            sourceLanguage = root["sourceLanguage"] as? String ?: "en",
            languages = (root["languages"] as List<*>).map { parseLanguage(it as Map<*, *>) },
            documents = (root["documents"] as List<*>).map { parseDocument(it as Map<*, *>) }
        )
    }

    private fun parseLanguage(raw: Map<*, *>): I18nLanguage =
        I18nLanguage(
            code = raw["code"] as String,
            displayName = raw["displayName"] as String,
            directory = raw["directory"] as String,
            index = raw["index"] as String,
            rootReadme = raw["rootReadme"] as String,
            status = I18nStatus.parse(raw["status"] as String)
        )

    private fun parseDocument(raw: Map<*, *>): I18nDocument {
        @Suppress("UNCHECKED_CAST")
        val translations = (raw["translations"] as? Map<String, String>).orEmpty()
        return I18nDocument(source = raw["source"] as String, translations = translations)
    }
}
