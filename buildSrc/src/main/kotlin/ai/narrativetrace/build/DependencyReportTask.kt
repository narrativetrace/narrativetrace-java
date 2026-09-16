/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.time.Instant

/**
 * INTENT: the root `dependencyReport` as a typed task whose up-to-date decision is driven by the
 * DECLARED module graph, and by nothing else.
 *
 * Before 2026-09-14 the task was an ad-hoc `tasks.register` with `outputs.file` and no inputs:
 * Gradle treats such a task as up-to-date whenever its output is unchanged, so a dependency bump
 * left `module-dependencies.txt` stale (build-automation assessment, Priority 1). An ad-hoc task
 * cannot be given this input cleanly either — its `doLast` lambda's implementation hash moves
 * with the build-script classpath (any init script reruns it), so "rerun on a declaration change
 * and only then" was not something a test could pin. Here the action is this class and the input
 * is [modules], a serialisable value Gradle fingerprints and compares by equality.
 *
 * Behavioural coverage: `TaskInputInvalidationTest` (narrativetrace-build-tests) drives this task
 * in a throwaway multi-project fixture — first run executes, unchanged rerun is UP-TO-DATE, a
 * changed declaration reruns it, a build-script edit that changes no declaration does not.
 */
abstract class DependencyReportTask : DefaultTask() {

    /** Every module's declared compile-time dependencies — see [DependencyReportSupport.collect]. */
    @get:Input
    abstract val modules: ListProperty<ModuleDependencies>

    /** `build/reports/dependency-graph/module-dependencies.txt`, written for LLM consumption. */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun write() {
        val text = DependencyReportSupport.render(modules.get(), generatedAt = Instant.now().toString())
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(text)
        println(text)
    }
}
