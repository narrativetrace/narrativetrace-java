<!-- source: documentation/recipes/send-traces-to-the-observability-stack-you-already-run.md blob ad8f578e4dd6 | translated: 2026-10-18 | reviewed: - -->
# Envie os traces para o stack de observabilidade que você já tem

[English](../../recipes/send-traces-to-the-observability-stack-you-already-run.md) | [Español](../../es/recetas/envia-las-trazas-al-stack-de-observabilidad-que-ya-tienes.md) | **Português** | [简体中文](../../zh-CN/实用方案/把追踪发到你已有的可观测性栈.md)

## O problema

> A gente já tem tracing e dashboards. Não vou adotar um segundo lugar para
> olhar — isso consegue ir para o stack que já rodamos?

Sim: uma execução, dois consumidores. As linhas narrativas continuam indo para
o seu logger, e os mesmos eventos se tornam spans de OpenTelemetry no caminho
de saída. Abaixo há um checkout com as duas saídas lado a lado.

## Configuração mínima

`narrativetrace-opentelemetry` mais a API do OTel, que o módulo deixa por sua
conta (`compileOnly`, então a versão do seu SDK é que vale):

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.narrativetrace:narrativetrace-opentelemetry:0.5.4")
    implementation("io.opentelemetry:opentelemetry-api:1.62.0")
}
```

Existem duas formas de entrada, e a diferença é *quando* um span existe:

| Modo | Classe | Use quando |
|---|---|---|
| Ao vivo, enquanto os métodos rodam | `OtelTraceEventListener` | Você quer os spans no seu coletor mesmo se o processo morrer no meio da requisição — esta página |
| Em lote, depois do fato | `TraceSpanExporter` | Você quer os spans de uma árvore de traces já terminada, exportados uma vez, com o menor custo de execução: `exporter.export(context.captureTrace().roots())` |

Em um deploy o listener ao vivo normalmente não é construído no seu código:
uma implementação de `TraceEventListener` no classpath é descoberta por
`ServiceLoader` e colocada ao lado da retenção automaticamente — estar presente
*é* a configuração ([Guia de configuração](../guia-de-configuracao.md) §10 e
§11). Um exportador em memória não pode ser descoberto assim, então o cenário
abaixo compõe o pipeline explicitamente, o que também é a imagem mais clara do
que a descoberta teria montado.

## O cenário

O pipeline é a única ideia nova: o listener de narração que você já tem, com o
listener do OTel composto no mesmo caminho durável. O checkout em si é
envolvido exatamente como em todas as outras páginas:

```java
// src/test/java/com/example/checkout/ObservabilityRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ai.narrativetrace.opentelemetry.OtelTraceEventListener;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ai.narrativetrace.slf4j.Slf4jTraceEventListener;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves documentation/recipes/send-traces-to-the-observability-stack-you-already-run.md: ONE run
 * of the checkout writes the log lines it always wrote AND an OpenTelemetry span per traced call,
 * with the parentage and the attributes the page shows — and without the redacted values (rule 8,
 * docs as tests).
 *
 * <p><b>@llmNote</b> No JUnit extension here, deliberately: this page's subject is the pipeline a
 * deployment composes, so the context is built with an explicit one — narration on the durable
 * path, the OTel listener beside it. In production the listener is usually discovered off the
 * classpath instead (configuration-guide §11), which needs no code at all; an in-memory exporter
 * cannot be discovered, so the page shows the explicit composition the same guide's §10 documents.
 *
 * <p>The exported spans are written out by {@link #writeExportedSpans}, naming and sorting
 * everything it prints, so one run's dump is byte-identical to the next — a raw span dump carries
 * timestamps and OTel's own generated ids and could never be embedded.
 */
class ObservabilityRecipeTest {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  private static final Path PAGE_OUTPUT =
      Path.of(
          "build/narrativetrace/recipes/send-traces-to-the-observability-stack-you-already-run");

  private InMemorySpanExporter spans;
  private SdkTracerProvider tracerProvider;
  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void wireTheStack() {
    spans = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build();

    // The pipeline this page is about: the narration you already have, and the OTel listener
    // beside it on the same durable path. Retention — what captureTrace() reads — is untouched.
    var narration = new Slf4jTraceEventListener();
    var export = new OtelTraceEventListener(tracerProvider.get("narrativetrace"));
    context =
        new ThreadLocalNarrativeContext(
            new NarrativeTraceConfig(), new DualPathPipeline(narration.andThen(export)));
  }

  @AfterEach
  void tearDown() {
    context.reset();
    tracerProvider.close();
  }

  @Test
  void one_run_narrates_and_exports() throws IOException {
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
    assertOneSpanPerTracedCall();
    assertNoRedactedValueWasExported();
    writeExportedSpans();
  }

  /** Four traced calls, four spans, ended innermost first — and the tree is reconstructed. */
  private void assertOneSpanPerTracedCall() {
    var exported = spans.getFinishedSpanItems();
    assertThat(exported.stream().map(SpanData::getName))
        .containsExactly(
            "InventoryService.reserve",
            "PaymentService.charge",
            "OrderRepository.save",
            "OrderService.placeOrder");
    var byId =
        exported.stream().collect(Collectors.toMap(SpanData::getSpanId, Function.identity()));
    var root = named(exported, "OrderService.placeOrder");
    assertThat(byId.get(named(exported, "PaymentService.charge").getParentSpanId()))
        .isEqualTo(root);
    assertThat(byId).doesNotContainKey(root.getParentSpanId());
    assertRootParentsOnTheAdoptedHeader(root);
  }

  /** The adopted header named this trace's remote parent; it must reach the exported root too. */
  private void assertRootParentsOnTheAdoptedHeader(SpanData root) {
    var header = Traceparent.parse(DEMO_TRACEPARENT);
    assertThat(root.getParentSpanContext().isRemote())
        .as("the root adopted a traceparent, so its parent is the header's span, not local")
        .isTrue();
    assertThat(root.getTraceId())
        .as("adoption connects this service's OTel trace to the caller's trace, not a fresh one")
        .isEqualTo(header.traceId().toString());
  }

  /** What the deny-list caught never reaches the exporter either — there is no attribute for it. */
  private void assertNoRedactedValueWasExported() {
    var attributes =
        spans.getFinishedSpanItems().stream()
            .flatMap(span -> span.getAttributes().asMap().entrySet().stream())
            .collect(
                Collectors.toMap(
                    e -> e.getKey().getKey(), e -> String.valueOf(e.getValue()), (a, b) -> a));
    assertThat(attributes).doesNotContainKeys("narrative.param.cardNumber", "narrative.param.card");
    assertThat(attributes.values()).noneMatch(value -> value.contains("4242424242424242"));
  }

  private static SpanData named(List<SpanData> exported, String name) {
    return exported.stream().filter(span -> span.getName().equals(name)).findFirst().orElseThrow();
  }

  /** The dump the page embeds: span name, parent, and every attribute, sorted by key. */
  private void writeExportedSpans() throws IOException {
    var exported = spans.getFinishedSpanItems();
    var names =
        exported.stream()
            .collect(Collectors.toMap(SpanData::getSpanId, SpanData::getName, (a, b) -> a));
    var text = new StringBuilder();
    for (var span : exported) {
      text.append("span: ").append(span.getName()).append('\n');
      text.append("  parent: ").append(parentLabel(span, names)).append('\n');
      appendAttributes(span, text);
    }
    Files.createDirectories(PAGE_OUTPUT);
    Files.writeString(PAGE_OUTPUT.resolve("exported-spans.txt"), text.toString());
  }

  /**
   * A root under an adopted {@code traceparent} has a parent OTel never created locally — never
   * mislabel that "no parent" the way an absent local name would otherwise read.
   */
  private static String parentLabel(SpanData span, Map<String, String> names) {
    if (!span.getParentSpanContext().isValid()) {
      return "(none — this is the root)";
    }
    var local = names.get(span.getParentSpanId());
    return local != null ? local : "(remote — adopted from the inbound traceparent)";
  }

  private static void appendAttributes(SpanData span, StringBuilder text) {
    span.getAttributes().asMap().entrySet().stream()
        .collect(
            Collectors.toMap(
                e -> e.getKey().getKey(), Map.Entry::getValue, (a, b) -> a, java.util.TreeMap::new))
        .forEach(
            (key, value) ->
                text.append("    ").append(key).append(": ").append(value).append('\n'));
  }
}
```

## A saída real

**As linhas de log**, inalteradas por tudo isso — a mesma narração que
[Adicione logs a este serviço](adicione-logs-a-este-servico.md) produz:

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

**Os spans que saíram da mesma execução**, como o exportador em memória os
recebeu — nome, pai e cada atributo, ordenados:

```text
span: InventoryService.reserve
  parent: OrderService.placeOrder
    narrative.class: InventoryService
    narrative.method: reserve
    narrative.param.quantity: 2
    narrative.param.sku: SKU-KB
    narrative.trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
    narrative.trace_name: solid wand romps
    nt.entryType: entry
    nt.schemaVersion: 1.0
span: PaymentService.charge
  parent: OrderService.placeOrder
    narrative.class: PaymentService
    narrative.method: charge
    narrative.outcome: "PAY-C-1234-7998"
    narrative.param.amountCents: 7998
    narrative.param.customerId: C-1234
    narrative.trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
    narrative.trace_name: solid wand romps
    nt.entryType: entry
    nt.schemaVersion: 1.0
span: OrderRepository.save
  parent: OrderService.placeOrder
    narrative.class: OrderRepository
    narrative.method: save
    narrative.param.order.amountCents: 7998
    narrative.param.order.customerId: C-1234
    narrative.param.order.orderId: ORD-C-1234-SKU-KB-2
    narrative.param.order.quantity: 2
    narrative.param.order.sku: SKU-KB
    narrative.trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
    narrative.trace_name: solid wand romps
    nt.entryType: entry
    nt.schemaVersion: 1.0
span: OrderService.placeOrder
  parent: (remote — adopted from the inbound traceparent)
    narrative.class: OrderService
    narrative.method: placeOrder
    narrative.outcome: "ORD-C-1234-SKU-KB-2"
    narrative.param.customerId: C-1234
    narrative.param.quantity: 2
    narrative.param.sku: SKU-KB
    narrative.trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
    narrative.trace_name: solid wand romps
    nt.entryType: entry
    nt.schemaVersion: 1.0
```

## Como ler isso

- **Quatro chamadas traçadas, quatro spans, uma árvore.** Os spans chegam na
  ordem em que *terminaram* — o mais interno primeiro, a raiz por último, que é
  como qualquer pipeline do OTel vê uma árvore terminada. A paternidade não é
  inferida dessa ordem nem de um thread-local: o listener lê o `parentSpanId`
  explícito de cada evento, e é por isso que `InventoryService.reserve`,
  `PaymentService.charge` e `OrderRepository.save` todos nomeiam
  `OrderService.placeOrder` como pai mesmo tendo terminado antes dele. O
  próprio pai de `OrderService.placeOrder` aparece como `(remote — adopted
  from the inbound traceparent)`, não `(none — this is the root)`: esta
  execução adotou um cabeçalho antes de fazer o pedido, então o pai dele é o
  span de quem chamou, nomeado pelo cabeçalho — um span que este processo
  nunca rodou e que, portanto, nunca exportou.
- **O que cada span carrega.** `narrative.class` e `narrative.method` são a
  chamada; `narrative.param.<nome>` é um atributo por parâmetro, tipado
  (`narrative.param.quantity: 2` é um long, não a string `"2"`);
  `narrative.outcome` é o valor retornado, e está ausente nas duas chamadas
  `void` porque não havia nada a retornar. `narrative.trace_id` e
  `narrative.trace_name` são a identidade própria do NarrativeTrace para o
  trace, levada como atributos para que um span no seu backend possa ser casado
  com a linha de log e o arquivo de trace que descrevem a mesma chamada —
  [Acompanhe uma requisição entre serviços](acompanhe-uma-requisicao-entre-servicos.md)
  é o mesmo id fazendo o mesmo trabalho através de uma fronteira de processo.
  `nt.entryType` e `nt.schemaVersion` marcam o schema que esses atributos
  seguem.
- **Um parâmetro que é record chega achatado.** `OrderRepository.save(order)` é
  um parâmetro, e foi exportado como `narrative.param.order.orderId`,
  `narrative.param.order.customerId`, `narrative.param.order.sku`,
  `narrative.param.order.quantity`, `narrative.param.order.amountCents` —
  componente por componente, porque foi assim que o valor foi renderizado: lendo
  o estado dele, nunca chamando o `toString()`.
- **A ocultação alcança este caminho também.** Não existe atributo
  `narrative.param.cardNumber` nem `narrative.param.card` em nenhum dos spans
  acima — não um atributo contendo `[REDACTED]`, nenhum atributo. Um valor
  negado é descartado na captura, antes de o evento existir, então nenhum
  consumidor de nenhum dos dois caminhos pode recebê-lo; o cenário afirma isso
  tanto quanto afirma a árvore de spans
  ([Mantenha os segredos fora dos logs](mantenha-os-segredos-fora-dos-logs.md)).
- **Qual ajuste moveu qual evento para qual consumidor.** Três decisões, nesta
  ordem:
  1. `TracingLevel` decide se um evento é criado. Em `SUMMARY` esses mesmos
     quatro spans seriam exportados *sem* os atributos `narrative.param.*`,
     porque valores de parâmetro não são renderizados naquele nível; em `OFF`
     não há evento e, portanto, não há span
     ([Trace em produção sem o custo](trace-em-producao-sem-o-custo.md)).
  2. A forma do pipeline decide quem vê o evento. Aqui um listener durável —
     narração, depois exportação — roda inline, e o slot de melhor esforço
     mantém a retenção, que é o que `captureTrace()` e os arquivos de trace
     leem. Nada foi tirado do logger para alimentar o exportador.
  3. O nível do seu logger decide o que é *impresso*. Subir o logger
     `narrativetrace` para `INFO` esvazia o bloco de log acima e não muda nada
     nos spans.
- **Por que isso não é um segundo lugar para olhar.** Os spans são seus, no seu
  coletor, sob o seu nome de serviço, ao lado dos spans que o seu framework já
  emite. O que o NarrativeTrace acrescenta é um span por *chamada de método
  traçada* com os parâmetros e o resultado nele — um nível abaixo do span HTTP
  que você já tem.

## O que não é capturado

- **O que o seu stack faz com os spans é do seu stack.** Amostragem, retenção,
  indexação e se um atributo é pesquisável são decisões do coletor e do backend.
  Esta página mostra o que sai do processo; nenhuma afirmação é feita sobre a UI
  de qualquer fornecedor.
- **Os ids de span nunca são compartilhados; o de trace é, assim que um
  cabeçalho nomeia um.** O OpenTelemetry sempre escolhe os próprios ids de
  span, e a identidade do NarrativeTrace vai junto como atributos
  `narrative.trace_id` / `narrative.trace_name` para casar um span com uma
  linha de log — a posição de um span na cascata do seu backend é um id que o
  OTel escolheu, nunca um do próprio NarrativeTrace. O id de trace é
  diferente quando um cabeçalho foi adotado, como acima: a raiz vira filha do
  contexto remoto que o cabeçalho nomeou, então o id de trace do próprio
  OpenTelemetry para toda a subárvore exportada passa a ser também o daquele
  cabeçalho — o mesmo que `narrative.trace_id` já carregava, e é isso que
  conecta os spans deste serviço ao trace de quem chamou no seu backend, não
  apenas os coloca ao lado.
- **Ao vivo e em lote não são os mesmos spans.** O listener desta página cria um
  span enquanto um método roda, então um órfão (uma entrada sem saída — um
  processo morto no meio da chamada) acaba encerrado com status de erro pelo TTL
  do próprio listener. O exportador em lote só vê árvores terminadas e
  acrescenta `narrative.duration_ms`, que o caminho ao vivo não tem.
- **O custo é um listener na thread de quem chama.** Sob a topologia padrão um
  listener composto roda inline, então a criação do span está no caminho da
  chamada traçada. Essa é a troca pela durabilidade; uma topologia de fila ou de
  ring buffer tira isso do caminho, e o contrato do listener é escrito para
  continuar funcionando quando a topologia muda.
- **Só o que é traçado é exportado.** Não existe span para uma chamada que
  nenhuma integração interceptou — o caminho de exportação herda exatamente a
  fronteira do caminho de captura.

**A skill para isso:** `narrativetrace-doctor` — uma verificação de ligação por
integração de framework, e ela relata se algum destino de fato recebe o que é
traçado.
