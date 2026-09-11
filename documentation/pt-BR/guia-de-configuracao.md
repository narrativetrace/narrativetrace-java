<!-- source: documentation/configuration-guide.md blob f6d93be29ca8 | translated: 2026-09-11 | reviewed: - -->
# Guia de configuração de NarrativeTrace Java

[English](../configuration-guide.md) | [Español](../es/guia-de-configuracion.md) | **Português** | [简体中文](../zh-CN/配置指南.md)

Este guia documenta a configuração de runtime e de testes do NarrativeTrace Java.
Para saber qual configuração pertence a qual etapa do seu processo —
desenvolvimento, CI/aceitação, produção — veja o
[Guia do Ciclo de Vida](guia-do-ciclo-de-vida.md).

## Superfície de configuração

O NarrativeTrace oferece seis caminhos de configuração:

| Caminho | Mecanismo | Ideal para |
|---|---|---|
| Plugin do Gradle | DSL `narrativeTrace { }` em `build.gradle.kts` | Projetos Gradle (recomendado) |
| JUnit 5 | `junit-platform.properties` | Saída de trace em tempo de teste |
| Java puro / Agente | `narrativetrace.properties` no classpath | Apps standalone, agente |
| Gradle (manual) | `gradle.properties` + encaminhamento no build script | Projetos Gradle sem o plugin |
| Spring | Anotação `@EnableNarrativeTrace` | Apps Spring |
| Micronaut | `application.yml` via `@ConfigurationProperties` | Apps Micronaut |

Todos os caminhos aceitam overrides via propriedades do sistema (flags `-D`) como fonte de maior prioridade.

## DSL do plugin de Gradle

O plugin do Gradle (`ai.narrativetrace`) configura tudo automaticamente. Aplique-o e personalize opcionalmente:

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}

// A configuração zero funciona — padrões sensatos para tudo:
narrativeTrace { }

