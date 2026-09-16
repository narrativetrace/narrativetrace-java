/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * INTENT: the per-module `jdepend` task as a typed task whose up-to-date decision is driven by
 * the class files it analyses.
 *
 * Before 2026-09-14 the task was an ad-hoc `tasks.register("jdepend")` with `outputs.file` and a
 * `dependsOn(classes)`: the dependency ordered execution but fingerprinted nothing, so once the
 * JSON existed Gradle reused it after any change to the analysed classes — an incremental build
 * could gate `jdependCheck` on an obsolete architecture report (build-automation assessment,
 * Priority 1). [classesDir] is an [InputDirectory] with RELATIVE path sensitivity: a changed,
 * added or removed class reruns the analysis; a moved checkout does not.
 *
 * Behavioural coverage: `TaskInputInvalidationTest` (narrativetrace-build-tests) drives this task
 * in a throwaway fixture build — first run executes, unchanged rerun is UP-TO-DATE, a new class
 * reruns it, a resource-only change does not.
 */
abstract class JDependReportTask : DefaultTask() {

    /** The module's compiled main classes — the analysed input. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classesDir: DirectoryProperty

    /** The module name written into the report (`"module"` in the JSON). */
    @get:Input
    abstract val moduleName: Property<String>

    /** The JSON report `jdependCheck` and `jdependReport` read back. */
    @get:OutputFile
    abstract val jsonFile: RegularFileProperty

    @TaskAction
    fun analyze() {
        val input = JDependInput(moduleName.get(), classesDir.get().asFile)
        val result = JDependReportSupport.analyze(listOf(input)).first()
        JDependReportSupport.writeJson(result, jsonFile.get().asFile)
    }
}
