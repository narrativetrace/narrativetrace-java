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
package ai.narrativetrace.tooling.doctor;

import ai.narrativetrace.tooling.init.InstalledSkill;
import ai.narrativetrace.tooling.init.SkillFlavour;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Everything the doctor checks read, gathered once, read-only, zero network. A check is a pure
 * function over a {@code DoctorSnapshot}, which is why every check has both a passing and a failing
 * unit test with no disk I/O at all — the same discipline the TypeScript reference applies ({@code
 * packages/cli/src/doctor/types.ts}).
 *
 * <p>Built two ways: {@link SnapshotBuilder} walks a real project directory (the {@code java -jar}
 * launcher and the Gradle {@code narrativetraceDoctor} task both use it); tests build one directly
 * with {@link Builder}, starting from {@link #healthy()} and overriding only what a case needs.
 *
 * <p><b>@llmNote</b> The agent-skills half of the snapshot ({@link #installedSkills()}, {@link
 * #carrierCoordinate()}, {@link #catalogueSkills()}) is the installer's own vocabulary, read by the
 * installer's own {@code ProjectStateReader} — there is one reader of a project's skill
 * directories, not a doctor-shaped second copy of it that could disagree about what "ours" means.
 */
public final class DoctorSnapshot {

  /** The carrier {@link #healthy()} resolves, and the skills it has installed from it. */
  private static final String HEALTHY_CARRIER = "ai.narrativetrace:narrativetrace-skills:0.2.2";

  private static final List<String> HEALTHY_SKILLS =
      List.of("narrativetrace-doctor", "add-narrative-tracing");

  /** A version catalog entry's {@code group = "…"}. */
  private static final Pattern CATALOG_GROUP = Pattern.compile("\\bgroup\\s*=\\s*\"([^\"]+)\"");

  /** A version catalog entry's {@code name = "…"}. */
  private static final Pattern CATALOG_NAME = Pattern.compile("\\bname\\s*=\\s*\"([^\"]+)\"");

  /** A version catalog plugin entry: {@code { id = "ai.narrativetrace", version = "<v>" }}. */
  private static final Pattern NARRATIVETRACE_CATALOG_PLUGIN_VERSION =
      Pattern.compile("\"ai\\.narrativetrace\",\\s*version\\s*=\\s*\"([\\w.+\\-]+)\"");

  /** What a fix line pins a module to when the project declares no NarrativeTrace version. */
  public static final String VERSION_PLACEHOLDER = "<your NarrativeTrace version>";

  /** A NarrativeTrace coordinate with its version: {@code "ai.narrativetrace:<artifact>:<v>"}. */
  private static final Pattern NARRATIVETRACE_COORDINATE_VERSION =
      Pattern.compile("[\"']ai\\.narrativetrace:[\\w.\\-]+:([\\w.+\\-]+)[\"']");

  /** The plugin with its version: {@code id("ai.narrativetrace") version "<v>"}, either DSL. */
  private static final Pattern NARRATIVETRACE_PLUGIN_VERSION =
      Pattern.compile(
          "[\"']ai\\.narrativetrace[\"']\\s*\\)?\\s*version\\s*\\(?\\s*[\"']([\\w.+\\-]+)[\"']");

  private final String runningJavaVersion;
  private final Map<String, String> env;
  private final Map<String, String> systemProperties;
  private final Map<String, String> gradleProperties;
  private final String buildFileContent;
  private final Set<String> declaredDependencyCoordinates;
  private final boolean launcherOnTestRuntimeOnly;
  private final boolean compilerArgsDeclareParameters;
  private final Map<String, String> sourceFiles;
  private final Map<String, String> manifestFiles;
  private final Map<String, String> resourceFiles;
  private final Map<String, String> outputFiles;
  private final Map<String, String> approvalDirFiles;
  private final boolean extensionRegisteredViaServiceLoader;
  private final boolean extensionRegisteredViaExtendWith;
  private final List<InstalledSkill> installedSkills;
  private final String carrierCoordinate;
  private final List<String> catalogueSkills;

  private DoctorSnapshot(Builder b) {
    this.runningJavaVersion = b.runningJavaVersion;
    this.env = Map.copyOf(b.env);
    this.systemProperties = Map.copyOf(b.systemProperties);
    this.gradleProperties = Map.copyOf(b.gradleProperties);
    this.buildFileContent = b.buildFileContent;
    this.declaredDependencyCoordinates = Set.copyOf(b.declaredDependencyCoordinates);
    this.launcherOnTestRuntimeOnly = b.launcherOnTestRuntimeOnly;
    this.compilerArgsDeclareParameters = b.compilerArgsDeclareParameters;
    this.sourceFiles = Map.copyOf(b.sourceFiles);
    this.manifestFiles = Map.copyOf(b.manifestFiles);
    this.resourceFiles = Map.copyOf(b.resourceFiles);
    this.outputFiles = Map.copyOf(b.outputFiles);
    this.approvalDirFiles = Map.copyOf(b.approvalDirFiles);
    this.extensionRegisteredViaServiceLoader = b.extensionRegisteredViaServiceLoader;
    this.extensionRegisteredViaExtendWith = b.extensionRegisteredViaExtendWith;
    this.installedSkills = List.copyOf(b.installedSkills);
    this.carrierCoordinate = b.carrierCoordinate;
    this.catalogueSkills = List.copyOf(b.catalogueSkills);
  }

  public String runningJavaVersion() {
    return runningJavaVersion;
  }

  public Map<String, String> env() {
    return env;
  }

  public Map<String, String> systemProperties() {
    return systemProperties;
  }

  public Map<String, String> gradleProperties() {
    return gradleProperties;
  }

  public String buildFileContent() {
    return buildFileContent;
  }

  public Set<String> declaredDependencyCoordinates() {
    return declaredDependencyCoordinates;
  }

  public boolean launcherOnTestRuntimeOnly() {
    return launcherOnTestRuntimeOnly;
  }

  public boolean compilerArgsDeclareParameters() {
    return compilerArgsDeclareParameters;
  }

  public Map<String, String> sourceFiles() {
    return sourceFiles;
  }

  /**
   * Every build manifest beyond the root build file, by project-relative path: module build files,
   * settings files, version catalogs. The framework table's markers and module references are read
   * across all of them ({@link #manifestText()}), because a multi-module project declares its
   * framework where the module that uses it lives.
   */
  public Map<String, String> manifestFiles() {
    return manifestFiles;
  }

  /**
   * Source and configuration files under {@code src/} that are not Java sources: Kotlin and Groovy
   * sources, {@code application.properties}/{@code .yml}, {@code web.xml}. Framework wiring lives
   * in these as often as in Java; the checks that read {@link #sourceFiles()} never see them.
   */
  public Map<String, String> resourceFiles() {
    return resourceFiles;
  }

  /**
   * The root build file and every other manifest, as one text to match markers against — without
   * comment-only lines (a commented-out dependency is not a dependency), and with every version
   * catalog {@code group = "…", name = "…"} entry added as the quoted {@code "group:name"} the
   * markers match, the same coordinate a {@code module = "…"} entry spells directly.
   */
  public String manifestText() {
    StringBuilder text = new StringBuilder();
    Stream.concat(Stream.of(buildFileContent), manifestFiles.values().stream())
        .flatMap(String::lines)
        .filter(line -> !isCommentOnly(line))
        .forEach(line -> appendWithCatalogCoordinate(text, line));
    return text.toString();
  }

  /**
   * The root build file and every Gradle build script among the manifests, with their line and
   * block comments removed — the text a check reads when a switch must be LIVE code: a switch
   * inside a block comment, or after one on the same line, is not thrown.
   */
  public String buildScriptCode() {
    return Stream.concat(
            Stream.of(buildFileContent),
            manifestFiles.entrySet().stream()
                .filter(e -> e.getKey().endsWith(".gradle.kts") || e.getKey().endsWith(".gradle"))
                .map(Map.Entry::getValue))
        .map(JavaComments::strip)
        .collect(Collectors.joining("\n"));
  }

  private static boolean isCommentOnly(String line) {
    String content = line.strip();
    return content.startsWith("//")
        || content.startsWith("#")
        || content.startsWith("/*")
        || content.startsWith("*");
  }

  private static void appendWithCatalogCoordinate(StringBuilder text, String line) {
    text.append(line).append('\n');
    Matcher group = CATALOG_GROUP.matcher(line);
    Matcher name = CATALOG_NAME.matcher(line);
    if (group.find() && name.find()) {
      text.append('"').append(group.group(1)).append(':').append(name.group(1)).append("\"\n");
    }
  }

  /**
   * Every Java source, comments removed, and every {@link #resourceFiles() resource file}, for
   * wiring evidence. A comment that names the wiring is not the wiring, so a Java source is read
   * without its comments; a resource file keeps its text (its own comment syntax is the evidence's
   * business, and a {@code /*} in a properties value is a value).
   */
  public Stream<String> wiringTexts() {
    return Stream.concat(
        sourceFiles.values().stream().map(JavaComments::strip), resourceFiles.values().stream());
  }

  /**
   * The NarrativeTrace version this project already declares — on a coordinate or on the plugin —
   * or empty when it declares none. A fix line pins an integration module to THIS version, never to
   * whichever release the doctor itself came from: the modules must match the core the project
   * installed.
   */
  public Optional<String> narrativeTraceVersion() {
    return declaredNarrativeTraceVersion();
  }

  /**
   * {@link #narrativeTraceVersion()}, or a placeholder a reader replaces, for a fix line that must
   * pin a module even when the project declares no NarrativeTrace version yet.
   */
  public String narrativeTraceVersionOrPlaceholder() {
    return declaredNarrativeTraceVersion().orElse(VERSION_PLACEHOLDER);
  }

  private Optional<String> declaredNarrativeTraceVersion() {
    String text = manifestText();
    for (Pattern pattern :
        List.of(
            NARRATIVETRACE_COORDINATE_VERSION,
            NARRATIVETRACE_PLUGIN_VERSION,
            NARRATIVETRACE_CATALOG_PLUGIN_VERSION)) {
      Matcher m = pattern.matcher(text);
      if (m.find()) {
        return Optional.of(m.group(1));
      }
    }
    return Optional.empty();
  }

  public Map<String, String> outputFiles() {
    return outputFiles;
  }

  public Map<String, String> approvalDirFiles() {
    return approvalDirFiles;
  }

  public boolean extensionRegisteredViaServiceLoader() {
    return extensionRegisteredViaServiceLoader;
  }

  public boolean extensionRegisteredViaExtendWith() {
    return extensionRegisteredViaExtendWith;
  }

  /**
   * Every agent-skill directory found under either install root, in read order — a directory of
   * ours, somebody else's, or something that is not a directory at all.
   */
  public List<InstalledSkill> installedSkills() {
    return installedSkills;
  }

  /**
   * The coordinate of the skills carrier this project resolves, or empty when the entry point could
   * not resolve one (offline, or no repository provides it). Empty means "cannot tell", never
   * "nothing installed".
   */
  public String carrierCoordinate() {
    return carrierCoordinate;
  }

  /** Every skill name that carrier's catalogue lists, or empty when there is no carrier. */
  public List<String> catalogueSkills() {
    return catalogueSkills;
  }

  /** Whether a carrier was resolved at all — the one question that gates the skills check. */
  public boolean carrierResolved() {
    return !carrierCoordinate.isEmpty();
  }

  /** The skill directory at one flavour's path, or empty when nothing is there. */
  public Optional<InstalledSkill> installedSkill(SkillFlavour flavour, String name) {
    return installedSkills.stream()
        .filter(skill -> skill.flavour() == flavour && skill.name().equals(name))
        .findFirst();
  }

  /** True once either registration mechanism is present. */
  public boolean extensionRegistered() {
    return extensionRegisteredViaServiceLoader || extensionRegisteredViaExtendWith;
  }

  /** True once any dependency coordinate starts with the given group:artifact prefix. */
  public boolean declaresDependency(String coordinatePrefix) {
    return declaredDependencyCoordinates.stream().anyMatch(c -> c.startsWith(coordinatePrefix));
  }

  /**
   * A clean, fully-wired project: every check passes against this snapshot. Tests override from
   * here.
   */
  public static DoctorSnapshot healthy() {
    return builder()
        .runningJavaVersion(System.getProperty("java.version", "17.0.0"))
        .buildFileContent(
            """
            plugins { id("java") }
            dependencies {
                api("ai.narrativetrace:narrativetrace-junit5:0.2.2")
                testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
            }
            tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
            """)
        .addDependencyCoordinate("ai.narrativetrace:narrativetrace-junit5:0.2.2")
        .addDependencyCoordinate("org.junit.jupiter:junit-jupiter:5.11.4")
        .addDependencyCoordinate("org.junit.platform:junit-platform-launcher:1.11.4")
        .launcherOnTestRuntimeOnly(true)
        .compilerArgsDeclareParameters(true)
        .extensionRegisteredViaExtendWith(true)
        .putSourceFile(
            "src/test/java/com/example/OrderServiceTest.java",
            """
            import ai.narrativetrace.api.NotTraced;
            @ExtendWith(NarrativeTraceExtension.class)
            class OrderServiceTest {
              @NotTraced String secret;
              @Test void redactsSecretFields() {
                assertThat(rendered).doesNotContain(secretValue);
                assertThat(rendered).contains("[REDACTED]");
                assertThat(rendered).contains("orderId");
              }
            }
            """)
        .carrier(HEALTHY_CARRIER, HEALTHY_SKILLS)
        .addInstalledSkill(installedFromHealthyCarrier(HEALTHY_SKILLS.get(0)))
        .addInstalledSkill(installedFromHealthyCarrier(HEALTHY_SKILLS.get(1)))
        .build();
  }

  /** One page of the healthy project's own install, stamped with the carrier it came from. */
  private static InstalledSkill installedFromHealthyCarrier(String name) {
    return new InstalledSkill(
        SkillFlavour.AGENTS,
        name,
        InstalledSkill.Presence.OURS,
        HEALTHY_CARRIER,
        "---\nname: " + name + "\n---\n");
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Starts a copy of this snapshot for a single-field override in a test. */
  public Builder toBuilder() {
    Builder b = new Builder();
    b.runningJavaVersion = runningJavaVersion;
    b.env.putAll(env);
    b.systemProperties.putAll(systemProperties);
    b.gradleProperties.putAll(gradleProperties);
    b.buildFileContent = buildFileContent;
    b.declaredDependencyCoordinates.addAll(declaredDependencyCoordinates);
    b.launcherOnTestRuntimeOnly = launcherOnTestRuntimeOnly;
    b.compilerArgsDeclareParameters = compilerArgsDeclareParameters;
    copyProjectFilesInto(b);
    b.extensionRegisteredViaServiceLoader = extensionRegisteredViaServiceLoader;
    b.extensionRegisteredViaExtendWith = extensionRegisteredViaExtendWith;
    b.installedSkills.addAll(installedSkills);
    b.carrierCoordinate = carrierCoordinate;
    b.catalogueSkills.addAll(catalogueSkills);
    return b;
  }

  /** The five per-file maps of the project scan, copied into a builder. */
  private void copyProjectFilesInto(Builder b) {
    b.sourceFiles.putAll(sourceFiles);
    b.manifestFiles.putAll(manifestFiles);
    b.resourceFiles.putAll(resourceFiles);
    b.outputFiles.putAll(outputFiles);
    b.approvalDirFiles.putAll(approvalDirFiles);
  }

  /** Builds a {@link DoctorSnapshot}, from a real project scan or, in tests, by hand. */
  public static final class Builder {
    private String runningJavaVersion = System.getProperty("java.version", "17.0.0");
    private final Map<String, String> env = new LinkedHashMap<>();
    private final Map<String, String> systemProperties = new LinkedHashMap<>();
    private final Map<String, String> gradleProperties = new LinkedHashMap<>();
    private String buildFileContent = "";
    private final Set<String> declaredDependencyCoordinates = new LinkedHashSet<>();
    private boolean launcherOnTestRuntimeOnly;
    private boolean compilerArgsDeclareParameters;
    private final Map<String, String> sourceFiles = new LinkedHashMap<>();
    private final Map<String, String> manifestFiles = new LinkedHashMap<>();
    private final Map<String, String> resourceFiles = new LinkedHashMap<>();
    private final Map<String, String> outputFiles = new LinkedHashMap<>();
    private final Map<String, String> approvalDirFiles = new LinkedHashMap<>();
    private boolean extensionRegisteredViaServiceLoader;
    private boolean extensionRegisteredViaExtendWith;
    private final List<InstalledSkill> installedSkills = new ArrayList<>();
    private String carrierCoordinate = "";
    private final List<String> catalogueSkills = new ArrayList<>();

    private Builder() {}

    public Builder runningJavaVersion(String v) {
      this.runningJavaVersion = v;
      return this;
    }

    public Builder putEnv(String k, String v) {
      this.env.put(k, v);
      return this;
    }

    public Builder putSystemProperty(String k, String v) {
      this.systemProperties.put(k, v);
      return this;
    }

    public Builder putGradleProperty(String k, String v) {
      this.gradleProperties.put(k, v);
      return this;
    }

    public Builder buildFileContent(String content) {
      this.buildFileContent = content;
      return this;
    }

    public Builder addDependencyCoordinate(String coordinate) {
      this.declaredDependencyCoordinates.add(coordinate);
      return this;
    }

    public Builder clearDependencyCoordinates() {
      this.declaredDependencyCoordinates.clear();
      return this;
    }

    public Builder launcherOnTestRuntimeOnly(boolean v) {
      this.launcherOnTestRuntimeOnly = v;
      return this;
    }

    public Builder compilerArgsDeclareParameters(boolean v) {
      this.compilerArgsDeclareParameters = v;
      return this;
    }

    public Builder putSourceFile(String path, String content) {
      this.sourceFiles.put(path, content);
      return this;
    }

    public Builder clearSourceFiles() {
      this.sourceFiles.clear();
      return this;
    }

    public Builder putManifestFile(String path, String content) {
      this.manifestFiles.put(path, content);
      return this;
    }

    public Builder putResourceFile(String path, String content) {
      this.resourceFiles.put(path, content);
      return this;
    }

    public Builder putOutputFile(String path, String content) {
      this.outputFiles.put(path, content);
      return this;
    }

    public Builder putApprovalDirFile(String path, String content) {
      this.approvalDirFiles.put(path, content);
      return this;
    }

    public Builder extensionRegisteredViaServiceLoader(boolean v) {
      this.extensionRegisteredViaServiceLoader = v;
      return this;
    }

    public Builder extensionRegisteredViaExtendWith(boolean v) {
      this.extensionRegisteredViaExtendWith = v;
      return this;
    }

    /** One skill directory the installer's reader found in the project. */
    public Builder addInstalledSkill(InstalledSkill skill) {
      if (skill == null) {
        throw new IllegalArgumentException("an installed skill must not be null");
      }
      this.installedSkills.add(skill);
      return this;
    }

    /** Drops every skill directory recorded so far — the override a {@link #toBuilder()} needs. */
    public Builder clearInstalledSkills() {
      this.installedSkills.clear();
      return this;
    }

    /**
     * The carrier this project resolves and the skills its catalogue lists — or {@code ""} and an
     * empty list when the entry point could not resolve one. A whitespace-only coordinate is stored
     * as {@code ""}, so "unresolved" has exactly one representation for {@link #carrierResolved()}
     * to read.
     *
     * @throws IllegalArgumentException on a half-resolved carrier: a coordinate names a catalogue
     *     or neither is known, because the skills check reads the absence as "cannot tell" and a
     *     coordinate with no catalogue would read as "cannot tell" while claiming to know one
     */
    public Builder carrier(String coordinate, List<String> skillNames) {
      if (coordinate == null || skillNames == null) {
        throw new IllegalArgumentException(
            "use \"\" and an empty list for an unresolved carrier, never null");
      }
      if (coordinate.isBlank() != skillNames.isEmpty()) {
        throw new IllegalArgumentException(
            "a resolved carrier has both a coordinate and a catalogue, an unresolved one neither;"
                + " got \""
                + coordinate
                + "\" and "
                + skillNames.size()
                + " skill(s)");
      }
      this.carrierCoordinate = coordinate.isBlank() ? "" : coordinate;
      this.catalogueSkills.clear();
      this.catalogueSkills.addAll(skillNames);
      return this;
    }

    public DoctorSnapshot build() {
      return new DoctorSnapshot(this);
    }
  }
}
