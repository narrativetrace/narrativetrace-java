/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextResolverTest {

  private static Glossary glossary(BoundedContext... contexts) {
    var byName = new java.util.HashMap<String, BoundedContext>();
    for (var context : contexts) {
      byName.put(context.name(), context);
    }
    return new Glossary(1, Map.copyOf(byName), List.of());
  }

  @Test
  void resolvesPackageToDeclaredContext() {
    var resolver =
        new ContextResolver(
            glossary(
                new BoundedContext("billing", List.of("com.acme.billing"), null),
                new BoundedContext("support", List.of("com.acme.support"), null)));

    assertThat(resolver.resolve("com.acme.billing")).isEqualTo("billing");
    assertThat(resolver.resolve("com.acme.billing.overdraft")).isEqualTo("billing");
    assertThat(resolver.resolve("com.acme.support")).isEqualTo("support");
  }

  @Test
  void fallsBackToUnassignedWhenNoPrefixMatches() {
    var resolver =
        new ContextResolver(
            glossary(new BoundedContext("billing", List.of("com.acme.billing"), null)));

    assertThat(resolver.resolve("com.other.shop")).isEqualTo("_unassigned");
    assertThat(resolver.resolve("")).isEqualTo("_unassigned");
  }

  @Test
  void rejectsSiblingPackageSharingPrefixWithoutDelimiter() {
    var resolver =
        new ContextResolver(
            glossary(new BoundedContext("billing", List.of("com.acme.billing"), null)));

    assertThat(resolver.resolve("com.acme.billingx")).isEqualTo("_unassigned");
    assertThat(resolver.resolve("com.acme.billingx.core")).isEqualTo("_unassigned");
    assertThat(resolver.resolve("com.acme.billin")).isEqualTo("_unassigned");
  }

  @Test
  void longestMatchingPrefixWinsForNestedContexts() {
    var resolver =
        new ContextResolver(
            glossary(
                new BoundedContext("billing", List.of("com.acme.billing"), null),
                new BoundedContext("collections", List.of("com.acme.billing.collections"), null)));

    assertThat(resolver.resolve("com.acme.billing.collections.dunning")).isEqualTo("collections");
    assertThat(resolver.resolve("com.acme.billing.invoice")).isEqualTo("billing");
  }

  @Test
  void equalLengthPrefixTieResolvesToAlphabeticallyFirstContext() {
    var resolver =
        new ContextResolver(
            glossary(
                new BoundedContext("zebra", List.of("com.acme.billing"), null),
                new BoundedContext("alpha", List.of("com.acme.billing"), null)));

    assertThat(resolver.resolve("com.acme.billing.core")).isEqualTo("alpha");
  }

  @Test
  void rejectsNullPackageName() {
    var resolver = new ContextResolver(glossary());

    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> resolver.resolve(null))
        .withMessageContaining("packageName");
  }

  @Test
  void rejectsNullGlossary() {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContextResolver(null))
        .withMessageContaining("glossary");
  }
}
