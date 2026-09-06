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

extra["publishName"] = "NarrativeTrace OpenTelemetry"
extra["publishDescription"] = "OpenTelemetry span export for NarrativeTrace trees"

dependencies {
    api(project(":narrativetrace-core"))
    compileOnly("io.opentelemetry:opentelemetry-api:1.62.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("io.opentelemetry:opentelemetry-api:1.62.0")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.62.0")
}
