/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("ai.narrativetrace.examples.library.LibraryExample")
    applicationDefaultJvmArgs = listOf("-Dlogback.configurationFile=logback-library.xml")
}

kotlin {
    compilerOptions {
        javaParameters.set(true)
    }
}

dependencies {
    implementation(project(":narrativetrace-core"))
    implementation(project(":narrativetrace-examples:common"))
    implementation(project(":narrativetrace-proxy"))
    implementation(project(":narrativetrace-diagrams"))
    implementation(project(":narrativetrace-slf4j"))
    implementation(kotlin("stdlib"))
}

