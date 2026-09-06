/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class RenameSuggesterTest {

  private final RenameSuggester suggester = new RenameSuggester();

  @Test
  void splicesCanonicalTermIntoCamelCaseIdentifier() {
    assertThat(
            suggester.suggest(
                "openAccountWithOverdraft", "account with overdraft", "overdraft account"))
        .contains("openOverdraftAccount");
  }

  @Test
  void preservesPascalCaseConvention() {
    assertThat(
            suggester.suggest(
                "AccountWithOverdraftService", "account with overdraft", "overdraft account"))
        .contains("OverdraftAccountService");
  }

  @Test
  void preservesSnakeCaseConvention() {
    assertThat(
            suggester.suggest(
                "open_account_with_overdraft", "account with overdraft", "overdraft account"))
        .contains("open_overdraft_account");
  }

  @Test
  void matchesAliasAgainstNormalizedTokensDespitePluralSpelling() {
    assertThat(
            suggester.suggest(
                "openAccountsWithOverdraft", "account with overdraft", "overdraft account"))
        .contains("openOverdraftAccount");
  }

  @Test
  void returnsEmptyWhenAliasTokensDoNotAppearContiguously() {
    assertThat(suggester.suggest("chargeCard", "account with overdraft", "overdraft account"))
        .isEmpty();
    assertThat(
            suggester.suggest("accountForOverdraft", "account with overdraft", "overdraft account"))
        .isEmpty();
  }

  @Test
  void replacesWholeIdentifierWhenAliasCoversAllTokens() {
    assertThat(
            suggester.suggest(
                "accountWithOverdraft", "account with overdraft", "overdraft account"))
        .contains("overdraftAccount");
  }

  @Test
  void preservesRawCasingOfTokensOutsideTheSplicedWindow() {
    assertThat(
            suggester.suggest(
                "accountWithOverdraftXMLExport", "account with overdraft", "overdraft account"))
        .contains("overdraftAccountXMLExport");
  }

  @Test
  void leadingUnderscoreIdentifierStaysSnakeCase() {
    assertThat(
            suggester.suggest(
                "_account_with_overdraft", "account with overdraft", "overdraft account"))
        .contains("overdraft_account");
  }

  @Test
  void rejectsBlankArguments() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> suggester.suggest(" ", "a", "b"))
        .withMessageContaining("identifier");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> suggester.suggest(null, "a", "b"))
        .withMessageContaining("identifier");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> suggester.suggest("x", " ", "b"))
        .withMessageContaining("alias");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> suggester.suggest("x", null, "b"))
        .withMessageContaining("alias");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> suggester.suggest("x", "a", null))
        .withMessageContaining("canonical");
  }
}
