/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.agent.TestCodeClasses.Origin;
import java.net.URL;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The location rules of list 3 on their own: awkward layouts that are clumsy through the
 * transformer.
 */
class TestCodeClassesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/work/app/target/test-classes/",
        "/work/app/out/test/app/",
        "/work/app/out/test/classes",
        "/work/app/build/classes/java/test",
        "/work/app/build/classes/kotlin/test/",
        "/work/app/build/classes/scala/test/",
        "/work/app/build/classes/java/testFixtures/",
        "/work/app/build/classes/java/testIntegration/",
        "/work/app/build/classes/java/integrationTest/",
        "/work/app/build/classes/java/functionalTests/",
        "/work/app/target/scala-2.13/test-classes/",
        "/repo/lib/app-1.0-tests.jar",
        "/repo/lib/app-1.0-test-fixtures.jar",
        "C:/work/app/target/test-classes/",
        "C:\\work\\app\\build\\classes\\java\\test\\",
      })
  void aRecognisedTestLocationIsTestOutput(String path) {
    assertThat(TestCodeClasses.originOfPath(path)).isEqualTo(Origin.TEST);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/work/app/target/classes/",
        "/work/app/target/classes",
        "/work/app/build/classes/java/main/",
        "/work/app/build/classes/kotlin/main/",
        "/work/app/out/production/app/",
        "C:\\work\\app\\target\\classes\\",
        "/w/classes/java/test/proj/build/classes/java/main/",
        "/w/classes/java/test/proj/target/classes/",
        "/home/u/out/test/myapp/target/classes/",
        "/srv/test-classes/app/target/classes/",
        "/work/app/out/production/classes",
        "/work/app/out/production/mymodule",
        "C:\\out\\test\\app\\target\\classes",
      })
  void aRecognisedProductionLocationIsProductionOutput(String path) {
    assertThat(TestCodeClasses.originOfPath(path)).isEqualTo(Origin.PRODUCTION);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/repo/lib/guava-33.0.jar",
        "/repo/lib/app-1.0.jar",
        "/work/app/somewhere/else/",
        "/work/app/build/classes/java/latest/",
        "/work/app/build/classes/java/contest/",
        "/work/app/build/classes/java/testing/",
        "/work/test/app/",
        "/home/u/out/test/myapp/bin/",
        "/work/app/out/test",
        "",
      })
  void anythingElseIsUnknownOrigin(String path) {
    assertThat(TestCodeClasses.originOfPath(path)).isEqualTo(Origin.UNKNOWN);
  }

  @org.junit.jupiter.api.Test
  void aMissingLocationIsUnknownOrigin() {
    assertThat(TestCodeClasses.originOfPath(null)).isEqualTo(Origin.UNKNOWN);
  }

  @org.junit.jupiter.api.Test
  void aFileUrlIsClassifiedByItsPath() throws Exception {
    assertThat(TestCodeClasses.originOf(new URL("file:/work/app/target/test-classes/")))
        .isEqualTo(Origin.TEST);
    assertThat(TestCodeClasses.originOf(new URL("file:/C:/work/app/target/classes/")))
        .isEqualTo(Origin.PRODUCTION);
    assertThat(TestCodeClasses.originOf(null)).isEqualTo(Origin.UNKNOWN);
  }
}
