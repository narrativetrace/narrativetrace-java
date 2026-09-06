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

data class MutationEntry(
    val sourceFile: String,
    val mutatedClass: String,
    val mutatedMethod: String,
    val lineNumber: Int,
    val mutator: String,
    val description: String,
    val status: String,
    val module: String,
)

data class MutationReportInput(val module: String, val xmlFile: File)

data class MutationSummary(
    val module: String,
    val killed: Int,
    val survived: Int,
    val noCoverage: Int,
    val total: Int,
) {
    val killRate: Double get() = if (total == 0) 0.0 else killed.toDouble() / total
}

object MutationReportSupport {

    private val pitestModules = setOf("narrativetrace-core", "narrativetrace-proxy", "narrativetrace-clarity")

    fun collectEntriesFromProjects(projects: Iterable<Project>): List<MutationEntry> {
        val inputs = projects
            .filter { it.name in pitestModules }
            .map { MutationReportInput(it.name, it.file("build/reports/pitest/mutations.xml")) }
        return collectEntries(inputs)
    }

    fun collectEntries(inputs: Iterable<MutationReportInput>): List<MutationEntry> {
        val entries = mutableListOf<MutationEntry>()
        inputs.forEach { input ->
            if (!input.xmlFile.exists()) return@forEach
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            val doc = dbf.newDocumentBuilder().parse(input.xmlFile)
            val mutations = doc.getElementsByTagName("mutation")
            for (i in 0 until mutations.length) {
                val node = mutations.item(i)
                val status = node.attributes.getNamedItem("status").textContent
                val children = node.childNodes
                var sourceFile = ""
                var mutatedClass = ""
                var mutatedMethod = ""
                var lineNumber = 0
                var mutator = ""
                var description = ""
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    when (child.nodeName) {
                        "sourceFile" -> sourceFile = child.textContent
                        "mutatedClass" -> mutatedClass = child.textContent
                        "mutatedMethod" -> mutatedMethod = child.textContent
                        "lineNumber" -> lineNumber = child.textContent.toInt()
                        "mutator" -> mutator = child.textContent.substringAfterLast(".")
                        "description" -> description = child.textContent
                    }
                }
                entries.add(
                    MutationEntry(sourceFile, mutatedClass, mutatedMethod, lineNumber, mutator, description, status, input.module)
                )
            }
        }
        return entries
    }

    fun printReport(entries: List<MutationEntry>, out: PrintStream = System.out) {
        if (entries.isEmpty()) {
            out.println("\nNo mutation reports found. Run ./gradlew pitest first.")
            return
        }
        val summaries = entries.groupBy { it.module }.map { (module, list) ->
            MutationSummary(
                module = module,
                killed = list.count { it.status == "KILLED" },
                survived = list.count { it.status == "SURVIVED" },
                noCoverage = list.count { it.status == "NO_COVERAGE" },
                total = list.size,
            )
        }.sortedBy { it.module }

        out.println("\n${"=".repeat(90)}")
        out.println("MUTATION TESTING SUMMARY")
        out.println("=".repeat(90))
        out.println("%-30s  %7s  %7s  %7s  %7s  %8s".format("Module", "Killed", "Survived", "NoCov", "Total", "Kill %"))
        out.println("-".repeat(90))
        summaries.forEach {
            out.println(
                "%-30s  %7d  %8d  %7d  %7d  %7.1f%%".format(
                    it.module, it.killed, it.survived, it.noCoverage, it.total, it.killRate * 100
                )
            )
        }
        val totalKilled = summaries.sumOf { it.killed }
        val totalSurvived = summaries.sumOf { it.survived }
        val totalNoCov = summaries.sumOf { it.noCoverage }
        val totalAll = summaries.sumOf { it.total }
        val totalRate = if (totalAll == 0) 0.0 else totalKilled.toDouble() / totalAll
        out.println("-".repeat(90))
        out.println(
            "%-30s  %7d  %8d  %7d  %7d  %7.1f%%".format(
                "TOTAL", totalKilled, totalSurvived, totalNoCov, totalAll, totalRate * 100
            )
        )

        val survived = entries.filter { it.status == "SURVIVED" }
        if (survived.isEmpty()) {
            out.println("\nAll mutants killed!")
            return
        }

        out.println("\n${"=".repeat(90)}")
        out.println("SURVIVING MUTANTS (${survived.size})")
        out.println("=".repeat(90))
        val byClass = survived.groupBy { it.mutatedClass }.toSortedMap()
        byClass.forEach { (clazz, mutants) ->
            val module = mutants.first().module
            out.println("\n  $clazz ($module)")
            mutants.sortedBy { it.lineNumber }.forEach { m ->
                out.println("    L%-4d  %-30s  %s".format(m.lineNumber, m.mutatedMethod, m.description))
            }
        }
    }
}
