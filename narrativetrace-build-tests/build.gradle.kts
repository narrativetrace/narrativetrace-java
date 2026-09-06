/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.withType<Test> {
    systemProperty("projectDir", rootProject.projectDir.absolutePath)
    // JarLicensePackagingTest reads real archives rather than trusting the build script that
    // produced them, so the archives have to exist: one `open` module and one `free` one, the two
    // branches `narrativetrace-license-packaging` chooses between.
    dependsOn(":narrativetrace-api:jar", ":narrativetrace-core:jar")
    systemProperty("narrativetrace.buildVersion", project.version.toString())
}
