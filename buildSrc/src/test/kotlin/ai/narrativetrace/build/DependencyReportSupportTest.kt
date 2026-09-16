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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class DependencyReportSupportTest {

    private val core = ModuleDependencies(
        module = "narrativetrace-core",
        projectDependencies = listOf("narrativetrace-api"),
        externalDependencies = listOf("org.slf4j:slf4j-api:2.0.16"),
    )
    private val api = ModuleDependencies("narrativetrace-api", emptyList(), emptyList())

    @Test
    fun `renders the header, one section per module and one arrow per dependency`() {
        val text = DependencyReportSupport.render(listOf(core), generatedAt = "2026-09-14T00:00:00Z")

        assertEquals(
            """
            # Module Dependency Report
            # Generated: 2026-09-14T00:00:00Z

            ## narrativetrace-core
              -> narrativetrace-api
              -> org.slf4j:slf4j-api:2.0.16

            """.trimIndent() + "\n", // every section ends with a blank separator line
            text,
        )
    }

    @Test
    fun `a module without dependencies says so instead of printing an empty section`() {
        val text = DependencyReportSupport.render(listOf(api), generatedAt = "t")

        assertTrue(text.contains("## narrativetrace-api\n  (no dependencies)\n"), text)
    }

    @Test
    fun `a module with no compileClasspath is reported as such, not as dependency-free`() {
        val text = DependencyReportSupport.render(
            listOf(ModuleDependencies("narrativetrace-build-tests", null, null)),
            generatedAt = "t",
        )

        assertTrue(text.contains("## narrativetrace-build-tests\n  (no compileClasspath)\n"), text)
    }

    @Test
    fun `project dependencies are listed before external ones, each group sorted`() {
        val text = DependencyReportSupport.render(
            listOf(
                ModuleDependencies(
                    "m",
                    projectDependencies = listOf("zeta", "alpha"),
                    externalDependencies = listOf("org.b:b:1", "org.a:a:1"),
                )
            ),
            generatedAt = "t",
        )

        assertEquals(
            listOf("  -> alpha", "  -> zeta", "  -> org.a:a:1", "  -> org.b:b:1"),
            text.lines().filter { it.startsWith("  -> ") },
        )
    }

    @Test
    fun `modules are rendered in name order regardless of the order given`() {
        val text = DependencyReportSupport.render(listOf(core, api), generatedAt = "t")

        assertTrue(text.indexOf("## narrativetrace-api") < text.indexOf("## narrativetrace-core"), text)
    }

    // ---------------------------------------------------------------- collecting declarations

    @Test
    fun `collect reads declared compile-time dependencies without resolving them`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val lib = ProjectBuilder.builder().withName("lib").withParent(root).build()
        val app = ProjectBuilder.builder().withName("app").withParent(root).build()
        lib.plugins.apply("java-library")
        app.plugins.apply("java-library")
        app.dependencies.add("implementation", app.dependencies.project(mapOf("path" to ":lib")))
        app.dependencies.add("implementation", "org.example:zeta:2.0")
        app.dependencies.add("api", "org.example:alpha:1.0")
        app.dependencies.add("testImplementation", "org.example:test-only:1.0")

        val collected = DependencyReportSupport.collect(app)

        assertEquals(
            ModuleDependencies("app", listOf("lib"), listOf("org.example:alpha:1.0", "org.example:zeta:2.0")),
            collected,
        )
    }

    @Test
    fun `collect marks a project without compileClasspath as such`() {
        val bare = ProjectBuilder.builder().withName("container").build()

        assertEquals(ModuleDependencies("container", null, null), DependencyReportSupport.collect(bare))
    }

    // The declared graph IS the task input (`DependencyReportTask.modules`): Gradle fingerprints
    // it by serialising the value and compares runs by equality, so both must hold for the report
    // to rerun exactly when a declaration changes.

    @Test
    fun `the module graph is serialisable so Gradle can fingerprint it as a task input`() {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(listOf(core, api)) }

        assertTrue(core is Serializable)
        assertTrue(bytes.size() > 0)
    }

    @Test
    fun `a changed declaration is a different input value`() {
        val bumped = core.copy(externalDependencies = listOf("org.slf4j:slf4j-api:2.0.17"))

        assertNotEquals(core, bumped)
        assertEquals(core, core.copy())
    }
}