// Superfície completa:
narrativeTrace {
    enabled.set(true)                          // padrão: true
    mode.set("proxy")                          // "proxy" (padrão) | "agent" | "spring"
    testFramework.set("junit5")               // "junit5" (padrão) | "junit4"
    scope.set("test")                          // "test" (padrão) | "production"
    format.set("markdown")                     // "markdown" | "text" | "mermaid" | "plantuml"
    tracingLevel.set("DETAIL")                 // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
    outputDir.set(layout.buildDirectory.dir("narrativetrace"))
    glossary.set(false)                        // padrão: false — coleta o glossary.json ao final da suíte
    approval.set(false)                        // padrão: false — verifica a estrutura contra baselines commitadas
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))

    modules {                                  // opt-in granular (tudo com padrão false)
        slf4j.set(false)
        micrometer.set(false)
        servlet.set(false)
        springWeb.set(false)                   // implica servlet
    }

    agent {                                    // relevante apenas quando mode = "agent"
        packages.set(listOf("com.example.app"))
    }

    clarity {
        minScore.set(0.80)                     // padrão: 0.0 (sem gate)
        maxHighIssues.set(0)                   // padrão: Integer.MAX_VALUE (sem gate)
        maxSuiteIssues.set(0)                  // padrão: Integer.MAX_VALUE (apenas informativo)
        warnOnly.set(false)                    // padrão: false
    }
}
```

### O que o plugin faz

| Ação | Detalhe |
|---|---|
| Adiciona a flag do compilador `-parameters` | Em todas as tasks `JavaCompile`; pulada se já estiver presente |
| Adiciona dependências | Baseado em `mode`, `modules` e `testFramework`; versão detectada automaticamente a partir do JAR do plugin |
| Define propriedades da JVM de teste | `narrativetrace.output=true`, `narrativetrace.outputDir` e, opcionalmente, `narrativetrace.format`, `narrativetrace.level`, o par do glossário (`glossary=true`) e o par de aprovação (`approval=true`) |
| Registra a task `clarityCheck` | Lê `clarity-results.json`, aplica os limiares, integrada ao ciclo de vida `check` |
| Registra a task `clarityScan` | Análise de clareza independente a partir de classes compiladas (não exige testes) |
| Registra a task `glossaryScan` | Coleta de glossário independente a partir de classes compiladas, incluindo templates de anotação |
| Registra a task `approveNarratives` | Promove narrativas `*.received.nt` revisadas para baselines `*.approved.nt` (modo de aprovação) |
| Configura o argumento de JVM do agente | Quando `mode = "agent"`: resolve o JAR do agente, adiciona `-javaagent` às tasks Test |

### Modos de interceptação

| Modo | Dependências adicionadas (além de core + clarity + diagrams + framework de teste) |
|---|---|
| `proxy` (padrão) | `narrativetrace-proxy` |
| `agent` | `narrativetrace-agent` (configuração separada para resolução do JAR) |
| `spring` | `narrativetrace-spring` + `narrativetrace-proxy` |

### Ativação de módulos

| Flag | Artefato | Notas |
|---|---|---|
| `modules.slf4j` | `narrativetrace-slf4j` | Ponte SLF4J |
| `modules.micrometer` | `narrativetrace-micrometer` | Propagação entre threads |
| `modules.servlet` | `narrativetrace-servlet` | Filtro de servlet |
| `modules.springWeb` | `narrativetrace-spring-web` + `narrativetrace-servlet` | Adiciona servlet automaticamente |

### Escopo de dependências

| Escopo | Dependências de biblioteca | Dependência do framework de teste |
|---|---|---|
| `test` (padrão) | `testImplementation` | `testImplementation` |
| `production` | `implementation` | `testImplementation` (sempre) |

### Desativando o plugin

Para desativar o plugin completamente (por exemplo, em um subprojeto), defina `enabled.set(false)`. Nenhuma task é registrada, nenhuma dependência é adicionada, nenhuma flag de compilador é definida.

```kotlin
narrativeTrace {
    enabled.set(false)
}
```

### Limiares de clareza

A task `clarityCheck` lê `build/narrativetrace/clarity-results.json` (produzido pela extensão do JUnit durante `test`) e aplica os limiares configurados. Ela roda automaticamente como parte de `./gradlew check`.

- **`minScore`** — pontuação geral de clareza mínima (0.0–1.0). Qualquer cenário abaixo desse limiar falha a build.
- **`maxHighIssues`** — número máximo de problemas de severidade HIGH por cenário. Ultrapassar esse número falha a build.
- **`warnOnly`** — quando `true`, violações de limiar produzem avisos em vez de falhas de build.

Se não existir `clarity-results.json` (por exemplo, nenhum teste rodou), a task passa silenciosamente.

### Resolução de versão

O plugin detecta automaticamente sua versão a partir do JAR do plugin (não há propriedade DSL para isso). A mesma versão é usada para todas as dependências gerenciadas. Não existe uma propriedade `manageDependencies` — se `enabled=true`, o plugin gerencia as dependências com base em mode/modules/scope. Quem quiser controle manual total define `enabled.set(false)` e monta o wiring por conta própria.

Para a referência completa do plugin, incluindo receitas, DSL em Groovy e detalhes de validação, veja o [Guia do Plugin de Gradle](guia-do-plugin-de-gradle.md).

## 1. Níveis de tracing (`NarrativeTraceConfig`)

`ThreadLocalNarrativeContext` usa `NarrativeTraceConfig`, cujo padrão é `DETAIL`.

```java
var config = new NarrativeTraceConfig(TracingLevel.NARRATIVE);
var context = new ThreadLocalNarrativeContext(config);
```

Níveis disponíveis:

| Nível | Comportamento |
|---|---|
| `OFF` | Nenhum trace é capturado |
| `ERRORS` | Apenas caminhos de exceção são capturados |
| `SUMMARY` | Captura a entrada raiz, a folha mais profunda e as cadeias de exceção completas |
| `NARRATIVE` | Captura o fluxo de chamadas completo, suprime os valores de parâmetro; os valores de retorno são renderizados em todo nível ativo (uma promessa documentada — o retorno é o payload de uma narrativa) |
| `DETAIL` | Captura o fluxo de chamadas completo com valores de parâmetro e valores de retorno |

Mudanças de nível em tempo de execução são suportadas:

```java
config.setLevel(TracingLevel.ERRORS);
```

## 2. Configuração do JUnit 5 (`junit-platform.properties`)

A extensão do JUnit usa `ExtensionContext.getConfigurationParameter()`, que resolve os valores nesta ordem:

1. Propriedades do sistema (maior prioridade — flags `-D` da CLI continuam funcionando)
2. `junit-platform.properties` no classpath de teste
3. Padrões fixos no código (menor prioridade)

### Propriedades

| Propriedade | Valores | Padrão |
|---|---|---|
| `narrativetrace.output` | `true` / `false` | `true` |
| `narrativetrace.outputDir` | Qualquer caminho com permissão de escrita | `build/narrativetrace` |
| `narrativetrace.format` | `markdown`, `text`, `mermaid`, `plantuml` | `markdown` |
| `narrativetrace.unfolded` | `true` / `false` | `false` |
| `narrativetrace.glossary` | `true` / `false` | `false` |
| `narrativetrace.glossaryDir` | Qualquer caminho com permissão de escrita | diretório de trabalho |
| `narrativetrace.canonicalJson` | `true` / `false` | `false` |
| `narrativetrace.structuralJson` | `true` / `false` | `false` |
| `narrativetrace.approval` | `true` / `false` | `false` |
| `narrativetrace.approvedDir` | Diretório das baselines commitadas | `src/test/narratives` |
| `narrativetrace.bufferCapacity` | Slots no anel de eventos de um contexto de teste | `8192` |

`narrativetrace.bufferCapacity` dimensiona o anel de eventos do contexto que a
extensão constrói **por método de teste**. O padrão é 8.192 slots em vez dos
65.536 do runtime porque um teste traceia dezenas de chamadas, não dezenas de
milhares, e o anel é alocado antecipadamente na construção — o padrão do
runtime custaria 1,75 MB e cerca de 1,5 ms por método de teste para slots que
nenhum teste alcança. Com 8.192 isso é 224 kB.

Essa é a grafia do JUnit 5 para o mesmo ajuste; a chave do runtime
`narrativetrace.buffer.capacity` (uma propriedade do sistema ou uma entrada em
`narrativetrace.properties`) tem prioridade sobre ela, porque o ajuste de um
deployment prevalece sobre o padrão de uma integração. Nada no runtime detecta
o JUnit — a extensão passa sua escolha explicitamente, no próprio código.

Uma suíte que ultrapassa 8.192 eventos em um teste descarta o excedente, e
avisa: toda narrativa que ela escreve traz uma linha de rodapé com a contagem
e a propriedade a aumentar. Aumente esta para um ajuste só de teste, ou a
chave do runtime para mudar em todo lugar.

A coleta do glossário vem desativada por padrão porque ela reescreve
`glossary.json` e `glossary.md` **fora** do diretório de build — um artefato
commitado e revisado, não um produto da build. `narrativetrace.glossaryDir`
nomeia o diretório que contém esses dois arquivos; o plugin do Gradle o
define como a raiz do repositório, o que importa em builds multi-módulo, onde
o diretório de trabalho de uma task de teste é o subprojeto, mas o glossário
é um único arquivo por repositório.

`narrativetrace.glossaryDir` é lida mesmo com a coleta desativada: um
`glossary.json` commitado é o vocabulário do projeto com o qual a clareza é
pontuada (veja o [Guia de Clareza](guia-de-clareza.md)). A leitura não muda
nada em disco, então não precisa de opt-in; um repositório sem esse arquivo
pontua apenas com os dicionários integrados.

`narrativetrace.canonicalJson` adicionalmente escreve um array de entradas do
schema 1.1 em `<test>.canonical.json` ao lado de cada arquivo de trace — um
artefato de máquina para consumidores do schema canônico, como as demais
implementações do NarrativeTrace e os fixtures de conformidade.

`narrativetrace.structuralJson` adicionalmente escreve um
`<test>.structural.json` ao lado de cada arquivo de trace: o mesmo array de
entradas do schema 1.1 com todo campo de valor de runtime elidido (os
parâmetros trazem `[ELIDED]`, os valores de retorno e as mensagens de exceção
são null) — o artefato estrutural seguro para IA do ADR-002 Nível 1. As duas
flags são independentes e podem ser combinadas em uma mesma execução.

`narrativetrace.unfolded` **desliga o dobramento de laços** na narrativa
Markdown. Por padrão, uma sequência de chamadas irmãs consecutivas de mesma
forma é renderizada como a primeira iteração completa mais uma linha de
resumo — `×2 more: ‹Taxi›, #3 sku=` `` `"TENT"` `` ` — same flow (validate ✓
→ record ✓)`. Essa linha nomeia cada iteração dobrada: pelo seu rótulo de
identidade quando o argumento que a distingue tem um, e caso contrário pela
posição mais o próprio argumento, de modo que as iterações omitidas
continuam alcançáveis. Quando você precisa da subárvore *inteira* de cada
iteração e de todos os seus valores, defina `narrativetrace.unfolded=true` e
cada iteração é renderizada por completo. Só Markdown — nenhum outro formato
dobra, e o JSON canônico sempre carrega todas as iterações. Com o plugin do
Gradle, encaminhe como qualquer outra propriedade:

```kotlin
tasks.withType<Test> { systemProperty("narrativetrace.unfolded", "true") }
```

`narrativetrace.approval` ativa o modo de aprovação: depois de um teste que
**passa**, a estrutura livre de valores do cenário (a mesma renderização do
artefato `.nt`) é verificada contra a baseline commitada
`<approvedDir>/<TestClassSimpleName>/<artifact_name>.approved.nt` — a mesma
identidade de artefato de qualquer outro arquivo por teste, então um método
que roda mais de uma vez tem uma baseline por invocação. Uma
baseline ausente ou uma diferença estrutural falha o teste com um diff
legível e escreve a estrutura atual ao lado da baseline como
`*.received.nt`; revise-a e aceite-a com a task `approveNarratives` do
Gradle (ou renomeie manualmente). Testes que falham nunca são verificados —
sua estrutura está em pleno voo e não deve agitar os arquivos received. O
plugin do Gradle define as duas propriedades a partir do seu DSL `approval` /
`approvedDir`.

### Configuração baseada em arquivo (recomendado)

A saída é escrita por padrão — não é preciso nenhum arquivo para ativá-la.
Coloque um arquivo em `src/test/resources/junit-platform.properties` apenas
para mudar o formato ou desativá-la:

```properties
narrativetrace.output=false
```

Não é necessário nenhum wiring de `systemProperty()` no Gradle. O arquivo é exclusivo de teste e nunca chega à produção.

### Overrides via CLI

Propriedades do sistema continuam funcionando como overrides:

```bash
./gradlew test -Dnarrativetrace.output=false
./gradlew test -Dnarrativetrace.format=text
./gradlew test -Dnarrativetrace.outputDir=out/narrative
```

### Encaminhamento via CLI do Gradle (opcional)

Só é necessário se você quiser passar flags `-D` da CLI através do Gradle até a JVM de teste (forked):

```kotlin
tasks.withType<Test> {
    System.getProperty("narrativetrace.output")?.let { systemProperty("narrativetrace.output", it) }
    System.getProperty("narrativetrace.outputDir")?.let { systemProperty("narrativetrace.outputDir", it) }
    System.getProperty("narrativetrace.format")?.let { systemProperty("narrativetrace.format", it) }
}
```

### Nomes de cenário

A extensão deriva um nome de cenário legível a partir de cada teste:

- `customerPlacesOrder()` → "Customer places order"
- `customer_places_order()` → "Customer places order"
- `@DisplayName("customer places order")` → "customer places order" (repassado como está)

Sufixos de tipo de parâmetro do JUnit (por exemplo, `(NarrativeContext)`) são removidos automaticamente.

### Layout de arquivos

Layout base:

- `<outputDir>/traces/<TestClassSimpleName>/<artifact_name>.<ext>`

`<artifact_name>` é a **identidade de artefato** de uma invocação de
teste:

- Um método de teste comum é o seu nome convertido em slug —
  `customerPlacesOrder` → `customer_places_order`.
- Um método que roda mais de uma vez (`@ParameterizedTest`,
  `@RepeatedTest`) acrescenta `-<índice>-<rótulo>`: o número da invocação
  em base 1, preenchido com zeros até três dígitos, e depois o nome
  exibido da invocação pela mesma regra de slug —
  `equipment_can_be_found-002-find_tent`. O rótulo é omitido quando seu
  slug fica vazio, deixando `equipment_can_be_found-002`.
- `-` é o separador porque o alfabeto do slug é `[a-z0-9_]` e nunca pode
  produzir um: o artefato de uma invocação nunca colide com o de um
  método comum, e o nome volta a se separar em método, índice e rótulo.
  Duas invocações de um mesmo método sempre diferem no índice, então
  nomes exibidos que só se distinguem por caracteres que um caminho não
  pode carregar (`find/TENT` versus `find TENT`) continuam caindo em
  arquivos diferentes.
- Nada no nome varia entre execuções ou entre máquinas, que é o que
  permite commitar uma baseline de aprovação por invocação. Um nome longo
  demais para o sistema de arquivos é encurtado na sua metade de *método*
  e recebe oito caracteres hexadecimais do `String.hashCode` do Java
  sobre o slug completo; o índice e o rótulo nunca são a parte truncada.

Todos os artefatos por teste de uma invocação compartilham esse nome: o
trace, a exportação JSON, o diagrama, o artefato estrutural e a baseline
`.approved.nt` commitada ao lado deles.

> Um template `@ParameterizedTest(name = ...)` interpola argumentos no
> nome exibido, e esse nome chega tanto ao *nome de arquivo* do artefato
> quanto ao cabeçalho `scenario:` do artefato `.nt` livre de valores. Os
> corpos das chamadas continuam sem valores; o nome não. Não interpole um
> segredo em um template de nome exibido.

Quando `format=markdown`, a extensão também escreve, por teste:

- Diagrama Mermaid: `<outputDir>/diagrams/<TestClassSimpleName>/<artifact_name>.mmd`
- Exportação JSON: `<outputDir>/traces/<TestClassSimpleName>/<artifact_name>.json`
- Artefato estrutural: `<outputDir>/structural/<TestClassSimpleName>/<artifact_name>.nt`
  — a estrutura de chamadas livre de valores (especificação do formato:
  [formato-de-trace-estrutural.md](formato-de-trace-estrutural.md)). O
  arquivo em disco é a **baseline do último verde**: uma execução verde a
  avança, uma execução não verde compara com ela mas nunca a sobrescreve, de
  modo que cada delta se lê como "o que mudou desde a última vez que esse
  cenário passou". "Verde" é o veredito inteiro, não só as asserções — um
  teste que passou mas cuja estrutura a aprovação **rejeitou** termina
  vermelho, e sua estrutura não é escrita. Rejeitar uma mudança deixa
  portanto a baseline onde estava, e reverter a mudança não reporta delta
  nenhum

Depois que todos os testes de uma classe terminam, a extensão escreve:

- Manifesto da execução: `<outputDir>/manifest.json` — uma linha por
  cenário rastreado, em ordem de execução, nomeando o teste que o
  produziu, seu número de invocação quando o método rodou mais de uma
  vez, e cada artefato que lhe pertence como caminho relativo a
  `<outputDir>`. É o índice a ler quando você conhece o cenário e quer o
  arquivo:
  ```json
  {
    "scenario": "find TENT",
    "testClass": "traildepot.CatalogTest",
    "testMethod": "equipmentCanBeFound",
    "invocation": 2,
    "artifacts": {
      "trace": "traces/CatalogTest/equipment_can_be_found-002-find_tent.md",
      "structural": "structural/CatalogTest/equipment_can_be_found-002-find_tent.nt"
    }
  }
  ```
  Apenas artefatos realmente presentes em disco são listados, então a
  linha reflete o formato e as flags que a execução usou.
- Relatório de clareza: `<outputDir>/clarity-report.md`
- Resumo no console (impresso no stdout), terminando com o delta estrutural
  de uma linha contra a última execução verde:
  ```
  NarrativeTrace — Suite complete
    2 scenarios recorded
    Clarity: 100% high | 0% moderate | 0% low
    Reports: build/narrativetrace
    Since last green: 1 scenario unchanged · 1 changed: "Customer places order" (+1 call InventoryService.release)
  ```

O relatório no console de um teste que falha imprime o delta estrutural
contra o último artefato verde — resumo mais diff legível — em vez do trace
completo, e vincula o arquivo de trace como uma URI `file://` clicável.

