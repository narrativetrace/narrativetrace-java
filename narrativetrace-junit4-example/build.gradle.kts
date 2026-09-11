/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
dependencies {
    implementation(project(":narrativetrace-core"))
    implementation(project(":narrativetrace-proxy"))
    implementation(project(":narrativetrace-junit4"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.11.4")

    // PipelineBootstrap attaches Slf4jTraceEventListener reflectively once this is on the
    // classpath — no wrapper class, no wiring (documentation/configuration-guide.md §7).
    // src/test/resources/logback-test.xml decides where the narration actually goes.
    testRuntimeOnly(project(":narrativetrace-slf4j"))
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.38")
}
