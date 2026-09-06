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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceIdentityTest {

  @Test
  void detectsTheRunningProcessIdentityOnce() {
    var identity = ResourceIdentity.current();

    assertThat(identity.processPid()).isEqualTo(ProcessHandle.current().pid());
    assertThat(identity.runtimeVersion()).isEqualTo(Runtime.version().toString());
    assertThat(identity).isSameAs(ResourceIdentity.current());
  }

  @Test
  void hostNamePrefersHostnameOverComputernameOverLookup() {
    assertThat(
            ResourceIdentity.detectHostName(k -> "HOSTNAME".equals(k) ? "web-1" : null, () -> "x"))
        .isEqualTo("web-1");
    assertThat(
            ResourceIdentity.detectHostName(
                k -> "COMPUTERNAME".equals(k) ? "WIN-1" : null, () -> "x"))
        .isEqualTo("WIN-1");
    assertThat(ResourceIdentity.detectHostName(k -> null, () -> "looked-up"))
        .isEqualTo("looked-up");
  }

  @Test
  void blankEnvironmentValuesFallThrough() {
    assertThat(ResourceIdentity.detectHostName(k -> "", () -> "looked-up")).isEqualTo("looked-up");
  }

  @Test
  void failedLookupYieldsNullInsteadOfThrowing() {
    assertThat(
            ResourceIdentity.detectHostName(
                k -> null,
                () -> {
                  throw new java.net.UnknownHostException("no dns");
                }))
        .isNull();
  }

  // ── process id detection (item 38: ProcessHandle is absent on Android) ───────

  @Test
  void pidIsDetectedOnARuntimeThatHasProcessHandle() {
    assertThat(ResourceIdentity.detectPid(() -> ProcessHandle.current().pid())).isPositive();
  }

  @Test
  void missingProcessHandleYieldsNoPidInsteadOfFailingClassInit() {
    // Android has no java.lang.ProcessHandle, so the reference raises NoClassDefFoundError
    // -- an Error, not an Exception. ResourceIdentity resolves eagerly in a static holder,
    // so an escape here would abort class initialization on the first trace.
    assertThat(
            ResourceIdentity.detectPid(
                () -> {
                  throw new NoClassDefFoundError("java.lang.ProcessHandle");
                }))
        .isNull();
  }

  @Test
  void aSecurityManagerDenyingPidYieldsNoPidRatherThanThrowing() {
    assertThat(
            ResourceIdentity.detectPid(
                () -> {
                  throw new SecurityException("denied");
                }))
        .isNull();
  }

  @Test
  void anAbsentPidIsCarriedAsNullSoTheOptionalSchemaFieldIsOmitted() {
    var identity = new ResourceIdentity("web-1", null, "17.0.10+7");

    assertThat(identity.processPid()).isNull();
    assertThat(identity.hostName()).isEqualTo("web-1");
  }
}
