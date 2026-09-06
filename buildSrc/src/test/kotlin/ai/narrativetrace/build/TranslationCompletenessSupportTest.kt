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

class TranslationCompletenessSupportTest {

    @TempDir
    lateinit var repo: File

    private fun language(code: String, status: I18nStatus, directory: String = "documentation/$code") =
        I18nLanguage(code, code, directory, "documentation/INDEX-$code.md", "ROOT-$code.md", status)

    private fun touch(relative: String) {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText("stub\n")
    }

    @Test
    fun completeLanguageWithEveryDocumentPresentHasNoFailuresOrWarnings() {
        touch("documentation/es/guia.md")
        val manifest =
            I18nManifest(
                "en",
                listOf(language("es", I18nStatus.COMPLETE)),
                listOf(I18nDocument("documentation/guide.md", mapOf("es" to "guia.md")))
            )

        val result = TranslationCompletenessSupport.check(repo, manifest)

        assertEquals(emptyList<String>(), result.failures)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun completeLanguageMissingADocumentFailsTheBuildNamingTheDocument() {
        val manifest =
            I18nManifest(
                "en",
                listOf(language("es", I18nStatus.COMPLETE)),
                listOf(I18nDocument("documentation/guide.md", emptyMap()))
            )

        val result = TranslationCompletenessSupport.check(repo, manifest)

        assertEquals(1, result.failures.size)
        assertTrue(result.failures[0].contains("es"))
        assertTrue(result.failures[0].contains("documentation/guide.md"))
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun inProgressLanguageMissingDocumentsOnlyWarnsWithTheExactList() {
        val manifest =
            I18nManifest(
                "en",
                listOf(language("zh-CN", I18nStatus.IN_PROGRESS)),
                listOf(
                    I18nDocument("documentation/guide-one.md", emptyMap()),
                    I18nDocument("documentation/guide-two.md", emptyMap())
                )
            )

        val result = TranslationCompletenessSupport.check(repo, manifest)

        assertEquals(emptyList<String>(), result.failures)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("documentation/guide-one.md"))
        assertTrue(result.warnings[0].contains("documentation/guide-two.md"))
    }

    @Test
    fun aManifestEntryPointingAtAMissingFileIsAHardFailureRegardlessOfStatus() {
        val manifest =
            I18nManifest(
                "en",
                listOf(language("zh-CN", I18nStatus.IN_PROGRESS)),
                listOf(I18nDocument("documentation/guide.md", mapOf("zh-CN" to "指南.md")))
            )

        val result = TranslationCompletenessSupport.check(repo, manifest)

        assertEquals(1, result.failures.size)
        assertTrue(result.failures[0].contains("指南.md"))
        assertTrue(result.failures[0].contains("does not exist"))
    }

    @Test
    fun inProgressLanguageWithNothingMissingWarnsNothing() {
        touch("documentation/zh-CN/指南.md")
        val manifest =
            I18nManifest(
                "en",
                listOf(language("zh-CN", I18nStatus.IN_PROGRESS)),
                listOf(I18nDocument("documentation/guide.md", mapOf("zh-CN" to "指南.md")))
            )

        val result = TranslationCompletenessSupport.check(repo, manifest)

        assertEquals(emptyList<String>(), result.failures)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun multipleLanguagesAreCheckedIndependently() {
        touch("documentation/es/guia.md")
        val manifest =
            I18nManifest(
                "en",
                listOf(language("es", I18nStatus.COMPLETE), language("pt-BR", I18nStatus.IN_PROGRESS)),
                listOf(I18nDocument("documentation/guide.md", mapOf("es" to "guia.md")))
            )

        val result = TranslationCompletenessSupport.check(repo, manifest)

        assertEquals(emptyList<String>(), result.failures)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("pt-BR"))
    }
}
