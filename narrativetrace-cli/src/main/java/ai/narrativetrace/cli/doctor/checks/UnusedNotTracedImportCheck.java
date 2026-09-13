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
package ai.narrativetrace.cli.doctor.checks;

import ai.narrativetrace.cli.doctor.DocAnchors;
import ai.narrativetrace.cli.doctor.DoctorCheck;
import ai.narrativetrace.cli.doctor.DoctorSnapshot;
import ai.narrativetrace.cli.doctor.Finding;
import java.util.List;
import java.util.Map;

/**
 * {@code trap.unused-not-traced-import} — a source file imports {@code
 * ai.narrativetrace.api.NotTraced} but never writes {@code @NotTraced} anywhere else in the file.
 * "Remember it exists" is not "run the step": an agent (or a person) that adds the import as a
 * reminder and never applies it ships a redaction that was never wired — the same trap the
 * TypeScript and Python studies both surfaced (imported-but-unused redaction primitives).
 */
public final class UnusedNotTracedImportCheck implements DoctorCheck {

  public static final String ID = "trap.unused-not-traced-import";

  private static final String IMPORT_LINE = "import ai.narrativetrace.api.NotTraced;";
  private static final String ANNOTATION_USE = "@NotTraced";

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    List<String> offending =
        snapshot.sourceFiles().entrySet().stream()
            .filter(e -> e.getValue().contains(IMPORT_LINE))
            .filter(e -> countOccurrences(e.getValue(), ANNOTATION_USE) == 0)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    if (offending.isEmpty()) {
      return Finding.pass(
          ID,
          "Every @NotTraced import is actually applied somewhere in its file",
          DocAnchors.PRIVACY_REDACTION);
    }
    return Finding.fail(
        ID,
        "@NotTraced is imported but never applied in: " + String.join(", ", offending),
        "Annotate the sensitive field or parameter with @NotTraced, or remove the unused import —"
            + " an import alone redacts nothing.",
        DocAnchors.PRIVACY_REDACTION);
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = haystack.indexOf(needle);
    while (idx != -1) {
      count++;
      idx = haystack.indexOf(needle, idx + needle.length());
    }
    return count;
  }
}
