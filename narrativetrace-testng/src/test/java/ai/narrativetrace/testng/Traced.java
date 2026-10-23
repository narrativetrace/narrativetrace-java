/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import ai.narrativetrace.api.event.MethodSignature;
import java.util.List;

/** One traced call into the listener's shared context — what every fixture's test body does. */
final class Traced {

  private Traced() {}

  /** Enters and leaves {@code className.methodName}, returning {@code result}. */
  static void call(String className, String methodName, String result) {
    var context = NarrativeTraceListener.context();
    context.enterMethod(new MethodSignature(className, methodName, List.of()));
    context.exitMethodWithReturn(result);
  }

  /** Enters {@code className.methodName} and leaves it by throwing {@code thrown}. */
  static void fails(String className, String methodName, RuntimeException thrown) {
    var context = NarrativeTraceListener.context();
    context.enterMethod(new MethodSignature(className, methodName, List.of()));
    context.exitMethodWithException(thrown, null);
  }
}
