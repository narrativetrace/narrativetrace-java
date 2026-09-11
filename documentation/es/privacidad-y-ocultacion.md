<!-- source: documentation/privacy-and-redaction.md blob 270717ac0aff | translated: 2026-09-11 | reviewed: - -->
# Privacidad y ocultación

[English](../privacy-and-redaction.md) | **Español** | [Português](../pt-BR/privacidade-e-ocultacao.md) | [简体中文](../zh-CN/隐私与脱敏.md)

Esta librería corre dentro de tu proceso y escribe ficheros que tu equipo
va a compartir — artefactos de CI, baselines commiteadas, líneas de log de
producción. Esta página es la versión fila por fila de ese contrato: qué se
oculta, hasta dónde llega y hasta dónde no, y qué garantiza NarrativeTrace
frente a lo que no promete en absoluto.

**La salida de trazas en tiempo de prueba se escribe por defecto** — la
extensión de JUnit 5 y la integración de JUnit 4 escriben los artefactos
`.md`/`.json`/`.mmd` de cada prueba en el directorio efímero
`build/narrativetrace` (ignorado por git, regenerado en cada ejecución) sin
necesidad de configuración; `narrativetrace.output=false` lo desactiva. Ese
valor por defecto no cambia qué se oculta ni cómo — cada artefacto pasa por
el mismo `ValueRenderer` y la misma lista de denegación descrita más abajo,
tanto si la escritura se activó por defecto como si se activó
explícitamente. Consulta la [Guía de Configuración](guia-de-configuracion.md)
para cada propiedad, y [Qué hacer commit](que-commitear.md) para entender
por qué nada de esto pertenece al control de versiones.

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

### El `toString()` propio de un tipo nunca es de fiar mientras el tipo tenga estado

Este es el invariante sobre el que descansa el resto de esta página, y
merece la pena decirlo con claridad:

> **Una clase o `record` que declara campos de instancia se recorre campo a
> campo, a cualquier profundidad, consultando ambos mecanismos de ocultación
> por cada campo — sea lo que sea que su propio `toString()` hubiera
> impreso.**

Exactamente dos tipos de valor conservan su propio texto. Uno es una clase
**sin ningún campo de instancia**: no hay nada que ocultar ni nada que
recorrer. El otro es una clase **que define la plataforma** — `LocalDate`,
`Duration`, `UUID`, `URI` y similares — cuyo `toString()` es el formato del
JDK y no código de la aplicación, y que no puede declarar uno de tus campos
en primer lugar. Una clase declarada por *tu* propio código es código
de aplicación, extienda lo que extienda.

La única opción explícita para volver a un renderizado cuidadosamente
escrito es **`@NarrativeSummary`**: un método sin argumentos que escribes
*para* la traza, así que su salida es tu elección. Ni siquiera esa se
confía tal cual — su texto pasa por la comprobación de la forma del valor,
el escape de caracteres de control y el límite de longitud, exactamente
igual que un parámetro `String`, de modo que un resumen que interpola un
token bearer se sigue renderizando como `[REDACTED]`.

Hasta el 2026-09-11 la regla funcionaba al revés: cualquier `toString()` se
prefería sobre la introspección a menos que la clase declarara un campo
`@NotTraced`. Esa comprobación no consultaba ni la lista de denegación por
nombre ni los tipos de los campos, lo que dejaba dos vías abiertas. Un
simple `Login { username, password }` con un `toString()` escrito a mano
imprimía la contraseña **a profundidad cero** — sin anidamiento, sin
envoltorio, sin ninguna anotación de por medio. Y un `toString()`
cuidadosamente escrito en cualquier clase externa imprimía valores
`@NotTraced` anidados directamente a través de la serialización a texto
ordinaria de Java, porque la propia clase externa no declaraba nada
sensible. Recorrer los campos también vuelve a poner el límite de
profundidad y la protección contra ciclos delante de cada valor: tu
`toString()` solía ejecutarse fuera de ambos.

**El coste es real y se aceptó.** Una clase de valor con un `toString()`
agradable y sin `@NarrativeSummary` ahora se renderiza como un volcado de
campos — `Amount{currency: "EUR", units: 10}` en lugar de `EUR 10.00`. Más
feo, y correcto. Añade `@NarrativeSummary` a los tipos donde la lectura
importe.

Un **parámetro** se resuelve todavía antes, y la diferencia se debe a en
qué momento se toma la decisión. Un parámetro cuyo *nombre* deniega la
lista se decide en el momento de captura, antes de que el argumento llegue
a ningún renderer — así que nunca se llama a nada sobre el valor. La
distinción no es una inconsistencia: un nombre de campo se descubre
*mediante* la introspección, mientras que un nombre de parámetro se conoce
a partir de la firma del método antes de que el valor sea tocado en
absoluto.

La ocultación sobrevive a **cualquier envoltorio, a cualquier
profundidad** — `Optional`, `Future`, `AtomicReference`,
`AtomicReferenceArray` y un `Map.Entry` independiente se abren en lugar de
renderizarse mediante su propio `toString()`, y lo que contienen se
renderiza siguiendo exactamente estas reglas, que se vuelven a aplicar a lo
que sea que *eso* contenga. Una **clave** de `Map` se recorre de la misma
manera, de modo que una clave compuesta no puede sacar un campo por el
único lugar donde la serialización a texto es más difícil de evitar. Y la
ocultación **gana sobre una plantilla de narración que la nombra**:
`{param.property}` en `@Narrated`/`@OnError` resuelve una ruta hacia un
miembro oculto como `[REDACTED]`, en cada nivel de la ruta, nunca con el
valor literal.

### Cuando falla el renderizado de una parte

Un valor cuyo `toString()`, `@NarrativeSummary`, getter o accesor lanza una
excepción solo cuesta su propio hueco: esa parte se renderiza como
`<error: IllegalStateException>` — el **nombre del tipo de la excepción y
nada más** — y el resto del valor se renderiza completo. El mensaje se
excluye deliberadamente. Un mensaje de excepción suele interpolar el propio
valor que no se pudo formatear (`"cannot render " + password`), así que un
marcador que lo llevara convertiría la propia ruta de fallo del
renderizador en una fuga.

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
