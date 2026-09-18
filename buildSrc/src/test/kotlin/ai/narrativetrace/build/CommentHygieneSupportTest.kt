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

class CommentHygieneSupportTest {

    @TempDir
    lateinit var tempDir: File

    // ---- HISTORY_PATTERN / PORT_FRAMING_PATTERN --------------------------------------------

    @Test
    fun historyPatternMatchesOwnerRulingRuledYearBareDateAndUnreleasedWording() {
        assertTrue(CommentHygieneSupport.HISTORY_PATTERN.containsMatchIn("kept for redaction (owner ruling, 2026-09-11)."))
        assertTrue(CommentHygieneSupport.HISTORY_PATTERN.containsMatchIn("fixed the gap (ruled 2026-09-12)."))
        assertTrue(CommentHygieneSupport.HISTORY_PATTERN.containsMatchIn("recomputed here (2026-09-02)."))
        assertTrue(
            CommentHygieneSupport.HISTORY_PATTERN.containsMatchIn(
                "disable buffering here, by design. (since 0.1.3, unreleased)"
            )
        )
    }

    @Test
    fun historyPatternDoesNotMatchAnOrdinarySentenceOrAVersionAlone() {
        assertTrue(!CommentHygieneSupport.HISTORY_PATTERN.containsMatchIn("Escapes control characters before rendering."))
        assertTrue(!CommentHygieneSupport.HISTORY_PATTERN.containsMatchIn("Targets Java 17."))
    }

    @Test
    fun portFramingPatternMatchesGoldenSourceMirrorsAndPortedToPhrasing() {
        assertTrue(CommentHygieneSupport.PORT_FRAMING_PATTERN.containsMatchIn("Java is the golden source for this behavior."))
        assertTrue(CommentHygieneSupport.PORT_FRAMING_PATTERN.containsMatchIn("as in the TS port's redaction table."))
        assertTrue(CommentHygieneSupport.PORT_FRAMING_PATTERN.containsMatchIn("mirrors TS's NationalIdShapes."))
        assertTrue(CommentHygieneSupport.PORT_FRAMING_PATTERN.containsMatchIn("as in Python's redaction module."))
        assertTrue(CommentHygieneSupport.PORT_FRAMING_PATTERN.containsMatchIn("ported to .NET the following release."))
    }

    @Test
    fun portFramingPatternDoesNotMatchAPeerMentionWithoutSourceFraming() {
        assertTrue(!CommentHygieneSupport.PORT_FRAMING_PATTERN.containsMatchIn("Every NarrativeTrace runtime redacts the same shapes."))
    }

    // ---- moduleSourceFiles / buildSrcSourceFiles --------------------------------------------

    @Test
    fun moduleSourceFilesFindsEveryJavaFileUnderEachModulesSrcMainJava() {
        File(tempDir, "narrativetrace-core/src/main/java/ai/narrativetrace/core").mkdirs()
        File(tempDir, "narrativetrace-core/src/main/java/ai/narrativetrace/core/Foo.java").writeText("")
        File(tempDir, "narrativetrace-core/src/test/java/ai/narrativetrace/core").mkdirs()
        File(tempDir, "narrativetrace-core/src/test/java/ai/narrativetrace/core/FooTest.java").writeText("")
        File(tempDir, "narrativetrace-api/src/main/java/ai/narrativetrace/api").mkdirs()
        File(tempDir, "narrativetrace-api/src/main/java/ai/narrativetrace/api/Bar.java").writeText("")

        val found = CommentHygieneSupport.moduleSourceFiles(
            tempDir, listOf("narrativetrace-core", "narrativetrace-api")
        )

        assertEquals(
            listOf(
                File(tempDir, "narrativetrace-api/src/main/java/ai/narrativetrace/api/Bar.java"),
                File(tempDir, "narrativetrace-core/src/main/java/ai/narrativetrace/core/Foo.java")
            ).sortedBy { it.path },
            found
        )
    }

    @Test
    fun moduleSourceFilesSkipsAModuleWithNoSrcMainJavaDirectory() {
        val found = CommentHygieneSupport.moduleSourceFiles(tempDir, listOf("narrativetrace-examples"))

        assertEquals(emptyList<File>(), found)
    }

    @Test
    fun buildSrcSourceFilesFindsEveryKotlinFileUnderBuildSrcSrcMainKotlin() {
        File(tempDir, "buildSrc/src/main/kotlin/ai/narrativetrace/build").mkdirs()
        File(tempDir, "buildSrc/src/main/kotlin/ai/narrativetrace/build/Foo.kt").writeText("")
        File(tempDir, "buildSrc/src/test/kotlin/ai/narrativetrace/build").mkdirs()
        File(tempDir, "buildSrc/src/test/kotlin/ai/narrativetrace/build/FooTest.kt").writeText("")

        val found = CommentHygieneSupport.buildSrcSourceFiles(tempDir)

        assertEquals(listOf(File(tempDir, "buildSrc/src/main/kotlin/ai/narrativetrace/build/Foo.kt")), found)
    }

    @Test
    fun buildSrcSourceFilesReturnsEmptyWhenBuildSrcDoesNotExist() {
        assertEquals(emptyList<File>(), CommentHygieneSupport.buildSrcSourceFiles(tempDir))
    }

