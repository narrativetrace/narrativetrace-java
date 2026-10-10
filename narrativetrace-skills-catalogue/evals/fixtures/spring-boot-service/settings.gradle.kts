/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Resolution order, cheapest first — the same two routes clarity-consumer uses, for the same
// reason: a Tier B trial scaffolds this fixture into a temp directory outside every repository
// tree, so nothing relative to it can find the checkout.
//
//   1. `narrativetraceTestMavenRepo` — a local Maven file repository already carrying the plugin
//      (marker + implementation jar) and every library a consumer can resolve. The eval runner
//      passes it as ORG_GRADLE_PROJECT_narrativetraceTestMavenRepo, which is how a project
//      property reaches a `./gradlew` line the AGENT types rather than the harness.
//   2. NARRATIVETRACE_TEST_REPO — a Gradle composite build of a checkout, for a developer running
//      this fixture by hand from source.
//
// Route 1 is what makes this case measure THIS checkout: the agent types the published plugin
// coordinate, and it resolves here to the doctor that carries config.spring-enabled — the published
// release predates it. There is deliberately no third, auto-detected route: a path guessed
// relative to a temp directory is a path that resolves to somebody else's tree.
//
// `pluginManagement` is evaluated before the rest of this script, so it cannot read a val declared
// below it — both routes are therefore read twice, once there and once here. Diverging the two
// readings is how a build resolves its plugin from one place and its libraries from another.
pluginManagement {
    val testMavenRepo =
        providers.gradleProperty("narrativetraceTestMavenRepo").orNull?.takeIf { it.isNotBlank() }
    val checkout = System.getenv("NARRATIVETRACE_TEST_REPO")?.takeIf { it.isNotBlank() }
    if (testMavenRepo != null) {
        repositories {
            maven { url = uri(testMavenRepo) }
            mavenCentral()
            gradlePluginPortal()
        }
    } else {
        if (checkout != null) {
            includeBuild(checkout)
        }
        repositories {
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

val testMavenRepo =
    providers.gradleProperty("narrativetraceTestMavenRepo").orNull?.takeIf { it.isNotBlank() }
val checkout = System.getenv("NARRATIVETRACE_TEST_REPO")?.takeIf { it.isNotBlank() }

if (testMavenRepo != null) {
    gradle.beforeProject {
        repositories.maven { url = uri(testMavenRepo) }
    }
} else if (checkout != null) {
    includeBuild(checkout)
}

rootProject.name = "spring-boot-service"
