<!-- source: documentation/recipes/review-a-complex-flow-before-changing-it.md blob 8b35520bf49a | translated: 2026-10-22 | reviewed: - -->
# Revise um fluxo complexo antes de alterá-lo

[English](../../recipes/review-a-complex-flow-before-changing-it.md) | [Español](../../es/recetas/revisa-un-flujo-complejo-antes-de-cambiarlo.md) | **Português** | [简体中文](../../zh-CN/实用方案/修改复杂流程前先审查它.md)

## O problema

> Antes de alterar o checkout, me diga o que ele faz de verdade.

Ler o código-fonte responde o que ele *pode* fazer. Uma execução responde o que
ele *faz* com estas entradas — em que ordem, com quais valores e onde o fluxo
sai do código que você está prestes a editar. Esta página de propósito nunca
mostra a implementação: é esse o sentido de ler uma execução em vez do código.

## Configuração mínima

Nada além da [instalação](../guia-de-instalacao.md) para os dois artefatos de
trace. As linhas de log precisam do módulo SLF4J e de uma configuração do
Logback, exatamente como em
[Adicione logs a este serviço](adicione-logs-a-este-servico.md). O nível de
captura é uma propriedade, e `SUMMARY` é o que esta página usa ao lado do
`DETAIL` padrão:

```kotlin
// build.gradle.kts
narrativeTrace {
    tracingLevel.set("SUMMARY")   // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
}
```

## O cenário

Uma execução representativa do fluxo — um teste existente é o mais barato de se
emprestar, e é este teste:

```java
// src/test/java/com/example/checkout/CheckoutReviewFixture.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * One successful checkout, the flow
 * documentation/recipes/review-a-complex-flow-before-changing-it.md is read before changing
 * anything.
 *
 * <p>A fixture because two readers need the same run under different settings: {@code
 * ReviewFlowRecipeTest} runs it through the JUnit Platform launcher once at DETAIL and once at
 * SUMMARY, and the Gradle task {@code recipeReviewFlowLog} runs it alone so one scenario's log
 * lines land in one file. It passes either way — nothing here fails by design.
 */
@ExtendWith(NarrativeTraceExtension.class)
class CheckoutReviewFixture {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  @Test
  void a_customer_buys_two_keyboards(NarrativeContext context) {
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

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(3);
    assertThat(repository.saved()).hasSize(1);
  }
}
```

## A saída real

**Em `DETAIL`** (o padrão), `build/narrativetrace/traces/CheckoutReviewFixture/a_customer_buys_two_keyboards.md`:

```text
---
type: trace
run: curly fly romps
scenario: A customer buys two keyboards
entry_point: OrderService.placeOrder
duration_ms: 4.335
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 0
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: CheckoutReviewFixture.a_customer_buys_two_keyboards, passed, 1.996 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: none, no call threw in this run.
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** A customer buys two keyboards
**Duration:** 4.335ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 4.335ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.692ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 0.427ms #1.2
  - **OrderRepository.save**(order: `Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998)`) — 0.356ms #1.3
```

**Em `SUMMARY`**, a mesma execução do mesmo teste:

```text
---
type: trace
run: sweet hare notes
scenario: A customer buys two keyboards
entry_point: OrderService.placeOrder
duration_ms: 1.909
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 0
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: CheckoutReviewFixture.a_customer_buys_two_keyboards, passed, 1.334 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: none, no call threw in this run.
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** A customer buys two keyboards
**Duration:** 1.909ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: …, sku: …, quantity: …, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 1.909ms #1
  - **InventoryService.reserve**(sku: …, quantity: …) — 0.274ms #1.1
  - **PaymentService.charge**(customerId: …, amountCents: …, card: …) → `"PAY-C-1234-7998"` — 0.27ms #1.2
  - **OrderRepository.save**(order: …) — 0.233ms #1.3
```

**O log que o mesmo fluxo escreveu** pelo próprio logger do projeto, com valores
e tudo:

```text
TRACE [solid wand romps] [narrativetrace] - → OrderService.placeOrder(customerId: "C-1234", sku: "SKU-KB", quantity: 2, cardNumber: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - → InventoryService.reserve(sku: "SKU-KB", quantity: 2)
TRACE [solid wand romps] [narrativetrace] - ← completed
TRACE [solid wand romps] [narrativetrace] - → PaymentService.charge(customerId: "C-1234", amountCents: 7998, card: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - ← returned: "PAY-C-1234-7998"
TRACE [solid wand romps] [narrativetrace] - → OrderRepository.save(order: Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998))
TRACE [solid wand romps] [narrativetrace] - ← completed
TRACE [solid wand romps] [narrativetrace] - ← returned: "ORD-C-1234-SKU-KB-2"
```

