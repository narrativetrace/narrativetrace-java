<!-- source: documentation/choosing-an-integration.md blob bd89ddac9a5f | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Eligiendo una integración

[English](../choosing-an-integration.md) | **Español** | [Português](../pt-BR/escolhendo-uma-integracao.md) | [简体中文](../zh-CN/选择集成方式.md)

NarrativeTrace tiene un único modelo de captura — un evento de
entrada/salida publicado a través del pipeline — al que se llega mediante
cinco mecanismos de conexión distintos. Esta página responde a "qué módulo
necesito en realidad", primero como tabla de referencia, luego como
diagrama de decisión, y por último con las salvedades de cada vía.

## Quieres... / Empieza con...

| Quieres | Empieza con |
|---|---|
| Trazas en tests, con el mínimo cableado | Plugin de Gradle + `narrativetrace-junit5` |
| Lo mismo, en JUnit 4 | `narrativetrace-junit4` |
| Elegir exactamente qué se envuelve, en Java puro | `narrativetrace-proxy` (proxy dinámico JDK) |
| Beans de Spring trazados automáticamente | `narrativetrace-spring` |
| Ciclo de vida de peticiones HTTP de Spring en producción | `narrativetrace-spring-web` (conecta `narrativetrace-servlet`) |
| Cualquier app de servlets, sin Spring | `narrativetrace-servlet` |
| Beans y peticiones de Micronaut | `narrativetrace-micronaut` + `narrativetrace-micronaut-http` |
| **Cero cambios de código** — una app que no puedes o no quieres modificar | `narrativetrace-agent` (agente Java) |
| Visibilidad asíncrona entre hilos (`@Async`, Reactor, executors) | `narrativetrace-micrometer`, o `ContextSnapshot` a mano |
| Trazas en tu flujo de logs de producción | `narrativetrace-slf4j` |
| Spans de OpenTelemetry | `narrativetrace-opentelemetry` |

