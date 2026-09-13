/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.core.render.TraceNamer;
import org.junit.jupiter.api.Test;

class RunIdentityTest {

  @Test
  void generateProducesAWellFormedHexId() {
    var identity = RunIdentity.generate();

    assertThat(identity.id()).hasSize(32).matches("[0-9a-f]{32}");
  }

  @Test
  void generateDerivesTheNameFromTheSameTablesAsTraceNamer() {
    var identity = RunIdentity.generate();

    assertThat(identity.name()).isEqualTo(TraceNamer.name(identity.id()));
  }

  @Test
  void generateProducesAThreeWordPhrase() {
    var identity = RunIdentity.generate();

    assertThat(identity.name().split(" ")).hasSize(3);
  }

  @Test
  void twoGenerationsDifferInPractice() {
    var first = RunIdentity.generate();
    var second = RunIdentity.generate();

    assertThat(first.id()).isNotEqualTo(second.id());
  }

  @Test
  void rejectsNullId() {
    assertThatThrownBy(() -> new RunIdentity(null, "bold elk soars"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  void rejectsEmptyId() {
    assertThatThrownBy(() -> new RunIdentity("", "bold elk soars"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  void rejectsNullName() {
    assertThatThrownBy(() -> new RunIdentity("a".repeat(32), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void rejectsEmptyName() {
    assertThatThrownBy(() -> new RunIdentity("a".repeat(32), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void sameIdAlwaysProducesTheSameName() {
    var hex = "a3f7c1b290de4f8801234567deadbeef";

    var one = new RunIdentity(hex, TraceNamer.name(hex));
    var two = new RunIdentity(hex, TraceNamer.name(hex));

    assertThat(one).isEqualTo(two);
  }
}
