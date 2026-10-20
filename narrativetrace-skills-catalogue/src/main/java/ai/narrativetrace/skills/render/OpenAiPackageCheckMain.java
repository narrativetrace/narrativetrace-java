/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The {@code checkOpenAiPluginZip} Gradle task's entry point: opens the built OpenAI plugin ZIP and
 * throws, naming every violation, if {@link OpenAiPackageCheck#violations} finds one — JavaExec's
 * default behaviour (fail the build on an uncaught exception) is the gate.
 */
public final class OpenAiPackageCheckMain {

  private OpenAiPackageCheckMain() {}

  public static void main(String[] args) throws IOException {
    Path zip = Path.of(args[0]);
    String version = args[1];
    List<String> violations =
        OpenAiPackageCheck.violations(
            zip,
            OpenAiPackageLayout.entries(CatalogueIndex.ALL, CatalogueIndex.OPENAI_DIRECTORY),
            version);
    if (!violations.isEmpty()) {
      throw new IllegalStateException(
          zip + " failed the OpenAI plugin package checks:\n" + String.join("\n", violations));
    }
    System.out.println(
        "checkOpenAiPluginZip: " + zip + " matches the layout and passes every check");
  }
}
