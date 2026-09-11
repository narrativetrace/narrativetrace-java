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

class PropertyTestClassifierTest {

    @TempDir
    lateinit var root: File

    private fun testFile(module: String, simpleName: String, body: String) {
        val dir = File(root, "$module/src/test/java/ai/narrativetrace/x")
        dir.mkdirs()
        File(dir, "$simpleName.java").writeText(body)
    }

    @Test
    fun `a class using @Property is a property test`() {
        testFile("core", "ValueRendererPropertyTest", "class ValueRendererPropertyTest { @Property void p() {} }")

        val result = PropertyTestClassifier.classify(root)

        assertTrue(result.propertyTestClasses.contains("ValueRendererPropertyTest"))
        assertTrue(result.hostileCorpusPropertyClasses.isEmpty())
    }

    @Test
    fun `a property test referencing HostileCorpus is Tier A`() {
        testFile(
            "security-tests", "InjectionContainmentPropertyTest",
            "class InjectionContainmentPropertyTest { @Property void p(HostileCorpus c) {} }"
        )

        val result = PropertyTestClassifier.classify(root)

        assertTrue(result.hostileCorpusPropertyClasses.contains("InjectionContainmentPropertyTest"))
        assertTrue(result.fuzzTierAClasses.contains("InjectionContainmentPropertyTest"))
    }

    @Test
    fun `a FuzzTest class is tracked separately and folds into fuzz-tier-a`() {
        testFile("security-tests", "OutputFormatFuzzTest", "class OutputFormatFuzzTest { @FuzzTest(maxDuration=\"5m\") void f(byte[] d) {} }")

        val result = PropertyTestClassifier.classify(root)

        assertTrue(result.fuzzTestClasses.contains("OutputFormatFuzzTest"))
        assertTrue(result.propertyTestClasses.isEmpty())
        assertTrue(result.fuzzTierAClasses.contains("OutputFormatFuzzTest"))
    }

    @Test
    fun `an ordinary test class with neither annotation is classified as nothing`() {
        testFile("core", "PlainTest", "class PlainTest { @Test void t() {} }")

        val result = PropertyTestClassifier.classify(root)

        assertTrue(result.propertyTestClasses.isEmpty())
        assertTrue(result.fuzzTestClasses.isEmpty())
        assertTrue(result.fuzzTierAClasses.isEmpty())
    }

    @Test
    fun `build directories are never scanned`() {
        val buildDir = File(root, "core/build/generated/src/test/java/ai/narrativetrace/x")
        buildDir.mkdirs()
        File(buildDir, "GeneratedPropertyTest.java").writeText("class GeneratedPropertyTest { @Property void p() {} }")

        val result = PropertyTestClassifier.classify(root)

        assertTrue(result.propertyTestClasses.isEmpty())
    }

    @Test
    fun `matching filters suites by the class's simple name, ignoring the package`() {
        val suites = listOf(
            TestSuiteResult("ai.narrativetrace.core.render.ValueRendererPropertyTest", "core", 3, 0, 0, 0, 1.0),
            TestSuiteResult("ai.narrativetrace.core.render.OtherTest", "core", 2, 0, 0, 0, 1.0),
        )

        val matched = PropertyTestClassifier.matching(suites, setOf("ValueRendererPropertyTest"))

        assertEquals(1, matched.size)
        assertEquals("ai.narrativetrace.core.render.ValueRendererPropertyTest", matched.single().className)
    }
}
