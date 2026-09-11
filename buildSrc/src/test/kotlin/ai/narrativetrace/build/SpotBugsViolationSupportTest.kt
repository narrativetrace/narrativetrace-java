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

class SpotBugsViolationSupportTest {

    @TempDir
    lateinit var dir: File

    private fun report(vararg bugs: Triple<String, String, Int>): File {
        val file = File(dir, "main.xml")
        val body = bugs.joinToString("\n") { (type, category, priority) ->
            "<BugInstance type=\"$type\" category=\"$category\" priority=\"$priority\"></BugInstance>"
        }
        file.writeText("<BugCollection>$body</BugCollection>")
        return file
    }

    @Test
    fun `splits security findings from everything else`() {
        val file = report(
            Triple("SQL_INJECTION", "SECURITY", 1),
            Triple("DM_DEFAULT_ENCODING", "STYLE", 3),
        )

        val violations = SpotBugsViolationSupport.collect(listOf(SpotBugsReportInput("core", file)))

        assertEquals(1, SpotBugsViolationSupport.securityFindings(violations).size)
        assertEquals(1, SpotBugsViolationSupport.styleFindings(violations).size)
        assertEquals("SQL_INJECTION", SpotBugsViolationSupport.securityFindings(violations).single().type)
    }

    @Test
    fun `a missing report contributes no violations, not an error`() {
        val violations = SpotBugsViolationSupport.collect(listOf(SpotBugsReportInput("core", File(dir, "absent.xml"))))

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `an empty report (no findings) is clean on both categories`() {
        val file = report()

        val violations = SpotBugsViolationSupport.collect(listOf(SpotBugsReportInput("core", file)))

        assertTrue(SpotBugsViolationSupport.securityFindings(violations).isEmpty())
        assertTrue(SpotBugsViolationSupport.styleFindings(violations).isEmpty())
    }
}
