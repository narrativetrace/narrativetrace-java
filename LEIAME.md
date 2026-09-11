<!-- source: README.md blob ae3b90f86e52 | translated: 2026-09-10 | reviewed: - -->
# NarrativeTrace

[English](README.md) | [Español](LEAME.md) | **Português** | [简体中文](自述文件.md)

> O código é o log.

NarrativeTrace™ transforma código Java em execução em uma narrativa de execução
legível, construída a partir dos nomes de método, classe e parâmetro que você
já escreveu. Sem linhas `logger.info(...)`. Se o trace estiver ilegível, seu
código precisa de refatoração — não de mais instruções de log.

Com pressa: [execute a demo](#experimente-em-um-único-comando) → [adicione a
um teste](#adicione-a-um-teste) → [escolha sua
integração](#escolha-sua-integração).

## O problema

Metade deste método é ruído de logging:

```java
public OrderResult placeOrder(String customerId, String productId, int quantity) {
    logger.info("Placing order for customer {} product {} quantity {}", customerId, productId, quantity);

    var inventory = inventoryService.reserve(productId, quantity);
    logger.debug("Reserved inventory: {}", inventory);

    var payment = paymentService.charge(customerId, inventory.total());
    logger.info("Payment processed: {}", payment.transactionId());

    var result = new OrderResult(payment.transactionId(), inventory.items());
    logger.info("Order placed successfully: {}", result);
    return result;
}
```

A lógica de negócio são três linhas. O logging, outras quatro. Cada
desenvolvedor escreve esses logs de um jeito diferente — mensagens diferentes,
níveis diferentes, valores incluídos diferentes. O resultado é inconsistente,
verboso e emaranhado com o código que ele descreve.

O NarrativeTrace elimina tudo isso por completo:

```java
public OrderResult placeOrder(String customerId, String productId, int quantity) {
    var inventory = inventoryService.reserve(productId, quantity);
    var payment = paymentService.charge(customerId, inventory.total());
    return new OrderResult(payment.transactionId(), inventory.items());
}
```

Lógica de negócio pura. O trace é gerado a partir dos nomes de métodos, dos
nomes de parâmetros e dos valores de retorno — a informação que já estava ali.

## Deriva código-log

As linhas de log são a única parte do código sem verificação do compilador
e, na prática, sem cobertura de testes — então elas silenciosamente deixam
de ser verdadeiras conforme o código muda. Uma renomeação deixa a mensagem
descrevendo o nome antigo; um passo adicionado simplesmente nunca é
mencionado; uma mudança de unidade (centavos → euros) faz `total` descrever
um número diferente. Nada detecta isso: o texto do log quase nunca é
verificado por uma asserção, e quando é, a asserção é frágil e é a primeira
coisa removida. Um log obsoleto é peor do que nenhum — em um incidente ele é
lido como evidência do que aconteceu, quando é uma frase que alguém escreveu
uma vez sobre um código que já mudou.

> **Deriva código-log, eliminada por construção.** Uma linha de log é uma
> afirmação sobre o código, escrita uma vez e nunca mais verificada. Um trace
> narrativo é derivado da execução — então não há nada para desviar.

Para ser preciso: um template de narração (`@Narrated`) ainda é uma string
escrita à mão, e um parâmetro renomeado pode quebrar seu marcador — é
exatamente por isso que ele é a exceção aqui, não o caminho padrão (veja o
[Guia de anotações](documentation/pt-BR/guia-de-anotacoes.md)). Tudo o mais
em um trace — as chamadas, os argumentos e os resultados — é derivado, nunca
escrito, então não há nada ali para ficar obsoleto. E como um trace é
estrutural, uma mudança real de comportamento se torna algo que um revisor
pode comparar, não uma frase que silenciosamente parou de descrever o
código — ative o modo de aprovação (mais abaixo) e esse diff falha o build
em vez de passar despercebido.

## Como é a saída

Execute seu código e obtenha traces de execução como este:

```
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-MECHANICAL-KB", quantity: 2)
  CustomerService.findCustomer(customerId: "C-1234") -> Customer[id=C-1234, name=Alice Johnson, tier=GOLD]
  ProductCatalogService.lookupPrice(productId: "SKU-MECHANICAL-KB") -> 89.99
  InventoryService.reserve(productId: "SKU-MECHANICAL-KB", quantity: 2) -> Reservation[productId=SKU-MECHANICAL-KB, quantity=2]
  PaymentService.charge(customerId: "C-1234", amount: 179.98) -> PaymentConfirmation[transactionId=TXN-00001, amount=179.98]
-> OrderResult[orderId=ORD-00001, transactionId=TXN-00001, totalCharged=179.98, itemCount=2]
```

**Quando algo dá errado**, o trace deixa o bug visível:

```
OrderService.placeOrder(customerId: "C-BROKE", productId: "SKU-MOUSE-PAD", quantity: 3)
  CustomerService.findCustomer(customerId: "C-BROKE") -> Customer[id=C-BROKE, name=Charlie Broke, tier=STANDARD]
  ProductCatalogService.lookupPrice(productId: "SKU-MOUSE-PAD") -> 24.99
  InventoryService.reserve(productId: "SKU-MOUSE-PAD", quantity: 3) -> Reservation[productId=SKU-MOUSE-PAD, quantity=3]
  PaymentService.charge(customerId: "C-BROKE", amount: 74.97) !! PaymentDeclinedException: Payment declined for customer C-BROKE
!! PaymentDeclinedException: Payment declined for customer C-BROKE
```

`InventoryService.reserve` foi chamado, mas `InventoryService.release` não
aparece em nenhuma parte do trace. O bug está à vista.

**O trace vale tanto quanto seus nomes.** O mesmo fluxo do Minecraft "o
jogador entra no mundo", traçado duas vezes — uma com nomes de domínio, outra
com nomes genéricos.

**Nomes de domínio:**

```
WorldServer.playerJoined(playerName: "Steve")
  WorldGenerator.generateChunk(x: 0, z: 0) -> Chunk(x: 0, z: 0, biome: "plains")
  PlayerInventory.addItem(item: OAK_LOG, quantity: 4) -> true
  CraftingTable.craft(recipe: WOODEN_PICKAXE) -> WOODEN_PICKAXE
  CreatureSpawner.spawnHostile(type: ZOMBIE, x: 10, y: 64, z: 20) -> Creature(type: ZOMBIE, ...)
```

**Nomes genéricos:**

```
GameManager.handle(input: "Steve")
  DataProcessor.process(a: 0, b: 0) -> DataResult(a: 0, b: 0, tag: "plains")
  StateManager.update(type: 1, count: 4) -> true
  ThingFactory.create(type: 1) -> 1
  EntityHandler.execute(kind: 1, a: 10, b: 64, c: 20) -> Entity(kind: 1, ...)
```

Mesmo grafo de chamadas. Mesmos valores de retorno. Só os nomes mudam. Se o
seu código não consegue contar a própria história, ele precisa de
refatoração — e é por isso que o NarrativeTrace também [pontua seus
nomes](#além-do-primeiro-trace).

### Por que isso importa para o desenvolvimento assistido por IA

Cada linha `logger.info(...)` é uma linha que as ferramentas de IA para
programação — Claude Code, Copilot, Cursor — precisam analisar, gastar tokens
nela e contornar ao raciocinar. Em uma classe de serviço típica, o logging é
30–50% das linhas. Remova-as e o mesmo orçamento de tokens cobre mais do seu
código real, o modelo vê o que o código faz em vez de como ele loga o que faz,
e os pull requests mostram mudanças de lógica de negócio em vez de mudanças
misturadas de lógica e logging.

Há uma segunda metade nisso: cada teste também emite um **trace estrutural
seguro para IA** (`structural/<Classe>/<cenário>.nt`) — a estrutura de
chamadas com todos os valores de tempo de execução removidos, seguro para
entregar a uma ferramenta de IA ou commitar no repositório. Veja o [formato de
trace estrutural](documentation/pt-BR/formato-de-trace-estrutural.md).

### Como isso se compara

| Em vez de | NarrativeTrace |
|---|---|
| **Logging estruturado** (SLF4J + MDC) — você escreve as instruções de log | Gera-as a partir da estrutura do código; quando o tracing de requisições está ativo, também preenche campos de correlação como `traceId` e um `traceName` legível como `bold elk soars`. Ele *usa* o SLF4J em vez de substituí-lo |
| **Tracing distribuído** (OpenTelemetry, Jaeger) — spans entre serviços, sem valores de parâmetros | Árvores de chamadas em nível de método com valores de parâmetros e retornos. O `narrativetrace-opentelemetry` une os dois mundos: exporta as árvores do NarrativeTrace como spans do OTel com atributos `narrative.*` |
| **Logging AOP** (Spring AOP, AspectJ) — linhas planas e mecânicas de entrada/saída | Árvores de chamadas aninhadas, além de uma pontuação de clareza sobre os nomes que as produziram |

O NarrativeTrace não substitui seus alertas de produção nem seus mapas de
topologia de serviços. Ele te dá o que nenhum dos dois oferece: uma narrativa
de execução legível por humanos que também funciona como diagnóstico de
qualidade de código.

### Não substitui seu framework de logging

O NarrativeTrace não mexe no seu framework de logging. Ele não inclui nenhum
appender, nenhum encoder, nenhum sink — sua configuração do Logback ou
Log4j, seus formatos e destinos, continuam funcionando exatamente como hoje.

O que ele substitui é a narração que você escreve manualmente: as linhas
`log.info("Placing order {} for customer {}", ...)` que descrevem o que um
método está fazendo. Um método instrumentado produz essa narrativa
automaticamente, a partir da própria assinatura do método e do seu valor de
retorno.

Isso não é uma metáfora. O caminho síncrono e durável do pipeline de eventos
*é* um listener do SLF4J (`narrativetrace-slf4j`) — a narrativa gerada chega
aos seus appenders pela mesma chamada SLF4J que um `log.info(...)` escrito
manualmente usaria. As instruções de log manuais continuam funcionando lado a
lado: mesmo logger, mesmos streams, antes, durante ou depois de um método
instrumentado. Misture os dois livremente enquanto você migra — veja
[Convivendo com logging
tradicional](documentation/pt-BR/guia-de-configuracao.md#convivendo-com-logging-tradicional)
para um exemplo completo.

## Experimente em um único comando

Sem projeto, sem wiring — o repositório traz um lançador de demos que executa
as aplicações de exemplo e as narra ao vivo (a primeira execução compila o
exemplo):

```bash
./demo.sh --list                                  # ecommerce, clarity, minecraft, library
./demo.sh --example ecommerce --no-pause          # o grafo de serviços Spring emblemático
./demo.sh --example ecommerce --classic           # a mesma execução como linhas de log comuns, com timestamp
./demo.sh --example minecraft --no-pause          # a comparação de nomes de acima, de verdade
./demo.sh --example ecommerce --lang es           # a mesma execução renderizada de novo através do glossário de domínio
```

Sem `--no-pause` a demo para depois de cada cenário — `[Enter]` continua, `q`
sai — e cada cenário começa com uma nota sobre o wiring daquele trace
específico. Veja [os exemplos](narrativetrace-examples/) para saber o que
cada um ensina.

## Adicione a um teste

O caminho mais curto de "biblioteca interessante" até "vi um trace útil do meu
próprio código" é o plugin de Gradle mais um teste de JUnit 5. Java 17+
([matriz de compatibilidade
completa](documentation/pt-BR/guia-de-instalacao.md#compatibilidade)).

**1. Aplique o plugin.** Ele adiciona as dependências, define a flag do
compilador `-parameters` (sem ela os traces mostram `arg0`, `arg1`), configura
a JVM de testes e fornece o engine do Jupiter:

```kotlin
// build.gradle.kts
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

**2. Trace um serviço em um teste:**

```java
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

**3. Execute a suíte:**

```bash
./gradlew test
```

**4. Abra a narrativa** — o nome do método de teste virou o nome do cenário:

```text
build/narrativetrace/traces/OrderServiceTest/customer_places_order.md
```

Cada teste da suíte grava seu próprio conjunto de artefatos:

```text
build/narrativetrace/
├── traces/<ClasseDeTeste>/<cenário>.md        a narrativa para humanos
├── traces/<ClasseDeTeste>/<cenário>.json      o mesmo trace como JSON canônico
├── diagrams/<ClasseDeTeste>/<cenário>.mmd     diagrama de sequência Mermaid
├── structural/<ClasseDeTeste>/<cenário>.nt    forma do comportamento sem valores (segura para IA)
└── clarity-report.md                          feedback de nomes de toda a suíte
```

Sem o plugin, a mesma configuração são quatro linhas de Gradle e duas
dependências — veja o [Guia de
instalação](documentation/pt-BR/guia-de-instalacao.md).

Quer continuar a partir daqui — renomear o método e ver a pontuação de
clareza cair, adicionar `@NotTraced` e ver um valor oculto, ativar o modo de
aprovação e ver um `.received.nt`? → [Primeiros 10
minutos](documentation/pt-BR/primeiros-10-minutos.md) percorre tudo isso com
saída real, executada de verdade.

### Qual artefato responde a qual pergunta

Um teste escreve vários arquivos. Abra o que responde à sua pergunta:

| Sua pergunta | Leia |
|---|---|
| O que chamou o quê, e em que ordem? | `structural/…/<scenario>.nt` — estrutura de chamadas, sem valores |
| Quais foram os valores reais? | `traces/…/<scenario>.json` — cada chamada, cada valor capturado |
| O que aconteceu, para uma pessoa? | `traces/…/<scenario>.md` — a narrativa |
| Qual arquivo contém este cenário? | `manifest.json` — cenário → arquivo, uma linha por invocação |

A narrativa em Markdown dobra uma sequência de iterações de mesma forma na
primeira completa mais uma linha `×2 more: #2 sku=…`, de modo que as
repetições ficam nomeadas em vez de exibidas. O JSON mantém todas as
iterações aconteça o que acontecer, e `narrativetrace.unfolded=true`
também renderiza todas elas em Markdown.

### Gradle ou Maven?

Os jars de runtime são artefatos Maven comuns.
`ai.narrativetrace:narrativetrace-core:0.2.1` e todos os módulos ao lado dele
se resolvem e funcionam exatamente da mesma forma a partir de um build Maven;
nada na própria biblioteca é específico do Gradle. O que *é* específico do
Gradle é o plugin acima — uma conveniência que conecta a flag do compilador,
as dependências e a JVM de testes para você.

Ou seja: os mesmos jars, com qualquer ferramenta de build — mas a experiência
de configuração documentada e de primeira classe hoje é a do Gradle. Um
exemplo Maven completo já respalda essa afirmação:
[`narrativetrace-maven-example`](narrativetrace-maven-example) é um
`pom.xml` autônomo que consome artefatos instalados localmente, com o
Surefire conectado para o diretório de saída e a extensão JUnit 5 registrada
à maneira do Maven — o [Guia de Maven](documentation/maven-guide.md) (em
inglês) percorre tudo do início ao fim, incluindo a única diferença que
importa: sem plugin não há flag do compilador automática nem a tarefa
`approveNarratives`, e o que fazer em cada caso. Ambos resolvem os mesmos
artefatos publicados no Maven Central.

## Escolha sua integração

Os testes são por onde a maioria começa. É para onde você vai a seguir:

| O que você quer | Comece com |
|---|---|
| Traces nos testes, com o mínimo de wiring | Plugin de Gradle + `narrativetrace-junit5` |
| O mesmo, em JUnit 4 | `narrativetrace-junit4` |
| Escolher exatamente o que é envolvido, em Java puro | `narrativetrace-proxy` (proxy dinâmico da JDK) |
| Beans do Spring traçados automaticamente | `narrativetrace-spring` |
| Ciclo de vida de requisições HTTP do Spring em produção | `narrativetrace-spring-web` (conecta o `narrativetrace-servlet`) |
| Qualquer app servlet, sem Spring | `narrativetrace-servlet` |
| Beans e requisições do Micronaut | `narrativetrace-micronaut` + `narrativetrace-micronaut-http` |
| **Zero mudanças de código** — um app que você não pode ou não quer modificar | `narrativetrace-agent` (agente java) |
| Visibilidade assíncrona entre threads (`@Async`, Reactor, executors) | `narrativetrace-micrometer`, ou `ContextSnapshot` manualmente |
| Traces no seu stream de log de produção | `narrativetrace-slf4j` |
| Spans do OpenTelemetry | `narrativetrace-opentelemetry` |

**Zero mudanças de código** merece detalhamento, porque não exige nenhuma
mudança no build — o agente java reescreve as classes conforme elas são
carregadas:

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.* -jar your-app.jar
```

Todo método não privado de toda classe sob os pacotes indicados recebe,
compilada em seu corpo, a captura de entrada/saída; nada fora deles é tocado.
A comparação de pacotes é somente de inclusão e respeita o delimitador, então
`com.acme` nunca captura `com.acmeExtra`. Em um servidor de aplicações sem
classpath alcançável, use o jar `-standalone` e `loggingJars=`; o agente
deliberadamente não traz nenhum provedor SLF4J próprio. Receitas para todos
os caminhos acima estão no [Guia de
instalação](documentation/pt-BR/guia-de-instalacao.md); um diagrama de
decisão para escolher uma está em [Escolhendo uma
integração](documentation/pt-BR/escolhendo-uma-integracao.md).

## Módulos

| Módulo | Você precisa dele quando... |
|--------|------------------------------|
| `narrativetrace-api` | O contrato somente de compilação: anotações, modelo de eventos e SPIs. Normalmente você o obtém de forma transitiva com `core` — dependa dele sozinho se você é autor de uma biblioteca que publica contra o contrato. |
| `narrativetrace-core` | Sempre necessário. Contexto de execução, pipeline, renderizadores, configuração e exportação. Zero dependências em tempo de execução. |
| `narrativetrace-proxy` | Usa tracing com proxy JDK (o mais comum). |
| `narrativetrace-junit5` | Auto-tracing em testes JUnit 5. |
| `narrativetrace-junit4` | Auto-tracing em testes JUnit 4. |
| `narrativetrace-spring` | Auto-encapsulamento de beans do Spring. |
| `narrativetrace-micronaut` | Auto-encapsulamento de beans do Micronaut. |
| `narrativetrace-slf4j` | Encaminha traces através de SLF4J/Logback. |
| `narrativetrace-clarity` | Analisa a qualidade dos nomes de métodos/parâmetros. |
| `narrativetrace-glossary` | Coleta um glossário de domínio (linguagem ubíqua) a partir dos traces. |
| `narrativetrace-diagrams` | Gera diagramas de sequência Mermaid/PlantUML. |
| `narrativetrace-opentelemetry` | Exporta árvores de trace como spans do OpenTelemetry, ou cria spans ao vivo via decorator. |
| `narrativetrace-micrometer` | Propagação de trace entre threads via context-propagation do Micrometer. |
| `narrativetrace-agent` | Tracing em nível de bytecode sem wiring de proxy. |
| `narrativetrace-servlet` | Ciclo de vida de requisições em produção em qualquer app servlet (sem exigir Spring). |
| `narrativetrace-spring-web` | `@Configuration` do Spring para o `narrativetrace-servlet` — conecta automaticamente o filtro com `ObjectProvider`. |
| `narrativetrace-micronaut-http` | Filtro HTTP reativo do Micronaut para o ciclo de vida do trace por requisição. |
| `narrativetrace-examples` | Apps de referência (somente código-fonte): e-commerce, comparação de nomes no Minecraft, empréstimo de biblioteca, demo de clareza de reservas de hotel, um WAR EJB 4 (Jakarta EE) de sinistros de seguro no WildFly, traçado sem código pelo agente java (`ejb4`, testes baseados em Docker), além de utilitários compartilhados em `common`. |

**Ponto de partida típico:** o plugin de Gradle, que instala `core` +
`proxy` + `junit5` para você.

## Além do primeiro trace

Três coisas que a suíte te dá assim que os traces estão funcionando, cada uma
com um guia por trás:

**Pontuação de clareza.** Se o trace *é* o código, a qualidade do trace *é* a
qualidade do código. O `clarity-report.md` pontua cada nome de método, classe
e parâmetro que rodou: nomes genéricos como `processData` ou `handleRequest`
pontuam baixo, nomes específicos do domínio como `reserveInventory` ou
`customerId` pontuam alto, e o `clarityCheck` pode fazer o build falhar
segundo o limiar que você definir. Ainda é experimental.
→ [Guia de clareza](documentation/pt-BR/guia-de-clareza.md)

**Testes de aprovação narrativa.** Cada execução compara a *estrutura* de
cada cenário com a última execução verde e encerra a suíte com uma linha:

```
NarrativeTrace — Suite complete
  5 scenarios recorded
  Clarity: 100% high | 0% moderate | 0% low
  Reports: build/narrativetrace
  Since last green: 4 scenarios unchanged · 1 changed: "Customer places order" (+1 call InventoryService.release)
```

Ative o modo de aprovação (`narrativetrace.approval=true`, ou
`approval.set(true)` no DSL do plugin) e essa estrutura se torna um contrato
commitado: um teste que passa mas cuja forma difere da sua baseline
`src/test/narratives/<Classe>/<cenário>.approved.nt` falha com um diff
legível, a nova forma fica ao lado como `.received.nt`, e `./gradlew
approveNarratives` promove o que você revisou. Uma mudança de comportamento —
inclusive uma que um agente de IA tenha deslizado em uma refatoração —
precisa ser revisada e aprovada, não basta compilar. As baselines são livres
de valores, então são estáveis entre execuções e seguras para commitar.
→ [Formato de trace estrutural](documentation/pt-BR/formato-de-trace-estrutural.md)

**Concorrência.** O paralelismo fork-join (`ForkGroup`), o trabalho
fire-and-forget (`FireAndForgetGroup`) e os retornos de `CompletableFuture`
são cidadãos de primeira classe na árvore de traces, renderizados com
marcadores `⑂ fork` / `⑃ join`, nomes de threads e análise de tempo de
espera. O `captureTrace()` é deliberadamente restrito à thread — capture na
thread que registra, ou enxerte o trabalho no trace pai com
`ContextSnapshot.wrap()`.
→ [Referência completa: Concorrência](documentation/llms-full.md#concurrency)
(em inglês)

## Privacidade e segurança

Esta biblioteca roda dentro do seu processo e escreve arquivos que sua
equipe vai compartilhar. O que isso significa, em uma única tela:

| Garantia | Como ela se sustenta |
|---|---|
| **A ocultação é incondicional** | `@NotTraced` e a lista de negação baseada em nome (`password`, `token`, `cvv`, `ssn`, …) se aplicam a todo caminho de saída publicado — traces de teste, artefatos de CI, logs de container, narração do agente. Nenhum estágio, flag ou propriedade os desativa (decisão do proprietário, 2026-08-16). Isso sobrevive um container de profundidade (`Optional`, `Future`, `AtomicReference`, `Map.Entry`); `@NotTraced` prevalece sobre um `toString()` personalizado, e um template `{param.path}` que nomeia um membro oculto resolve para `[REDACTED]`. |
| **O artefato seguro para IA não guarda nenhum valor** | O arquivo estrutural `.nt` contém apenas nomes, hierarquia e tipos de resultado. Nada para ocultar, zero superfície de prompt injection — e isso é um teste de propriedade, não uma política. |
| **Falhas de tracing não podem derrubar sua aplicação** | O registro é isolado de exceções em todos os caminhos, e os dois consumidores do pipeline engolem seus próprios erros. Um `toString()` que lança exceção, um buffer cheio ou um appender quebrado nunca mudam o que seu método retorna ou lança. |
| **O uso de recursos é limitado** | O caminho de análise com buffer é um anel de tamanho fixo (65.536 slots por padrão, `narrativetrace.buffer.capacity`) que descarta em vez de bloquear — e avisa disso: uma captura que perdeu eventos imprime a contagem no seu próprio rodapé. A renderização de valores é limitada em tamanho de string, tamanho de coleção, largura de objeto e profundidade de aninhamento. |
| **A saída não pode ser forjada** | Os valores renderizados são escapados para que não possam injetar linhas de log, quebrar o Markdown ou corromper a sintaxe dos diagramas. |
| **Empilhar com outros wrappers é seguro** | Proxies AOP, bibliotecas de contrato, interceptadores de container e outros agentes podem envolver o mesmo método que o NarrativeTrace. A ordem de aninhamento pode mudar como o trace *é lido* — nunca o que ele *retorna ou lança*: o registro é isolado de exceções em todos os caminhos e nunca substitui um resultado ou exceção. O objetivo é um frame de trace por travessia de fronteira de negócio; maquinário (métodos bridge, stubs de view do container, código gerado por outra biblioteca) não é o alvo. |

Dois limites honestos. Primeiro, a única forma de um valor escapar da
ocultação é o código da aplicação construir seu próprio `ValueRenderer` com
`RedactionPolicy.DISABLED` — um ato deliberado e revisável no seu próprio
código-fonte, nunca um estado de configuração. Segundo, a captura lê *campos*
por reflexão e nunca chama seus getters, mas invoca sim um `toString()`
personalizado, um método `@NarrativeSummary`, os acessores de componentes de
`record` e os caminhos de propriedade nomeados em templates
`@Narrated`/`@OnError`; mantenha-os puros, como você faria para um depurador.
Hoje o escopo é somente de inclusão — `packages=` para o agente, pacotes base
para Spring e Micronaut — ainda sem lista de exclusão.

→ [Privacidade e ocultação](documentation/pt-BR/privacidade-e-ocultacao.md)
para o contrato de ocultação linha a linha, verificado contra o código,
[Guia do ciclo de vida](documentation/pt-BR/guia-do-ciclo-de-vida.md) para a
postura de privacidade em cada estágio, [Guia de
anotações](documentation/pt-BR/guia-de-anotacoes.md) para o contrato de
pureza completo, e [Escolhendo uma integração § Empilhar com outros
wrappers](documentation/pt-BR/escolhendo-uma-integracao.md#empilhar-com-outros-wrappers)
para o detalhe por mecanismo da convivência com proxies AOP, bibliotecas de
contrato e outros agentes.

## Desempenho

Colocamos esforço de verdade nos caminhos quentes, e não vamos afirmar
"sobrecarga zero" — traçar faz trabalho, e trabalho custa algo. O que
medimos (JMH, JDK 17, `-prof gc`, uma operação de benchmark = uma chamada):

- **Tracing desligado ou contexto inativo:** o proxy JDK adiciona ~12–26 ns
  por chamada sobre uma invocação direta (que mede ~9–12 ns neste harness) e
  24 B/op — um único `Object[]` para os argumentos. Um portão `isActive()`
  pula toda a captura, renderização e reflexão quando o tracing está
  desabilitado.
- **O caminho inativo do agente de bytecode não aloca nada.** `agent_OFF`
  mede **56 B/op** — o mesmo que uma chamada direta — a ~37–44 ns por
  operação, o que inclui a troca de contexto que o benchmark realiza dentro
  da própria medição. Um método instrumentado lê um `isActive()` estático
  antes de preparar qualquer coisa: com o tracing desligado, nenhum argumento
  é encaixotado (boxing), nenhum array é construído, nenhuma chamada é feita.
- **Tracing ativo:** uma chamada de proxy traçada com captura de parâmetros e
  renderização de valores mede **0,6–1,7 µs** e aloca **~1,0–1,5 kB/op**,
  dependendo das anotações. Capturar um trace de um único nó e reiniciar o
  contexto custa 1,3–2,3 µs e 2,8–3,1 kB.

Esses números vêm dos [benchmarks JMH](narrativetrace-benchmarks/) executados
em um container compartilhado, onde a alocação se reproduz byte a byte e os
números de nanossegundos variam com a carga da máquina — por isso os dois são
registrados como *tetos* em
[`baseline.txt`](narrativetrace-benchmarks/baseline.txt) e
[`allocation-baseline.txt`](narrativetrace-benchmarks/allocation-baseline.txt),
e as regressões permanecem visíveis entre commits. Eles são mais altos que os
números que este README trazia até 2026-08-31, medidos contra um pipeline que
fazia menos: agora toda entrada e saída publica um evento através de um
buffer em anel para o armazenamento retido, e todo span carrega identidade de
trace W3C. O desempenho é uma preocupação contínua, não um problema
resolvido. Para loops extremamente quentes, use `TracingLevel.OFF` ou reduza
o escopo traçado.

## O que é gratuito e o que é Pro

**Gratuito** é tudo o que está neste repositório — de código disponível
(source-available) sob a BSL 1.1, gratuito em produção, convertendo-se para
Apache 2.0 quatro anos após cada lançamento: todo o runtime, traces por teste
em todos os formatos, o artefato estrutural `.nt` com relatório de deltas e
modo de aprovação, a pontuação de clareza, o glossário de domínio e as visões
traduzidas de traces, e todas as integrações da tabela acima. O jar de
contrato `narrativetrace-api` é Apache 2.0 sem ressalvas.

**Pro** é inteligência *entre* execuções e repositórios: resumos de fluxo
agregados e grafos de dependência em tempo de execução, diffs de migração e
semânticos, diagramas de sequência agregados, bots de revisão de PR e
análise de drift, tendência histórica de clareza, ferramentas e servidor MCP
para agentes de IA, e a suíte de auditoria e conformidade (`@AuditEvent`,
motor de políticas, mascaramento de campos, rastreabilidade de controles).
Nem tudo está publicado hoje. O [Guia de
funcionalidades](documentation/pt-BR/guia-de-funcionalidades.md) é a tabela
de status autoritativa: ela rotula cada funcionalidade como Open, Gratuito,
Pro, Em desenvolvimento ou Planejado, e cita o código por trás de cada linha
publicada.

## Documentação

**[documentation/](documentation/LEIAME.md)** indexa todos os documentos
deste repositório, tanto os guias em inglês quanto suas traduções para o
espanhol e o chinês. A documentação completa publicada — incluindo páginas
sem equivalente aqui — está em
**[narrativetrace.ai/docs](https://narrativetrace.ai/docs.html)** (em
inglês). Para agentes de IA:
[`documentation/llms.txt`](documentation/llms.txt) (em inglês) é o índice
legível por máquina,
[`documentation/llms-full.md`](documentation/llms-full.md) (em inglês) a
referência completa em um único arquivo, e todo módulo publicado traz um jar
de fontes rico em Javadoc
([javadoc.io](https://javadoc.io/doc/ai.narrativetrace)).

Comece por aqui:

- [Primeiros 10 minutos](documentation/pt-BR/primeiros-10-minutos.md) — um serviço minúsculo, um teste JUnit, oito passos até um trace real, com saída real
- [Guia de instalação](documentation/pt-BR/guia-de-instalacao.md) — dependências, todos os caminhos de integração, como a captura funciona, configuração da saída de traces
- [Escolhendo uma integração](documentation/pt-BR/escolhendo-uma-integracao.md) — qual módulo você precisa, como um diagrama de decisão
- [Guia de configuração](documentation/pt-BR/guia-de-configuracao.md) — níveis de tracing, configuração de JUnit/Gradle/Spring/Micronaut/SLF4J
- [Guia do plugin de Gradle](documentation/pt-BR/guia-do-plugin-de-gradle.md) — referência do DSL, quality gates, receitas
- [Guia de anotações](documentation/pt-BR/guia-de-anotacoes.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`

Para se aprofundar:

- [Guia do ciclo de vida](documentation/pt-BR/guia-do-ciclo-de-vida.md) — onde o NarrativeTrace vive no seu processo: desenvolvimento, CI/aceitação, produção
- [Privacidade e ocultação](documentation/pt-BR/privacidade-e-ocultacao.md) — o contrato de ocultação linha a linha, verificado contra o código
- [O que commitar](documentation/pt-BR/o-que-commitar.md) — quais arquivos gerados são artefatos de CI e quais são baselines revisadas
- [Solução de problemas](documentation/pt-BR/solucao-de-problemas.md) — sintoma → causa → correção para os modos de falha que as pessoas realmente encontram
- [Guia de integração com Spring](documentation/pt-BR/guia-de-integracao-com-spring.md) — tracing de beans, filtro de servlet, propagação de `@Async`
- [Guia de integração com Micronaut](documentation/pt-BR/guia-de-integracao-com-micronaut.md) — tracing de beans, filtro HTTP, propriedades de configuração
- [Guia de clareza](documentation/pt-BR/guia-de-clareza.md) — modelo de pontuação, componentes de NLP, integração com JUnit
- [Guia de funcionalidades](documentation/pt-BR/guia-de-funcionalidades.md) — catálogo canônico de cada funcionalidade em todas as plataformas, com tier e status
- [Formato de trace estrutural](documentation/pt-BR/formato-de-trace-estrutural.md) — o artefato `.nt` livre de valores por trás do relatório de deltas e dos testes de aprovação
- [Referência completa](documentation/llms-full.md) (em inglês) — API, interioridades da captura, configuração, receitas de integração, solução de problemas, em um único arquivo
- [Glossário de domínio](https://narrativetrace.ai/doc.html?p=docs/glossary.md) — glossário de linguagem ubíqua coletado a partir dos traces, e visões traduzidas de traces (em inglês)

## Compilando a partir do código-fonte

```bash
./gradlew test                                     # executa todos os testes
./gradlew check                                    # testes + PMD + cobertura JaCoCo + os demais gates
./gradlew verifyAll                                # toda categoria de verificação que este repositório tem — veja abaixo
./gradlew :narrativetrace-examples:runExamples     # todos os exemplos em sequência
./gradlew :narrativetrace-examples:traceExamples   # executa os testes → arquivos Markdown de trace
./gradlew :narrativetrace-examples:ejb4:dockerTest # WAR EJB 4 no WildFly traçado apenas pelo agente (requer Docker)
```

`./gradlew verifyAll` executa, em um único comando e de ponta a ponta, toda
verificação que este repositório possui: testes unitários, cobertura, testes
de mutação, testes baseados em propriedades, os dois níveis de fuzzing,
estresse de concorrência, benchmarks e alocação, regras de arquitetura,
scanners de segredos/segurança/dependências, formatação, lint e verificações
de tradução. É **deliberadamente demorado** — só o teste de mutação
costuma levar mais de uma hora — e isso é intencional: o objetivo é um único
comando em que qualquer pessoa que clone o repositório possa confiar, não um
comando rápido. Uma categoria que falha nunca interrompe a execução; todas
rodam de qualquer forma, e `verifyAll` só termina com código de saída
diferente de zero no final, se algo tiver falhado. Ele grava uma linha por
categoria em um relatório JSON — a ferramenta concreta, um status
(`passed`/`failed`/`skipped`/`not-implemented`; este último é uma resposta
real e de primeira classe para uma categoria que este projeto simplesmente não
tem ferramenta para cobrir, não uma falha) — e números reais extraídos da
saída dessa ferramenta, nunca estimados — além de uma tabela em Markdown
renderizada a partir desse mesmo JSON, de modo que os dois nunca possam
discordar entre si.

## Perguntas frequentes

### Quanto overhead isso adiciona, e o que acontece sob alta concorrência?

Não vamos afirmar "overhead zero" — veja [Desempenho](#desempenho) acima para os números datados que esta resposta resume (JMH, JDK 17, `-prof gc`, gravados em [`narrativetrace-benchmarks/baseline.txt`](narrativetrace-benchmarks/baseline.txt) e [`allocation-baseline.txt`](narrativetrace-benchmarks/allocation-baseline.txt), atualizados pela última vez em 2026-08-31/2026-09-01): uma chamada direta, não traçada, custa ~9–12 ns neste harness; o proxy dinâmico da JDK com o tracing desligado adiciona só ~12–26 ns e 24 B/op (um `Object[]` para os argumentos) atrás de uma checagem `isActive()` que pula toda captura, renderização e reflexão. O caminho inativo do agente de bytecode é ainda mais barato — ~37–44 ns a **56 B/op, a mesma alocação da própria chamada direta** — porque um método instrumentado lê um `isActive()` estático antes de empacotar qualquer coisa. Com o tracing totalmente ativo (captura de parâmetros e renderização de valores), uma chamada traçada custa **0,6–1,7 µs** e aloca **~1,0–1,5 kB/op**; capturar e resetar um trace de um único nó custa 1,3–2,3 µs e 2,8–3,1 kB.

O que o NarrativeTrace em si adiciona é essa captura — interceptar a chamada, ler os argumentos, construir a árvore de trace. Tudo depois da captura (a escrita do SLF4J, o collector, o disco ou a rede) é o mesmo custo que sua stack de logging já paga; o NarrativeTrace não adiciona um segundo destino. Para uma equipe substituindo instruções de log escritas à mão, o lado do destino fica quase no zero a zero: N chamadas de log por método viram uma escrita de trace, e essas instruções deixam de ser escritas, revisadas e mantidas sincronizadas com o código.

Sob concorrência, os dois caminhos do `DualPathPipeline` padrão têm garantias diferentes. O caminho síncrono — normalmente um `Slf4jTraceEventListener` — roda em linha na própria thread de quem chama: a escrita é concluída antes do método retornar, então é exatamente tão durável — e custa exatamente o mesmo — quanto uma chamada de log já custa. O caminho com buffer, de melhor esforço — o que alimenta `captureTrace()` e a análise — é um anel de tamanho fixo (65.536 slots por padrão, `narrativetrace.buffer.capacity`) que nunca cresce. Ele descarta sob carga acima de 70% de ocupação em vez de bloquear quem chama, e cada evento descartado é **contado** — sobrescritas do anel, descartes do dreno adaptativo e descartes do assinante igualmente, via `BufferedEventConsumer.droppedCount()` — e mostrado no próprio rodapé do trace, omitido só quando nada foi perdido: um trace curto nunca fica indistinguível em silêncio de um trace tranquilo.

**O limite honesto:** hoje não existe sampling nesta implementação, nem em nenhuma implementação do NarrativeTrace — toda chamada traçada é capturada por completo no `TracingLevel` configurado. Um amostrador por porcentagem ou por taxa está no roadmap, mas não foi lançado. Se você precisa limitar o volume de captura agora, use `TracingLevel.OFF` ou restrinja o escopo traçado ao limite que importa.

### Como sei que um parâmetro com PII ou credenciais não vai vazar em um trace?

Quatro camadas independentes, não uma única promessa geral — o contrato linha a linha, verificado contra o código, é [Privacidade e ocultação](documentation/pt-BR/privacidade-e-ocultacao.md):

1. **`@NotTraced` em um parâmetro, campo ou componente de record** — ocultação explícita que você controla. Ela prevalece até sobre um `toString()` cuidadosamente escrito na classe declarante, e é incondicional: nenhum stage, flag ou propriedade a desliga.
2. **Uma lista de negação por nome, sempre ativa e multilíngue** (`RedactionPolicy.DEFAULT`) — compara nomes de campos e parâmetros com `password`, `secret`, `token`, `ssn`, `cvv`, `apikey`, `cardnumber`, `jwt`, `cookie`, `sessionid`, `accountnumber`, `routingnumber`, mais os equivalentes em português (`senha`, `cartão`, `cpf`, `cnpj`), espanhol (`contraseña`, `dni`, `rut`), alemão (`Passwort`, `Kennwort`) e chinês (`密码`, `身份证`). Está ativa por padrão, não é opcional, e os padrões mais propensos a falsos positivos (`pan`, `iban`, `rut`, `cuit`, `dni`, `senha`, `cpf`, `cnpj`, `nir`, `mima`) só correspondem nos limites do token identificador, então `panelId` e `circuitBreaker` continuam visíveis.
3. **Correspondência pela forma do valor, independente do nome do campo** — uma string com forma de JWT, um número de cartão válido por Luhn, um valor com forma de `Set-Cookie`, um checksum de identificação nacional (RUT chileno, CPF/CNPJ brasileiro, DNI/NIE espanhol, NIR francês, carteira de identidade chinesa), ou um número de Seguro Social dos EUA com hífens é ocultado mesmo que chegue sob um nome inocente como `data` ou `value`. O SSN dos EUA é a única forma desta lista sem um checksum a que recorrer, então só conta a forma com hífens `AAA-GG-SSSS`: nove dígitos soltos são indistinguíveis de um número de pedido, e ocultá-los custaria mais do que protege.
4. **O modo estrutural sem valores `.nt` (ADR-002) — a garantia categórica.** Um artefato `.nt` carrega só os *nomes* de classe, método e parâmetro, a hierarquia de chamadas, e os *tipos* de resultado — zero valores em tempo de execução, zero superfície de injeção de prompt, e isso é uma propriedade verificada por teste, não uma política que alguém poderia esquecer de aplicar. Salvo como baseline `.approved.nt`, é o que entregar a uma ferramenta de IA externa quando nenhum valor pode sair do processo de jeito nenhum. Veja o [formato de trace estrutural](documentation/pt-BR/formato-de-trace-estrutural.md).

Seja preciso sobre o limite: as camadas 1–3 são heurísticas e extensíveis — os padrões são adicionados à medida que lacunas são encontradas, e sempre podem deixar passar uma que ninguém nomeou ainda. A camada 4 é a única *categórica*. Se o seu modelo de ameaça exige "nenhum valor pode jamais sair do processo", recorra ao artefato estrutural `.nt`, não só às camadas de ocultação.

### Os IDs de trace podem se correlacionar com um ID de correlação padrão entre serviços, ou o tracing é só local?

Sim — através do W3C `traceparent`, o mesmo mecanismo que o próprio OpenTelemetry usa. Um cabeçalho `traceparent` de entrada é adotado via `NarrativeContext.adoptTraceparent(...)` (conectado automaticamente pelo filtro de servlet, pelo filtro HTTP do Micronaut, e pelo filtro web do Spring), e o próprio ID de trace do NarrativeTrace **se torna** diretamente o ID de trace desse cabeçalho — não é um identificador separado apenas com uma forma parecida. `outboundTraceparent()` dá a qualquer cliente HTTP o valor para anexar na saída (o exemplo de ecommerce conecta isso a um `HttpRequest.Builder` real). Quando não há cabeçalho presente, um novo ID é gerado na mesma forma W3C de 32 caracteres hexadecimais minúsculos. O módulo `narrativetrace-opentelemetry` também exporta os spans do NarrativeTrace (`TraceSpanExporter`, em lote; `OtelTraceEventListener`, ao vivo) com atributos tipados, então seu collector OTel, Jaeger ou middleware de ID de correlação já existentes entendem o ID sem nada para reconciliar.

O que fica local: a árvore narrativa em si — as chamadas de método aninhadas, os argumentos, a narração — é capturada por processo e nunca é enviada a outro serviço; só o ID de trace cruza a fronteira. Um serviço downstream produz sua própria árvore narrativa correlacionada com esse mesmo ID, não uma única árvore combinada entre serviços.

## Licença

A API e o formato de saída do NarrativeTrace são padrões abertos
(Apache 2.0). Seu runtime é gratuito e de código disponível (BSL 1.1,
convertendo-se para Apache 2.0 quatro anos após cada lançamento). Pro é
comercial.

| Artefato | Licença | O que isso significa |
|---|---|---|
| `narrativetrace-api` | [Apache 2.0](LICENSE-APACHE) | As anotações, o modelo de eventos e as SPIs — tudo aquilo contra o que seu código compila. Um padrão aberto, para que qualquer implementação possa mirar nele. |
| todo outro artefato `ai.narrativetrace` | [BSL 1.1](LICENSE) | O runtime. Gratuito para uso em produção, inclusive em produtos e serviços que você oferece aos seus próprios clientes. A única exclusão é oferecer o próprio NarrativeTrace — ou um produto ou serviço cujo valor derive substancialmente dele — a terceiros como produto ou serviço de logging, tracing ou narrativa de código. Cada versão lançada se torna Apache 2.0 quatro anos após ser publicada. |
| a documentação (guias em prosa) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | Os guias e a documentação de referência. A especificação do formato de saída e os esquemas JSON são Apache 2.0: eles são o padrão aberto. |

Qual categoria cada módulo é publicado é declarado em
[`licensing.properties`](licensing.properties), e o build recusa um grafo de
dependências que as licenças não conseguem sustentar.

O runtime **não** é código aberto, e este README não vai chamá-lo assim. Ele
é de código disponível e gratuito, com uma promessa datada de se tornar
código aberto.

<!-- legal:trademark:begin -->
Nenhuma das licenças concede qualquer direito de marca: NarrativeTrace é uma
marca da Empower Agile, e a permissão para usar, copiar ou modificar o código
não é permissão para usar o nome na sua própria distribuição ou serviço.
<!-- legal:trademark:end -->

### A licença, em palavras simples

O jar `narrativetrace-api`, a especificação do formato de saída e os esquemas
JSON são Apache 2.0 — código aberto sem reservas, sem restrições além das da
própria Apache.

<!-- legal:plain-words:begin -->
**Grátis para rodar.** O runtime é de código disponível sob a Business Source
License 1.1: você pode lê-lo, auditá-lo, corrigi-lo e usá-lo em produção sem
custo — inclusive dentro dos produtos e serviços que você vende aos seus
próprios clientes.

**Uma única exclusão.** Você não pode oferecer o próprio NarrativeTrace — ou um
produto ou serviço cujo valor derive substancialmente dele — a terceiros como
produto ou serviço de logging, tracing ou narrativa de código.

**Ela se abre em uma data.** Cada versão lançada se converte para Apache 2.0
quatro anos após ser publicada; a data exata é impressa no LICENSE daquela
versão.

*Este resumo é uma cortesia, não uma licença. O arquivo LICENSE é o único texto
vinculante; onde os dois divergirem, o LICENSE prevalece.*
<!-- legal:plain-words:end -->
