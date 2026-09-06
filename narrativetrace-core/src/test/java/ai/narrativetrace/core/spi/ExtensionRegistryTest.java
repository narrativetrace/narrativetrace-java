/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.spi.ReportContributor;
import ai.narrativetrace.api.spi.TraceEventListener;
import ai.narrativetrace.core.config.ConfigResolver;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionRegistryTest {

  @TempDir Path tempDir;

  /** Ordinary provider. */
  public static class FirstListener implements TraceEventListener {
    @Override
    public void onEvent(TraceEvent event) {
      // no-op fixture
    }
  }

  /** Second ordinary provider, to prove one failure does not take out its neighbours. */
  public static class SecondListener implements TraceEventListener {
    @Override
    public void onEvent(TraceEvent event) {
      // no-op fixture
    }
  }

  /** Provider whose construction fails, standing in for a broken extension jar. */
  public static class ThrowingConstructorListener implements TraceEventListener {
    public ThrowingConstructorListener() {
      throw new IllegalStateException("deliberately broken provider");
    }

    @Override
    public void onEvent(TraceEvent event) {
      // unreachable
    }
  }

  @AfterEach
  void clearDiscoveryProperties() {
    System.clearProperty(ExtensionRegistry.DISCOVERY_KEY);
    System.clearProperty(ExtensionRegistry.DISABLED_KEY);
  }

  @Test
  void discoversProvidersDeclaredOnTheClasspath() throws IOException {
    var registry = registryOver(declaring(FirstListener.class, SecondListener.class));

    var listeners = registry.load(TraceEventListener.class);

    assertThat(listeners)
        .hasSize(2)
        .hasAtLeastOneElementOfType(FirstListener.class)
        .hasAtLeastOneElementOfType(SecondListener.class);
  }

  @Test
  void returnsEmptyListWhenNothingIsDeclared() throws IOException {
    var registry = registryOver(declaring());

    assertThat(registry.load(TraceEventListener.class)).isEmpty();
  }

  @Test
  void discoversOnlyOncePerServiceType() throws IOException {
    var registry = registryOver(declaring(FirstListener.class));

    var first = registry.load(TraceEventListener.class);
    var second = registry.load(TraceEventListener.class);

    assertThat(second).isSameAs(first);
  }

  @Test
  void rejectsNullServiceType() throws IOException {
    var registry = registryOver(declaring());

    assertThatThrownBy(() -> registry.load(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serviceType");
  }

  @Test
  void killSwitchDisablesDiscoveryEntirely() throws IOException {
    System.setProperty(ExtensionRegistry.DISCOVERY_KEY, "off");
    var registry = registryOver(declaring(FirstListener.class, SecondListener.class));

    assertThat(registry.load(TraceEventListener.class)).isEmpty();
  }

  @Test
  void killSwitchIsCaseInsensitiveAndIgnoresSurroundingSpace() throws IOException {
    System.setProperty(ExtensionRegistry.DISCOVERY_KEY, "  OFF  ");
    var registry = registryOver(declaring(FirstListener.class));

    assertThat(registry.load(TraceEventListener.class)).isEmpty();
  }

  @Test
  void anyOtherDiscoveryValueLeavesDiscoveryOn() throws IOException {
    System.setProperty(ExtensionRegistry.DISCOVERY_KEY, "on");
    var registry = registryOver(declaring(FirstListener.class));

    assertThat(registry.load(TraceEventListener.class)).hasSize(1);
  }

  @Test
  void disabledListSkipsOnlyTheNamedProvider() throws IOException {
    System.setProperty(ExtensionRegistry.DISABLED_KEY, FirstListener.class.getName());
    var registry = registryOver(declaring(FirstListener.class, SecondListener.class));

    assertThat(registry.load(TraceEventListener.class))
        .singleElement()
        .isInstanceOf(SecondListener.class);
  }

  @Test
  void disabledListToleratesSpacesAndEmptyEntries() throws IOException {
    System.setProperty(
        ExtensionRegistry.DISABLED_KEY, " , " + FirstListener.class.getName() + " ,, ");
    var registry = registryOver(declaring(FirstListener.class, SecondListener.class));

    assertThat(registry.load(TraceEventListener.class))
        .singleElement()
        .isInstanceOf(SecondListener.class);
  }

  @Test
  void providerThatFailsToConstructIsSkippedAndItsNeighboursSurvive() throws IOException {
    var registry =
        registryOver(
            declaring(
                FirstListener.class, ThrowingConstructorListener.class, SecondListener.class));

    assertThat(registry.load(TraceEventListener.class))
        .hasSize(2)
        .hasAtLeastOneElementOfType(FirstListener.class)
        .hasAtLeastOneElementOfType(SecondListener.class);
  }

  @Test
  void providerClassThatCannotBeFoundIsSkippedAndItsNeighboursSurvive() throws IOException {
    var loader =
        declaringNames(
            FirstListener.class.getName(),
            "ai.narrativetrace.core.spi.NoSuchListenerOnThisClasspath",
            SecondListener.class.getName());

    assertThat(registryOver(loader).load(TraceEventListener.class)).hasSize(2);
  }

  /**
   * A syntactically invalid entry is unrecoverable for the rest of that declaration file — the JDK
   * parser cannot resume past it — so the guarantee is narrower than for a missing class: discovery
   * yields nothing from that file but never throws, and the registry stays usable.
   */
  @Test
  void malformedServicesEntryYieldsNoProvidersButNeverThrows() throws IOException {
    var loader = declaringNames("not a valid class name", SecondListener.class.getName());
    var registry = registryOver(loader);

    assertThat(registry.load(TraceEventListener.class)).isEmpty();
    assertThat(registry.load(ReportContributor.class)).isEmpty();
  }

  private ExtensionRegistry registryOver(ClassLoader loader) {
    return new ExtensionRegistry(new ConfigResolver(loader), loader);
  }

  /**
   * Builds a class loader whose only contribution is a {@code META-INF/services} declaration; the
   * provider classes themselves resolve through the parent loader.
   */
  private ClassLoader declaring(Class<?>... providers) throws IOException {
    return declaringNames(List.of(providers).stream().map(Class::getName).toArray(String[]::new));
  }

  private ClassLoader declaringNames(String... providerClassNames) throws IOException {
    var services = tempDir.resolve("META-INF/services");
    Files.createDirectories(services);
    Files.write(services.resolve(TraceEventListener.class.getName()), List.of(providerClassNames));
    return new URLClassLoader(
        new URL[] {tempDir.toUri().toURL()}, ExtensionRegistryTest.class.getClassLoader());
  }
}
