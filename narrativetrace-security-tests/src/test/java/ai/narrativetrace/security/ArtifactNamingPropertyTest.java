/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.core.output.OutputDirectoryResolver;
import ai.narrativetrace.security.corpus.CorpusCase;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.oracle.Emitters;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The writers' other input: not the value, the <em>name</em>.
 *
 * <p>INTENT: Every other target in this suite feeds hostile data through a renderer. This one feeds
 * it through the path builder, because a trace artifact's location is derived from a test class and
 * method name, and {@code TraceTestSupport} is public API whose callers do not all derive those
 * from {@code Class.getName()} — a scenario name, an HTTP route or a JUnit display name reaches
 * these methods in real integrations.
 *
 * <p><b>@llmNote</b> The limit asserted here is 255 <em>bytes</em>, not characters: ext4, APFS and
 * every other mainstream filesystem count bytes, so 200 three-byte characters overflow a component
 * that 200 ASCII ones fit inside. The corpus carries that exact case, and a character-counting cap
 * passes it while the filesystem refuses the write.
 */
class ArtifactNamingPropertyTest {

  /** The per-component limit ext4, APFS and NTFS share. */
  private static final int MAX_COMPONENT_BYTES = 255;

  @Test
  void everyCorpusNameWritesEveryArtifactWithoutThrowing(@TempDir Path sandbox) {
    var tree = Emitters.treeOf("\"probe\"", "\"result\"");
    for (var name : HostileCorpus.names()) {
      var output = sandboxFor(sandbox, name.id()).resolve("out");
      assertThatCode(() -> Emitters.writtenArtifacts(tree, output, name.value(), "m"))
          .as("class name %s: %s", name.id(), name.description())
          .doesNotThrowAnyException();
      assertThatCode(() -> Emitters.writtenArtifacts(tree, output, "cls", name.value()))
          .as("method name %s: %s", name.id(), name.description())
          .doesNotThrowAnyException();
    }
  }

  /**
   * The escape this found: {@code diagrams/} and {@code structural/} resolved the class name
   * verbatim, so {@code ../../../tmp/x} put artifacts at an <em>absolute</em> path outside the
   * output directory the caller gave. The sandbox is one level above the output directory, so
   * anything that walked out of it lands somewhere this assertion can see.
   */
  @Test
  void everyArtifactAHostileNameProducesStaysInsideTheOutputDirectory(@TempDir Path sandbox) {
    var tree = Emitters.treeOf("\"probe\"", "\"result\"");
    for (var name : HostileCorpus.names()) {
      var enclosure = sandboxFor(sandbox, name.id());
      var output = enclosure.resolve("out");

      Emitters.writtenArtifacts(tree, output, name.value(), name.value());

      var written = filesUnder(enclosure);
      assertThat(written).as("%s wrote nothing, so nothing was checked", name.id()).isNotEmpty();
      assertThat(written)
          .as("%s: %s", name.id(), name.description())
          .allSatisfy(file -> assertThat(file).startsWith(output));
    }
  }

  @Test
  void noPathComponentAHostileNameProducesExceedsTheFilesystemLimit(@TempDir Path dir) {
    var resolver = new OutputDirectoryResolver(dir);
    for (var name : HostileCorpus.names()) {
      assertComponentsFit(dir, resolver.traceFile(name.value(), "m"), name);
      assertComponentsFit(dir, resolver.traceFile("cls", name.value()), name);
    }
  }

  /**
   * Truncation without disambiguation is a silent overwrite: two 300-character names sharing their
   * first 240 characters would land on one artifact, and one test's approved baseline would judge
   * another test's trace.
   */
  @Test
  void namesThatDifferOnlyPastTheLimitStillResolveToDifferentArtifacts(@TempDir Path dir) {
    var resolver = new OutputDirectoryResolver(dir);
    var seen = new HashMap<Path, String>();
    for (var name : HostileCorpus.names()) {
      if (!name.id().startsWith("long-")) {
        continue;
      }
      var file = resolver.traceFile("cls", name.value() + name.id());
      assertThat(seen.put(file, name.id()))
          .as("%s collides with %s at %s", name.id(), seen.get(file), file.getFileName())
          .isNull();
    }
  }

  @Test
  void resolvingTheSameNameTwiceAlwaysGivesTheSamePath(@TempDir Path dir) {
    var resolver = new OutputDirectoryResolver(dir);
    for (var name : HostileCorpus.names()) {
      assertThat(resolver.traceFile(name.value(), name.value()))
          .as("%s must resolve deterministically", name.id())
          .isEqualTo(resolver.traceFile(name.value(), name.value()));
    }
  }

  private static Path sandboxFor(Path sandbox, String id) {
    try {
      return Files.createDirectories(sandbox.resolve("case-" + id));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<Path> filesUnder(Path directory) {
    try (Stream<Path> files = Files.walk(directory)) {
      return files.filter(Files::isRegularFile).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void assertComponentsFit(Path base, Path resolved, CorpusCase name) {
    for (var component : base.relativize(resolved)) {
      assertThat(component.toString().getBytes(StandardCharsets.UTF_8).length)
          .as("%s: component '%s' must fit a filesystem path element", name.id(), component)
          .isLessThanOrEqualTo(MAX_COMPONENT_BYTES);
    }
  }
}
