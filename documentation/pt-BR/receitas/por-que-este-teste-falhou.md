<!-- source: documentation/recipes/why-did-this-test-fail.md blob 3e9b39b9dd80 | translated: 2026-10-11 | reviewed: - -->
# Por que este teste falhou

[English](../../recipes/why-did-this-test-fail.md) | [Español](../../es/recetas/por-que-fallo-esta-prueba.md) | **Português** | [简体中文](../../zh-CN/实用方案/这个测试为什么失败了.md)

## O problema

> Por que este teste falhou?

Um teste do checkout está vermelho. A asserção diz o que não casou; ela não diz o
que o código fez no caminho até ali, que é a parte de que você precisa para saber
se o errado é o código ou a expectativa.

## Configuração mínima

Nada além da [instalação](../guia-de-instalacao.md). A extensão do JUnit 5
escreve uma narrativa por método de teste sem nenhuma configuração — para um
teste que falha também, que é o caso desta página. Os arquivos aparecem em
`build/narrativetrace/traces/<ClasseDeTeste>/<cenário>.md`, e
`build/narrativetrace/manifest.json` lista todos os artefatos de cada cenário
quando você sabe o nome do teste mas não o do arquivo dele.

## O cenário

O teste que falha:

```java
// src/test/java/com/example/checkout/StaleExpectationFixture.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The failing checkout test documentation/recipes/why-did-this-test-fail.md is read through. It
 * fails on an assertion, by design, which is why it is a fixture: {@code tasks.test} excludes
 * {@code *Fixture}, and {@code TestFailureRecipeTest} runs it through the JUnit Platform launcher
 * and writes down what the run reported.
 *
 * <p><b>@llmNote</b> Nothing here says which side is wrong — that is the reader's exercise, and the
 * narrative the run writes is what settles it. Do not add a comment that gives it away.
 */
@ExtendWith(NarrativeTraceExtension.class)
class StaleExpectationFixture {

  /** A fixed W3C traceparent, so the page's embedded artifact names the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  @Test
  void the_customer_gets_an_order_id_for_their_keyboards(NarrativeContext context) {
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

    var orderId = checkout.placeOrder("C-1234", "SKU-KB", 2, "4242424242424242");

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-1");
  }
}
```

## A saída real

O que o runner reportou:

```text
expected: "ORD-C-1234-SKU-KB-1"
 but was: "ORD-C-1234-SKU-KB-2"
```

E o que a mesma execução escreveu ao lado, em
`build/narrativetrace/traces/StaleExpectationFixture/the_customer_gets_an_order_id_for_their_keyboards.md`:

```text
---
type: trace
run: mini wedge soars
scenario: The customer gets an order id for their keyboards
entry_point: OrderService.placeOrder
duration_ms: 2.435
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 0
---

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The customer gets an order id for their keyboards
**Duration:** 2.435ms | **Result:** FAILED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 2.435ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.389ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 0.243ms #1.2
  - **OrderRepository.save**(order: `Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998)`) — 0.323ms #1.3
```

## Como ler

Leia a narrativa de cima para baixo contra a asserção, um span por vez:

- `#1 OrderService.placeOrder(... quantity: 2 ...) → "ORD-C-1234-SKU-KB-2"`. O
  valor que a asserção rejeitou está ali mesmo, e a entrada que o produziu
  também: a chamada pediu **dois** teclados.
- `#1.1 InventoryService.reserve(sku: "SKU-KB", quantity: 2)` — duas unidades
  reservadas, coerente com a chamada.
- `#1.2 PaymentService.charge(..., amountCents: 7998, ...)` — 7998 são duas
  unidades a 3999, então o preço também concorda com a quantidade.
- `#1.3 OrderRepository.save(order: Order(... quantity: 2 ...))` — o que foi
  guardado concorda com o que foi pedido.
- `**Result:** FAILED` no cabeçalho é o veredicto do *teste*, não do código:
  `error_count: 0` e todos os spans se completaram. Nada lançou.

Cada span concorda com os outros e com a chamada que os iniciou. A expectativa —
`ORD-C-1234-SKU-KB-1` — é a única coisa na execução que diz um teclado, então é
ela que está desatualizada; o fixture contra o qual foi escrita passou a pedir
dois. A correção está no teste.

A leitura oposta é a outra receita: quando um span *discorda* da intenção — uma
quantidade que muda entre duas chamadas, uma chamada que deveria ter seguido e
não seguiu — o código está errado, e
[Ajude-me a depurar e corrigir este bug](ajude-me-a-depurar-e-corrigir-este-bug.md)
é esse passo a passo.

## O que não é capturado

- **Apenas aquela execução daquele teste.** A narrativa é escrita por método de
  teste e por execução. Uma falha que acontece uma vez a cada vinte execuções
  precisa que uma delas seja a que escreveu o arquivo; uma execução que passa não
  sobrescreve nada (cada execução escreve seu próprio arquivo, e o `.nt` livre de
  valores de uma execução que falhou não avança).
- **A asserção não está no trace.** As duas saídas acima vêm de dois lugares — o
  runner reportou a divergência, a extensão escreveu o fluxo — e nada as
  correlaciona automaticamente além do nome do cenário.
- **Um valor que o teste calculou mas nunca passou a uma chamada rastreada é
  invisível.** O trace carrega parâmetros e valores de retorno de chamadas
  envolvidas, não variáveis locais.
- **Os tempos de uma execução que falhou não significam nada.** A execução deixou
  de ser representativa no instante em que a asserção lançou.

**A skill para isto:** `narrativetrace-debug` — ela parte do sintoma, lê o
primeiro span onde um valor diverge e o nomeia pelo identificador de span.
