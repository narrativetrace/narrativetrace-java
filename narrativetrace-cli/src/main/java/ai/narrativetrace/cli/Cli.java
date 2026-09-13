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

import ai.narrativetrace.cli.doctor.DoctorChecks;
import ai.narrativetrace.cli.doctor.DoctorRender;
import ai.narrativetrace.cli.doctor.DoctorReport;
import ai.narrativetrace.cli.doctor.DoctorSnapshot;
import ai.narrativetrace.cli.doctor.SnapshotBuilder;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * {@code narrativetrace} — one CLI over NarrativeTrace's open artifact formats. Today it has one
 * command, {@code doctor}; every argument-parsing decision is testable in isolation via {@link
 * Deps}, with no real process/filesystem access required — the same discipline the TypeScript
 * reference's injectable {@code CliDeps} applies.
 *
 * <p>Exit codes: {@code 0} the command ran and every check passed (or {@code --help}); {@code 1}
 * the command ran and at least one check failed; {@code 2} the command could not run at all (no
 * command given, an unknown command, or an unknown option).
 */
public final class Cli {

  private static final String USAGE =
      """
narrativetrace — one CLI over NarrativeTrace's open artifact formats

Usage:
  narrativetrace doctor [--json]

Commands:
  doctor    Read-only project diagnosis: toolchain, configuration, and known traps. Zero network.

Options:
  --json    Machine-readable output instead of human text.
  --help    Show this message.
""";

  private static final String DOCTOR_USAGE =
      """
      narrativetrace doctor [--json]

      Read-only. Checks toolchain/install state, configuration, and known traps against the current
      project. Exit 0 = clean, 1 = findings, 2 = could not run.
      """;

  /** Everything {@link Cli#run} reads from the outside world, injectable for hermetic tests. */
  public record Deps(
      Path cwd, Function<Path, DoctorSnapshot> buildSnapshot, PrintStream out, PrintStream err) {

    /** The real environment: the process's cwd, a real filesystem/env scan, real stdout/stderr. */
    public static Deps standard() {
      return new Deps(Path.of("").toAbsolutePath(), SnapshotBuilder::build, System.out, System.err);
    }
  }

  private Cli() {}

  public static int run(String[] args, Deps deps) {
    if (args.length == 0) {
      deps.err().println(USAGE);
      return 2;
    }
    String command = args[0];
    if (command.equals("--help") || command.equals("-h")) {
      deps.out().println(USAGE);
      return 0;
    }
    if (!command.equals("doctor")) {
      deps.err().println("Unknown command: " + command + "\n\n" + USAGE);
      return 2;
    }
    return runDoctor(List.of(args).subList(1, args.length), deps);
  }

  private static int runDoctor(List<String> rest, Deps deps) {
    boolean json = false;
    for (String arg : rest) {
      switch (arg) {
        case "--json" -> json = true;
        case "--help", "-h" -> {
          deps.out().println(DOCTOR_USAGE);
          return 0;
        }
        default -> {
          deps.err().println("Unknown doctor option: " + arg + "\n\n" + DOCTOR_USAGE);
          return 2;
        }
      }
    }
    DoctorSnapshot snapshot = deps.buildSnapshot().apply(deps.cwd());
    DoctorReport report = DoctorChecks.run(snapshot);
    deps.out().print(json ? DoctorRender.renderJson(report) : DoctorRender.renderHuman(report));
    return report.exitCode();
  }
}
