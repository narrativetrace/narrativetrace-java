/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * The zero-code legacy story, end to end (plan Phase 2 milestones 3–4): the unmodified WAR is
 * deployed to WildFly in Docker with only {@code -javaagent} attached, one HTTP request runs the
 * servlet → EJB-proxy → bean → service chain, and the narration for that chain is asserted from the
 * container log. The WAR has no NarrativeTrace dependency of any kind.
 *
 * <p>Spike gotchas baked in (plan §Spike findings): the image silently ignores {@code
 * JAVA_OPTS_APPEND}, so the full {@code JAVA_OPTS} is passed; instrumented deployment classes
 * resolve {@code AgentRuntime} only with {@code
 * -Djboss.modules.system.pkgs=…,ai.narrativetrace.agent} (exactly that package — see the JAVA_OPTS
 * comment); agent attachment is asserted from the boot log — never inferred from a healthy app;
 * narration logs at SLF4J TRACE, so the slf4j-simple level is forced to trace.
 */
@Tag("docker")
class WildFlyAgentNarrationTest {

  private static final String WILDFLY_IMAGE = "quay.io/wildfly/wildfly:40.0.1.Final-jdk17";
  private static final String AGENT_JAR = "/opt/narrativetrace/narrativetrace-agent.jar";
  private static final String PROVIDER_JAR = "/opt/narrativetrace/slf4j-simple.jar";
  private static final String DEPLOYMENTS = "/opt/jboss/wildfly/standalone/deployments/";
  private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);

  // Full JAVA_OPTS (image defaults + agent): JAVA_OPTS_APPEND is silently ignored by this image.
  private static final String JAVA_OPTS =
      String.join(
          " ",
          "-XX:MetaspaceSize=96M",
          "-XX:MaxMetaspaceSize=256m",
          "-Djava.net.preferIPv4Stack=true",
          "-Djava.awt.headless=true",
          // Exactly the agent package, nothing wider: system.pkgs makes matching packages
          // parent-first from the system classpath, and this WAR's own classes live under
          // ai.narrativetrace.examples — a broader prefix (the spike's ai.narrativetrace)
          // makes WildFly load the WAR's beans from the system classpath, which fails the
          // deployment with ClassNotFoundException. Injected bytecode only needs
          // ai.narrativetrace.agent.AgentRuntime resolvable.
          "-Djboss.modules.system.pkgs=org.jboss.byteman,ai.narrativetrace.agent",
          "-Dorg.slf4j.simpleLogger.defaultLogLevel=trace",
          "-javaagent:"
              + AGENT_JAR
              + "=packages=ai.narrativetrace.examples.ejb4,loggingJars="
              + PROVIDER_JAR);

  @Test
  void narratesServletToEjbChainFromUnmodifiedWar() throws Exception {
    try (var wildfly = wildFlyWithAgent()) {
      wildfly.start();

      // Gotcha: a healthy app proves nothing — assert the agent is actually attached.
      assertThat(wildfly.getLogs())
          .as("boot log must show the agent on the JVM command line")
          .contains("-javaagent:" + AGENT_JAR);

      var body = fileClaim(wildfly);

      assertThat(body).contains("claim CLM-1 on POL-1001:");
      var narration = awaitNarration(wildfly);
      assertThat(narration)
          .contains("→ ClaimsServlet.doGet(")
          .contains("→ ClaimsProcessorBean.processClaim(")
          .contains("→ PolicyLookupEJB.findPolicyByNumber(")
          .contains("→ FraudChkMgr.chkClaim(")
          .contains("→ CoverageCalcEJB.calcPayout(")
          .contains("← returned:");
    }
  }

  private static GenericContainer<?> wildFlyWithAgent() {
    return new GenericContainer<>(WILDFLY_IMAGE)
        .withCopyFileToContainer(hostFile("narrativetrace.test.standaloneJar"), AGENT_JAR)
        .withCopyFileToContainer(hostFile("narrativetrace.test.slf4jProviderJar"), PROVIDER_JAR)
        // Fixed target name pins the context root to /legacy regardless of the WAR's version.
        .withCopyFileToContainer(hostFile("narrativetrace.test.warFile"), DEPLOYMENTS + "ejb4.war")
        .withEnv("JAVA_OPTS", JAVA_OPTS)
        .withExposedPorts(8080)
        // Not "Deployed \"ejb4.war\"": WildFly logs that line even when the deployment
        // FAILED (WFLYCTL0186 failed services). The Undertow context registration is the
        // signal that the web app is actually serving.
        .waitingFor(Wait.forLogMessage(".*WFLYUT0021: Registered web context: '/ejb4'.*", 1))
        .withStartupTimeout(STARTUP_TIMEOUT);
  }

  private static MountableFile hostFile(String property) {
    var path = System.getProperty(property);
    assertThat(path).as("system property %s (set by the dockerTest task)", property).isNotNull();
    return MountableFile.forHostPath(path);
  }

  private static String fileClaim(GenericContainer<?> wildfly) throws Exception {
    var uri =
        URI.create(
            "http://%s:%d/ejb4/claims?policy=POL-1001&amountCents=800000&description=kitchen+water+damage"
                .formatted(wildfly.getHost(), wildfly.getMappedPort(8080)));
    // No try-with-resources: HttpClient implements AutoCloseable only from Java 21.
    var client = HttpClient.newHttpClient();
    var response =
        client.send(
            HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .as("GET %s answered: %s", uri, response.body())
        .isEqualTo(200);
    return response.body();
  }

  /**
   * The docker log stream can lag the response slightly; poll until the request chain's outermost
   * and innermost enters are both visible. Not "← returned:" — startup seeding already logs that.
   */
  private static String awaitNarration(GenericContainer<?> wildfly) throws InterruptedException {
    var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    var logs = wildfly.getLogs();
    while (!(logs.contains("→ ClaimsServlet.doGet(")
            && logs.contains("→ CoverageCalcEJB.calcPayout("))
        && System.nanoTime() < deadline) {
      Thread.sleep(500);
      logs = wildfly.getLogs();
    }
    return logs;
  }
}
