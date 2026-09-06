/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FuzzReportSupportTest {

    @TempDir
    lateinit var dir: File

    private val testClass = "ai.narrativetrace.security.fuzz.TemplateFuzzTest"

    private fun report(suiteAttributes: String, cases: String): File =
        dir.resolve("TEST-$testClass.xml").apply {
            writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="$testClass" $suiteAttributes>
                $cases
                </testsuite>
                """.trimIndent()
            )
        }

    private fun fuzzed() = report(
        """tests="105" skipped="0" failures="0" errors="0" time="304.829"""",
        """  <testcase name="empty" classname="$testClass" time="0.0"/>
           |  <testcase name="Fuzzing..." classname="$testClass" time="301.0"/>
        """.trimMargin()
    )

    @Test
    fun aTargetThatFuzzedHasNoProblem() {
        report(
            """tests="105" skipped="0" failures="0" errors="0" time="304.829"""",
            """  <testcase name="Fuzzing..." classname="$testClass" time="301.0"/>"""
        )

        val read = FuzzReportSupport.read("Template", testClass, dir)

        assertTrue(read.fuzzed)
        assertEquals(null, read.problem)
        assertEquals(105, read.runs)
        assertEquals(304.829, read.seconds)
    }

    @Test
    fun aSkippedTargetIsTheDefectThisCheckExistsFor() {
        report(
            """tests="1" skipped="1" failures="0" errors="0" time="0.01"""",
            """  <testcase name="Fuzzing..." classname="$testClass" time="0.0"><skipped/></testcase>"""
        )

        val read = FuzzReportSupport.read("Template", testClass, dir)

        assertEquals(1, read.skipped)
        assertTrue(read.problem!!.contains("skipped=1"))
    }

    @Test
    fun aSeedCorpusReplayIsNotFuzzing() {
        report(
            """tests="34" skipped="0" failures="0" errors="0" time="0.111"""",
            """  <testcase name="empty" classname="$testClass" time="0.0"/>"""
        )

        val read = FuzzReportSupport.read("Template", testClass, dir)

        assertFalse(read.fuzzed)
        assertTrue(read.problem!!.contains("Fuzzing..."))
    }

    @Test
    fun aMissingReportIsATargetThatNeverRan() {
        val read = FuzzReportSupport.read("Template", testClass, dir)

        assertFalse(read.fuzzed)
        assertTrue(read.problem!!.contains("did not run at all"))
    }

    @Test
    fun aSuiteWithNoAttributesReadsAsUnfuzzedRatherThanThrowing() {
        report("", "")

        val read = FuzzReportSupport.read("Template", testClass, dir)

        assertEquals(0, read.runs)
        assertEquals(0.0, read.seconds)
        assertFalse(read.fuzzed)
    }

    @Test
    fun theLineNamesTheTargetAndItsInvocationCount() {
        fuzzed()

        assertEquals(
            "  Template: fuzzed, 105 invocations in 305s",
            FuzzReportSupport.line(FuzzReportSupport.read("Template", testClass, dir))
        )
    }

    @Test
    fun theLineOfAnUnfuzzedTargetSaysSoLoudly() {
        report("""tests="1" skipped="1" time="0.01"""", "")

        assertTrue(FuzzReportSupport.line(FuzzReportSupport.read("Template", testClass, dir)).contains("NOT FUZZED"))
    }

    @Test
    fun onlyTheTargetsThatCouldNotProveTheyFuzzedAreProblems() {
        fuzzed()
        val good = FuzzReportSupport.read("Template", testClass, dir)
        val bad = FuzzReportSupport.read("Traceparent", "ai.narrativetrace.security.fuzz.TraceparentFuzzTest", dir)

        val problems = FuzzReportSupport.problems(listOf(good, bad))

        assertEquals(1, problems.size)
        assertTrue(problems[0].startsWith("Traceparent: "))
    }
}
