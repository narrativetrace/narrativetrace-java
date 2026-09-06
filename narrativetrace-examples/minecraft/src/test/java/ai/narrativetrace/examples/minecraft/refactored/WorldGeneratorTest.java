/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft.refactored;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

  @Test
  void generateChunkReturnsChunkWithCoordinates() {
    WorldGenerator generator = new DefaultWorldGenerator();

    Chunk chunk = generator.generateChunk(3, 7);

    assertThat(chunk.x()).isEqualTo(3);
    assertThat(chunk.z()).isEqualTo(7);
    assertThat(chunk.biome()).isNotBlank();
  }
}
