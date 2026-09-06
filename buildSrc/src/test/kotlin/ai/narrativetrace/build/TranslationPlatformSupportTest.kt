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

class TranslationPlatformSupportTest {

    @TempDir
    lateinit var repo: File

    private fun write(relative: String, content: String) {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun runAllDegradesToStalenessOnlyWithOneWarningWhenTheManifestIsAbsent() {
        write("documentation/guide.md", "# Guide\n")
        val hash = TranslationCheckSupport.gitBlobHash("# Guide\n".toByteArray()).take(12)
        write(
            "documentation/es/guide.md",
            "<!-- source: documentation/guide.md blob $hash | translated: 2026-08-13 -->\n# Guía\n"
        )

        val result = TranslationPlatformSupport.runAll(repo)

        assertEquals(emptyList<String>(), result.failures)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("no manifest"))
    }

    @Test
    fun runAllStillCatchesAStaleTranslationWhenNoManifestIsPresent() {
        write("documentation/guide.md", "# Guide\n")
        write(
            "documentation/es/guide.md",
            "<!-- source: documentation/guide.md blob aaaaaaaaaaaa | translated: 2026-08-13 -->\n# Guía\n"
        )

        val result = TranslationPlatformSupport.runAll(repo)

        assertEquals(1, result.failures.size)
        assertTrue(result.failures[0].contains("stale"))
    }

    private fun writeManifest() {
        write(
            I18nManifestSupport.MANIFEST_RELATIVE_PATH,
            """
            {
              "sourceLanguage": "en",
              "languages": [
                {"code": "es", "displayName": "Español", "directory": "documentation/es",
                 "index": "documentation/LEAME.md", "rootReadme": "LEAME.md", "status": "complete"}
              ],
              "documents": [
                {"source": "documentation/guide.md", "translations": {"es": "guia.md"}}
              ]
            }
            """.trimIndent()
        )
    }

    @Test
    fun runAllCombinesStalenessCompletenessStructureAndIndexFailuresWhenTheManifestIsPresent() {
        writeManifest()
        // No documentation/es/guia.md at all: a hard completeness failure (complete language).
        // No documentation/LEAME.md either: a hard index failure (complete language, no sibling index).

        val result = TranslationPlatformSupport.runAll(repo)

        assertTrue(result.failures.any { it.contains("guia.md") })
        assertTrue(result.failures.any { it.contains("LEAME.md") })
    }

    @Test
    fun runAllWarnsTheReviewSummaryWhenTheManifestIsPresent() {
        writeManifest()
        write("documentation/guide.md", "# Guide\n")
        val hash = TranslationCheckSupport.gitBlobHash("# Guide\n".toByteArray()).take(12)
        write(
            "documentation/es/guia.md",
            "<!-- source: documentation/guide.md blob $hash | translated: 2026-08-13 | reviewed: 2026-09-01 -->\n# Guía\n"
        )
        write(
            "documentation/LEAME.md",
            "# Documentación\n\n[English](README.md) | **Español**\n\n| Documento |\n|---|\n| [Guía](es/guia.md) |\n"
        )
        write("documentation/README.md", "# Docs\n\n[English](README.md) | [Español](LEAME.md)\n")

        val result = TranslationPlatformSupport.runAll(repo)

        assertEquals(emptyList<String>(), result.failures)
        assertTrue(result.warnings.any { it.contains("unreviewed") })
    }
}
