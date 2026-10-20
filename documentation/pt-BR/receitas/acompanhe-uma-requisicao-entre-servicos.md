<!-- source: documentation/recipes/follow-one-request-across-services.md blob e9981b3c652f | translated: 2026-10-17 | reviewed: - -->
# Acompanhe uma requisição entre serviços (tracing distribuído)

[English](../../recipes/follow-one-request-across-services.md) | [Español](../../es/recetas/sigue-una-peticion-entre-servicios.md) | **Português** | [简体中文](../../zh-CN/实用方案/跨服务追踪一次请求.md)

## O problema

> O checkout chama o serviço de pagamentos por HTTP. Quando um pedido dá
> errado eu quero uma única requisição nos dois serviços — não dois logs que
> preciso alinhar na mão.

Dois serviços, dois arquivos de log, dois conjuntos de ids. O que faz deles
uma só história é um único valor que viaja com a requisição: o header
[`traceparent`](https://www.w3.org/TR/trace-context/#traceparent-header) do
W3C. Quem chama envia o trace em que está e o span de onde está chamando; quem
recebe adota os dois em vez de começar um trace próprio. Abaixo está uma
execução real disso, com as linhas dos dois serviços em um mesmo arquivo.

## Configuração mínima

Além da [instalação](../guia-de-instalacao.md), quem chama não precisa de nada
novo: `outboundTraceparent()` já está no contexto que ele tem. Quem recebe
precisa da metade de entrada — e qual código é essa metade depende de como ele
foi construído:

| Quem recebe é | A metade de entrada é |
|---|---|
| Uma aplicação servlet (Spring MVC, Jakarta EE) | O `NarrativeTraceFilter` do `narrativetrace-servlet`, que adota o header por você |
| Uma aplicação Micronaut | O filtro do `narrativetrace-micronaut-http`, do mesmo jeito |
| Qualquer outra coisa — um `HttpServer` simples, um framework de handlers | Três linhas no que roda primeiro em cada requisição: reset, `adoptTraceparent(Traceparent.parse(header))`, servir |

O serviço de pagamentos desta página é a terceira linha: o projeto do checkout
roda sem framework, então o handler lê o header ele mesmo. Esse é também o
exemplo honesto do que os filtros fazem, porque são as mesmas três linhas.

As linhas de log vêm do módulo SLF4J e de uma configuração do Logback, como em
[Adicione logs a este serviço](adicione-logs-a-este-servico.md). Esta página
usa uma com o id do trace em cru e a thread no pattern, porque o id do trace é
exatamente o que está sendo seguido:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- The configuration the two correlation recipes run under: src/main/resources/logback.xml
         with the thread name and the raw trace id added to the pattern, because those two pages
         are about which trace a line belongs to and which thread wrote it. A Gradle task selects
         this file with -Dlogback.configurationFile=logback-correlation.xml and names the FILE
         appender's target with narrativetrace.recipes.log, so one scenario's lines land in one
         file a page can embed. Deliberately no %d{HH:mm:ss.SSS}: a clock would differ on every
         run and the embedded block is a real run's bytes. -->
    <property name="LOG_FILE"
              value="${narrativetrace.recipes.log:-build/narrativetrace/recipes/suite.log}" />

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%-5level [%thread] [%logger] [%X{traceId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>${LOG_FILE}</file>
        <append>false</append>
        <encoder>
            <pattern>%-5level [%thread] [%logger] [%X{traceId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="narrativetrace" level="TRACE" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

## O cenário

**O lado de quem chama: um header.** O `PaymentService` do checkout agora é um
cliente HTTP. Ele pede ao contexto o contexto de trace que deve enviar e põe um
header — todo o resto é uma requisição comum:

```java
// src/main/java/com/example/checkout/HttpPaymentService.java
package com.example.checkout;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * The checkout's payment collaborator when the payment service is a different process.
 *
 * <p>INTENT: The outbound half of cross-process correlation, in the shape any HTTP client copies:
 * ask the context for {@link NarrativeContext#outboundTraceparent()} and, when it is non-null, set
 * one header. The callee then continues this trace instead of starting its own.
 *
 * <p><b>@llmNote</b> The request body is a comma-separated line rather than JSON on purpose: this
 * class exists to show the header, and a parser would be the only thing a reader had to skip. A
 * real client sends whatever the payment service's API says.
 *
 * <p><b>@edgeCase</b> A null {@code outboundTraceparent()} — no span open, nothing adopted — sends
 * no header at all, which is the honest outcome: a header without a real parent id would point the
 * callee at a span that never existed.
 */
public final class HttpPaymentService implements PaymentService {

  private static final int DECLINED_STATUS = 402;

  private final NarrativeContext context;
  private final URI endpoint;
  private final HttpClient client = HttpClient.newHttpClient();

  public HttpPaymentService(NarrativeContext context, URI endpoint) {
    if (context == null || endpoint == null) {
      throw new IllegalArgumentException("context and endpoint must not be null");
    }
    this.context = context;
    this.endpoint = endpoint;
  }

  @Override
  public String charge(String customerId, int amountCents, String card) {
    var body = customerId + "," + amountCents + "," + card;
    var request =
        withTraceparent(
            HttpRequest.newBuilder()
                .uri(endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    var response = send(request.build());
    if (response.statusCode() == DECLINED_STATUS) {
      throw new PaymentDeclinedException(response.body());
    }
    return response.body();
  }

  /** Sets the W3C header when this call has a span to name as the callee's parent. */
  private HttpRequest.Builder withTraceparent(HttpRequest.Builder builder) {
    var traceparent = context.outboundTraceparent();
    if (traceparent == null) {
      return builder;
    }
    return builder.header(Traceparent.HEADER_NAME, traceparent.format());
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("the payment service could not be reached", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while charging", e);
    }
  }
}
```

**O lado de quem recebe: o contexto dele.** O serviço de pagamentos é um
segundo serviço, o que significa um segundo contexto narrativo — criado com o
nome de logger dele, para que um mesmo arquivo de log diferencie os dois
(`src/test/java/com/example/checkout/PaymentServiceProcess.java`):

```java
  private final NarrativeContext context =
      new ThreadLocalNarrativeContext(
          new NarrativeTraceConfig(), PipelineBootstrap.createDefault(LOGGER_NAME));
  private final PaymentService payments =
      NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
```

**O lado de quem recebe: adotar, servir, capturar, resetar.** Esta é toda a
metade de entrada, no mesmo arquivo — e a mesma sequência que o
`NarrativeTraceFilter` executa em uma aplicação servlet:

```java
  private void handle(HttpExchange exchange) throws IOException {
    var header = exchange.getRequestHeaders().getFirst(Traceparent.HEADER_NAME);
    inboundHeader.set(header);
    context.reset();
    context.adoptTraceparent(Traceparent.parse(header));
    try {
      var fields = body(exchange).split(",", 3);
      respond(exchange, OK, payments.charge(fields[0], Integer.parseInt(fields[1]), fields[2]));
    } catch (PaymentDeclinedException declined) {
      respond(exchange, PAYMENT_REQUIRED, declined.getMessage());
    } finally {
      lastTrace.set(context.captureTrace());
      context.reset();
    }
  }

```

**A execução.** O checkout, envolvido como sempre, com o cliente HTTP onde
antes estava o serviço de pagamentos local. O servidor é um `HttpServer` em
loopback na mesma JVM: a requisição atravessa de verdade um socket e o header
chega de verdade como header, que é do que esta página trata — um segundo
processo acrescentaria um segundo classpath e não provaria mais nada:

```java
// src/test/java/com/example/checkout/AcrossServicesRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/follow-one-request-across-services.md: the checkout charges a card
 * through a second service over HTTP, the W3C {@code traceparent} goes with the request, and the
 * callee's own trace carries the caller's trace id and names the caller's span as its parent (rule
 * 8, docs as tests).
 *
 * <p><b>@llmNote</b> The callee's narrative is rendered here, not by the JUnit extension: the
 * extension writes the trace of the context it manages, and the point of this page is that the
 * second service has a context — and a narrative — of its own. The Gradle task {@code
 * recipeAcrossServicesLog} runs this class alone under logback-correlation.xml, so the page's log
 * block holds both services' lines with their trace ids.
 */
@ExtendWith(NarrativeTraceExtension.class)
class AcrossServicesRecipeTest {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/follow-one-request-across-services");

  @Test
  void a_customer_checks_out_against_the_payment_service(NarrativeContext context)
      throws IOException {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    try (var paymentService = new PaymentServiceProcess()) {
      var checkout = checkoutCalling(paymentService, context, stock, repository);

      var orderId = checkout.placeOrder("C-1234", "SKU-KB", 2, "4242424242424242");

      assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
      assertThat(repository.saved()).hasSize(1);
      writeCalleeNarrative(paymentService);
      assertTheTraceCrossedTheWire(paymentService);
    }
  }

  /** The checkout, with the HTTP payment client where the local one used to be. */
  private static OrderService checkoutCalling(
      PaymentServiceProcess paymentService,
      NarrativeContext context,
      InMemoryInventory stock,
      InMemoryOrderRepository repository) {
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(
            new HttpPaymentService(context, paymentService.endpoint()),
            PaymentService.class,
            context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    return NarrativeTraceProxy.trace(
        new DefaultOrderService(inventory, payments, orders), OrderService.class, context);
  }

  /**
   * One trace, two services: the header named the caller's own span, and the callee's root adopted
   * it as its parent instead of starting a trace of its own.
   */
  private static void assertTheTraceCrossedTheWire(PaymentServiceProcess paymentService) {
    var sent = Traceparent.parse(paymentService.inboundTraceparent());
    assertThat(sent).isNotNull();
    assertThat(sent.traceId()).isEqualTo(Traceparent.parse(DEMO_TRACEPARENT).traceId());

    var calleeRoot = paymentService.lastTrace().roots().get(0);
    assertThat(calleeRoot.signature().methodName()).isEqualTo("charge");
    assertThat(calleeRoot.spanContext().traceId()).isEqualTo(sent.traceId());
    assertThat(calleeRoot.spanContext().parentSpanId()).isEqualTo(sent.parentSpanId());
  }

  /** The callee's own narrative, into the directory the page embeds from. */
  private static void writeCalleeNarrative(PaymentServiceProcess paymentService)
      throws IOException {
    Files.createDirectories(PAGE_OUTPUT);
    Files.writeString(
        PAGE_OUTPUT.resolve("payment-service.md"),
        new MarkdownRenderer().render(paymentService.lastTrace()));
  }
}
```

## A saída real

**As linhas dos dois serviços, um arquivo** — as de quem chama sob
`narrativetrace`, as do serviço de pagamentos sob `narrativetrace.payments`:

```text
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → OrderService.placeOrder(customerId: "C-1234", sku: "SKU-KB", quantity: 2, cardNumber: [REDACTED])
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → InventoryService.reserve(sku: "SKU-KB", quantity: 2)
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← completed
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → PaymentService.charge(customerId: "C-1234", amountCents: 7998, card: [REDACTED])
TRACE [payment-service-1] [narrativetrace.payments] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → PaymentService.charge(customerId: "C-1234", amountCents: 7998, card: [REDACTED])
TRACE [payment-service-1] [narrativetrace.payments] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: "PAY-C-1234-7998"
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: "PAY-C-1234-7998"
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - → OrderRepository.save(order: Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998))
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← completed
TRACE [Test worker] [narrativetrace] [c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7] - ← returned: "ORD-C-1234-SKU-KB-2"
```

**O trace de quem chama,** o artefato estrutural sem valores daquela execução,
em
`build/narrativetrace/structural/AcrossServicesRecipeTest/a_customer_checks_out_against_the_payment_service.nt`
— os ids de span abaixo são os que esta página usa para ler:

```text
scenario: A customer checks out against the payment service

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) → value
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) → value
  #1.3 - OrderRepository.save(order)
```

**A narrativa do próprio serviço de pagamentos,** capturada pelo contexto dele
no fim da requisição e renderizada ao lado da de quem chama:

```text
- **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 9.621ms #1
```

## Como ler isso

- **Um id de trace, nas linhas dos dois serviços.**
  `c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7` é o terceiro colchete de cada linha acima,
  tanto as de quem chama quanto as de quem recebe. O de quem chama é o trace em
  que esta execução está; o de quem recebe é esse mesmo id porque ele o
  *adotou*. Nada os correlaciona depois do fato — eles nunca estiveram
  separados.
- **Os dois serviços são distinguidos pelo logger, não pelo id.**
  `[narrativetrace]` é o checkout, `[narrativetrace.payments]` é o serviço de
  pagamentos, porque o contexto dele foi construído com esse nome de logger. A
  coluna da thread diz a mesma coisa pelo outro lado: `[Test worker]` é a
  thread de quem chama, `[payment-service-1]` é a do servidor.
- **O `#1.2` de quem chama e o `#1` de quem recebe são o mesmo trabalho, visto
  das duas pontas.** Na narrativa de quem chama,
  `#1.2 PaymentService.charge` é o span *cliente*: começa quando o checkout
  chama o cliente e termina quando a resposta volta, então inclui a ida e a
  volta inteiras. Na narrativa do serviço de pagamentos,
  `#1 PaymentService.charge` é o span *servidor*: o trabalho que ele realmente
  fez. Ids de span são por árvore de traces e cada serviço numera a sua a
  partir de `#1` — o que junta é o id do trace, não o número.
- **O id do span pai na raiz de quem recebe é o span de quem chama.** O header
  que a requisição levou era
  `00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-<o id de span do #1.2 de quem chama>-01`,
  e o span raiz de quem recebe registra esse id como pai. Isso não aparece em
  uma linha de log — um id de span é gerado por span, então muda em cada
  execução e nada estável byte a byte poderia ser impresso aqui — por isso é
  afirmado por asserção, em `assertTheTraceCrossedTheWire` acima: a raiz de quem
  recebe carrega o id de trace enviado *e* o id do span pai enviado. A
  paternidade através do cabo é o que esta página afirma, e aquela asserção é a
  prova.
- **Como juntá-los em uma busca de logs.** O id do trace está no MDC sob
  `traceId` em cada linha que o NarrativeTrace escreve, em qualquer um dos dois
  serviços. Ponha `%X{traceId}` no seu pattern (como acima) ou um encoder JSON
  na frente, e uma requisição nos dois serviços é
  `grep c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7` — ou, no agregador que você roda,
  `traceId:"c4e1…"`. O *nome* do trace pelo qual
  [Adicione logs a este serviço](adicione-logs-a-este-servico.md) faz grep é
  derivado do mesmo id, então serve de chave de busca também, e é mais legível
  para uma pessoa.
- **As duas narrativas continuam duas.** Os spans do serviço de pagamentos
  estão no trace do serviço de pagamentos, não no de quem chama:
  `captureTrace()` em cada lado devolve o trabalho daquele lado. Um id de trace,
  duas árvores — que é exatamente o que você tem em produção, onde o outro
  serviço é um processo diferente em outra máquina.

## O que não é capturado

- **Um salto por um serviço que não repassa o header quebra a corrente.** A
  correlação é cooperativa: a corrente se mantém enquanto cada serviço *passa o
  `traceparent` adiante*. Um proxy, um consumidor de fila ou um serviço de
  terceiros que descarte o header deixa o próximo serviço com NarrativeTrace sem
  nada para adotar, e ele começa um trace novo — corretamente, já que não tem
  nenhum pai honesto para nomear. Um serviço sem NarrativeTrace nenhum está de
  bom tamanho *se* repassar o header que recebeu.
- **Nenhum header é enviado quando não há o que nomear.** Sem span aberto e sem
  nada adotado, `outboundTraceparent()` devolve `null` e o cliente não envia
  nada, em vez de um `traceparent` apontando para um span que nunca existiu. Uma
  chamada de saída sem trace não pode ser juntada, e isso é de propósito.
- **Só `traceparent`.** O cliente acima não envia `tracestate` nem baggage:
  nada neste caminho lê ou escreve estado de fornecedor, e nenhum valor da
  aplicação atravessa o cabo por causa do tracing.
- **Nada volta.** A resposta não carrega header de trace, então a narrativa de
  quem chama diz o que foi enviado e o que voltou, nunca o que quem recebe fez
  por dentro. A narrativa do próprio receptor é o registro disso, e o id do
  trace é o que a encontra.
- **Dois serviços, uma JVM, um socket.** O que esta execução não exercita é um
  segundo processo, um segundo classpath e uma rede que pode falhar. O mecanismo
  do header não muda por nenhum deles; os modos de falha da chamada em si são do
  seu cliente HTTP.

**A skill para isso:** `narrativetrace-verify` — uma mudança que cruza
serviços é exatamente o caso para o qual ela existe: escreva a intenção
primeiro, rode o menor caminho real e leia o trace contra ela antes de
considerar qualquer coisa pronta.
