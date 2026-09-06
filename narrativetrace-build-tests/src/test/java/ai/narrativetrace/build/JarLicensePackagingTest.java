/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/**
 * Proves the licence is inside the artifact, not just at the repository root.
 *
 * <p>A Maven jar is a copy of the Licensed Work handed to someone who may never see this
 * repository. Apache-2.0 §4(a) requires that recipient to get a copy of the License with it; BSL
 * 1.1 requires the License to be "conspicuously displayed on each original or modified copy of the
 * Licensed Work". The {@code narrativetrace-license-packaging} convention plugin puts both texts,
 * plus NOTICE, into every jar's {@code META-INF}; this test opens the real archives and reads them
 * back.
 *
 * <p>Two modules, because the interesting fact is that the choice is made from {@code
 * licensing.properties} rather than hardcoded: {@code narrativetrace-api} is the one {@code open}
 * module and must carry the Apache text, {@code narrativetrace-core} is {@code free} and must carry
 * the BSL text — filled, since the committed LICENSE is a template whose Licensed Work version and
 * Change Date are per-version parameters. The build file makes both jars a dependency of this test.
 *
 * <p>Not asserted here: that every other published module gets the same treatment. That is what the
 * per-module {@code verifyJarLicense} task in the same convention plugin covers, on every module
 * that applies it, without a list anyone has to remember to extend.
 */
class JarLicensePackagingTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));

  /** The version the jars are named after — the working tree's, {@code -SNAPSHOT} and all. */
  private static final String BUILD_VERSION = System.getProperty("narrativetrace.buildVersion");

  /** The version a licence may name: a Change Date attaches to a release, never to a snapshot. */
  private static final String RELEASE_VERSION = BUILD_VERSION.replace("-SNAPSHOT", "");

  private File jarOf(String module) {
    var jar =
        new File(PROJECT_DIR, module + "/build/libs/" + module + "-" + BUILD_VERSION + ".jar");
    assertThat(jar)
        .as("%s's jar — the test task declares it as a dependency, so it must exist here", module)
        .isFile();
    return jar;
  }

  private String entry(File jar, String name) throws IOException {
    try (var zip = new ZipFile(jar)) {
      ZipEntry found = zip.getEntry(name);
      assertThat(found).as("%s in %s", name, jar.getName()).isNotNull();
      try (var stream = zip.getInputStream(found)) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
  }

  private boolean hasEntry(File jar, String name) throws IOException {
    try (var zip = new ZipFile(jar)) {
      return zip.getEntry(name) != null;
    }
  }

  private String rootFile(String name) throws IOException {
    return Files.readString(new File(PROJECT_DIR, name).toPath(), StandardCharsets.UTF_8);
  }

  // --- the open module: Apache 2.0 ----------------------------------------

  @Test
  void apiJarCarriesTheApacheLicenceVerbatim() throws IOException {
    var jar = jarOf("narrativetrace-api");

    assertThat(entry(jar, "META-INF/LICENSE-APACHE"))
        .as("byte-for-byte the repository's Apache text — a licence is not paraphrasable")
        .isEqualTo(rootFile("LICENSE-APACHE"));
  }

  @Test
  void apiJarDoesNotCarryTheRuntimeLicence() throws IOException {
    // The whole point of the open contract: a consumer of the api jar is never handed BSL terms.
    var jar = jarOf("narrativetrace-api");

    assertThat(hasEntry(jar, "META-INF/LICENSE")).isFalse();
    assertThat(entry(jar, "META-INF/LICENSE-APACHE")).doesNotContain("Business Source License");
  }

  // --- a free module: BSL 1.1, filled -------------------------------------

  @Test
  void coreJarCarriesTheBusinessSourceLicence() throws IOException {
    var jar = jarOf("narrativetrace-core");

    assertThat(entry(jar, "META-INF/LICENSE"))
        .startsWith("Business Source License 1.1")
        .containsPattern("Change License: +Apache License, Version 2\\.0");
    assertThat(hasEntry(jar, "META-INF/LICENSE-APACHE")).isFalse();
  }

  @Test
  void coreJarCarriesNoUnfilledPlaceholder() throws IOException {
    // The committed LICENSE is a template. Packaged unfilled it would state no version and no
    // Change Date while looking exactly like a licence that did.
    assertThat(entry(jarOf("narrativetrace-core"), "META-INF/LICENSE")).doesNotContain("{{");
  }

  @Test
  void coreJarsLicenceNamesThisVersionAndItsChangeDate() throws IOException {
    var licence = entry(jarOf("narrativetrace-core"), "META-INF/LICENSE");

    assertThat(licence)
        .as("the Licensed Work parameter names the release this jar is a build of")
        .contains("version " + RELEASE_VERSION + ".");
    // The expected date derives from the SOURCE LICENSE's state, never from the
    // clock: in the private tree the template's {{CHANGE_DATE}} is filled at jar
    // time (today + 4y), but a published snapshot carries the release's frozen
    // literal date — asserting now()+4y there goes red one day after the release
    // and stays red forever (caught by the 0.2.0 pre-push audit).
    var rootLicence = rootFile("LICENSE");
    var expectedChangeDate =
        rootLicence.contains("{{CHANGE_DATE}}")
            ? LocalDate.now().plusYears(4).toString()
            : changeDateLiteralIn(rootLicence);
    assertThat(licence)
        .as("the Change Date parameter is a concrete date, as BSL requires")
        .contains("(" + expectedChangeDate + " for this version)");
  }

  /** The frozen literal a published snapshot's LICENSE carries in its Change Date parameter. */
  private static String changeDateLiteralIn(String licence) {
    var m =
        java.util.regex.Pattern.compile("\\((\\d{4}-\\d{2}-\\d{2}) for this version\\)")
            .matcher(licence);
    assertThat(m.find()).as("a filled LICENSE names its Change Date").isTrue();
    return m.group(1);
  }

  // --- NOTICE, in both -----------------------------------------------------

  @Test
  void bothJarsCarryTheNoticeVerbatim() throws IOException {
    var notice = rootFile("NOTICE");

    assertThat(entry(jarOf("narrativetrace-api"), "META-INF/NOTICE")).isEqualTo(notice);
    assertThat(entry(jarOf("narrativetrace-core"), "META-INF/NOTICE")).isEqualTo(notice);
  }

  @Test
  void theNoticeInsideAJarStillExplainsWhichLicenceCoversWhat() throws IOException {
    // NOTICE is the map: without it, a reader of the api jar has an Apache text and no way to know
    // the runtime beside it is BSL, or that the schemas are carved out.
    assertThat(entry(jarOf("narrativetrace-api"), "META-INF/NOTICE"))
        .contains("ai.narrativetrace:narrativetrace-api")
        .contains("Apache License, Version 2.0 — see LICENSE-APACHE.")
        .contains("Business Source License 1.1 — see LICENSE.");
  }
}
