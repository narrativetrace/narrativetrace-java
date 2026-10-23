/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testng.ITestNGListener;

/**
 * The adopter adds the dependency and nothing else: TestNG's service loader registers the listener.
 * {@code @Listeners} registers it too, and both at once still write each test once.
 */
class RegistrationTest {

  @Test
  void theServiceFileNamesTheListener() throws Exception {
    try (InputStream in =
        getClass()
            .getClassLoader()
            .getResourceAsStream("META-INF/services/org.testng.ITestNGListener")) {
      assertThat(in).isNotNull();
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8).strip())
          .isEqualTo(NarrativeTraceListener.class.getName());
    }
    assertThat(ServiceLoader.load(ITestNGListener.class))
        .anyMatch(listener -> listener instanceof NarrativeTraceListener);
  }

  @Test
  void theServiceLoaderAloneRegistersIt(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(outputDir.resolve("traces/OrderFixture/customer_places_order.md")).exists();
    assertThat(outputDir.resolve("manifest.json")).exists();
  }

  @Test
  void theListenersAnnotationAloneRegistersIt(@TempDir Path outputDir) throws Exception {
    var withoutSpi = TestNgRuns.withoutServiceLoaderListeners();
    var plain = outputDir.resolve("plain");
    var annotated = outputDir.resolve("annotated");

    TestNgRuns.run(plain, Map.of(), withoutSpi, OrderFixture.class);
    var run = TestNgRuns.run(annotated, Map.of(), withoutSpi, AnnotatedFixture.class);

    assertThat(TestNgRuns.listing(plain))
        .as("the control: with the service loader hidden, an unannotated class writes nothing")
        .isEmpty();
    assertThat(annotated.resolve("traces/AnnotatedFixture/parcel_is_weighed.md")).exists();
    assertThat(annotated.resolve("manifest.json")).exists();
    assertThat(run.out()).contains("NarrativeTrace — Suite complete");
  }

  @Test
  void bothRegistrationsWriteEachTestOnce(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, AnnotatedFixture.class);

    var manifest = Files.readString(outputDir.resolve("manifest.json"));
    assertThat(manifest.split("parcel_is_weighed.md", -1)).hasSize(2);
    assertThat(run.out().split("NarrativeTrace — Suite complete", -1)).hasSize(2);
  }
}
