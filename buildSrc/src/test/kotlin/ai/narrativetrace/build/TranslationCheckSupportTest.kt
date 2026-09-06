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

class TranslationCheckSupportTest {

    @Test
    fun parseHeaderExtractsSourcePathAndHashPrefix() {
        val header =
            TranslationCheckSupport.parseHeader(
                "<!-- source: documentation/installation-guide.md blob a359ff222229 | translated: 2026-08-13 -->"
            )

        assertEquals("documentation/installation-guide.md", header?.sourcePath)
        assertEquals("a359ff222229", header?.blobHashPrefix)
    }

    @Test
    fun parseHeaderRejectsMalformedLines() {
        assertNull(TranslationCheckSupport.parseHeader(""))
        assertNull(TranslationCheckSupport.parseHeader("# A plain title"))
        assertNull(TranslationCheckSupport.parseHeader("<!-- source: x.md blob TOOSHORT | translated: 2026-08-13 -->"))
        assertNull(TranslationCheckSupport.parseHeader("<!-- source: x.md blob ABCDEF123456 | translated: 2026-08-13 -->"))
        assertNull(TranslationCheckSupport.parseHeader("<!-- source: x.md blob a359ff222229 | translated: yesterday -->"))
    }

    @Test
    fun parseHeaderWithoutAReviewedClauseIsUnreviewed() {
        val header =
            TranslationCheckSupport.parseHeader(
                "<!-- source: documentation/installation-guide.md blob a359ff222229 | translated: 2026-08-13 -->"
            )

        assertNull(header?.reviewed)
    }

    @Test
    fun parseHeaderWithAReviewedDashIsUnreviewed() {
        val header =
            TranslationCheckSupport.parseHeader(
                "<!-- source: x.md blob a359ff222229 | translated: 2026-08-13 | reviewed: - -->"
            )

        assertNull(header?.reviewed)
    }

    @Test
    fun parseHeaderWithAReviewedDateCapturesIt() {
        val header =
            TranslationCheckSupport.parseHeader(
                "<!-- source: x.md blob a359ff222229 | translated: 2026-08-13 | reviewed: 2026-09-01 -->"
            )

        assertEquals("2026-09-01", header?.reviewed)
    }

    @Test
    fun parseHeaderRejectsAMalformedReviewedDate() {
        assertNull(
            TranslationCheckSupport.parseHeader(
                "<!-- source: x.md blob a359ff222229 | translated: 2026-08-13 | reviewed: yesterday -->"
            )
        )
    }

    @TempDir
    lateinit var repo: File

    private fun writeSource(relative: String, content: String): String {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(content)
        return TranslationCheckSupport.gitBlobHash(content.toByteArray()).take(12)
    }

    private fun writeTranslation(relative: String, sourcePath: String, hashPrefix: String) {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText("<!-- source: $sourcePath blob $hashPrefix | translated: 2026-08-13 -->\ncuerpo\n")
    }

    @Test
    fun checkPassesWhenTranslationsMatchSources() {
        val hash = writeSource("documentation/guide.md", "# Guide\n")
        writeTranslation("documentation/es/guide.md", "documentation/guide.md", hash)
        writeTranslation("documentation/zh-CN/guide.md", "documentation/guide.md", hash)

        assertEquals(emptyList<String>(), TranslationCheckSupport.check(repo))
    }