**O trace estrutural dessa execução**, `build/narrativetrace/structural/CheckoutReviewFixture/a_customer_buys_two_keyboards.nt`:

```text
scenario: A customer buys two keyboards

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) → value
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) → value
  #1.3 - OrderRepository.save(order)
```

## Como ler

**O fluxo, pelo artefato `DETAIL`.** `#1` é a chamada que o teste fez e a única
raiz: tudo indentado abaixo dela aconteceu dentro dela, na ordem impressa.
`#1.1` reserva duas unidades antes de qualquer cobrança; `#1.2` cobra 7998 —
duas unidades a 3999, então o preço é derivado da quantidade e não passado;
`#1.3` salva um pedido que já carrega o identificador que `#1` devolve. Lido como
uma frase: este checkout reserva primeiro, cobra depois, salva por último, e o
identificador do pedido é cunhado antes de salvar. Uma mudança que cobre antes de
reservar, ou que salve antes de cobrar, é uma mudança *nisso*, e agora você sabe
disso sem confiar em um comentário.

**Para que serve cada nível.** `DETAIL` carrega os valores dos parâmetros e é o
que você lê enquanto trabalha no fluxo. Em `SUMMARY` todo valor de parâmetro é
`…` — suprimido na captura, não escondido na renderização — e só as raízes e as
chamadas folha são mantidas; este fluxo tem dois níveis de profundidade, então
nenhum quadro é intermediário e as mesmas quatro chamadas aparecem. Um fluxo mais
profundo perde o meio. Os valores de retorno sobrevivem em todo nível ativo: o
retorno é a carga útil de uma narrativa. Use `SUMMARY` onde a forma e os
resultados são o que você precisa e os valores são custo — uma execução de CI, um
soak longo — e `OFF`/`ERRORS` em produção até precisar de mais.

**Qual saída uma pessoa lê e qual um agente lê.** As linhas de log são para quem
está de plantão: elas se intercalam com tudo o mais que o serviço registra,
carregam os valores e são indexadas pelo nome do trace — `[solid wand romps]` —
então um checkout pode ser extraído com grep de um arquivo movimentado. O `.nt` é
para o agente e para quem revisa: **livre de valores por construção** — compare-o
com a linha de log acima e a diferença não é que os valores foram substituídos,
mas que eles nunca foram escritos. `(customerId, sku, quantity, cardNumber)` são
os *nomes* dos parâmetros; `→ value` diz que um valor foi devolvido sem dizer
qual. É isso que torna seguro commitá-lo, vê-lo num diff de revisão e entregá-lo
a um modelo: ele não pode vazar o identificador de um cliente, e não muda quando
os dados mudam — só quando as *chamadas* mudam. Fixá-lo é
[Revise uma refatoração: o comportamento mudou?](revise-uma-refatoracao-o-comportamento-mudou.md).

## O que não é capturado

- **Chamadas dentro de classes não envolvidas.** Quatro interfaces foram
  envolvidas; uma chamada entre duas classes atrás delas, ou um método privado,
  nunca aparece. Um fluxo que parece raso pode ser profundo.
- **O que a integração escolhida não intercepta.** O proxy JDK vê somente as
  chamadas feitas *através* da referência envolvida: uma chamada interna que uma
  classe faz a si mesma (`this.helper()`) é invisível para ele, enquanto o agente
  Java a veria. [Escolhendo uma integração](../escolhendo-uma-integracao.md) é o
  trade-off de cada caminho, e esta é a parte dele que muda o que você pode ler
  aqui.
- **Trabalho que saiu da thread.** Uma chamada entregue a um executor sem o
  contexto não viaja mais longe; o trace termina onde a thread terminou.
- **Valores que a lista de negação ou a regra de forma do valor pegaram**, aqui
  `cardNumber` e `card`, são `[REDACTED]` tanto no log quanto no artefato
  `DETAIL`, e ausentes por construção do `.nt`.
- **Uma execução, não o contrato.** Outra entrada toma outro caminho — um sku sem
  estoque nunca chega a `#1.2`.

**A skill para isto:** `narrativetrace-verify` — ela escreve a intenção primeiro,
roda o menor caminho real e lê o trace livre de valores contra essa intenção
antes de qualquer coisa ser dada por pronta.
