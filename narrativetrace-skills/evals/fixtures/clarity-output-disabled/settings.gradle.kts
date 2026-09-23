/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// Resolution order, cheapest/fastest first:
//   1. -PnarrativetraceTestMavenRepo=<path> — a local Maven file repository already carrying the
//      plugin (marker + implementation jar) and every library it can add to a consumer. This is
//      what the Tier A2 replay (SkillReplayer.clarityScan/clarityCheck) passes: the outer build's
//      publishSkillsTestRepo task fills it, so this fixture only ever compiles its own three
//      classes and runs the scan/check — no includeBuild of the whole 29-module repo.
//   2. NARRATIVETRACE_TEST_REPO=<checkout> — a Gradle composite build of that checkout, still
//      used by the Tier B eval trials (see evals/README.md) and by a developer running this
//      fixture by hand who wants source, not published coordinates.
//   3. Auto-detected checkout four directories up, the same composite path as (2).
val testMavenRepo =
    providers.gradleProperty("narrativetraceTestMavenRepo").orNull?.takeIf { it.isNotBlank() }

val localRepoCandidate = file("../../../../").canonicalPath
val narrativeTraceRepo = System.getenv("NARRATIVETRACE_TEST_REPO")
    ?: localRepoCandidate.takeIf { file("$it/settings.gradle.kts").isFile }

pluginManagement {
    val configuredTestMavenRepo =
        providers.gradleProperty("narrativetraceTestMavenRepo").orNull?.takeIf { it.isNotBlank() }
    if (configuredTestMavenRepo != null) {
        repositories {
            maven { url = uri(configuredTestMavenRepo) }
        }
    }
    val configuredRepo = System.getenv("NARRATIVETRACE_TEST_REPO")
    val localRepo = file("../../../../").canonicalPath
    val repo = configuredRepo ?: localRepo.takeIf { file("$it/settings.gradle.kts").isFile }
    if (configuredTestMavenRepo == null && !repo.isNullOrBlank()) {
        includeBuild(repo)
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

if (testMavenRepo != null) {
    // Project dependency resolution (narrativetrace-core, -clarity, -glossary, ...) — a separate
    // repository listing from pluginManagement's above, which only resolves the plugin itself.
    // Added ahead of build.gradle.kts's own `repositories { mavenCentral() }`, additively: that
    // block still runs, it just never needs to — every NarrativeTrace coordinate resolves here.
    gradle.beforeProject {
        repositories.maven { url = uri(testMavenRepo) }
    }
} else if (!narrativeTraceRepo.isNullOrBlank()) {
    includeBuild(narrativeTraceRepo)
}

rootProject.name = "clarity-output-disabled"