## 3. Configuração para Java puro / agente (`narrativetrace.properties`)

Para apps Java standalone e o agente de bytecode, `ConfigResolver` carrega a configuração a partir do classpath.

Ordem de resolução:

1. Propriedades do sistema (maior prioridade)
2. `narrativetrace.properties` no classpath
3. Padrões fixos no código (menor prioridade)

### Propriedades

| Propriedade | Valores | Padrão |
|---|---|---|
| `narrativetrace.level` | `OFF`, `ERRORS`, `SUMMARY`, `NARRATIVE`, `DETAIL` | `DETAIL` |
| `narrativetrace.packages` | Prefixos de pacote separados por ponto e vírgula | (vazio) |
| `narrativetrace.loggerName` | Nome do logger SLF4J | `narrativetrace` |
| `narrativetrace.loggingJars` | Arquivos jar ou diretórios separados por ponto e vírgula | (vazio) |
| `narrativetrace.capture.resource` | `true` / `false` | `true` |
| `narrativetrace.capture.sourceLocation` | `true` / `false` | `false` |
| `narrativetrace.capture.instanceIds` | `true` / `false` | `false` |
| `narrativetrace.narration` | `off` para suprimir o listener SLF4J | (ativado) |
| `narrativetrace.pipeline` | Nome de uma topologia de pipeline registrada | (caminho duplo) |
| `narrativetrace.pipeline.<nome>.*` | Configurações da topologia nomeada | (por topologia) |
| `narrativetrace.buffer.capacity` | Slots no anel da topologia padrão, arredondados para cima até uma potência de dois | `65536` |
| `narrativetrace.discovery` | `off` para desativar a descoberta de extensões | (ativado) |
| `narrativetrace.discovery.disabled` | Nomes de classes provedoras separados por vírgula | (vazio) |

### Flags de captura

