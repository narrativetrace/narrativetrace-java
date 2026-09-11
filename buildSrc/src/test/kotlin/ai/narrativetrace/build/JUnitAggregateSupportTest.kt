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

class JUnitAggregateSupportTest {

    @TempDir
    lateinit var dir: File

    private fun writeSuite(name: String, tests: Int, failures: Int, errors: Int, skipped: Int, time: Double) {
        File(dir, "TEST-$name.xml").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            |<testsuite name="$name" tests="$tests" failures="$failures" errors="$errors" skipped="$skipped" time="$time">
            |</testsuite>
            """.trimMargin()
        )
    }

    @Test
    fun `reads every TEST- xml directly in the directory`() {
        writeSuite("ai.narrativetrace.core.FooTest", tests = 5, failures = 0, errors = 0, skipped = 1, time = 1.234)
        writeSuite("ai.narrativetrace.core.BarTest", tests = 3, failures = 1, errors = 0, skipped = 0, time = 0.5)

        val suites = JUnitAggregateSupport.readDir(dir, "narrativetrace-core")

        assertEquals(2, suites.size)
        val foo = suites.first { it.className == "ai.narrativetrace.core.FooTest" }
        assertEquals(5, foo.tests)
        assertEquals(1, foo.skipped)
        assertEquals("narrativetrace-core", foo.module)
    }

    @Test
    fun `an absent directory reads as no suites, not an error`() {
        assertEquals(emptyList<TestSuiteResult>(), JUnitAggregateSupport.readDir(File(dir, "missing"), "m"))
    }

    @Test
    fun `ignores files that are not TEST- reports`() {
        File(dir, "notes.txt").writeText("irrelevant")
        writeSuite("ai.narrativetrace.core.FooTest", tests = 1, failures = 0, errors = 0, skipped = 0, time = 0.1)

        assertEquals(1, JUnitAggregateSupport.readDir(dir, "m").size)
    }

    @Test
    fun `passed excludes failures, errors and skips, floored at zero`() {
        val suite = TestSuiteResult("X", "m", tests = 10, failures = 2, errors = 1, skipped = 1, timeSeconds = 0.0)
        assertEquals(6, JUnitAggregateSupport.passed(suite))

        val malformed = TestSuiteResult("Y", "m", tests = 1, failures = 5, errors = 0, skipped = 0, timeSeconds = 0.0)
        assertEquals(0, JUnitAggregateSupport.passed(malformed))
    }

    @Test
    fun `summarize sums across suites into the shared metric shape`() {
        val suites = listOf(
            TestSuiteResult("A", "m", tests = 5, failures = 1, errors = 0, skipped = 1, timeSeconds = 1.0),
            TestSuiteResult("B", "m", tests = 3, failures = 0, errors = 1, skipped = 0, timeSeconds = 2.0),
        )

        val metrics = JUnitAggregateSupport.summarize(suites)

        assertEquals(5, metrics["tests_passed"]) // (5-1-0-1) + (3-0-1-0)
        assertEquals(2, metrics["tests_failed"]) // failures + errors
        assertEquals(1, metrics["tests_skipped"])
        assertEquals(2, metrics["test_classes"])
    }

    @Test
    fun `allGreen is false when any suite has a failure or an error`() {
        val green = listOf(TestSuiteResult("A", "m", 5, 0, 0, 0, 1.0))
        val red = listOf(TestSuiteResult("A", "m", 5, 0, 0, 0, 1.0), TestSuiteResult("B", "m", 3, 0, 1, 0, 1.0))

        assertTrue(JUnitAggregateSupport.allGreen(green))
        assertTrue(!JUnitAggregateSupport.allGreen(red))
    }
}
