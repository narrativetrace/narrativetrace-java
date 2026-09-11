<!-- source: documentation/annotations-guide.md blob 8d2a8c6da217 | translated: 2026-09-11 | reviewed: - -->

# Guía de anotaciones de NarrativeTrace para Java

[English](../annotations-guide.md) | **Español** | [简体中文](../zh-CN/注解指南.md)

Esta guía enumera todas las anotaciones disponibles en NarrativeTrace para Java y explica cuándo y cómo usar cada una.

NarrativeTrace sigue la filosofía de **El código es el log**: los nombres de métodos, los nombres de parámetros y los valores de retorno ya deberían comunicar por sí mismos la historia de la ejecución. Mantén primero la lógica de negocio limpia y expresiva, y usa las anotaciones de forma excepcional, no por defecto. Añade anotaciones solo cuando aporten un valor adicional concreto, como una narración dirigida, contexto específico de un error o la ocultación de datos sensibles.

## Inventario de anotaciones

| Anotación | Módulo | Objetivo | Propósito |
|---|---|---|---|
| `@Narrated` | `narrativetrace-core` | Método | Añade texto de narración legible para humanos a un método trazado. |
| `@OnError` | `narrativetrace-core` | Método | Añade texto de error contextual cuando un método lanza una excepción. |
| `@NotTraced` | `narrativetrace-core` | Parámetro, campo, componente de record | Marca un valor como oculto en la salida de la traza; también se respeta en campos y componentes de record durante la introspección reflexiva. |
| `@NarrativeSummary` | `narrativetrace-core` | Método | Proporciona un renderizado de valores personalizado para objetos en las trazas. |
| `@EnableNarrativeTrace` | `narrativetrace-spring` | Tipo (`@Configuration`) | Habilita el tracing con auto-proxy de Spring para los paquetes seleccionados. |

## Anotaciones del núcleo

### `@Narrated`

`@Narrated` es una válvula de escape, no la forma estándar de añadir
narración — el camino por defecto se deriva enteramente del propio nombre
del método, sus parámetros y su resultado. Recurrir a una plantilla es una
señal, la misma que la puntuación de claridad existe para detectar: significa
que el código no está diciendo por sí mismo lo que hace. Antes de escribir
una, pregúntate si el nombre del método es el problema real — un nombre
mejor arregla cada traza que pase por ese método, no solo esta línea.

Úsalo, deliberadamente, en métodos cuando quieras una frase explícita en la traza en lugar de depender solo del nombre del método + parámetros.

```java
public interface OrderService {
    @Narrated("Placing order of {quantity} units for customer {customerId}")
    OrderResult placeOrder(String customerId, int quantity);
}
```

Cómo funciona:

- Los marcadores de la plantilla usan los nombres de los parámetros (por ejemplo `{customerId}`).
- Funciona con el tracing por proxy y con el tracing basado en agente.
- Enriquece la salida de la traza en todos los niveles con texto de narración legible para humanos.

### `@OnError`

Usa `@OnError` para adjuntar mensajes específicos del contexto a las excepciones.

```java
public interface PaymentService {
    @OnError(value = "Payment declined for {customerId}, amount was {amount}",
             exception = PaymentDeclinedException.class)
    @OnError(value = "Temporary payment failure for {customerId}",
             exception = ExternalServiceException.class)
    PaymentConfirmation charge(String customerId, double amount, @NotTraced String token);
}
```

Cómo funciona:

- Puedes declarar varias anotaciones `@OnError` en el mismo método (es repetible).
- Si varias coinciden, se elige el tipo de excepción más específico.
- Un `@OnError("...")` a secas es equivalente a `exception = Throwable.class`.

La anotación contenedora `@OnErrors` existe detrás del `@OnError` repetible. En código normal nunca la escribes directamente — simplemente apila varias anotaciones `@OnError`.

### `@NotTraced`

Usa `@NotTraced` en **parámetros, campos o componentes de record** sensibles para que sus valores queden ocultos en todas partes.