As flags `narrativetrace.capture.*` ampliam qual identidade é capturada por
evento. Elas controlam **apenas a captura, nunca a forma do schema**: todo
campo controlado por elas permanece anulável no schema canônico e
simplesmente fica ausente quando a flag está desligada, de modo que
consumidores downstream e fixtures multiplataforma nunca ramificam com base
na configuração.

- **`narrativetrace.capture.resource`** (ativado por padrão) — estampa a
  identidade de processo autodetectada em cada span: `host.name` (a partir de
  `HOSTNAME`/`COMPUTERNAME`, recorrendo a uma busca reversa como fallback),
  `process.pid` e `process.runtime.version`. A detecção roda uma vez por
  processo. Deployments sensíveis ao hostname podem desativar. Esses campos
  nunca são reemitidos em spans do OpenTelemetry — os detectores de recurso
  do SDK do OTel são donos daquele caminho; isso cobre as saídas próprias da
  biblioteca (JSON canônico, MDC).
- **`narrativetrace.capture.sourceLocation`** (desativado por padrão) —
  registra `code.filepath`/`code.lineno` nas entradas de enter. Os dois
  caminhos de captura são deliberadamente assimétricos: o agente embute o
  próprio arquivo-fonte e o primeiro número de linha do método instrumentado
  no momento da instrumentação (de graça), enquanto o proxy paga um stack
  walk por chamada e registra o frame do *chamador* (interfaces com proxy
  não carregam informação de linha) — por isso a flag vem desligada por
  padrão.
- **`narrativetrace.capture.instanceIds`** (desativado por padrão) —
  registra o hash de identidade do objeto receptor (hex em minúsculas) como
  `nt.instanceId` nas entradas de enter; útil para distinguir instâncias de
  uma mesma classe.

A coleta automática mantém a forma de identidade por design. Contexto com
forma de conteúdo (totais de pedido, feature flags, estado de negócio) nunca
entra na captura automática — o enriquecimento de atributos/MDC em três
níveis é o canal do cliente para contexto imprevisto, e flui pela ocultação e
pela elisão como qualquer outro valor.

`narrativetrace.loggingJars` dá suporte ao attach standalone do agente em
hosts cujos class loaders não enxergam um provedor SLF4J (application
servers): cada jar listado — diretórios são expandidos para os jars que
contêm — é anexado à busca do class loader do sistema antes de o tracing
começar. Um caminho que não existe falha rápido no momento do attach.
Também disponível como o argumento de agente `loggingJars=`.

### Dimensionando o buffer de eventos

O caminho de melhor esforço retém os eventos em um **buffer em anel de
tamanho fixo**. Ele nunca cresce: a capacidade é escolhida uma vez, o anel
inteiro é alocado na construção, e um produtor que ultrapassa a drenagem
sobrescreve o slot mais antigo em vez de expandir. Não há capacidade
inicial, fator de crescimento nem redimensionamento — o que um processo
traceado gasta com retenção é decidido na inicialização e permanece
decidido.

`narrativetrace.buffer.capacity` define esse tamanho para a topologia de
caminho duplo padrão, arredondado para cima até a próxima potência de dois
(o anel usa máscara em vez de divisão). O padrão é **65.536 slots**. Um
valor que o anel não consegue honrar — não numérico, zero, negativo, ou
acima de 2^30 — recai para o padrão em vez de falhar a inicialização: um
buffer mal dimensionado custa histórico de análise, que esse caminho tem
permissão para perder, enquanto uma inicialização recusada custa a
aplicação.

**A regra de dimensionamento:**

```
capacidade  ≈  pico de eventos/s  ×  pior travamento tolerável de drenagem
memória na saturação  ≈  capacidade  ×  ~300 B/evento
```

Exemplo prático. 1.000 req/s × 50 chamadas traceadas por requisição × 2
eventos por chamada (enter e exit) dá 100.000 eventos/s. Reserve um
travamento de drenagem de 500 ms no pior caso — uma pausa longa de GC, uma
thread faminta — e sobram 50.000 eventos pendentes no pico. Isso cabe no
padrão de 65.536 slots, e custa aproximadamente 20 MB na saturação. Com o
dobro do tráfego, ou um travamento maior que você esteja disposto a
suportar, você aumenta deliberadamente:

```properties
narrativetrace.buffer.capacity=131072
```

Acima de 70% de ocupação o buffer descarta em vez de enfileirar, e cada
evento descartado é contado (`EventPipeline.droppedEventCount()`) — um anel
pequeno demais para seu tráfego aparece como um número, não como silêncio.
Os três modos de perda são contados: o anel sobrescrevendo um slot que o
consumidor ainda não tinha alcançado, a drenagem adaptativa descartando um
lote acima do limiar de descarte, e um subscriber que não conseguiu
acompanhar o ritmo.

A contagem não está apenas disponível: ela é **anunciada**. Uma captura que
perdeu eventos carrega isso na árvore (`TraceTree.loss()`), e todo formato
renderizado com um slot de rodapé imprime uma linha — a contagem e a
propriedade a aumentar:

```
⚠ Incomplete narrative: 1204 events shed under load (buffer full) — raise narrativetrace.buffer.capacity.
```

Text, prosa e Markdown carregam isso como um rodapé (Markdown como
blockquote, além de `incomplete: true` e `dropped_events:` no frontmatter do
documento); Mermaid e PlantUML carregam isso como um comentário de diagrama,
de modo que o desenho em si não muda. O artefato estrutural `.nt`
deliberadamente não carrega: ele é a baseline de aprovação e o formato dos
fixtures de conformidade, e precisa permanecer byte a byte idêntico para um
comportamento idêntico. Uma captura que não perdeu nada não imprime nada.

**Por que existe uma thread de drenagem, e quando ela existe.** O contexto
padrão não inicia nenhuma: ele constrói seu consumer com
`startConsumer=false` e drena sob demanda, então `captureTrace()` drena o
anel antes de ler. Um consumer que você inicia por conta própria é dono de
uma thread por causa da cauda — os últimos eventos publicados antes de o
tráfego parar já estão no anel, e nada de novo vem para carregá-los para
fora. Drenar apenas como efeito colateral de publicar os deixaria presos ali
até uma próxima publicação que talvez nunca aconteça, então a thread
estaciona e reverifica em vez de terminar, e continua drenando até o anel
ficar vazio. `close()` drena o que resta, e por isso fechar esse consumer é
obrigatório — veja o [guia do ciclo de vida](guia-do-ciclo-de-vida.md).

### Configuração baseada em arquivo

Coloque um arquivo no classpath (por exemplo, `src/main/resources/narrativetrace.properties`):

```properties
narrativetrace.level=DETAIL
narrativetrace.packages=com.example.app.*;com.example.shared.*
narrativetrace.loggerName=myapp.traces
```

### Uso programático

```java
var resolver = new ConfigResolver();
var level = resolver.resolve("narrativetrace.level", "DETAIL");
```

### Detecção de arquivo duplicado

Se vários arquivos `narrativetrace.properties` forem encontrados no classpath (por exemplo, um no JAR da aplicação e outro em uma dependência), `ConfigResolver` lança `DuplicateConfigurationException` listando todos os locais. Isso evita bugs silenciosos de sombreamento.

### Fallback do agente

Quando o agente não recebe argumentos de CLI, ele recorre ao `ConfigResolver`:

```bash
# Argumentos de CLI explícitos (maior prioridade)
java -javaagent:narrativetrace-agent.jar=packages=com.example.app -jar app.jar

# Com nome de logger personalizado
java -javaagent:narrativetrace-agent.jar=packages=com.example.app,loggerName=myapp.traces -jar app.jar

# Recorre a narrativetrace.properties no classpath
java -javaagent:narrativetrace-agent.jar -jar app.jar
```

