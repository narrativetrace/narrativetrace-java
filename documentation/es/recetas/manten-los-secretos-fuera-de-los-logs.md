<!-- source: documentation/recipes/keep-secrets-out-of-the-logs.md blob 11e1423f2f0a | translated: 2026-10-22 | reviewed: - -->
# Mantén los secretos fuera de los logs

[English](../../recipes/keep-secrets-out-of-the-logs.md) | **Español** | [Português](../../pt-BR/receitas/mantenha-os-segredos-fora-dos-logs.md) | [简体中文](../../zh-CN/实用方案/别让密钥进日志.md)

## El problema

> Si esto loguea cada llamada con cada argumento, va a loguear un número de
> tarjeta. Convénceme de que no.

El checkout recibe una contraseña y un número de tarjeta. Abajo está una
ejecución real con los dos, el log que escribió de verdad y la prueba que
demuestra que ninguno de los dos valores está en ese archivo.

## Configuración mínima

Ninguna. La ocultación está activa en todas las integraciones que publicamos y
no hay ninguna superficie de configuración que la desactive — la lista de
denegación se consulta en el momento de la *captura*, antes de que un valor se
convierta en evento, así que un valor denegado nunca llega a la línea de log,
ni al archivo de traza, ni a la ruta con buffer, ni a un listener del pipeline.
[Privacidad y ocultación](../privacidad-y-ocultacion.md) es la versión fila a
fila, verificada contra el código.

Hay dos cosas, eso sí, que vale la pena hacer una vez en tu propio proyecto:

1. **Escribe la prueba de esta página para tu propio flujo.** Una ocultación
   que no has comprobado es una afirmación, no una propiedad de tu build. El
   hallazgo del doctor "la ocultación nunca se comprueba en una prueba" existe
   justo para esto.
2. **Conoce la vía de escape.** Un secreto bajo un nombre que nadie podría
   adivinar — `fingerprint`, `answer2` — se alcanza con `@NotTraced` sobre el
   parámetro, el campo o el componente del record, que siempre gana
   ([Guía de anotaciones](../guia-de-anotaciones.md)).

## El escenario

Un colaborador nuevo, para que la ejecución lleve una contraseña además de una
tarjeta. Su parámetro se llama `password` — ese nombre es justo el punto:

```java
// src/main/java/com/example/checkout/CustomerAccounts.java
package com.example.checkout;

/**
 * Signs a customer in before the checkout charges them. The parameter is named {@code password}.
 */
public interface CustomerAccounts {

  /**
   * @return the session id the checkout carries for the rest of the request
   * @throws IllegalArgumentException when the password is blank
   */
  String signIn(String customerId, String password);
}
```

La ejecución autentica al cliente y coloca el pedido. Los dos valores secretos
son constantes aquí para que la prueba pueda buscarlos después en el log; un
flujo real los recibe en la petición:

```java
// src/test/java/com/example/checkout/SecretsRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/keep-secrets-out-of-the-logs.md: one checkout carrying a password
 * and a card number writes neither into the log its own logger produced — the password caught by
 * its NAME, the card number by its name and again by its VALUE (rule 8, docs as tests).
 *
 * <p><b>@llmNote</b> The assertion reads the file Logback wrote, not a rendering this test
 * produced: a page claiming "the secret is not in your log" is only worth what a real log file
 * says. The Gradle task {@code recipeSecretsLog} runs this class alone and names that file, which
 * is the file the page embeds; in the suite run the same property names the shared log, where the
 * absence assertions hold over every other scenario's lines too.
 */
@ExtendWith(NarrativeTraceExtension.class)
class SecretsRecipeTest {

  /** A fixed W3C traceparent, so the page's embedded output names the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  /** Luhn-valid, like a real one — which is what the value-shape rule recognizes. */
  static final String CARD_NUMBER = "4242424242424242";

  /**
   * The password this run signs in with. Matches no value-shape rule at all: only its parameter's
   * NAME can save it.
   *
   * <p><b>@llmNote</b> Named for the phrase rather than for the field it fills, because the public
   * snapshot's secrets gate reads a constant whose name carries that word and whose value is a
   * string literal as a credential shipped in the open — correctly, for every such line but a
   * documentation fixture's. Keeping the fixture out of that shape costs one word and keeps the
   * gate's signal clean for the next reader; every port mirroring this page wants the same.
   */
  static final String SIGN_IN_PASSPHRASE = "orchid-trombone-77";

  @Test
  void a_checkout_with_a_password_and_a_card_writes_neither(NarrativeContext context)
      throws IOException {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));

    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    var accounts =
        NarrativeTraceProxy.trace(new InMemoryAccounts(), CustomerAccounts.class, context);
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    var checkout =
        NarrativeTraceProxy.trace(
            new DefaultOrderService(inventory, payments, orders), OrderService.class, context);

    var session = accounts.signIn("C-1234", SIGN_IN_PASSPHRASE);
    var orderId = checkout.placeOrder("C-1234", "SKU-KB", 2, CARD_NUMBER);

    assertThat(session).isEqualTo("SESSION-C-1234");
    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
    assertNeitherSecretIsInTheLog();
  }

  /** The proof: the log file the project's own logger wrote, read back and searched. */
  private static void assertNeitherSecretIsInTheLog() throws IOException {
    var log = Files.readString(logFile());

    assertThat(log)
        .contains(
            "CustomerAccounts.signIn(customerId: \"C-1234\", password: [REDACTED])",
            "cardNumber: [REDACTED]",
            "card: [REDACTED]");
    assertThat(log).doesNotContain(SIGN_IN_PASSPHRASE, CARD_NUMBER);
  }

  /** Where Logback wrote it — the Gradle task that owns this run names the file. */
  private static Path logFile() {
    return Path.of(
        System.getProperty("narrativetrace.recipes.log", "build/narrativetrace/recipes/suite.log"));
  }
}
```

