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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class PmdViolationSupportTest {
    @TempDir
    lateinit var tempDir: File

    private fun pmdXml(name: String, rule: String, priority: Int): File {
        val xml = tempDir.resolve(name)
        xml.writeText(
            """
            <pmd>
              <file name="/repo/module/src/main/java/ai/sample/OrderService.java">
                <violation beginline="10" endline="14" rule="$rule" ruleset="Best Practices" priority="$priority">
                  Avoid unused private methods.
                </violation>
              </file>
            </pmd>
            """.trimIndent()
        )
        return xml
    }

    @Test
    fun collectViolationsParsesAttributesAndTrimsMessage() {
        val violations = PmdViolationSupport.collectViolations(
            listOf(PmdReportInput("module-a", "main", pmdXml("main.xml", "UnusedPrivateMethod", 3)))
        )

        assertEquals(1, violations.size)
        val v = violations[0]
        assertEquals("/repo/module/src/main/java/ai/sample/OrderService.java", v.file)
        assertEquals(10, v.beginLine)
        assertEquals(14, v.endLine)
        assertEquals("UnusedPrivateMethod", v.rule)
        assertEquals("Best Practices", v.ruleset)
        assertEquals(3, v.priority)
        assertEquals("Avoid unused private methods.", v.message)
        assertEquals("module-a", v.module)
        assertEquals("main", v.sourceSet)
    }

    @Test
    fun collectViolationsMergesInputsAndSkipsMissingFiles() {
        val violations = PmdViolationSupport.collectViolations(
            listOf(
                PmdReportInput("module-a", "main", pmdXml("main.xml", "UnusedPrivateMethod", 3)),
                PmdReportInput("module-a", "test", tempDir.resolve("absent.xml")),
                PmdReportInput("module-b", "main", pmdXml("other.xml", "EmptyCatchBlock", 1)),
            )
        )

        assertEquals(2, violations.size)
        assertEquals(setOf("module-a", "module-b"), violations.map { it.module }.toSet())
    }

    @Test
    fun printReportWithoutViolationsSaysSo() {
        val out = ByteArrayOutputStream()

        PmdViolationSupport.printReport(emptyList(), PrintStream(out))

        assertTrue(out.toString().contains("No PMD violations found."))
    }

    @Test
    fun printReportOrdersByPriorityAndSummarizesByRule() {
        val violations = PmdViolationSupport.collectViolations(
            listOf(
                PmdReportInput("module-a", "main", pmdXml("main.xml", "UnusedPrivateMethod", 3)),
                PmdReportInput("module-b", "main", pmdXml("other.xml", "EmptyCatchBlock", 1)),
            )
        )
        val out = ByteArrayOutputStream()

        PmdViolationSupport.printReport(violations, PrintStream(out))

        val text = out.toString()
        assertTrue(text.contains("PMD VIOLATIONS (2 total)"))
        assertTrue(text.indexOf("EmptyCatchBlock") < text.indexOf("UnusedPrivateMethod"))
        assertTrue(text.contains("src/main/java/ai/sample/OrderService.java:10"))
        assertTrue(text.contains("By rule: EmptyCatchBlock(1), UnusedPrivateMethod(1)"))
    }
}
