/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Safety property: serialization is deterministic — {@code write ∘ read ∘ write == write} for any
 * structurally valid glossary (plan section 10).
 */
class GlossarySerializationProperties {

  private final GlossaryJsonWriter writer = new GlossaryJsonWriter();
  private final GlossaryJsonReader reader = new GlossaryJsonReader();

  @Property
  void writeReadWriteProducesIdenticalBytes(@ForAll("glossaries") Glossary glossary) {
    var firstWrite = writer.write(glossary);
    var reread = reader.read(firstWrite);

    assertThat(reread).isEqualTo(glossary);
    assertThat(writer.write(reread)).isEqualTo(firstWrite);
  }

  @Provide
  Arbitrary<Glossary> glossaries() {
    return GlossaryArbitraries.glossaries();
  }
}
