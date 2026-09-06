/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    id("narrativetrace-publish")
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

extra["publishName"] = "NarrativeTrace Micronaut"
extra["publishDescription"] = "Micronaut Framework integration with BeanCreatedEventListener"

kotlin {
    compilerOptions {
        javaParameters.set(true)
    }
}

dependencies {
    implementation(project(":narrativetrace-core"))
    implementation(project(":narrativetrace-proxy"))
    implementation("io.micronaut:micronaut-context:4.10.26")
    compileOnly(project(":narrativetrace-slf4j"))
    implementation(kotlin("stdlib"))
    ksp("io.micronaut:micronaut-inject-kotlin:4.10.26")

    kspTest("io.micronaut:micronaut-inject-kotlin:4.10.26")
    testImplementation("io.micronaut.test:micronaut-test-junit5:4.8.1")
    testImplementation("io.micronaut:micronaut-inject:4.10.26")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation(project(":narrativetrace-slf4j"))
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("ch.qos.logback:logback-classic:1.5.38")
    testImplementation(testFixtures(project(":narrativetrace-core")))
}
