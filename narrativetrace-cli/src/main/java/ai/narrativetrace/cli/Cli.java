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

import ai.narrativetrace.tooling.doctor.DoctorChecks;
import ai.narrativetrace.tooling.doctor.DoctorRender;
import ai.narrativetrace.tooling.doctor.DoctorReport;
import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.SnapshotBuilder;
import ai.narrativetrace.tooling.init.Carrier;
import ai.narrativetrace.tooling.init.ExecutionReport;
import ai.narrativetrace.tooling.init.InitPlan;
import ai.narrativetrace.tooling.init.InitPlanner;
import ai.narrativetrace.tooling.init.PlanExecutor;
import ai.narrativetrace.tooling.init.PlanRenderer;
import ai.narrativetrace.tooling.init.ProjectState;
import ai.narrativetrace.tooling.init.ProjectStateReader;
import ai.narrativetrace.tooling.init.UninstallPlanner;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * {@code narrativetrace} — one CLI over NarrativeTrace's open artifact formats. Four commands:
 * {@code doctor} diagnoses, {@code init} installs the agent skills into a project, {@code
 * uninstall} removes exactly what {@code init} wrote, and {@code feedback} drafts a problem report
 * about NarrativeTrace itself and shows how to file it. Every argument-parsing decision is testable
 * in isolation via {@link Deps}, with no real process/filesystem access required — the same
 * discipline the TypeScript reference's injectable {@code CliDeps} applies.
 *
 * <p>Exit codes: {@code 0} the command ran and every check passed, or the plan applied, or it was a
 * dry run (or {@code --help}); {@code 1} the command ran and at least one check failed, or at least
 * one action was refused, or the carrier could not be opened; {@code 2} the command could not run
 * at all (no command given, an unknown command, or an unknown option).
 *
 * <p><b>@llmNote</b> This launcher makes NO network call of its own. {@code init} installs from the
 * carrier bundled in its own jar, or from whatever {@code --from} names locally — never from a
 * repository it would have to fetch. {@code feedback} sends nothing: it writes two files, prints a
 * URL or a command line, and leaves opening or running them to the person. The ONE outward-facing
 * thing it does is ask an already-installed {@code gh} whether it is authenticated, which is that
 * tool's own call through that tool's own credential — and only on the {@code gh} channel.
 */
public final class Cli {

  private static final String USAGE =
      """
narrativetrace — one CLI over NarrativeTrace's open artifact formats

Usage:
  narrativetrace doctor [--json]
  narrativetrace init [--dry-run] [--write-existing] [--force] [--only <half>] [--vendor <vendor>]
                      [--from <jar|dir|coordinate>] [--json]
  narrativetrace uninstall [--dry-run] [--only <half>] [--json]
  narrativetrace feedback <draft|url|gh> --category <c> --step <s>
                          --did <t> --happened <t> --expected <t> [--language <tag>]
                          [--agent-product <p>] [--agent-model <m>] [--trace <path>] [--json]

Commands:
  doctor     Read-only project diagnosis: toolchain, configuration, and known traps. Zero network.
  init       Installs the NarrativeTrace agent skills and the AGENTS.md section into this project.
  uninstall  Removes exactly what init wrote, and nothing beside it.
  feedback   Drafts a problem report, checks it carries no values, and shows you how to file it.

Options:
  --json    Machine-readable output instead of human text.
  --help    Show this message.
""";

  /** Package-private: {@link FeedbackVerb} prints it, and there is one copy of it. */
  static final String FEEDBACK_USAGE =
      """
      narrativetrace feedback <draft|url|gh> [options]

      Drafts a problem report about NarrativeTrace from this project: the install coordinate the
      build declares, the doctor's own JSON report, and at most one structural trace. Every field
      is checked against the value-free rules FIRST — the verb refuses to write a body file or
      build a URL while any rule stands, and names the rule.

      Channels:
        draft  Write and print the whole draft. Nothing is filed.
        url    Print the pre-filled issue-form URL. You open it and submit it yourself.
        gh     Print the exact `gh issue create` line. It is never run from here.

      Options:
        --category <c>       prompt | skill | doctor | library. Required.
        --step <s>           Which step, check id or skill step it happened at. Required.
        --did <t>            What you did. Required.
        --happened <t>       What happened instead. Required.
        --expected <t>       What you expected. Required.
        --language <tag>     The language the report is written in. en by default.
        --agent-product <p>  The agent product drafting this, as it reports itself.
        --agent-model <m>    The agent model, as it reports itself.
        --trace <path>       A path suffix naming the structural trace to attach.
        --json               Machine-readable output instead of human text.

      Nothing is sent anywhere. Exit 0 = drafted, 1 = that channel is not available, 2 = could not
      run (a missing flag, or a value-free rule refused the report).
      """;

  private static final String DOCTOR_USAGE =
      """
      narrativetrace doctor [--json]

      Read-only. Checks toolchain/install state, configuration, and known traps against the current
      project. Exit 0 = clean, 1 = findings, 2 = could not run.
      """;

  private static final String INSTALLER_OPTIONS =
      """
        --dry-run         Show the plan and the unified diff. Writes nothing, always exits 0.
        --write-existing  Permission to touch an AGENTS.md or CLAUDE.md that is already there.
        --force           Permission to overwrite a skill directory somebody else owns.
        --only <half>     skills | agents-md. Both halves by default.
        --vendor <vendor> claude | none. Detected from the project by default.
        --from <carrier>  A jar, a directory, or a group:artifact:version coordinate already in the
                          local Maven repository. The carrier bundled with this jar by default.
        --json            Machine-readable output instead of human text.

      Exit 0 = applied (or a dry run), 1 = something was refused, 2 = could not run.
      """;

