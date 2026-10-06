/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

import ai.narrativetrace.contract.probes.ApprovalDefaultProbe;
import ai.narrativetrace.contract.probes.BufferCapacityDefaultProbe;
import ai.narrativetrace.contract.probes.BufferedPathIgnoresLoggerThresholdProbe;
import ai.narrativetrace.contract.probes.EntryPointProbe;
import ai.narrativetrace.contract.probes.FieldNameRedactionOnCustomClassProbe;
import ai.narrativetrace.contract.probes.FieldlessValueNotTrustedProbe;
import ai.narrativetrace.contract.probes.LauncherAddedByPluginProbe;
import ai.narrativetrace.contract.probes.ManifestJsonProbe;
import ai.narrativetrace.contract.probes.NativeStringificationNotTrustedProbe;
import ai.narrativetrace.contract.probes.NumberSubclassNotTrustedProbe;
import ai.narrativetrace.contract.probes.OutputDefaultProbe;
import ai.narrativetrace.contract.probes.ParameterNameRedactionProbe;
import ai.narrativetrace.contract.probes.PlatformTypeCarveoutProbe;
import ai.narrativetrace.contract.probes.RunNameConsoleFooterProbe;
import ai.narrativetrace.contract.probes.RunNameManifestFieldProbe;
import ai.narrativetrace.contract.probes.Slf4jZeroCodeAttachProbe;
import ai.narrativetrace.contract.probes.StructuralHeaderRuleProbe;
import ai.narrativetrace.contract.probes.TypedErrorMarkerProbe;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * INTENT: The standalone runner behind {@code contract-probe} — reads the {@code contract.yaml} it
 * is pointed at, runs every entry's dispatched probe against a PUBLISHED install (never {@code
 * mavenLocal}, never {@code project(...)}), and prints holds/fails per entry plus one summary line.
 * Writes a JSON result when {@code --out} is given. Exits 1 on any FAILS — the signal {@code
 * scripts/contract-check.sh} (the nightly wrapper) and the root {@code contractCheck} Gradle task
 * both key off.
 *
 * <p>EVERY entry is checked. An entry describes the code it was committed with, and the wrapper
 * hands this runner the contract as of the tag whose artifact it installed, so "this claim has not
 * shipped yet" is not a state that can arise and there is no verdict for it. The installed version
 * is still NAMED in the report and the JSON: that is machine-written metadata saying which artifact
 * answered, not a claim about when a rule began.
 *
 * @llmNote A probe that cannot answer at all returns null, and null is a FAILS with its own
 *     explaining detail — never a skip. Adding a third verdict here means the gate can go quiet,
 *     which is the failure mode this design exists to prevent.
 */
public final class ContractRunner {

  /**
   * Entry id → the probe that answers it. A map rather than a {@code switch} so the set of
   * dispatched ids is readable at runtime: {@link ContractDispatchCoverageTest} reads the contract
   * file this runner is pointed at and asserts every id in it appears here, which is the guard that
   * was missing when {@code probed-run-name-console-footer} and {@code
   * probed-run-name-manifest-field} shipped in the contract with their probe classes written but
   * never wired — the nightly gate crashed instead of reporting. A {@code switch}'s cases cannot be
   * enumerated without writing the same list a second time, and a list written twice is the defect
   * this guards against.
   */
  private static final Map<String, BiFunction<ContractEntry, Args, String>> PROBES =
      Map.ofEntries(
          Map.entry("entry-point-core", ContractRunner::entryPoint),
          Map.entry("entry-point-proxy", ContractRunner::entryPoint),
          Map.entry("entry-point-junit5", ContractRunner::entryPoint),
          Map.entry("entry-point-slf4j", ContractRunner::entryPoint),
          Map.entry(
              "reflectable-buffer-capacity-default",
              (entry, options) -> BufferCapacityDefaultProbe.observe()),
          Map.entry("probed-approval-default", (entry, options) -> ApprovalDefaultProbe.observe()),
          Map.entry("probed-manifest-json", (entry, options) -> ManifestJsonProbe.observe()),
          Map.entry(
              "probed-parameter-name-redaction",
              (entry, options) -> ParameterNameRedactionProbe.observe()),
          Map.entry(
              "probed-field-name-redaction-on-custom-class",
              (entry, options) -> FieldNameRedactionOnCustomClassProbe.observe()),
          Map.entry(
              "config-shape-slf4j-zero-code",
              (entry, options) -> Slf4jZeroCodeAttachProbe.observe()),
          Map.entry(
              "config-shape-buffered-path-ignores-logger-threshold",
              (entry, options) -> BufferedPathIgnoresLoggerThresholdProbe.observe()),
          Map.entry("probed-output-default", (entry, options) -> OutputDefaultProbe.observe()),
          Map.entry(
              "config-shape-launcher-added-by-plugin",
              (entry, options) ->
                  LauncherAddedByPluginProbe.observe(options.version(), options.gradlewPath())),
          Map.entry(
              "probed-structural-header-rule",
              (entry, options) -> StructuralHeaderRuleProbe.observe()),
          Map.entry(
              "probed-typed-error-marker", (entry, options) -> TypedErrorMarkerProbe.observe()),
          Map.entry(
              "probed-native-stringification-not-trusted",
              (entry, options) -> NativeStringificationNotTrustedProbe.observe()),
          Map.entry(
              "probed-platform-type-carveout",
              (entry, options) -> PlatformTypeCarveoutProbe.observe()),
          Map.entry(
              "probed-fieldless-value-not-trusted",
              (entry, options) -> FieldlessValueNotTrustedProbe.observe()),
          Map.entry(
              "probed-number-subclass-not-trusted",
              (entry, options) -> NumberSubclassNotTrustedProbe.observe()),
          Map.entry(
              "probed-run-name-console-footer",
              (entry, options) -> RunNameConsoleFooterProbe.observe()),
          Map.entry(
              "probed-run-name-manifest-field",
              (entry, options) -> RunNameManifestFieldProbe.observe()));

