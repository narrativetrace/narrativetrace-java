/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * INTENT: the environment every command of one trial runs with, and the evidence a grader reads
 * afterwards — the transcript, the recording {@code gh} stand-in, and its log.
 *
 * <p>All of it lives in a work directory OUTSIDE the scaffolded project. That is the whole point:
 * the agent cannot read what it is being graded on, and cannot write it either, so a transcript is
 * evidence rather than something the subject produced about itself. Only the grader is told where
 * the two files are.
 *
 * <p><b>@llmNote</b> The stand-ins are installed on EVERY recording trial's PATH, not only the
 * feedback cases'. Two reasons, and the second is the stronger one. First, {@code gh} being merely
 * absent makes "the agent tried to file an issue" and "the agent did not try" indistinguishable — a
 * `command not found` says nothing about intent, and intent is exactly what the approval gate
 * measures. Second, no trial can then create a real issue anywhere, whatever an agent decides to
 * run: {@code gh} is outside every skill's closed command vocabulary, so nothing legitimate loses
 * anything by it. The {@code curl} stand-in serves the published site, because reading {@code
 * llms.txt} is the product's own first instruction, and loopback, because a server's own endpoint
 * is how a web-framework case's program is run.
 *
 * <p><b>@sideEffects</b> {@link #under} creates the work directory and writes one executable
 * stand-in per blocked command into it. {@link #recordUserTurn} appends to the transcript.
 */
public final class TrialEnvironment {

  /** What the grader is told, and the agent is not. */
  private static final String TRANSCRIPT_VAR = "NARRATIVETRACE_TRANSCRIPT";

  private static final String GH_LOG_VAR = "NARRATIVETRACE_GH_LOG";

  /**
   * The commands a trial may not really run, each with the exit code its stand-in answers.
   *
   * <p>{@code gh} exits 0: the point is to record that the agent tried to FILE, so the stand-in
   * behaves as if it had worked and files nothing. {@code curl} exits 6 — curl's own "could not
   * resolve host" — because the truthful answer to a request from a sandbox with no network is a
   * network failure, where exiting 0 with empty output would read as "that repository has nothing
   * in it" and send the agent down a wrong path.
   *
   * <p>Both are outside every skill's closed command vocabulary ({@code ./gradlew}, {@code git},
   * {@code find}) by ruling, so nothing a skill legitimately instructs loses anything by this.
   * {@code curl} earned its place: a real feedback trial spent a whole turn making about forty
   * requests to {@code api.github.com} and {@code raw.githubusercontent.com} looking for the
   * repository to file into.
   *
   * <p>One host is served, not blocked: {@link #SERVED_HOST}, the published site. The init prompt's
   * first step is "read llms.txt first", and an agent that fetches it with {@code curl} rather than
   * a summarising page tool is doing exactly what the product asks (2026-10-08: Haiku 5.5 refused a
   * lossy WebFetch summary, reached for curl, hit the stand-in's exit 6 six times out of six, and
   * stopped — a harness verdict, not a product one). The stand-in records the request either way
   * and hands a request whose every URL is on that host to the real curl behind it on PATH; one
   * foreign URL in the same invocation, or no URL at all, still answers exit 6.
   */
  private static final Map<String, Integer> BLOCKED_COMMANDS = Map.of("gh", 0, "curl", 6);

  /** The one host a {@code curl} stand-in passes through to the real curl: the published site. */
  static final String SERVED_HOST = "narrativetrace.ai";

  /**
   * Loopback names a {@code curl} stand-in also passes through, with or without a scheme: a
   * web-framework case's program is a server, and running it means requesting its own endpoint.
   * Loopback reaches no network, so serving it costs the trial none of its isolation.
   */
  @SuppressWarnings("PMD.AvoidUsingHardCodedIP") // loopback by definition, never a configured host
  static final List<String> LOOPBACK_HOSTS = List.of("localhost", "127.0.0.1", "[::1]");

  /**
   * How a project property reaches a {@code ./gradlew} line the AGENT types. A {@code -P} flag only
   * reaches one the harness types, and a fixture scaffolded into a temp directory has nothing
   * relative to it that can find this checkout.
   */
  private static final String TEST_REPO_VAR = "ORG_GRADLE_PROJECT_narrativetraceTestMavenRepo";

  /** Where {@code publishSkillsTestRepo} leaves the plugin and every library it can add. */
  private static final String TEST_REPO_PATH = "narrativetrace-skills-catalogue/build/test-repo";

  /**
   * No {@code __pycache__}, because a grader that imports a shared helper would write one INTO the
   * committed case directory — a trial leaving build output inside the repository it is testing,
   * which {@code EvalCaseLayoutTest} then reads as a case directory with no prompt in it.
   */
  private static final String NO_BYTECODE_VAR = "PYTHONDONTWRITEBYTECODE";

  private static final TrialEnvironment NONE = new TrialEnvironment(null, Map.of());

  private final Path workDir;
  private final Map<String, String> agentEnvironment;

  private TrialEnvironment(Path workDir, Map<String, String> agentEnvironment) {
    this.workDir = workDir;
    this.agentEnvironment = Map.copyOf(agentEnvironment);
  }

  /** A trial that records nothing and adds nothing — what a unit test of the flow wires. */
  public static TrialEnvironment none() {
    return NONE;
  }

  /**
   * A trial recording into {@code workDir}, with the {@code gh} stand-in installed and a route to
   * the libraries published under {@code repoRoot}.
   *
   * @throws IllegalArgumentException when {@code workDir}'s path could break out of the stub's own
   *     POSIX quoting
   * @throws IOException if the work directory or the stub cannot be written
   */
  public static TrialEnvironment under(Path workDir, Path repoRoot) throws IOException {
    requireQuotableWorkDirectory(workDir);
    Path binDir = Files.createDirectories(workDir.resolve("bin"));
    Path log = workDir.resolve("gh-invocations.log");
    for (Map.Entry<String, Integer> blocked : BLOCKED_COMMANDS.entrySet()) {
      writeStandIn(binDir.resolve(blocked.getKey()), blocked.getKey(), blocked.getValue(), log);
    }

    Map<String, String> environment = new LinkedHashMap<>();
    environment.put("PATH", binDir + ":" + System.getenv("PATH"));
    environment.put(NO_BYTECODE_VAR, "1");
    environment.putAll(IsolatedAgentConfig.agentConfigEnv(workDir));
    Path testRepo = repoRoot.resolve(TEST_REPO_PATH);
    if (Files.isDirectory(testRepo)) {
      environment.put(TEST_REPO_VAR, testRepo.toString());
    }
    return new TrialEnvironment(workDir, environment);
  }

  /** Whether this trial keeps a transcript and a {@code gh} log at all. */
  public boolean isRecording() {
    return workDir != null;
  }

  /** Every turn's prompt and the agent's own stdout, in order — the grader's record of the run. */
  public Path transcript() {
    return requireRecording().resolve("transcript.jsonl");
  }

  /**
   * One line per invocation of a blocked command the stand-ins recorded, argv included — {@code gh}
   * and {@code curl} in the same file, so "what did this trial try to reach?" is one place to look.
   */
  public Path blockedInvocations() {
    return requireRecording().resolve("gh-invocations.log");
  }

  /**
   * The same log, under the name the graders' environment variable still uses.
   *
   * <p>The file name and the variable are unchanged on purpose: a grader reading {@code
   * $NARRATIVETRACE_GH_LOG} for "was anything filed?" is reading the right file, and renaming it
   * would be a change to every committed case for no behaviour at all.
   */
  public Path ghInvocations() {
    return blockedInvocations();
  }

  /** What the agent's own turns run with: the stub's PATH and the route to the libraries. */
  public Map<String, String> agentEnvironment() {
    return agentEnvironment;
  }

  /** The agent's environment, plus where the two pieces of evidence are. */
  public Map<String, String> graderEnvironment() {
    if (!isRecording()) {
      return agentEnvironment;
    }
    Map<String, String> environment = new LinkedHashMap<>(agentEnvironment);
    environment.put(TRANSCRIPT_VAR, transcript().toString());
    environment.put(GH_LOG_VAR, ghInvocations().toString());
    return Map.copyOf(environment);
  }

  /**
   * Marks the start of {@code turn} in the transcript, with the words the user is given there.
   *
   * <p>The marker is what lets a grader say WHICH turn it is reading — every agent line after it
   * and before the next marker belongs to that turn — and it carries the user's own words, so a
   * grader compares the agent's behaviour against the reply that was actually scripted rather than
   * against a copy of it.
   *
   * @throws IOException if the transcript cannot be appended to
   */
  public void recordUserTurn(int turn, String text) throws IOException {
    Path transcript = transcript();
    Files.createDirectories(transcript.getParent());
    Files.writeString(
        transcript,
        "{\"nt_turn\":" + turn + ",\"role\":\"user\",\"text\":" + jsonString(text) + "}\n",
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }

  /**
   * Copies whatever evidence this trial produced into {@code destination}, and returns it.
   *
   * <p>A trial's work directory is a temp directory the runner deletes when the trial ends,
   * whatever the outcome — right for a pass and useless for a fail, where the grader's one-line
   * reason is on the console and the record it read is gone. A red row nobody can diagnose is the
   * one outcome a Tier B harness cannot afford, since a trial costs a real request.
   *
   * @throws IOException if the destination cannot be written
   * @sideEffects creates {@code destination} and writes this trial's evidence into it
   */
  public Path keepEvidenceUnder(Path destination) throws IOException {
    requireRecording();
    Files.createDirectories(destination);
    for (Path evidence : List.of(transcript(), ghInvocations())) {
      if (Files.isRegularFile(evidence)) {
        Files.copy(
            evidence,
            destination.resolve(evidence.getFileName()),
            StandardCopyOption.REPLACE_EXISTING);
      }
    }
    return destination;
  }

  private Path requireRecording() {
    if (!isRecording()) {
      throw new IllegalStateException("a trial that records nothing has no evidence to point at");
    }
    return workDir;
  }

  /**
   * A recording stand-in: it writes down what it was asked to do, and does none of it — except
   * {@code curl} for {@link #SERVED_HOST}, which it hands to the real curl after recording.
   *
   * <p>Each argument is appended on the same line, so one invocation is one greppable line and a
   * grader can both count them and search them for a planted value.
   */
  private static void writeStandIn(Path stub, String name, int exitCode, Path log)
      throws IOException {
    requireQuotableWorkDirectory(log);
    Files.writeString(
        stub,
        "#!/bin/sh\n"
            + "# A recording stand-in for `"
            + name
            + "`, installed first on this trial's PATH by\n"
            + "# the eval runner. It records its argv and does NOTHING ELSE, so no trial can file\n"
            + "# an issue or reach a network — and so that 'the agent tried to' is"
            + " distinguishable\n"
            + "# from 'it did not', which the command merely being absent is not.\n"
            + "{\n"
            + "  printf '"
            + name
            + "'\n"
            + "  for arg in \"$@\"; do printf ' %s' \"$arg\"; done\n"
            + "  printf '\\n'\n"
            + "} >> '"
            + log
            + "'\n"
            + servedRequestOrExit(name, exitCode));
    if (!stub.toFile().setExecutable(true)) {
      throw new IOException("could not make the " + name + " stand-in executable: " + stub);
    }
  }

  /**
   * curl options whose NEXT argument is their value (a file, a header, a body) — never a URL. An
   * option outside this list and {@link #CURL_PLAIN_OPTIONS} is refused: {@code -x}/{@code
   * --proxy}, {@code -K}/{@code --config}, {@code --connect-to} and their kind each name a host.
   */
  private static final String CURL_VALUE_OPTIONS =
      "-o|-w|-H|-X|-d|-m|-A|-e|-u|-b|-c|--output|--write-out|--header|--request|--data|--data-raw"
          + "|--data-binary|--data-urlencode|--max-time|--user-agent|--connect-timeout|--retry"
          + "|--retry-delay|--referer|--cookie|--cookie-jar|--user";

  /** curl options that take no value and reach nothing on their own. */
  private static final String CURL_PLAIN_OPTIONS =
      "--silent|--show-error|--location|--fail|--fail-with-body|--include|--head|--verbose"
          + "|--compressed|--globoff|--insecure|--no-progress-meter";

  /** The letters a bundled short-option cluster ({@code -sSL}) may carry: value-less, harmless. */
  private static final String CURL_PLAIN_LETTERS = "sSLfiIvkg";

  /**
   * The stand-in's tail: {@code exit N} for every command but {@code curl}. For curl, EVERY
   * argument is classified, and the request is passed to the first real curl behind the stand-in on
   * PATH only when each one is accounted for: a known option (or its value), or a URL — with an
   * http(s) scheme or none — whose authority carries no userinfo, whose port is numeric, and whose
   * host is exactly {@link #SERVED_HOST} or a {@link #LOOPBACK_HOSTS} name. Anything else answers
   * the blocked code: a foreign or schemeless foreign host, another scheme, an unknown option, a
   * proxy. The tail uses shell builtins only and marks its environment, so a stand-in that finds
   * nothing but itself on PATH exits instead of exec'ing itself again.
   *
   * <p><b>@llmNote</b> Classify every argument, never "look for a bad URL": a first version scanned
   * only {@code http(s)://} arguments, and a schemeless {@code evil.example}, an {@code ftp://}
   * URL, {@code --url=…} or {@code -x proxy} beside a served URL all reached the real curl
   * (adversarial pass, 2026-10-09). A host in capitals is refused too — POSIX {@code case} has no
   * portable lowercasing, and refusing is the safe side.
   */
  private static String servedRequestOrExit(String name, int exitCode) {
    if (!"curl".equals(name)) {
      return "exit " + exitCode + "\n";
    }
    String hosts =
        Stream.concat(Stream.of(SERVED_HOST), LOOPBACK_HOSTS.stream())
            .map(host -> "\"" + host + "\"")
            .collect(Collectors.joining("|"));
    return """
    served=0
    skip=0
    for arg in "$@"; do
      if [ "$skip" = 1 ]; then skip=0; continue; fi
      case "$arg" in
        --url) continue ;;
        --url=*) arg=${arg#--url=} ;;
        %1$s) skip=1; continue ;;
        --*=*)
          case "${arg%%%%=*}" in %1$s) continue ;; *) exit %3$d ;; esac ;;
        %2$s) continue ;;
        -|--*) exit %3$d ;;
        -*[!%4$s]*) exit %3$d ;;
        -*) continue ;;
      esac
      rest=$arg
      case "$rest" in
        *://*)
          case "${rest%%%%://*}" in http|https) ;; *) exit %3$d ;; esac
          rest=${rest#*://} ;;
      esac
      authority=${rest%%%%[/?#]*}
      case "$authority" in *@*) exit %3$d ;; esac
      case "$authority" in
        "["*) host="${authority%%%%]*}]" ;;
        *) host=${authority%%%%:*} ;;
      esac
      port=${authority#"$host"}
      case "$port" in "" | :) ;; :*[!0-9]*) exit %3$d ;; :*) ;; *) exit %3$d ;; esac
      case "$host" in %5$s) served=1 ;; *) exit %3$d ;; esac
    done
    [ "$served" = 1 ] || exit %3$d
    [ -n "$NARRATIVETRACE_CURL_STANDIN" ] && exit %3$d
    export NARRATIVETRACE_CURL_STANDIN=1
    self=${0%%/*}
    IFS=:
    for dir in $PATH; do
      [ "$dir" = "$self" ] && continue
      [ "$dir/curl" -ef "$0" ] && continue
      [ -x "$dir/curl" ] && exec "$dir/curl" "$@"
    done
    exit %3$d
    """
        .formatted(CURL_VALUE_OPTIONS, CURL_PLAIN_OPTIONS, exitCode, CURL_PLAIN_LETTERS, hosts);
  }

  /** The stub embeds its log path in a POSIX single-quoted string, which one quote would end. */
  private static void requireQuotableWorkDirectory(Path path) {
    if (path.toString().indexOf('\'') >= 0) {
      throw new IllegalArgumentException(
          "a trial's work directory may not carry a ' — the gh stand-in quotes its log path: "
              + path);
    }
  }

  /**
   * An RFC 8259 quoted string, so a marker is one line whatever the turn's text carries.
   *
   * <p><b>@llmNote</b> Written here rather than reusing {@code RunLedgerRow}'s own quoting, which
   * escapes the two mandatory characters and NOT a line feed. That is sound for a ledger row (no
   * field of one can carry a newline) and wrong here, where turn 1's text is a whole prompt file.
   * Converging them means converging the ledger's matching parser too, which is its own change.
   */
  private static String jsonString(String text) {
    StringBuilder out = new StringBuilder("\"");
    for (int i = 0; i < text.length(); i++) {
      appendEscaped(out, text.charAt(i));
    }
    return out.append('"').toString();
  }

  private static void appendEscaped(StringBuilder out, char c) {
    switch (c) {
      case '"' -> out.append("\\\"");
      case '\\' -> out.append("\\\\");
      case '\n' -> out.append("\\n");
      case '\r' -> out.append("\\r");
      case '\t' -> out.append("\\t");
      default -> out.append(c < 0x20 ? String.format(Locale.ROOT, "\\u%04x", (int) c) : c);
    }
  }
}
