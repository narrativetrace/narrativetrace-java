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

class TranslationStructureSupportTest {

    @TempDir
    lateinit var repo: File

    private fun write(relative: String, content: String): File {
        val file = repo.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    // --- heading tree -------------------------------------------------------------------------

    @Test
    fun profileCapturesHeadingLevelsInOrderIgnoringHeadingsInsideFencedCode() {
        val text = "# Title\n\nProse.\n\n## Section\n\n```markdown\n### not a real heading\n```\n\n## Section 2\n"

        val profile = TranslationStructureSupport.profile(text)

        assertEquals(listOf(1, 2, 2), profile.headingLevels)
    }

    @Test
    fun compareReportsNothingWhenHeadingTreesMatch() {
        val source = TranslationStructureSupport.profile("# T\n\n## A\n\n## B\n")
        val translation = TranslationStructureSupport.profile("# T\n\n## A\n\n## B\n")

        val result = TranslationStructureSupport.compare(source, translation, "en.md", "es.md")

        assertEquals(emptyList<String>(), result.failures)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun compareNamesFileAndHeadingOnACountMismatch() {
        val source = TranslationStructureSupport.profile("# T\n\n## A\n\n## B\n")
        val translation = TranslationStructureSupport.profile("# T\n\n## A\n")

        val result = TranslationStructureSupport.compare(source, translation, "en.md", "es.md")

        assertEquals(1, result.failures.count { it.contains("heading") })
        assertTrue(result.failures.any { it.contains("es.md") })
    }

    @Test
    fun compareNamesTheDivergingHeadingOnALevelMismatch() {
        val source = TranslationStructureSupport.profile("# T\n\n## A\n\n## B\n")
        val translation = TranslationStructureSupport.profile("# T\n\n### A\n\n## B\n")

        val result = TranslationStructureSupport.compare(source, translation, "en.md", "es.md")

        assertTrue(result.failures.any { it.contains("heading 2") && it.contains("level 2") && it.contains("level 3") })
    }

    // --- fenced code blocks ---------------------------------------------------------------------

    @Test
    fun profileCapturesFencedCodeBlocksVerbatimIncludingTheInfoString() {
        val text = "# T\n\n```java\nvar x = 1;\n```\n\nProse.\n"

        val profile = TranslationStructureSupport.profile(text)

        assertEquals(listOf("```java\nvar x = 1;"), profile.codeBlocks)
    }

    @Test
    fun compareWarnsRatherThanFailsWhenACodeBlockDiffersButTheCountMatches() {
        // Real content in this repo legitimately varies code-block content — translated comments,
        // localized <placeholder> labels, a rewrapped comment, even a per-language example argument
        // (`--lang es` vs `--lang zh-CN`) — none of which a dependency-free parser can tell apart
        // from a real drift. See the class doc: content drift is a warning, never a failure.
        val source = TranslationStructureSupport.profile("```java\nvar x = 1; // comment in english\n```\n")
        val translation = TranslationStructureSupport.profile("```java\nvar x = 1; // comentario en español\n```\n")

        val result = TranslationStructureSupport.compare(source, translation, "en.md", "es.md")

        assertEquals(emptyList<String>(), result.failures)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("code block"))
        assertTrue(result.warnings[0].contains("es.md"))
    }

    @Test
    fun compareDoesNotWarnWhenCodeBlocksAreIdentical() {
        val source = TranslationStructureSupport.profile("```java\nvar x = 1;\n```\n")
        val translation = TranslationStructureSupport.profile("```java\nvar x = 1;\n```\n")

        val result = TranslationStructureSupport.compare(source, translation, "en.md", "es.md")

        assertEquals(emptyList<String>(), result.failures)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun compareFailsOnAMissingCodeBlockNamingTheCount() {
        val source = TranslationStructureSupport.profile("```java\nvar x = 1;\n```\n\n```java\nvar y = 2;\n```\n")
        val translation = TranslationStructureSupport.profile("```java\nvar x = 1;\n```\n")

        val result = TranslationStructureSupport.compare(source, translation, "en.md", "es.md")

        assertEquals(1, result.failures.size)
        assertTrue(result.failures[0].contains("code block"))
        assertTrue(result.failures[0].contains("count"))
        assertEquals(emptyList<String>(), result.warnings)
    }

    // --- tables -------------------------------------------------------------------------------

    @Test
    fun profileCapturesTableShapeAsRowsAndColumns() {
        val text = "| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |\n"

        val profile = TranslationStructureSupport.profile(text)

        assertEquals(listOf(2 to 2), profile.tables)
    }

    @Test
    fun compareFlagsATableThatLostARow() {
        val source = TranslationStructureSupport.profile("| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |\n")
        val translation = TranslationStructureSupport.profile("| A | B |\n|---|---|\n| 1 | 2 |\n")

        val result = TranslationStructureSupport.compare(source, translation, "en.md", "es.md")

        assertTrue(result.failures.any { it.contains("table") })
    }

    @Test
    fun compareFlagsATableThatLostAColumn() {
        val source = TranslationStructureSupport.profile("| A | B |\n|---|---|\n| 1 | 2 |\n")
        val translation = TranslationStructureSupport.profile("| A |\n|---|\n| 1 |\n")

        val result = TranslationStructureSupport.compare(source, translation, "en.md", "es.md")

        assertTrue(result.failures.any { it.contains("table") && it.contains("column") })
    }

    // --- links --------------------------------------------------------------------------------

    @Test
    fun brokenLinksReportsARelativeLinkThatDoesNotResolve() {
        val translation = write("documentation/es/guia.md", "[Otra guía](guia-inexistente.md)\n")

        val problems = TranslationStructureSupport.brokenLinks(translation, repo)

        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("guia-inexistente.md"))
    }

    @Test
    fun brokenLinksIgnoresExternalAndAnchorLinks() {
        val translation =
            write(
                "documentation/es/guia.md",
                "[Sitio](https://example.com) [Correo](mailto:a@example.com) [Ancla](#seccion)\n"
            )

        val problems = TranslationStructureSupport.brokenLinks(translation, repo)

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun brokenLinksResolvesAValidRelativeLink() {
        write("documentation/guide.md", "# Guide\n")
        val translation = write("documentation/es/guia.md", "[Inglés](../guide.md)\n")

        val problems = TranslationStructureSupport.brokenLinks(translation, repo)

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun brokenLinksResolvesALinkToADirectory() {
        repo.resolve("narrativetrace-benchmarks").mkdirs()
        val translation = write("guia.md", "[Benchmarks](narrativetrace-benchmarks/)\n")

        val problems = TranslationStructureSupport.brokenLinks(translation, repo)

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun brokenLinksIgnoresLinksInsideFencedCode() {
        val translation = write("documentation/es/guia.md", "```markdown\n[no existe](nope.md)\n```\n")

        val problems = TranslationStructureSupport.brokenLinks(translation, repo)

        assertEquals(emptyList<String>(), problems)
    }

    // --- orchestration --------------------------------------------------------------------------

    @Test
    fun checkAllFindsStructuralDriftBetweenADiscoveredTranslationAndItsSource() {
        write("documentation/guide.md", "# Guide\n\n## One\n\n## Two\n")
        write(
            "documentation/es/guia.md",
            "<!-- source: documentation/guide.md blob aaaaaaaaaaaa | translated: 2026-08-13 -->\n" +
                "# Guía\n\n## Uno\n"
        )

        val result = TranslationStructureSupport.checkAll(repo)

        assertTrue(result.failures.any { it.contains("documentation/es/guia.md") })
    }

    @Test
    fun checkAllSkipsATranslationWhoseSourceIsMissing() {
        write(
            "documentation/es/guia.md",
            "<!-- source: documentation/gone.md blob aaaaaaaaaaaa | translated: 2026-08-13 -->\n# Guía\n"
        )

        val result = TranslationStructureSupport.checkAll(repo)

        assertEquals(emptyList<String>(), result.failures)
        assertEquals(emptyList<String>(), result.warnings)
    }
}
