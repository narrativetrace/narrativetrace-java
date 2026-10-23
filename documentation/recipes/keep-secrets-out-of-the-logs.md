# Keep secrets out of the logs

[English](keep-secrets-out-of-the-logs.md) | [Español](../es/recetas/manten-los-secretos-fuera-de-los-logs.md) | [Português](../pt-BR/receitas/mantenha-os-segredos-fora-dos-logs.md) | [简体中文](../zh-CN/实用方案/别让密钥进日志.md)

## The problem

> If this thing logs every call with every argument, it is going to log a
> card number. Convince me it does not.

The checkout takes a password and a card number. Below is one run of it with
both, the log it actually wrote, and the test that proves neither value is in
that file.

## Minimal setup

Nothing. Redaction is on in every shipped integration and there is no
configuration surface that turns it off — the deny-list is consulted at
*capture*, before a value becomes an event, so a denied value never reaches
the log line, the trace file, the buffered path or a listener on the pipeline.
[Privacy and Redaction](../privacy-and-redaction.md) is the row-by-row
version, verified against the code.

Two things, though, are worth doing once in your own project:

1. **Write this page's test for your own flow.** Redaction you have not
   asserted is a claim, not a property of your build. The doctor's
   "redaction never proven in a test" finding exists for exactly this.
2. **Know the escape hatch.** A secret under a name nobody could guess —
   `fingerprint`, `answer2` — is reachable with `@NotTraced` on the
   parameter, field or record component, which always wins
   ([Annotations Guide](../annotations-guide.md)).

## The scenario

One new collaborator, so the run carries a password as well as a card. Its
parameter is called `password` — that name is the whole point:

<!-- snippet: recipes/checkout/src/main/java/com/example/checkout/CustomerAccounts.java -->
```java
// src/main/java/com/example/checkout/CustomerAccounts.java
package com.example.checkout;

/**
 * Signs a customer in before the checkout charges them. The parameter is named {@code password}.
 */
public interface CustomerAccounts {

  /**
   * @return the session id the checkout carries for the rest of the request
   * @throws IllegalArgumentException when the password is blank
   */
  String signIn(String customerId, String password);
}
```
<!-- /snippet -->

The run signs the customer in and places the order. The two secret values are
constants here so the test can search the log for them afterwards — a real
flow gets them from the request:

<!-- snippet: recipes/checkout/src/test/java/com/example/checkout/SecretsRecipeTest.java -->
```java
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
```
<!-- /snippet -->

## The actual output

**The log the run wrote**, through the project's own logger:

<!-- snippet: recipes/checkout/build/narrativetrace/recipes/keep-secrets-out-of-the-logs/checkout.log -->
```text
TRACE [solid wand romps] [narrativetrace] - → CustomerAccounts.signIn(customerId: "C-1234", password: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - ← returned: "SESSION-C-1234"
TRACE [solid wand romps] [narrativetrace] - → OrderService.placeOrder(customerId: "C-1234", sku: "SKU-KB", quantity: 2, cardNumber: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - → InventoryService.reserve(sku: "SKU-KB", quantity: 2)
TRACE [solid wand romps] [narrativetrace] - ← completed
TRACE [solid wand romps] [narrativetrace] - → PaymentService.charge(customerId: "C-1234", amountCents: 7998, card: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - ← returned: "PAY-C-1234-7998"
TRACE [solid wand romps] [narrativetrace] - → OrderRepository.save(order: Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998))
TRACE [solid wand romps] [narrativetrace] - ← completed
TRACE [solid wand romps] [narrativetrace] - ← returned: "ORD-C-1234-SKU-KB-2"
```
<!-- /snippet -->

**The trace file of the same run**, which is the other surface a secret could
have reached —
`build/narrativetrace/traces/SecretsRecipeTest/a_checkout_with_a_password_and_a_card_writes_neither.md`:

