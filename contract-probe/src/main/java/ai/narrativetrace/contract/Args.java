/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

import java.io.File;

/** Parsed {@code --key=value} command-line arguments for {@link ContractRunner}. */
record Args(
    String version, String contractPath, String outPath, String registryBase, String gradlewPath) {

  static Args parse(String[] args) {
    String version = null;
    String contract = "documentation/contract.yaml";
    String out = null;
    String registryBase = "https://repo1.maven.org/maven2";
    for (String arg : args) {
      if (arg.startsWith("--version=")) {
        version = arg.substring("--version=".length());
      } else if (arg.startsWith("--contract=")) {
        contract = arg.substring("--contract=".length());
      } else if (arg.startsWith("--out=")) {
        out = arg.substring("--out=".length());
      } else if (arg.startsWith("--registry-base=")) {
        registryBase = arg.substring("--registry-base=".length());
      }
    }
    if (version == null) {
      throw new IllegalArgumentException("--version=<published version> is required");
    }
    // Computed from the JVM's own working directory (contract-probe/, set by the `runContract`
    // JavaExec) rather than a literal "./gradlew": LauncherAddedByPluginProbe changes the working
    // directory to a generated temp project before invoking it, so a relative path would resolve
    // against the wrong directory at that point.
    String gradlewPath = new File(System.getProperty("user.dir"), "gradlew").getAbsolutePath();
    return new Args(version, contract, out, registryBase, gradlewPath);
  }
}
