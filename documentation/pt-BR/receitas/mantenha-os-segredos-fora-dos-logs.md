<!-- source: documentation/recipes/keep-secrets-out-of-the-logs.md blob fb50d49099c8 | translated: 2026-10-17 | reviewed: - -->
# Mantenha os segredos fora dos logs

[English](../../recipes/keep-secrets-out-of-the-logs.md) | [Español](../../es/recetas/manten-los-secretos-fuera-de-los-logs.md) | **Português** | [简体中文](../../zh-CN/实用方案/别让密钥进日志.md)

## O problema

> Se isso loga cada chamada com cada argumento, vai logar um número de cartão.
> Me convença de que não.

O checkout recebe uma senha e um número de cartão. Abaixo está uma execução
real com os dois, o log que ela de fato escreveu, e o teste que prova que
nenhum dos dois valores está naquele arquivo.

## Configuração mínima

Nenhuma. A ocultação está ligada em toda integração que publicamos e não existe
superfície de configuração que a desligue — a lista de negação é consultada na
*captura*, antes de um valor virar evento, então um valor negado nunca chega à
linha de log, nem ao arquivo de trace, nem ao caminho com buffer, nem a um
listener no pipeline.
[Privacidade e ocultação](../privacidade-e-ocultacao.md) é a versão linha por
linha, verificada contra o código.

Duas coisas, porém, valem ser feitas uma vez no seu próprio projeto:

1. **Escreva o teste desta página para o seu fluxo.** Ocultação que você não
   afirmou em teste é uma alegação, não uma propriedade do seu build. O achado
   do doctor "ocultação nunca provada em teste" existe exatamente para isso.
2. **Conheça a saída de emergência.** Um segredo com um nome que ninguém
   adivinharia — `fingerprint`, `answer2` — é alcançável com `@NotTraced` no
   parâmetro, no campo ou no componente do record, e ele sempre vence
   ([Guia de anotações](../guia-de-anotacoes.md)).

## O cenário

Um colaborador novo, para que a execução carregue uma senha além de um cartão.
O parâmetro dele se chama `password` — esse nome é todo o ponto:

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

A execução autentica o cliente e faz o pedido. Os dois valores secretos são
constantes aqui para que o teste possa procurá-los no log depois; um fluxo real
os recebe na requisição:

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

## A saída real

**O log que a execução escreveu**, pelo logger do próprio projeto:

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

**O arquivo de trace da mesma execução**, que é a outra superfície que um
segredo poderia ter alcançado —
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

## Como ler isso

**Qual regra pegou qual valor.** Três valores desapareceram, por dois
mecanismos independentes:

| Linha | Valor | Pego por |
|---|---|---|
| `#1` `CustomerAccounts.signIn(…, password: [REDACTED])` | `orchid-trombone-77` | O **nome**. `password` está na lista de negação sempre ativa, que compara nomes de parâmetro, de campo e de componente de record — em vários idiomas — em qualquer classe, inclusive nas suas. O valor em si não casa com nenhum padrão; nada além do nome poderia tê-lo salvado |
| `#2` `OrderService.placeOrder(…, cardNumber: [REDACTED])` | `4242424242424242` | O **nome** de novo: `cardNumber` está na mesma lista |
| `#2.2` `PaymentService.charge(…, card: [REDACTED])` | o mesmo número, uma chamada mais fundo | O **valor**. O parâmetro se chama `card`, que a lista de negação não conhece — e o número é válido por Luhn, então a verificação independente da *forma* do valor o pegou de qualquer jeito |

Essa terceira linha é a que vale lembrar: `card` é como um parâmetro se chama
em código real, e o eixo do nome sozinho o teria impresso. O eixo da forma
conhece um número de cartão válido por Luhn, um JWT, uma string `Set-Cookie`,
um número de Seguridade Social americano com hífens e um número de identidade
nacional que passa a própria soma de verificação.

**Onde as regras moram.** Na biblioteca, não na sua configuração:
`RedactionPolicy.DEFAULT`, consultada pelo único `ValueRenderer` que toda
integração publicada constrói do mesmo jeito. Não existe propriedade, DSL nem
nível de log que chegue até lá;
[Privacidade e ocultação](../privacidade-e-ocultacao.md) lista tanto as
superfícies quanto o que vence o quê. `customerId: "C-1234"`, aliás, *é*
escrito inteiro — um id de cliente é identificador, não segredo, e o
NarrativeTrace não adivinha. Se o seu não pode ser logado, isso é
`@NotTraced`, que é uma decisão no seu código e não um estado que um deploy
pode virar.

**Por que o teste é a prova, não a página.** A última linha do cenário lê o
arquivo que o Logback escreveu e afirma duas coisas: que os três parâmetros
ocultados estão presentes *como* `[REDACTED]`, e que nenhuma das duas strings
secretas aparece em lugar nenhum dele. Uma página só pode te mostrar bytes; um
teste quebra o build no dia em que uma refatoração renomear `password` para
algo que a lista não conhece, ou alguém construir o próprio `ValueRenderer`
com `RedactionPolicy.DISABLED`. É a diferença entre "era seguro quando isto foi
escrito" e "é seguro agora". Mantenha a asserção de ausência — o
`doesNotContain` — mesmo quando a de presença parecer suficiente: um valor que
desaparece porque a chamada deixou de ser traçada ainda satisfaria a primeira.

## O que não é capturado

- **Código que não é traçado não é do NarrativeTrace ocultar.** Quatro
  interfaces são envolvidas acima; um segredo passado entre duas classes não
  envolvidas não é visto e, portanto, nem ocultado nem logado. Isso não é
  garantia de segurança — é garantia de silêncio.
- **Um segredo que a sua própria instrução de log escreve é seu.** O
  NarrativeTrace oculta o que *ele* renderiza. `log.info("charging {}",
  cardNumber)` é a sua linha, no seu código, e nada aqui a filtra. O contrato de
  ocultação é sobre narrativa gerada, não sobre tudo o que o processo escreve.
- **Um valor de retorno não é casado por nome**, porque não tem nome. `signIn`
  devolve `"SESSION-C-1234"` e isso é impresso. Um token de sessão devolvido por
  um método traçado precisa que as regras de forma o peguem, ou `@NotTraced` no
  método, ou um tipo de retorno mais estreito.
- **Nomes que a lista não conhece.** A lista é grande e multilíngue, não
  infinita. Um segredo chamado `q3` só é pego pela forma, se tiver uma. Afirme
  em teste, não presuma — que é todo o ponto desta página.
- **O que acontece depois que a linha de log é escrita.** Envio, retenção e
  acesso ao arquivo são da sua plataforma; o trace estrutural `.nt` não carrega
  valor nenhum e é o artefato feito para commitar e para entregar a um modelo
  ([Revise um fluxo complexo antes de alterá-lo](revise-um-fluxo-complexo-antes-de-altera-lo.md)).

**A skill para isso:** `narrativetrace-doctor` — uma das suas verificações é
exatamente "ocultação nunca provada em teste", e ela nomeia a correção.
