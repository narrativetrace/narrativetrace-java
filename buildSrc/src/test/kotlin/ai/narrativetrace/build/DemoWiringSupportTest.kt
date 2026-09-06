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

class DemoWiringSupportTest {

    @TempDir
    lateinit var repo: File

    private fun writeExampleSource(relative: String, content: String) {
        val file = repo.resolve("narrativetrace-examples/$relative")
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    /** Writes the base table plus, by default, byte-identical es/zh-CN chrome tables. */
    private fun writeWiring(content: String, locales: Map<String, String> = mapOf("es" to content, "zh-CN" to content)) {
        val file = repo.resolve("narrativetrace-examples/demo/wiring.awk")
        file.parentFile.mkdirs()
        file.writeText(content)
        locales.forEach { (locale, localeContent) ->
            repo.resolve("narrativetrace-examples/demo/wiring-$locale.awk").writeText(localeContent)
        }
    }

    @Test
    fun scenarioTitlesReadsInlineHeaderLiterals() {
        writeExampleSource(
            "minecraft/src/main/java/MinecraftExample.java",
            """logger.info("=== Refactored: Player Joins World ===\n");"""
        )

        assertEquals(setOf("Refactored: Player Joins World"), DemoWiringSupport.scenarioTitles(repo))
    }

    @Test
    fun scenarioTitlesReadsLabelsPassedToBeginTrace() {
        writeExampleSource(
            "ecommerce/src/main/java/ECommerceExample.java",
            """beginTrace("Scenario 5: Out of Stock");"""
        )

        assertEquals(setOf("Scenario 5: Out of Stock"), DemoWiringSupport.scenarioTitles(repo))
    }

    @Test
    fun scenarioTitlesIgnoresParameterizedHeadersThatPrintALabelAtRuntime() {
        writeExampleSource(
            "ecommerce/src/main/java/ECommerceExample.java",
            """
            logger.info("=== {} ===\n", label);
            beginTrace("Scenario 5: Out of Stock");
            """.trimIndent()
        )

        assertEquals(setOf("Scenario 5: Out of Stock"), DemoWiringSupport.scenarioTitles(repo))
    }

    @Test
    fun scenarioTitlesIgnoresHeadersBuiltByStringConcatenation() {
        writeExampleSource(
            "common/src/main/java/DemoTraces.java",
            """Files.writeString(dir.resolve(name), "=== " + scenario + " ===\n\n" + text);"""
        )
        writeExampleSource(
            "ecommerce/src/main/java/ECommerceExample.java",
            """beginTrace("Scenario 5: Out of Stock");"""
        )

        assertEquals(setOf("Scenario 5: Out of Stock"), DemoWiringSupport.scenarioTitles(repo))
    }

    @Test
    fun wiringKeysReadsTheScenarioKeysOfTheAwkTable() {
        writeWiring(
            """
            BEGIN {
              wiring["Scenario 5: Out of Stock"] = \
                "Wiring: same Spring proxies as scenario 1.\n" \
                "@OnError on InventoryService.reserve supplies the bracketed message."
              wiring["Refactored: Player Joins World"] = "Wiring: NarrativeTraceProxy.trace(...)."
            }
            """.trimIndent()
        )

        assertEquals(
            setOf("Scenario 5: Out of Stock", "Refactored: Player Joins World"),
            DemoWiringSupport.wiringKeys(repo)
        )
    }

    @Test
    fun checkReportsAScenarioTheLauncherCannotExplain() {
        writeExampleSource(
            "library/src/main/kotlin/LibraryExample.kt",
            """
            logger.info("=== Scenario 1: Successful Book Borrow ===\n")
            logger.info("=== Scenario 2: Book Unavailable ===\n")
            """.trimIndent()
        )
        writeWiring("""BEGIN { wiring["Scenario 1: Successful Book Borrow"] = "Wiring: proxies." }""")

        val problems = DemoWiringSupport.check(repo)

        assertEquals(1, problems.size, problems.toString())
        assertTrue(
            problems.single().contains("Scenario 2: Book Unavailable"),
            problems.single()
        )
    }

    @Test
    fun checkReportsAWiringNoteLeftBehindByARename() {
        writeExampleSource(
            "library/src/main/kotlin/LibraryExample.kt",
            """logger.info("=== Scenario 1: Successful Book Borrow ===\n")"""
        )
        writeWiring(
            """
            BEGIN {
              wiring["Scenario 1: Successful Book Borrow"] = "Wiring: proxies."
              wiring["Scenario 1: Successful Borrow"] = "Wiring: proxies, under the old name."
            }
            """.trimIndent()
        )

        val problems = DemoWiringSupport.check(repo)

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems.single().contains("Scenario 1: Successful Borrow"), problems.single())
    }