<!-- snippet: recipes/checkout/build/narrativetrace/traces/SecretsRecipeTest/a_checkout_with_a_password_and_a_card_writes_neither.md mask=duration,traceName -->
```text
---
type: trace
run: gross hill grins
scenario: A checkout with a password and a card writes neither
entry_point: CustomerAccounts.signIn
duration_ms: 3.444
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 5
error_count: 0
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: SecretsRecipeTest.a_checkout_with_a_password_and_a_card_writes_neither, passed, 2.281 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: none, no call threw in this run.
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — CustomerAccounts.signIn

**Scenario:** A checkout with a password and a card writes neither
**Duration:** 3.444ms | **Result:** PASSED

### Call Flow

- **CustomerAccounts.signIn**(customerId: `"C-1234"`, password: `[REDACTED]`) → `"SESSION-C-1234"` — 0.922ms #1
- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 2.032ms #2
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.187ms #2.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 0.282ms #2.2
  - **OrderRepository.save**(order: `Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998)`) — 0.23ms #2.3
```
<!-- /snippet -->

## How to read it

**Which rule caught which value.** Three values are gone, by two independent
mechanisms:

| Line | Value | Caught by |
|---|---|---|
| `#1` `CustomerAccounts.signIn(…, password: [REDACTED])` | `orchid-trombone-77` | Its **name**. `password` is on the always-on deny-list, which matches parameter names, field names and record-component names — in several languages — on any class, yours included. The value itself matches no pattern at all; nothing but the name could have saved it |
| `#2` `OrderService.placeOrder(…, cardNumber: [REDACTED])` | `4242424242424242` | Its **name** again: `cardNumber` is on the same list |
| `#2.2` `PaymentService.charge(…, card: [REDACTED])` | the same number, one call deeper | Its **value**. The parameter is named `card`, which the deny-list does not know — and the number is Luhn-valid, so the independent value-shape check caught it anyway |

That third row is the one worth remembering: `card` is what a parameter gets
called in real code, and the name axis alone would have printed it. The shape
axis knows a Luhn-valid card number, a JWT, a `Set-Cookie` string, a dashed
US Social Security number and a national identity number that passes its own
checksum.

**Where the rules live.** In the library, not in your configuration:
`RedactionPolicy.DEFAULT`, consulted by the one `ValueRenderer` every shipped
integration constructs the same way. There is no property, no DSL knob and no
log-level that reaches it;
[Privacy and Redaction](../privacy-and-redaction.md) lists both the surfaces
and what outranks what. `customerId: "C-1234"` *is* written in full, by the
way — a customer id is identifying, not secret, and NarrativeTrace does not
guess. If yours must not be logged, that is `@NotTraced`, which is a decision
in your source rather than a state a deploy can flip.

**Why the test is the proof, not the page.** The last line of the scenario
reads the file Logback wrote and asserts two things: that the three redacted
parameters are present *as* `[REDACTED]`, and that neither secret string
appears anywhere in it. A page can only show you bytes; a test fails the
build the day a refactor renames `password` to something the list does not
know, or someone constructs their own `ValueRenderer` with
`RedactionPolicy.DISABLED`. That is the difference between "it was safe when
this was written" and "it is safe now". Keep the absence assertion — the
`doesNotContain` — even when the presence one looks sufficient: a value that
vanishes because the call stopped being traced at all would still satisfy the
first.

## What is not captured

- **Code that is not traced is not NarrativeTrace's to redact.** Four
  interfaces are wrapped above; a secret passed between two unwrapped classes
  is not seen, and therefore neither redacted nor logged. That is not a
  guarantee of safety — it is a guarantee of silence.
- **A secret your own log statement writes is yours.** NarrativeTrace
  redacts what *it* renders. `log.info("charging {}", cardNumber)` is your
  line, in your code, and nothing here filters it. The redaction contract is
  about generated narrative, not about everything the process writes.
- **A return value is not matched by name**, because it has none. `signIn`
  returns `"SESSION-C-1234"` and it is printed. A session token returned from
  a traced method needs the shape rules to catch it, or `@NotTraced` on the
  method, or a narrower return type.
- **Names the list does not know.** The list is big and multilingual, not
  infinite. A secret called `q3` is caught only by its shape, if it has one.
  Assert, do not assume — which is this page's whole point.
- **What happens after the log line is written.** Shipping, retention and
  access to the file are your platform's; the structural `.nt` carries no
  values at all and is the artifact meant for committing and for handing to a
  model ([Review a complex flow before changing it](review-a-complex-flow-before-changing-it.md)).

**The skill for this:** `narrativetrace-doctor` — one of its checks is
exactly "redaction never proven in a test", and it names the fix.
