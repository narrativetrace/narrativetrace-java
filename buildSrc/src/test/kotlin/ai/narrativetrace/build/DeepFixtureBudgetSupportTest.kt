/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeepFixtureBudgetSupportTest {

    @TempDir
    lateinit var tempDir: File

    // ---- codeOnly -----------------------------------------------------------------------------

    @Test
    fun codeOnlyBlanksLineAndBlockCommentsAndStringAndTextBlockLiterals() {
        val source =
            "// a chain of 10_000\n" +
                "/* also 50_000 here */\n" +
                "var s = \"chain 10_000\";\n" +
                "var t = \"\"\"\n" +
                "  a chain of 10_000\n" +
                "\"\"\";\n" +
                "var depth = 5_000;\n"

        val code = DeepFixtureBudgetSupport.codeOnly(source)

        assertFalse(code.contains("chain"))
        assertTrue(code.contains("var depth = 5_000;"))
    }

    @Test
    fun codeOnlyPreservesLineNumbersOfSurvivingCode() {
        val source = "// line 1\nvar depth = 5_000;\n"

        val code = DeepFixtureBudgetSupport.codeOnly(source)

        assertEquals(2, code.lines().indexOfFirst { it.contains("depth") } + 1)
    }

    // ---- isDeepFixtureBody ---------------------------------------------------------------------

    @Test
    fun isDeepFixtureBodyTrueForABuilderCallPassedTheThreshold() {
        assertTrue(DeepFixtureBudgetSupport.isDeepFixtureBody("chainOfRecords(10_000);"))
        assertTrue(DeepFixtureBudgetSupport.isDeepFixtureBody("chainOf(10_000, List::of);"))
        assertTrue(DeepFixtureBudgetSupport.isDeepFixtureBody("nestLists(5_000);"))
    }

    @Test
    fun isDeepFixtureBodyTrueForADepthOrSizeVariableAssignedTheThreshold() {
        assertTrue(DeepFixtureBudgetSupport.isDeepFixtureBody("var depth = 5_000;\nuseIt(depth);"))
        assertTrue(DeepFixtureBudgetSupport.isDeepFixtureBody("int fixtureSize = 10000;"))
    }

    @Test
    fun isDeepFixtureBodyFalseBelowTheThreshold() {
        assertFalse(DeepFixtureBudgetSupport.isDeepFixtureBody("chainOfRecords(3);"))
        assertFalse(DeepFixtureBudgetSupport.isDeepFixtureBody("var depth = 4_999;"))
    }

    @Test
    fun isDeepFixtureBodyFalseForAnUnrelatedLargeLiteral() {
        assertFalse(DeepFixtureBudgetSupport.isDeepFixtureBody("var port = 8080;"))
        assertFalse(DeepFixtureBudgetSupport.isDeepFixtureBody("assertThat(count).isEqualTo(50_000);"))
    }

    @Test
    fun isDeepFixtureBodyFalseForANonLiteralOffsetFromAConstant() {
        assertFalse(DeepFixtureBudgetSupport.isDeepFixtureBody("chainOfRecords(RenderWalk.MAX_DEPTH + 5);"))
    }

    // ---- findHits --------------------------------------------------------------------------------

    private fun writeTestFile(name: String, content: String): File {
        val dir = File(tempDir, "narrativetrace-core/src/test/java/ai/narrativetrace/core")
        dir.mkdirs()
        val file = File(dir, name)
        file.writeText(content)
        return file
    }

    @Test
    fun findHitsFlagsADeepFixtureMethodWithNoTimeoutAnnotation() {
        val file = writeTestFile(
            "Foo.java",
            "package ai.narrativetrace.core;\n" +
                "class Foo {\n" +
                "  @Test\n" +
                "  void aTenThousandDeepChain() {\n" +
                "    var deep = chainOfRecords(10_000);\n" +
                "  }\n" +
                "}\n",
        )

        val hits = DeepFixtureBudgetSupport.findHits(tempDir, file)

        assertEquals(1, hits.size)
        assertEquals("aTenThousandDeepChain", hits[0].method)
        assertFalse(hits[0].hasTimeout)
    }

    @Test
    fun findHitsRecordsAMethodLevelTimeoutAsAlreadyBudgeted() {
        val file = writeTestFile(
            "Foo.java",
            "package ai.narrativetrace.core;\n" +
                "class Foo {\n" +
                "  @Test\n" +
                "  @Timeout(value = 5, unit = TimeUnit.SECONDS)\n" +
                "  void aTenThousandDeepChain() {\n" +
                "    var deep = chainOfRecords(10_000);\n" +
                "  }\n" +
                "}\n",
        )

        val hits = DeepFixtureBudgetSupport.findHits(tempDir, file)

        assertEquals(1, hits.size)
        assertTrue(hits[0].hasTimeout)
    }

    @Test
    fun findHitsRecordsAClassLevelTimeoutAsBudgetingEveryMethod() {
        val file = writeTestFile(
            "Foo.java",
            "package ai.narrativetrace.core;\n" +
                "@Timeout(value = 5, unit = TimeUnit.SECONDS)\n" +
                "class Foo {\n" +
                "  @Test\n" +
                "  void aTenThousandDeepChain() {\n" +
                "    var deep = chainOfRecords(10_000);\n" +
                "  }\n" +
                "}\n",
        )

        val hits = DeepFixtureBudgetSupport.findHits(tempDir, file)

        assertEquals(1, hits.size)
        assertTrue(hits[0].hasTimeout)
    }

    @Test
    fun findHitsIgnoresATimeoutBelongingToAnEarlierMethod() {
        val file = writeTestFile(
            "Foo.java",
            "package ai.narrativetrace.core;\n" +
                "class Foo {\n" +
                "  @Test\n" +
                "  @Timeout(value = 5, unit = TimeUnit.SECONDS)\n" +
                "  void shallow() {\n" +
                "    var x = 1;\n" +
                "  }\n" +
                "\n" +
                "  @Test\n" +
                "  void aTenThousandDeepChain() {\n" +
                "    var deep = chainOfRecords(10_000);\n" +
                "  }\n" +
                "}\n",
        )

        val hits = DeepFixtureBudgetSupport.findHits(tempDir, file)

        assertEquals(1, hits.size)
        assertEquals("aTenThousandDeepChain", hits[0].method)
        assertFalse(hits[0].hasTimeout)
    }

    @Test
    fun findHitsFindsNothingWhenNoMethodBuildsADeepFixture() {
        val file = writeTestFile(
            "Foo.java",
            "package ai.narrativetrace.core;\n" +
                "class Foo {\n" +
                "  @Test\n" +
                "  void shallow() {\n" +
                "    var x = chainOfRecords(3);\n" +
                "  }\n" +
                "}\n",
        )

        assertEquals(emptyList<DeepFixtureBudgetSupport.Hit>(), DeepFixtureBudgetSupport.findHits(tempDir, file))
    }

    // ---- moduleTestFiles -------------------------------------------------------------------------

    @Test
    fun moduleTestFilesFindsEveryJavaFileUnderEachModulesSrcTestJava() {
        writeTestFile("Foo.java", "class Foo {}\n")
        File(tempDir, "narrativetrace-api/src/test/java/ai/narrativetrace/api").mkdirs()
        File(tempDir, "narrativetrace-api/src/test/java/ai/narrativetrace/api/Bar.java").writeText("")

        val found = DeepFixtureBudgetSupport.moduleTestFiles(
            tempDir, listOf("narrativetrace-core", "narrativetrace-api"),
        )

        assertEquals(2, found.size)
    }

    @Test
    fun moduleTestFilesSkipsAModuleWithNoSrcTestJavaDirectory() {
        assertEquals(emptyList<File>(), DeepFixtureBudgetSupport.moduleTestFiles(tempDir, listOf("narrativetrace-examples")))
    }

    // ---- readAllowlist ---------------------------------------------------------------------------

    @Test
    fun readAllowlistParsesAFlatArrayOfFileMethodReason() {
        val file = File(tempDir, "allowlist.json")
        file.writeText(
            """[{"file": "narrativetrace-core/src/test/java/Foo.java", "method": "shallow", "reason": "fails fast"}]""",
        )

        assertEquals(
            listOf(
                DeepFixtureBudgetSupport.AllowlistEntry(
                    "narrativetrace-core/src/test/java/Foo.java", "shallow", "fails fast",
                ),
            ),
            DeepFixtureBudgetSupport.readAllowlist(file),
        )
    }

    @Test
    fun readAllowlistReturnsEmptyForAMissingFileOrAnEmptyArray() {
        assertEquals(emptyList<DeepFixtureBudgetSupport.AllowlistEntry>(), DeepFixtureBudgetSupport.readAllowlist(File(tempDir, "missing.json")))

        val empty = File(tempDir, "empty.json")
        empty.writeText("[]")
        assertEquals(emptyList<DeepFixtureBudgetSupport.AllowlistEntry>(), DeepFixtureBudgetSupport.readAllowlist(empty))
    }

    // ---- lint --------------------------------------------------------------------------------------

    @Test
    fun lintReportsAnUnbudgetedUnlistedHitAsAViolationAndNeverFlagsAnAllowlistedOne() {
        val leftover = DeepFixtureBudgetSupport.Hit("Foo.java", "leftover", 4, hasTimeout = false)
        val excused = DeepFixtureBudgetSupport.Hit("Foo.java", "excused", 10, hasTimeout = false)
        val budgeted = DeepFixtureBudgetSupport.Hit("Foo.java", "budgeted", 16, hasTimeout = true)

        val result = DeepFixtureBudgetSupport.lint(
            listOf(leftover, excused, budgeted),
            listOf(DeepFixtureBudgetSupport.AllowlistEntry("Foo.java", "excused", "fails fast")),
        )

        assertEquals(listOf(leftover), result.violations)
        assertEquals(emptyList<DeepFixtureBudgetSupport.AllowlistEntry>(), result.staleAllowlistEntries)
    }

    @Test
    fun lintFlagsAnAllowlistEntryWithNoMatchingHitAsStale() {
        val result = DeepFixtureBudgetSupport.lint(
            emptyList(),
            listOf(DeepFixtureBudgetSupport.AllowlistEntry("Foo.java", "gone", "no longer needed")),
        )

        assertEquals(emptyList<DeepFixtureBudgetSupport.Hit>(), result.violations)
        assertEquals(1, result.staleAllowlistEntries.size)
    }

    @Test
    fun lintIsCleanWhenNothingMatchesAndNothingIsAllowlisted() {
        val result = DeepFixtureBudgetSupport.lint(emptyList(), emptyList())

        assertEquals(emptyList<DeepFixtureBudgetSupport.Hit>(), result.violations)
        assertEquals(emptyList<DeepFixtureBudgetSupport.AllowlistEntry>(), result.staleAllowlistEntries)
    }
}
