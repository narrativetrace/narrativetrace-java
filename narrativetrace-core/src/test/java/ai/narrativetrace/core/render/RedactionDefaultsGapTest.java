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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The names and value shapes the default policy missed, per an adversarial review.
 *
 * <p>INTENT: NarrativeTrace captures parameters and return values by design, so the default
 * renderer is the only thing standing between a payment or session DTO and the log. The audit's
 * probe showed card numbers, PANs, JWTs, cookies, session ids, account and routing numbers and
 * IBANs all rendering in full.
 */
class RedactionDefaultsGapTest {

  @Nested
  @DisplayName("names the audit found unprotected")
  class NamesFromTheAudit {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "cardNumber",
          "card_number",
          "pan",
          "jwt",
          "cookie",
          "setCookie",
          "set_cookie",
          "sessionId",
          "session_id",
          "accountNumber",
          "account_number",
          "routingNumber",
          "routing_number",
          "iban"
        })
    void everyNameTheAuditReportedIsRedacted(String name) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedact(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"CARDNUMBER", "Pan", "JWT", "SetCookie", "SESSION_ID", "IbAn", "RoutingNumber"})
    void theNewNamesMatchWhateverTheirCasing(String name) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedact(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "customerCardNumber",
          "primaryAccountNumber",
          "bankIban",
          "userSessionIdentifier",
          "rawJwtHeader",
          "cookieJar"
        })
    void theNewNamesMatchInsideCompoundNames(String name) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedact(name)).isTrue();
    }
  }

  @Nested
  @DisplayName("names that must stay visible")
  class FalsePositiveGuard {

    /**
     * `pan` and `iban` are three and four letters long, so adding them as plain substrings would
     * have redacted `companyName`, `expansionRatio`, `panelId` and `japaneseAddress`. They are
     * matched on identifier-token boundaries instead.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
          "companyName",
          "company",
          "expansionRatio",
          "panelId",
          "japaneseAddress",
          "spanCount",
          "planId",
          "shippingPanel",
          "occupancy",
          "urbanArea"
        })
    void aNameThatMerelyContainsPanIsNotRedacted(String name) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedact(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"orderId", "quantity", "customerName", "totalAmount", "createdAt"})
    void ordinaryBusinessNamesStayVisible(String name) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedact(name)).isFalse();
    }
  }

  /**
   * The hard-coded IP address below is deliberate and is the assertion: a dotted numeric string
   * must not be mistaken for a JWT's dotted segments.
   */
  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  @Nested
  @DisplayName("value shapes that betray a secret regardless of the field name")
  class ValueShapes {

    @Test
    void aJwtIsMaskedEvenUnderAnInnocentName() {
      var jwt =
          "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
              + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFkYSJ9"
              + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";

      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue(jwt)).isTrue();
    }

    @Test
    void aLuhnValidPanIsMasked() {
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue("4111111111111111")).isTrue();
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue("4111 1111 1111 1111")).isTrue();
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue("4111-1111-1111-1111")).isTrue();
    }

    @Test
    void anOrderNumberThatLooksPanishButFailsLuhnStaysVisible() {
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue("4111111111111112")).isFalse();
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue("1234567890123")).isFalse();
    }

    @Test
    void aSetCookieStringIsMasked() {
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue("SESSION=abc123; Path=/; HttpOnly"))
          .isTrue();
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue("id=x; Max-Age=3600; Secure")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "",
          "hello world",
          "ORD-2026-0001",
          "a.b.c",
          "192.168.0.1",
          "user@example.com",
          "2026-09-02T10:00:00Z",
          "12345",
          "key=value",
          "name=Ada; age=36"
        })
    void ordinaryValuesAreNotMasked(String value) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue(value)).isFalse();
    }

    @Test
    void aDisabledPolicyMasksNoValue() {
      assertThat(RedactionPolicy.DISABLED.shouldRedactValue("4111111111111111")).isFalse();
    }

    @Test
    void aNullValueIsNotMasked() {
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue(null)).isFalse();
    }

    @Test
    void aCustomNamePolicyStillMasksSecretValueShapes() {
      var custom = RedactionPolicy.ofPatterns(Set.of("nothing"));
      assertThat(custom.shouldRedactValue("4111111111111111")).isTrue();
    }
  }

  /**
   * National identity numbers, language-neutral and on by default.
   *
   * <p>INTENT: A national id is the one sensitive value whose <em>field name</em> is most often in
   * a language the deny-list is read in but not written in — {@code numero}, {@code documento},
   * {@code numeroDocumento}. Its checksum does not care what the field is called, which is exactly
   * why the value axis is the right one for it.
   *
   * <p><b>@llmNote</b> Every scheme gets both halves: a real number that must vanish, and a
   * lookalike that fails the checksum and must stay visible. Without the second half the matcher
   * could be "any 11 digits" and every test here would still pass.
   */
  @Nested
  @DisplayName("national identity numbers, by their own check digits")
  class NationalIdShapesTest {

    /**
     * Three of the CPFs below exist for the {@code remainder < 2} rule rather than for Brazil: a
     * check digit computed from remainder 0, 1 and 2 pins each side of it, and without them the
     * rule could be {@code <= 2}, or absent, and every case here would still pass.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
          "12.345.678-5", // Chile, RUT, dotted
          "12345678-5", // Chile, RUT, plain
          "1234567-4", // Chile, seven-digit body
          "10000013-K", // Chile, verifier 10 is written K
          "10000004-0", // Chile, verifier 11 is written 0
          "529.982.247-25", // Brazil, CPF, formatted
          "52998224725", // Brazil, CPF, bare
          "11144477735", // Brazil, CPF, second example
          "75749118606", // Brazil, CPF whose first check digit comes from remainder 1
          "99603082430", // Brazil, CPF whose second check digit comes from remainder 0
          "99351819019", // Brazil, CPF whose second check digit comes from remainder 2
          "11.222.333/0001-81", // Brazil, CNPJ, formatted
          "11222333000181", // Brazil, CNPJ, bare
          "12345678Z", // Spain, DNI
          "12345678-Z", // Spain, DNI, hyphenated
          "X1234567L", // Spain, NIE, X prefix
          "Y1234567X", // Spain, NIE, Y prefix
          "Z1234567R", // Spain, NIE, Z prefix
          "184127645108946", // France, NIR
          "1 84 12 76 451 089 46", // France, NIR, spaced as it is printed
          "175032A12345606", // France, NIR, Corsica 2A in the department position
          "180022B75123469", // France, NIR, Corsica 2B in the department position
          "11010519491231002X", // China, resident id, X check character
          "440301199001010012", // China, resident id, numeric check character
          "11010521001231003X" // China, birth year exactly at the upper bound of the range
        })
    void aNationalIdentityNumberIsMaskedWhateverTheFieldIsCalled(String value) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "52998224726", // CPF with the last check digit wrong
          "11222333000182", // CNPJ with the last check digit wrong
          "12345678-6", // RUT with the wrong verifier
          "12345678A", // DNI with the wrong check letter
          "X1234567A", // NIE with the wrong check letter
          "184127645108947", // NIR with the wrong key
          "110105194912310021", // Chinese id with the wrong check character
          "110105194913320019", // Chinese id, valid checksum, thirteenth month
          "123456785", // a nine-digit order number: a RUT without its verifier separator
          "12345678901", // an eleven-digit reference that is not a CPF
          "987654321", // an ordinary invoice number
          "2026-09-02" // a date, which is digits and a separator too
        })
    void aLookalikeThatFailsItsChecksumStaysVisible(String value) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue(value)).isFalse();
    }

    /**
     * The birth date embedded at positions 7-14 is most of what separates a Chinese resident id
     * from any eighteen-digit number: the check character alone lets one in eleven through.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
          "110105189912310007", // before the earliest plausible birth year
          "110105210101010004", // after the latest
          "110105199000010019", // month 00
          "110105199001000007", // day 00
          "110105199001320000" // day 32
        })
    void anEighteenDigitNumberWithAnImpossibleBirthDateIsNotAResidentId(String value) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue(value)).isFalse();
    }

    /**
     * Every repeated-digit string satisfies both CPF check digits, and none of them is a document —
     * they are what a form writes when it has none. Rejecting them before the checksum is the
     * difference between a matcher and a length test.
     */
    @ParameterizedTest
    @ValueSource(strings = {"11111111111", "22222222222", "99999999999"})
    void aRepeatedDigitPlaceholderIsNotACpf(String value) {
      assertThat(RedactionPolicy.DEFAULT.shouldRedactValue(value)).isFalse();
    }

    @Test
    void aDisabledPolicyMasksNoNationalId() {
      assertThat(RedactionPolicy.DISABLED.shouldRedactValue("52998224725")).isFalse();
      assertThat(RedactionPolicy.DISABLED.shouldRedactValue("11010519491231002X")).isFalse();
    }

    @Test
    void aNationalIdIsHiddenThroughTheRendererUnderAnInnocentFieldName() {
      var renderer = new ValueRenderer();
      record Applicant(String reference, String city) {}

      var flat = renderer.render(new Applicant("52998224725", "Recife"));
      var structured = renderer.renderStructured(new Applicant("52998224725", "Recife")).toString();

      assertThat(flat).doesNotContain("52998224725").contains(RedactionPolicy.MARKER);
      assertThat(structured).doesNotContain("52998224725").contains(RedactionPolicy.MARKER);
      assertThat(flat).as("an ordinary field beside it stays readable").contains("Recife");
    }
  }
}
