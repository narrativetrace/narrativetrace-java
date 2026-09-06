<!-- source: documentation/first-10-minutes.md blob 4729e8e94bf8 | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Primeiros 10 minutos

[English](../first-10-minutes.md) | Español | **Português** | [简体中文](../zh-CN/前10分钟.md)

Um serviço minúsculo, um teste JUnit, oito passos. Todo comando abaixo foi
executado de verdade contra esta versão do repositório — os caminhos de
arquivo, as pontuações de clareza, a mensagem de asserção e o marcador
`[REDACTED]` são saída real, não ilustrações. As únicas coisas que vão ser
diferentes na sua máquina são a duração (`ms`) e o `trace_name` de duas
palavras, ambos gerados na hora a cada execução.

Java 17+, o plugin do Gradle, JUnit 5. Se você ainda não executou a demo,
`./demo.sh --example ecommerce --no-pause` a partir da raiz do repositório é
ainda mais rápido — esta página é para quando você quer ver isso rodando com
o *seu próprio* código.

## 1. Adicione o plugin

```kotlin
// build.gradle.kts
plugins {
    id("ai.narrativetrace") version "0.2.0"
}
```

Essa é toda a configuração de dependências: o plugin adiciona
`narrativetrace-core`, `-proxy`, `-clarity`, `-diagrams` e `-junit5`, a flag
de compilador `-parameters`, e o mecanismo da JUnit Platform.

## 2. Adicione uma interface de serviço e uma implementação

```java
// src/main/java/com/example/orders/OrderService.java
package com.example.orders;

public interface OrderService {
    String placeOrder(String customerId, String productId, int quantity);
}
```

```java
// src/main/java/com/example/orders/DefaultOrderService.java
package com.example.orders;

public class DefaultOrderService implements OrderService {
    @Override
    public String placeOrder(String customerId, String productId, int quantity) {
        return "ORD-" + customerId + "-" + productId + "-" + quantity;
    }
}
```

Uma interface, porque o proxy JDK usado neste tutorial encapsula
interfaces. A [matriz de integração](escolhendo-uma-integracao.md) tem o
caminho para serviços que não têm uma.

## 3. Adicione um teste JUnit

```java
// src/test/java/com/example/orders/OrderServiceTest.java
package com.example.orders;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        OrderService service = NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, context);

        service.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

`NarrativeTraceExtension` injeta `context`; `NarrativeTraceProxy.trace(...)`
encapsula a implementação real por trás da interface, então toda chamada a
`service` é capturada.

## 4. Execute a suíte

```bash
./gradlew test
```

A saída fica em `build/narrativetrace/` — exatamente a forma que o plugin
promete, nada mais, um arquivo por cenário por formato:

```text
build/narrativetrace/
   |
   +-- traces/OrderServiceTest/customer_places_order.md    narrativa para humanos
   +-- traces/OrderServiceTest/customer_places_order.json  mesmo trace, em JSON canônico
   +-- diagrams/OrderServiceTest/customer_places_order.mmd diagrama de sequência Mermaid
   +-- structural/OrderServiceTest/customer_places_order.nt forma sem valores (baseline do último verde)
   +-- clarity-report.md                                   feedback de nomenclatura para toda a suíte
   +-- clarity-results.json                                mesmas pontuações, para o clarityCheck
```

`OrderServiceTest.customerPlacesOrder` virou `customer_places_order` — o
nome do método de teste, humanizado e convertido em slug. Não há mais nada
para configurar.

> O NarrativeTrace também imprime um resumo ao vivo na saída padrão do
> processo de teste, que o Gradle, por padrão, encaminha apenas para o
> relatório XML/HTML — um simples `./gradlew test` não mostra nada no seu
> terminal, mesmo que os arquivos acima sejam escritos corretamente. Para
> ver isso ao vivo, veja
> [Solução de problemas](solucao-de-problemas.md#não-vejo-nada-no-meu-terminal).

## 5. Abra a narrativa

`build/narrativetrace/traces/OrderServiceTest/customer_places_order.md`:

```markdown
---
type: trace
scenario: Customer places order
entry_point: OrderService.placeOrder
duration_ms: 3.973
trace_id: be4e2e8ea4dfcb18ccc43fbb59223bcc
trace_name: rural ivory heaps
method_count: 1
error_count: 0
---

## Trace: OrderService.placeOrder

**Scenario:** Customer places order
**Duration:** 3.973ms | **Result:** PASSED

### Call Flow

- **OrderService.placeOrder**(customerId: `"C-1234"`, productId: `"SKU-KB"`, quantity: `2`) → `"ORD-C-1234-SKU-KB-2"` — 3.973ms
```

Cada valor no fluxo de chamadas — os valores dos parâmetros, o valor de
retorno — veio da chamada que você realmente fez. Nada foi escrito à mão.

## 6. Renomeie `placeOrder` para `process` e veja a clareza cair

A qualidade dos nomes é medida, não declarada por asserção. Antes da
renomeação, o `clarity-report.md` dizia:

```markdown
| Scenario | Score |
|----------|-------|
| Customer places order | 0.89 |

