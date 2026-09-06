/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Minimal example proving the NarrativeTrace runtime jars work from a plain Maven build, with no
 * Gradle involved anywhere in the loop.
 *
 * <p>This package keeps the domain intentionally tiny so the focus stays on {@code pom.xml} wiring
 * (Surefire's output-directory system properties, the {@code -parameters} compiler flag, the JUnit
 * 5 extension) rather than on business logic — see {@code ../documentation/maven-guide.md} for the
 * walkthrough this module exists to back.
 */
package ai.narrativetrace.examples.maven;
