/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
dependencies {
    implementation(project(":narrativetrace-core"))
    implementation("net.sourceforge.plantuml:plantuml-lgpl:1.2024.8")
    // DemoTraces renders translated scenario files in-process (`./demo.sh --lang es`).
    implementation(project(":narrativetrace-glossary"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
