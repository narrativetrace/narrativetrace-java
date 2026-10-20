/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Opens a built OpenAI plugin ZIP and checks it against the layout the catalogue derived: its
 * entries equal {@link OpenAiPackageLayout#entries}, its version is semantic and matches the
 * release, every {@code "./"}-relative path the manifest names resolves to a packaged entry, each
 * icon the manifest points at is square and within the vendor's size bounds, and no forbidden entry
 * ({@code hooks/}, {@code mcp.json}, a {@code .app.json} or a {@code .class}) is present. {@code
 * checkOpenAiPluginZip} (the Gradle gate) runs this; {@code OpenAiPackageCheckTest} exercises every
 * branch directly against hand-built zips, so a broken package fails before anyone uploads it.
 */
public final class OpenAiPackageCheck {

  private static final Pattern RELATIVE_PATH = Pattern.compile("\"\\./([^\"]+)\"");
  private static final Pattern VERSION_LINE = Pattern.compile("\"version\": \"([^\"]*)\"");
  private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
  private static final int MIN_ICON_PX = 48;
  private static final long MAX_ICON_BYTES = 5L * 1024 * 1024;
  private static final String PLUGIN_JSON = "plugin.json";

  private OpenAiPackageCheck() {}

  public static List<String> violations(
      Path zip, List<String> expectedEntries, String expectedVersion) throws IOException {
    List<String> problems = new ArrayList<>();
    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
      List<String> actual = sortedEntryNames(zipFile);
      checkEntrySet(expectedEntries, actual, problems);
      checkForbiddenEntries(actual, problems);
      String manifest = readEntry(zipFile, PLUGIN_JSON);
      checkVersion(manifest, expectedVersion, problems);
      checkRelativePaths(manifest, actual, problems);
      checkIcons(zipFile, manifest, problems);
    }
    return List.copyOf(problems);
  }

  /**
   * Directory entries (name ends with {@code /}) are a ZIP-format artifact, not packaged content.
   */
  private static List<String> sortedEntryNames(ZipFile zipFile) {
    List<String> names = new ArrayList<>();
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (!entry.isDirectory()) {
        names.add(entry.getName());
      }
    }
    names.sort(String::compareTo);
    return names;
  }

  private static void checkEntrySet(
      List<String> expected, List<String> actual, List<String> problems) {
    List<String> expectedSorted = new ArrayList<>(expected);
    expectedSorted.sort(String::compareTo);
    if (!expectedSorted.equals(actual)) {
      problems.add(
          "entries do not match the layout — expected " + expectedSorted + " but found " + actual);
    }
  }

  private static void checkForbiddenEntries(List<String> actual, List<String> problems) {
    for (String name : actual) {
      if (isForbidden(name)) {
        problems.add("forbidden entry present: " + name);
      }
    }
  }

  private static boolean isForbidden(String name) {
    return name.startsWith("hooks/")
        || name.equals("mcp.json")
        || name.endsWith(".app.json")
        || name.endsWith(".class");
  }

  private static void checkVersion(String manifest, String expectedVersion, List<String> problems) {
    if (!SEMVER.matcher(expectedVersion).matches()) {
      problems.add("version '" + expectedVersion + "' is not semantic (X.Y.Z, no -SNAPSHOT)");
    }
    Matcher versionLine = VERSION_LINE.matcher(manifest);
    if (!versionLine.find()) {
      problems.add("plugin.json carries no \"version\" key");
    } else if (!versionLine.group(1).equals(expectedVersion)) {
      problems.add(
          "plugin.json version '" + versionLine.group(1) + "' does not equal " + expectedVersion);
    }
  }

  private static void checkRelativePaths(
      String manifest, List<String> actual, List<String> problems) {
    Set<String> actualSet = new TreeSet<>(actual);
    Matcher paths = RELATIVE_PATH.matcher(manifest);
    while (paths.find()) {
      String path = paths.group(1);
      if (!actualSet.contains(path)) {
        problems.add("manifest path './" + path + "' resolves to no packaged entry");
      }
    }
  }

  private static void checkIcons(ZipFile zipFile, String manifest, List<String> problems)
      throws IOException {
    Matcher paths = RELATIVE_PATH.matcher(manifest);
    while (paths.find()) {
      String path = paths.group(1);
      if (path.endsWith(".png")) {
        checkIcon(zipFile, path, problems);
      }
    }
  }

  private static void checkIcon(ZipFile zipFile, String path, List<String> problems)
      throws IOException {
    ZipEntry entry = zipFile.getEntry(path);
    if (entry == null) {
      return; // already reported by checkRelativePaths
    }
    byte[] bytes = readBytes(zipFile, entry);
    if (bytes.length > MAX_ICON_BYTES) {
      problems.add(path + " is larger than 5 MiB");
      return;
    }
    checkIconDimensions(path, bytes, problems);
  }

  private static void checkIconDimensions(String path, byte[] bytes, List<String> problems) {
    int width = PngDimensions.width(bytes);
    int height = PngDimensions.height(bytes);
    if (width != height) {
      problems.add(path + " is not square (" + width + "x" + height + ")");
    } else if (width < MIN_ICON_PX) {
      problems.add(path + " is smaller than 48px (" + width + "px)");
    }
  }

  private static String readEntry(ZipFile zipFile, String name) throws IOException {
    ZipEntry entry = zipFile.getEntry(name);
    if (entry == null) {
      throw new IllegalStateException(name + " missing from the zip");
    }
    return new String(readBytes(zipFile, entry), StandardCharsets.UTF_8);
  }

  private static byte[] readBytes(ZipFile zipFile, ZipEntry entry) throws IOException {
    try (InputStream in = zipFile.getInputStream(entry)) {
      return in.readAllBytes();
    }
  }
}
