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

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.cli.doctor.DoctorSnapshot;
import ai.narrativetrace.cli.doctor.Finding;
import org.junit.jupiter.api.Test;

class ParameterArg0CheckTest {

  private final ParameterArg0Check check = new ParameterArg0Check();

  @Test
  void passesWithNoRenderedOutput() {
    Finding f = check.run(DoctorSnapshot.healthy());
    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
    assertThat(f.id()).isEqualTo(ParameterArg0Check.ID);
  }

  @Test
  void passesWithCleanlyNamedRenderedOutput() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder()
            .putOutputFile("build/narrativetrace/trace.nt", "placeOrder(orderId, total)")
            .build();
    assertThat(check.run(s).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void failsWhenRenderedOutputShowsArg0() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder()
            .putOutputFile("build/narrativetrace/trace.nt", "placeOrder(arg0, arg1)")
            .build();
    Finding f = check.run(s);
    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("build/narrativetrace/trace.nt");
    assertThat(f.fix()).contains("-parameters");
  }

  @Test
  void listsMultipleOffendingFilesSorted() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder()
            .putOutputFile("b.nt", "call(arg0)")
            .putOutputFile("a.nt", "call(arg3)")
            .build();
    Finding f = check.run(s);
    assertThat(f.message()).contains("a.nt, b.nt");
  }
}
