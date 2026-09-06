/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("narrativetrace-publish")
    `java-test-fixtures`
}

extra["publishName"] = "NarrativeTrace API"
extra["publishDescription"] =
    "The annotation, event-model and SPI surface third parties compile against."

// Exclude test fixtures from Maven Central publication
components.named<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}

// Zero dependencies, by contract: this is the surface other people's code compiles
// against, and every dependency it takes becomes a dependency they cannot refuse.
// The ArchUnit rules in src/test enforce the same rule at the package level.
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("net.jqwik:jqwik:1.9.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}
