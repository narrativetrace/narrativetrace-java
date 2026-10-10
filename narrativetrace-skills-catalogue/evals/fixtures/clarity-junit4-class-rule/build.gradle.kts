/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
plugins {
    java
    id("ai.narrativetrace") version "0.3.0"
}

repositories {
    mavenCentral()
}

narrativeTrace {
    testFramework.set("junit4")
    clarity {
        minScore.set(0.50)
    }
}
