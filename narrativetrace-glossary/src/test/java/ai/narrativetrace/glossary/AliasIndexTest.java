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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AliasIndexTest {

  private static final GlossaryTerm OVERDRAFT_ACCOUNT =
      new GlossaryTerm(
          "overdraft account",
          "billing",
          TermKind.NOUN_PHRASE,
          TermStatus.CURATED,
          null,
          Map.of(),
          List.of(new SynonymAlias("account with overdraft", null)),
          List.of(),
          LocalDate.of(2026, 8, 11));

  private final AliasIndex index =
      AliasIndex.of(
          new Glossary(
              1,
              Map.of(
                  "billing", new BoundedContext("billing", List.of(), null),
                  "support", new BoundedContext("support", List.of(), null)),
              List.of(OVERDRAFT_ACCOUNT)));

  @Test
  void findsCanonicalTermForAliasWithinItsContext() {
    var key = new TermKey("billing", "account with overdraft");

    assertThat(index.isAlias(key)).isTrue();
    assertThat(index.canonicalFor(key)).contains(OVERDRAFT_ACCOUNT);
  }

  @Test
  void aliasMatchingIsScopedToTheDeclaringContext() {
    var key = new TermKey("support", "account with overdraft");

    assertThat(index.isAlias(key)).isFalse();
    assertThat(index.canonicalFor(key)).isEmpty();
  }

  @Test
  void canonicalTermItselfIsNotAnAlias() {
    assertThat(index.isAlias(new TermKey("billing", "overdraft account"))).isFalse();
  }

  @Test
  void superstringOfAliasPhraseDoesNotMatch() {
    assertThat(index.isAlias(new TermKey("billing", "account with overdraft protection")))
        .isFalse();
    assertThat(index.isAlias(new TermKey("billing", "account with"))).isFalse();
  }

  @Test
  void rejectsNulls() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> AliasIndex.of(null))
        .withMessageContaining("glossary");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> index.isAlias(null))
        .withMessageContaining("key");
  }
}
