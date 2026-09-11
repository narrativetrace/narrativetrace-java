<!-- source: documentation/privacy-and-redaction.md blob 1b80647f6c96 | translated: 2026-09-10 | reviewed: - -->
# Privacidad y ocultación

[English](../privacy-and-redaction.md) | **Español** | [Português](../pt-BR/privacidade-e-ocultacao.md) | [简体中文](../zh-CN/隐私与脱敏.md)

Esta librería corre dentro de tu proceso y escribe ficheros que tu equipo
va a compartir — artefactos de CI, baselines commiteadas, líneas de log de
producción. Esta página es la versión fila por fila de ese contrato: qué se
oculta, hasta dónde llega y hasta dónde no, y qué garantiza NarrativeTrace
frente a lo que no promete en absoluto.

## Ocultación, superficie por superficie

| Superficie | ¿Se puede desactivar la ocultación integrada? |
|---|---|
| Salida estándar de JUnit (proxy, agente, Spring, Micronaut, servlet, SLF4J) | No |
| Narración del agente | No |
| Un `ValueRenderer` personalizado que construya tu propio código | Sí — solo pasando `RedactionPolicy.DISABLED` al constructor explícitamente |
| `@NotTraced` | No aplica — es lo que provoca la ocultación, y siempre gana |
| `.nt` estructural | No aplica — no lleva valores que ocultar, para empezar |

Verificado contra el código, no inferido de la documentación: cada
integración que se distribuye — `NarrativeTraceProxy` y `AgentRuntime` (los
dos sitios donde de verdad ocurre la captura), y cada módulo de framework
construido sobre ellos — construye su `ValueRenderer` de la misma forma,
como un campo `private static final` sin punto de inyección:

```java
private static final ValueRenderer VALUE_RENDERER = new ValueRenderer();
```

`new ValueRenderer()` usa por defecto `RedactionPolicy.DEFAULT`. No hay
flag de configuración, propiedad del sistema ni opción del DSL del plugin
que llegue a esa constante — la única forma de obtener
`RedactionPolicy.DISABLED` es código de aplicación que construye su propia
instancia de `ValueRenderer` directamente, saltándose toda vía de
integración distribuida. Eso es un acto deliberado y revisable en tu propio
código fuente, no un estado de configuración que un despliegue pueda
cambiar en silencio.

## Qué atrapa la lista de denegación, y qué la supera

Dos mecanismos de ocultación independientes se aplican a cada valor
renderizado por reflexión:

1. **`@NotTraced`** en un parámetro, campo o componente de record —
   siempre oculta, incondicionalmente, en todas partes.
2. **La lista de denegación basada en nombre** (`RedactionPolicy.DEFAULT`)
   — compara **nombres de parámetro, nombres de campo y nombres de
   componente de record** contra un conjunto multilingüe integrado
   (`password`, `token`, `cvv`, `ssn`, `secret`, `authorization`,
   `cardNumber`, y sus equivalentes en español, portugués, francés, alemán
   y chino, entre otros), más una segunda comprobación independiente sobre
   la *forma* del propio valor (un JWT, un número de tarjeta válido según
   Luhn, una cadena `Set-Cookie`, un número de identidad nacional que pasa
   su propio checksum, un número de la Seguridad Social de EE. UU. con
   guiones), de modo que un token bearer pasado bajo un nombre no
   reconocido se sigue atrapando.

   Los nombres de parámetro se añadieron el 2026-09-10. Hasta entonces este
   eje solo alcanzaba campos y componentes de record, así que un método que
   recibía `String password` lo imprimía completo a menos que el parámetro
   llevara `@NotTraced` — mientras el README afirmaba lo contrario. La
   decisión ahora ocurre en el **momento de captura**, en los metadatos por
   método que tanto el proxy como el agente ya cachean, lo cual tiene dos
   consecuencias que vale la pena conocer: la búsqueda no cuesta nada por
   llamada trazada, y un valor denegado nunca entra al `TraceEvent`, así
   que no puede llegar a la vía de auditoría, al consumidor con buffer ni a
   un listener conectado a través del SPI del pipeline. Nunca se
   renderiza y luego se sustituye — un secreto formateado y descartado
   igual existió como string.

Un **`toString()` cuidado** normalmente se prefiere a la introspección
reflexiva — pero una clase que declara un campo `@NotTraced` se introspecta
de todos modos, así que se respeta la anotación en lugar de lo que ese
`toString()` habría impreso. Para un **campo o componente de record**, la
lista de denegación basada en nombre *no* tiene ese mismo poder de
anulación: solo se aplica cuando NarrativeTrace ya está introspeccionando
campos, así que una clase con su propio `toString()` y sin ningún miembro
`@NotTraced` se confía tal como está escrita. Solo la anotación explícita
supera a un `toString()` cuidado.