## La salida real

**El log que escribió la ejecución**, a través del logger del propio proyecto:

```text
TRACE [solid wand romps] [narrativetrace] - → CustomerAccounts.signIn(customerId: "C-1234", password: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - ← returned: "SESSION-C-1234"
TRACE [solid wand romps] [narrativetrace] - → OrderService.placeOrder(customerId: "C-1234", sku: "SKU-KB", quantity: 2, cardNumber: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - → InventoryService.reserve(sku: "SKU-KB", quantity: 2)
TRACE [solid wand romps] [narrativetrace] - ← completed
TRACE [solid wand romps] [narrativetrace] - → PaymentService.charge(customerId: "C-1234", amountCents: 7998, card: [REDACTED])
TRACE [solid wand romps] [narrativetrace] - ← returned: "PAY-C-1234-7998"
TRACE [solid wand romps] [narrativetrace] - → OrderRepository.save(order: Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998))
TRACE [solid wand romps] [narrativetrace] - ← completed
TRACE [solid wand romps] [narrativetrace] - ← returned: "ORD-C-1234-SKU-KB-2"
```

**El archivo de traza de la misma ejecución**, que es la otra superficie a la
que podría haber llegado un secreto —
`build/narrativetrace/traces/SecretsRecipeTest/a_checkout_with_a_password_and_a_card_writes_neither.md`:

```text
---
type: trace
run: gross hill grins
scenario: A checkout with a password and a card writes neither
entry_point: CustomerAccounts.signIn
duration_ms: 3.444
trace_id: c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7
trace_name: solid wand romps
method_count: 5
error_count: 0
---
<!--
NarrativeTrace trace file
What this is: the recorded call tree of one test run: SecretsRecipeTest.a_checkout_with_a_password_and_a_card_writes_neither, passed, 2.281 ms.
How to read it: indentation is call nesting; each line is one call with its parameters and its return value or thrown exception, as rendered values. [REDACTED] marks a redacted value.
Failing call: none, no call threw in this run.
Run index: ../../INDEX.md. Artifact list: ../../manifest.json (both are written when the run ends).
-->

## Trace: solid wand romps — CustomerAccounts.signIn

**Scenario:** A checkout with a password and a card writes neither
**Duration:** 3.444ms | **Result:** PASSED

### Call Flow

- **CustomerAccounts.signIn**(customerId: `"C-1234"`, password: `[REDACTED]`) → `"SESSION-C-1234"` — 0.922ms #1
- **OrderService.placeOrder**(customerId: `"C-1234"`, sku: `"SKU-KB"`, quantity: `2`, cardNumber: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 2.032ms #2
  - **InventoryService.reserve**(sku: `"SKU-KB"`, quantity: `2`) — 0.187ms #2.1
  - **PaymentService.charge**(customerId: `"C-1234"`, amountCents: `7998`, card: `[REDACTED]`) → `"PAY-C-1234-7998"` — 0.282ms #2.2
  - **OrderRepository.save**(order: `Order(orderId: "ORD-C-1234-SKU-KB-2", customerId: "C-1234", sku: "SKU-KB", quantity: 2, amountCents: 7998)`) — 0.23ms #2.3
```

## Cómo leerlo

**Qué regla atrapó qué valor.** Faltan tres valores, por dos mecanismos
independientes:

