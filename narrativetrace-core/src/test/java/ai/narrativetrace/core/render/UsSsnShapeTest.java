/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The US Social Security value shape, added 2026-09-10.
 *
 * <p>INTENT: An audit found the concept covered by the single English name {@code ssn} and by no
 * value shape at all, so a real SSN under an innocuous field name leaked in full. These tests pin
 * both halves of the deal: it catches an SSN whatever the field is called, and it does not catch
 * the ordinary business data that a laxer matcher would blank.
 */
class UsSsnShapeTest {

  @Nested
  @DisplayName("recognises a real SSN whatever the field is called")
  class Recognises {

    @ParameterizedTest
    @ValueSource(strings = {"123-45-6789", "001-01-0001", "899-99-9999", "078-05-1120"})
    @DisplayName("a validly-structured dashed SSN is secret-shaped")
    void catchesDashedSsn(String ssn) {
      assertThat(SecretValueShapes.isSecretShaped(ssn)).isTrue();
    }

    @Test
    @DisplayName("the name is irrelevant — that is the entire point of the value axis")
    void catchesItUnderAnInnocuousName() {
      var renderer = new ValueRenderer(200, 5, 5);
      assertThat(renderer.render("123-45-6789")).isEqualTo(RedactionPolicy.MARKER);
    }
  }

  @Nested
  @DisplayName("leaves ordinary data alone")
  class DoesNotOverreach {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "123456789", // bare nine digits: an order number, an account id, a phone number
          "12-345-6789", // wrong grouping
          "123-456-789", // wrong grouping
          "1234-56-789", // wrong grouping
          "123-45-678", // too short
          "123-45-67890" // too long
        })
    @DisplayName("only the dashed AAA-GG-SSSS form counts — punctuation is the signal")
    void ignoresEverythingButTheDashedForm(String notAnSsn) {
      assertThat(SecretValueShapes.isSecretShaped(notAnSsn)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "000-12-3456", // area 000 is never issued
          "666-12-3456", // area 666 is never issued
          "900-12-3456", // area 900-999 is never issued
          "999-12-3456",
          "123-00-4567", // group 00 is never issued
          "123-45-0000", // serial 0000 is never issued
          "000-00-0000" // the placeholder in every test fixture and redacted form
        })
    @DisplayName("structurally impossible numbers stay visible, placeholders included")
    void ignoresNumbersTheSsaNeverIssues(String impossible) {
      assertThat(SecretValueShapes.isSecretShaped(impossible)).isFalse();
    }

    @Test
    @DisplayName("a bare nine-digit order number is not blanked")
    void leavesOrderNumbersVisible() {
      var renderer = new ValueRenderer(200, 5, 5);
      assertThat(renderer.render("400123456"))
          .isNotEqualTo(RedactionPolicy.MARKER)
          .contains("400123456");
    }
  }
}
