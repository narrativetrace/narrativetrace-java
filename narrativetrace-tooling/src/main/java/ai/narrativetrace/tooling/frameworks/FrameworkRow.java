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
package ai.narrativetrace.tooling.frameworks;

/**
 * One row of the framework table: marker → module → wiring → doctor check → Tier B case.
 *
 * <p>A new framework is a new row, never new machinery: the doctor builds one check per {@link
 * CheckBinding.WiringCheck} row, and the documentation's integration table and covered-frameworks
 * line render from the same rows.
 *
 * @param id the stable row id ({@code spring}, {@code junit4}, …)
 * @param name the framework as a reader names it
 * @param tierBCase the Tier B eval case that exercises the row, or {@code none}
 */
public record FrameworkRow(
    String id,
    String name,
    Marker marker,
    IntegrationModule module,
    Wiring wiring,
    CheckBinding check,
    String tierBCase) {

  /** The {@link #tierBCase()} of a row no Tier B case exercises yet. */
  public static final String NO_TIER_B_CASE = "none";

  public FrameworkRow {
    if (!Kebab.isKebab(id)) {
      throw new IllegalArgumentException("a row id is kebab-case, got " + id);
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("a row names its framework");
    }
    if (marker == null || module == null || wiring == null || check == null) {
      throw new IllegalArgumentException("row " + id + " leaves a column empty");
    }
    if (tierBCase == null || tierBCase.isBlank()) {
      throw new IllegalArgumentException("row " + id + " names a Tier B case or none");
    }
    if (check instanceof CheckBinding.WiringCheck && !(wiring instanceof Wiring.Snippet)) {
      throw new IllegalArgumentException(
          "row " + id + " earns a wiring check only for wiring the doctor can see in source");
    }
  }
}
