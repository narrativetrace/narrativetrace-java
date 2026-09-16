/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask

/**
 * INTENT: every archive this repository builds (jar, sources jar, javadoc jar, zip distributions)
 * must be byte-for-byte reproducible from the same source — a clean rebuild on a different
 * machine, at a different time, must produce the identical archive a release was verified from
 * (build-automation assessment 2026-09-14, Priority 1/4: "Explicitly configure reproducible
 * archive order and timestamps"). Gradle's [AbstractArchiveTask] defaults embed the wall-clock
 * entry timestamp and the filesystem's own directory-listing order into every archive it writes —
 * neither is a property of the source, so two otherwise-identical builds produce different bytes.
 *
 * Applied once per project from the root `subprojects` block, so a new module inherits
 * reproducible archives with zero configuration of its own — the same "every module gets the
 * right behaviour by default" shape [ReproducibleArchives] shares with [DependencyReportSupport]
 * and the typed report tasks.
 *
 * Behavioural coverage: [ReproducibleArchivesTest] (buildSrc) proves both flags land on a
 * registered archive task; `narrativetrace-build-tests`' `ReproducibleJarTest` builds the same jar
 * twice from clean and compares the bytes.
 */
object ReproducibleArchives {

    fun apply(project: Project) {
        project.tasks.withType(AbstractArchiveTask::class.java).configureEach {
            // The entry timestamp: without this, every archive differs from its predecessor by
            // nothing but the second it happened to be built in.
            isPreserveFileTimestamps = false
            // Entry order: without this, two builds can list the same source directory's files
            // in a different order (filesystem-dependent), producing byte-different archives that
            // are otherwise identical.
            isReproducibleFileOrder = true
        }
    }
}
