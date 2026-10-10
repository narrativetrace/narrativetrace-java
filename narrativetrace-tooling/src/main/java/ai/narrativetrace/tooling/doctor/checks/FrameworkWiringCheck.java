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

/**
 * {@code config.<framework>-*} — one framework-table row, observed from manifest and source: the
 * framework is present but its integration module is not referenced, or the module is referenced
 * but its wiring was never applied. Generalises {@link ExtensionRegisteredCheck}'s "on the
 * classpath, never registered" to every row whose wiring is source-level.
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
  private final Wiring.Snippet wiring;
  private final String id;

  /**
   * @throws IllegalArgumentException for a row that earns no wiring check of its own
   */
  public FrameworkWiringCheck(FrameworkRow row) {
    if (!(row.check() instanceof CheckBinding.WiringCheck check)
        || !(row.wiring() instanceof Wiring.Snippet snippet)) {
      throw new IllegalArgumentException("row " + row.id() + " has no source-level wiring check");
    }
    this.row = row;
    this.wiring = snippet;
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
              + ". Then "
              + wiringFix(),
          DocAnchors.CHOOSING_AN_INTEGRATION);
    }
    if (wiring.appliedIn(snapshot.wiringTexts())) {
      return Finding.pass(
          id,
          module.artifact() + " is wired: " + wiring.description(),
          DocAnchors.CHOOSING_AN_INTEGRATION);
    }
    return Finding.fail(
        id,
        module.artifact()
            + " is referenced but its wiring ("
            + wiring.description()
            + ") is never applied — nothing in it is traced",
        "Apply the wiring: " + wiringFix(),
        DocAnchors.CHOOSING_AN_INTEGRATION);
  }

  private String wiringFix() {
    return "add "
        + wiring.description()
        + ", adapted to this project's packages (from "
        + wiring.fixture()
        + "):\n"
        + WiringSnippets.text(row.id());
  }
}