## 4. Configuração do Gradle (`gradle.properties`)

Para projetos Gradle, `gradle.properties` oferece um único lugar para definir as configurações de saída de teste do NarrativeTrace. As propriedades definidas ali ficam disponíveis como propriedades de projeto do Gradle e podem ser encaminhadas para a JVM de teste (forked).

### Definir propriedades

A saída é escrita por padrão; o `gradle.properties` é onde você mudaria o
formato ou a desativaria. Adicione ao `gradle.properties` na raiz do projeto:

```properties
narrativetrace.output=false
narrativetrace.format=markdown
```

### Encaminhar para a JVM de teste

Propriedades de projeto do Gradle não fluem automaticamente para JVMs de teste forked. Adicione o encaminhamento em `build.gradle.kts`:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    listOf("narrativetrace.output", "narrativetrace.outputDir", "narrativetrace.format")
        .forEach { key ->
            (findProperty(key) as? String)?.let { systemProperty(key, it) }
        }
}
```

Isso lê cada propriedade de `gradle.properties` (ou de flags `-P` da CLI) e a passa como propriedade do sistema para a JVM de teste. Propriedades do sistema têm a maior prioridade na resolução de `getConfigurationParameter()` do JUnit.

### Overrides via CLI com `-P`

Propriedades de projeto do Gradle podem ser sobrescritas pela linha de comando com `-P`:

```bash
./gradlew test -Pnarrativetrace.format=text
./gradlew test -Pnarrativetrace.output=false
```

### DSL específico do JUnit

O Gradle também oferece uma forma específica do JUnit para passar parâmetros de configuração diretamente:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform {
        configurationParameter("narrativetrace.output", "false") // desativa; ativado por padrão
        configurationParameter("narrativetrace.format", "markdown")
    }
}
```

Isso só alimenta o `getConfigurationParameter()` do JUnit — não afeta o `ConfigResolver` nem o agente. Use `gradle.properties` com encaminhamento quando precisar de uma única fonte de configuração para todas as integrações.

## 5. Configuração do Spring

Use filtros de pacote para controlar quais beans são considerados para o encapsulamento com proxy:

```java
@Configuration
@EnableNarrativeTrace
public class AppConfig { }
```

Quando `basePackages` é omitido, o padrão é o pacote da classe anotada — assim como `@ComponentScan`. Para restringir o escopo explicitamente:

```java
@EnableNarrativeTrace(basePackages = {"com.example.order", "com.example.payment"})
```

### Nome do logger

Quando `narrativetrace-slf4j` está no classpath, o bean `NarrativeContext` criado automaticamente narra através do SLF4J automaticamente (`Slf4jTraceEventListener` no caminho síncrono do pipeline). Configure o nome do logger SLF4J via a anotação:

```java
@EnableNarrativeTrace(loggerName = "myapp.traces")
```

O nome de logger padrão é `"narrativetrace"`. Defina como string vazia para desativar o encapsulamento automático com SLF4J:

```java
@EnableNarrativeTrace(loggerName = "")
```

O nome do logger também se propaga para `Slf4jTraceExporter` no módulo spring-web, que deriva o logger de exportação como `<loggerName>.export`.

Apps Spring usam suas próprias convenções de configuração. A anotação `@EnableNarrativeTrace` é a abordagem recomendada — não são necessários arquivos de properties.

Comportamento:

- Beans fora de `basePackages` (ou do pacote padrão) são ignorados.
- Beans sem interfaces são ignorados (limitação do proxy dinâmico JDK).
- Apenas interfaces nos pacotes configurados são traceadas; interfaces do framework Spring são ignoradas.
- Um bean `NarrativeContext` é fornecido automaticamente. Quando `narrativetrace-slf4j` está no classpath e `loggerName` não está vazio, ele narra através do SLF4J sob esse logger.
- Definir seu próprio bean `narrativeContext` sobrescreve o criado automaticamente.

Para a propagação entre threads com `@Async`, a configuração do filtro de servlet e os padrões de deployment em produção, veja o [Guia de Integração com Spring](guia-de-integracao-com-spring.md).

## 6. Configuração do Micronaut

A integração com o Micronaut é autodescoberta no classpath — nenhuma anotação de ativação é necessária. A configuração usa `application.yml`:

```yaml
# src/main/resources/application.yml
narrativetrace:
  base-packages:
    - com.example.order
    - com.example.payment
  logger-name: myapp.traces
  service-name: order-service
  service-version: "2.0"
  environment: production
```

### Propriedades

| Propriedade | Tipo | Padrão | Propósito |
|---|---|---|---|
| `narrativetrace.base-packages` | `List<String>` | `[]` (vazio — não encapsula nada) | Prefixos de pacote para o encapsulamento de beans |
| `narrativetrace.logger-name` | `String` | `"narrativetrace"` | Nome do logger SLF4J para eventos de trace |
| `narrativetrace.service-name` | `String` | `""` | Metadados de identidade do serviço |
| `narrativetrace.service-version` | `String` | `""` | Metadados de identidade do serviço |
| `narrativetrace.environment` | `String` | `""` | Metadados de identidade do serviço |

### O que acontece

Um `BeanCreatedEventListener<Any>` encapsula beans elegíveis em proxies dinâmicos JDK. Aplicam-se as mesmas regras de elegibilidade do Spring:
- A classe do bean precisa estar em um pacote base configurado
- O bean precisa implementar pelo menos uma interface em um pacote configurado
- Beans sem interfaces correspondentes ficam intocados

Um bean `NarrativeContext` é fornecido automaticamente (marcado `@Secondary`). Quando `narrativetrace-slf4j` está no classpath e `loggerName` não está vazio, o contexto é conectado (wired) com o log de eventos via SLF4J. Defina seu próprio `@Bean NarrativeContext` para sobrescrever o padrão.

### Filtro HTTP

Adicione `narrativetrace-micronaut-http` para o ciclo de vida do trace por requisição:

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.1")
```

O filtro HTTP reativo (`HttpServerFilter`) é autorregistrado ao estar no classpath. Ciclo de vida: reset → estampar metadados HTTP → prosseguir → capturar → exportar → reset.

Um `Slf4jTraceExporter` padrão é fornecido (marcado `@Secondary`). Forneça seu próprio `@Bean TraceExporter` para sobrescrever.

Para o guia de integração completo, veja o [Guia de Integração com Micronaut](guia-de-integracao-com-micronaut.md).

## 7. Configuração do SLF4J

**O NarrativeTrace não é um framework de logging.** Tudo a seguir se conecta *à* sua configuração existente de SLF4J/Logback/Log4j — muda o que é narrado (gerado em vez de escrito manualmente), nunca como, onde ou por meio de quê seus logs são enviados. Veja [Não substitui seu framework de logging](../../LEIAME.md#não-substitui-seu-framework-de-logging) para a versão curta.

A narração de trace através do seu framework de logging existente é automática: quando `narrativetrace-slf4j` está no classpath, `PipelineBootstrap` (a composition root por trás de todo contexto construído por padrão) anexa `Slf4jTraceEventListener` ao caminho síncrono do pipeline de eventos. Sem classe wrapper, sem wiring:

```java
var context = new ThreadLocalNarrativeContext(); // narra via SLF4J quando o módulo está presente
```

Vete a narração sem remover o módulo, via `narrativetrace.narration=off`.

### Nome do logger

Por padrão, os eventos de trace são logados sob o logger SLF4J `narrativetrace`. Roteie os eventos para um logger diferente com a propriedade `narrativetrace.loggerName` (cadeia do ConfigResolver: propriedade do sistema ou `narrativetrace.properties`), ou de forma programática:

```java
var context = new ThreadLocalNarrativeContext(
    new NarrativeTraceConfig(), PipelineBootstrap.createDefault("myapp.traces"));
