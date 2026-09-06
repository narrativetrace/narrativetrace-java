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
package ai.narrativetrace.api.event;

/**
 * Auto-detected process-level resource identity: host name, process id, and Java runtime version.
 *
 * <p>INTENT: Completes the resource tier beside the client-supplied {@link ServiceIdentity}.
 * Detected once per process ({@link #current()}) and stamped onto spans when {@code
 * narrativetrace.capture.resource} is enabled (the default; hostname-sensitive clients opt out).
 * Never re-emitted onto OpenTelemetry spans — the OTel SDK's resource detectors own that path; this
 * covers the library's own outputs (canonical JSON, MDC).
 *
 * @param hostName Local host name, or {@code null} when it cannot be determined.
 * @param processPid Current process id, or {@code null} where the runtime cannot report one
 *     (Android has no {@code java.lang.ProcessHandle}).
 * @param runtimeVersion Java runtime version string (e.g. {@code "17.0.10+7"}).
 */
public record ResourceIdentity(String hostName, Long processPid, String runtimeVersion) {

  private static final class Holder {
    static final ResourceIdentity CURRENT = detect();
  }

  /** The process-wide detected identity; resolved once, on first use. */
  public static ResourceIdentity current() {
    return Holder.CURRENT;
  }

  private static ResourceIdentity detect() {
    return new ResourceIdentity(
        detectHostName(System::getenv, () -> java.net.InetAddress.getLocalHost().getHostName()),
        detectPid(() -> ProcessHandle.current().pid()),
        Runtime.version().toString());
  }

  /**
   * Process id from the given source, or {@code null} when the runtime cannot report one.
   *
   * <p><b>@llmNote</b> Catches {@link Throwable}, not {@link Exception}: Android has no {@code
   * java.lang.ProcessHandle}, so touching it raises {@code NoClassDefFoundError}. This value is
   * resolved eagerly in a static holder, so an escaping error would abort class initialization on
   * the first trace rather than degrade. {@code process.pid} is optional in {@code
   * entry.schema.json}, so absence is the honest answer here — unlike {@code service}, nothing
   * needs inventing (ADR-014). Package-private with an injectable source so the failure branch is
   * testable.
   */
  @SuppressWarnings({"PMD.AvoidCatchingThrowable", "PMD.AvoidCatchingGenericException"})
  static Long detectPid(java.util.concurrent.Callable<Long> source) {
    try {
      return source.call();
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Host name via the environment first ({@code HOSTNAME}/{@code COMPUTERNAME} — cheap, set in
   * containers and CI), falling back to a reverse lookup, then {@code null}. Never throws.
   * Package-private with injectable sources so every fallback branch is testable.
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  static String detectHostName(
      java.util.function.UnaryOperator<String> env, java.util.concurrent.Callable<String> lookup) {
    var name = env.apply("HOSTNAME");
    if (name == null || name.isBlank()) {
      name = env.apply("COMPUTERNAME");
    }
    if (name != null && !name.isBlank()) {
      return name;
    }
    try {
      return lookup.call();
    } catch (Exception e) {
      return null;
    }
  }
}
