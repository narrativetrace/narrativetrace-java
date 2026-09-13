/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Runs one fixture class through the real JUnit Platform Launcher against the PUBLISHED {@code
 * narrativetrace-junit5} extension — the only way to observe an extension-gated default (approval
 * mode, output-on-by-default) without a Gradle {@code test} task in the way. System properties set
 * before {@link #run} are what the launcher's default {@code ConfigurationParameters} reads
 * (installation-guide.md "CLI override": "System properties override all other sources").
 */
final class JUnitLauncherSupport {

  private JUnitLauncherSupport() {}

  static void run(Class<?> testClass) {
    LauncherDiscoveryRequest request =
        LauncherDiscoveryRequestBuilder.request().selectors(selectClass(testClass)).build();
    Launcher launcher = LauncherFactory.create();
    launcher.execute(request);
  }
}