    // ---- findHits ----------------------------------------------------------------------------

    @Test
    fun findHitsReportsRepoRelativeFileOneIndexedLineTrimmedTextAndRule() {
        File(tempDir, "narrativetrace-core/src/main/java/ai/narrativetrace/core").mkdirs()
        val file = File(tempDir, "narrativetrace-core/src/main/java/ai/narrativetrace/core/Foo.java")
        file.writeText(
            "package ai.narrativetrace.core;\n" +
                "// fixed the leak (owner ruling, 2026-09-11).\n" +
                "public final class Foo {}\n"
        )

        val hits = CommentHygieneSupport.findHits(tempDir, listOf(file))

        assertEquals(
            listOf(
                CommentHygieneSupport.Hit(
                    "narrativetrace-core/src/main/java/ai/narrativetrace/core/Foo.java",
                    2,
                    "// fixed the leak (owner ruling, 2026-09-11).",
                    "history"
                )
            ),
            hits
        )
    }

    @Test
    fun findHitsFindsNothingInAFileWithNoHistoryOrPortFramingComment() {
        File(tempDir, "narrativetrace-core/src/main/java/ai/narrativetrace/core").mkdirs()
        val file = File(tempDir, "narrativetrace-core/src/main/java/ai/narrativetrace/core/Clean.java")
        file.writeText(
            "package ai.narrativetrace.core;\n" +
                "// Redacts a value whose shape matches a known secret pattern.\n" +
                "public final class Clean {}\n"
        )

        assertEquals(emptyList<CommentHygieneSupport.Hit>(), CommentHygieneSupport.findHits(tempDir, listOf(file)))
    }

    @Test
    fun findHitsNeverFlagsAHouseTagOrIntentLineEvenWithADateOrAPeerMention() {
        File(tempDir, "narrativetrace-core/src/main/java/ai/narrativetrace/core").mkdirs()
        val file = File(tempDir, "narrativetrace-core/src/main/java/ai/narrativetrace/core/Tagged.java")
        file.writeText(
            "package ai.narrativetrace.core;\n" +
                "/**\n" +
                " * INTENT: keeps parity with every NarrativeTrace runtime as of 2026-09-02.\n" +
                " * @llmNote never call this from a (2026-09-02) migration script.\n" +
                " * @sideEffects mirrors TS's cache, ported to this class in one step.\n" +
                " * @pattern golden source for this module, revisited (owner ruling, 2026-09-11).\n" +
                " */\n" +
                "public final class Tagged {}\n"
        )

        assertEquals(emptyList<CommentHygieneSupport.Hit>(), CommentHygieneSupport.findHits(tempDir, listOf(file)))
    }

    // ---- readAllowlist -------------------------------------------------------------------------

    @Test
    fun readAllowlistParsesAFlatFileToReasonObject() {
        val file = File(tempDir, "allowlist.json")
        file.writeText("""{"narrativetrace-core/src/main/java/ai/narrativetrace/core/Foo.java": "pending wave"}""")

        assertEquals(
            mapOf("narrativetrace-core/src/main/java/ai/narrativetrace/core/Foo.java" to "pending wave"),
            CommentHygieneSupport.readAllowlist(file)
        )
    }

    @Test
    fun readAllowlistReturnsEmptyForAMissingFileOrAnEmptyObject() {
        assertEquals(emptyMap<String, String>(), CommentHygieneSupport.readAllowlist(File(tempDir, "missing.json")))

        val empty = File(tempDir, "empty.json")
        empty.writeText("{}")
        assertEquals(emptyMap<String, String>(), CommentHygieneSupport.readAllowlist(empty))
    }

    // ---- lint ----------------------------------------------------------------------------------

    @Test
    fun lintReportsAnUnlistedHitAsAViolationAndNeverFlagsAnAllowlistedOne() {
        val leftover = CommentHygieneSupport.Hit("narrativetrace-core/src/main/java/Leftover.java", 1, "// (owner ruling, 2026-09-11)", "history")
        val pending = CommentHygieneSupport.Hit("narrativetrace-api/src/main/java/Pending.java", 1, "// (owner ruling, 2026-09-11)", "history")

        val result = CommentHygieneSupport.lint(
            listOf(leftover, pending),
            mapOf("narrativetrace-api/src/main/java/Pending.java" to "wave pending")
        )

        assertEquals(listOf(leftover), result.violations)
        assertEquals(emptyList<String>(), result.staleAllowlistEntries)
    }

    @Test
    fun lintFlagsAnAllowlistEntryWhoseFileNoLongerHasAnyHitAsStale() {
        val result = CommentHygieneSupport.lint(
            emptyList(),
            mapOf("narrativetrace-core/src/main/java/Clean.java" to "no longer needed")
        )

        assertEquals(emptyList<CommentHygieneSupport.Hit>(), result.violations)
        assertEquals(listOf("narrativetrace-core/src/main/java/Clean.java"), result.staleAllowlistEntries)
    }

    @Test
    fun lintIsCleanWhenNothingMatchesAndNothingIsAllowlisted() {
        val result = CommentHygieneSupport.lint(emptyList(), emptyMap())

        assertEquals(emptyList<CommentHygieneSupport.Hit>(), result.violations)
        assertEquals(emptyList<String>(), result.staleAllowlistEntries)
    }
}
