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
package ai.narrativetrace.cli.doctor;

/**
 * The version facts the doctor checks against — this CLI build's own declared minimums, the Java
 * equivalent of the TypeScript reference reading an installed package's {@code engines} field. Java
 * has no per-dependency resolved-manifest range to read without a live Gradle dependency graph, so
 * these constants travel with the doctor's own release instead: a version bump to {@code
 * narrativetrace-junit5}'s supported range is the same commit that updates this file (the same
 * discipline {@code llms.txt}'s "Before you start" blocks already apply per-runtime).
 */
public final class Requirements {

  /** The minimum JDK feature version NarrativeTrace's runtime and JUnit 5 extension require. */
  public static final int MIN_JAVA_FEATURE_VERSION = 17;

  /** Inclusive lower bound of the JUnit Jupiter versions {@code narrativetrace-junit5} supports. */
  public static final String JUNIT5_MIN = "5.9.0";

  /** Exclusive upper bound — the next JUnit major line, not yet supported. */
  public static final String JUNIT5_MAX_EXCLUSIVE = "6.0.0";

  private Requirements() {}
}
