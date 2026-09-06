/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Safety properties from plan section 10: merging is additive-only (never deletes or rewrites an
 * existing entry) and idempotent (re-merging the same harvest changes nothing, byte-for-byte).
 */
class GlossaryMergerProperties {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC);

  private final GlossaryMerger merger = new GlossaryMerger(FIXED_CLOCK);
  private final GlossaryJsonWriter writer = new GlossaryJsonWriter();

  @Property
  void mergeNeverRemovesOrMutatesExistingEntries(
      @ForAll("glossaries") Glossary existing, @ForAll("harvests") HarvestResult harvest) {
    var merged = merger.merge(existing, harvest).glossary();

    assertThat(merged.terms()).containsAll(existing.terms());
    assertThat(merged.contexts()).containsAllEntriesOf(existing.contexts());
    assertThat(merged.schemaVersion()).isEqualTo(existing.schemaVersion());
  }

  @Property
  void mergingTheSameHarvestTwiceIsByteIdentical(
      @ForAll("glossaries") Glossary existing, @ForAll("harvests") HarvestResult harvest) {
    var once = merger.merge(existing, harvest);
    var twice = merger.merge(once.glossary(), harvest);

    assertThat(twice.glossary()).isEqualTo(once.glossary());
    assertThat(twice.newTerms()).isEmpty();
    assertThat(writer.write(twice.glossary())).isEqualTo(writer.write(once.glossary()));
  }

  @Provide
  Arbitrary<Glossary> glossaries() {
    return GlossaryArbitraries.glossaries();
  }

  @Provide
  Arbitrary<HarvestResult> harvests() {
    return GlossaryArbitraries.harvests();
  }

  @Property
  void mergeNeverTouchesTheHumanOwnedAbbreviationsSection(
      @ForAll("glossaries") Glossary existing, @ForAll("harvests") HarvestResult harvest) {
    var merged = merger.merge(existing, harvest).glossary();

    assertThat(merged.abbreviations()).isEqualTo(existing.abbreviations());
  }

  @Property
  void everyWrittenGlossaryReadsBackIdenticalWhateverItsSchema(
      @ForAll("glossaries") Glossary existing) {
    var text = writer.write(existing);
    var reparsed = new GlossaryJsonReader().read(text);

    assertThat(reparsed.abbreviations()).isEqualTo(existing.abbreviations());
    assertThat(reparsed.terms()).isEqualTo(existing.terms());
    assertThat(writer.write(reparsed)).isEqualTo(text);
  }

  @Property
  void theSchemaStampFollowsTheSectionAndNothingElse(@ForAll("glossaries") Glossary existing) {
    var stamped = new GlossaryJsonReader().read(writer.write(existing)).schemaVersion();

    assertThat(stamped).isEqualTo(existing.abbreviations().isEmpty() ? 1 : 2);
  }
}
