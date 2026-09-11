/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.SensitiveConcept.Language;
import ai.narrativetrace.core.render.SensitiveConcept.ValueShape;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the built-in vocabulary that {@link RedactionPolicy} is built from.
 *
 * <p>INTENT: The vocabulary moved from two flat literal sets to a list of concepts. This asserts
 * the move changed no matching behaviour — every word the flat lists held is still held, on the
 * same axis — and then pins the words deliberately added on top. A term silently losing its match
 * mode is a security regression that nothing else would catch.
 */
class SensitiveVocabularyTest {

  /**
   * Every substring term the flat {@code DEFAULT_PATTERNS} literal held before the vocabulary
   * existed. None may be lost.
   */
  private static final Set<String> HISTORICAL_SUBSTRING_TERMS =
      Set.of(
          "password",
          "passwd",
          "secret",
          "token",
          "apikey",
          "api_key",
          "cvv",
          "ssn",
          "authorization",
          "credential",
          "privatekey",
          "private_key",
          "cardnumber",
          "card_number",
          "jwt",
          "cookie",
          "setcookie",
          "set_cookie",
          "sessionid",
          "session_id",
          "accountnumber",
          "account_number",
          "routingnumber",
          "routing_number",
          "contrasena",
          "tarjeta",
          "cedula",
          "claveacceso",
          "clave_acceso",
          "clavesecreta",
          "clave_secreta",
          "cartao",
          "motdepasse",
          "mot_de_passe",
          "cartebancaire",
          "carte_bancaire",
          "numerocarte",
          "numero_carte",
          "密码",
          "身份证",
          "shenfenzheng");

  /** Every token-matched term the flat {@code WORD_PATTERNS} literal held. None may be lost. */
  private static final Set<String> HISTORICAL_TOKEN_TERMS =
      Set.of("pan", "iban", "rut", "cuit", "dni", "senha", "cpf", "cnpj", "nir", "mima");

  @Test
  @DisplayName("no word the flat deny-list held was lost in the move to concepts")
  void preservesEveryHistoricalSubstringTerm() {
    assertThat(SensitiveVocabulary.substringTerms()).containsAll(HISTORICAL_SUBSTRING_TERMS);
  }

  @Test
  @DisplayName("no word changed match axis — a token term must not silently become a substring")
  void preservesEveryHistoricalTokenTerm() {
    assertThat(SensitiveVocabulary.identifierTokenTerms())
        .containsExactlyInAnyOrderElementsOf(HISTORICAL_TOKEN_TERMS);
  }

  @Test
  @DisplayName("a token term is never also a substring term — the axes must stay disjoint")
  void axesAreDisjoint() {
    assertThat(SensitiveVocabulary.substringTerms())
        .doesNotContainAnyElementsOf(SensitiveVocabulary.identifierTokenTerms());
  }

  @Test
  @DisplayName("the words added to close the audited gaps are present")
  void carriesTheAuditedAdditions() {
    assertThat(SensitiveVocabulary.substringTerms())
        .as("socialSecurityNumber did not match the bare 'ssn' abbreviation")
        .contains("socialsecurity", "social_security", "socialsecuritynumber")
        .as("German was absent while Spanish, Portuguese, French and Chinese were covered")
        .contains("passwort", "kennwort")
        .as("common credential words with no entry at all")
        .contains("passphrase", "otp", "bearer", "accesskey");
  }

  @Test
  @DisplayName("every concept answers the value-shape question, one way or the other")
  void everyConceptDeclaresItsValueAxis() {
    for (var concept : SensitiveVocabulary.CONCEPTS) {
      assertThat(concept.valueShape())
          .as("concept '%s' must declare a shape or say why it has none", concept.id())
          .isNotNull();
      if (concept.valueShape() instanceof ValueShape.None none) {
        assertThat(none.reason())
            .as("concept '%s' declares no value shape but gives no reason", concept.id())
            .isNotBlank();
      }
    }
  }

  @Test
  @DisplayName("concept ids are unique — the cross-port comparison keys on them")
  void conceptIdsAreUnique() {
    assertThat(SensitiveVocabulary.CONCEPTS.stream().map(SensitiveConcept::id))
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("every term is canonical already — matching folds nothing at lookup time")
  void everyTermIsStoredCanonical() {
    for (var concept : SensitiveVocabulary.CONCEPTS) {
      for (var term : concept.allTerms()) {
        assertThat(term.text())
            .as("term '%s' in concept '%s' is not stored canonical", term.text(), concept.id())
            .isEqualTo(SecretValueShapes.canonical(term.text()));
      }
    }
  }

  @Test
  @DisplayName("a concept declaring a language declares at least one word for it")
  void noLanguageColumnIsEmpty() {
    for (var concept : SensitiveVocabulary.CONCEPTS) {
      for (var entry : concept.names().entrySet()) {
        assertThat(entry.getValue())
            .as("concept '%s' lists language %s with no words", concept.id(), entry.getKey())
            .isNotEmpty();
      }
    }
  }

  @Test
  @DisplayName("English is covered for every concept that is not language-specific")
  void nationalIdsAsideEveryConceptHasEnglish() {
    for (var concept : SensitiveVocabulary.CONCEPTS) {
      if (concept.category() == SensitiveConcept.Category.NATIONAL_ID) {
        continue; // a Chilean RUT has no English name; that is the point of it
      }
      assertThat(concept.names())
          .as("concept '%s' has no English terms", concept.id())
          .containsKey(Language.EN);
    }
  }
}
