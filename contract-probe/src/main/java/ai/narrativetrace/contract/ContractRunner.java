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
import ai.narrativetrace.contract.probes.LauncherAddedByPluginProbe;
import ai.narrativetrace.contract.probes.ManifestJsonProbe;
import ai.narrativetrace.contract.probes.NativeStringificationNotTrustedProbe;
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
 * INTENT: The standalone runner behind {@code contract-probe} (docs-vs-published-gate §2) — reads
 * {@code documentation/contract.yaml}, decides which entries apply at the installed version (ruling
 * 1: exempt only while {@code since} is strictly later than installed), runs the applicable ones'
 * dispatched probe against a PUBLISHED install (never {@code mavenLocal}, never {@code
 * project(...)}), and prints holds/fails/not-applicable-before-since per entry plus one summary
 * line. Writes a JSON result when {@code --out} is given. Exits 1 on any FAILS — the signal {@code
 * scripts/contract-check.sh} (the nightly wrapper) and the root {@code contractCheck} Gradle task
 * both key off.
 */
public final class ContractRunner {

  /**
   * Entry id → the probe that answers it. A map rather than a {@code switch} so the set of
   * dispatched ids is readable at runtime: {@link ContractDispatchCoverageTest} reads
   * documentation/contract.yaml and asserts every id in it appears here, which is the guard that
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
              "probed-run-name-console-footer",
              (entry, options) -> RunNameConsoleFooterProbe.observe()),
          Map.entry(
              "probed-run-name-manifest-field",
              (entry, options) -> RunNameManifestFieldProbe.observe()));

  private ContractRunner() {}

  /** The entry ids this runner can answer — the coverage guard's half of the comparison. */
  static Set<String> dispatchedEntryIds() {
    return PROBES.keySet();
  }

  private static String entryPoint(ContractEntry entry, Args options) {
    return EntryPointProbe.observe(entry.coordinate(), options.registryBase(), options.version());
  }

  public static void main(String[] args) throws IOException {
    Args options = Args.parse(args);
    List<ContractEntry> entries = ContractYaml.read(new File(options.contractPath()));

    int holds = 0;
    int notApplicable = 0;
    int fails = 0;
    List<String> jsonEntries = new ArrayList<>();

    for (ContractEntry entry : entries) {
      String verdict;
      String detail;
      if (!Versions.isApplicable(entry.since(), options.version())) {
        verdict = "not-applicable-before-since";
        detail = "since " + entry.since() + " is later than installed " + options.version();
        notApplicable++;
      } else {
        String observed = observe(entry, options);
        if (entry.expect().equals(observed)) {
          verdict = "holds";
          detail = "observed \"" + observed + "\"";
          holds++;
        } else {
          verdict = "fails";
          detail = failureMessage(entry, options.version(), observed);
          fails++;
        }
      }
      System.out.printf(Locale.ROOT, "%-45s %-30s %s%n", entry.id(), verdict, detail);
      jsonEntries.add(
          "{\"id\":\""
              + escape(entry.id())
              + "\",\"kind\":\""
              + escape(entry.kind())
              + "\",\"since\":\""
              + escape(entry.since())
              + "\",\"verdict\":\""
              + verdict
              + "\",\"detail\":\""
              + escape(detail)
              + "\"}");
    }

    String summary =
        holds
            + " holds, "
            + notApplicable
            + " not-applicable-before-since, "
            + fails
            + " fails (installed version "
            + options.version()
            + ")";
    System.out.println();
    System.out.println(summary);

    if (options.outPath() != null) {
      String json =
          "{\"version\":\""
              + escape(options.version())
              + "\",\"entries\":["
              + String.join(",", jsonEntries)
              + "],\"summary\":{\"holds\":"
              + holds
              + ",\"notApplicableBeforeSince\":"
              + notApplicable
              + ",\"fails\":"
              + fails
              + "}}";
      Files.writeString(new File(options.outPath()).toPath(), json);
    }

    if (fails > 0) {
      System.exit(1);
    }
  }

  private static String failureMessage(
      ContractEntry entry, String installedVersion, String observed) {
    String coordinate = entry.coordinate() != null ? entry.coordinate() : entry.id();
    return "documentation/contract.yaml: "
        + entry.id()
        + " documented default \""
        + entry.expect()
        + "\" (since "
        + entry.since()
        + ") but "
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
