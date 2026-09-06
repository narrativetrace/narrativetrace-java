/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.gradle.api.Project
import java.io.File
import java.io.PrintStream
import javax.xml.parsers.DocumentBuilderFactory

data class NcssEntry(val ncss: Int, val type: String, val name: String, val module: String, val line: Int)

data class MetricsInput(val module: String, val xmlFile: File)

object MetricsReportSupport {
    private val ncssRegex = Regex("""NCSS line count of (\d+)""")

    fun collectEntriesFromProjects(projects: Iterable<Project>): List<NcssEntry> {
        val inputs = projects.map { MetricsInput(it.name, it.file("build/reports/pmd/metrics.xml")) }
        return collectEntries(inputs)
    }

    fun collectEntries(inputs: Iterable<MetricsInput>): List<NcssEntry> {
        val entries = mutableListOf<NcssEntry>()
        inputs.forEach { input ->
            val xml = input.xmlFile
            if (!xml.exists()) {
                return@forEach
            }

            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            val doc = dbf.newDocumentBuilder().parse(xml)
            val violations = doc.getElementsByTagName("violation")
            for (i in 0 until violations.length) {
                val node = violations.item(i)
                val attrs = node.attributes
                val beginLine = attrs.getNamedItem("beginline").textContent.toInt()
                val pkg = attrs.getNamedItem("package")?.textContent ?: ""
                val cls = attrs.getNamedItem("class")?.textContent ?: ""
                val method = attrs.getNamedItem("method")?.textContent
                val text = node.textContent.trim()
                val ncssMatch = ncssRegex.find(text)
                if (ncssMatch != null) {
                    val ncss = ncssMatch.groupValues[1].toInt()
                    val fqn = if (method != null) "$pkg.$cls#$method" else "$pkg.$cls"
                    val type = if (method != null) "method" else "class"
                    entries.add(NcssEntry(ncss, type, fqn, input.module, beginLine))
                }
            }
        }
        return entries
    }

    fun printReport(entries: List<NcssEntry>, out: PrintStream = System.out) {
        val methods = entries.filter { it.type == "method" }.sortedByDescending { it.ncss }
        val classes = entries.filter { it.type == "class" }.sortedByDescending { it.ncss }

        out.println("\n${"=".repeat(100)}")
        out.println("METHODS BY NCSS (non-commenting source statements)")
        out.println("=".repeat(100))
        out.println("%-6s  %-70s  %s".format("NCSS", "Method", "Module:Line"))
        out.println("-".repeat(100))
        methods.forEach { out.println("%-6d  %-70s  %s:%d".format(it.ncss, it.name, it.module, it.line)) }

        out.println("\n${"=".repeat(100)}")
        out.println("CLASSES BY NCSS")
        out.println("=".repeat(100))
        out.println("%-6s  %-70s  %s".format("NCSS", "Class", "Module:Line"))
        out.println("-".repeat(100))
        classes.forEach { out.println("%-6d  %-70s  %s:%d".format(it.ncss, it.name, it.module, it.line)) }

        out.println("\nTotal: ${methods.size} methods, ${classes.size} classes")
    }
}
