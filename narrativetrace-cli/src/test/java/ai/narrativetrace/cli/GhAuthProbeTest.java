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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * What the four outcomes of a {@code gh auth status} probe mean.
 *
 * <p>All four answer the same question — may the {@code gh} channel be offered — and three of them
 * are failures that are not errors. A tool that is absent is not a problem to report; it is a
 * channel that is closed, and the verb has another one.
 */
class GhAuthProbeTest {

  @Test
  void anExitOfZeroMeansSignedIn() {
    assertThat(GhAuthProbe.authenticated(() -> exited(0))).isTrue();
  }

  @Test
  void aNonZeroExitMeansNotSignedIn() {
    assertThat(GhAuthProbe.authenticated(() -> exited(1))).isFalse();
  }

  @Test
  void aProcessThatCannotBeStartedMeansNotAvailable() {
    assertThat(
            GhAuthProbe.authenticated(
                () -> {
                  throw new IOException("gh: command not found");
                }))
        .isFalse();
  }

  @Test
  void aPlatformThatRefusesToStartProcessesAtAllMeansNotAvailable() {
    assertThat(
            GhAuthProbe.authenticated(
                () -> {
                  throw new UnsupportedOperationException("no process API here");
                }))
        .isFalse();
  }

  @Test
  void aProbeThatDoesNotFinishIsKilledAndMeansNotAvailable() {
    FakeProcess wedged = new FakeProcess(0, false);

    assertThat(GhAuthProbe.authenticated(() -> wedged)).isFalse();
    assertThat(wedged.killed).as("a wedged probe must not be left running").isTrue();
  }

  @Test
  void anInterruptedProbeMeansNotAvailableAndLeavesTheThreadInterrupted() {
    boolean answer =
        GhAuthProbe.authenticated(
            () ->
                new FakeProcess(0, true) {
                  @Override
                  public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
                    throw new InterruptedException("stop");
                  }
                });

    assertThat(answer).isFalse();
    assertThat(Thread.interrupted()).as("an interrupt must not be swallowed silently").isTrue();
  }

  @Test
  void theRealProbeAnswersWithoutThrowingWhateverThisMachineHas() {
    assertThat(GhAuthProbe.authenticated()).isIn(true, false);
  }

  private static Process exited(int code) {
    return new FakeProcess(code, true);
  }

  /** A finished (or wedged) process, with just enough of the contract for the probe to read. */
  private static class FakeProcess extends Process {

    private final int exitCode;
    private final boolean finishes;
    private boolean killed;

    FakeProcess(int exitCode, boolean finishes) {
      this.exitCode = exitCode;
      this.finishes = finishes;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      return finishes;
    }

    @Override
    public int waitFor() {
      return exitCode;
    }

    @Override
    public int exitValue() {
      return exitCode;
    }

    @Override
    public boolean isAlive() {
      return !finishes;
    }

    @Override
    public Process destroyForcibly() {
      killed = true;
      return this;
    }

    @Override
    public void destroy() {
      killed = true;
    }

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]);
    }
  }
}
