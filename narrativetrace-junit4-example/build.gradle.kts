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
}
