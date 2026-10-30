<!-- source: documentation/recipes/add-logging-to-this-service.md blob 46475e9a43a2 | translated: 2026-10-11 | reviewed: - -->
# Adicione logs a este serviço

[English](../../recipes/add-logging-to-this-service.md) | [Español](../../es/recetas/anade-registro-a-este-servicio.md) | **Português** | [简体中文](../../zh-CN/实用方案/给这个服务加日志.md)

## O problema

> Adicione logs a este serviço.

O serviço é um checkout. Ele reserva estoque, cobra um cartão e salva um pedido,
e não contém nenhuma instrução de log:

```java
// src/main/java/com/example/checkout/DefaultOrderService.java
package com.example.checkout;

/** The checkout: reserve the stock, charge the card, save the order — and release on a decline. */
public final class DefaultOrderService implements OrderService {

  private static final int UNIT_PRICE_CENTS = 3999;

  private final InventoryService inventory;
  private final PaymentService payments;
  private final OrderRepository orders;

  public DefaultOrderService(
      InventoryService inventory, PaymentService payments, OrderRepository orders) {
    this.inventory = inventory;
    this.payments = payments;
    this.orders = orders;
  }

  @Override
  public String placeOrder(String customerId, String sku, int quantity, String cardNumber) {
    inventory.reserve(sku, quantity);
    var amountCents = UNIT_PRICE_CENTS * quantity;
    try {
      payments.charge(customerId, amountCents, cardNumber);
    } catch (PaymentDeclinedException declined) {
      inventory.release(sku, quantity);
      throw declined;
    }
    var orderId = "ORD-" + customerId + "-" + sku + "-" + quantity;
    orders.save(new Order(orderId, customerId, sku, quantity, amountCents));
    return orderId;
  }
}
```

O que você quer de verdade é aquilo para que o log é um meio: o que esta chamada
fez, com quais valores fez, no log que seu time já lê. Por isso a resposta abaixo
também não escreve nenhuma instrução de log.

## Configuração mínima

Além da [instalação](../guia-de-instalacao.md) — `narrativetrace-core`, o proxy
JDK e `-parameters` para que os nomes dos parâmetros sobrevivam à compilação —
esta página precisa de duas dependências de runtime e de um arquivo de
configuração:

```kotlin
// build.gradle.kts
dependencies {
    runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.5.5")   // se liga ao pipeline
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")          // o backend SLF4J que ele precisa
}
```

`narrativetrace-slf4j` se liga sozinho no instante em que está no classpath; nada
no código abaixo o menciona. O Logback decide para onde vão seus eventos:

```xml
<!-- src/main/resources/logback.xml -->
<configuration>
    <!-- The pattern deliberately carries no %d{HH:mm:ss.SSS}: every recipe page embeds a real
         run's log output byte-for-byte, and a clock in it would differ on every run. Put the
         timestamp back in your own project — nothing else here changes. -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%-5level [%X{traceName}] [%logger] - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="narrativetrace" level="TRACE" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

## O cenário

Cada colaborador é envolvido uma vez, onde é construído — o checkout por último,
para que as chamadas que passam por ele sejam capturadas antes de chegarem à
implementação real. Nada mais muda:

```java
// src/test/java/com/example/checkout/AddLoggingRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/add-logging-to-this-service.md: the checkout, with no log statement
 * anywhere in it, logs every call through the project's own SLF4J logger once its collaborators are
 * wrapped (rule 8, docs as tests).
 *
 * <p><b>@llmNote</b> The page's output block is the log file Logback writes, not a rendering this
 * test produces — that is the whole claim of the page, so the evidence has to come from the logger.
 * The Gradle task {@code recipeAddLoggingLog} runs this class alone and names that file.
 */
@ExtendWith(NarrativeTraceExtension.class)
class AddLoggingRecipeTest {

  /**
   * A fixed W3C traceparent, adopted so this page's embedded output names the same trace every run
   * — the same mechanism a servlet filter uses for an inbound request header. A real run generates
   * a random trace id every time, and the name in the log lines is derived from it.
   */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  @Test
  void a_customer_checks_out(NarrativeContext context) {
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
    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(3);
    assertThat(repository.saved()).hasSize(1);
  }
}
```

É um teste JUnit 5 porque é ali que um fluxo assim já é exercitado; as mesmas
quatro linhas de ligação funcionam em um `main`, em uma fábrica de beans do
Spring ou em um servlet. `NarrativeTraceExtension` é o que escreve os artefatos
de trace por teste que as outras receitas leem.

## A saída real

O log que a execução acima escreveu, pelo próprio logger do projeto:

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

## Como ler

- `→` abre uma chamada e `←` fecha a aberta mais recentemente, então a indentação
  é dispensável: o primeiro `→ OrderService.placeOrder` e o último
  `← returned: "ORD-C-1234-SKU-KB-2"` são a mesma chamada, e tudo entre eles
  aconteceu dentro dela. Nos artefatos de trace essas mesmas quatro chamadas
  carregam os identificadores de span `#1`, `#1.1`, `#1.2` e `#1.3` —
  [Revise um fluxo complexo antes de alterá-lo](revise-um-fluxo-complexo-antes-de-altera-lo.md)
  as lê desse jeito.
- Cada linha carrega os *nomes* dos parâmetros que o método declara, o que a
  transforma em uma frase em vez de uma tupla:
  `reserve(sku: "SKU-KB", quantity: 2)` não precisa de legenda.
- `← completed` é um método `void` retornando; `← returned: …` carrega o valor.
  `charge` devolvendo `"PAY-C-1234-7998"` é o identificador do próprio gateway, e
  `amountCents: 7998` é de onde vem o preço de dois teclados.
- Dois valores desapareceram. `cardNumber: [REDACTED]` foi escondido pelo *nome*
  — a lista de negação sempre ativa, sem anotação e sem configuração.
  `card: [REDACTED]`, uma linha abaixo, foi escondido pelo *valor*: o parâmetro
  se chama `card`, que a lista não conhece, e o número que ele guardava é válido
  por Luhn, então a regra de forma do valor o pegou de todo modo. Veja
  [Privacidade e ocultação](../privacidade-e-ocultacao.md).
- `[solid wand romps]` é o nome próprio do trace no MDC, então cada linha de um
  checkout pode ser extraída com grep de um log movimentado.

A conclusão que isso sustenta: a narrativa veio dos nomes dos métodos, dos nomes
dos parâmetros e dos valores, e nenhuma instrução de log foi escrita para
obtê-la.

## O que não é capturado

- **Só o código envolvido é registrado.** Quatro interfaces foram envolvidas
  acima, então quatro chamadas aparecem. Uma chamada entre duas classes não
  envolvidas — ou um método privado dentro de `DefaultOrderService` — é invisível
  aqui. Se envolver cada colaborador à mão não é o que você quer, o agente Java
  ou a integração com Spring se ligam sem tocar no código de construção:
  [Escolhendo uma integração](../escolhendo-uma-integracao.md).
- **Um valor que casa com as regras de ocultação é substituído antes de ser
  escrito,** inclusive nesta linha de log. Isso não é configurável para menos, e
  significa que um parâmetro oculto não pode ser recuperado do log.
- **O nível do próprio logger continua valendo.** Estas linhas são `TRACE` no
  logger `narrativetrace`; em `INFO` o arquivo fica vazio e nada avisa você.
- **Os tempos não estão nas linhas de log.** A duração de cada chamada está nos
  artefatos de trace, não aqui.

**A skill para isto:** `add-narrative-tracing` — ela instala as duas
dependências, envolve a primeira classe e confere a ligação com
`narrativetrace-doctor`.