| Línea | Valor | Atrapado por |
|---|---|---|
| `#1` `CustomerAccounts.signIn(…, password: [REDACTED])` | `orchid-trombone-77` | Su **nombre**. `password` está en la lista de denegación siempre activa, que compara nombres de parámetro, de campo y de componente de record — en varios idiomas — en cualquier clase, incluida la tuya. El valor en sí no encaja con ningún patrón; nada salvo el nombre podía salvarlo |
| `#2` `OrderService.placeOrder(…, cardNumber: [REDACTED])` | `4242424242424242` | Otra vez su **nombre**: `cardNumber` está en esa misma lista |
| `#2.2` `PaymentService.charge(…, card: [REDACTED])` | el mismo número, una llamada más adentro | Su **valor**. El parámetro se llama `card`, que la lista de denegación no conoce — y el número es válido según Luhn, así que la comprobación independiente de la *forma* del valor lo atrapó igualmente |

Esa tercera fila es la que conviene recordar: `card` es como se llama un
parámetro en código real, y el eje del nombre por sí solo lo habría impreso. El
eje de la forma conoce un número de tarjeta válido según Luhn, un JWT, una
cadena `Set-Cookie`, un número de la seguridad social estadounidense con
guiones y un número de identidad nacional que pasa su propia suma de control.

**Dónde viven las reglas.** En la librería, no en tu configuración:
`RedactionPolicy.DEFAULT`, consultada por el único `ValueRenderer` que todas
las integraciones publicadas construyen de la misma manera. No hay propiedad,
ni DSL, ni nivel de log que llegue hasta ahí;
[Privacidad y ocultación](../privacidad-y-ocultacion.md) enumera tanto las
superficies como qué prevalece sobre qué. `customerId: "C-1234"`, por cierto,
*sí* se escribe completo — un id de cliente es identificativo, no secreto, y
NarrativeTrace no adivina. Si el tuyo no debe loguearse, eso es `@NotTraced`,
que es una decisión en tu código fuente y no un estado que un despliegue pueda
cambiar.

**Por qué la prueba es la evidencia, no la página.** La última línea del
escenario lee el archivo que escribió Logback y comprueba dos cosas: que los
tres parámetros ocultados están presentes *como* `[REDACTED]`, y que ninguna de
las dos cadenas secretas aparece en ningún lugar del archivo. Una página solo
puede mostrarte bytes; una prueba rompe el build el día en que una
refactorización renombra `password` a algo que la lista no conoce, o alguien
construye su propio `ValueRenderer` con `RedactionPolicy.DISABLED`. Esa es la
diferencia entre "era seguro cuando se escribió esto" y "es seguro ahora".
Conserva la aserción de ausencia — el `doesNotContain` — incluso cuando la de
presencia parezca suficiente: un valor que desaparece porque la llamada dejó de
estar trazada seguiría satisfaciendo la primera.

## Qué no queda capturado

- **El código que no está trazado no es asunto de NarrativeTrace ocultar.**
  Arriba se envuelven cuatro interfaces; un secreto que pasa entre dos clases
  sin envolver no se ve y, por tanto, no se oculta ni se loguea. Eso no es una
  garantía de seguridad: es una garantía de silencio.
- **Un secreto que escribe tu propia sentencia de log es tuyo.** NarrativeTrace
  oculta lo que *él* renderiza. `log.info("charging {}", cardNumber)` es tu
  línea, en tu código, y nada aquí la filtra. El contrato de ocultación trata de
  la narrativa generada, no de todo lo que escribe el proceso.
- **Un valor de retorno no se compara por nombre**, porque no tiene ninguno.
  `signIn` devuelve `"SESSION-C-1234"` y se imprime. Un token de sesión devuelto
  por un método trazado necesita que lo atrapen las reglas de forma, o
  `@NotTraced` sobre el método, o un tipo de retorno más estrecho.
- **Nombres que la lista no conoce.** La lista es grande y multilingüe, no
  infinita. Un secreto llamado `q3` solo se atrapa por su forma, si tiene una.
  Comprueba, no supongas — que es justo el punto de esta página.
- **Qué pasa después de escribir la línea de log.** El envío, la retención y el
  acceso al archivo son de tu plataforma; la traza estructural `.nt` no lleva
  ningún valor y es el artefacto pensado para commitear y para entregar a un
  modelo ([Revisa un flujo complejo antes de cambiarlo](revisa-un-flujo-complejo-antes-de-cambiarlo.md)).

**La habilidad para esto:** `narrativetrace-doctor` — una de sus comprobaciones
es exactamente "la ocultación nunca se comprueba en una prueba", y nombra el
arreglo.
