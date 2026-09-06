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
package ai.narrativetrace.api.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.tree.TraceTree;
import java.util.List;
import org.junit.jupiter.api.Test;

class NamedTraceTest {

  private static final TraceTree EMPTY_TREE =
      new TraceTree() {
        @Override
        public List<ai.narrativetrace.api.event.TraceNode> roots() {
          return List.of();
        }

        @Override
        public boolean isEmpty() {
          return true;
        }
      };

  @Test
  void carriesTheScenarioLabelAlongsideTheTree() {
    var trace = new NamedTrace("places an order", EMPTY_TREE);

    assertThat(trace.name()).isEqualTo("places an order");
    assertThat(trace.tree()).isSameAs(EMPTY_TREE);
  }

  @Test
  void acceptsAnEmptyTreeBecauseAScenarioMayCaptureNothing() {
    assertThat(new NamedTrace("captured nothing", EMPTY_TREE).tree().isEmpty()).isTrue();
  }

  @Test
  void rejectsNullName() {
    assertThatThrownBy(() -> new NamedTrace(null, EMPTY_TREE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void rejectsBlankName() {
    assertThatThrownBy(() -> new NamedTrace("   ", EMPTY_TREE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void rejectsNullTree() {
    assertThatThrownBy(() -> new NamedTrace("named", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tree");
  }
}
