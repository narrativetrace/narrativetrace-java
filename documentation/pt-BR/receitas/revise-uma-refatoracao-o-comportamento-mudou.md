<!-- source: documentation/recipes/review-a-refactoring-did-behaviour-change.md blob 5157fea6e605 | translated: 2026-10-11 | reviewed: - -->
# Revise uma refatoração: o comportamento mudou?

[English](../../recipes/review-a-refactoring-did-behaviour-change.md) | [Español](../../es/recetas/revisa-una-refactorizacion-cambio-el-comportamiento.md) | **Português** | [简体中文](../../zh-CN/实用方案/审查重构行为变了吗.md)

## O problema

> Refatorei o checkout. Os testes estão verdes. O comportamento mudou?

Verde significa que nenhuma asserção notou. Uma refatoração que remove uma
chamada sobre a qual ninguém faz asserção fica verde e está errada, e esta é a
forma comum disso: a chamada compensatória no caminho de erro, onde nenhum teste
do caminho feliz chega.

## Configuração mínima

Além da [instalação](../guia-de-instalacao.md), o modo de aprovação — uma
propriedade e um diretório de baselines commitadas:

```kotlin
// build.gradle.kts
narrativeTrace {
    approval.set(true)                              // comparar, não só escrever
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))   // o padrão
}
```

A primeira execução de cada teste escreve
`src/test/narratives/<ClasseDeTeste>/<cenário>.received.nt` e falha; você o lê,
roda `./gradlew approveNarratives` para promover cada arquivo revisado a
`.approved.nt`, e commita esses. Veja
[Guia do plugin de Gradle, `approveNarratives`](../guia-do-plugin-de-gradle.md#approvenarratives)
e [O que commitar](../o-que-commitar.md#modo-de-aprovação-de-ponta-a-ponta).

## O cenário

O teste fixa o caminho de erro que uma refatoração tem mais chance de quebrar —
um cartão recusado não deve salvar pedido algum — e ele passa contra as duas
versões do checkout, que é justamente o problema:

```java
// src/test/java/com/example/checkout/RefactoredCheckoutFixture.java
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
 * The test documentation/recipes/review-a-refactoring-did-behaviour-change.md pins: a declined card
 * must save no order. It passes before and after the refactoring — which is the whole point of the
 * page — so only the approved structure can object.
 *
 * <p>A fixture because {@code RefactoringRecipeTest} runs it twice under approval mode, once per
 * version of the checkout, and reads the verdicts.
 */
@ExtendWith(NarrativeTraceExtension.class)
class RefactoredCheckoutFixture {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  /**
   * Which version of the checkout this run wires: the one that releases the reservation when the
   * charge is declined, or the refactoring that dropped that call. A field, because one build has
   * to run both — in your project the refactoring is an edit to one file and this switch does not
   * exist.
   */
  static volatile boolean refactoringDroppedTheRelease;

  @Test
  void a_declined_card_saves_no_order(NarrativeContext context) {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));

    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    var checkout =
        NarrativeTraceProxy.trace(
            refactoringDroppedTheRelease
                ? new OrderServiceWithoutRelease(inventory, payments, orders)
                : new DefaultOrderService(inventory, payments, orders),
            OrderService.class,
            context);

    assertThatThrownBy(
            () -> checkout.placeOrder("C-1234", "SKU-KB", 2, CardPaymentService.DECLINING_CARD))
        .isInstanceOf(PaymentDeclinedException.class);

    assertThat(repository.saved()).isEmpty();
  }
}
```

## A saída real

A baseline commitada, em
`src/test/narratives/RefactoredCheckoutFixture/a_declined_card_saves_no_order.approved.nt`:

```text
scenario: A declined card saves no order

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
  #1.3 - InventoryService.release(sku, quantity)
```

O veredicto da execução refatorada — as asserções passaram, a estrutura não:

```text
Narrative changed against the approved baseline (-1 call InventoryService.release):
 scenario: A declined card saves no order
 
 #1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
   #1.1 - InventoryService.reserve(sku, quantity)
   #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
-  #1.3 - InventoryService.release(sku, quantity)
Received: build/narrativetrace/recipes/review-a-refactoring-did-behaviour-change/approved-after-the-refactoring/RefactoredCheckoutFixture/a_declined_card_saves_no_order.received.nt
If this change is intended, approve it via the approveNarratives task. A stale .received.nt left after you resolve this is exactly what the approval-traces check catches → narrativetrace-doctor skill
```

E o arquivo que ela oferece para revisão, o que `approveNarratives` promoveria:

```text
scenario: A declined card saves no order

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) !! PaymentDeclinedException
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) !! PaymentDeclinedException
```

(O caminho em `Received:` é o deste repositório: a execução que produz esta página
aponta `approvedDir` para dentro de `build/`, porque a tarefa que escreve uma
página não pode escrever na árvore de fontes. No seu projeto é
`src/test/narratives`, ao lado da baseline.)

## Como ler

- **A primeira linha nomeia a mudança, não só o fato dela:**
  `-1 call InventoryService.release`. Uma chamada a menos, e qual.
- **O corpo do diff é a forma fixada com a linha removida marcada.** `#1`, `#1.1`
  e `#1.2` estão inalterados e sem marca;
  `-  #1.3 - InventoryService.release(sku, quantity)` é o comportamento que foi
  embora. Quem revisa sem nunca ter visto este código consegue dizer o que a
  refatoração fez a partir de quatro linhas.
- **O teste continuou passando.** A falha é da aprovação, e é esse o ponto: a
  suíte afirma que nenhum pedido é salvo, e a refatoração não quebrou isso. Nada
  no teste jamais olhou para a liberação, e nada precisava.
- **Agora decida.** Se remover a liberação é intencional — quem chama assumiu a
  tarefa, digamos — rode `./gradlew approveNarratives`, revise o diff no arquivo
  promovido e commite: a nova forma passa a ser a fixada. Se não é intencional,
  você acabou de pegar o bug que
  [Ajude-me a depurar e corrigir este bug](ajude-me-a-depurar-e-corrigir-este-bug.md)
  corrigiu, voltando. De todo modo a decisão fica registrada no repositório, por
  uma pessoa, em um arquivo que quem revisa pode ler.
- **A baseline é livre de valores**, então aprová-la não vaza nada e uma mudança
  nos dados de teste nunca a faz falhar:
  `(customerId, sku, quantity, cardNumber)` são nomes,
  `!! PaymentDeclinedException` é um resultado.

## O que não é capturado

- **Esta é uma comparação estrutural: as mesmas chamadas, na mesma ordem, com os
  mesmos resultados.** Não é prova de que todo valor seja igual. Uma refatoração
  que cobre 7998 onde cobrava 3999, com todas as chamadas no lugar, passa por
  este portão — é para isso que suas asserções existem, e o artefato `DETAIL` é
  onde você veria.
- **Somente as chamadas que a integração intercepta.** Uma chamada privada
  removida, ou uma entre duas classes não envolvidas, é invisível para a baseline
  exatamente como é para o trace.
- **Um teste que não roda não é comparado.** A aprovação protege os cenários que
  você fixou; um fluxo sem teste não tem baseline, e apagar um teste apaga o
  portão dele (o `.approved.nt` fica órfão, algo que `narrativetrace-doctor`
  reporta).
- **Os tempos nunca fazem parte disso.** A baseline não carrega durações, então
  não pode pegar uma mudança que só deixou o fluxo mais lento.

**A skill para isto:** `narrativetrace-verify` — ela lê o trace estrutural contra
a intenção da mudança e fixa o fluxo como baseline com o seu sim.
