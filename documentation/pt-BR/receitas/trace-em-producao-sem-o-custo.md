<!-- source: documentation/recipes/trace-in-production-without-the-cost.md blob c6daba7c3d47 | translated: 2026-10-22 | reviewed: - -->
# Trace em produção sem o custo

[English](../../recipes/trace-in-production-without-the-cost.md) | [Español](../../es/recetas/traza-en-produccion-sin-el-coste.md) | **Português** | [简体中文](../../zh-CN/实用方案/在生产环境追踪而不付出代价.md)

## O problema

> Eu quero isso em produção para as requisições que dão errado. Não vou pagar
> por um trace completo de cada requisição que dá certo.

Isso é um único ajuste — `TracingLevel` — e esta página é a mesma execução
vista por ele duas vezes: o checkout com o cartão recusado em `DETAIL`, onde
cada valor é capturado, e em `ERRORS`, onde só as execuções que falharam
sobrevivem.

## Configuração mínima

Um nível, definido onde o seu deploy é configurado. Estas três formas dizem a
mesma coisa:

```kotlin
// build.gradle.kts — for test runs through the Gradle plugin
narrativeTrace {
    tracingLevel.set("ERRORS")   // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
}
```

```properties
# narrativetrace.properties — for a plain Java or java-agent deployment
narrativetrace.level=ERRORS
```

```bash
# or on the command line, which wins over the file
java -Dnarrativetrace.level=ERRORS -jar app.jar
```

O nível é o dial do próprio NarrativeTrace e ele fica na frente de tudo: uma
chamada que ele filtra nunca chega a ser um evento, para nenhum consumidor. O
nível do seu logger é um segundo dial, independente, que decide o que é
*impresso* — as
[perguntas frequentes, "Two dials, two paths"](../../faq.md) (em inglês) são a
versão longa, e a distinção importa aqui porque subir o nível de um logger
economiza volume de log sem mudar nada na captura.

## O cenário

A execução é a reprodução do cartão recusado de
[Ajude-me a depurar e corrigir este bug](ajude-me-a-depurar-e-corrigir-este-bug.md)
— `reserve`, depois `charge` lançando, depois `release` — reutilizada sem
mudanças, porque o assunto desta página é o dial e não outro fluxo. O driver
abaixo roda ela nos dois níveis e também roda um checkout *bem-sucedido* em
`ERRORS`:

