/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ElementNoteComposerTest {

  private final ElementNoteComposer composer = new ElementNoteComposer();

  @Test
  void methodNoteNamesGenericVerbAndVagueNoun() {
    assertThat(composer.methodNote("processData"))
        .isEqualTo("Generic verb 'process' + vague noun 'data'");
  }

  @Test
  void methodNoteNamesStandardVerbAndDomainNoun() {
    assertThat(composer.methodNote("saveReservation"))
        .isEqualTo("Standard verb 'save' + domain noun 'reservation'");
  }

  @Test
  void methodNoteNamesDomainVerbAndDomainNoun() {
    assertThat(composer.methodNote("reserveRoom"))
        .isEqualTo("Domain verb 'reserve' + domain noun 'room'");
  }

  @Test
  void methodNoteNamesBooleanPrefix() {
    assertThat(composer.methodNote("isActive"))
        .isEqualTo("Boolean prefix 'is' + domain noun 'active'");
  }

  @Test
  void methodNoteNamesUnknownVerbLikeFirstTokenAsUnknown() {
    assertThat(composer.methodNote("vaporizeData"))
        .isEqualTo("Unknown verb 'vaporize' + vague noun 'data'");
  }

  @Test
  void methodNoteNamesUnknownNonVerbFirstTokenAsUnclear() {
    assertThat(composer.methodNote("fizzleThing"))
        .isEqualTo("Unclear verb 'fizzle' + vague noun 'thing'");
  }

  @Test
  void methodNoteNamesBroadNounForTypedGeneric() {
    assertThat(composer.methodNote("updateStatus"))
        .isEqualTo("Standard verb 'update' + broad noun 'status'");
  }

  @Test
  void methodNoteNamesMeaninglessNoun() {
    assertThat(composer.methodNote("processFoo"))
        .isEqualTo("Generic verb 'process' + meaningless noun 'foo'");
  }

  @Test
  void methodNoteForSingleTokenUsesOnlyVerbPhrase() {
    assertThat(composer.methodNote("validate")).isEqualTo("Domain verb 'validate'");
  }

  @Test
  void methodNoteForSingleGenericTokenUsesOnlyVerbPhrase() {
    assertThat(composer.methodNote("process")).isEqualTo("Generic verb 'process'");
  }

  @Test
  void methodNoteAppendsRenameHintForGenericVerbWithKnownCollocations() {
    assertThat(composer.methodNote("processOrder"))
        .isEqualTo(
            "Generic verb 'process' + broad noun 'order'"
                + " — consider: backorderOrder, cancelOrder, fulfillOrder");
  }

  @Test
  void methodNoteAppendsRenameHintForUnknownVerbWithKnownCollocations() {
    assertThat(composer.methodNote("fizzleOrder"))
        .isEqualTo(
            "Unclear verb 'fizzle' + broad noun 'order'"
                + " — consider: backorderOrder, cancelOrder, fulfillOrder");
  }

  @Test
  void methodNoteOmitsRenameHintForDomainVerb() {
    assertThat(composer.methodNote("shipOrder"))
        .isEqualTo("Domain verb 'ship' + broad noun 'order'");
  }

  @Test
  void methodNoteSpellsOutNonUniversalAbbreviation() {
    assertThat(composer.methodNote("saveCfg"))
        .isEqualTo("Standard verb 'save' + domain noun 'cfg'; spell out: cfg → configuration");
  }

  @Test
  void methodNoteDoesNotSpellOutUniversalAbbreviation() {
    assertThat(composer.methodNote("saveUrl")).isEqualTo("Standard verb 'save' + broad noun 'url'");
  }

  @Test
  void classNoteNamesRecognizedRoleSuffix() {
    assertThat(composer.classNote("OrderService")).isEqualTo("Role suffix 'Service'");
  }

  @Test
  void classNoteFlagsGenericSuffix() {
    assertThat(composer.classNote("OrderManager"))
        .isEqualTo("Generic suffix 'Manager' — prefer a precise role");
  }

  @Test
  void classNoteNamesDomainNameWithoutConventionalSuffix() {
    assertThat(composer.classNote("CustomerAccount"))
        .isEqualTo("Domain name, no conventional suffix");
  }

  @Test
  void classNoteCallsOutVaguePrefix() {
    assertThat(composer.classNote("DataValidator"))
        .isEqualTo("Role suffix 'Validator'; vague prefix 'Data'");
  }

  @Test
  void classNoteSpellsOutAbbreviatedPrefix() {
    assertThat(composer.classNote("MgrService"))
        .isEqualTo("Role suffix 'Service'; spell out: mgr → manager");
  }

  @Test
  void parameterNoteNamesDomainSpecificNoun() {
    assertThat(composer.parameterNote("customerId")).isEqualTo("Domain-specific noun 'customerId'");
  }

  @Test
  void parameterNoteQualifiesBroadNoun() {
    assertThat(composer.parameterNote("amount"))
        .isEqualTo("Broad noun 'amount' — qualify it (e.g., orderAmount)");
  }

  @Test
  void parameterNoteAsksVagueNameToSayWhatItHolds() {
    assertThat(composer.parameterNote("data")).isEqualTo("Vague name 'data' — say what it holds");
  }

  @Test
  void parameterNoteFlagsMeaninglessName() {
    assertThat(composer.parameterNote("x")).isEqualTo("Meaningless name 'x'");
  }

  @Test
  void parameterNoteSpellsOutAbbreviation() {
    assertThat(composer.parameterNote("cfg"))
        .isEqualTo("Domain-specific noun 'cfg'; spell out: cfg → configuration");
  }

  @Test
  void propertyNoteNamesDomainSpecificComponent() {
    assertThat(composer.propertyNote("customerId"))
        .isEqualTo("Domain-specific component 'customerId'");
  }

  @Test
  void propertyNoteQualifiesBroadComponent() {
    assertThat(composer.propertyNote("amount"))
        .isEqualTo("Broad component 'amount' — qualify it (e.g., orderAmount)");
  }

  @Test
  void propertyNoteAsksVagueComponentToNameItsDomainConcept() {
    assertThat(composer.propertyNote("data"))
        .isEqualTo("Vague component 'data' — name the component after its domain concept");
  }

  @Test
  void propertyNoteFlagsMeaninglessComponent() {
    assertThat(composer.propertyNote("x"))
        .isEqualTo("Meaningless component 'x' — name the component after its domain concept");
  }

  @Test
  void propertyNoteSpellsOutAbbreviation() {
    assertThat(composer.propertyNote("cfg"))
        .isEqualTo("Domain-specific component 'cfg'; spell out: cfg → configuration");
  }

  @Test
  void teachesTheProjectsOwnExpansionForListedShorthand() {
    var composer =
        new ElementNoteComposer(
            DomainVocabulary.of(
                java.util.Set.of(), java.util.Set.of(), Map.of("fx", "foreign exchange")));

    assertThat(composer.methodNote("settleFx"))
        .as("accepted shorthand is taught, not corrected")
        .isEqualTo(
            "Domain verb 'settle' + domain noun 'fx'; project shorthand: fx → foreign exchange");
  }

  @Test
  void teachesListedShorthandOnClassParameterAndComponentNotesToo() {
    var composer =
        new ElementNoteComposer(
            DomainVocabulary.of(
                java.util.Set.of(), java.util.Set.of(), Map.of("cfg", "configuration")));

    assertThat(composer.classNote("CfgService")).contains("project shorthand: cfg → configuration");
    assertThat(composer.parameterNote("cfg")).contains("project shorthand: cfg → configuration");
    assertThat(composer.propertyNote("cfg")).contains("project shorthand: cfg → configuration");
  }

  @Test
  void listedShorthandIsNeverAlsoAskedToBeSpelledOut() {
    var composer =
        new ElementNoteComposer(
            DomainVocabulary.of(
                java.util.Set.of(), java.util.Set.of(), Map.of("cfg", "config file")));

    assertThat(composer.parameterNote("cfg")).doesNotContain("spell out");
  }

  @Test
  void anUnlistedAbbreviationBesideAListedOneIsStillCorrected() {
    var composer =
        new ElementNoteComposer(
            DomainVocabulary.of(
                java.util.Set.of(), java.util.Set.of(), Map.of("cfg", "configuration")));

    assertThat(composer.classNote("CfgMgr"))
        .contains("project shorthand: cfg → configuration")
        .contains("spell out: mgr → manager");
  }

  /**
   * Regression: an identifier that tokenizes to nothing used to raise {@code
   * IndexOutOfBoundsException} out of the clarity analyzer, failing the run it was scoring. The
   * tokenizer drops underscores and empty fragments, so {@code __} and {@code ""} both produce no
   * tokens — and both reach the analyzer from bytecode the library did not write: a Kotlin
   * unused-parameter placeholder, an obfuscated jar, a synthetic accessor. Found by the security
   * suite's generated identifiers.
   */
  @Test
  void aMethodNameWithNoWordsInItGetsANoteRatherThanACrash() {
    assertThat(composer.methodNote("__")).isEqualTo("No words to read in this name");
  }

  @Test
  void aClassNameWithNoWordsInItGetsANoteRatherThanACrash() {
    assertThat(composer.classNote("___")).isEqualTo("No words to read in this name");
  }

  @Test
  void anEmptyMethodNameGetsANoteRatherThanACrash() {
    assertThat(composer.methodNote("")).isEqualTo("No words to read in this name");
  }

  @Test
  void anEmptyClassNameGetsANoteRatherThanACrash() {
    assertThat(composer.classNote("")).isEqualTo("No words to read in this name");
  }

  @Test
  void anEmptyParameterNameStillGetsItsOrdinaryNote() {
    assertThat(composer.parameterNote("")).isNotBlank();
  }
}