```java
public interface AuthService {
    Session login(String username, @NotTraced String password);
}

// También en un campo o componente de record anidado dentro de un objeto trazado:
record Card(String last4, @NotTraced String pan) {}
```

Cómo funciona:

- El nombre sigue visible; el valor se sustituye por `[REDACTED]` en todos los renderers y exportadores.
- En un campo o componente de record, la ocultación ocurre durante la introspección reflexiva, de modo que un secreto anidado dentro de un DTO trazado queda oculto sin borrar el objeto completo.
- Casos de uso típicos: contraseñas, tokens, secretos, datos de tarjetas.

**La introspección reflexiva oculta por defecto, no filtra por defecto.** NarrativeTrace hace reflexión sobre los campos de un objeto trazado — pero una lista de denegación integrada basada en nombres (`RedactionPolicy`) oculta automáticamente los nombres sensibles habituales (`password`, `cvv`, `ssn`, `token`, `secret`, `authorization`, `cardNumber`, `accountNumber`, `routingNumber`, `sessionId`, `jwt`, `cookie`, `pan`, `iban`, …), y los valores de un `Map` cuya clave coincida, antes de renderizar ningún valor. Las claves de un `Map` se renderizan por la misma ruta protegida que cualquier otro valor: los objetos usados como clave respetan `@NotTraced` y la lista de denegación sobre sus propios campos (nunca su `toString()` crudo), y las claves de tipo cadena se sanean y se limitan en longitud. `@NotTraced` cubre los campos sensibles que la lista de denegación no reconocería por el nombre. Sobrescribe los patrones con `new ValueRenderer(…, RedactionPolicy.ofPatterns(...))` o desactívala con `RedactionPolicy.DISABLED`.

**Dos secretos se ocultan por lo que son, no solo por cómo se llaman.** La lista de denegación basada en nombres no puede ver un token portador pasado como `value`, devuelto como un `String` desnudo o situado sin nombre dentro de una lista, así que una segunda regla independiente mira los bytes. Se reconocen exactamente tres formas: un JWT (tres segmentos base64url cuyo primero empieza por `eyJ`), un número de tarjeta (13–19 dígitos, se admiten separadores, que supera la suma de comprobación de Luhn) y una cadena `Set-Cookie` (`nombre=valor` seguido de un atributo de cookie como `Path`, `Max-Age` o `HttpOnly`). Todo lo demás se renderiza con normalidad — esto es una lista corta de firmas estructurales, no una heurística de entropía, porque un valor borrado por conjetura es un agujero en tu narrativa que no puedes ver. Se acepta deliberadamente un falso positivo: un identificador con la longitud de un número de tarjeta que casualmente satisface Luhn. Un número de pedido que no lo satisface permanece visible. `RedactionPolicy.DISABLED` desactiva esta regla junto con la lista de denegación por nombres; `RedactionPolicy.ofPatterns(...)` sustituye solo los nombres y la mantiene activa.

**El `toString()` propio de un tipo nunca es de fiar mientras el tipo tenga estado.** Una clase o `record` que declara campos de instancia — propios o heredados — se recorre campo a campo, a cualquier profundidad, consultando ambos mecanismos de ocultación por cada campo, sea lo que sea que su `toString()` hubiera impreso. Exactamente dos tipos de valor conservan su propio texto: una clase sin ningún campo de instancia (nada que ocultar, nada que recorrer), y una clase que define la plataforma (`LocalDate`, `Duration`, `UUID`, `URI` y similares), cuyo `toString()` es el formato del JDK y no código de la aplicación. Una clase declarada por tu propio código es código de aplicación, extienda lo que extienda. La única opción explícita para volver a un renderizado cuidadosamente escrito es `@NarrativeSummary`, más abajo — e incluso su texto pasa por la comprobación de la forma del valor, el escape de caracteres de control y el límite de longitud. Hasta el 2026-09-11 la regla funcionaba al revés, y eso dejaba que un simple `Login { username, password }` con un `toString()` escrito a mano imprimiera la contraseña a profundidad cero, y que cualquier `toString()` cuidadosamente escrito imprimiera valores `@NotTraced` anidados directamente a través de la serialización a texto ordinaria de Java. El coste es real y se aceptó: una clase de valor con un `toString()` agradable y sin `@NarrativeSummary` ahora se renderiza como un volcado de campos — `Amount{currency: "EUR", units: 10}` en lugar de `EUR 10.00`. Añade `@NarrativeSummary` a los tipos donde la lectura importe.

