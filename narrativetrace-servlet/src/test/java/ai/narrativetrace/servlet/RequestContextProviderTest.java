/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.export.RequestContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

/** The servlet binding of the API's request-context SPI, exercised across the module boundary. */
class RequestContextProviderTest {

  @Test
  void defaultProviderReturnsNull() {
    RequestContextProvider<HttpServletRequest> provider = request -> null;
    assertThat(provider.resolveUserContext(new StubHttpServletRequest())).isNull();
  }

  @Test
  void resolvedContextCarriesTheThreeIdentityFields() {
    RequestContextProvider<HttpServletRequest> provider =
        request -> new RequestContextProvider.UserContext("user-42", "session-abc", "tenant-xyz");

    var resolved = provider.resolveUserContext(new StubHttpServletRequest());

    assertThat(resolved.enduserId()).isEqualTo("user-42");
    assertThat(resolved.sessionId()).isEqualTo("session-abc");
    assertThat(resolved.tenantId()).isEqualTo("tenant-xyz");
  }
}
