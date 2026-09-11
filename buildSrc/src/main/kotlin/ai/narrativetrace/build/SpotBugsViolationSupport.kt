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
import javax.xml.parsers.DocumentBuilderFactory

/** One SpotBugs finding — `category` is SpotBugs' own top-level grouping (SECURITY, STYLE, …). */
data class SpotBugsViolation(val module: String, val type: String, val category: String, val priority: Int)

data class SpotBugsReportInput(val module: String, val xmlFile: File)

/**
 * INTENT: Splits one `spotbugsMain` XML report into the two roles it plays in `verifyAll` — SpotBugs'
 * own `SECURITY` category is FindSecBugs' contribution and belongs on the `sast` row; every other
 * category (`CORRECTNESS`, `STYLE`, `BAD_PRACTICE`, …) is style/correctness and belongs on `lint`.
 * One scan produces both rows; this is the reader that tells them apart rather than running SpotBugs
 * twice.
 */
object SpotBugsViolationSupport {

    const val SECURITY_CATEGORY = "SECURITY"

    fun collectFromProjects(projects: Iterable<Project>): List<SpotBugsViolation> =
        collect(projects.map { SpotBugsReportInput(it.name, it.file("build/reports/spotbugs/main.xml")) })

    fun collect(inputs: Iterable<SpotBugsReportInput>): List<SpotBugsViolation> {
        val violations = mutableListOf<SpotBugsViolation>()
        inputs.forEach { input ->
            if (!input.xmlFile.exists()) return@forEach
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            val doc = dbf.newDocumentBuilder().parse(input.xmlFile)
            val bugs = doc.getElementsByTagName("BugInstance")
            for (i in 0 until bugs.length) {
                val node = bugs.item(i)
                val attrs = node.attributes
                violations.add(
                    SpotBugsViolation(
                        module = input.module,
                        type = attrs.getNamedItem("type")?.textContent ?: "UNKNOWN",
                        category = attrs.getNamedItem("category")?.textContent ?: "UNKNOWN",
                        priority = attrs.getNamedItem("priority")?.textContent?.toIntOrNull() ?: 0,
                    )
                )
            }
        }
        return violations
    }

    fun securityFindings(violations: List<SpotBugsViolation>): List<SpotBugsViolation> =
        violations.filter { it.category == SECURITY_CATEGORY }

    fun styleFindings(violations: List<SpotBugsViolation>): List<SpotBugsViolation> =
        violations.filter { it.category != SECURITY_CATEGORY }
}
