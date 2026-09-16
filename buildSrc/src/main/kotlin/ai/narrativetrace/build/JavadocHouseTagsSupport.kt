/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

/**
 * INTENT: this codebase's JavaDoc house tags (`@llmNote`, `@sideEffects`, `@pattern`) are real
 * block tags used throughout main sources, but the standard doclet only accepts tags it has been
 * told about — an unlisted tag is `error: unknown tag`, not a warning, and the standard `javadoc`
 * task fails the build on it. The 0.2.2 release discovered this the only place it could still
 * happen: `publishAggregationToCentralPortal` builds every published module's `javadocJar`, and
 * `check` never built javadoc, so nothing before that step ever tried.
 *
 * Registers the three tags with the doclet so they render as labelled sections instead of
 * failing — never `-Xdoclint:none`, which would hide them from the generated HTML along with
 * every other diagnostic.
 *
 * Applied by `narrativetrace-publish` (every published module) — see [JavadocHouseTagsSupportTest]
 * for the applied-convention proof.
 */
object JavadocHouseTagsSupport {

    /**
     * `-tag name:locations:header` triples, `a` (all locations) matching how these tags are
     * actually written: on classes, constructors, methods and fields alike.
     */
    private val HOUSE_TAGS =
        listOf(
            "llmNote:a:LLM note:",
            "sideEffects:a:Side effects:",
            "pattern:a:Pattern:",
        )

    fun apply(project: Project) {
        project.tasks.withType(Javadoc::class.java).configureEach {
            (options as StandardJavadocDocletOptions).tags(HOUSE_TAGS)
        }
    }
}
