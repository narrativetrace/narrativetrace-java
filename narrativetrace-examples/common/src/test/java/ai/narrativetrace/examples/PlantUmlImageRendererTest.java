/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlantUmlImageRendererTest {

  @Test
  void rendersSinglePumlFileToSvg(@TempDir Path tempDir) throws Exception {
    var puml = tempDir.resolve("test.puml");
    Files.writeString(puml, "@startuml\nBob -> Alice : hello\n@enduml");

    PlantUmlImageRenderer.renderDirectory(tempDir);

    var svg = tempDir.resolve("test.svg");
    assertThat(svg).exists();
    var content = Files.readString(svg);
    assertThat(content).contains("<svg");
    assertThat(content).contains("Bob");
  }

  @Test
  void rendersNestedPumlFiles(@TempDir Path tempDir) throws Exception {
    var subdir = tempDir.resolve("traces/OrderTest");
    Files.createDirectories(subdir);
    Files.writeString(subdir.resolve("order.puml"), "@startuml\nA -> B : call\n@enduml");
    Files.writeString(subdir.resolve("payment.puml"), "@startuml\nC -> D : pay\n@enduml");

    PlantUmlImageRenderer.renderDirectory(tempDir);

    assertThat(subdir.resolve("order.svg")).exists();
    assertThat(subdir.resolve("payment.svg")).exists();
  }

  @Test
  void skipsDirectoryWithNoPumlFiles(@TempDir Path tempDir) throws Exception {
    Files.writeString(tempDir.resolve("readme.md"), "# hello");

    PlantUmlImageRenderer.renderDirectory(tempDir);

    try (var files = Files.list(tempDir)) {
      assertThat(files.filter(p -> p.toString().endsWith(".svg")).toList()).isEmpty();
    }
  }
}
