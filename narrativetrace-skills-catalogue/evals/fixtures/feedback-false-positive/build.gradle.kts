/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    java
    id("ai.narrativetrace") version "0.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.narrativetrace:narrativetrace-core:0.3.0")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.3.0")

    testImplementation("ai.narrativetrace:narrativetrace-junit5:0.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

// Without this, parameter names degrade to arg0/arg1 in every rendered trace — and the doctor's
// trap.llms-before-you-start says so. A fixture whose premise is "this project is configured
// correctly" has to actually be.
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}