```

Isso é útil quando aplicações ou módulos diferentes precisam de roteamento de log separado. O nome do logger se propaga para outros componentes:

- **Exportador do Spring web** — quando o contexto usa um nome de logger personalizado, `Slf4jTraceExporter` deriva automaticamente `<loggerName>.export` (por exemplo, `myapp.traces.export`)
- **Anotação do Spring** — `@EnableNarrativeTrace(loggerName = "myapp.traces")` configura o nome para o contexto criado automaticamente
- **Agente** — `loggerName=myapp.traces` nos argumentos do agente ou `narrativetrace.loggerName=myapp.traces` em properties

### Níveis de log

Os eventos são logados sob o logger configurado nestes níveis padrão:

| Tipo de evento | Nível padrão |
|---|---|
| Entrada de método | `TRACE` |
| Retorno de método | `TRACE` |
| Exceção de método | `WARN` |

### Níveis de log personalizados

Sobrescreva os padrões construindo o listener você mesmo e entregando ao contexto um pipeline montado em torno dele:

```java
var listener = new Slf4jTraceEventListener("myapp.traces", Map.of(
    Slf4jTraceEventListener.EventType.ENTRY, Level.DEBUG,
    Slf4jTraceEventListener.EventType.RETURN, Level.DEBUG,
    Slf4jTraceEventListener.EventType.EXCEPTION, Level.ERROR
));
var context = new ThreadLocalNarrativeContext(
    new NarrativeTraceConfig(), new DualPathPipeline(listener));
```

### Campos MDC

`Slf4jTraceEventListener` define campos MDC em cada evento de trace. Os filtros de requisição dos módulos servlet e
Micronaut HTTP também populam campos MDC persistentes com escopo de requisição antes de os métodos traceados rodarem.

| Chave MDC | Valor |
|---|---|
| `traceId` | ID de trace W3C bruto de 32 caracteres |
| `traceName` | Nome legível determinístico de três palavras derivado de `traceId` |
| `spanId` | ID do span atual |
| `parentSpanId` | ID do span pai quando presente |
| `service.name` | Nome de serviço configurado quando presente |
| `service.version` | Versão de serviço configurada quando presente |
| `service.environment` | Ambiente configurado quando presente |
| `host.name` | Nome de host autodetectado (`narrativetrace.capture.resource`, ativado por padrão) |
| `process.pid` | Id do processo (`narrativetrace.capture.resource`) |
| `process.runtime.version` | Versão do runtime Java (`narrativetrace.capture.resource`) |
| `nt.class` | Nome da classe do serviço traceado |
| `nt.method` | Nome do método |
| `nt.package` | Pacote declarante do serviço traceado, quando capturado |
| `nt.depth` | Profundidade de chamada (1 para eventos de enter de nível superior) |
| `nt.threadVirtual` | Se a entrada rodou em uma thread virtual (nome/id da thread são built-ins do `%thread`) |

`traceName` é determinístico, mas não tem unicidade garantida. Use `traceId` para correlação exata e
`traceName` para legibilidade.

Use essas chaves em patterns do logback para obter saída de log estruturada.

**Aparência é configuração de logging; captura é configuração do
NarrativeTrace.** O texto da mensagem narrativa permanece limpo por design —
tudo além dele (identidade do trace, identidade do serviço, classe, método,
profundidade) é publicado como chaves MDC, e seu pattern de logging decide o
que aparece: um pattern sem `%X{...}` mostra a narrativa pura, `%X{nt.class}`
expõe uma chave, um encoder JSON emite todas elas para os agregadores de
log. As configurações do NarrativeTrace controlam apenas o que é *capturado*
— e, portanto, o que *pode* aparecer — nunca como uma linha de log é
formatada.

### Convivendo com logging tradicional

Código bem estruturado — métodos pequenos com nomes claros, valores calculados retornados em vez de logados — não precisa de nenhuma chamada SLF4J. O NarrativeTrace captura tudo a partir das assinaturas de método e dos valores de retorno.

Em código que ainda não está totalmente estruturado assim, você pode misturar chamadas SLF4J tradicionais para coisas como cálculos intermediários ou pontos de decisão que não aparecem nas fronteiras de método. Os dois se intercalam naturalmente:

```java
public class DefaultOrderService implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOrderService.class);

    @Override
    public OrderResult placeOrder(String customerId, String productId, int quantity) {
        log.info("Placing order: customer={}, product={}, qty={}", customerId, productId, quantity);

        var customer = customers.findCustomer(customerId);
        log.debug("Resolved customer {} (tier: {})", customer.name(), customer.tier());

        double unitPrice = catalog.lookupPrice(productId);
        double total = unitPrice * quantity;
        log.debug("Calculated total: {} x {} = {}", unitPrice, quantity, total);

        inventory.reserve(productId, quantity);
        var payment = payments.charge(customerId, total, "tok_" + customer.id());
        log.info("Payment {} confirmed for ${}", payment.transactionId(), payment.amount());

        var orderId = "ORD-%05d".formatted(orderCounter.getAndIncrement());
        return new OrderResult(orderId, payment.transactionId(), total, quantity);
    }
}
```

```
TRACE [narrativetrace]        → OrderService.placeOrder(customerId: C-1234, productId: SKU-MECHANICAL-KB, quantity: 2)
INFO  [DefaultOrderService]   Placing order: customer=C-1234, product=SKU-MECHANICAL-KB, qty=2
TRACE [narrativetrace]        → CustomerService.findCustomer(customerId: C-1234)
TRACE [narrativetrace]        ← returned: Customer[id=C-1234, name=Alice Johnson, tier=GOLD]
DEBUG [DefaultOrderService]   Resolved customer Alice Johnson (tier: GOLD)
TRACE [narrativetrace]        → ProductCatalogService.lookupPrice(productId: SKU-MECHANICAL-KB)
TRACE [narrativetrace]        ← returned: 89.99
DEBUG [DefaultOrderService]   Calculated total: 89.99 x 2 = 179.98
...
INFO  [DefaultOrderService]   Payment TXN-00001 confirmed for $179.98
TRACE [narrativetrace]        ← returned: OrderResult[orderId=ORD-00001, ...]
```

Isso torna o NarrativeTrace fácil de adotar incrementalmente — adicione-o junto do logging existente e depois remova as chamadas de log manuais à medida que você refatora rumo a fronteiras de método mais limpas.

### Frameworks de logging legados

Apps que usam `java.util.logging` ou Log4j 1.x: adicione a ponte SLF4J apropriada ([jul-to-slf4j](https://www.slf4j.org/legacy.html#jul-to-slf4j) ou [log4j-over-slf4j](https://www.slf4j.org/legacy.html#log4j-over-slf4j)) e a saída do NarrativeTrace flui para a sua infraestrutura de logging existente sem alterações.

## 8. Interação entre TracingLevel e SLF4J

TracingLevel (seção 1) e os níveis de log do SLF4J (seção 7) são duas camadas de filtragem independentes. As duas precisam permitir um evento para que ele apareça na saída de log.

### Fluxo de dados

```
chamada de método → filtro TracingLevel → pipeline de eventos
                                            ↓
                                  Slf4jTraceEventListener
                                            ↓
                                  filtro de nível do logger SLF4J → saída de log
