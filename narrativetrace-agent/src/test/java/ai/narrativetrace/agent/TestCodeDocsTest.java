/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import org.junit.jupiter.api.Test;

/**
 * List 3's rule is published in the installation guide: the annotation packages and superclasses
 * are asserted against the constants {@link TestCodeClasses} matches with, and the example notice
 * against the line the agent really prints — never typed twice.
 */
class TestCodeDocsTest {

  private static final Path INSTALLATION_GUIDE =
      Path.of(System.getProperty("projectDir", "."), "documentation", "installation-guide.md");

  private static String dotted(String internalName) {
    return internalName.replace('/', '.').replaceAll("\\.$", "");
  }

  @Test
  void everyAnnotationPackageAndSuperclassTheCodeMatchesIsPublished() throws IOException {
    var guide = Files.readString(INSTALLATION_GUIDE);

    for (var annotationPackage : TestCodeClasses.TEST_ANNOTATION_PACKAGES) {
      assertThat(guide).contains("`" + dotted(annotationPackage) + "`");
    }
    for (var superclass : TestCodeClasses.TEST_SUPERCLASSES) {
      assertThat(guide).contains("`" + dotted(superclass) + "`");
    }
    assertThat(guide).contains("tests=skip|trace").contains("narrativetrace.tests");
  }

  @Test
  void theExampleNoticeIsTheLineTheAgentPrints() throws Exception {
    var transformer = new NarrativeClassFileTransformer(AgentConfig.parse("packages=com.example"));
    var domain =
        new ProtectionDomain(
            new CodeSource(
                new URL("file:/work/app/target/test-classes/"),
                (java.security.cert.Certificate[]) null),
            null);

    var printed = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(printed, true, StandardCharsets.UTF_8));
    try {
      transformer.transform(null, "com/example/ServiceTest", null, domain, new byte[0]);
    } finally {
      System.setErr(originalErr);
    }

    assertThat(Files.readString(INSTALLATION_GUIDE))
        .contains(printed.toString(StandardCharsets.UTF_8).strip());
  }
}
