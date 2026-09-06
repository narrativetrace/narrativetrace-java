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

class TranslationReviewSupportTest {

    @TempDir
    lateinit var repo: File

    private fun write(relative: String, content: String) {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun unreviewedListsFilesWithNoReviewedClauseAndFilesMarkedDash() {
        write(
            "documentation/es/one.md",
            "<!-- source: documentation/one.md blob aaaaaaaaaaaa | translated: 2026-08-13 -->\nUno\n"
        )
        write(
            "documentation/es/two.md",
            "<!-- source: documentation/two.md blob bbbbbbbbbbbb | translated: 2026-08-13 | reviewed: - -->\nDos\n"
        )

        val unreviewed = TranslationReviewSupport.unreviewed(repo)

        assertEquals(2, unreviewed.size)
        assertTrue(unreviewed.any { it.contains("one.md") })
        assertTrue(unreviewed.any { it.contains("two.md") })
    }

    @Test
    fun unreviewedExcludesFilesCarryingAReviewedDate() {
        write(
            "documentation/es/one.md",
            "<!-- source: documentation/one.md blob aaaaaaaaaaaa | translated: 2026-08-13 | reviewed: 2026-09-01 -->\nUno\n"
        )

        assertEquals(emptyList<String>(), TranslationReviewSupport.unreviewed(repo))
    }

    @Test
    fun unreviewedSkipsFilesWithNoValidHeaderAtAll() {
        write("documentation/es/plain.md", "No header here.\n")

        assertEquals(emptyList<String>(), TranslationReviewSupport.unreviewed(repo))
    }

    @Test
    fun summaryLineSaysNothingIsUnreviewedWhenNothingIs() {
        write(
            "documentation/es/one.md",
            "<!-- source: documentation/one.md blob aaaaaaaaaaaa | translated: 2026-08-13 | reviewed: 2026-09-01 -->\nUno\n"
        )

        val line = TranslationReviewSupport.summaryLine(repo)

        assertTrue(line.contains("0"))
        assertTrue(line.contains("translationStatus"))
    }

    @Test
    fun summaryLineCountsUnreviewedDocuments() {
        write(
            "documentation/es/one.md",
            "<!-- source: documentation/one.md blob aaaaaaaaaaaa | translated: 2026-08-13 -->\nUno\n"
        )
        write(
            "documentation/es/two.md",
            "<!-- source: documentation/two.md blob bbbbbbbbbbbb | translated: 2026-08-13 -->\nDos\n"
        )

        val line = TranslationReviewSupport.summaryLine(repo)

        assertTrue(line.contains("2"))
    }

    @Test
    fun statusReportListsEachLanguageAndItsCoverage() {
        write("documentation/es/uno.md", "# Uno\n")
        val manifest =
            I18nManifest(
                "en",
                listOf(
                    I18nLanguage("es", "Español", "documentation/es", "documentation/LEAME.md", "LEAME.md", I18nStatus.COMPLETE),
                    I18nLanguage("zh-CN", "简体中文", "documentation/zh-CN", "documentation/自述文件.md", "自述文件.md", I18nStatus.IN_PROGRESS)
                ),
                listOf(
                    I18nDocument("documentation/one.md", mapOf("es" to "uno.md")),
                    I18nDocument("documentation/two.md", emptyMap())
                )
            )

        val report = TranslationReviewSupport.statusReport(repo, manifest)

        assertTrue(report.contains("es"))
        assertTrue(report.contains("zh-CN"))
        assertTrue(report.contains("1/2"))
        assertTrue(report.contains("0/2"))
    }

    @Test
    fun statusReportSaysSoWhenTheManifestIsAbsent() {
        val report = TranslationReviewSupport.statusReport(repo, null)

        assertTrue(report.contains("no manifest") || report.contains("No manifest"))
    }
}
