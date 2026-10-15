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
package ai.narrativetrace.tooling.platforms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformRowContractTest {

  private static PlatformRow row(String id, List<String> markers, String verified) {
    return new PlatformRow(
        id, "Name", markers, new Pointer.None(), "install", "finds", "manual", verified);
  }

  @Test
  void aWellFormedRowIsBuilt() {
    assertThat(row("some-platform", List.of(".some"), "2026-10-10").id())
        .isEqualTo("some-platform");
  }

  @Test
  void aRowIdMustBeKebabCase() {
    assertThatThrownBy(() -> row("Some_Platform", List.of(".x"), "2026-10-10"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kebab-case");
  }

  @Test
  void aRowWithNoMarkerPathCouldNeverBeDetected() {
    assertThatThrownBy(() -> row("x", List.of(), "2026-10-10"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no marker path");
  }

  @Test
  void aVerificationDateMustBeADate() {
    assertThatThrownBy(() -> row("x", List.of(".x"), "last week"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("yyyy-mm-dd");
    assertThatThrownBy(() -> row("x", List.of(".x"), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyColumnMustBeFilled() {
    assertThatThrownBy(
            () ->
                new PlatformRow(
                    "x", " ", List.of(".x"), new Pointer.None(), "i", "f", "manual", "2026-10-10"))
        .hasMessageContaining("name");
    assertThatThrownBy(
            () -> new PlatformRow("x", "X", List.of(".x"), null, "i", "f", "manual", "2026-10-10"))
        .hasMessageContaining("pointer");
    assertThatThrownBy(
            () ->
                new PlatformRow(
                    "x", "X", List.of(".x"), new Pointer.None(), "", "f", "manual", "2026-10-10"))
        .hasMessageContaining("install path");
    assertThatThrownBy(
            () ->
                new PlatformRow(
                    "x", "X", List.of(".x"), new Pointer.None(), "i", "", "manual", "2026-10-10"))
        .hasMessageContaining("agent-finds");
    assertThatThrownBy(
            () ->
                new PlatformRow(
                    "x",
                    "X",
                    List.of(".x"),
                    new Pointer.None(),
                    "i",
                    "f",
                    "Not A Lane",
                    "2026-10-10"))
        .hasMessageContaining("lane");
  }

  @Test
  void aPointerPathStaysInsideTheProject() {
    assertThatThrownBy(() -> new Pointer.ManagedSection("/etc/replit.md"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Pointer.ManagedSection("../replit.md"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Pointer.Rule(" ", "text"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Pointer.Rule(".cursor/rules/x.mdc", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theNoPointerPointsAtNoFile() {
    assertThat(new Pointer.None().path()).isEmpty();
  }
}
