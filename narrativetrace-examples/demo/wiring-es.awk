# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# Notas de cableado por escenario para el lanzador de la demo, en español: cómo está
# configurada la traza de CADA escenario. La tabla base (en inglés) es wiring.awk; las
# claves son IDÉNTICAS (los títulos de los escenarios, tal como los imprimen los
# ejemplos) y la tarea `demoWiringCheck` exige paridad de claves en ambas direcciones.
#
# Los identificadores, nombres de clase y propiedades se mantienen literales — igual que
# en las vistas traducidas, el código conserva su nombre original.

BEGIN {
  # ---- cómo se eligen los renderers — impreso una vez, en la primera sección de renderizado ----

  wiring["--- Trace tree ---"] = \
    "Los renderers no se configuran: no hay default, ni registro, ni ajuste. La captura\n" \
    "produce un TraceTree y tú llamas al renderer que quieras — aquí es una línea,\n" \
    "new IndentedTextRenderer().render(trace); ProseRenderer, MarkdownRenderer y los\n" \
    "renderers de diagramas de abajo funcionan igual. NarrativeRenderer es un único método\n" \
    "(String render(TraceTree)), así que tu propio renderer es una lambda.\n" \
    "Las líneas en vivo → ← !! no son un renderer: es Slf4jTraceEventListener en el\n" \
    "DualPathPipeline, formateando cada evento según ocurre — la única vista que obtienes\n" \
    "sin escribir código de renderizado, y lo que ingiere tu herramienta de logs.\n" \
    "La configuración elige renderer en exactamente un lugar, los archivos de traza\n" \
    "escritos desde las pruebas: -Dnarrativetrace.output=true con\n" \
    "-Dnarrativetrace.format=markdown|text|mermaid|plantuml (markdown es el default;\n" \
    "narrativeTrace { format = … } del plugin de Gradle fija la misma propiedad)."

  # ---- ecommerce (Spring AOP, anotaciones, propagación asíncrona) ----

  wiring["Scenario 1: Successful Order + Async Notification"] = \
    "Cableado: @EnableNarrativeTrace en ECommerceConfig — Spring envuelve cada bean de\n" \
    "servicio en un proxy de trazado al arrancar, así que DefaultOrderService no contiene\n" \
    "ningún código de trazado. La línea // del árbol es @Narrated en\n" \
    "OrderService.placeOrder; cardToken se imprime como [REDACTED] porque ese parámetro\n" \
    "lleva @NotTraced. La notificación asíncrona se une a esta misma traza mediante\n" \
    "ContextPropagatingTaskDecorator en el bean taskExecutor."

  wiring["Scenario 2: Payment Failure — Inventory Leak Bug"] = \
    "Cableado: sin cambios respecto al escenario 1 — no se añadió nada para capturar o\n" \
    "registrar este fallo. El proxy registra la PaymentDeclinedException lanzada y\n" \
    "desenrolla el árbol por sí mismo; el texto entre corchetes tras !! viene de @OnError\n" \
    "en PaymentService.charge."

  wiring["Scenario 3: Flaky External Service"] = \
    "Cableado: aquí no hay Spring — NarrativeTraceProxy.trace(impl,\n" \
    "NotificationService.class, context) envuelve un objeto plano en tiempo de ejecución\n" \
    "con un proxy dinámico del JDK. El mismo NarrativeContext que los beans de arriba, así\n" \
    "que ambas llamadas caen en la misma traza: las anotaciones y el contenedor son\n" \
    "comodidades, no requisitos."

  wiring["Scenario 4: Unknown Customer"] = \
    "Cableado: sin cambios — los mismos proxies de Spring produjeron estas líneas, y solo\n" \
    "difiere el renderizado. @OnError en CustomerService.findCustomer aporta el mensaje\n" \
    "entre corchetes; el traceId del MDC en el prefijo lo fija el ejemplo, como lo haría\n" \
    "Micrometer."

  wiring["Scenario 5: Out of Stock"] = \
    "Cableado: los mismos proxies de Spring; @OnError en InventoryService.reserve aporta\n" \
    "el mensaje entre corchetes. El diagrama de abajo es el mismo árbol capturado pasado a\n" \
    "PlantUmlSequenceDiagramRenderer y dibujado por AsciiSequenceDiagram — no una nueva\n" \
    "ejecución."

  wiring["Scenario 6: Explicit Async Trace Capture"] = \
    "Cableado: ContextPropagatingTaskDecorator en el bean taskExecutor (ECommerceConfig)\n" \
    "lleva el MDC y el contexto narrativo al hilo trabajador; el\n" \
    "NarrativeTraceThreadLocalAccessor de Micrometer registrado en narrativeContext() hace\n" \
    "esa propagación automática para todo lo que ejecute el executor."

  # ---- clarity (proxies planos; la variable bajo prueba es el naming, no la configuración) ----

  wiring["Scenario 1: Guest Books a Room (Excellent Naming)"] = \
    "Cableado: sin Spring, sin anotaciones — NarrativeTraceProxy.trace(impl, Iface.class,\n" \
    "context) alrededor de cada servicio, con DualPathPipeline(new\n" \
    "Slf4jTraceEventListener()) convirtiendo los eventos en las líneas en vivo. Los cuatro\n" \
    "escenarios están cableados idénticamente; solo cambia la calidad de los nombres."

  wiring["Scenario 2: Booking via Manager (Adequate Naming)"] = \
    "Cableado: el mismo montaje de proxies tras context.reset() — esta vez una sola\n" \
    "interfaz trazada. Nada cambió en la configuración entre escenarios; cambiaron los\n" \
    "nombres."

  wiring["Scenario 3: Legacy Data Processing (Poor Naming)"] = \
    "Cableado: el mismo montaje de proxies otra vez. Un trazador solo puede informar de lo\n" \
    "que el código dice de sí mismo: nombres genéricos dentro, traza genérica fuera —\n" \
    "ninguna configuración rescata este caso."

  wiring["Scenario 4: Guest Repository (Cohesion Mismatch)"] = \
    "Cableado: el mismo montaje de proxies, una interfaz de repositorio. La cohesión se\n" \
    "juzga después a partir del árbol capturado, así que las llamadas inconexas de abajo\n" \
    "son todo lo que el analizador tiene para trabajar."

  wiring["CLARITY ANALYSIS REPORT"] = \
    "Cableado: los cuatro árboles capturados arriba se pasan a ClarityAnalyzer, y\n" \
    "ClarityReportRenderer imprime un informe por escenario más el resumen de la suite. El\n" \
    "análisis lee trazas capturadas — sin instrumentación extra, sin segunda ejecución del\n" \
    "código."

  # ---- minecraft (cableado idéntico en ambas mitades; solo difiere el vocabulario) ----

  wiring["Refactored: Player Joins World"] = \
    "Cableado: sin Spring, sin anotaciones — cada interfaz se envuelve con\n" \
    "NarrativeTraceProxy.trace(impl, Iface.class, context), y DualPathPipeline(new\n" \
    "Slf4jTraceEventListener()) convierte los eventos en líneas SLF4J ordinarias."

  wiring["Unrefactored: Player Joins World"] = \
    "Cableado: byte a byte el montaje de arriba — mismos proxies, mismo pipeline, mismo\n" \
    "contexto tras un reset(). Solo difieren los nombres de clases y métodos, y ese es\n" \
    "precisamente el punto."

  # ---- library (Kotlin, la misma API de proxy, anotaciones en interfaces) ----

  wiring["Scenario 1: Successful Book Borrow"] = \
    "Cableado: Kotlin, sin Spring — NarrativeTraceProxy.trace(...) alrededor de\n" \
    "CatalogService, MemberService y LendingService. @Narrated en\n" \
    "LendingService.borrowBook aporta la línea //; cardNumber se imprime como [REDACTED]\n" \
    "por @NotTraced en el parámetro."

  wiring["Scenario 2: Book Unavailable"] = \
    "Cableado: los mismos tres proxies tras context.reset(). BookUnavailableException la\n" \
    "lanza el propio LendingService y el proxy la registra a la salida — no hay código de\n" \
    "manejo de errores en esta ruta, y nada que mantener sincronizado cuando cambie."
}
