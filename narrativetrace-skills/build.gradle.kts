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
}

extra["publishName"] = "NarrativeTrace Skills"
extra["publishDescription"] =
    "The skills CARRIER: narrativetrace-doctor, add-narrative-tracing and add-narrativetrace-clarity " +
        "as resources under META-INF/narrativetrace/skills/ — one SKILL.md per flavour (agents, claude) " +
        "plus catalogue.json. No classes: what an installer reads out of this jar is text, and the " +
        "typed catalogue that renders it lives in the unpublished narrativetrace-skills-catalogue."

// No sources and no dependencies, by construction. The pages under src/main/resources are committed
// BUILD OUTPUT (RenderMain's carrier target, pinned by RenderDriftTest) — never hand-edited; run
// `./gradlew :narrativetrace-skills-catalogue:renderSkills` and commit what it writes.