    @Test
    fun checkReportsStaleTranslationWithBothHashes() {
        writeSource("documentation/guide.md", "# Guide v2\n")
        writeTranslation("documentation/es/guide.md", "documentation/guide.md", "aaaaaaaaaaaa")

        val problems = TranslationCheckSupport.check(repo)

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("documentation/es/guide.md"))
        assertTrue(problems[0].contains("aaaaaaaaaaaa"))
        assertTrue(problems[0].contains("stale"))
    }

    @Test
    fun checkReportsMissingSource() {
        writeTranslation("documentation/es/guide.md", "documentation/gone.md", "aaaaaaaaaaaa")

        val problems = TranslationCheckSupport.check(repo)

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("documentation/gone.md"))
        assertTrue(problems[0].contains("missing"))
    }

    @Test
    fun checkRequiresValidHeaderInsideLanguageDirectories() {
        writeSource("documentation/guide.md", "# Guide\n")
        repo.resolve("documentation/es").mkdirs()
        repo.resolve("documentation/es/guide.md").writeText("# Sin encabezado\n")

        val problems = TranslationCheckSupport.check(repo)

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("documentation/es/guide.md"))
        assertTrue(problems[0].contains("header"))
    }

    @Test
    fun checkIgnoresTheI18nDirectoryAndHeaderlessRootFiles() {
        repo.resolve("documentation/i18n").mkdirs()
        repo.resolve("documentation/i18n/terminology.md").writeText("# Terminology\n")
        repo.resolve("README.md").writeText("# NarrativeTrace\n")

        assertEquals(emptyList<String>(), TranslationCheckSupport.check(repo))
    }

    @Test
    fun checkVerifiesRootTranslationsCarryingHeaders() {
        val hash = writeSource("README.md", "# NarrativeTrace\n")
        writeTranslation("LEAME.md", "README.md", hash)
        writeTranslation("Z-stale.md", "README.md", "aaaaaaaaaaaa")

        val problems = TranslationCheckSupport.check(repo)

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("Z-stale.md"))
    }

    @Test
    fun checkVerifiesModuleLevelTranslationsCarryingHeaders() {
        writeSource("narrativetrace-examples/README.md", "# Examples v2\n")
        writeTranslation("narrativetrace-examples/LEAME.md", "narrativetrace-examples/README.md", "aaaaaaaaaaaa")

        val problems = TranslationCheckSupport.check(repo)

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("narrativetrace-examples/LEAME.md"))
        assertTrue(problems[0].contains("stale"))
    }

    @Test
    fun checkIgnoresHeaderedFilesInBuildOutputAndHiddenDirectories() {
        writeSource("narrativetrace-examples/README.md", "# Examples v2\n")
        writeTranslation("narrativetrace-examples/build/site/LEAME.md", "narrativetrace-examples/README.md", "aaaaaaaaaaaa")
        writeTranslation(".git/copies/LEAME.md", "narrativetrace-examples/README.md", "aaaaaaaaaaaa")

        assertEquals(emptyList<String>(), TranslationCheckSupport.check(repo))
    }

    @Test
    fun checkScansEvenWhenTheRepoRootItselfIsNamedBuildOrHidden() {
        val nestedRoot = repo.resolve("build")
        nestedRoot.mkdirs()
        nestedRoot.resolve("README.md").writeText("# Examples v2\n")
        nestedRoot.resolve("LEAME.md").writeText(
            "<!-- source: README.md blob aaaaaaaaaaaa | translated: 2026-08-13 -->\ncuerpo\n"
        )

        val problems = TranslationCheckSupport.check(nestedRoot)

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("LEAME.md"))
    }

    @Test
    fun checkRejectsSourcePathsEscapingTheRepoRoot() {
        writeTranslation("documentation/es/guide.md", "../outside.md", "aaaaaaaaaaaa")

        val problems = TranslationCheckSupport.check(repo)

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("escapes"))
    }

    @Test
    fun checkReportsMultipleProblemsSortedByFile() {
        writeSource("documentation/guide.md", "# Guide\n")
        writeTranslation("documentation/es/guide.md", "documentation/guide.md", "aaaaaaaaaaaa")
        repo.resolve("documentation/zh-CN").mkdirs()
        repo.resolve("documentation/zh-CN/guide.md").writeText("没有头\n")

        val problems = TranslationCheckSupport.check(repo)

        assertEquals(2, problems.size)
        assertTrue(problems[0].contains("documentation/es/guide.md"))
        assertTrue(problems[1].contains("documentation/zh-CN/guide.md"))
    }

    @Test
    fun translatedFilesCombinesLanguageDirectoriesAndHeaderedFilesWithoutDuplicates() {
        writeSource("documentation/guide.md", "# Guide\n")
        writeTranslation("documentation/es/guide.md", "documentation/guide.md", "aaaaaaaaaaaa")
        writeSource("README.md", "# NarrativeTrace\n")
        writeTranslation("LEAME.md", "README.md", "bbbbbbbbbbbb")

        val relativePaths =
            TranslationCheckSupport.translatedFiles(repo)
                .map { repo.toPath().relativize(it.toPath()).toString() }
                .toSet()

        assertEquals(setOf("documentation/es/guide.md", "LEAME.md"), relativePaths)
    }

    @Test
    fun gitBlobHashMatchesGitHashObject() {
        // Known git blob hashes: `git hash-object` of an empty file and of "hello\n".
        assertEquals(
            "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391",
            TranslationCheckSupport.gitBlobHash(ByteArray(0))
        )
        assertEquals(
            "ce013625030ba8dba906f756967f9e9ca394464a",
            TranslationCheckSupport.gitBlobHash("hello\n".toByteArray())
        )
    }
}
