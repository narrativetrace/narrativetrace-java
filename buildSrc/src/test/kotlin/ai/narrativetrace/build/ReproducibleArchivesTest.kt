/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.gradle.api.tasks.bundling.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReproducibleArchivesTest {

    @Test
    fun `applying the convention turns off timestamps and turns on ordered entries`() {
        val project = ProjectBuilder.builder().build()
        val jar = project.tasks.register("testJar", Jar::class.java).get()

        ReproducibleArchives.apply(project)

        assertFalse(jar.isPreserveFileTimestamps, "timestamps must not leak into the archive")
        assertTrue(jar.isReproducibleFileOrder, "entry order must not depend on the filesystem")
    }

    @Test
    fun `a jar registered AFTER the convention is applied still gets both flags`() {
        val project = ProjectBuilder.builder().build()

        ReproducibleArchives.apply(project)
        val jar = project.tasks.register("laterJar", Jar::class.java).get()

        assertFalse(jar.isPreserveFileTimestamps)
        assertTrue(jar.isReproducibleFileOrder)
    }

    @Test
    fun `a project with no archive tasks is untouched`() {
        val project = ProjectBuilder.builder().build()

        ReproducibleArchives.apply(project)

        assertTrue(project.tasks.withType(Jar::class.java).isEmpty())
    }
}
