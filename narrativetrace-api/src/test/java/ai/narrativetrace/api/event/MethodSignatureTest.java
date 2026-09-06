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
package ai.narrativetrace.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MethodSignatureTest {

  @Test
  void capturesClassNameMethodNameAndEmptyParams() {
    var signature = new MethodSignature("OrderService", "placeOrder", List.of());

    assertThat(signature.className()).isEqualTo("OrderService");
    assertThat(signature.methodName()).isEqualTo("placeOrder");
    assertThat(signature.parameters()).isEmpty();
  }

  @Test
  void holdsListOfParameters() {
    var params =
        List.of(
            new ParameterCapture("customerId", "\"C-123\"", false),
            new ParameterCapture("amount", "99.95", false));
    var signature = new MethodSignature("OrderService", "placeOrder", params);

    assertThat(signature.parameters()).hasSize(2);
    assertThat(signature.parameters().get(0).name()).isEqualTo("customerId");
    assertThat(signature.parameters().get(1).name()).isEqualTo("amount");
  }

  @Test
  void holdsOptionalNarrationAndErrorContext() {
    var signature =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(),
            "Placing order for customer C-123",
            "Context: charging customer C-123");

    assertThat(signature.narration()).isEqualTo("Placing order for customer C-123");
    assertThat(signature.errorContext()).isEqualTo("Context: charging customer C-123");
  }

  @Test
  void narrationAndErrorContextDefaultToNull() {
    var signature = new MethodSignature("OrderService", "placeOrder", List.of());

    assertThat(signature.narration()).isNull();
    assertThat(signature.errorContext()).isNull();
  }

  @Test
  void holdsOptionalPackageName() {
    var signature =
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), null, null, null, "com.acme.billing");

    assertThat(signature.packageName()).isEqualTo("com.acme.billing");
  }

  @Test
  void packageNameDefaultsToNullFromEveryCompatibilityConstructor() {
    var threeArg = new MethodSignature("OrderService", "placeOrder", List.of());
    var fiveArg = new MethodSignature("OrderService", "placeOrder", List.of(), "n", "e");
    var sixArg = new MethodSignature("OrderService", "placeOrder", List.of(), "n", "e", "t");

    assertThat(threeArg.packageName()).isNull();
    assertThat(fiveArg.packageName()).isNull();
    assertThat(sixArg.packageName()).isNull();
  }
}