Un **parámetro** es distinto, y la diferencia se debe a en qué momento se
toma la decisión. Un parámetro cuyo *nombre* deniega la lista se resuelve
en el momento de captura, antes de que el argumento llegue a ningún
renderer — así que nunca se llama a ningún `toString()`, cuidado o no,
sobre él. La distinción no es una inconsistencia: un nombre de campo se
descubre *mediante* la introspección, mientras que un nombre de parámetro
se conoce a partir de la firma del método antes de que el valor sea
tocado en absoluto.

La ocultación también **sobrevive un nivel de contenedor** — `Optional`,
`Future`, `AtomicReference`, `AtomicReferenceArray` y un `Map.Entry`
independiente se abren en lugar de renderizarse mediante su propio
`toString()`, así que un valor oculto dentro de uno de ellos sigue oculto
en vez de filtrarse a través del envoltorio. Y **gana sobre una plantilla
de narración que lo nombra**: `{param.property}` en `@Narrated`/`@OnError`
resuelve una ruta hacia un miembro oculto como `[REDACTED]`, en cada nivel
de la ruta, nunca con el valor literal.

Detalle completo y ejemplos trabajados: [Guía de anotaciones](guia-de-anotaciones.md).

## Garantías

- **Los fallos de tracing están aislados de la ejecución del host.** El
  registro está aislado de excepciones en cada vía; ambos consumidores del
  pipeline se tragan sus propios errores. Un `toString()` que lanza
  excepción, un buffer lleno o un appender roto nunca cambian lo que tu
  método devuelve o lanza.
- **Las salidas estándar respetan la ocultación.** Consulta la tabla de
  arriba — ninguna integración distribuida expone una forma de saltársela.
- **El artefacto `.nt` estructural no tiene ningún valor de runtime.**
  Solo nombres, jerarquía de llamadas y tipos de resultado — cero
  superficie de inyección de prompts, y eso es un property test
  (ADR-002), no una política que alguien pueda olvidar aplicar.
- **La vía de análisis en buffer puede descartar eventos, pero siempre
  informa de la pérdida.** Nunca bloquea al llamador y nunca crece más
  allá de su límite; una captura que perdió eventos imprime el recuento en
  su propio pie de página en lugar de subinformar en silencio.

## No-garantías

- **Ninguna promesa de "coste cero".** El tracing hace trabajo, y el
  trabajo cuesta algo — consulta la
  [sección de rendimiento del README](../../LEAME.md#rendimiento) para las
  cifras medidas.
- **Ningún tracing de métodos privados.** Ambas vías de captura solo ven
  métodos de interfaz (proxy) o métodos no privados (agente); la rama de
  un método privado se infiere de cuáles de *sus* llamadas aparecen en la
  traza.
- **Ningún tracing automático de un bean sin interfaz, en ninguna vía
  basada en proxy.** Proxy, Spring y Micronaut envuelven todos a través de
  un proxy dinámico JDK, y una clase sin interfaz se deja intacta —
  en silencio, no como un error. El agente Java es la vía que no tiene
  este límite.
- **Todavía sin lista de exclusión para el agente.** `AgentConfig` parsea
  exactamente `packages`, `loggerName`, `level` y `loggingJars` — solo
  inclusión, nada para tallar una excepción dentro de un paquete incluido.
- **Sin soporte de Android o de imagen nativa de GraalVM hoy.** Detalle
  completo, incluido *por qué* Android es un "no" y no un "todavía no", en
  [Guía de instalación § Compatibilidad](guia-de-instalacion.md#compatibilidad).

## El modelo de pérdida en producción, visualmente

Cada evento se escribe de forma síncrona en el flujo de log durable y,
*además*, se publica en un buffer en memoria de mejor esfuerzo y tamaño
acotado — las dos mitades del pipeline de doble vía llevan obligaciones
opuestas por diseño:

```text
vía de log síncrona (SLF4J, en línea en el hilo del llamador)
   no debe hacer fallar la aplicación host
   no debe quedarse sin recursos si falla la vía de análisis
   durable — este es el registro

vía de análisis en buffer (anillo acotado, drenado hacia el almacén retenido)
   puede descartar eventos bajo carga, y siempre lo dice
   nunca debe bloquear al llamador
   nunca debe crecer más allá de su límite (65.536 slots por defecto)
   de mejor esfuerzo — esto es análisis, no auditoría
```

Perder el buffer pierde fidelidad de análisis para esa ejecución. Perder el
flujo de log pierde el registro. Esa asimetría es la razón de que sean dos
vías con dos comportamientos de fallo distintos, en lugar de una sola vía
con una única contrapartida.

## Lo que esta página no cubre

Dónde encaja NarrativeTrace en cada etapa de tu proceso — desarrollo,
CI/aceptación, producción — está en la
[Guía del ciclo de vida](guia-del-ciclo-de-vida.md). Qué ocurre cuando se
apila con proxies de AOP, librerías de contratos, u otro agente que
envuelve las mismas clases — el orden de anidamiento puede cambiar cómo *se
lee* una traza, nunca lo que un método *devuelve o lanza*, porque el
registro nunca reemplaza un resultado — está en [Eligiendo una integración §
Apilarse con otros
envoltorios](eligiendo-una-integracion.md#apilarse-con-otros-envoltorios).
