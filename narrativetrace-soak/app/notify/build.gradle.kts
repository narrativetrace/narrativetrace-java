/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// The soak harness's downstream notify process (see ../../README.md) — replaces the ecommerce
// example's JsonPlaceholderNotificationService third-party call so the soak never reaches any
// external host. See shop/build.gradle.kts for why this stays off narrativetrace-spring-web.
// See ../shop/build.gradle.kts for why the dependency-management plugin is not applied here.
plugins {
    id("org.springframework.boot") version "3.5.6"
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))

    implementation(project(":narrativetrace-api"))
    implementation(project(":narrativetrace-core"))
    implementation(project(":narrativetrace-agent"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<Jar>("jar") {
    enabled = false
}
