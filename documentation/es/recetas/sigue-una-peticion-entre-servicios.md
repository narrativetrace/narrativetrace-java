<!-- source: documentation/recipes/follow-one-request-across-services.md blob e9981b3c652f | translated: 2026-10-17 | reviewed: - -->
# Sigue una petición entre servicios (tracing distribuido)

[English](../../recipes/follow-one-request-across-services.md) | **Español** | [Português](../../pt-BR/receitas/acompanhe-uma-requisicao-entre-servicos.md) | [简体中文](../../zh-CN/实用方案/跨服务追踪一次请求.md)

## El problema

> El checkout llama al servicio de pagos por HTTP. Cuando un pedido sale mal
> quiero una sola petición vista en los dos servicios, no dos logs que tengo
> que cuadrar a mano.

Dos servicios, dos archivos de log, dos juegos de identificadores. Lo que los
convierte en una sola historia es un único valor que viaja con la petición: la
cabecera
[`traceparent`](https://www.w3.org/TR/trace-context/#traceparent-header) del
W3C. Quien llama envía la traza en la que está y el span desde el que llama;
quien recibe adopta ambos en lugar de empezar una traza propia. Abajo está una
ejecución real de eso, con las líneas de los dos servicios en un mismo archivo.

## Configuración mínima

Más allá de la [instalación](../guia-de-instalacion.md), quien llama no
necesita nada nuevo: `outboundTraceparent()` ya está en el contexto que tiene.
Quien recibe necesita la mitad entrante — y qué código es eso depende de cómo
esté construido:

| Quien recibe es | La mitad entrante es |
|---|---|
| Una aplicación servlet (Spring MVC, Jakarta EE) | El `NarrativeTraceFilter` de `narrativetrace-servlet`, que adopta la cabecera por ti |
| Una aplicación Micronaut | El filtro de `narrativetrace-micronaut-http`, igual |
| Cualquier otra cosa — un `HttpServer` simple, un framework de handlers | Tres líneas en lo primero que se ejecuta por petición: reset, `adoptTraceparent(Traceparent.parse(header))`, servir |

El servicio de pagos de esta página es la tercera fila: el proyecto del
checkout funciona sin framework, así que el handler lee la cabecera él mismo.
Ese es además el ejemplo honesto de lo que hacen los filtros, porque son las
mismas tres líneas.

Las líneas de log vienen del módulo SLF4J y de una configuración de Logback,
igual que en [Añade registro a este servicio](anade-registro-a-este-servicio.md).
Esta página usa una con el identificador de traza en crudo y el hilo en el
patrón, porque el identificador de traza es justo lo que se está siguiendo:

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

## El escenario

**El lado de quien llama: una cabecera.** El `PaymentService` del checkout es
ahora un cliente HTTP. Le pide al contexto el contexto de traza que debe
enviar y pone una cabecera — todo lo demás es una petición corriente:

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

**El lado de quien recibe: su propio contexto.** El servicio de pagos es un
segundo servicio, lo que significa un segundo contexto narrativo — creado con
su propio nombre de logger, para que un mismo archivo de log distinga a los
dos (`src/test/java/com/example/checkout/PaymentServiceProcess.java`):

```java
  private final NarrativeContext context =
      new ThreadLocalNarrativeContext(
          new NarrativeTraceConfig(), PipelineBootstrap.createDefault(LOGGER_NAME));
  private final PaymentService payments =
      NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
```

**El lado de quien recibe: adoptar, servir, capturar, resetear.** Esta es toda
la mitad entrante, en el mismo archivo — y la misma secuencia que
`NarrativeTraceFilter` ejecuta en una aplicación servlet:

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

**La ejecución.** El checkout, envuelto como siempre, con el cliente HTTP
donde antes estaba el servicio de pagos local. El servidor es un `HttpServer`
en loopback dentro de la misma JVM: la petición cruza de verdad un socket y la
cabecera llega de verdad como cabecera, que es de lo que trata esta página —
un segundo proceso añadiría un segundo classpath y no probaría nada más:

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

## La salida real

**Las líneas de los dos servicios, un archivo** — las de quien llama bajo
`narrativetrace`, las del servicio de pagos bajo `narrativetrace.payments`:

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

**La traza propia de quien llama,** el artefacto estructural sin valores de esa
ejecución, en
`build/narrativetrace/structural/AcrossServicesRecipeTest/a_customer_checks_out_against_the_payment_service.nt`
— los identificadores de span de abajo son los que usa esta página para leer:

```text
scenario: A customer checks out against the payment service

#1 - OrderService.placeOrder(customerId, sku, quantity, cardNumber) → value
  #1.1 - InventoryService.reserve(sku, quantity)
  #1.2 - PaymentService.charge(customerId, amountCents, card) → value
  #1.3 - OrderRepository.save(order)
```

**La narrativa propia del servicio de pagos,** capturada por su propio contexto
al final de la petición y renderizada junto a la de quien llama:

```text
- **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 9.621ms #1
```

## Cómo leerlo

- **Un identificador de traza, en las líneas de los dos servicios.**
  `c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7` es el tercer corchete de cada línea de
  arriba, tanto las de quien llama como las de quien recibe. El de quien llama
  es la traza en la que está esta ejecución; el de quien recibe es ese mismo
  identificador porque lo *adoptó*. Nada los correlaciona a posteriori: nunca
  estuvieron separados.
- **A los dos servicios los distingue el logger, no el identificador.**
  `[narrativetrace]` es el checkout, `[narrativetrace.payments]` es el servicio
  de pagos, porque su contexto se construyó con ese nombre de logger. La
  columna del hilo dice lo mismo desde el otro lado: `[Test worker]` es el hilo
  de quien llama, `[payment-service-1]` es el del servidor.
- **El `#1.2` de quien llama y el `#1` de quien recibe son el mismo trabajo,
  visto desde los dos extremos.** En la narrativa de quien llama,
  `#1.2 PaymentService.charge` es el span *cliente*: empieza cuando el checkout
  llama al cliente y termina cuando vuelve la respuesta, así que incluye todo
  el viaje de ida y vuelta. En la narrativa del servicio de pagos,
  `#1 PaymentService.charge` es el span *servidor*: el trabajo que realmente
  hizo. Los identificadores de span son propios de cada árbol de trazas y cada
  servicio numera el suyo desde `#1` — lo que une es el identificador de traza,
  no el número.
- **El identificador del span padre en la raíz de quien recibe es el span de
  quien llama.** La cabecera que llevó la petición era
  `00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-<el id de span del #1.2 de quien llama>-01`,
  y el span raíz de quien recibe registra ese id como su padre. Eso no se ve en
  una línea de log — un id de span se genera por span, así que cambia en cada
  ejecución y nada estable byte a byte podría imprimirse aquí — por lo que se
  comprueba con una aserción, en `assertTheTraceCrossedTheWire` de arriba: la
  raíz de quien recibe lleva el identificador de traza enviado *y* el id del
  span padre enviado. La paternidad a través del cable es lo que afirma esta
  página, y esa aserción es su prueba.
- **Cómo unirlos en una búsqueda de logs.** El identificador de traza está en
  el MDC bajo `traceId` en cada línea que escribe NarrativeTrace, en cualquiera
  de los dos servicios. Pon `%X{traceId}` en tu patrón (como arriba) o un
  encoder JSON delante, y una petición en los dos servicios es
  `grep c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7` — o, en el agregador que uses,
  `traceId:"c4e1…"`. El *nombre* de traza por el que hace grep
  [Añade registro a este servicio](anade-registro-a-este-servicio.md) se deriva
  del mismo identificador, así que también sirve como clave de búsqueda y se lee
  mejor para una persona.
- **Las dos narrativas siguen siendo dos.** Los spans del servicio de pagos
  están en la traza del servicio de pagos, no en la de quien llama:
  `captureTrace()` en cada lado devuelve el trabajo de ese lado. Un
  identificador de traza, dos árboles — que es exactamente lo que tienes en
  producción, donde el otro servicio es un proceso distinto en otra máquina.

## Qué no queda capturado

- **Un salto por un servicio que no reenvía la cabecera rompe la cadena.** La
  correlación es cooperativa: la cadena se mantiene mientras cada servicio
  *pase `traceparent` adelante*. Un proxy, un consumidor de una cola o un
  servicio de terceros que descarte la cabecera deja al siguiente servicio con
  NarrativeTrace sin nada que adoptar, y este empieza una traza nueva —
  correctamente, porque no tiene ningún padre honesto que nombrar. Un servicio
  sin NarrativeTrace no es problema *si* reenvía la cabecera que recibió.
- **No se envía ninguna cabecera cuando no hay nada que nombrar.** Sin ningún
  span abierto y sin nada adoptado, `outboundTraceparent()` devuelve `null` y el
  cliente no envía nada, en lugar de un `traceparent` que apuntaría a un span
  que nunca existió. Una llamada saliente sin trazar no se puede unir, y es a
  propósito.
- **Solo `traceparent`.** El cliente de arriba no envía `tracestate` ni
  baggage: nada en esta ruta lee ni escribe estado de proveedor, y ningún valor
  de la aplicación cruza el cable por causa del tracing.
- **No vuelve nada.** La respuesta no lleva ninguna cabecera de traza, así que
  la narrativa de quien llama dice qué envió y qué recibió, nunca qué hizo quien
  recibe por dentro. La narrativa propia de quien recibe es el registro de eso,
  y el identificador de traza es lo que la encuentra.
- **Dos servicios, una JVM, un socket.** Lo que esta ejecución no ejercita es
  un segundo proceso, un segundo classpath y una red que puede fallar. El
  mecanismo de la cabecera no cambia por ninguno de ellos; los modos de fallo de
  la llamada en sí son de tu cliente HTTP.

**La habilidad para esto:** `narrativetrace-verify` — un cambio que cruza
servicios es justo el caso para el que existe: deja escrita la intención,
ejecuta el camino real más pequeño y lee la traza contra ella antes de dar algo
por terminado.
