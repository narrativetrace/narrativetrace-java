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
package ai.narrativetrace.cli;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Asks an already-installed {@code gh} whether it is signed in — the one question that decides
 * whether the {@code gh} channel is offered at all.
 *
 * <p>INTENT: {@code gh issue create} is the convenience path, and offering it to somebody who has
 * no {@code gh}, or an unauthenticated one, is offering them an error message. {@code gh auth
 * status} answers exactly that question, using that tool's own credential; this launcher holds none
 * and never will.
 *
 * <p><b>@llmNote</b> The probe is the ONLY outward-facing thing the launcher's own process causes,
 * and it causes it indirectly: {@code gh} validates its token against the host. That is the user's
 * tool making the user's call. The launcher still makes no request of its own, holds no credential
 * and files nothing — which is the invariant this note exists to keep honest.
 *
 * <p><b>@llmNote</b> Absence, a non-zero exit, a timeout and a platform that cannot start a process
 * all answer the SAME way: not available. An unavailable channel is a fact, never an error — the
 * verb says "use the URL instead" and exits 1.
 *
 * <p><b>@llmNote</b> {@link Starter} is the seam, for the same reason {@code Cli.Deps} is one: the
 * DECISION — which outcomes mean "signed in" — is the part worth testing, and testing it by
 * installing a tool is testing the machine instead. {@link #ghAuthStatus()} is the one line that
 * touches a real process.
 *
 * <p><b>@sideEffects</b> Starts one short-lived subprocess, with its output discarded.
 */
final class GhAuthProbe {

  /** Long enough for a token check, short enough that nobody waits on a wedged network. */
  private static final long TIMEOUT_SECONDS = 10;

  /** Starts the probe's process. The real one runs {@code gh auth status}; a test fakes it. */
  interface Starter {
    Process start() throws IOException;
  }

  private GhAuthProbe() {}

  /** Whether {@code gh} is present AND signed in. Any doubt answers false. */
  static boolean authenticated() {
    return authenticated(GhAuthProbe::ghAuthStatus);
  }

  /** The decision, over whatever started the process. */
  static boolean authenticated(Starter starter) {
    try {
      Process process = starter.start();
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return false;
      }
      return process.exitValue() == 0;
    } catch (IOException | RuntimeException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /**
   * The real probe. A fixed argv of three literals — nothing a project, a flag or a report can
   * reach, which is what makes this one line safe to have at all.
   */
  private static Process ghAuthStatus() throws IOException {
    return new ProcessBuilder("gh", "auth", "status")
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start();
  }
}
