/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.spi.TraceEventListener;
import ai.narrativetrace.core.config.ConfigResolver;
import ai.narrativetrace.core.spi.ExtensionRegistry;
import ai.narrativetrace.core.spi.ServiceDeclarations;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PipelineBootstrapTest {

  @TempDir Path tempDir;

  private EventPipeline pipeline;

  /** Records what it receives, so listener placement can be asserted through real publishing. */
  public static class RecordingListener implements TraceEventListener {
    static final List<TraceEvent> RECEIVED = new ArrayList<>();

    @Override
    public void onEvent(TraceEvent event) {
      RECEIVED.add(event);
    }
  }

  /** Fails on every event, standing in for a misbehaving extension. */
  public static class ThrowingListener implements TraceEventListener {
    static int invocations;

    @Override
    public void onEvent(TraceEvent event) {
      invocations++;
      throw new IllegalStateException("deliberately broken listener");
    }
  }

  /** Replacement topology that records the specs it was composed with. */
  public static class RecordingFactory implements EventPipelineFactory {
    static final List<PipelineSpec> SPECS = new ArrayList<>();

    @Override
    public String name() {
      return "recording";
    }

    @Override
    public EventPipeline create(PipelineSpec spec) {
      SPECS.add(spec);
      return new DualPathPipeline();
    }
  }

  /** Misbehaving strategy: claims a name, then hands back nothing. */
  public static class NullReturningFactory implements EventPipelineFactory {
    @Override
    public String name() {
      return "null-returning";
    }

    @Override
    public EventPipeline create(PipelineSpec spec) {
      return null;
    }
  }

  @BeforeEach
  void resetFixtures() {
    RecordingListener.RECEIVED.clear();
    ThrowingListener.invocations = 0;
    RecordingFactory.SPECS.clear();
  }

  @AfterEach
  void cleanUp() {
    if (pipeline != null) {
      pipeline.close();
    }
    System.clearProperty(PipelineBootstrap.STRATEGY_KEY);
    System.clearProperty(PipelineBootstrap.NARRATION_KEY);
    System.clearProperty(PipelineBootstrap.CAPACITY_KEY);
  }

  @Test
  void buildsTheDefaultCapturingTopologyWhenNothingIsConfiguredOrDeclared() throws IOException {
    pipeline = bootstrapWith(declaringNoListeners()).build(null);

    assertThat(pipeline).isInstanceOf(DualPathPipeline.class);
    assertThat(((DualPathPipeline) pipeline).retainsEvents()).isTrue();
  }

  /**
   * The acceptance bar for zero-overhead defaults: with nothing discovered, the best-effort slot
   * holds the retaining consumer itself — no fan-out composite is allocated around it.
   */
  @Test
  void allocatesNoFanoutWhenNoListenersAreDeclared() throws IOException {
    pipeline = bootstrapWith(declaringNoListeners()).build(null);

    assertThat(((DualPathPipeline) pipeline).bestEffortConsumer())
        .isInstanceOf(BufferedEventConsumer.class)
        .isNotInstanceOf(ListenerFanoutConsumer.class);
  }

  @Test
  void bufferCapacityKeySizesTheDefaultTopologysRing() throws IOException {
    System.setProperty(PipelineBootstrap.CAPACITY_KEY, "4096");

    pipeline = bootstrapWith(declaringNoListeners()).build(null);

    assertThat(ringCapacityOf(pipeline)).isEqualTo(4096);
  }

  /**
   * A mis-typed observability knob must not stop an application from starting, so every value the
   * ring cannot honour degrades to the default. {@code 2147483647} is the one that matters: the
   * buffer's power-of-two rounding overflows above 2^30, and an unguarded pass-through would throw
   * {@code NegativeArraySizeException} out of the composition root.
   */
  @ParameterizedTest
  @ValueSource(strings = {"not-a-number", "", "   ", "0", "-1", "-65536", "6.5", "2147483647"})
  void unusableBufferCapacityDegradesToTheDefault(String configured) throws IOException {
    System.setProperty(PipelineBootstrap.CAPACITY_KEY, configured);

    pipeline = bootstrapWith(declaringNoListeners()).build(null);

    assertThat(ringCapacityOf(pipeline)).isEqualTo(BufferedEventConsumer.DEFAULT_CAPACITY);
  }

  /**
   * The seam {@code narrativetrace-junit5} uses: an integration that knows its workload passes its
   * own default, and core never has to know which integration it is.
   */
  @Test
  void anIntegrationsDefaultCapacitySizesTheRingWhenNothingIsConfigured() throws IOException {
    pipeline = bootstrapWith(declaringNoListeners()).build(null, 8192);

    assertThat(ringCapacityOf(pipeline)).isEqualTo(8192);
  }

  @Test
  void aConfiguredCapacityOutranksTheIntegrationsDefault() throws IOException {
    System.setProperty(PipelineBootstrap.CAPACITY_KEY, "1024");

    pipeline = bootstrapWith(declaringNoListeners()).build(null, 8192);

    assertThat(ringCapacityOf(pipeline)).isEqualTo(1024);
  }

  @Test
  void anUnusableConfiguredCapacityFallsBackToTheIntegrationsDefaultNotTheLibrarys()
      throws IOException {
    System.setProperty(PipelineBootstrap.CAPACITY_KEY, "not-a-number");

    pipeline = bootstrapWith(declaringNoListeners()).build(null, 8192);

    assertThat(ringCapacityOf(pipeline)).isEqualTo(8192);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
  void anIntegrationCannotAskForARingTheBufferCannotBuild(int capacity) throws IOException {
    var bootstrap = bootstrapWith(declaringNoListeners());

    assertThatThrownBy(() -> bootstrap.build(null, capacity))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("defaultBufferCapacity");
  }

  @Test
  void theDefaultCapacityIsTheLibrarysWhenNoIntegrationChoosesOne() throws IOException {
    pipeline = bootstrapWith(declaringNoListeners()).build(null);

    assertThat(ringCapacityOf(pipeline)).isEqualTo(BufferedEventConsumer.DEFAULT_CAPACITY);
  }

  @Test
  void bufferCapacityRoundsUpToThePowerOfTwoTheRingCanAddress() throws IOException {
    System.setProperty(PipelineBootstrap.CAPACITY_KEY, "3000");

    pipeline = bootstrapWith(declaringNoListeners()).build(null);

    assertThat(ringCapacityOf(pipeline)).isEqualTo(4096);
  }

  @Test
  void aConfiguredCapacityAlsoSizesTheRingBehindDiscoveredListeners() throws IOException {
    System.setProperty(PipelineBootstrap.CAPACITY_KEY, "512");

    pipeline = bootstrapWith(declaring(RecordingListener.class)).build(null);

    var fanout = (ListenerFanoutConsumer) ((DualPathPipeline) pipeline).bestEffortConsumer();
    assertThat(((BufferedEventConsumer) fanout.retention()).bufferCapacity()).isEqualTo(512);
  }

  @Test
  void placesDiscoveredListenersBesideRetentionOnTheBestEffortSlot() throws IOException {
    pipeline = bootstrapWith(declaring(RecordingListener.class)).build(null);
    var event = enterEvent();

    pipeline.publish(event);
    pipeline.flush();

    assertThat(RecordingListener.RECEIVED).containsExactly(event);
    assertThat(pipeline.events()).containsExactly(event);
  }

  @Test
  void listenerThatThrowsIsDisabledAndRetentionKeepsWorking() throws IOException {
    pipeline = bootstrapWith(declaring(ThrowingListener.class)).build(null);

    pipeline.publish(enterEvent());
    pipeline.publish(enterEvent());
    pipeline.flush();

    assertThat(ThrowingListener.invocations).isOne();
    assertThat(pipeline.events()).hasSize(2);
  }

  @Test
  void namedTopologyIsBuiltByTheMatchingDiscoveredFactory() throws IOException {
    System.setProperty(PipelineBootstrap.STRATEGY_KEY, "recording");
    var loader =
        ServiceDeclarations.declaring(tempDir, EventPipelineFactory.class, RecordingFactory.class);

    pipeline = bootstrapWith(loader).build(null);

    assertThat(RecordingFactory.SPECS).hasSize(1);
  }

  @Test
  void namedTopologyMatchesCaseInsensitively() throws IOException {
    System.setProperty(PipelineBootstrap.STRATEGY_KEY, "RECORDING");
    var loader =
        ServiceDeclarations.declaring(tempDir, EventPipelineFactory.class, RecordingFactory.class);

    pipeline = bootstrapWith(loader).build(null);

    assertThat(RecordingFactory.SPECS).hasSize(1);
  }

  @Test
  void namedTopologyHandsDiscoveredListenersToTheFactory() throws IOException {
    System.setProperty(PipelineBootstrap.STRATEGY_KEY, "recording");
    ServiceDeclarations.declaring(tempDir, TraceEventListener.class, RecordingListener.class);
    var loader =
        ServiceDeclarations.declaring(tempDir, EventPipelineFactory.class, RecordingFactory.class);

    pipeline = bootstrapWith(loader).build(null);

    assertThat(RecordingFactory.SPECS.get(0).listeners())
        .singleElement()
        .isInstanceOf(RecordingListener.class);
  }

  @Test
  void factoryReadsItsOwnNamespacedSettingsAndNothingElse() throws IOException {
    System.setProperty(PipelineBootstrap.STRATEGY_KEY, "recording");
    System.setProperty("narrativetrace.pipeline.recording.ringSize", "4096");
    var loader =
        ServiceDeclarations.declaring(tempDir, EventPipelineFactory.class, RecordingFactory.class);
    try {
      pipeline = bootstrapWith(loader).build(null);

      var settings = RecordingFactory.SPECS.get(0).settings();
      assertThat(settings.get("ringSize", "unset")).isEqualTo("4096");
      assertThat(settings.get("unconfigured", "fallback")).isEqualTo("fallback");
    } finally {
      System.clearProperty("narrativetrace.pipeline.recording.ringSize");
    }
  }

  @Test
  void namedTopologyWithNoMatchingFactoryFailsFastInsteadOfFallingBack() throws IOException {
    System.setProperty(PipelineBootstrap.STRATEGY_KEY, "chronicle");
    var bootstrap = bootstrapWith(declaringNoListeners());

    assertThatThrownBy(() -> bootstrap.build(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("chronicle")
        .hasMessageContaining(PipelineBootstrap.STRATEGY_KEY);
  }

  @Test
  void blankStrategyValueIsTreatedAsUnconfigured() throws IOException {
    System.setProperty(PipelineBootstrap.STRATEGY_KEY, "   ");

    pipeline = bootstrapWith(declaringNoListeners()).build(null);

    assertThat(pipeline).isInstanceOf(DualPathPipeline.class);
  }

  /**
   * Core carries no slf4j dependency, so asking for narration here must degrade to a working,
   * silent pipeline rather than failing — the shape of a core-only deployment.
   */
  @Test
  void requestingNarrationWithoutTheSlf4jModuleStillYieldsAWorkingPipeline() throws IOException {
    pipeline = bootstrapWith(declaringNoListeners()).build("narrativetrace");

    pipeline.publish(enterEvent());
    pipeline.flush();

    assertThat(pipeline.events()).hasSize(1);
  }

  @Test
  void narrationVetoSuppressesTheLookupEntirely() throws IOException {
    System.setProperty(PipelineBootstrap.NARRATION_KEY, "off");

    pipeline = bootstrapWith(declaringNoListeners()).build("narrativetrace");

    assertThat(pipeline).isInstanceOf(DualPathPipeline.class);
    assertThat(((DualPathPipeline) pipeline).retainsEvents()).isTrue();
  }

  @Test
  void staticEntryPointsBuildTheDefaultTopology() {
    pipeline = PipelineBootstrap.createDefault();

    assertThat(pipeline).isInstanceOf(DualPathPipeline.class);
    assertThat(((DualPathPipeline) pipeline).retainsEvents()).isTrue();

    pipeline.close();
    pipeline = PipelineBootstrap.createDefault(PipelineBootstrap.DEFAULT_LOGGER_NAME);

    assertThat(pipeline).isInstanceOf(DualPathPipeline.class);
  }

  @Test
  void factoryReturningNoPipelineFailsRatherThanHandingBackNull() throws IOException {
    System.setProperty(PipelineBootstrap.STRATEGY_KEY, "null-returning");
    var loader =
        ServiceDeclarations.declaring(
            tempDir, EventPipelineFactory.class, NullReturningFactory.class);
    var bootstrap = bootstrapWith(loader);

    assertThatThrownBy(() -> bootstrap.build(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("null-returning");
  }

  @Test
  void rejectsNullCollaborators() {
    assertThatThrownBy(() -> new PipelineBootstrap(null, new ExtensionRegistry()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PipelineBootstrap(new ConfigResolver(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private PipelineBootstrap bootstrapWith(ClassLoader loader) {
    var config = new ConfigResolver(loader);
    return new PipelineBootstrap(config, new ExtensionRegistry(config, loader));
  }

  private ClassLoader declaring(Class<?> listener) throws IOException {
    return ServiceDeclarations.declaring(tempDir, TraceEventListener.class, listener);
  }

  private ClassLoader declaringNoListeners() throws IOException {
    return ServiceDeclarations.declaring(tempDir, TraceEventListener.class);
  }

  /** Reaches through the topology to the size the ring was actually built with. */
  private static int ringCapacityOf(EventPipeline pipeline) {
    var slot = ((DualPathPipeline) pipeline).bestEffortConsumer();
    return ((BufferedEventConsumer) slot).bufferCapacity();
  }

  private static TraceEvent enterEvent() {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(), System.nanoTime(), new MethodSignature("Foo", "bar", List.of()));
  }
}