**El ocultado sobrevive a cualquier envoltorio, a cualquier profundidad.** Un contenedor como `Optional`, `OptionalInt`/`OptionalLong`/`OptionalDouble`, `Future`, `AtomicReference`, `AtomicReferenceArray` o un `Map.Entry` suelto imprime el `toString()` crudo de su contenido si se le trata como un valor, así que NarrativeTrace lo abre y renderiza lo que contiene siguiendo exactamente estas reglas — que se vuelven a aplicar a lo que sea que *eso* contenga. Un `AtomicReferenceArray` se renderiza igual que el `Object[]` que contiene los mismos elementos, y un `Map.Entry` suelto se renderiza como `clave=valor`, exactamente igual que dentro de un `Map`. Un `Optional<Card>` se renderiza como `Card(number: "4111", cvv: [REDACTED])`, nunca como `Optional[Card[number=4111, cvv=123]]`; un envoltorio vacío se renderiza como `<empty>`. Esto importa porque `Optional<T>` es el tipo de retorno idiomático de una búsqueda, que es precisamente por donde viajan los datos ocultados.

**El ocultado gana sobre una plantilla que lo nombre.** `@Narrated` y `@OnError` resuelven las rutas `{param.propiedad}` sobre los argumentos crudos, y una ruta que alcanza un miembro ocultado se resuelve como `[REDACTED]` — a cualquier profundidad, así que un miembro ocultado a mitad de una ruta también oculta todo lo que se nombre por debajo de él. Lo mismo vale cuando un marcador nombra el objeto entero en lugar de una ruta dentro de él: `{card}` se renderiza como `Card(number: "4111", cvv: [REDACTED])`, nunca con el `toString()` propio del objeto, que no sabe nada de `@NotTraced`. Un marcador que nombra un objeto siempre lo renderiza con el mismo renderizador que lo captura, de modo que una plantilla y un argumento capturado coinciden en el aspecto del valor: un `record` se narra estructuralmente, como `Money(currency: "EUR", amount: 10)`, en ambos casos. Una clase corriente también se narra de forma estructural, como `Amount{currency: "EUR", units: 10}`: «no oculta nada» nunca fue un hecho que el renderizador pudiera comprobar, solo «ningún miembro `@NotTraced` *aquí*». Para elegir tú mismo la narración — para un `record` o para cualquier otra cosa — dale al tipo un método `@NarrativeSummary`, que aquí se respeta exactamente igual que en todas partes. Nombrar una ruta, o un objeto, nunca debilita las reglas que se aplican al valor directamente. La tercera forma de marcador obedece a esas mismas dos reglas: un `{nombre}` desnudo que nombra un valor directamente lo responde la lista de denegación leyendo esa clave exactamente igual que lee un nombre de campo, y la forma del propio valor, de modo que `@Narrated("login {password}")` y un JWT que llega como `{value}` se renderizan ambos como `[REDACTED]`. Si necesitas el valor en una narración, quita `@NotTraced` del componente; esa eliminación es la decisión deliberada y revisable, y queda a la vista en el diff.

### `@NarrativeSummary`

Usa `@NarrativeSummary` en un método sin argumentos que devuelva una cadena corta de resumen para el renderizado de valores.

```java
public record Customer(String id, String name, CustomerTier tier) {
    @NarrativeSummary
    public String toNarrativeSummary() {
        return "Customer[id=%s, tier=%s]".formatted(id, tier);
    }
}
```

Cómo funciona:

- `ValueRenderer` busca un método público anotado con `@NarrativeSummary` y sin parámetros.
- Si lo encuentra, la salida de ese método se usa en las trazas — después de pasar por la
  comprobación de la forma del valor, el escape de caracteres de control y el límite de
  longitud, así que un resumen que interpola un token portador se sigue renderizando como
  `[REDACTED]`. Tu intención elige el texto; no exime a los bytes.
