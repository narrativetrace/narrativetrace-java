/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The assertions every fuzz target shares. A crash alone is not an oracle.
 *
 * <p>INTENT: The parity document lists six oracles plus the AI-consumer one, and every port
 * implements the same list. Keeping them here — rather than inline in each property — is what makes
 * "the ports mirror the targets and corpus" checkable: a reader can count them.
 *
 * <p><b>@llmNote</b> The redaction oracle looks for a fresh random token per case, not a fixed
 * string. A fixed secret is findable by a renderer that special-cases it and, worse, is findable by
 * a *test* that passes because some earlier case cleared the same string out. It also checks a
 * prefix of the token, because a partial leak through a truncating emitter is still a leak.
 */
public final class Oracles {

  /**
   * Wall-clock budget for one input through one emitter. Generous on purpose: this is a hang
   * detector, not a benchmark. The perf tiers measure speed; this only says a narration cannot cost
   * unbounded time in the size of its input.
   */
  public static final long BUDGET_MILLIS = 10_000;

  /**
   * Ceiling on one emitter's output. The renderer truncates strings at 200 characters and
   * collections at 5 elements, so a 1 MiB input must not produce a 1 MiB output — but diagrams and
   * documents legitimately repeat a value across sections, so the ceiling is generous rather than
   * tight.
   */
  public static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;

  /**
   * Prefix every thread this library starts carries — the same one the Tier 0 footprint probe uses.
   */
  public static final String THREAD_PREFIX = "narrative-trace-";

  /** How much of a sentinel must be absent for the redaction oracle to pass. */
  private static final int PARTIAL_LEAK_LENGTH = 12;

  private static final SecureRandom RANDOM = new SecureRandom();

  private Oracles() {}

  /**
   * A token no output may carry, unique per case.
   *
   * @return a 22-character token whose leading {@code sentinel} makes a failure readable and whose
   *     random tail makes a false positive impossible
   */
  public static String freshSentinel() {
    var bytes = new byte[7];
    RANDOM.nextBytes(bytes);
    return "sentinel" + HexFormat.of().formatHex(bytes);
  }

  /** Runs {@code work}, failing when it takes longer than {@link #BUDGET_MILLIS}. */
  public static <T> T withinBudget(String label, Supplier<T> work) {
    var start = System.nanoTime();
    var result = work.get();
    var elapsedMillis = (System.nanoTime() - start) / 1_000_000;
    assertThat(elapsedMillis)
        .as("%s must cost bounded time in the size of its input", label)
        .isLessThan(BUDGET_MILLIS);
    return result;
  }

  /** Every emitter's output stays under {@link #MAX_OUTPUT_BYTES}. */
  public static void boundedSize(Map<String, String> outputs) {
    outputs.forEach(
        (emitter, output) ->
            assertThat(output.length())
                .as("%s must produce bounded output", emitter)
                .isLessThan(MAX_OUTPUT_BYTES));
  }

  /**
   * The redaction oracle: the sentinel reaches no byte of any output, whole or partial.
   *
   * @param outputs every emitter's output, keyed by emitter
   * @param sentinel the token planted behind {@code @NotTraced}
   */
  public static void containsNoSentinel(Map<String, String> outputs, String sentinel) {
    var partial = sentinel.substring(0, PARTIAL_LEAK_LENGTH);
    assertThat(outputs)
        .as("a value marked redacted must appear in no output of any format at any depth")
        .isNotEmpty();
    outputs.forEach(
        (emitter, output) -> {
          assertThat(output).as("%s leaked the redacted value", emitter).doesNotContain(sentinel);
          assertThat(output)
              .as("%s leaked the leading bytes of the redacted value", emitter)
              .doesNotContain(partial);
        });
  }

  /**
   * Rendering twice produces the same bytes — no time, identity hash or iteration order leaks in.
   */
  public static void idempotent(String label, Supplier<String> render) {
    assertThat(render.get()).as("%s must render identically twice", label).isEqualTo(render.get());
  }

  /** No thread this library starts survives a render — the no-hook-left-behind oracle. */
  public static void noLibraryThreadLeft() {
    var live =
        Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> name.startsWith(THREAD_PREFIX))
            .toList();
    assertThat(live).as("rendering must start no background thread").isEmpty();
  }
}
