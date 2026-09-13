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
package com.example;

/**
 * Wrapped with {@code NarrativeTraceProxy.trace(...)} elsewhere in the real project this fixture
 * stands in for — {@code authToken} is a recognized sensitive parameter name. Deliberately no test
 * anywhere in this fixture asserts the literal {@code "[REDACTED]"}: the gap the deviation case
 * exists to catch.
 */
public class PaymentService {

  public String charge(String authToken, String amount) {
    return "charged " + amount;
  }
}