- Si el método lanza una excepción, el valor se renderiza como `<error: IllegalStateException>`
  — el nombre del tipo de la excepción y nada más. El mensaje se excluye a propósito: suele
  interpolar el propio valor que no se pudo formatear.
- Si no lo encuentra, el renderizado recorre los campos del objeto. Un tipo sin campos de
  instancia, o uno que define la plataforma, conserva en su lugar su propio `toString()`.

## El contrato de pureza — efectos secundarios durante el tracing

NarrativeTrace puede invocar un conjunto pequeño y fijo de rutas de código de tus objetos mientras renderiza
una traza. Mantén esos miembros **puros** — libres de efectos secundarios como carga perezosa, contadores
de acceso, poblado de cachés o E/S — exactamente igual que lo harías para un depurador o un serializador.

Qué se invoca y qué no:

- **La introspección de campos nunca llama a tu código.** Para cualquier objeto que tenga
  estado, `ValueRenderer` lee sus *campos* por reflexión — una lectura pura de memoria. Un
  getter que incrementa un contador o carga datos de forma perezosa no es tocado por la
  introspección. Esta es ahora la ruta por defecto, no el respaldo: desde el 2026-09-11 un
  `toString()` personalizado ya no sustituye a la introspección en un tipo que tiene campos,
  así que se ejecutan *menos* de tus miembros durante el renderizado que antes, no más.
- **Lo que NarrativeTrace sí invoca:** un método `@NarrativeSummary`, el `toString()` de un
  tipo sin campos de instancia, los accesores de componentes de record y cualquier ruta de
  propiedad que nombres en una plantilla `@Narrated`/`@OnError` (`{order.total}` se resuelve
  llamando primero al método accesor directo `total()` y después al getter JavaBean
  `getTotal()`). Estos son los únicos lugares donde se ejecuta código de usuario durante el
  renderizado.
- **La invocación está acotada y aislada.** La salida tiene límites (longitud de cadenas, elementos de
  colecciones, profundidad de introspección), un getter, resumen o `toString()` que lanza una excepción
  nunca puede hacer fallar la llamada de negocio trazada (las plantillas recurren al literal
  `{placeholder}`; la parte fallida de un renderizado recurre a `<error: TypeName>`, nombrando el tipo
  de la excepción y nunca su mensaje), y los valores se renderizan de forma eager en
  el punto de llamada — cualquier efecto secundario ocurre una sola vez, en un punto determinista, en el hilo llamante.
- **El renderizado nunca fuerza computación diferida.** Un `Future` solo se desenvuelve cuando
  `isDone()`; no se bloquea ni se dispara nada.

Si un miembro no puede ser puro, anótalo con `@NotTraced` — el valor de un miembro oculto no se
lee jamás — o dale al tipo un `@NarrativeSummary` para que controles exactamente qué se accede.
Un `toString()` curado ya no cumple este propósito en un tipo que tiene campos: no se llega a
llamar, precisamente para que no pueda imprimir más allá de una ocultación. Con
`TracingLevel.OFF` (y para los valores de parámetros con `SUMMARY`), no se renderiza ningún
argumento, así que no se toca código de usuario en la ruta caliente.

## Anotaciones de Spring

### `@EnableNarrativeTrace`