```java
// src/test/java/com/example/checkout/CostRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/**
 * Proves documentation/recipes/trace-in-production-without-the-cost.md: the SAME declined-payment
 * run, captured once at {@code DETAIL} and once at {@code ERRORS}, writes the two artifacts that
 * page embeds — and what {@code ERRORS} drops is every call that returned normally (rule 8, docs as
 * tests).
 *
 * <p><b>@llmNote</b> The scenario is {@link DeclinedPaymentFixture}'s fixed reproduction, reused
 * rather than rewritten: this page's subject is the level dial, not another flow, and a reader
 * comparing the two levels should be comparing one run's renderings. Only the passing method is
 * selected — the fixture's other test fails by design for
 * documentation/recipes/help-me-debug-and-fix-this-bug.md.
 */
class CostRecipeTest {

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/trace-in-production-without-the-cost");

  private static final String SCENARIO = "the_stock_is_released_when_the_card_is_declined";

  @Test
  void errors_keeps_the_failed_run_whole_and_writes_nothing_for_a_successful_one()
      throws IOException {
    var errors = capture("errors", "ERRORS");

    assertThat(errors)
        .contains(
            "OrderService.placeOrder",
            "InventoryService.reserve",
            "PaymentService.charge",
            "InventoryService.release");
    // The control: the same path IS written when the level keeps successful runs, so the
    // absence below is the level's doing and not a path that was never right.
    assertThat(successfulRunAt(null)).exists();
    assertThat(successfulRunAt("ERRORS")).doesNotExist();
  }

  @Test
  void detail_carries_the_values_errors_suppresses() throws IOException {
    assertThat(capture("detail", null)).contains("customerId: `\"C-1234\"`");
    assertThat(capture("errors", "ERRORS")).contains("customerId: …").doesNotContain("\"C-1234\"");
  }

  @Test
  void both_levels_still_redact_the_card_number() throws IOException {
    assertThat(capture("detail", null)).contains("[REDACTED]").doesNotContain("4000000000000002");
    assertThat(capture("errors", "ERRORS"))
        .contains("[REDACTED]")
        .doesNotContain("4000000000000002");
  }

  /**
   * The successful checkout at [level]: at ERRORS nothing failed, so there is no trace to write.
   */
  private static Path successfulRunAt(String level) {
    var outputDir = PAGE_OUTPUT.resolve(level == null ? "detail-successful" : "errors-successful");
    var previous = System.getProperty("narrativetrace.level");
    setOrClear(level);
    try {
      var run =
          RecipeRuns.run(DiscoverySelectors.selectClass(CheckoutReviewFixture.class), outputDir);
      assertThat(run.getTestsFailedCount()).isZero();
    } finally {
      setOrClear(previous);
    }
    return outputDir.resolve("traces/CheckoutReviewFixture/a_customer_buys_two_keyboards.md");
  }

  /** Runs the reproduction into its own directory, at [level] (null leaves the default, DETAIL). */
  private static String capture(String directory, String level) throws IOException {
    var outputDir = PAGE_OUTPUT.resolve(directory);
    var previous = System.getProperty("narrativetrace.level");
    setOrClear(level);
    try {
      var run =
          RecipeRuns.run(
              DiscoverySelectors.selectMethod(
                  DeclinedPaymentFixture.class, SCENARIO, NarrativeContext.class.getName()),
              outputDir);
      assertThat(run.getTestsFailedCount()).isZero();
    } finally {
      setOrClear(previous);
    }
    return Files.readString(outputDir.resolve("traces/DeclinedPaymentFixture/" + SCENARIO + ".md"));
  }

  private static void setOrClear(String level) {
    if (level == null) {
      System.clearProperty("narrativetrace.level");
    } else {
      System.setProperty("narrativetrace.level", level);
    }
  }
}
```

## A saída real

**Em `DETAIL`**, a execução recusada, com cada valor capturado:

```text
---
type: trace
run: big tray chirps
scenario: The stock is released when the card is declined
entry_point: OrderService.placeOrder
duration_ms: 1.424
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 2
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: DeclinedPaymentFixture.the_stock_is_released_when_the_card_is_declined, passed, 1.763 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: line 31 of this file (the first call that threw).
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The stock is released when the card is declined
**Duration:** 1.424ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) — 1.424ms #1
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.18ms #1.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) #1.2

    > ❌ `PaymentDeclinedException`: card declined by the issuer — 0.192ms
  - **InventoryService.release**(sku: `"SKU-KB"`, quantity: `2`) — 0.179ms #1.3
  - ❌ `PaymentDeclinedException`: card declined by the issuer
```

**Em `ERRORS`**, a mesma execução do mesmo código:

```text
---
type: trace
run: fast cleft grows
scenario: The stock is released when the card is declined
entry_point: OrderService.placeOrder
duration_ms: 2.282
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 4
error_count: 2
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: DeclinedPaymentFixture.the_stock_is_released_when_the_card_is_declined, passed, 1.697 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: line 31 of this file (the first call that threw).
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — OrderService.placeOrder

**Scenario:** The stock is released when the card is declined
**Duration:** 2.282ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: …, sku: …, quantity: …, cardNumber: `[REDACTED]`) — 2.282ms #1
  - **InventoryService.reserve**(sku: …, quantity: …) — 0.214ms #1.1
  - **PaymentService.charge**(customerId: …, amountCents: …, card: …) #1.2

    > ❌ `PaymentDeclinedException`: card declined by the issuer — 0.302ms
  - **InventoryService.release**(sku: …, quantity: …) — 0.44ms #1.3
  - ❌ `PaymentDeclinedException`: card declined by the issuer
