<!-- source: documentation/recipes/send-traces-to-the-observability-stack-you-already-run.md blob 30a4dbd5faec | translated: 2026-10-18 | reviewed: - -->
# Envía las trazas al stack de observabilidad que ya tienes

[English](../../recipes/send-traces-to-the-observability-stack-you-already-run.md) | **Español** | [Português](../../pt-BR/receitas/envie-os-traces-para-o-stack-de-observabilidade-que-voce-ja-tem.md) | [简体中文](../../zh-CN/实用方案/把追踪发到你已有的可观测性栈.md)

## El problema

> Ya tenemos tracing y dashboards. No voy a adoptar un segundo sitio donde
> mirar: ¿esto puede ir al stack que ya corremos?

Sí: una ejecución, dos consumidores. Las líneas narrativas siguen yendo a tu
logger, y los mismos eventos se convierten en spans de OpenTelemetry de camino
a la salida. Abajo hay un checkout con las dos salidas lado a lado.

## Configuración mínima

`narrativetrace-opentelemetry` más la API de OTel, que el módulo te deja a ti
(`compileOnly`, así que gana la versión de tu SDK):

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.narrativetrace:narrativetrace-opentelemetry:0.4.3")
    implementation("io.opentelemetry:opentelemetry-api:1.62.0")
}
```

Hay dos maneras de entrar, y la diferencia es *cuándo* existe un span:

| Modo | Clase | Úsalo cuando |
|---|---|---|
| En vivo, mientras corren los métodos | `OtelTraceEventListener` | Quieres los spans en tu colector incluso si el proceso muere a mitad de la petición — esta página |
| Por lotes, a posteriori | `TraceSpanExporter` | Quieres los spans de un árbol de trazas ya terminado, exportados una vez, al menor coste de ejecución: `exporter.export(context.captureTrace().roots())` |

En un despliegue, el listener en vivo normalmente no se construye en tu código
en absoluto: una implementación de `TraceEventListener` en el classpath se
descubre con `ServiceLoader` y se coloca junto a la retención automáticamente —
estar presente *es* la configuración
([Guía de configuración](../guia-de-configuracion.md) §10 y §11). Un exportador
en memoria no se puede descubrir así, de modo que el escenario de abajo compone
el pipeline explícitamente, que además es la imagen más clara de lo que habría
construido ese descubrimiento.

## El escenario

El pipeline es la única idea nueva: el listener de narración que ya tienes, con
el listener de OTel compuesto sobre la misma ruta durable. El checkout en sí
está envuelto exactamente igual que en cualquier otra página:

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

## La salida real

**Las líneas de log**, a las que nada de esto cambia — la misma narración que
produce [Añade registro a este servicio](anade-registro-a-este-servicio.md):

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

**Los spans que salieron de la misma ejecución**, tal como los recibió el
exportador en memoria — nombre, padre y cada atributo, ordenados:

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

## Cómo leerlo

- **Cuatro llamadas trazadas, cuatro spans, un árbol.** Los spans llegan en el
  orden en que *terminaron* — primero el más interno, la raíz al final, que es
  como cualquier pipeline de OTel ve un árbol terminado. La paternidad no se
  infiere de ese orden ni de un thread-local: el listener lee el `parentSpanId`
  explícito de cada evento, que es por lo que `InventoryService.reserve`,
  `PaymentService.charge` y `OrderRepository.save` nombran todos a
  `OrderService.placeOrder` como su padre aunque terminaran antes que él. El
  propio padre de `OrderService.placeOrder` se lee como `(remote — adopted
  from the inbound traceparent)`, no `(none — this is the root)`: esta
  ejecución adoptó una cabecera antes de hacer el pedido, así que su padre es
  el span de quien llama, nombrado por la cabecera — un span que este proceso
  nunca ejecutó y que, por tanto, nunca exportó.
- **Qué lleva cada span.** `narrative.class` y `narrative.method` son la
  llamada; `narrative.param.<nombre>` es un atributo por parámetro, con tipo
  (`narrative.param.quantity: 2` es un long, no la cadena `"2"`);
  `narrative.outcome` es el valor devuelto, y está ausente en las dos llamadas
  `void` porque no había nada que devolver. `narrative.trace_id` y
  `narrative.trace_name` son la identidad propia que NarrativeTrace da a la
  traza, llevada como atributos para que un span en tu backend pueda emparejarse
  con la línea de log y el archivo de traza que describen la misma llamada —
  [Sigue una petición entre servicios](sigue-una-peticion-entre-servicios.md) es
  ese mismo identificador haciendo el mismo trabajo a través de un límite de
  proceso. `nt.entryType` y `nt.schemaVersion` marcan el esquema que siguen
  estos atributos.
- **Un parámetro que es un record llega aplanado.**
  `OrderRepository.save(order)` es un parámetro, y se exportó como
  `narrative.param.order.orderId`, `narrative.param.order.customerId`,
  `narrative.param.order.sku`, `narrative.param.order.quantity`,
  `narrative.param.order.amountCents` — componente a componente, porque así se
  renderizó el valor: leyendo su estado, nunca llamando a su `toString()`.
- **La ocultación también llega a esta ruta.** No hay ningún atributo
  `narrative.param.cardNumber` ni `narrative.param.card` en ninguno de los spans
  de arriba — no un atributo con `[REDACTED]`, sino ningún atributo. Un valor
  denegado se descarta en la captura, antes de que exista el evento, así que
  ningún consumidor de ninguna de las dos rutas puede recibirlo; el escenario lo
  comprueba igual que comprueba el árbol de spans
  ([Mantén los secretos fuera de los logs](manten-los-secretos-fuera-de-los-logs.md)).
- **Qué ajuste movió qué evento a qué consumidor.** Tres decisiones, en este
  orden:
  1. `TracingLevel` decide si un evento se crea en absoluto. En `SUMMARY` estos
     mismos cuatro spans se exportarían *sin* los atributos
     `narrative.param.*`, porque a ese nivel los valores de los parámetros no se
     renderizan; en `OFF` no hay evento y, por tanto, no hay span
     ([Traza en producción sin el coste](traza-en-produccion-sin-el-coste.md)).
  2. La forma del pipeline decide quién ve el evento. Aquí un listener durable
     — narración y luego exportación — corre en línea, y la ranura de mejor
     esfuerzo conserva la retención, que es lo que leen `captureTrace()` y los
     archivos de traza. No se le quitó nada al logger para alimentar al
     exportador.
  3. El nivel de tu logger decide qué se *imprime*. Subir el logger
     `narrativetrace` a `INFO` vacía el bloque de log de arriba y no cambia nada
     de los spans.
- **Por qué esto no es un segundo sitio donde mirar.** Los spans son tuyos, en
  tu colector, bajo tu nombre de servicio, junto a los spans que tu framework ya
  emite. Lo que añade NarrativeTrace es un span por *llamada a método trazada*
  con los parámetros y el resultado puestos encima — un nivel por debajo del
  span HTTP que ya tienes.

## Qué no queda capturado

- **Lo que tu stack haga con los spans es de tu stack.** El muestreo, la
  retención, la indexación y si un atributo es buscable son decisiones del
  colector y del backend. Esta página muestra qué sale del proceso; no se afirma
  nada sobre la interfaz de ningún proveedor.
- **Los identificadores de span nunca se comparten; el de traza sí, en cuanto
  una cabecera nombra uno.** OpenTelemetry siempre elige sus propios
  identificadores de span, y la identidad de NarrativeTrace viaja al lado
  como atributos `narrative.trace_id` / `narrative.trace_name` para emparejar
  un span con una línea de log — la posición de un span en la cascada de tu
  backend es un identificador que eligió OTel, nunca uno propio de
  NarrativeTrace. El identificador de traza es distinto cuando se adoptó una
  cabecera, como arriba: la raíz se empareja con el contexto remoto que
  nombró la cabecera, así que el identificador de traza propio de
  OpenTelemetry para todo el subárbol exportado pasa a ser también el de esa
  cabecera — el mismo que ya llevaba `narrative.trace_id`, y lo que conecta
  los spans de este servicio con la traza de quien llama en tu backend, no
  solo los pone al lado.
- **En vivo y por lotes no son los mismos spans.** El listener de esta página
  crea un span mientras corre un método, así que un huérfano (una entrada sin
  salida — un proceso matado a mitad de la llamada) acaba cerrado con estado de
  error por el propio TTL del listener. El exportador por lotes solo ve árboles
  terminados y añade `narrative.duration_ms`, que la ruta en vivo no tiene.
- **El coste es un listener en el hilo de quien llama.** Bajo la topología por
  defecto un listener compuesto corre en línea, así que la creación del span
  está en el camino de la llamada trazada. Ese es el intercambio por la
  durabilidad; una topología de cola o de buffer en anillo lo mueve fuera, y el
  contrato del listener está escrito para que siga funcionando cuando cambie la
  topología.
- **Solo se exporta lo que está trazado.** No existe ningún span para una
  llamada que ninguna integración interceptó — la ruta de exportación hereda
  exactamente el límite de la ruta de captura.

**La habilidad para esto:** `narrativetrace-doctor` — una comprobación de
conexión por integración de framework, y además informa de si algún destino
recibe de verdad lo que se traza.