  private static final String INIT_USAGE =
      """
      narrativetrace init [options]

      Copies the NarrativeTrace agent skills into .agents/skills/ (and .claude/skills/ where the
      project is one of that vendor's) and writes one marked section into AGENTS.md. Zero network.

      """
          + INSTALLER_OPTIONS;

  private static final String UNINSTALL_USAGE =
      """
      narrativetrace uninstall [options]

      Removes exactly what init wrote: skill directories carrying its provenance line, the marked
      section, and the one @AGENTS.md import line. Never touches anything else.

      """
          + INSTALLER_OPTIONS;

  /** What to type when the carrier cannot be found — the CLI itself never fetches anything. */
  private static final String FETCH_HINT =
      """
      Fetch the carrier once, then point --from at it:
        mvn dependency:copy -Dartifact=ai.narrativetrace:narrativetrace-skills:<version> \
      -DoutputDirectory=.
        narrativetrace init --from narrativetrace-skills-<version>.jar\
      """;

  /** Everything {@link Cli#run} reads from the outside world, injectable for hermetic tests. */
  public record Deps(
      Path cwd,
      Function<Path, DoctorSnapshot> buildSnapshot,
      Function<String, Carrier> openCarrier,
      BooleanSupplier ghAuthenticated,
      PrintStream out,
      PrintStream err) {

    /** The real environment: the process's cwd, a real filesystem/env scan, real stdout/stderr. */
    public static Deps standard() {
      CarrierLocator locator = CarrierLocator.standard();
      return new Deps(
          Path.of("").toAbsolutePath(),
          root -> SnapshotBuilder.build(root, bundledCarrierOrNone(locator)),
          locator::open,
          GhAuthProbe::authenticated,
          System.out,
          System.err);
    }
  }

  private Cli() {}

  /**
   * The carrier bundled in this launcher's own jar, or {@code null} when there is none to open.
   *
   * <p>The doctor is read-only and must always produce a report: a launcher built without the
   * skills resources, or one whose classpath entry cannot be read as a file, makes the skills check
   * answer "cannot tell" rather than turning a diagnosis into a stack trace. {@code init} takes the
   * opposite view of the same failure and refuses, which is why the two do not share this.
   */
  private static Carrier bundledCarrierOrNone(CarrierLocator locator) {
    try {
      return locator.open(null);
    } catch (RuntimeException e) {
      return null;
    }
  }

  public static int run(String[] args, Deps deps) {
    if (args.length == 0) {
      deps.err().println(USAGE);
      return 2;
    }
    String command = args[0];
    List<String> rest = List.of(args).subList(1, args.length);
    if (command.equals("--help") || command.equals("-h")) {
      deps.out().println(USAGE);
      return 0;
    }
    return switch (command) {
      case "doctor" -> runDoctor(rest, deps);
      case "init" -> runInstaller(true, rest, deps);
      case "uninstall" -> runInstaller(false, rest, deps);
      case "feedback" -> FeedbackVerb.run(rest, deps);
      default -> unknownCommand(command, deps);
    };
  }

  private static int unknownCommand(String command, Deps deps) {
    deps.err().println("Unknown command: " + command + "\n\n" + USAGE);
    return 2;
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

  /** The two installer verbs: same flags, same output, one plans an install and one its removal. */
  private static int runInstaller(boolean install, List<String> rest, Deps deps) {
    InstallerArguments parsed = InstallerArguments.parse(rest);
    String usage = install ? INIT_USAGE : UNINSTALL_USAGE;
    if (parsed.help()) {
      deps.out().println(usage);
      return 0;
    }
    if (parsed.error() != null) {
      deps.err().println(parsed.error() + "\n\n" + usage);
      return 2;
    }
    return install ? runInit(parsed, deps) : runUninstall(parsed, deps);
  }

  /**
   * The carrier is opened BEFORE the project is read: a run that cannot find its skills has nothing
   * to plan, and finding that out after walking the project would only delay the same message.
   */
  private static int runInit(InstallerArguments parsed, Deps deps) {
    Carrier carrier;
    try {
      carrier = deps.openCarrier().apply(parsed.from());
    } catch (IllegalArgumentException | UncheckedIOException e) {
      deps.err().println(e.getMessage() + "\n\n" + FETCH_HINT);
      return 1;
    }
    return runPlan(state -> InitPlanner.plan(state, carrier, parsed.options()), parsed, deps);
  }

  private static int runUninstall(InstallerArguments parsed, Deps deps) {
    return runPlan(state -> UninstallPlanner.plan(state, parsed.options()), parsed, deps);
  }

  private static int runPlan(
      Function<ProjectState, InitPlan> planner, InstallerArguments parsed, Deps deps) {
    try {
      return report(planner.apply(ProjectStateReader.read(deps.cwd())), parsed, deps);
    } catch (IllegalArgumentException | UncheckedIOException e) {
      deps.err().println(e.getMessage());
      return 1;
    }
  }

  /**
   * What the plan did, or what it would do. Both forms come from {@link PlanRenderer#render} so
   * this launcher and the Gradle task cannot drift apart on what a dry run shows.
   */
  private static int report(InitPlan plan, InstallerArguments parsed, Deps deps) {
    if (parsed.options().dryRun()) {
      deps.out().print(PlanRenderer.render(plan, parsed.json()));
      return plan.exitCode();
    }
    ExecutionReport executed = PlanExecutor.execute(plan, deps.cwd());
    deps.out().print(PlanRenderer.render(executed, parsed.json()));
    return executed.exitCode();
  }
}
