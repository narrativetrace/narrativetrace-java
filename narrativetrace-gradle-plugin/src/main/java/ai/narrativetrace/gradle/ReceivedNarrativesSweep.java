/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Promotes every reviewed {@code *.received.nt} narrative under a directory to its {@code
 * *.approved.nt} baseline — the file-system half of the {@code approveNarratives} task.
 *
 * <p>INTENT: The {@code .received.nt} / {@code .approved.nt} name pair is the contract with the
 * test-time verifier ({@code NarrativeApproval} in narrativetrace-core); the plugin implements the
 * sweep itself because it deliberately depends on no NarrativeTrace runtime module — the same
 * boundary discipline as the clarity JSON parser.
 */
final class ReceivedNarrativesSweep {

  private ReceivedNarrativesSweep() {}

  /**
   * @return the approved baselines written, empty when the directory has nothing to promote or does
   *     not exist yet
   */
  static List<Path> promote(Path narrativesDir) throws IOException {
    if (!Files.isDirectory(narrativesDir)) {
      return List.of();
    }
    List<Path> received;
    try (var files = Files.walk(narrativesDir)) {
      received = files.filter(f -> f.getFileName().toString().endsWith(".received.nt")).toList();
    }
    var promoted = new ArrayList<Path>();
    for (var file : received) {
      var approved =
          file.resolveSibling(
              file.getFileName().toString().replace(".received.nt", ".approved.nt"));
      Files.move(file, approved, StandardCopyOption.REPLACE_EXISTING);
      promoted.add(approved);
    }
    return promoted;
  }
}
