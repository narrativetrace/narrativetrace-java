/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    id("narrativetrace-publish")
}

extra["publishName"] = "NarrativeTrace JUnit 5"
extra["publishDescription"] = "JUnit 5 extension for automatic trace output"

tasks.test {
    exclude("**/FailingTestFixture.class")
}

dependencies {
    api(project(":narrativetrace-core"))
    api(project(":narrativetrace-proxy"))
    api(project(":narrativetrace-diagrams"))
    api(project(":narrativetrace-clarity"))
    api(project(":narrativetrace-glossary"))
    api("org.junit.jupiter:junit-jupiter-api:5.11.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
