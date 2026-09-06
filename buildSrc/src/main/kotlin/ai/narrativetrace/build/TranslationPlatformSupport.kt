/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/** Everything the `translationCheck` task reports: hard failures (fail the build) and warnings (printed, never fail it). */
data class TranslationPlatformResult(val failures: List<String>, val warnings: List<String>)

/**
 * INTENT: The single orchestrator behind the `translationCheck` task — ties together staleness
 * (`TranslationCheckSupport`, unconditional), and, when `documentation/i18n/manifest.json` is
 * present, completeness (`TranslationCompletenessSupport`), structure parity
 * (`TranslationStructureSupport`), index/menu integrity (`TranslationIndexSupport`) and the
 * review-field summary (`TranslationReviewSupport`).
 *
 * @llmNote Graceful degradation is deliberate and tested: a repository (or a port that has not yet
 * adopted the manifest) with no `documentation/i18n/manifest.json` still gets the original
 * staleness-only check, plus exactly one warning saying why nothing else ran — never a crash, never
 * a silent skip.
 */
object TranslationPlatformSupport {

    /** Runs every translation check the current tree supports; never throws — callers decide what to do with it. */
    fun runAll(repoRoot: File): TranslationPlatformResult {
        val staleness = TranslationCheckSupport.check(repoRoot)
        val manifest = I18nManifestSupport.loadOrNull(repoRoot)
            ?: return TranslationPlatformResult(
                staleness,
                listOf(
                    "translationCheck: no manifest at ${I18nManifestSupport.MANIFEST_RELATIVE_PATH} — " +
                        "ran the staleness-only check"
                )
            )
        val completeness = TranslationCompletenessSupport.check(repoRoot, manifest)
        val structure = TranslationStructureSupport.checkAll(repoRoot)
        val index = TranslationIndexSupport.checkAll(repoRoot, manifest)
        val failures = staleness + completeness.failures + structure.failures + index
        val warnings = completeness.warnings + structure.warnings + TranslationReviewSupport.summaryLine(repoRoot)
        return TranslationPlatformResult(failures, warnings)
    }
}
