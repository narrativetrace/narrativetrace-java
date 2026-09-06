/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples;

import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.export.TraceTreeCanonicalMapper;
import ai.narrativetrace.glossary.GlossaryLoader;
import ai.narrativetrace.glossary.TraceTranslationView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Demo-launcher hook: writes each scenario's captured trace as a translated markdown file.
 *
 * <p>INTENT: The demo's translated mode ({@code ./demo.sh --lang es}) renders the run through the
 * example's committed glossary in the same JVM — the same {@link TraceTranslationView} rendering
 * the live {@code TranslationSubscriber} uses (their parity is pinned by a jqwik property in the
 * glossary module). A live subscriber only knows trace ids; this hook owns the scenario-named
 * files, because only the demo knows which scenario a trace belongs to. Examples call {@link
 * #capture} right where they capture each scenario's tree; the call is a no-op unless the launcher
 * set {@code -Dnarrativetrace.demo.translationDir} and {@code -Dnarrativetrace.demo.locale}, so
 * ordinary runs write nothing. The glossary resolves through {@link GlossaryLoader} ({@code
 * -Dnarrativetrace.glossary.path} in the demo). A best-effort sink: any problem is reported to
 * stderr and swallowed — trace persistence must never break a demo run.
 */
public final class DemoTraces {

  /** System property naming the directory that receives translated scenario files. */
  static final String DIR_PROPERTY = "narrativetrace.demo.translationDir";

  /** System property naming the locale the demo run is rendered into. */
  static final String LOCALE_PROPERTY = "narrativetrace.demo.locale";

  private static final java.util.concurrent.atomic.AtomicInteger SEQUENCE =
      new java.util.concurrent.atomic.AtomicInteger(1);

  private DemoTraces() {}

  /**
   * Writes {@code <nn>_<slug(scenario)>.md} into the configured directory, or does nothing when the
   * demo launcher did not request translated capture or the trace is empty. The {@code nn}
   * run-sequence prefix keeps alphabetical file order equal to capture order, so the launcher walks
   * translated scenarios in the order they ran. Line 1 is the exact scenario title ({@code === …
   * ===}): the launcher uses it as the section header and as the key into its localized wiring
   * tables — a slug round-trip would lose the casing and punctuation the keys carry.
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // best-effort: demo must never break
  public static void capture(String scenario, TraceTree trace) {
    var dir = System.getProperty(DIR_PROPERTY);
    var locale = System.getProperty(LOCALE_PROPERTY);
    if (dir == null || locale == null || trace.isEmpty()) {
      return;
    }
    try {
      writeTranslated(scenario, trace, Path.of(dir), locale);
    } catch (IOException | RuntimeException e) { // NOPMD
      System.err.println("DemoTraces: could not write translated trace: " + e.getMessage());
    }
  }

  private static void writeTranslated(String scenario, TraceTree trace, Path dir, String locale)
      throws IOException {
    var glossary = GlossaryLoader.load();
    if (glossary.isEmpty()) {
      System.err.println("DemoTraces: no glossary found — translated capture skipped");
      return;
    }
    var view = new TraceTranslationView(glossary.get(), className -> null);
    var text = view.render(TraceTreeCanonicalMapper.fromTree(trace), locale);
    var name = "%02d_%s.md".formatted(SEQUENCE.getAndIncrement(), slug(scenario));
    Files.createDirectories(dir);
    Files.writeString(dir.resolve(name), "=== " + scenario + " ===\n\n" + text);
  }

  /**
   * Lowercased, punctuation-free file stem: {@code "Scenario 1: Order!"} → {@code
   * scenario_1_order}.
   */
  private static String slug(String scenario) {
    return scenario
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("^_+|_+$", "");
  }
}
