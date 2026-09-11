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
 * INTENT: Answers "which test classes are property tests, and which of those are Tier A fuzzing
 * over the shared hostile corpus" straight from the source tree, so `verifyAll`'s `property` and
 * `fuzz-tier-a` rows can slice one `./gradlew test` run's JUnit XML into the right buckets without
 * a second test invocation and without a hand-maintained class list that drifts from the source.
 *
 * A class counts as a **property test** when a source file under `src/test/java` uses jqwik's
 * `@Property` annotation; its simple (unqualified) name is what JUnit XML's `<testsuite name=...>`
 * carries for a top-level class, which is enough to match — this repository has no two test classes
 * sharing a simple name.
 *
 * A property-test class counts as **Tier A** (jqwik properties over the shared hostile corpus) when
 * the same file also references `HostileCorpus`. Jazzer's `@FuzzTest` classes are Tier A too when
 * they run in regression mode (replaying the committed corpus in milliseconds, inside the ordinary
 * `test` task) — the corpus replay runs per commit while the budgeted sweep is scheduled — so
 * `fuzzTestClasses` is reported separately and
 * a caller ORs the two together for the fuzz-tier-a row.
 */
object PropertyTestClassifier {

    // jqwik's @Property is most often bare (no arguments), so the annotation name itself is the
    // match — bounded on the right so `@PropertySource`-shaped names can never match.
    private val PROPERTY_ANNOTATION = Regex("""@Property\b(?!\w)""")
    private val FUZZ_TEST_ANNOTATION = Regex("""@FuzzTest\b(?!\w)""")
    private val HOSTILE_CORPUS_REFERENCE = Regex("""\bHostileCorpus\b""")

    data class Classification(
        val propertyTestClasses: Set<String>,
        val hostileCorpusPropertyClasses: Set<String>,
        val fuzzTestClasses: Set<String>,
    ) {
        /** Every class whose `test` results belong on the fuzz-tier-a row. */
        val fuzzTierAClasses: Set<String> get() = hostileCorpusPropertyClasses + fuzzTestClasses
    }

    /** Scans every `src/test/java` tree under [rootDir], one module deep, source of truth today. */
    fun classify(rootDir: File): Classification {
        val testSources = rootDir.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" }
            .filter { it.isFile && it.extension == "java" && it.path.contains("${File.separator}src${File.separator}test${File.separator}") }

        val property = mutableSetOf<String>()
        val hostileCorpus = mutableSetOf<String>()
        val fuzzTest = mutableSetOf<String>()

        testSources.forEach { file ->
            val text = file.readText()
            val simpleName = file.nameWithoutExtension
            if (PROPERTY_ANNOTATION.containsMatchIn(text)) {
                property += simpleName
                if (HOSTILE_CORPUS_REFERENCE.containsMatchIn(text)) hostileCorpus += simpleName
            }
            if (FUZZ_TEST_ANNOTATION.containsMatchIn(text)) fuzzTest += simpleName
        }
        return Classification(property, hostileCorpus, fuzzTest)
    }

    /** The suites among [suites] whose class's simple name is in [simpleNames]. */
    fun matching(suites: List<TestSuiteResult>, simpleNames: Set<String>): List<TestSuiteResult> =
        suites.filter { simpleNames.contains(it.className.substringAfterLast('.')) }
}
