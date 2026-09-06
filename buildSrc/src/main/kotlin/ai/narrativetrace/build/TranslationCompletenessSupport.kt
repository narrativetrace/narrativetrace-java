/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/** One language's completeness result: hard failures (always) plus a warning when the language is in-progress. */
data class CompletenessResult(val failures: List<String>, val warnings: List<String>)

/**
 * INTENT: Backs the manifest-driven half of `translationCheck` — for every document the manifest
 * declares in scope, verifies each language either has it or is allowed not to yet.
 *
 * A `complete` language missing a document fails the build: "complete" is a public claim ("this
 * language has full parity"), and a silent gap behind that claim is exactly the failure mode this
 * platform exists to catch. An `in-progress` language missing documents only warns, with the exact
 * list, so the remaining work stays visible without blocking unrelated commits.
 *
 * A manifest entry that names a translation file which does not exist on disk is a *hard* failure
 * regardless of the language's status — that is not an incomplete translation, it is the manifest
 * lying about the tree, and "in-progress" never excuses that.
 */
object TranslationCompletenessSupport {

    /** Checks every language the manifest declares against every document it declares. */
    fun check(repoRoot: File, manifest: I18nManifest): CompletenessResult {
        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        for (language in manifest.languages) {
            checkLanguage(repoRoot, manifest, language, failures, warnings)
        }
        return CompletenessResult(failures.sorted(), warnings.sorted())
    }

    private fun checkLanguage(
        repoRoot: File,
        manifest: I18nManifest,
        language: I18nLanguage,
        failures: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val missing = mutableListOf<String>()
        for (document in manifest.documents) {
            val translated = document.translations[language.code]
            if (translated == null) {
                missing += document.source
            } else if (!existsUnderLanguageDirectory(repoRoot, language, translated)) {
                failures += "${language.code}: manifest declares '${language.directory}/$translated' " +
                    "for ${document.source} but the file does not exist"
            }
        }
        if (missing.isEmpty()) {
            return
        }
        val message = "${language.code} (${language.status.label}): missing translation of " +
            missing.joinToString(", ")
        if (language.status == I18nStatus.COMPLETE) {
            failures += message
        } else {
            warnings += message
        }
    }

    private fun existsUnderLanguageDirectory(repoRoot: File, language: I18nLanguage, filename: String): Boolean =
        repoRoot.resolve(language.directory).resolve(filename).isFile
}