Usa `@EnableNarrativeTrace` en una clase de configuración de Spring para envolver automáticamente los beans con proxies de NarrativeTrace.

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.orders", "com.example.payments"})
public class AppConfig {
}
```

Atributos:

- `basePackages` — prefijos de paquete que limitan qué beans se envuelven. Por defecto es el paquete de la clase anotada.
- `loggerName` — nombre del logger SLF4J para los eventos de traza (por defecto `"narrativetrace"`). Cuando `narrativetrace-slf4j` está en el classpath, el contexto autocreado narra a través de SLF4J bajo este nombre (`Slf4jTraceEventListener` en la ruta síncrona de la tubería). Ponlo en `""` para desactivar la narración.

Cómo funciona:

- Registra la configuración Spring de NarrativeTrace y el post-procesador de beans.
- Solo se consideran los beans dentro de `basePackages`.
- Los beans deben implementar interfaces para poder ser envueltos en proxies (proxies dinámicos JDK).
- Cuando `narrativetrace-slf4j` está en el classpath, el contexto autocreado narra a través de SLF4J automáticamente. Define tu propio bean `narrativeContext` para sobrescribirlo.

## Ejemplo completo

```java
public interface TransferService {
    @Narrated("Transferring {amount} from {fromAccountId} to {toAccountId}")
    @OnError(value = "Transfer rejected for source account {fromAccountId}",
             exception = IllegalStateException.class)
    TransferResult transfer(
            String fromAccountId,
            String toAccountId,
            double amount,
            @NotTraced String authToken
    );
}
```

Este único método combina narración, contexto de error dirigido y ocultación de parámetros.

## Validación de plantillas

Las plantillas de `@Narrated` y `@OnError` usan marcadores `{paramName}` y `{param.property}`. Si un marcador no coincide con ningún parámetro — por ejemplo `{custmerId}` en lugar de `{customerId}` — el literal `{custmerId}` sobrevive en la salida resuelta de la traza.

NarrativeTrace lo detecta automáticamente durante las pruebas. Tras cada prueba, la extensión de JUnit 5 y la regla de JUnit 4 recorren cada nodo de traza en busca de patrones `{...}` sin resolver e imprimen una advertencia:

```
WARNING: Unresolved template placeholder(s) detected:
  - OrderService.placeOrder: {custmerId} in narration
  - ExpenseService.pay: {expense.payer.name} in narration
      ↳ nested path not supported — only one property level resolves (e.g. {object.property})
```

Solo se resuelve un único nivel de propiedad: `{expense.payer}` llama a `payer()` (o `getPayer()`) sobre el argumento `expense`. Una ruta más profunda como `{expense.payer.name}` nunca se puede resolver — el analizador trata `payer.name` como un único accesor inexistente — así que el marcador sobrevive literalmente en tiempo de ejecución (de forma tolerante, sin lanzar nunca). Como se trata de un error estructural de escritura y no de un valor que resultó ser `null`, la validación en tiempo de pruebas señala las rutas de varios niveles con la pista adicional `nested path not supported`.

**Una ruta ocultada se resuelve como `[REDACTED]`** — ni al valor, ni al marcador literal. Un marcador que nombra el objeto en sí (`{card}`) se rechaza igual: NarrativeTrace renderiza el objeto sin sus miembros ocultados en lugar de llamar a su `toString()`. Ambas mitades de la regla de ocultado se aplican a lo largo de la ruta: `@NotTraced` sobre un campo o componente de registro, y la lista de denegación basada en nombres (`password`, `cvv`, `token`, …) sobre una propiedad que ninguna anotación cubre. Como lo que se rechaza es la ruta entera, esto también vale para una ruta de varios niveles que de todos modos nunca podría resolverse: `{order.card.cvv}` se renderiza como `[REDACTED]` en lugar de sobrevivir literalmente. Un marcador que no nombra ningún parámetro, o una propiedad que su dueño no declara, sí se conserva literalmente — eso es una errata de escritura, no hay ningún valor detrás, y la advertencia anterior debe seguir apareciendo.

No se necesita configuración — las advertencias aparecen en la salida de consola siempre que la extensión o la regla estén activas. Esto detecta erratas en los nombres de los marcadores (por ejemplo `{order.stauts}`) y rutas de varios niveles irresolubles en cuanto una prueba ejercita el método anotado.

## Véase también

- [Guía de instalación](guia-de-instalacion.md) — dependencias, vías de integración, configuración de la salida de trazas
- [Guía de configuración](guia-de-configuracion.md) — niveles de tracing, configuración de JUnit/Gradle/Spring/SLF4J
- [Guía de claridad](guia-de-claridad.md) — modelo de puntuación, componentes NLP, integración con JUnit
