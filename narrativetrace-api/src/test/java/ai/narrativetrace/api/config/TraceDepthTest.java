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
package ai.narrativetrace.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceDepthTest {

  @Test
  void parsesTheNamesTolerantOfCaseAndWhitespace() {
    assertThat(TraceDepth.fromName(" Internal ", TraceDepth.PUBLIC)).isEqualTo(TraceDepth.INTERNAL);
    assertThat(TraceDepth.fromName("public", TraceDepth.INTERNAL)).isEqualTo(TraceDepth.PUBLIC);
  }

  @Test
  void anythingUnrecognisedFallsBackSoATypoNeverTurnsDepthOn() {
    assertThat(TraceDepth.fromName(null, TraceDepth.PUBLIC)).isEqualTo(TraceDepth.PUBLIC);
    assertThat(TraceDepth.fromName("  ", TraceDepth.PUBLIC)).isEqualTo(TraceDepth.PUBLIC);
    assertThat(TraceDepth.fromName("deep", TraceDepth.PUBLIC)).isEqualTo(TraceDepth.PUBLIC);
  }
}
