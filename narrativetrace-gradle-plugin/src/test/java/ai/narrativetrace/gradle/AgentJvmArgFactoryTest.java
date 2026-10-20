/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code -javaagent} argument contract with {@code AgentConfig.parse}: agent options are
 * {@code key=value} pairs, so package filters must be emitted under the {@code packages=} key. A
 * bare package list makes the agent throw during premain and the test JVM fail to launch.
 */
class AgentJvmArgFactoryTest {

  @Test
  void noPackagesOrExcludeEmitsBareJavaagentArg() {
    var arg = AgentJvmArgFactory.javaagentArg("/libs/agent.jar", List.of(), List.of());

    assertThat(arg).isEqualTo("-javaagent:/libs/agent.jar");
  }

  @Test
  void singlePackageIsEmittedUnderThePackagesKey() {
    var arg = AgentJvmArgFactory.javaagentArg("/libs/agent.jar", List.of("com.shop"), List.of());

    assertThat(arg).isEqualTo("-javaagent:/libs/agent.jar=packages=com.shop");
  }

  @Test
  void multiplePackagesAreSemicolonJoinedUnderThePackagesKey() {
    var arg =
        AgentJvmArgFactory.javaagentArg(
            "/libs/agent.jar", List.of("com.shop", "com.billing"), List.of());

    assertThat(arg).isEqualTo("-javaagent:/libs/agent.jar=packages=com.shop;com.billing");
  }

  @Test
  void excludeAloneIsEmittedUnderTheExcludeKeyWithNoPackagesSegment() {
    var arg = AgentJvmArgFactory.javaagentArg("/libs/agent.jar", List.of(), List.of("org.mockito"));

    assertThat(arg).isEqualTo("-javaagent:/libs/agent.jar=exclude=org.mockito");
  }

  @Test
  void packagesAndExcludeAreCommaJoinedWithPackagesFirst() {
    var arg =
        AgentJvmArgFactory.javaagentArg(
            "/libs/agent.jar", List.of("com.shop"), List.of("org.mockito"));

    assertThat(arg).isEqualTo("-javaagent:/libs/agent.jar=packages=com.shop,exclude=org.mockito");
  }

  @Test
  void multipleExcludeEntriesAreSemicolonJoinedUnderTheExcludeKey() {
    var arg =
        AgentJvmArgFactory.javaagentArg(
            "/libs/agent.jar", List.of(), List.of("org.mockito", "net.bytebuddy"));

    assertThat(arg).isEqualTo("-javaagent:/libs/agent.jar=exclude=org.mockito;net.bytebuddy");
  }
}
