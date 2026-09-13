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
package ai.narrativetrace.cli.doctor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything the eleven doctor checks read, gathered once, read-only, zero network. A check is a
 * pure function over a {@code DoctorSnapshot}, which is why every check has both a passing and a
 * failing unit test with no disk I/O at all — the same discipline the TypeScript reference applies
 * ({@code packages/cli/src/doctor/types.ts}).
 *
 * <p>Built two ways: {@link SnapshotBuilder} walks a real project directory (the {@code java -jar}
 * launcher and the Gradle {@code narrativetraceDoctor} task both use it); tests build one directly
 * with {@link Builder}, starting from {@link #healthy()} and overriding only what a case needs.
 */
public final class DoctorSnapshot {

  private final String runningJavaVersion;
  private final Map<String, String> env;
  private final Map<String, String> systemProperties;
  private final Map<String, String> gradleProperties;
  private final String buildFileContent;
  private final Set<String> declaredDependencyCoordinates;
  private final boolean launcherOnTestRuntimeOnly;
  private final boolean compilerArgsDeclareParameters;
  private final Map<String, String> sourceFiles;
  private final Map<String, String> outputFiles;
  private final Map<String, String> approvalDirFiles;
  private final boolean extensionRegisteredViaServiceLoader;
  private final boolean extensionRegisteredViaExtendWith;

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
    this.outputFiles = Map.copyOf(b.outputFiles);
    this.approvalDirFiles = Map.copyOf(b.approvalDirFiles);
    this.extensionRegisteredViaServiceLoader = b.extensionRegisteredViaServiceLoader;
    this.extensionRegisteredViaExtendWith = b.extensionRegisteredViaExtendWith;
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
        .build();
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
    b.sourceFiles.putAll(sourceFiles);
    b.outputFiles.putAll(outputFiles);
    b.approvalDirFiles.putAll(approvalDirFiles);
    b.extensionRegisteredViaServiceLoader = extensionRegisteredViaServiceLoader;
    b.extensionRegisteredViaExtendWith = extensionRegisteredViaExtendWith;
    return b;
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
    private final Map<String, String> outputFiles = new LinkedHashMap<>();
    private final Map<String, String> approvalDirFiles = new LinkedHashMap<>();
    private boolean extensionRegisteredViaServiceLoader;
    private boolean extensionRegisteredViaExtendWith;

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

    public DoctorSnapshot build() {
      return new DoctorSnapshot(this);
    }
  }
}
