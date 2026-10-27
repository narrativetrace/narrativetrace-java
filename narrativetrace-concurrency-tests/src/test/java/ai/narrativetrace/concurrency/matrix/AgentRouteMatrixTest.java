/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.matrix;

import static ai.narrativetrace.concurrency.support.Requests.SKUS;
import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.concurrency.agentfixture.AgentFixtureMain;
import ai.narrativetrace.concurrency.support.FlatNode;
import ai.narrativetrace.concurrency.support.Observed;
import ai.narrativetrace.concurrency.support.Requests;
import ai.narrativetrace.concurrency.support.Verdict;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The AGENT route: the real standalone agent attached to a forked JVM ({@code
 * DeepAgentEndToEndTest}'s shape), the shop classes woven by bytecode — no proxy anywhere. Each
 * scenario forks once; its three phases (one request, eight overlapping, a pooled worker reused)
 * are read back from the files the fork wrote.
 */
class AgentRouteMatrixTest {

  private static final int OVERLAPPING = 8;

  @TempDir static Path tempDir;

  private static Path servletFork;
  private static Path springAsync;

  @BeforeAll
  static void forkBothScenarios() throws Exception {
    servletFork = fork("servlet-fork");
    springAsync = fork("spring-async");
  }

  @Test
  void agentServletForkGroup_workersNestUnderTheSubmittingSpanWithTheirValues() throws Exception {
    var id = Requests.id(1);
    var observed = read(servletFork, "single", id);
    Verdict.ownWorkNestedUnderTheSubmittingSpan(observed, id, SKUS, "reserve");
    Verdict.workRanOnWorkerThreads(observed, "reserve");
  }

  @Test
  void agentServletForkGroup_eightOverlappingRequestsExportOnlyTheirOwnWork() throws Exception {
    assertEightOwn(servletFork, "reserve");
  }

  @Test
  void agentServletForkGroup_theSameWorkerServesTwoRequestsEachExportsOnlyItsOwn()
      throws Exception {
    assertSameWorkerDifferentTraces(servletFork, "reserve");
  }

  @Test
  void agentSpringAsync_workersNestUnderTheSubmittingSpanWithTheirValues() throws Exception {
    var id = Requests.id(1);
    var observed = read(springAsync, "single", id);
    Verdict.ownWorkNestedUnderTheSubmittingSpan(observed, id, SKUS, "reserveAsync");
    Verdict.workRanOnWorkerThreads(observed, "reserveAsync");
  }

  @Test
  void agentSpringAsync_eightOverlappingRequestsExportOnlyTheirOwnWork() throws Exception {
    assertEightOwn(springAsync, "reserveAsync");
  }

  @Test
  void agentSpringAsync_theSameWorkerServesTwoRequestsEachExportsOnlyItsOwn() throws Exception {
    assertSameWorkerDifferentTraces(springAsync, "reserveAsync");
  }

  private static void assertEightOwn(Path dir, String workMethod) throws IOException {
    for (var id : Requests.ids(100, OVERLAPPING)) {
      var observed = read(dir, "concurrent", id);
      Verdict.ownWorkNestedUnderTheSubmittingSpan(observed, id, SKUS, workMethod);
      Verdict.workRanOnWorkerThreads(observed, workMethod);
    }
  }

  private static void assertSameWorkerDifferentTraces(Path dir, String workMethod)
      throws IOException {
    var first = read(dir, "reuse", Requests.id(21));
    var second = read(dir, "reuse", Requests.id(22));
    Verdict.ownWorkNestedUnderTheSubmittingSpan(first, Requests.id(21), SKUS, workMethod);
    Verdict.ownWorkNestedUnderTheSubmittingSpan(second, Requests.id(22), SKUS, workMethod);
    assertThat(threadsOf(first, workMethod))
        .as("both requests were handed over to the one pool thread")
        .hasSize(1)
        .isEqualTo(threadsOf(second, workMethod));
    assertThat(first.traceIds()).doesNotContainAnyElementsOf(second.traceIds());
  }

  private static Set<String> threadsOf(Observed observed, String workMethod) {
    var names = new TreeSet<String>();
    observed.named(workMethod).stream().map(FlatNode::thread).forEach(names::add);
    return names;
  }

  private static Observed read(Path dir, String phase, String requestId) throws IOException {
    var file = dir.resolve(phase + "-" + requestId + ".txt");
    assertThat(file).as("the fork exported a trace for %s in phase %s", requestId, phase).exists();
    return Observed.read(Files.readString(file));
  }

  /** One forked run of {@link AgentFixtureMain}; fails unless it exits 0 and says it finished. */
  private static Path fork(String scenario) throws Exception {
    var outDir = Files.createDirectories(tempDir.resolve(scenario));
    var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var process =
        new ProcessBuilder(
                java,
                "-javaagent:"
                    + required("narrativetrace.test.standaloneJar")
                    + "=packages=ai.narrativetrace.concurrency.shop",
                "-cp",
                required("narrativetrace.test.forkedClasspath"),
                AgentFixtureMain.class.getName(),
                scenario,
                outDir.toString())
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .start();
    var console = new String(process.getInputStream().readAllBytes());
    var finished = process.waitFor(120, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    assertThat(finished).as("forked JVM must terminate; output so far:%n%s", console).isTrue();
    assertThat(process.exitValue()).as("forked JVM exit code; output:%n%s", console).isZero();
    assertThat(console).contains("SCENARIO_DONE " + scenario);
    return outDir;
  }

  private static String required(String property) {
    var value = System.getProperty(property);
    assertThat(value).as("%s system property", property).isNotNull();
    assertThat(new File(value.split(File.pathSeparator)[0])).exists();
    return value;
  }
}
