/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RedactionPolicyTest {

  @Test
  void defaultRedactsExactSensitiveNames() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("password")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cvv")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("authorization")).isTrue();
  }

  @Test
  void defaultRedactsSensitiveNamesEmbeddedAsSubstrings() {
    // Substring matching catches real-world compound names.
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("userPassword")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cardCvv")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("accessTokenExpiry")).isTrue();
  }

  @Test
  void defaultMatchingIsCaseInsensitive() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("PASSWORD")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("Cvv")).isTrue();
  }

  @Test
  void defaultDoesNotRedactBenignNamesThatMerelyShareAPrefix() {
    // Near-miss: "author" must not be caught by the "authorization" pattern; "username" matches
    // none.
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("author")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("username")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("broken")).isFalse();
  }

  @Test
  void nullFieldNameIsNotRedacted() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact(null)).isFalse();
  }

  @Test
  void disabledPolicyRedactsNothing() {
    assertThat(RedactionPolicy.DISABLED.shouldRedact("password")).isFalse();
    assertThat(RedactionPolicy.DISABLED.shouldRedact("cvv")).isFalse();
  }

  @Test
  void customPatternsReplaceTheDefaultsEntirely() {
    var policy = RedactionPolicy.ofPatterns(Set.of("ssn"));

    assertThat(policy.shouldRedact("taxSsn")).isTrue();
    assertThat(policy.shouldRedact("password")).isFalse(); // no longer in the set
  }

  @Test
  void customPatternsAreMatchedCaseInsensitively() {
    var policy = RedactionPolicy.ofPatterns(Set.of("PIN"));

    assertThat(policy.shouldRedact("cardpin")).isTrue();
  }

  // Every non-English word below is written as a backslash-u escape rather than as a literal
  // character. Java resolves the escape before lexing, so this file stays ASCII on disk and no
  // build machine's default charset can change what a security control matches.

  @Test
  void defaultRedactsSpanishSensitiveNames() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("contrase\u00f1a")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("tarjeta")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("c\u00e9dula")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("rut")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cuit")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("dni")).isTrue();
  }

  @Test
  void defaultRedactsPortugueseSensitiveNames() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("senha")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cpf")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cnpj")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cart\u00e3o")).isTrue();
  }

  @Test
  void defaultRedactsFrenchSensitiveNames() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("motDePasse")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("mot_de_passe")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("nir")).isTrue();
  }

  @Test
  void defaultRedactsChineseSensitiveNames() {
    // U+5BC6 U+7801 = mima (password); U+8EAB U+4EFD U+8BC1 = shenfenzheng (identity card)
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("\u5bc6\u7801")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("\u7528\u6237\u5bc6\u7801")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("\u8eab\u4efd\u8bc1")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("mima")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("shenfenzheng")).isTrue();
  }

  /**
   * The deny-list is written in folded ASCII, so a team that types the accent and a team that does
   * not get the same protection. Neither spelling is the "correct" one to a field author.
   */
  @Test
  void accentedAndUnaccentedSpellingsAreMatchedAlike() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("contrasena")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("CONTRASE\u00d1A")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cedula")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("Cart\u00e3oCredito")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cartaoCredito")).isTrue();
    // The decomposed spelling is the same word: n + U+0303 rather than U+00F1.
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("contrasen\u0303a")).isTrue();
  }

  @Test
  void nonEnglishShortNamesMatchAsIdentifierTokensInCompoundNames() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("rutCliente")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cuitEmpresa")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("dniTitular")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("senhaUsuario")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cpf_cliente")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("CNPJ")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("nirAssure")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("mimaHash")).isTrue();
  }

  /**
   * The {@code pan} lesson, applied to six more short words. Every name here contains a deny-list
   * word as a substring and is an ordinary business field; a security default that blanks these is
   * one teams switch off entirely, which leaks everything rather than one field.
   */
  @Test
  void shortNonEnglishWordsDoNotBlankOrdinaryBusinessFields() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("truthValue")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("bruteForceAttempts")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("scrutinyScore")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("circuitBreaker")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("biscuitCount")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("midnightCutoff")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("chosenHash")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("frozenHashes")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("nirvanaLevel")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("semiMajorAxis")).isFalse();
  }

  /**
   * {@code clave} and {@code carte} were narrowed out of the deny-list entirely on 2026-09-03: a
   * token match only ever protected them from <em>someone else's</em> compound ({@code enclaveId},
   * {@code cartesianProduct}), never from the codebase's own ({@code clavePrimaria}, {@code
   * carteGraphique}) — the pattern was a whole token there too. Bare {@code clave}/{@code carte},
   * every old collision example, and the codebase's own compounds all stay visible now.
   */
  @Test
  void claveAndCarteStayVisibleBareAndInOrdinaryCompounds() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("enclaveId")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("conclaveDate")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("cartesianProduct")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("descartesPoint")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("carteraDigital")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("clave")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("clavePrimaria")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("claveForanea")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("llavePrimaria")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("carte")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("carteGraphique")).isFalse();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("carteRoutiere")).isFalse();
  }

  /**
   * The narrower unit the 2026-09-03 ruling replaced {@code clave}/{@code carte} with: specific
   * enough to be safe as a plain substring, so both the camelCase and snake_case seam need their
   * own pattern the way {@code motDePasse}/{@code mot_de_passe} already did.
   */
  @Test
  void narrowedClaveAndCarteCompoundsAreRedacted() {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("claveAcceso")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("clave_acceso")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("claveSecreta")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("clave_secreta")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("carteBancaire")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("carte_bancaire")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("numeroCarte")).isTrue();
    assertThat(RedactionPolicy.DEFAULT.shouldRedact("numero_carte")).isTrue();
  }

  @Nested
  @DisplayName("the additive configuration knob")
  class AdditionalPatterns {

    @AfterEach
    void clearTheProperty() {
      System.clearProperty(AdditionalRedactionPatterns.PROPERTY);
    }

    @Test
    void noConfigurationAddsNothing() {
      assertThat(AdditionalRedactionPatterns.parse(null)).isEmpty();
      assertThat(AdditionalRedactionPatterns.parse("")).isEmpty();
      assertThat(AdditionalRedactionPatterns.parse("   ")).isEmpty();
    }

    @Test
    void patternsAreCommaSeparatedAndStripped() {
      assertThat(AdditionalRedactionPatterns.parse("betalingskort"))
          .containsExactly("betalingskort");
      assertThat(AdditionalRedactionPatterns.parse(" alpha , beta ,gamma"))
          .containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    @Test
    void emptyEntriesAreDroppedRatherThanMatchingEverything() {
      // "a,,b" must not yield "", whose substring test matches every field name there is.
      assertThat(AdditionalRedactionPatterns.parse("a,,b")).containsExactlyInAnyOrder("a", "b");
      assertThat(AdditionalRedactionPatterns.parse(",")).isEmpty();
      assertThat(AdditionalRedactionPatterns.parse(" , ")).isEmpty();
    }

    @Test
    void theSystemPropertyWinsOverTheEnvironmentVariable() {
      assertThat(AdditionalRedactionPatterns.configured(key -> "fromProperty", key -> "fromEnv"))
          .containsExactly("fromProperty");
    }

    @Test
    void theEnvironmentVariableIsReadWhenNoPropertyIsSet() {
      assertThat(AdditionalRedactionPatterns.configured(key -> null, key -> "fromEnv"))
          .containsExactly("fromEnv");
    }

    @Test
    void additionsWidenTheDefaultVocabularyRatherThanReplacingIt() {
      System.setProperty(AdditionalRedactionPatterns.PROPERTY, "betalingskort");

      var policy = RedactionPolicy.defaults();

      assertThat(policy.shouldRedact("betalingskort")).isTrue();
      assertThat(policy.shouldRedact("kundeBetalingskortNummer")).isTrue();
      assertThat(policy.shouldRedact("password")).as("the defaults are still there").isTrue();
      assertThat(policy.shouldRedact("contraseña")).isTrue();
    }

    /**
     * An operator who widened redaction must not be silently undone by application code reaching
     * for {@code ofPatterns}. The two knobs answer different people: {@code ofPatterns} is the
     * application's opinion, the property is the deployment's, and only {@link
     * RedactionPolicy#DISABLED} turns everything off.
     */
    @Test
    void additionsSurviveAPolicyThatReplacedTheDefaults() {
      System.setProperty(AdditionalRedactionPatterns.PROPERTY, "betalingskort");

      var policy = RedactionPolicy.ofPatterns(Set.of("ssn"));

      assertThat(policy.shouldRedact("taxSsn")).isTrue();
      assertThat(policy.shouldRedact("betalingskort")).isTrue();
      assertThat(policy.shouldRedact("password")).as("ofPatterns still replaces").isFalse();
    }

    @Test
    void additionsAreFoldedLikeEveryOtherPattern() {
      System.setProperty(AdditionalRedactionPatterns.PROPERTY, "Kontonummer, Mot\u00A0Cl\u00E9");

      var policy = RedactionPolicy.defaults();

      assertThat(policy.shouldRedact("KONTONUMMER")).isTrue();
      assertThat(policy.shouldRedact("kundeKontonummer")).isTrue();
      assertThat(policy.shouldRedact("mot\u00A0cle")).isTrue();
    }

    @Test
    void aDisabledPolicyIgnoresTheAdditions() {
      System.setProperty(AdditionalRedactionPatterns.PROPERTY, "betalingskort");

      assertThat(RedactionPolicy.DISABLED.shouldRedact("betalingskort")).isFalse();
    }
  }

  @Test
  void disabledPolicyRedactsNoNonEnglishNameEither() {
    assertThat(RedactionPolicy.DISABLED.shouldRedact("contrase\u00f1a")).isFalse();
    assertThat(RedactionPolicy.DISABLED.shouldRedact("\u5bc6\u7801")).isFalse();
    assertThat(RedactionPolicy.DISABLED.shouldRedact("claveAcceso")).isFalse();
  }
}
