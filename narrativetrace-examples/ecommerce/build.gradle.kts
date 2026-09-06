/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
dependencies {
    implementation(project(":narrativetrace-core"))
    implementation(project(":narrativetrace-examples:common"))
    implementation(project(":narrativetrace-proxy"))
    implementation(project(":narrativetrace-diagrams"))
    implementation(project(":narrativetrace-slf4j"))
    implementation(project(":narrativetrace-spring"))
    implementation(project(":narrativetrace-micrometer"))
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.springframework:spring-context:6.2.19")
    implementation("io.micrometer:context-propagation:1.1.2")
}

plugins {
    application
}

application {
    mainClass.set("ai.narrativetrace.examples.ecommerce.ECommerceExample")
    applicationDefaultJvmArgs = listOf("-Dlogback.configurationFile=logback-ecommerce.xml")
}
