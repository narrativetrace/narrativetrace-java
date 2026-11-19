/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import org.junit.jupiter.api.Test;

/**
 * Nothing that can throw runs in {@link BoundedEventBuffer#put} between claiming a ring slot and
 * publishing it (B-342).
 *
 * <p>INTENT: A traced call at the bottom of a recursion that exhausts the stack can raise {@link
 * StackOverflowError} anywhere in the publish path; {@link TraceBoundary} swallows it, as it must,
 * and the caller carries on. If the error lands after the claim and before the publication, the
 * claimed slot is never written: the drain stops in front of it on every later flush, and every
 * later event sits behind it — invisible to every capture — until producers lap the whole ring. In
 * a JUnit suite that was "the next tests after the overflow leave no trace file at all".
 *
 * <p>A {@link StackOverflowError} is raised where a frame is pushed, so the fix is that no frame is
 * pushed in that window: once the claim's own call has returned, {@code put} makes no call at all
 * in production — the slot is marked, written and published by three volatile field stores. These
 * tests pin exactly that, on the compiled bytecode of {@code put} and the fields it writes, because
 * the property is structural and its failure is not reproducible on demand: whether the stack runs
 * out inside the window depends on the depth the recursion started at and on what the JIT has
 * compiled. A behavioural sweep (publish once from every stack depth of a small thread) passed
 * against the unfixed code in the unit-test JVM, so it could not serve as the guard; the agent's
 * {@code StackOverflowAgentEndToEndTest} is the behavioural check, end to end.
 *
 * <p>A recovering design — the consumer stepping over a slot no producer will ever write — was
 * considered and not chosen: it changes the slot protocol the jcstress tests pin, and a consumer
 * cannot tell an abandoned claim from a slow producer.
 *
 * <p><b>@llmNote</b> The one call the bytecode may hold after the claim is the package-private test
 * seam ({@code afterClaim}), and only behind a null check of a final field that every production
 * constructor leaves {@code null}. If an edit to {@code put} adds any call after the claim — a
 * helper, a {@code VarHandle} access mode, a fence — these tests fail; that is the point, not
 * something to adjust.
 */
class AbandonedClaimTest {

  private static final Pattern INSTRUCTION = Pattern.compile("^\\s*(\\d+): (\\w+)\\s*(.*)$");
  private static final String CLAIM = "java/lang/invoke/VarHandle.getAndAdd";

  @Test
  void afterTheClaimPutCallsNothingButTheAbsentTestSeam() {
    var afterClaim = instructionsAfterTheClaim();

    var calls = afterClaim.stream().filter(i -> i.opcode().startsWith("invoke")).toList();
    assertThat(calls)
        .as("calls after the claim; only the test seam may be one")
        .singleElement()
        .satisfies(seam -> assertThat(seam.operand()).contains("java/lang/Runnable.run"));
    var guard = afterClaim.stream().filter(i -> "ifnull".equals(i.opcode())).toList();
    assertThat(guard).as("the seam's null check").hasSize(1);
    assertThat(guard.get(0).offset()).isLessThan(calls.get(0).offset());
    assertThat(Integer.parseInt(guard.get(0).operand().trim()))
        .as("an absent seam jumps past its call")
        .isGreaterThan(calls.get(0).offset());
  }

  @Test
  void afterTheClaimTheSlotIsMarkedWrittenAndPublishedByThreeVolatileStores() throws Exception {
    var stores =
        instructionsAfterTheClaim().stream()
            .filter(i -> "putfield".equals(i.opcode()))
            .map(i -> i.operand().replaceAll(".*BoundedEventBuffer\\$Slot\\.(\\w+):.*", "$1"))
            .toList();

    assertThat(stores).containsExactly("sequence", "event", "sequence");
    for (var field : List.of("sequence", "event")) {
      assertThat(
              Modifier.isVolatile(
                  BoundedEventBuffer.Slot.class.getDeclaredField(field).getModifiers()))
          .as("Slot.%s is volatile, so its stores keep their order without a fence call", field)
          .isTrue();
    }
  }

  @Test
  void aProductionBufferCarriesNoClaimSeam() throws Exception {
    var seam = BoundedEventBuffer.class.getDeclaredField("afterClaim");
    seam.setAccessible(true);

    assertThat(Modifier.isFinal(seam.getModifiers())).as("the seam field is final").isTrue();
    assertThat(seam.get(new BoundedEventBuffer(4))).as("the production constructor").isNull();
  }

  /** One disassembled instruction of {@code put}: its bytecode offset, opcode and operand text. */
  private record Instruction(int offset, String opcode, String operand) {}

  /** The instructions of {@code put} that follow the claim's {@code getAndAdd}. */
  private static List<Instruction> instructionsAfterTheClaim() {
    var instructions = putInstructions();
    int claim = 0;
    while (!instructions.get(claim).operand().contains(CLAIM)) {
      claim++;
    }
    return instructions.subList(claim + 1, instructions.size());
  }

  private static List<Instruction> putInstructions() {
    var lines = disassembly().lines().toList();
    int start = 0;
    while (!lines.get(start).trim().startsWith("void put(")) {
      start++;
    }
    return lines.subList(start + 1, lines.size()).stream()
        .takeWhile(line -> !line.isBlank())
        .map(INSTRUCTION::matcher)
        .filter(Matcher::matches)
        .map(m -> new Instruction(Integer.parseInt(m.group(1)), m.group(2), m.group(3)))
        .toList();
  }

  /** {@code javap -c -p} of the buffer's compiled class, read from where the test JVM loaded it. */
  private static String disassembly() {
    var javap = ToolProvider.findFirst("javap").orElseThrow();
    var out = new ByteArrayOutputStream();
    int exit;
    try (var print = new PrintStream(out, true, StandardCharsets.UTF_8)) {
      exit =
          javap.run(
              print,
              print,
              "-c",
              "-p",
              "-cp",
              classesDirectory(),
              BoundedEventBuffer.class.getName());
    }
    assertThat(exit).as("javap exit code").isZero();
    return out.toString(StandardCharsets.UTF_8);
  }

  private static String classesDirectory() {
    URL location = BoundedEventBuffer.class.getProtectionDomain().getCodeSource().getLocation();
    try {
      return Path.of(location.toURI()).toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
