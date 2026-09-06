/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TranslationIndexSupportTest {

    @TempDir
    lateinit var repo: File

    private fun write(relative: String, content: String): File {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    private fun manifestWith(vararg languages: I18nLanguage) =
        I18nManifest(
            "en",
            languages.toList(),
            listOf(
                I18nDocument("documentation/one.md", mapOf("es" to "uno.md")),
                I18nDocument("documentation/two.md", mapOf("es" to "dos.md"))
            )
        )

    private fun es(status: I18nStatus = I18nStatus.COMPLETE) =
        I18nLanguage("es", "Español", "documentation/es", "documentation/LEAME.md", "LEAME.md", status)

    private fun zh(status: I18nStatus = I18nStatus.IN_PROGRESS) =
        I18nLanguage("zh-CN", "简体中文", "documentation/zh-CN", "documentation/自述文件.md", "自述文件.md", status)

    // --- menu line parsing ----------------------------------------------------------------------

    @Test
    fun menuLineIsTheFirstNonBlankLineAfterTheH1() {
        val text = "# Title\n\n[English](README.md) | **Español**\n\nProse.\n"

        assertEquals("[English](README.md) | **Español**", TranslationIndexSupport.menuLine(text))
    }

    @Test
    fun menuLineIsNullWhenThereIsNoH1() {
        assertNull(TranslationIndexSupport.menuLine("Just prose.\n"))
    }

    // --- expected menu construction --------------------------------------------------------------

    @Test
    fun expectedMenuBoldsTheCurrentLanguageAndLinksAnExistingIndex() {
        write("documentation/LEAME.md", "stub")
        val manifest = manifestWith(es())

        val menu = TranslationIndexSupport.expectedMenu(manifest, repo, "es")

        assertEquals("[English](README.md) | **Español**", menu)
    }

    @Test
    fun expectedMenuLeavesAMissingIndexAsPlainText() {
        val manifest = manifestWith(zh())

        val menu = TranslationIndexSupport.expectedMenu(manifest, repo, null)

        assertEquals("[English](README.md) | 简体中文", menu)
    }

    @Test
    fun expectedMenuLinksAnotherLanguagesExistingIndexFromEnglish() {
        write("documentation/LEAME.md", "stub")
        val manifest = manifestWith(es())

        val menu = TranslationIndexSupport.expectedMenu(manifest, repo, null)

        assertEquals("[English](README.md) | [Español](LEAME.md)", menu)
    }

    // --- document row extraction ------------------------------------------------------------------

    @Test
    fun documentTargetsReadsTableRowLinksAndIgnoresTheMenuAndSeparatorRows() {
        val text =
            "# Documentación\n\n[English](README.md) | **Español**\n\n" +
                "| Documento | Qué cubre |\n|---|---|\n" +
                "| [Uno](es/uno.md) | ... |\n| [Dos](es/dos.md) | ... |\n"

        val targets = TranslationIndexSupport.documentTargets(text)

        assertEquals(listOf("es/uno.md", "es/dos.md"), targets)
    }

    // --- per-language index check -------------------------------------------------------------

    @Test
    fun checkLanguageIndexPassesWhenRowsMenuAndTargetsAllMatch() {
        write("documentation/es/uno.md", "# Uno\n")
        write("documentation/es/dos.md", "# Dos\n")
        write(
            "documentation/LEAME.md",
            "# Documentación\n\n[English](README.md) | **Español**\n\n" +
                "| Documento | Qué cubre |\n|---|---|\n" +
                "| [Uno](es/uno.md) | ... |\n| [Dos](es/dos.md) | ... |\n"
        )
        val manifest = manifestWith(es())

        val problems = TranslationIndexSupport.checkLanguageIndex(repo, manifest, es())

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun checkLanguageIndexFlagsAMissingRowForATranslatedDocument() {
        write("documentation/es/uno.md", "# Uno\n")
        write("documentation/es/dos.md", "# Dos\n")
        write(
            "documentation/LEAME.md",
            "# Documentación\n\n[English](README.md) | **Español**\n\n" +
                "| Documento | Qué cubre |\n|---|---|\n| [Uno](es/uno.md) | ... |\n"
        )
        val manifest = manifestWith(es())

        val problems = TranslationIndexSupport.checkLanguageIndex(repo, manifest, es())

        assertTrue(problems.any { it.contains("es/dos.md") })
    }

    @Test
    fun checkLanguageIndexFlagsAnOrphanRowNotInTheManifest() {
        write("documentation/es/uno.md", "# Uno\n")
        write("documentation/es/dos.md", "# Dos\n")
        write("documentation/es/tres.md", "# Tres\n")
        write(
            "documentation/LEAME.md",
            "# Documentación\n\n[English](README.md) | **Español**\n\n" +
                "| Documento | Qué cubre |\n|---|---|\n" +
                "| [Uno](es/uno.md) | ... |\n| [Dos](es/dos.md) | ... |\n| [Tres](es/tres.md) | ... |\n"
        )
        val manifest = manifestWith(es())

        val problems = TranslationIndexSupport.checkLanguageIndex(repo, manifest, es())

        assertTrue(problems.any { it.contains("es/tres.md") })
    }

    @Test
    fun checkLanguageIndexFlagsARowWhoseTargetDoesNotResolve() {
        write("documentation/es/uno.md", "# Uno\n")
        write(
            "documentation/LEAME.md",
            "# Documentación\n\n[English](README.md) | **Español**\n\n" +
                "| Documento | Qué cubre |\n|---|---|\n| [Uno](es/uno.md) | ... |\n| [Dos](es/dos.md) | ... |\n"
        )
        val manifest = manifestWith(es())

        val problems = TranslationIndexSupport.checkLanguageIndex(repo, manifest, es())

        assertTrue(problems.any { it.contains("does not resolve") && it.contains("es/dos.md") })
    }

    @Test
    fun checkLanguageIndexFlagsAWrongMenuLine() {
        write("documentation/es/uno.md", "# Uno\n")
        write("documentation/es/dos.md", "# Dos\n")
        write(
            "documentation/LEAME.md",
            "# Documentación\n\n[English](README.md) | Español\n\n" +
                "| Documento | Qué cubre |\n|---|---|\n" +
                "| [Uno](es/uno.md) | ... |\n| [Dos](es/dos.md) | ... |\n"
        )
        val manifest = manifestWith(es())

        val problems = TranslationIndexSupport.checkLanguageIndex(repo, manifest, es())

        assertTrue(problems.any { it.contains("menu") })
    }

    @Test
    fun checkLanguageIndexFailsWhenACompleteLanguagesIndexIsMissing() {
        val manifest = manifestWith(es())

        val problems = TranslationIndexSupport.checkLanguageIndex(repo, manifest, es())

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("missing"))
    }

    @Test
    fun checkLanguageIndexIsSilentWhenAnInProgressLanguagesIndexIsMissing() {
        val manifest = manifestWith(zh())

        val problems = TranslationIndexSupport.checkLanguageIndex(repo, manifest, zh())

        assertEquals(emptyList<String>(), problems)
    }

    // --- English index menu ---------------------------------------------------------------------

    @Test
    fun checkEnglishIndexMenuPassesWhenTheMenuMatches() {
        write("documentation/LEAME.md", "stub")
        write("documentation/README.md", "# NarrativeTrace documentation\n\n[English](README.md) | [Español](LEAME.md)\n")
        val manifest = manifestWith(es())

        val problems = TranslationIndexSupport.checkEnglishIndexMenu(repo, manifest)

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun checkEnglishIndexMenuFlagsADriftedMenu() {
        write("documentation/README.md", "# NarrativeTrace documentation\n\n[English](README.md)\n")
        val manifest = manifestWith(es())

        val problems = TranslationIndexSupport.checkEnglishIndexMenu(repo, manifest)

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("documentation/README.md"))
    }

    // --- orchestration ----------------------------------------------------------------------------

    @Test
    fun checkAllCombinesTheEnglishMenuCheckAndEveryLanguageIndexCheck() {
        write("documentation/README.md", "# NarrativeTrace documentation\n\n[English](README.md)\n")
        val manifest = manifestWith(es())

        val problems = TranslationIndexSupport.checkAll(repo, manifest)

        assertTrue(problems.any { it.contains("documentation/README.md") })
        assertTrue(problems.any { it.contains("documentation/LEAME.md") })
    }
}
