/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * INTENT: one reading of a case directory's {@code case.json} — the single place that knows where a
 * case's own declarations live and how a string field is spelled there.
 *
 * <p>Hand-parsed rather than read through a JSON library: this module takes no dependency it does
 * not need, and a case manifest is a committed file of two or three flat string fields, linted by
 * the layout test rather than by a schema.
 *
 * @llmNote A field this returns empty for is a field the manifest does not carry; the CALLER
 *     decides whether that is a default or an error. No reader here invents a value.
 * @llmNote The {@code field} name a caller passes is also what its own error message must name,
 *     which is why {@link #manifestPath} is here beside the readers rather than inlined at each
 *     call site: a reader and the message about it have to point at one file.
 */
final class CaseManifest {

  private CaseManifest() {}

  /** The string {@code field} holds in {@code caseDir}'s manifest, or empty when neither exists. */
  static Optional<String> stringField(Path caseDir, String field) {
    Path manifest = caseDir.resolve("case.json");
    if (!Files.isRegularFile(manifest)) {
      return Optional.empty();
    }
    Matcher matcher = patternFor(field).matcher(read(manifest));
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  /**
   * The text {@code pattern}'s first group captures inside {@code caseDir}'s manifest, or empty
   * when neither the manifest nor a match exists — the raw-text reading a nested object needs,
   * since a nested object is not a string field and must not be read by scanning the whole file.
   *
   * <p><b>@llmNote</b> Scoping matters here rather than being tidiness: {@link #stringField} finds
   * its pattern ANYWHERE in the file, so a reader of {@code "2": "..."} that did not first narrow
   * to the enclosing object would read a top-level field of the same name as a turn.
   */
  static Optional<String> rawObjectField(Path caseDir, String field, Pattern pattern) {
    Path manifest = caseDir.resolve("case.json");
    if (!Files.isRegularFile(manifest)) {
      return Optional.empty();
    }
    Matcher matcher = pattern.matcher(read(manifest));
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  /** Where {@code caseDir}'s manifest lives, for a message that has to name the offending file. */
  static Path manifestPath(Path caseDir) {
    return caseDir.resolve("case.json");
  }

  private static Pattern patternFor(String field) {
    return Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"");
  }

  private static String read(Path manifest) {
    try {
      return Files.readString(manifest);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + manifest, e);
    }
  }
}
