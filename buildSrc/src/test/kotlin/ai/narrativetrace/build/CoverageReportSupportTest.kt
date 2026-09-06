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

class CoverageReportSupportTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun parsesClassCoverageFromJacocoXml() {
        val xml = tempDir.resolve("jacoco.xml")
        xml.writeText(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
            <report name="test-module">
              <package name="ai/sample">
                <class name="ai/sample/OrderService" sourcefilename="OrderService.java">
                  <counter type="LINE" missed="3" covered="47"/>
                </class>
                <class name="ai/sample/PaymentService" sourcefilename="PaymentService.java">
                  <counter type="LINE" missed="0" covered="20"/>
                </class>
              </package>
            </report>
            """.trimIndent()
        )

        val entries = CoverageReportSupport.collectEntries(
            listOf(CoverageInput("module-a", xml))
        )

        assertEquals(2, entries.size)
        assertEquals(CoverageEntry("ai.sample.OrderService", "module-a", 3, 47), entries[0])
        assertEquals(CoverageEntry("ai.sample.PaymentService", "module-a", 0, 20), entries[1])
    }

    @Test
    fun skipsNonExistentXmlFile() {
        val missing = tempDir.resolve("nope.xml")
        val entries = CoverageReportSupport.collectEntries(
            listOf(CoverageInput("m", missing))
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun printReportSortsByMissedDescending() {
        val entries = listOf(
            CoverageEntry("a.Small", "m1", 1, 99),
            CoverageEntry("a.Big", "m1", 10, 40),
            CoverageEntry("a.Perfect", "m1", 0, 50)
        )

        val output = ByteArrayOutputStream()
        CoverageReportSupport.printReport(entries, PrintStream(output))
        val text = output.toString()

        assertTrue(text.contains("COVERAGE BY CLASS"))
        assertTrue(text.indexOf("a.Big") < text.indexOf("a.Small"))
        assertTrue(text.indexOf("a.Small") < text.indexOf("a.Perfect"))
    }

    @Test
    fun printReportShowsRatioAsPercentage() {
        val entries = listOf(
            CoverageEntry("a.Svc", "m1", 2, 8)
        )

        val output = ByteArrayOutputStream()
        CoverageReportSupport.printReport(entries, PrintStream(output))
        val text = output.toString()

        assertTrue(text.contains("80.0%"))
    }

    @Test
    fun aggregatesMultipleModules() {
        val xml1 = tempDir.resolve("jacoco1.xml")
        xml1.writeText(
            """
            <report name="mod-a">
              <package name="a">
                <class name="a/Foo" sourcefilename="Foo.java">
                  <counter type="LINE" missed="5" covered="15"/>
                </class>
              </package>
            </report>
            """.trimIndent()
        )
        val xml2 = tempDir.resolve("jacoco2.xml")
        xml2.writeText(
            """
            <report name="mod-b">
              <package name="b">
                <class name="b/Bar" sourcefilename="Bar.java">
                  <counter type="LINE" missed="0" covered="30"/>
                </class>
              </package>
            </report>
            """.trimIndent()
        )

        val entries = CoverageReportSupport.collectEntries(
            listOf(CoverageInput("mod-a", xml1), CoverageInput("mod-b", xml2))
        )

        assertEquals(2, entries.size)
        assertEquals("mod-a", entries[0].module)
        assertEquals("mod-b", entries[1].module)
    }
}
