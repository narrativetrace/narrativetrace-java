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

extra["publishName"] = "NarrativeTrace SLF4J"
extra["publishDescription"] = "SLF4J bridge with MDC propagation"

dependencies {
    implementation(project(":narrativetrace-core"))
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("net.jqwik:jqwik:1.9.2")
    testImplementation("ch.qos.logback:logback-classic:1.5.38")
    testImplementation(testFixtures(project(":narrativetrace-core")))
    testImplementation("com.github.noconnor:junitperf-junit5:1.37.0")
}
