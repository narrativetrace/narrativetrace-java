/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/**
 * One `<testsuite>` element's own attributes — one JUnit XML report file is one test class, and
 * every category row `verifyAll` prints is built by summing these, never by re-running a suite.
 */
data class TestSuiteResult(
    val className: String,
    val module: String,
    val tests: Int,
    val failures: Int,
    val errors: Int,
    val skipped: Int,
    val timeSeconds: Double,
)

/**
 * INTENT: The one reader of JUnit XML `verifyAll` shares across every category whose numbers come
 * from a test run — unit-tests, property, fuzz-tier-a, and the build-tests half of conformance all
 * read the *same* `./gradlew test` output, sliced by which test classes belong to which category,
 * rather than paying for a second test invocation per category.
 *
 * Attributes are read by regex, not a DOM parse, deliberately — a `<testsuite>` element's own
 * attributes never contain a literal `"`, and `verifyAll` never touches the innumerable `<testcase>`
 * children, only the totals Surefire/JUnit already computed and printed.
 */
object JUnitAggregateSupport {

    private val NAME = Regex("""<testsuite\b[^>]*\bname="([^"]*)"""")
    private val TESTS = Regex("""<testsuite\b[^>]*\btests="(\d+)"""")
    private val FAILURES = Regex("""<testsuite\b[^>]*\bfailures="(\d+)"""")
    private val ERRORS = Regex("""<testsuite\b[^>]*\berrors="(\d+)"""")
    private val SKIPPED = Regex("""<testsuite\b[^>]*\bskipped="(\d+)"""")
    private val TIME = Regex("""<testsuite\b[^>]*\btime="([\d.]+)"""")

    /** Every `TEST-*.xml` report directly inside [dir] (non-recursive — that is JUnit's own layout). */
    fun readDir(dir: File, module: String): List<TestSuiteResult> {
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("TEST-") && f.extension == "xml" }
            ?: return emptyList()
        return files.mapNotNull { parse(it, module) }
    }

    /** [module]'s ordinary `test` task results, at Gradle's default JUnit XML location. */
    fun readModuleTestResults(moduleDir: File, module: String, taskName: String = "test"): List<TestSuiteResult> =
        readDir(File(moduleDir, "build/test-results/$taskName"), module)

    private fun parse(file: File, module: String): TestSuiteResult? {
        val text = file.readText()
        val name = NAME.find(text)?.groupValues?.get(1) ?: return null
        return TestSuiteResult(
            className = name,
            module = module,
            tests = TESTS.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            failures = FAILURES.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            errors = ERRORS.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            skipped = SKIPPED.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            timeSeconds = TIME.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0,
        )
    }

    /** `tests - failures - errors - skipped`, floored at zero for a malformed/partial report. */
    fun passed(suite: TestSuiteResult): Int =
        (suite.tests - suite.failures - suite.errors - suite.skipped).coerceAtLeast(0)

    /** Sums a slice of suites into the `tests_passed` / `tests_failed` / `tests_skipped` metric shape every test-shaped category row uses. */
    fun summarize(suites: List<TestSuiteResult>): Map<String, Any> = mapOf(
        "tests_passed" to suites.sumOf { passed(it) },
        "tests_failed" to suites.sumOf { it.failures + it.errors },
        "tests_skipped" to suites.sumOf { it.skipped },
        "test_classes" to suites.size,
    )

    /** True when every suite in [suites] reported zero failures and zero errors. */
    fun allGreen(suites: List<TestSuiteResult>): Boolean = suites.all { it.failures == 0 && it.errors == 0 }
}
