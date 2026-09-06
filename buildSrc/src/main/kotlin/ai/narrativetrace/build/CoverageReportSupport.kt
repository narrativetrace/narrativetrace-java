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

data class CoverageEntry(val className: String, val module: String, val missed: Int, val covered: Int) {
    val total: Int get() = missed + covered
    val ratio: Double get() = if (total == 0) 0.0 else covered.toDouble() / total
}

data class CoverageInput(val module: String, val xmlFile: File)

object CoverageReportSupport {
    fun collectEntriesFromProjects(projects: Iterable<Project>): List<CoverageEntry> {
        val inputs = projects.map {
            CoverageInput(it.name, it.file("build/reports/jacoco/test/jacocoTestReport.xml"))
        }
        return collectEntries(inputs)
    }

    fun collectEntries(inputs: Iterable<CoverageInput>): List<CoverageEntry> {
        val entries = mutableListOf<CoverageEntry>()
        inputs.forEach { input ->
            if (!input.xmlFile.exists()) return@forEach
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            // Disable DTD loading to avoid network access
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            val doc = dbf.newDocumentBuilder().parse(input.xmlFile)
            val classes = doc.getElementsByTagName("class")
            for (i in 0 until classes.length) {
                val node = classes.item(i)
                val className = node.attributes.getNamedItem("name")?.textContent?.replace('/', '.') ?: continue
                val counters = node.childNodes
                for (j in 0 until counters.length) {
                    val counter = counters.item(j)
                    if (counter.nodeName == "counter" &&
                        counter.attributes?.getNamedItem("type")?.textContent == "LINE"
                    ) {
                        val missed = counter.attributes.getNamedItem("missed").textContent.toInt()
                        val covered = counter.attributes.getNamedItem("covered").textContent.toInt()
                        entries.add(CoverageEntry(className, input.module, missed, covered))
                    }
                }
            }
        }
        return entries
    }

    fun printReport(entries: List<CoverageEntry>, out: PrintStream = System.out) {
        val sorted = entries.sortedByDescending { it.missed }

        out.println("\n${"=".repeat(110)}")
        out.println("COVERAGE BY CLASS (sorted by missed lines)")
        out.println("=".repeat(110))
        out.println("%-6s  %-6s  %-7s  %-60s  %s".format("Miss", "Cover", "Ratio", "Class", "Module"))
        out.println("-".repeat(110))
        sorted.forEach {
            out.println(
                "%-6d  %-6d  %-7s  %-60s  %s".format(
                    it.missed, it.covered, "%.1f%%".format(it.ratio * 100), it.className, it.module
                )
            )
        }
        val totalMissed = entries.sumOf { it.missed }
        val totalCovered = entries.sumOf { it.covered }
        val totalRatio = if (totalMissed + totalCovered == 0) 0.0 else totalCovered.toDouble() / (totalMissed + totalCovered)
        out.println("-".repeat(110))
        out.println(
            "%-6d  %-6d  %-7s  %-60s".format(
                totalMissed, totalCovered, "%.1f%%".format(totalRatio * 100), "TOTAL (${entries.size} classes)"
            )
        )
    }
}
