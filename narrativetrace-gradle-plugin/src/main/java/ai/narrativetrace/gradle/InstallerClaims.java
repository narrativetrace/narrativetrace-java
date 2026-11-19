/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.gradle.api.Task;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * Makes an installer task run once per build, however many projects applied the plugin.
 *
 * <p>INTENT: every project that applies the plugin registers its own {@code narrativetraceInit},
 * and they all aim at the one repository root. {@code ./gradlew narrativetraceInit} from the root
 * runs every instance; the first to reach this service does the work and each other instance logs
 * one INFO line naming the task path that did.
 *
 * <p><b>@llmNote</b> A shared {@link BuildService}, not a static map: a static would outlive the
 * build in a long-lived daemon and make the second build in a daemon skip. A service lives for
 * exactly one build, and the claim is a single atomic {@code putIfAbsent}, so it holds under {@code
 * --parallel}. It keys on the task KIND plus the canonical target path — an init and an uninstall
 * of the same root never shadow each other.
 */
public abstract class InstallerClaims implements BuildService<BuildServiceParameters.None> {

  /** The name the service is registered under, once per build. */
  static final String SERVICE_NAME = "narrativetraceInstallerClaims";

  private final ConcurrentMap<String, String> owners = new ConcurrentHashMap<>();

  /**
   * Tries to claim {@code kind} for {@code target}.
   *
   * @return {@code null} when the caller now owns the work, otherwise the task path that already
   *     claimed it
   */
  String claim(String kind, Path target, String taskPath) {
    return owners.putIfAbsent(kind + "@" + canonical(target), taskPath);
  }

  /**
   * Claims the work for {@code task}, or logs the one INFO line that says who did it.
   *
   * @return whether the caller should do the work
   */
  boolean claimOrSkip(Task task, Path target) {
    String owner = claim(task.getName(), target, task.getPath());
    if (owner == null) {
      return true;
    }
    task.getLogger()
        .info(
            "{} skipped: already done in this build by {} (target {}).",
            task.getName(),
            owner,
            target);
    return false;
  }

  private static String canonical(Path target) {
    try {
      return target.toRealPath().toString();
    } catch (IOException e) {
      return target.toAbsolutePath().normalize().toString();
    }
  }
}
