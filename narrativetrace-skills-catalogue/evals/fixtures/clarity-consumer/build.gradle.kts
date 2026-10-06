/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    java
    id("ai.narrativetrace") version "0.2.5"
}

repositories {
    mavenCentral()
}

// Keep the fixture's names varied enough for a non-empty issue set and report elements.
narrativeTrace {
    clarity {
        minScore.set(0.0)
        maxHighIssues.set(Int.MAX_VALUE)
    }
}
