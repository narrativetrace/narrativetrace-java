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
package ai.narrativetrace.cli;

/**
 * The {@code java -jar}/{@code bin/narrativetrace} entry point. Kept to a single delegating line —
 * {@link Cli#run} carries every branch worth a unit test, so this class is excluded from the
 * module's coverage verification (see {@code build.gradle.kts}), the same way the examples modules
 * exclude their own thin launcher classes.
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    System.exit(Cli.run(args, Cli.Deps.standard()));
  }
}
