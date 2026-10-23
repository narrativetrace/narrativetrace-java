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
package ai.narrativetrace.tooling.doctor.checks;

import ai.narrativetrace.tooling.doctor.DocAnchors;
import ai.narrativetrace.tooling.doctor.DoctorCheck;
import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.Finding;
import ai.narrativetrace.tooling.frameworks.CheckBinding;
import ai.narrativetrace.tooling.frameworks.FrameworkRow;
import ai.narrativetrace.tooling.frameworks.FrameworkTable;
import ai.narrativetrace.tooling.frameworks.IntegrationModule;
import ai.narrativetrace.tooling.frameworks.Wiring;
import ai.narrativetrace.tooling.frameworks.WiringSnippets;
import java.util.Optional;

/**
 * {@code config.<framework>-*} — one framework-table row, observed from manifest and source: the
 * framework is present but its integration module is not referenced, or the module is referenced
 * but its wiring was never applied. Generalises {@link ExtensionRegisteredCheck}'s "on the
 * classpath, never registered" to every row whose wiring is source-level — and, for a module that
 * attaches itself once present ({@link Wiring.DependenciesOnly}, TestNG's service-loader listener),
 * checks the one half that can go wrong: the framework is declared and the module is not.
 *
 * <p>INTENT: the {@code add-narrative-tracing} skill lists no framework; its one framework step
 * runs the doctor and applies every {@code config.<framework>-*} fix in order. So every failing fix
 * here is complete on its own: the dependency lines (plugin DSL and plain Gradle), then the row's
 * wiring snippet verbatim — the compiled fixture's text from {@link WiringSnippets}.
 *
 * <p><b>@llmNote</b> Text and manifest only, never a build: a marker or a module reference is a
 * pattern over the manifests' text, wiring evidence a pattern over the project's sources and
 * resources. A project that neither uses the framework nor references the module passes — a check
 * fails a project for what it got wrong, never for what it does not use.
 */
public final class FrameworkWiringCheck implements DoctorCheck {

  private final FrameworkRow row;

  /** The row's source wiring; empty for a module whose dependency is its wiring. */
  private final Optional<Wiring.Snippet> wiring;

  private final String id;

  /**
   * @throws IllegalArgumentException for a row that earns no wiring check of its own
   */
  public FrameworkWiringCheck(FrameworkRow row) {
    if (!(row.check() instanceof CheckBinding.WiringCheck check)) {
      throw new IllegalArgumentException("row " + row.id() + " earns no wiring check of its own");
    }
    this.row = row;
    this.wiring =
        row.wiring() instanceof Wiring.Snippet snippet ? Optional.of(snippet) : Optional.empty();
    this.id = check.id();
  }

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    String manifest = snapshot.manifestText();
    IntegrationModule module = row.module();
    boolean referenced = module.referencedIn(manifest);
    if (!referenced && !FrameworkTable.detected(row, manifest)) {
      return Finding.pass(
          id, row.name() + " not detected — nothing to wire", DocAnchors.CHOOSING_AN_INTEGRATION);
    }
    if (!referenced) {
      return Finding.fail(
          id,
          row.name()
              + " detected ("
              + row.marker().description()
              + ") but "
              + module.artifact()
              + " is not referenced — nothing in it is traced",
          "Add "
              + row.module().artifact()
              + " — "
              + row.module().addInstruction(snapshot.narrativeTraceVersionOrPlaceholder())
              + wiring.map(snippet -> ". Then " + wiringFix(snippet)).orElse("."),
          DocAnchors.CHOOSING_AN_INTEGRATION);
    }
    if (wiring.isEmpty()) {
      return Finding.pass(
          id,
          module.artifact() + " is referenced: " + row.wiring().description(),
          DocAnchors.CHOOSING_AN_INTEGRATION);
    }
    var snippet = wiring.get();
    if (snippet.appliedIn(snapshot.wiringTexts())) {
      return Finding.pass(
          id,
          module.artifact() + " is wired: " + snippet.description(),
          DocAnchors.CHOOSING_AN_INTEGRATION);
    }
    return Finding.fail(
        id,
        module.artifact()
            + " is referenced but its wiring ("
            + snippet.description()
            + ") is never applied — nothing in it is traced",
        "Apply the wiring: " + wiringFix(snippet),
        DocAnchors.CHOOSING_AN_INTEGRATION);
  }

  private String wiringFix(Wiring.Snippet snippet) {
    return "add "
        + snippet.description()
        + ", adapted to this project's packages (from "
        + snippet.fixture()
        + "):\n"
        + WiringSnippets.text(row.id());
  }
}
