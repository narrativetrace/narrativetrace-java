/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

/**
 * The documented {@code @edgeCase} of {@code NarrativeTraceListener.artifactIdentity}: the row
 * index of a data-driven invocation is read from TestNG's own result class, and a result of any
 * other class falls back to the run's start-order counter. TestNG's listener interfaces are public,
 * so a harness, an IDE or another listener's wrapper can hand the callback an {@code ITestResult}
 * this module did not build — and then every invocation must still own a number of its own, since
 * the number is what keeps one invocation's artifacts and baseline off another's.
 */
class ForeignResultIdentityTest {

  @Test
  void aDataDrivenResultOfAForeignClassIsNumberedByStartOrder() {
    var run = TestNgRun.current();

    var first = NarrativeTraceListener.artifactIdentity(foreignResult("countedByStartOrder"), run);
    var second = NarrativeTraceListener.artifactIdentity(foreignResult("countedByStartOrder"), run);

    assertThat(first.invocationIndex()).isOne();
    assertThat(second.invocationIndex())
        .as("the fallback is a counter, so the second invocation is not the first's")
        .isEqualTo(2);
  }

  @Test
  void theStartOrderCounterIsKeptPerMethod() {
    var run = TestNgRun.current();
    NarrativeTraceListener.artifactIdentity(foreignResult("countedOnItsOwn"), run);
    NarrativeTraceListener.artifactIdentity(foreignResult("countedOnItsOwn"), run);

    var other = NarrativeTraceListener.artifactIdentity(foreignResult("countedSeparately"), run);

    assertThat(other.invocationIndex())
        .as("another method's repetitions are counted on their own")
        .isOne();
    assertThat(other.methodName()).isEqualTo("countedSeparately");
  }

  /** An {@code ITestResult} that is data-driven and is not TestNG's own {@code TestResult}. */
  private static ITestResult foreignResult(String methodName) {
    var method = dataDrivenMethod(methodName);
    return (ITestResult)
        Proxy.newProxyInstance(
            ForeignResultIdentityTest.class.getClassLoader(),
            new Class<?>[] {ITestResult.class},
            handler(
                call ->
                    switch (call) {
                      case "getMethod" -> method;
                      default -> null;
                    }));
  }

  private static ITestNGMethod dataDrivenMethod(String methodName) {
    return (ITestNGMethod)
        Proxy.newProxyInstance(
            ForeignResultIdentityTest.class.getClassLoader(),
            new Class<?>[] {ITestNGMethod.class},
            handler(
                call ->
                    switch (call) {
                      case "getRealClass" -> RepeatedRowFixture.class;
                      case "getMethodName" -> methodName;
                      case "isDataDriven" -> Boolean.TRUE;
                      case "getInvocationCount" -> 2;
                      default -> null;
                    }));
  }

  private static InvocationHandler handler(java.util.function.Function<String, Object> answers) {
    return (proxy, method, args) -> answers.apply(method.getName());
  }
}
