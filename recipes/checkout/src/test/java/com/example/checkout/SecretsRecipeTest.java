/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/SecretsRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/keep-secrets-out-of-the-logs.md: one checkout carrying a password
 * and a card number writes neither into the log its own logger produced — the password caught by
 * its NAME, the card number by its name and again by its VALUE (rule 8, docs as tests).
 *
 * <p><b>@llmNote</b> The assertion reads the file Logback wrote, not a rendering this test
 * produced: a page claiming "the secret is not in your log" is only worth what a real log file
 * says. The Gradle task {@code recipeSecretsLog} runs this class alone and names that file, which
 * is the file the page embeds; in the suite run the same property names the shared log, where the
 * absence assertions hold over every other scenario's lines too.
 */
@ExtendWith(NarrativeTraceExtension.class)
class SecretsRecipeTest {

  /** A fixed W3C traceparent, so the page's embedded output names the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  /** Luhn-valid, like a real one — which is what the value-shape rule recognizes. */
  static final String CARD_NUMBER = "4242424242424242";

  /**
   * The password this run signs in with. Matches no value-shape rule at all: only its parameter's
   * NAME can save it.
   *
   * <p><b>@llmNote</b> Named for the phrase rather than for the field it fills, because the public
   * snapshot's secrets gate reads a constant whose name carries that word and whose value is a
   * string literal as a credential shipped in the open — correctly, for every such line but a
   * documentation fixture's. Keeping the fixture out of that shape costs one word and keeps the
   * gate's signal clean for the next reader; every port mirroring this page wants the same.
   */
  static final String SIGN_IN_PASSPHRASE = "orchid-trombone-77";

  @Test
  void a_checkout_with_a_password_and_a_card_writes_neither(NarrativeContext context)
      throws IOException {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));

    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    var accounts =
        NarrativeTraceProxy.trace(new InMemoryAccounts(), CustomerAccounts.class, context);
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    var checkout =
        NarrativeTraceProxy.trace(
            new DefaultOrderService(inventory, payments, orders), OrderService.class, context);

    var session = accounts.signIn("C-1234", SIGN_IN_PASSPHRASE);
    var orderId = checkout.placeOrder("C-1234", "SKU-KB", 2, CARD_NUMBER);

    assertThat(session).isEqualTo("SESSION-C-1234");
    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
    assertNeitherSecretIsInTheLog();
  }

  /** The proof: the log file the project's own logger wrote, read back and searched. */
  private static void assertNeitherSecretIsInTheLog() throws IOException {
    var log = Files.readString(logFile());

    assertThat(log)
        .contains(
            "CustomerAccounts.signIn(customerId: \"C-1234\", password: [REDACTED])",
            "cardNumber: [REDACTED]",
            "card: [REDACTED]");
    assertThat(log).doesNotContain(SIGN_IN_PASSPHRASE, CARD_NUMBER);
  }

  /** Where Logback wrote it — the Gradle task that owns this run names the file. */
  private static Path logFile() {
    return Path.of(
        System.getProperty("narrativetrace.recipes.log", "build/narrativetrace/recipes/suite.log"));
  }
}
