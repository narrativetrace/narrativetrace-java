/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CallSiteLocatorTest {

  @Test
  void librariesOwnClassesAndJdkPlumbingAreInfrastructure() {
    assertThat(CallSiteLocator.isInfrastructure("ai.narrativetrace.proxy.CallSiteLocator"))
        .isTrue();
    assertThat(CallSiteLocator.isInfrastructure("ai.narrativetrace.proxy.NarrativeTraceProxy"))
        .isTrue();
    assertThat(
            CallSiteLocator.isInfrastructure(
                "ai.narrativetrace.proxy.NarrativeTraceProxy$FixedIdentity"))
        .isTrue();
    assertThat(CallSiteLocator.isInfrastructure("java.lang.reflect.Method")).isTrue();
    assertThat(CallSiteLocator.isInfrastructure("jdk.internal.reflect.DirectMethodHandleAccessor"))
        .isTrue();
    assertThat(CallSiteLocator.isInfrastructure("jdk.proxy2.$Proxy14")).isTrue();
    assertThat(CallSiteLocator.isInfrastructure("com.sun.proxy.$Proxy3")).isTrue();
  }

  @Test
  void generatedProxiesInUserPackagesAreInfrastructure() {
    // Proxies for non-public interfaces are defined in the interface's own package.
    assertThat(CallSiteLocator.isInfrastructure("com.acme.billing.$Proxy14")).isTrue();
    assertThat(CallSiteLocator.isInfrastructure("$Proxy0")).isTrue();
  }

  @Test
  void userClassesAreNeverInfrastructureEvenWithSimilarNames() {
    assertThat(CallSiteLocator.isInfrastructure("ai.narrativetrace.proxy.CallSiteLocatorTest"))
        .isFalse();
    assertThat(CallSiteLocator.isInfrastructure("com.acme.ProxyService")).isFalse();
    assertThat(CallSiteLocator.isInfrastructure("com.acme.MyProxyFactory")).isFalse();
    assertThat(CallSiteLocator.isInfrastructure("com.acme.billing.OrderService")).isFalse();
  }
}
