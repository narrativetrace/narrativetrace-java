/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JavadocHouseTagsSupportTest {

    @Test
    fun `applying the convention configures all three house tags on a javadoc task`() {
        val project = ProjectBuilder.builder().build()
        val javadoc = project.tasks.register("testJavadoc", Javadoc::class.java).get()

        JavadocHouseTagsSupport.apply(project)

        val tags = (javadoc.options as StandardJavadocDocletOptions).tags.orEmpty()
        assertTrue(tags.any { it.startsWith("llmNote:") }, "llmNote must be a known doclet tag")
        assertTrue(tags.any { it.startsWith("sideEffects:") }, "sideEffects must be a known doclet tag")
        assertTrue(tags.any { it.startsWith("pattern:") }, "pattern must be a known doclet tag")
    }

    @Test
    fun `a javadoc task registered AFTER the convention is applied still gets all three tags`() {
        val project = ProjectBuilder.builder().build()

        JavadocHouseTagsSupport.apply(project)
        val javadoc = project.tasks.register("laterJavadoc", Javadoc::class.java).get()

        val tags = (javadoc.options as StandardJavadocDocletOptions).tags.orEmpty()
        assertTrue(tags.any { it.startsWith("llmNote:") })
        assertTrue(tags.any { it.startsWith("sideEffects:") })
        assertTrue(tags.any { it.startsWith("pattern:") })
    }

    @Test
    fun `a project with no javadoc tasks is untouched`() {
        val project = ProjectBuilder.builder().build()

        JavadocHouseTagsSupport.apply(project)

        assertTrue(project.tasks.withType(Javadoc::class.java).isEmpty())
    }
}
