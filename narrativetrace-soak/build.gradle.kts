/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// Parent aggregator for the soak harness — no sources of its own. `app/shop` and `app/notify`
// (Gradle projects :narrativetrace-soak:shop and :narrativetrace-soak:notify, see
// settings.gradle.kts) are the two Spring Boot processes; `k6/`, `compose.yaml` and
// `run-soak.sh` are plain files driven by the shell, not by Gradle. See README.md.