```

**E em `ERRORS`, um checkout que deu certo**: artefato nenhum. Não há nada para
embutir, que é todo o ponto — o cenário acima afirma as duas metades dessa
alegação, a ausência do arquivo em `ERRORS` e a presença dele em `DETAIL`, para
que o vazio seja obra do nível e não de um caminho que nunca esteve certo.

## Como ler isso

- **O que `ERRORS` mantém.** A execução que falhou, inteira. `#1` lançou, e a
  subárvore de um nó com erro é mantida intacta — então `reserve` (`#1.1`),
  `charge` (`#1.2`) e `release` (`#1.3`) continuam lá, inclusive as duas
  chamadas que retornaram normalmente. Isso é deliberado: as chamadas em volta
  de uma falha são o que você precisa para diagnosticá-la, e descartar o irmão
  bem-sucedido te deixaria com uma exceção e nenhuma história. Uma chamada que
  retornou *e* não leva a falha nenhuma abaixo dela é descartada, e uma raiz sem
  falha nenhuma em toda a subárvore é descartada por completo — e é por isso que
  o checkout bem-sucedido não escreveu nada.
- **O que `ERRORS` descarta.** Todo valor de parâmetro: `customerId: …`,
  `quantity: …`. O `…` é supressão na *captura* — o valor nunca foi renderizado,
  então ele não pode ser recuperado depois a partir desses eventos, em nenhum
  nível posterior. O que sobrevive é a forma, os resultados e a exceção, e
  `cardNumber: [REDACTED]` continua ocultado porque ocultação é uma decisão
  diferente de supressão.
- **A poda acontece depois da captura, não em vez dela.** Cada chamada foi
  interceptada, cada evento foi publicado; depois o `TraceTreeBuilder` montou a
  árvore e removeu o que o nível não mantém. Essa é a forma honesta do custo:
  `ERRORS` é mais barato que `DETAIL` porque os valores não são renderizados — a
  parte cara — não porque as chamadas deixem de ser interceptadas.
- **Só `OFF` pula a interceptação.** Em `OFF` o portão `isActive()` do proxy
  retorna antes de qualquer array de argumentos, evento ou publicação, e nada
  mais adiante consegue trazer a chamada de volta. `ERRORS`, `SUMMARY` e
  `NARRATIVE` continuam interceptando cada chamada e publicando uma entrada e
  uma saída para ela. Se a pergunta é "quanto o tracing custa neste laço
  quente", a resposta é outro nível para aquele escopo, ou `OFF`.
- **Os números.** As cifras medidas — o custo do caminho inativo, a alocação
  por chamada, o custo de uma chamada traçada ativa — estão na
  [seção de desempenho do README](../../../LEIAME.md#desempenho), ao lado das
  baselines de JMH commitadas de onde elas vêm. Elas não são repetidas aqui,
  porque uma cópia só, que se move junto com as medições, vale mais que duas que
  se contradizem.

## O que não é capturado

- **As cifras publicadas são medições, não uma promessa.** Elas foram tiradas
  com JMH no harness e no hardware que o README nomeia, uma operação de
  benchmark por chamada. Os seus números dependem do seu JDK, do seu hardware,
  do formato dos seus parâmetros (um grafo de objetos profundo renderiza por
  mais tempo que um `int`) e de quanto do seu código é traçado. Meça o nível que
  você pretende rodar.
- **Um nível não é um controle de privacidade.** O `ERRORS` acaba escrevendo
  menos valores porque os suprime, mas supressão e ocultação são mecanismos
  diferentes com garantias diferentes:
  [Mantenha os segredos fora dos logs](mantenha-os-segredos-fora-dos-logs.md) é
  o que se mantém em todos os níveis.
- **Nada aqui muda quanto o seu logger escreve.** Cada evento capturado
  continua sendo oferecido ao caminho síncrono, então o nível do próprio logger
  `narrativetrace` é o que decide o volume de log. Dois diais.
- **Um nível não pode ser elevado retroativamente.** Valores não capturados se
  foram; reproduzir a execução em `DETAIL` é o único caminho de volta, que é o
  que a receita de depuração faz.
- **Níveis por escopo não aparecem aqui.** Esta página define um nível para uma
  execução inteira. Estreitar o escopo traçado em vez disso — quais classes são
  envolvidas, afinal — é a outra metade da pergunta do custo, e pertence a
  [Escolhendo uma integração](../escolhendo-uma-integracao.md).

**A skill para isso:** `narrativetrace-doctor` — ela relata o nível em que um
projeto está realmente rodando, que é a primeira coisa a verificar quando um
trace sai mais magro do que o esperado.