    @Test
    fun checkReportsAMissingWiringTableInsteadOfFailingToReadIt() {
        writeExampleSource(
            "library/src/main/kotlin/LibraryExample.kt",
            """logger.info("=== Scenario 1: Successful Book Borrow ===\n")"""
        )

        val problems = DemoWiringSupport.check(repo)

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems.single().contains("wiring.awk"), problems.single())
    }

    @Test
    fun checkReportsAWiringNoteMissingFromALocaleTable() {
        writeExampleSource(
            "library/src/main/kotlin/LibraryExample.kt",
            """logger.info("=== Scenario 1: Successful Book Borrow ===\n")"""
        )
        val base = """BEGIN { wiring["Scenario 1: Successful Book Borrow"] = "Wiring: proxies." }"""
        writeWiring(base, mapOf("es" to "BEGIN { }", "zh-CN" to base))

        val problems = DemoWiringSupport.check(repo)

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems.single().contains("wiring-es.awk"), problems.single())
        assertTrue(problems.single().contains("Scenario 1: Successful Book Borrow"), problems.single())
    }

    @Test
    fun checkReportsALocaleNoteWhoseKeyIsNotInTheBaseTable() {
        writeExampleSource(
            "library/src/main/kotlin/LibraryExample.kt",
            """logger.info("=== Scenario 1: Successful Book Borrow ===\n")"""
        )
        val base = """BEGIN { wiring["Scenario 1: Successful Book Borrow"] = "Wiring: proxies." }"""
        val divergent =
            """
            BEGIN {
              wiring["Scenario 1: Successful Book Borrow"] = "Cableado: proxies."
              wiring["Scenario 1: Successful Borrow"] = "Cableado: bajo el nombre antiguo."
            }
            """.trimIndent()
        writeWiring(base, mapOf("es" to divergent, "zh-CN" to base))

        val problems = DemoWiringSupport.check(repo)

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems.single().contains("wiring-es.awk"), problems.single())
        assertTrue(problems.single().contains("Scenario 1: Successful Borrow"), problems.single())
    }

    @Test
    fun checkReportsAMissingLocaleTable() {
        writeExampleSource(
            "library/src/main/kotlin/LibraryExample.kt",
            """logger.info("=== Scenario 1: Successful Book Borrow ===\n")"""
        )
        val base = """BEGIN { wiring["Scenario 1: Successful Book Borrow"] = "Wiring: proxies." }"""
        writeWiring(base, mapOf("zh-CN" to base))

        val problems = DemoWiringSupport.check(repo)

        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems.single().contains("wiring-es.awk"), problems.single())
    }

    @Test
    fun checkAcceptsNotesForSectionsPrintedWithoutAScenarioHeader() {
        writeExampleSource(
            "clarity/src/main/java/ClarityDemoExample.java",
            """
            logger.info("=== Scenario 1: Guest Books a Room (Excellent Naming) ===\n");
            logger.info("         CLARITY ANALYSIS REPORT");
            """.trimIndent()
        )
        writeWiring(
            """
            BEGIN {
              wiring["Scenario 1: Guest Books a Room (Excellent Naming)"] = "Wiring: proxies."
              wiring["CLARITY ANALYSIS REPORT"] = "Wiring: ClarityAnalyzer over the captured trees."
            }
            """.trimIndent()
        )

        assertEquals(emptyList<String>(), DemoWiringSupport.check(repo))
    }
}
