/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import java.io.Serializable

/**
 * One module's DECLARED compile-time dependencies, as `dependencyReport` renders them: project
 * dependencies by module name, external ones as `group:name:version`. `null` lists mean the module
 * has no `compileClasspath` configuration at all (a container project such as
 * `narrativetrace-examples`), which the report says explicitly rather than reading as
 * "no dependencies".
 *
 * [Serializable] and a data class on purpose: the list of these IS the task's declared input
 * (`inputs.property`), which Gradle fingerprints by serialising the value and compares between
 * runs by equality — so a changed declaration, and only a changed declaration, reruns the report.
 */
data class ModuleDependencies(
    val module: String,
    val projectDependencies: List<String>?,
    val externalDependencies: List<String>?,
) : Serializable

/**
 * INTENT: renders `build/reports/dependency-graph/module-dependencies.txt` from the declared
 * module graph, so the root task is only "collect declarations → render → write" with the
 * declarations as its fingerprinted input. Before 2026-09-14 the task declared an output and no
 * inputs, which Gradle treats as up-to-date whenever the output file is unchanged — a dependency
 * bump left the report stale (build-automation assessment, Priority 1).
 */
object DependencyReportSupport {

    /**
     * [project]'s declared `compileClasspath` dependencies, as the report lists them — read from
     * the declarations, never resolved, so collecting them costs no network and no resolution.
     * A project without a `compileClasspath` configuration yields `null` lists.
     */
    fun collect(project: Project): ModuleDependencies {
        val compileClasspath = project.configurations.findByName("compileClasspath")
            ?: return ModuleDependencies(project.name, null, null)
        val declared = compileClasspath.allDependencies
        return ModuleDependencies(
            module = project.name,
            // `dependencyProject` is deprecated for removal in Gradle 9; `path` is the supported
            // accessor and the module name is its last segment.
            projectDependencies = declared.filterIsInstance<ProjectDependency>()
                .map { it.path.substringAfterLast(':') }
                .sorted(),
            externalDependencies = declared
                .filter { it !is ProjectDependency && it.group != null }
                .map { "${it.group}:${it.name}:${it.version ?: ""}" }
                .sorted(),
        )
    }

    /** The report text; [generatedAt] is the timestamp line, passed in so rendering stays pure. */
    fun render(modules: List<ModuleDependencies>, generatedAt: String): String {
        val sb = StringBuilder()
        sb.appendLine("# Module Dependency Report")
        sb.appendLine("# Generated: $generatedAt")
        sb.appendLine()
        modules.sortedBy { it.module }.forEach { module ->
            sb.appendLine("## ${module.module}")
            val projects = module.projectDependencies
            val externals = module.externalDependencies
            when {
                projects == null || externals == null -> sb.appendLine("  (no compileClasspath)")
                projects.isEmpty() && externals.isEmpty() -> sb.appendLine("  (no dependencies)")
                else -> {
                    projects.sorted().forEach { sb.appendLine("  -> $it") }
                    externals.sorted().forEach { sb.appendLine("  -> $it") }
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }
}
