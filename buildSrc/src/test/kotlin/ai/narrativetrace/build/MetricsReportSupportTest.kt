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

class MetricsReportSupportTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun collectEntriesParsesMethodAndClassViolations() {
        val xml = tempDir.resolve("metrics.xml")
        xml.writeText(
            """
            <pmd>
              <file>
                <violation beginline="12" package="ai.sample" class="OrderService" method="checkout">NCSS line count of 37</violation>
                <violation beginline="4" package="ai.sample" class="OrderService">NCSS line count of 88</violation>
                <violation beginline="33" package="ai.sample" class="OrderService" method="ignore">Different message</violation>
              </file>
            </pmd>
            """.trimIndent()
        )

        val entries = MetricsReportSupport.collectEntries(listOf(MetricsInput("module-a", xml)))

        assertEquals(2, entries.size)
        assertEquals(NcssEntry(37, "method", "ai.sample.OrderService#checkout", "module-a", 12), entries[0])
        assertEquals(NcssEntry(88, "class", "ai.sample.OrderService", "module-a", 4), entries[1])
    }

    @Test
    fun printReportSortsAndIncludesTotals() {
        val entries = listOf(
            NcssEntry(15, "method", "a.C#m1", "m1", 10),
            NcssEntry(60, "method", "a.C#m2", "m2", 20),
            NcssEntry(20, "class", "a.C", "m1", 1),
            NcssEntry(80, "class", "b.D", "m2", 2)
        )

        val output = ByteArrayOutputStream()
        MetricsReportSupport.printReport(entries, PrintStream(output))
        val text = output.toString()

        assertTrue(text.contains("METHODS BY NCSS"))
        assertTrue(text.contains("CLASSES BY NCSS"))
        assertTrue(text.contains("Total: 2 methods, 2 classes"))
        assertTrue(text.indexOf("a.C#m2") < text.indexOf("a.C#m1"))

        val classSectionStart = text.indexOf("CLASSES BY NCSS")
        assertTrue(classSectionStart >= 0)
        val classDIndex = text.indexOf("b.D", classSectionStart)
        val classCIndex = text.indexOf("a.C", classSectionStart)
        assertTrue(classDIndex < classCIndex)
    }
}
