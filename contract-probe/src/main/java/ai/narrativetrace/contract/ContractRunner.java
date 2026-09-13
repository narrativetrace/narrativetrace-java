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
import ai.narrativetrace.contract.probes.EntryPointProbe;
import ai.narrativetrace.contract.probes.LauncherAddedByPluginProbe;
import ai.narrativetrace.contract.probes.ManifestJsonProbe;
import ai.narrativetrace.contract.probes.NativeStringificationNotTrustedProbe;
import ai.narrativetrace.contract.probes.OutputDefaultProbe;
import ai.narrativetrace.contract.probes.ParameterNameRedactionProbe;
import ai.narrativetrace.contract.probes.PlatformTypeCarveoutProbe;
import ai.narrativetrace.contract.probes.Slf4jZeroCodeAttachProbe;
import ai.narrativetrace.contract.probes.StructuralHeaderRuleProbe;
import ai.narrativetrace.contract.probes.TypedErrorMarkerProbe;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

  private ContractRunner() {}

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
    return switch (entry.id()) {
      case "entry-point-core", "entry-point-proxy", "entry-point-junit5", "entry-point-slf4j" ->
          EntryPointProbe.observe(entry.coordinate(), options.registryBase(), options.version());
      case "reflectable-buffer-capacity-default" -> BufferCapacityDefaultProbe.observe();
      case "probed-approval-default" -> ApprovalDefaultProbe.observe();
      case "probed-manifest-json" -> ManifestJsonProbe.observe();
      case "probed-parameter-name-redaction" -> ParameterNameRedactionProbe.observe();
      case "config-shape-slf4j-zero-code" -> Slf4jZeroCodeAttachProbe.observe();
      case "probed-output-default" -> OutputDefaultProbe.observe();
      case "config-shape-launcher-added-by-plugin" ->
          LauncherAddedByPluginProbe.observe(options.version(), options.gradlewPath());
      case "probed-structural-header-rule" -> StructuralHeaderRuleProbe.observe();
      case "probed-typed-error-marker" -> TypedErrorMarkerProbe.observe();
      case "probed-native-stringification-not-trusted" ->
          NativeStringificationNotTrustedProbe.observe();
      case "probed-platform-type-carveout" -> PlatformTypeCarveoutProbe.observe();
      default ->
          throw new IllegalStateException(
              "no probe dispatch registered for entry \""
                  + entry.id()
                  + "\" — add one in ContractRunner.observe");
    };
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
