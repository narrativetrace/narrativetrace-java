<!-- source: documentation/recipes/help-me-debug-and-fix-this-bug.md blob f2a6f2f6e0b7 | translated: 2026-10-22 | reviewed: - -->
# Ajude-me a depurar e corrigir este bug

[English](../../recipes/help-me-debug-and-fix-this-bug.md) | [Español](../../es/recetas/ayudame-a-depurar-y-arreglar-este-error.md) | **Português** | [简体中文](../../zh-CN/实用方案/帮我调试并修复这个缺陷.md)

## O problema

> Ajude-me a depurar e corrigir este bug: o cartão de um cliente foi recusado e o
> teclado nunca voltou ao estoque.

O erro que o time tem é `PaymentDeclinedException: card declined by the issuer`,
que não é o bug. O bug é o que o checkout fez *depois* dele.

## Configuração mínima

Nada além da [instalação](../guia-de-instalacao.md): a extensão do JUnit 5
escreve um trace por teste sem nenhuma configuração. O último passo desta página
— fixar o fluxo corrigido — liga o modo de aprovação, que é uma única
propriedade:

```kotlin
// build.gradle.kts
narrativeTrace {
    approval.set(true)   // compara a estrutura de cada teste que passa com sua baseline
}
```

Veja [Guia do plugin de Gradle, `approval`](../guia-do-plugin-de-gradle.md#approval)
e [O que commitar](../o-que-commitar.md#modo-de-aprovação-de-ponta-a-ponta).

## O cenário

Reproduza no menor teste que possa falhar por esse motivo — um cartão recusado
tem de deixar o estoque como o encontrou:

```java
// src/test/java/com/example/checkout/DeclinedPaymentFixture.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The reproduction documentation/recipes/help-me-debug-and-fix-this-bug.md is written around: a
 * declined card must leave the stock as it found it.
 *
 * <p>The second test is the same reproduction against the checkout as it stood before the fix
 * ({@link OrderServiceWithoutRelease}), so the page's "before" trace comes from a real failing run
 * rather than from prose. It FAILS by design, which is why this class is a fixture: {@code
 * tasks.test} excludes {@code *Fixture}, and {@code DebugRecipeTest} runs it through the JUnit
 * Platform launcher and asserts on its verdict.
 *
 * <p><b>@llmNote</b> Both tests adopt the same fixed traceparent, so the two artifacts a reader
 * compares differ only where the code differs.
 */
@ExtendWith(NarrativeTraceExtension.class)
class DeclinedPaymentFixture {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  @Test
  void the_stock_is_released_when_the_card_is_declined(NarrativeContext context) {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));

    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    var checkout =
        NarrativeTraceProxy.trace(
            new DefaultOrderService(inventory, payments, orders), OrderService.class, context);

    assertThatThrownBy(
            () -> checkout.placeOrder("C-1234", "SKU-KB", 2, CardPaymentService.DECLINING_CARD))
        .isInstanceOf(PaymentDeclinedException.class);

    assertThat(repository.saved()).isEmpty();
    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(5);
  }

  @Test
  void the_stock_is_released_when_the_card_is_declined_before_the_fix(NarrativeContext context) {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));

    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    var checkout =
        NarrativeTraceProxy.trace(
            new OrderServiceWithoutRelease(inventory, payments, orders),
            OrderService.class,
            context);

    assertThatThrownBy(
            () -> checkout.placeOrder("C-1234", "SKU-KB", 2, CardPaymentService.DECLINING_CARD))
        .isInstanceOf(PaymentDeclinedException.class);

    assertThat(repository.saved()).isEmpty();
    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(5);
  }
}
```

O segundo teste roda a mesma reprodução contra o checkout como ele era antes da
correção, que é a execução de onde vem o primeiro trace abaixo:

```java
// src/main/java/com/example/checkout/OrderServiceWithoutRelease.java
package com.example.checkout;

/**
 * The same checkout with no release path: a declined charge leaves the reservation behind.
 *
 * <p>INTENT: Two recipe pages need this code to RUN, not to be described — {@code
 * documentation/recipes/help-me-debug-and-fix-this-bug.md} (the bug, before its fix) and {@code
 * documentation/recipes/review-a-refactoring-did-behaviour-change.md} (the refactoring that drops
 * the release call while every test stays green). It is kept beside {@link DefaultOrderService}
 * rather than reconstructed by a flag, so each page embeds real source and each run is a real run.
 *
 * <p><b>@llmNote</b> Never the implementation to copy: {@link DefaultOrderService} is. A trace
 * names the interface, not the class, so both render as {@code OrderService.placeOrder} — which is
 * exactly what makes one approved baseline comparable across the two.
 */
public final class OrderServiceWithoutRelease implements OrderService {

  private static final int UNIT_PRICE_CENTS = 3999;

  private final InventoryService inventory;
  private final PaymentService payments;
  private final OrderRepository orders;

  public OrderServiceWithoutRelease(
      InventoryService inventory, PaymentService payments, OrderRepository orders) {
    this.inventory = inventory;
    this.payments = payments;
    this.orders = orders;
  }

  @Override
  public String placeOrder(String customerId, String sku, int quantity, String cardNumber) {
    inventory.reserve(sku, quantity);
    var amountCents = UNIT_PRICE_CENTS * quantity;
    payments.charge(customerId, amountCents, cardNumber);
    var orderId = "ORD-" + customerId + "-" + sku + "-" + quantity;
    orders.save(new Order(orderId, customerId, sku, quantity, amountCents));
    return orderId;
  }
}
```

## A saída real

A narrativa da própria execução que falhou, em
`build/narrativetrace/traces/DeclinedPaymentFixture/the_stock_is_released_when_the_card_is_declined_before_the_fix.md`:

```text
---
type: trace
run: elfin crest nods
scenario: The stock is released when the card is declined before the fix
entry_point: OrderService.placeOrder
duration_ms: 2.003
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 3
error_count: 2
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: DeclinedPaymentFixture.the_stock_is_released_when_the_card_is_declined_before_the_fix, failed, 1.462 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: line 31 of this file (the first call that threw).
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The stock is released when the card is declined before the fix
**Duration:** 2.003ms | **Result:** FAILED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) — 2.003ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.381ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) #1.2

    > ❌ `PaymentDeclinedException`: card declined by the issuer — 0.321ms
  - ❌ `PaymentDeclinedException`: card declined by the issuer
```

A correção é um `catch`: libere o que foi reservado e deixe a recusa se propagar
como antes.

```java
// src/main/java/com/example/checkout/DefaultOrderService.java
package com.example.checkout;

/** The checkout: reserve the stock, charge the card, save the order — and release on a decline. */
public final class DefaultOrderService implements OrderService {

  private static final int UNIT_PRICE_CENTS = 3999;

  private final InventoryService inventory;
  private final PaymentService payments;
  private final OrderRepository orders;

  public DefaultOrderService(
      InventoryService inventory, PaymentService payments, OrderRepository orders) {
    this.inventory = inventory;
    this.payments = payments;
    this.orders = orders;
  }

  @Override
  public String placeOrder(String customerId, String sku, int quantity, String cardNumber) {
    inventory.reserve(sku, quantity);
    var amountCents = UNIT_PRICE_CENTS * quantity;
    try {
      payments.charge(customerId, amountCents, cardNumber);
    } catch (PaymentDeclinedException declined) {
      inventory.release(sku, quantity);
      throw declined;
    }
    var orderId = "ORD-" + customerId + "-" + sku + "-" + quantity;
    orders.save(new Order(orderId, customerId, sku, quantity, amountCents));
    return orderId;
  }
}
```

A mesma reprodução, agora passando, contra o checkout corrigido:

```text
---
type: trace
run: elfin crest nods
scenario: The stock is released when the card is declined
entry_point: OrderService.placeOrder
duration_ms: 3.03
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 2
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: DeclinedPaymentFixture.the_stock_is_released_when_the_card_is_declined, passed, 1.835 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: line 31 of this file (the first call that threw).
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The stock is released when the card is declined
**Duration:** 3.03ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) — 3.03ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.608ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) #1.2

    > ❌ `PaymentDeclinedException`: card declined by the issuer — 0.404ms
  - **InventoryService.release**(sku: `"SKU-KB"`, quantity: `2`) — 0.367ms #1.3
  - ❌ `PaymentDeclinedException`: card declined by the issuer
```

Com o modo de aprovação ligado, a primeira execução do teste corrigido escreve
sua estrutura livre de valores ao lado do teste como `.received.nt`;
`./gradlew approveNarratives` a promove à baseline commitada, que agora é a forma
deste fluxo:

```text
scenario: The stock is released when the card is declined

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
  #1.3 - InventoryService.release(sku, quantity)
```

## Como ler

No trace que falha:

- `#1 OrderService.placeOrder` termina com `❌ PaymentDeclinedException` e sem
  valor de retorno: a chamada que o cliente fez não se completou.
- `#1.1 InventoryService.reserve(sku: "SKU-KB", quantity: 2)` não tem `❌` nem
  valor — um método `void` que se completou. Duas unidades estão retidas agora.
- `#1.2 PaymentService.charge` lançou. Seus parâmetros dizem o que foi tentado:
  `amountCents: 7998` pelas duas unidades, `card: [REDACTED]`.
- **Depois de `#1.2` não há nada.** `#1` vai direto da cobrança para a própria
  falha, então as duas unidades que `#1.1` reservou continuam retidas. Essa
  ausência é o bug, e a mensagem da exceção nunca a menciona — o trace é o que
  torna uma omissão visível, porque mostra o fluxo inteiro e não só a linha que
  lançou.

No trace que passa: `#1.3 InventoryService.release(sku: "SKU-KB", quantity: 2)`
agora fica entre a cobrança e a falha. `#1` ainda termina na recusa — o que está
correto, o cartão do cliente realmente foi recusado — e o estoque voltou.

Na baseline: o `.nt` aprovado carrega as mesmas quatro chamadas com os *nomes* de
seus parâmetros e nenhum valor, que é o que permite commitá-lo e lê-lo em uma
revisão. `#1.3` já faz parte da forma fixada: uma mudança posterior que remova a
liberação faz um teste que passa falhar com um diff que nomeia
`InventoryService.release`, de modo que este bug não pode voltar em silêncio.
Isso é
[Revise uma refatoração: o comportamento mudou?](revise-uma-refatoracao-o-comportamento-mudou.md).

## O que não é capturado

- **O trace mostra as chamadas que aconteceram, não as que deveriam ter
  acontecido.** O `release` ausente é uma ausência que o leitor infere do fluxo;
  nada no artefato diz "falta uma chamada". O que a baseline acrescenta é a
  comparação que transforma uma ausência em falha de build — uma vez.
- **Uma exceção engolida dentro de código não envolvido é invisível.** Só as
  quatro interfaces envolvidas aparecem. Um `try`/`catch` dentro de
  `DefaultOrderService` que comesse a recusa e retornasse normalmente apareceria
  como uma chamada completada.
- **O número do cartão não pode ser recuperado** do trace nem do log. Ele é
  ocultado na captura, aqui pelo valor — o parâmetro se chama `card`, então a
  lista de negação por nome nunca o viu, e o número é válido por Luhn.
- **Os tempos não são evidência de problema de desempenho** em um teste tão
  pequeno: a primeira chamada envolvida de uma JVM carrega a preparação única do
  proxy.

**A skill para isto:** `narrativetrace-debug` — ela reproduz o sintoma com a
menor entrada, nomeia o span onde o valor ou a chamada divergem, corrige ali e
fixa o fluxo como teste de regressão com a sua aprovação.