Esta es la misma matriz que lleva el
[README raíz](../../LEAME.md#elige-tu-integración); vive también aquí como
ancla para el diagrama y el detalle de más abajo.

## La decisión

El informe del que creció esta página proponía un diagrama más corto que el
de abajo — su versión trataba "no ser una app de Spring/Micronaut" como la
única razón para recurrir al agente. Así no es como funcionan en realidad
las integraciones de framework: `narrativetrace-spring` y
`narrativetrace-micronaut` envuelven los beans con el mismo proxy dinámico
JDK que usa `narrativetrace-proxy`, así que un bean de Spring sin interfaz
se omite exactamente igual que lo haría un objetivo de proxy normal
(verificado contra `spring-integration-guide.md` y
`micronaut-integration-guide.md`: ambas dicen "bean must implement at least
one interface"). El diagrama de abajo incluye esa rama.

```text
¿Controlas cómo se construye el objeto (Java puro, un test)?
   |
   +-- sí  --> ¿el servicio implementa una interfaz?
   |             |
   |             +-- sí --> proxy JDK (narrativetrace-proxy)
   |             +-- no --> agente Java (instrumentación de bytecode,
   |                        no requiere interfaz)
   |
   +-- no  --> ¿es un bean de Spring o Micronaut?
                 |
                 +-- sí --> ¿el bean implementa una interfaz?
                 |             |
                 |             +-- sí --> integración de framework
                 |             |          (narrativetrace-spring /
                 |             |           narrativetrace-micronaut)
                 |             +-- no --> agente Java
                 |
                 +-- no  --> agente Java (cero cambios de código)
```

Dos vías convergen en el agente por la misma razón de fondo: es el único
mecanismo aquí que instrumenta bytecode directamente y por tanto nunca
llega a hacerse la pregunta de "¿tiene interfaz?". La contrapartida es que
el agente no tiene un equivalente a la granularidad de exclusión de
`basePackages` de Spring ni al opt-in por llamada del proxy — consulta las
salvedades de abajo y [Apilarse con otros
envoltorios](#apilarse-con-otros-envoltorios) para ver qué pasa cuando se
apila con algo más que también envuelve las mismas clases.

## Algo que comparten todas las vías

Los cinco mecanismos de conexión publican a través del mismo
`EventPipeline` (`ai.narrativetrace.core.pipeline`); ninguno define su
propia noción de llamada capturada. Elegir una integración es una cuestión
de *cómo se envuelve la llamada*, nunca de qué se registra una vez
envuelta — una única captura canónica alimenta todas las vías de
renderizado (detalle humano, estructura segura para IA, spans de
OpenTelemetry, agregación), que así se mantienen en paridad por
construcción y no por convención.

## Salvedades de cada vía

- **Proxy JDK** — el objetivo debe implementar la interfaz que se pasa a
  `NarrativeTraceProxy.trace(...)`, o la llamada lanza `ClassCastException`
  en el punto de envoltura. Solo son visibles los métodos de la interfaz;
  una llamada hecha directamente sobre la instancia concreta se salta el
  proxy por completo.
- **Spring / Micronaut** — el mismo requisito de interfaz, impuesto en
  silencio: un bean sin interfaz se deja intacto, no es un error. Los
  paquetes base son solo de inclusión.
- **Agente Java** — se instrumenta cada método no privado y no abstracto
  bajo los paquetes que coinciden; hoy no hay opt-out por método ni lista
  de exclusión (`AgentConfig` parsea `packages`, `loggerName`, `level`,
  `loggingJars` — nada más). La coincidencia de paquetes respeta el
  delimitador, así que `com.acme` nunca coincide con `com.acmeExtra`. En un
  host sin proveedor de logging en el classpath — lo más habitual, un
  servidor de aplicaciones desnudo — el agente no narra nada hasta que
  añades uno; consulta
  [Solución de problemas](solucion-de-problemas.md#el-agente-traza-pero-no-aparece-nada-en-mis-logs).
- **Trabajo entre hilos** — ninguna de las cinco vías anteriores fusiona
  automáticamente las llamadas de un hilo asíncrono/executor con la traza
  padre. Usa `ContextSnapshot.wrap(...)` a mano, o registra
  `NarrativeTraceThreadLocalAccessor` con Micrometer.

## Apilarse con otros envoltorios

NarrativeTrace rara vez es lo único que envuelve un método: proxies AOP,
librerías de contratos, interceptores de contenedor y otros agentes de
observabilidad pueden conectarse a la misma llamada. Tres reglas se
sostienen en todos los mecanismos de conexión de arriba:

- **Un frame de traza por cruce de frontera de negocio.** El objetivo es
  narrar las llamadas que hace tu código, no la maquinaria a su alrededor
  — los métodos puente, los stubs de vista generados por el contenedor,
  los decoradores generados por otra librería, o el propio renderizado de
  NarrativeTrace no son el objetivo.
- **El orden de anidamiento cambia cómo *se lee* una traza, nunca lo que
  *devuelve o lanza*.** Que un envoltorio quede más adentro o más afuera
  puede cambiar la forma del árbol de llamadas registrado, pero el
  registro está aislado frente a excepciones en todas las vías y nunca
  reemplaza un resultado — el resultado o la excepción de negocio que ve
  quien llama siempre es el real.
- **El alcance es solo de inclusión, en todas partes.** El `packages=`
  del agente, los paquetes base de Spring y Micronaut, y la interfaz
  objetivo explícita del proxy respetan todos un límite de delimitador
  (`com.acme` nunca coincide con `com.acmeExtra`), y ninguno tiene todavía
  lista de exclusión — una clase que vive dentro de tus paquetes
  coincidentes se envuelve, sea tuya o de otra librería.

## Techos de plataforma

Todas las vías de arriba asumen una JVM de servidor o escritorio. Android
no está soportado hoy (tres mecanismos distintos — el descubrimiento por
SPI, la búsqueda del listener de SLF4J y el renderizado reflexivo de
valores — se degradan en silencio bajo R8/ProGuard en lugar de fallar de
forma ruidosa), y la imagen nativa de GraalVM no está probada (las vías de
proxy y agente dependen de reflexión, sin metadatos de alcanzabilidad
publicados). Detalle completo:
[Guía de instalación § Compatibilidad](guia-de-instalacion.md#compatibilidad).

## Recetas

Cada vía de la matriz tiene una receta completa y lista para copiar y
pegar en la [Guía de instalación](guia-de-instalacion.md) — esta página
responde *cuál*, esa otra responde *cómo*.