```

**TracingLevel** controla o que é **gravado** na árvore de trace. Se uma chamada é filtrada aqui, ela nunca chega ao contexto, aos renderers ou ao SLF4J — simplesmente não existe.

**O nível de log do SLF4J** controla o que é **impresso** nos logs. Os eventos já estão capturados; isso afeta apenas se as instruções de log do `Slf4jTraceEventListener` atravessam o logback/log4j.

### Exemplos de combinação

| TracingLevel | Nível do logback em `narrativetrace` | Resultado |
|---|---|---|
| `DETAIL` | `INFO` | Árvore de trace completa (com valores de parâmetro) em arquivos e renderers, mas as linhas de log de entry/return são suprimidas (elas logam em TRACE). Apenas caminhos de exceção (WARN) aparecem nos logs. |
| `ERRORS` | `TRACE` | Apenas caminhos de exceção são gravados na árvore de trace. Essas exceções são logadas (WARN passa o limiar TRACE). Chamadas normais não produzem nada em lugar nenhum. |
| `NARRATIVE` | `TRACE` | Fluxo de chamadas completo gravado e logado, mas os valores de parâmetro aparecem como strings vazias (NARRATIVE suprime os valores). |
| `DETAIL` | `TRACE` | Tudo gravado e tudo logado — verbosidade máxima. |
| `OFF` | `TRACE` | Nada gravado, nada logado. O gate do TracingLevel bloqueia todos os eventos antes de chegarem ao SLF4J. |

### Qual ajuste para qual objetivo

| Objetivo | Ajuste | Por quê |
|---|---|---|
| Reduzir o ruído de logging | Aumente o nível SLF4J do logger `narrativetrace` | A árvore de trace continua sendo capturada para saída em arquivo e renderers; só o volume no console/arquivo de log diminui. |
| Reduzir o tamanho do arquivo de trace | Diminua o TracingLevel (por exemplo, `NARRATIVE` → `SUMMARY`) | Menos eventos entram na árvore de trace, produzindo uma saída renderizada menor. |
| Reduzir a sobrecarga de CPU/memória | Diminua o TracingLevel | O nível do SLF4J não tem efeito sobre a sobrecarga de captura — o proxy continua interceptando, serializando e gravando toda chamada permitida. Só o TracingLevel evita esse trabalho. |

## 9. Padrões recomendados por ambiente

| Ambiente | Nível sugerido | Saída sugerida |
|---|---|---|
| Trabalho local em features | `DETAIL` | ativado por padrão, `format=markdown` |
| Execuções de teste em CI | `NARRATIVE` ou `SUMMARY` | ativado por padrão, `format=markdown` |
| Produção sensível a performance | `ERRORS` (ou `OFF`) | sem saída de arquivo de teste |

## 10. Configuração do OpenTelemetry

O módulo `narrativetrace-opentelemetry` oferece dois modos de integração. Ambos exigem `opentelemetry-api` no classpath (é `compileOnly` no módulo — você fornece).

### Exportação em lote (post-hoc)

Exporte uma árvore de trace capturada para spans do OTel:

```java
var tracer = openTelemetry.getTracer("narrativetrace");
var exporter = new TraceSpanExporter(tracer);
exporter.export(context.captureTrace().roots());
```

Use isso para saída de teste ou exportação pós-requisição. Baixa sobrecarga durante a execução.

### Decorador de spans ao vivo (tempo real)

Crie spans do OTel a partir de eventos no pipeline:

```java
var tracer = openTelemetry.getTracer("narrativetrace");
var listener = new OtelTraceEventListener(tracer);
// Conecte ao DualPathPipeline como listener síncrono
```

`OtelTraceEventListener` é um `Consumer<TraceEvent>` que cria spans a partir de pares `EnterEvent`/`ExitEvent` com timestamps explícitos e vinculação explícita de `parentSpanId`.

### Atributos de span

Os dois modos definem o mesmo schema de atributos:

| Atributo | Origem |
|---|---|
| `narrative.class` | `MethodSignature.className()` |
| `narrative.method` | `MethodSignature.methodName()` |
| `narrative.trace_id` | `SpanContext.traceId()` |
| `narrative.trace_name` | `TraceNamer.name(SpanContext.traceId().value())` |
| `narrative.param.<nome>` | Valor renderizado de cada parâmetro |
| `narrative.outcome` | Valor de retorno renderizado |
| `narrative.duration_ms` | Duração do node (apenas em lote) |
| `narrative.concurrency.groupId` | ID do grupo fork-join ou fire-and-forget |
| `narrative.concurrency.kind` | `FORK_JOIN` ou `FIRE_AND_FORGET` |
| `narrative.concurrency.threadId` | ID da thread |
| `narrative.concurrency.threadName` | Nome da thread |
| `narrative.concurrency.virtual` | Se a thread é virtual |

## 11. Pontos de extensão

Módulos no classpath podem estender o NarrativeTrace através do
`java.util.ServiceLoader`, declarados em `META-INF/services`. Existem dois
tipos, e eles se ativam de formas diferentes.

**Extensões aditivas** são observadoras. Muitas podem coexistir, a ordem
entre elas não é definida, e a presença no classpath é o que as ativa —
soltar o jar no classpath é toda a instalação.

| Ponto de extensão | Recebe | Chamado em |
|---|---|---|
| `ai.narrativetrace.api.spi.TraceEventListener` | todo evento publicado | a thread que publica (depende da topologia) |
| `ai.narrativetrace.api.spi.ReportContributor` | todo trace acumulado em uma execução | uma vez ao final de uma execução de testes |

Um `TraceEventListener` precisa ser thread-safe; onde ele é anexado é
decisão da topologia, então o mesmo listener funciona sem alterações se o
pipeline for reconfigurado. Os dois tipos são isolados: um que lança uma
exceção é reportado uma vez e ignorado, sem afetar nem as outras extensões
nem a aplicação.

Duas válvulas de escape controlam a descoberta, para deployments que querem
que o classpath deixe de decidir:

```properties
# Desativa completamente a descoberta de extensões
narrativetrace.discovery=off