  /** The probe observed exactly the value the entry documents. */
  static final String HOLDS = "holds";

  /** It observed something else — a probe that could not answer at all included. */
  static final String FAILS = "fails";

  private ContractRunner() {}

  /** The entry ids this runner can answer — the coverage guard's half of the comparison. */
  static Set<String> dispatchedEntryIds() {
    return PROBES.keySet();
  }

  /** The verdict for one entry: exact string equality, or it fails. There is no third answer. */
  static String verdictOf(ContractEntry entry, String observed) {
    return entry.expect().equals(observed) ? HOLDS : FAILS;
  }

  private static String entryPoint(ContractEntry entry, Args options) {
    return EntryPointProbe.observe(entry.coordinate(), options.registryBase(), options.version());
  }

  /** One entry's line in the report and in the JSON, carried together so they cannot disagree. */
  private record Outcome(String verdict, String detail) {}

  /** What the whole run produced: the two tallies and one JSON object per entry, in file order. */
  private record Report(int holds, int fails, List<String> jsonEntries) {}

  public static void main(String[] args) throws IOException {
    Args options = Args.parse(args);
    List<ContractEntry> entries = ContractYaml.read(new File(options.contractPath()));
    Report report = runAll(entries, options);
    System.out.println();
    System.out.println(summary(report, options.version()));
    if (options.outPath() != null) {
      Files.writeString(
          new File(options.outPath()).toPath(), resultJson(report, options.version()));
    }
    if (report.fails() > 0) {
      System.exit(1);
    }
  }

  /** Runs every entry in file order, printing each verdict as it lands rather than at the end. */
  private static Report runAll(List<ContractEntry> entries, Args options) {
    int holds = 0;
    int fails = 0;
    List<String> jsonEntries = new ArrayList<>();
    for (ContractEntry entry : entries) {
      Outcome outcome = run(entry, options);
      if (HOLDS.equals(outcome.verdict())) {
        holds++;
      } else {
        fails++;
      }
      System.out.printf(
          Locale.ROOT, "%-45s %-30s %s%n", entry.id(), outcome.verdict(), outcome.detail());
      jsonEntries.add(entryJson(entry, outcome));
    }
    return new Report(holds, fails, jsonEntries);
  }

  private static Outcome run(ContractEntry entry, Args options) {
    String observed = observe(entry, options);
    String verdict = verdictOf(entry, observed);
    String detail =
        HOLDS.equals(verdict)
            ? "observed \"" + observed + "\""
            : failureMessage(entry, options.version(), observed);
    return new Outcome(verdict, detail);
  }

  private static String summary(Report report, String installedVersion) {
    return report.holds()
        + " holds, "
        + report.fails()
        + " fails (installed version "
        + installedVersion
        + ")";
  }

  private static String entryJson(ContractEntry entry, Outcome outcome) {
    return "{\"id\":\""
        + escape(entry.id())
        + "\",\"kind\":\""
        + escape(entry.kind())
        + "\",\"verdict\":\""
        + outcome.verdict()
        + "\",\"detail\":\""
        + escape(outcome.detail())
        + "\"}";
  }

  private static String resultJson(Report report, String installedVersion) {
    return "{\"version\":\""
        + escape(installedVersion)
        + "\",\"entries\":["
        + String.join(",", report.jsonEntries())
        + "],\"summary\":{\"holds\":"
        + report.holds()
        + ",\"fails\":"
        + report.fails()
        + "}}";
  }

  /**
   * All four facts on one line — the entry, the value the docs promise, the artifact that answered,
   * and what it actually read — so a skim of a failing run is enough to act on.
   */
  static String failureMessage(ContractEntry entry, String installedVersion, String observed) {
    String coordinate = entry.coordinate() != null ? entry.coordinate() : entry.id();
    return "contract.yaml: "
        + entry.id()
        + " documented default \""
        + entry.expect()
        + "\" but "
        + coordinate
        + " "
        + installedVersion
        + " (published) reads \""
        + (observed == null ? "<no answer>" : observed)
        + "\"";
  }

  private static String observe(ContractEntry entry, Args options) {
    BiFunction<ContractEntry, Args, String> probe = PROBES.get(entry.id());
    if (probe == null) {
      throw new IllegalStateException(
          "no probe dispatch registered for entry \""
              + entry.id()
              + "\" — add one in ContractRunner.observe");
    }
    return probe.apply(entry, options);
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
