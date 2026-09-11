/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class MutationReportSupportTest {
    @TempDir
    lateinit var tempDir: File

    private fun mutationsXml(): File {
        val xml = tempDir.resolve("mutations.xml")
        xml.writeText(
            """
            <mutations>
              <mutation status="KILLED">
                <sourceFile>OrderService.java</sourceFile>
                <mutatedClass>ai.sample.OrderService</mutatedClass>
                <mutatedMethod>checkout</mutatedMethod>
                <lineNumber>42</lineNumber>
                <mutator>org.pitest.mutationtest.engine.gregor.mutators.MathMutator</mutator>
                <description>Replaced integer addition with subtraction</description>
              </mutation>
              <mutation status="SURVIVED">
                <sourceFile>OrderService.java</sourceFile>
                <mutatedClass>ai.sample.OrderService</mutatedClass>
                <mutatedMethod>total</mutatedMethod>
                <lineNumber>77</lineNumber>
                <mutator>org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator</mutator>
                <description>Mutated return of integer sized value</description>
              </mutation>
            </mutations>
            """.trimIndent()
        )
        return xml
    }

    @Test
    fun collectEntriesParsesMutationFieldsAndShortensMutatorName() {
        val entries = MutationReportSupport.collectEntries(
            listOf(MutationReportInput("module-a", mutationsXml()))
        )

        assertEquals(2, entries.size)
        val killed = entries[0]
        assertEquals("OrderService.java", killed.sourceFile)
        assertEquals("ai.sample.OrderService", killed.mutatedClass)
        assertEquals("checkout", killed.mutatedMethod)
        assertEquals(42, killed.lineNumber)
        assertEquals("MathMutator", killed.mutator)
        assertEquals("Replaced integer addition with subtraction", killed.description)
        assertEquals("KILLED", killed.status)
        assertEquals("module-a", killed.module)
        assertEquals("SURVIVED", entries[1].status)
    }

    @Test
    fun collectEntriesSkipsMissingReportFiles() {
        val entries = MutationReportSupport.collectEntries(
            listOf(MutationReportInput("module-a", tempDir.resolve("absent.xml")))
        )

        assertEquals(emptyList<MutationEntry>(), entries)
    }

    @Test
    fun killRateIsZeroForEmptyModuleAndFractionOtherwise() {
        assertEquals(0.0, MutationSummary("m", killed = 0, survived = 0, noCoverage = 0, total = 0).killRate)
        assertEquals(0.75, MutationSummary("m", killed = 3, survived = 1, noCoverage = 0, total = 4).killRate)
    }

    @Test
    fun printReportWithoutEntriesPointsAtPitest() {
        val out = ByteArrayOutputStream()

        MutationReportSupport.printReport(emptyList(), PrintStream(out))

        assertTrue(out.toString().contains("No mutation reports found"))
    }

    @Test
    fun printReportSummarizesModulesAndListsSurvivingMutants() {
        val entries = MutationReportSupport.collectEntries(
            listOf(MutationReportInput("module-a", mutationsXml()))
        )
        val out = ByteArrayOutputStream()

        MutationReportSupport.printReport(entries, PrintStream(out))

        val text = out.toString()
        assertTrue(text.contains("MUTATION TESTING SUMMARY"))
        assertTrue(text.contains("module-a"))
        assertTrue(text.contains("TOTAL"))
        assertTrue(text.contains("SURVIVING MUTANTS (1)"))
        assertTrue(text.contains("Mutated return of integer sized value"))
        assertTrue(text.contains("50.0%"))
    }

    @Test
    fun printReportCelebratesWhenEveryMutantIsKilled() {
        val entries = MutationReportSupport.collectEntries(
            listOf(MutationReportInput("module-a", mutationsXml()))
        ).filter { it.status == "KILLED" }
        val out = ByteArrayOutputStream()

        MutationReportSupport.printReport(entries, PrintStream(out))

        val text = out.toString()
        assertTrue(text.contains("All mutants killed!"))
        assertTrue(!text.contains("SURVIVING MUTANTS"))
    }

    // Regression for the module-filter bug found while wiring `verifyAll`'s mutation category:
    // `collectEntriesFromProjects` once silently dropped narrativetrace-api and
    // narrativetrace-glossary through an internally hand-synced module list. That list is gone —
    // the caller now supplies the module set explicitly (build.gradle.kts's own
    // `mutationTestedModules`), so this proves the filter honours exactly the set it is given.
    @Test
    fun collectEntriesFromProjectsReadsExactlyTheGivenModuleSet() {
        val root = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val moduleNames = listOf(
            "narrativetrace-api", "narrativetrace-core", "narrativetrace-proxy",
            "narrativetrace-clarity", "narrativetrace-glossary", "narrativetrace-agent",
        )
        val subprojects = moduleNames.map { name ->
            val dir = tempDir.resolve(name).also { it.mkdirs() }
            val sub = ProjectBuilder.builder().withName(name).withProjectDir(dir).withParent(root).build()
            val reportsDir = dir.resolve("build/reports/pitest").also { it.mkdirs() }
            reportsDir.resolve("mutations.xml").writeText(mutationsXml().readText())
            sub
        }

        val entries = MutationReportSupport.collectEntriesFromProjects(
            subprojects,
            setOf(
                "narrativetrace-api", "narrativetrace-core", "narrativetrace-proxy",
                "narrativetrace-clarity", "narrativetrace-glossary",
            )
        )

        // 2 mutations per module report * the 5 requested modules; narrativetrace-agent is
        // excluded here (it runs through its own isolated `pitestAgent` task, not this aggregate),
        // even though its report file exists on disk — the filter is by module set, not by
        // whether a mutations.xml happens to be sitting there.
        assertEquals(10, entries.size)
        assertTrue(entries.none { it.module == "narrativetrace-agent" })
        assertTrue(entries.any { it.module == "narrativetrace-api" })
        assertTrue(entries.any { it.module == "narrativetrace-glossary" })
    }
}