# Ou desativa apenas provedores nomeados
narrativetrace.discovery.disabled=com.example.NoisyListener,com.example.SlowContributor
```

**Extensões de substituição** fornecem uma topologia de pipeline diferente
através de `ai.narrativetrace.core.pipeline.EventPipelineFactory`. Elas
*nunca* são ativadas pela presença no classpath — uma topologia decide a
durabilidade, então ela só muda quando a configuração a nomeia:

```properties
narrativetrace.pipeline=<nome>
narrativetrace.pipeline.<nome>.<configuracao>=<valor>
```

Sem `narrativetrace.pipeline`, a topologia de caminho duplo padrão é
construída e nenhuma factory chega a ser procurada. Com ela, uma factory que
responda a esse nome precisa estar no classpath: se nenhuma for encontrada, a
inicialização falha em vez de recair silenciosamente em uma topologia com
garantias de durabilidade diferentes.

Por padrão, o listener SLF4J é composto automaticamente no caminho durável
sempre que o módulo `narrativetrace-slf4j` está presente. Defina
`narrativetrace.narration=off` para manter a captura sem narração.

## 12. Ocultação

A introspecção reflexiva trata dados como sensíveis por padrão: um DTO chega
ao renderizador como uma sacola de valores de campos destinada a traces,
logs e exportações — e um `toString()` escrito à mão não o isenta, porque a
própria representação em texto de um tipo nunca é confiável enquanto o tipo
tiver campos. O NarrativeTrace oculta valores em dois eixos independentes,
ambos ativados por padrão.

### Eixo 1 — o nome do campo

Uma correspondência insensível a maiúsculas/minúsculas e a acentos contra um
vocabulário integrado. O vocabulário é **multilíngue e sempre ativo** — não
há uma localidade a selecionar nem nada em que fazer opt-in, porque uma
lista de negação só em inglês não dá uma garantia mais fraca, dá uma
distribuída de outro jeito: ela protege quem, por acaso, nomeia campos no
idioma em que a lista foi escrita.

| Idioma | Palavras |
|---|---|
| Inglês | `password`, `passwd`, `secret`, `token`, `apikey`, `api_key`, `cvv`, `ssn`, `authorization`, `credential`, `privatekey`, `private_key`, `cardnumber`, `card_number`, `jwt`, `cookie`, `setcookie`, `set_cookie`, `sessionid`, `session_id`, `accountnumber`, `account_number`, `routingnumber`, `routing_number`, `pan`, `iban` |
| Espanhol | `contraseña`, `tarjeta`, `cédula`, `rut`, `cuit`, `dni`, `claveAcceso`, `claveSecreta` |
| Português | `senha`, `cpf`, `cnpj`, `cartão` |
| Francês | `motDePasse`, `mot_de_passe`, `nir`, `carteBancaire`, `numeroCarte` |
| Chinês | `密码`, `身份证`, e o pinyin `mima`, `shenfenzheng` |

Os acentos são normalizados dos dois lados, então `contraseña`, `contrasena`
e `CONTRASEÑA` são um único padrão, e não três — incluindo a grafia
decomposta que um sistema de arquivos macOS devolve.

A maioria das palavras casa como **substring**, então `userPassword` e
`numeroTarjeta` são pegas. As curtas, em vez disso, casam nos **limites de
token do identificador**:

`pan` · `iban` · `rut` · `cuit` · `dni` · `senha` · `cpf` · `cnpj` · `nir` ·
`mima`

Cada uma delas está dentro de uma palavra de negócio comum — `cuit` em
`circuitBreaker`, `rut` em `truthValue`, `dni` em `midnightCutoff`, `senha`
na costura de `chosenHash` — e um padrão que apaga essas é um que os times
desligam por completo, o que vaza todo campo em vez de um só. `rutCliente`,
`cuit_empresa` e `DNI` continuam casando; `circuitBreaker` não.

**Na dúvida, estreite.** `clave` e `carte` soltas eram palavras de
correspondência por token nessa lista, pelo mesmo raciocínio de `rut`/`cuit`
acima — até que uma revisão de falante nativo descobriu que a correspondência
por token só protege uma palavra curta de um composto *alheio*, nunca de um do
próprio código-base: `clavePrimaria`/`claveForanea` (espanhol, "chave
primária"/"chave estrangeira" — código de banco de dados em espanhol também
escreve `llavePrimaria`) e `carteGraphique`/`carteRoutiere` (francês, "placa
de vídeo"/"mapa rodoviário") são elas mesmas um token de identificador
inteiro, então a regra antiga apagava essas também. As duas palavras foram
removidas e substituídas pelos compostos específicos que de fato são
credenciais — `claveAcceso`/`clave_acceso`, `claveSecreta`/`clave_secreta`,
`carteBancaire`/`carte_bancaire`, `numeroCarte`/`numero_carte` —, longos o
bastante para serem seguros como substring simples, casados na grafia
camelCase e na snake_case do mesmo jeito que `motDePasse`/`mot_de_passe` já
são.

### Eixo 2 — a forma do próprio valor

Um bearer token chega como `value`, `header`, `data`, ou o terceiro elemento
de uma lista sem nome algum, então o segundo eixo pergunta o que os bytes
dizem. Todo matcher é estrutural — não há heurística de entropia nem regra
de tamanho.

| Forma | Reconhecida por |
|---|---|
| JWT | o prefixo `eyJ` e três segmentos base64url |
| Número de cartão (PAN) | 13–19 dígitos, válido pelo algoritmo de Luhn |
| `Set-Cookie` | `name=value` mais um atributo RFC 6265 |
| RUT chileno | dígito verificador módulo 11; pontos opcionais, **o separador `-` do verificador é obrigatório** |
| CPF brasileiro | 11 dígitos, os dois dígitos verificadores |
| CNPJ brasileiro | 14 dígitos, os dois dígitos verificadores |
| DNI / NIE espanhol | a letra de controle módulo 23 |
| NIR francês | a chave módulo 97, incluindo os `2A`/`2B` da Córsega |
| Documento de identidade chinês | o caractere de controle ISO 7064 *e* uma data de nascimento plausível |

As formas de documento nacional são neutras quanto ao idioma: um CPF é um
CPF independentemente de como o campo que o guarda é chamado, e é
exatamente por isso que o eixo do valor é o certo para um documento cujo
nome de campo costuma estar em um idioma no qual a lista de negação é lida,
mas não foi escrita.

Um parecido que falha no checksum permanece **visível** — um número de
pedido, um número de nota fiscal, uma data. Por essa razão, uma sequência
simples de nove dígitos *não* é tratada como um RUT: o módulo 11 sozinho
ocultaria aproximadamente um em cada onze identificadores de nove dígitos do
seu sistema.

### Ampliando os padrões

Dois ajustes, e eles fazem coisas diferentes.

```properties
# ADICIONA ao vocabulário integrado (propriedade do sistema, ou a variável de
# ambiente NARRATIVETRACE_REDACTION_ADDITIONALPATTERNS)
narrativetrace.redaction.additionalPatterns=betalingskort,kontonummer
```

```java
// SUBSTITUI totalmente o vocabulário integrado
new ValueRenderer(RedactionPolicy.ofPatterns(Set.of("ssn", "internalRef")));
```

`additionalPatterns` é para quem *faz o deploy* do artefato — não precisa de
rebuild, e é lido primeiro da propriedade e depois da variável de ambiente,
nunca de `narrativetrace.properties`. `ofPatterns` é para quem *escreve* a
aplicação. Eles se compõem: as adições continuam se aplicando a uma política
construída por `ofPatterns`, porque substituir o vocabulário é a opinião de
uma aplicação sobre quais dos seus próprios campos são sensíveis, não uma
permissão para desfazer a ampliação feita por um deployment.

> **As adições são padrões de substring**, casadas exatamente como o
> vocabulário de substring integrado. Uma curta carrega a mesma armadilha
> que as palavras casadas por token evitam: adicionar `id` apaga todo
> identificador do trace.

### Desligando

```java
new ValueRenderer(RedactionPolicy.DISABLED);
```

`DISABLED` desliga os dois eixos e as adições. `@NotTraced` continua sendo
respeitado — é uma instrução explícita, não um padrão. Valores ocultos são
renderizados como o literal `[REDACTED]`, nunca como silêncio, para que
quem lê consiga distinguir "oculto" de "nunca capturado".

## Ver também

- [Guia do Plugin de Gradle](guia-do-plugin-de-gradle.md) — referência completa do DSL, modos de interceptação, receitas, DSL em Groovy
- [Guia de Instalação](guia-de-instalacao.md) — dependências, caminhos de integração, configuração do agente Java
- [Guia de Integração com Spring](guia-de-integracao-com-spring.md) — trace de beans, filtro de servlet, propagação com `@Async`, testes
- [Guia de Integração com Micronaut](guia-de-integracao-com-micronaut.md) — trace de beans, filtro HTTP, propriedades de configuração
- [Guia de Anotações](guia-de-anotacoes.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`
- [Guia de Clareza](guia-de-clareza.md) — modelo de pontuação, componentes de NLP, integração com JUnit