| Element | Score | Note |
|---------|-------|------|
| `OrderService.placeOrder` | 0.81 | Standard verb 'place' + broad noun 'order' |
```

Renomeie o método (interface, implementação e o ponto de chamada) para
`process` e execute `./gradlew test` novamente:

```markdown
| Scenario | Score |
|----------|-------|
| Customer places order | 0.68 |

| Category | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Method Names | 0.10 | 0.30 | 0.03 |
| Class Names | 0.91 | 0.20 | 0.18 |
| Parameter Names | 0.92 | 0.25 | 0.23 |
| Structural | 1.00 | 0.15 | 0.15 |
| Cohesion | 0.90 | 0.10 | 0.09 |
| **Overall** | **0.68** | | |

| Severity | Category | Element | Suggestion |
|----------|----------|---------|------------|
| HIGH | method-name | `OrderService.process` | Use a domain-specific verb+noun (e.g., calculateTotal, reserveInventory) |
```

Mesma chamada, mesmos valores, tudo igual, menos o nome — a pontuação geral
caiu de 0.89 para 0.68, a dimensão de nome de método sozinha caiu de 0.81
para 0.10, e um problema de severidade HIGH apareceu. Esse é o mecanismo por
trás do `clarityCheck`: defina um limiar na CI e um nome genérico falha o
build em vez de ser publicado silenciosamente. Veja o
[Guia de Clareza](guia-de-clareza.md) para o modelo de pontuação completo.
Renomeie de volta para `placeOrder` (ou para algo ainda mais específico)
antes de continuar.

## 7. Adicione `@NotTraced` e veja a ocultação

```java
public interface OrderService {
    String placeOrder(
        String customerId, String productId, int quantity, @NotTraced String paymentToken);
}
```

Como o `scope` padrão do plugin é `"test"`, os jars do NarrativeTrace —
incluindo o `narrativetrace-api`, onde vive o `@NotTraced` — ficam apenas em
`testImplementation`, não no classpath principal de compilação. Referenciar
a anotação a partir de uma interface em `src/main/java` faz o
`./gradlew test` falhar ao *compilar* com "package
ai.narrativetrace.api.annotation does not exist" antes mesmo de rodar um
teste. Adicione o jar da API em `compileOnly` para corrigir isso (ele não
carrega nenhuma dependência de runtime própria):

```kotlin
dependencies {
    compileOnly("ai.narrativetrace:narrativetrace-api:0.2.0")
}
```

Passe um token no teste (`service.placeOrder("C-1234", "SKU-KB", 2,
"tok_live_51H8x9J")`) e execute novamente. O trace:

```text
- **OrderService.placeOrder**(customerId: `"C-1234"`, productId: `"SKU-KB"`, quantity: `2`, paymentToken: `[REDACTED]`) → `"ORD-C-1234-SKU-KB-2"` — 9.428ms
```

O nome do parâmetro ainda aparece — você consegue ver que um token *foi*
passado — mas o valor dele nunca chega ao disco. Veja
[Privacidade e Ocultação](privacidade-e-ocultacao.md) para o que mais a
ocultação cobre e as duas formas de desativá-la.

## 8. Ative o modo de aprovação e veja o `.received.nt`

```kotlin
narrativeTrace {
    approval.set(true)
}
```

Execute `./gradlew test` sem nenhuma baseline commitada ainda, e o teste que
antes passava falha mesmo assim:

```text
java.lang.AssertionError: No approved narrative for scenario "Customer places order".
Received: src/test/narratives/OrderServiceTest/customer_places_order.received.nt
Review it and approve via the approveNarratives task (or rename it to customer_places_order.approved.nt).
```

`customer_places_order.received.nt` guarda a forma sem valores da chamada:

```text
scenario: Customer places order

- OrderService.placeOrder(customerId, productId, quantity, paymentToken) → value
```

Revise-o, depois promova-o:

```bash
./gradlew approveNarratives
# Approved: src/test/narratives/OrderServiceTest/customer_places_order.approved.nt
```

`./gradlew test` agora passa. A partir daqui, qualquer mudança estrutural
neste cenário — uma nova chamada, uma chamada removida, um tipo de resultado
alterado — falha o build com um diff legível até que alguém revise e
reaprove. Detalhes completos, incluindo o que conta como uma "mudança
estrutural", estão no
[Formato de Trace Estrutural](formato-de-trace-estrutural.md).

## Para onde ir a seguir

| Você quer | Vá para |
|---|---|
| Um caminho de integração diferente do proxy JDK acima | [Escolhendo uma Integração](escolhendo-uma-integracao.md) |
| O contrato de privacidade linha por linha | [Privacidade e Ocultação](privacidade-e-ocultacao.md) |
| Quais arquivos gerados commitar | [O que Commitar](o-que-commitar.md) |
| Algo acima não funcionou como mostrado | [Solução de Problemas](solucao-de-problemas.md) |
| Todo parâmetro de configuração | [Guia de Configuração](guia-de-configuracao.md) |
