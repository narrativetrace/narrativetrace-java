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

data class PmdViolation(
    val file: String,
    val beginLine: Int,
    val endLine: Int,
    val rule: String,
    val ruleset: String,
    val priority: Int,
    val message: String,
    val module: String,
    val sourceSet: String,
)

data class PmdReportInput(val module: String, val sourceSet: String, val xmlFile: File)

object PmdViolationSupport {

    fun collectViolationsFromProjects(projects: Iterable<Project>): List<PmdViolation> {
        val inputs = projects.flatMap { project ->
            listOf(
                PmdReportInput(project.name, "main", project.file("build/reports/pmd/main.xml")),
                PmdReportInput(project.name, "test", project.file("build/reports/pmd/test.xml")),
            )
        }
        return collectViolations(inputs)
    }

    fun collectViolations(inputs: Iterable<PmdReportInput>): List<PmdViolation> {
        val violations = mutableListOf<PmdViolation>()
        inputs.forEach { input ->
            val xml = input.xmlFile
            if (!xml.exists()) return@forEach

            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            val doc = dbf.newDocumentBuilder().parse(xml)
            val files = doc.getElementsByTagName("file")
            for (i in 0 until files.length) {
                val fileNode = files.item(i)
                val filePath = fileNode.attributes.getNamedItem("name").textContent
                val children = fileNode.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeName != "violation") continue
                    val attrs = child.attributes
                    violations.add(
                        PmdViolation(
                            file = filePath,
                            beginLine = attrs.getNamedItem("beginline").textContent.toInt(),
                            endLine = attrs.getNamedItem("endline").textContent.toInt(),
                            rule = attrs.getNamedItem("rule").textContent,
                            ruleset = attrs.getNamedItem("ruleset").textContent,
                            priority = attrs.getNamedItem("priority").textContent.toInt(),
                            message = child.textContent.trim(),
                            module = input.module,
                            sourceSet = input.sourceSet,
                        )
                    )
                }
            }
        }
        return violations
    }

    fun printReport(violations: List<PmdViolation>, out: PrintStream = System.out) {
        if (violations.isEmpty()) {
            out.println("\nNo PMD violations found.")
            return
        }
        val sorted = violations.sortedWith(compareBy({ it.priority }, { it.module }, { it.file }))
        out.println("\n${"=".repeat(100)}")
        out.println("PMD VIOLATIONS (${sorted.size} total)")
        out.println("=".repeat(100))
        sorted.forEach { v ->
            val shortFile = v.file.substringAfterLast("/src/")
            out.println("  P${v.priority}  ${v.module}  src/$shortFile:${v.beginLine}")
            out.println("      [${v.rule}] ${v.message}")
        }
        out.println("-".repeat(100))
        val byRule = sorted.groupBy { it.rule }.mapValues { it.value.size }.toSortedMap()
        out.println("By rule: ${byRule.entries.joinToString { "${it.key}(${it.value})" }}")
    }
}
