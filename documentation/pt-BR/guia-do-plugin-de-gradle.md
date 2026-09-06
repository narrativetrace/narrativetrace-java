<!-- source: documentation/gradle-plugin-guide.md blob 0bd33c301640 | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Guia do Plugin de Gradle do NarrativeTrace

[English](../gradle-plugin-guide.md) | [Español](../es/guia-del-plugin-de-gradle.md) | **Português** | [简体中文](../zh-CN/Gradle插件指南.md)

O plugin `ai.narrativetrace` para Gradle é a forma recomendada de usar o NarrativeTrace em projetos Gradle. Ele cuida do gerenciamento de dependências, das flags do compilador, da configuração de testes e das quality gates — tudo a partir de um único bloco DSL.

## Sumário

- [Início Rápido](#início-rápido)
- [O Que o Plugin Faz Automaticamente](#o-que-o-plugin-faz-automaticamente)
- [Propriedades](#propriedades) — [enabled](#enabled) | [mode](#mode) | [testFramework](#testframework) | [scope](#scope) | [format](#format) | [tracingLevel](#tracinglevel) | [outputDir](#outputdir) | [approval](#approval) | [approvedDir](#approveddir)
- [Bloco de Módulos](#bloco-de-módulos)
- [Bloco do Agente](#bloco-do-agente)
- [Bloco de Clareza](#bloco-de-clareza)
- [Tarefas](#tarefas) — [clarityCheck](#claritycheck) | [clarityScan](#clarityscan) | [glossaryScan](#glossaryscan) | [approveNarratives](#approvenarratives)
- [Requisitos](#requisitos)
- [Resolução de Versão](#resolução-de-versão)
- [Receitas Comuns](#receitas-comuns)
- [DSL do Groovy](#dsl-do-groovy)
- [Validação](#validação)
- [Referência Completa do DSL](#referência-completa-do-dsl)

## Início Rápido

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.0"
}
```

É só isso. Execute `./gradlew test` e a saída de traces aparece em `build/narrativetrace/`.

Você não precisa adicionar nenhuma dependência do JUnit. Com o valor padrão `testFramework = "junit5"`, o plugin muda a task de testes para a JUnit Platform *e* coloca a engine do Jupiter (`org.junit.jupiter:junit-jupiter-engine`, fixada na versão contra a qual o NarrativeTrace é testado) em `testRuntimeOnly`, porque a plataforma se recusa a iniciar sem uma. Declarar sua própria versão do JUnit continua funcionando — a resolução de conflitos do Gradle escolhe a maior das duas.

## O Que o Plugin Faz Automaticamente

| Ação | Detalhe |
|---|---|
| Adiciona a flag do compilador `-parameters` | Em todas as tasks `JavaCompile`, ignorada se já estiver presente |
| Adiciona dependências | Baseado em `mode`, `modules` e `testFramework`; a versão é detectada automaticamente a partir do JAR do plugin |
| Adiciona a engine da JUnit Platform | Somente com `testFramework = "junit5"`: `testRuntimeOnly org.junit.jupiter:junit-jupiter-engine:5.11.4`, para que o primeiro `gradle test` seja executado em vez de falhar com *"Cannot create Launcher without at least one TestEngine"*. O JUnit 4 não recebe nenhuma — ele não é movido para a plataforma |
| Define propriedades da JVM de teste | `narrativetrace.output=true`, `narrativetrace.outputDir` e, opcionalmente, `narrativetrace.format`, `narrativetrace.level`, o par do glossário (`glossary=true`) e o par de aprovação (`approval=true`) |
| Registra a task `clarityCheck` | Lê `clarity-results.json`, aplica os limiares, conectada ao ciclo de vida `check` |
| Registra a task `clarityScan` | Análise de clareza independente a partir das classes compiladas (não requer testes) |
| Registra a task `glossaryScan` | Coleta independente do glossário a partir das classes compiladas, incluindo templates de anotações |
| Registra a task `approveNarratives` | Promove narrativas `*.received.nt` revisadas para baselines `*.approved.nt` |
| Configura o argumento de JVM do agente | Quando `mode = "agent"`: resolve o JAR do agente, adiciona `-javaagent` às tasks Test |

## Propriedades

### `enabled`

Controla se o plugin faz alguma coisa. Quando `false`, nenhuma task é registrada, nenhuma dependência é adicionada, nenhuma flag do compilador é definida.

```kotlin
narrativeTrace {
    enabled.set(false)  // desativar neste subprojeto
}
```

Padrão: `true`

### `mode`

Seleciona a estratégia de interceptação. Isso determina qual dependência da biblioteca principal o plugin adiciona.

| Modo | Dependências adicionadas (além de core + clarity + diagrams + framework de testes) |
|---|---|
| `proxy` | `narrativetrace-proxy` — proxy dinâmico JDK, baseado em interface |
| `agent` | `narrativetrace-agent` — instrumentação de bytecode, sem necessidade de interface |
| `spring` | `narrativetrace-spring` + `narrativetrace-proxy` — encapsulamento automático via BeanPostProcessor do Spring |

Padrão: `"proxy"`

**O modo proxy** é o mais simples: envolva os serviços com `NarrativeTraceProxy.trace()` no código de teste.

**O modo agent** além disso cria uma configuração `narrativeTraceAgent`, resolve o JAR do agente e adiciona `-javaagent` a todas as tasks `Test`. Use o bloco `agent { }` para especificar quais pacotes instrumentar.

**O modo spring** adiciona o BeanPostProcessor do Spring que encapsula automaticamente os beans elegíveis. Use `@EnableNarrativeTrace` na sua classe de configuração.

### `testFramework`

| Valor | Dependência adicionada |
|---|---|
| `"junit5"` | `narrativetrace-junit5` |
| `"junit4"` | `narrativetrace-junit4` |

Padrão: `"junit5"`

### `scope`

Controla qual configuração do Gradle recebe as dependências das bibliotecas.

| Scope | Dependências de biblioteca | Dependência do framework de testes |
|---|---|---|
| `"test"` | `testImplementation` | `testImplementation` |
| `"production"` | `implementation` | `testImplementation` (sempre) |

Padrão: `"test"`

Use `"production"` ao implantar o NarrativeTrace em uma aplicação em execução (por exemplo, com o filtro de servlet para tracing por requisição). A dependência do framework de testes sempre permanece em `testImplementation`, independentemente do scope.

### `format`

Formato de saída dos arquivos de trace. Só é repassado às tasks de teste quando definido explicitamente — se omitido, a extensão do JUnit usa seu próprio padrão (markdown).

| Valor | Descrição |
|---|---|
| `"markdown"` | Markdown legível para humanos com diagramas Mermaid |
| `"text"` | Texto simples com indentação |
| `"mermaid"` | Somente diagrama de sequência Mermaid |
| `"plantuml"` | Somente diagrama de sequência PlantUML |

Sem convenção padrão — omita para deixar a extensão do JUnit decidir.

### `tracingLevel`

Controla quanto detalhe é capturado nos traces. Só é repassado às tasks de teste quando definido explicitamente.

| Valor | Comportamento |
|---|---|
| `"OFF"` | Nenhum trace capturado |
| `"ERRORS"` | Somente caminhos de exceção capturados |
| `"SUMMARY"` | Entrada raiz, folha mais profunda e cadeias de exceção completas |
| `"NARRATIVE"` | Fluxo de chamadas completo, valores de parâmetros suprimidos |
| `"DETAIL"` | Fluxo de chamadas completo com valores de parâmetros e valores de retorno |

Sem convenção padrão — omita para deixar o runtime decidir.

### `outputDir`

Diretório para arquivos de trace, relatórios de clareza e diagramas.

Padrão: `layout.buildDirectory.dir("narrativetrace")` (ou seja, `build/narrativetrace/`)

### `approval`

Modo de aprovação para narrativas estruturais. Quando `true`, o plugin repassa `narrativetrace.approval=true` e `narrativetrace.approvedDir` às tasks de teste: um teste que **passa**, mas cuja estrutura traçada difere da sua baseline `*.approved.nt` já commitada, falha com um diff legível, e a estrutura atual é escrita ao lado da baseline como `*.received.nt` para revisão. Aceite uma mudança intencional com a task [`approveNarratives`](#approvenarratives).

Padrão: `false`

```kotlin
narrativeTrace {
    approval.set(true)
}
```

### `approvedDir`

Diretório de baselines narrativas commitadas, organizado como `<dir>/<TestClassSimpleName>/<test_method_slug>.approved.nt` — as mesmas regras de diretório por classe e slug de qualquer outro artefato por teste.

Padrão: `layout.projectDirectory.dir("src/test/narratives")`

## Bloco de Módulos

Ativação granular e opcional de módulos adicionais do NarrativeTrace. Todos são `false` por padrão.

```kotlin
narrativeTrace {
    modules {
        slf4j.set(true)        // narrativetrace-slf4j
        micrometer.set(true)   // narrativetrace-micrometer
        servlet.set(true)      // narrativetrace-servlet
        springWeb.set(true)    // narrativetrace-spring-web + narrativetrace-servlet
    }
}
```

| Flag | Artefato | Notas |
|---|---|---|
| `slf4j` | `narrativetrace-slf4j` | Encaminha os eventos de trace através do SLF4J/logback |
| `micrometer` | `narrativetrace-micrometer` | Propagação de trace entre threads via context-propagation do Micrometer |
| `servlet` | `narrativetrace-servlet` | Filtro de servlet para o ciclo de vida do trace por requisição |
| `springWeb` | `narrativetrace-spring-web` + `narrativetrace-servlet` | Auto-configuração do Spring para o filtro de servlet; adiciona automaticamente o módulo servlet |

Definir `springWeb` quando `mode` não é `"spring"` produz um aviso (mas não falha).

## Bloco do Agente

Só é relevante quando `mode = "agent"`. Configura quais pacotes o agente de bytecode instrumenta.

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        packages.set(listOf("com.example.app", "com.example.shared"))
    }
}
```

Quando packages está vazio (o padrão), o agente instrumenta todas as classes. Os pacotes são unidos com `;` no argumento `-javaagent`.

O JAR do agente é resolvido de forma preguiçosa, no momento da execução dos testes, a partir de uma configuração dedicada do Gradle chamada `narrativeTraceAgent`.

## Bloco de Clareza

Configura a quality gate `clarityCheck`.

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.80)       // falha se algum cenário pontuar abaixo de 0.80
        maxHighIssues.set(0)     // falha se algum cenário tiver problemas HIGH
        maxSuiteIssues.set(0)    // falha em qualquer problema no nível da suíte (ex.: violação de vocabulário)
        warnOnly.set(true)       // registra avisos em vez de falhar
    }
}
```

### `minScore`

Pontuação mínima de clareza geral (0.0–1.0). Qualquer cenário abaixo desse limiar faz o build falhar.

Padrão: `0.0` (sem gate)

### `maxHighIssues`

Número máximo de problemas de severidade HIGH por cenário. Ultrapassar isso faz o build falhar.

Padrão: `Integer.MAX_VALUE` (sem gate)

### `maxSuiteIssues`

Número máximo de problemas no nível da suíte — problemas que pertencem à execução inteira, e não a um único cenário, como violações de vocabulário `non-canonical-term` vindas da coleta do glossário. Ultrapassar isso faz o build falhar; abaixo do limiar, eles são registrados como avisos. Violações de vocabulário só são relatadas quando um `glossary.json` commitado existe antes da execução.

Padrão: `Integer.MAX_VALUE` (somente consultivo)

### `warnOnly`

Quando `true`, violações de limiar geram avisos em vez de falhas de build.

Padrão: `false`

## Tarefas

### `clarityCheck`

Lê `build/narrativetrace/clarity-results.json` (produzido pela extensão do JUnit durante `test`) e aplica os limiares configurados.

- **Depende de**: `test`
- **Conectada a**: `check` (executa automaticamente com `./gradlew check`)
- **Ignora silenciosamente** quando `clarity-results.json` não existe (por exemplo, nenhum teste foi executado)

### `clarityScan`

Analisa a clareza de nomes das classes compiladas sem executar testes. Usa reflexão para varrer os arquivos de classe e produzir um relatório de clareza.

- **Depende de**: `classes`
- **Classpath**: `testRuntimeClasspath` (precisa do módulo clarity)
- **Argumentos**: `--classes-dir` e `--output-dir` derivados da configuração do plugin
- **Saída**: `clarity-scan-report.md` e `clarity-scan-results.json` em `outputDir` — deliberadamente distintos dos artefatos da execução de testes (`clarity-report.md` / `clarity-results.json`), de modo que uma varredura nunca sobrescreve o que a gate `clarityCheck` lê
- **Escopo**: tipos públicos e package-private; classes aninhadas privadas, anônimas, locais e lambda são ignoradas por serem detalhes de implementação

Execute isoladamente:

```bash
./gradlew clarityScan
```

### `glossaryScan`

Coleta o glossário de domínio a partir das classes compiladas sem executar testes.
Este é o **único** modo que coleta templates `@Narrated` / `@OnError`:
um trace capturado carrega a narração com os valores dos parâmetros já
interpolados, então coletar ali escreveria dados de runtime em um
arquivo commitado.

- **Depende de**: `classes`
- **Classpath**: `testRuntimeClasspath` (precisa do módulo do glossário)
- **Argumentos**: `--classes-dir` (`build/classes/java/main`), `--glossary-dir`
  (a raiz do repositório), `--output-dir` a partir da configuração do plugin

```bash
./gradlew glossaryScan
```

Para coletar durante a execução dos testes — a partir de traces reais, sem
templates — habilite na extensão:

```kotlin
narrativeTrace {
    glossary.set(true)
}
```

Isso define `narrativetrace.glossary=true` e aponta
`narrativetrace.glossaryDir` para a raiz do repositório. Está desativado por
padrão porque escreve `glossary.json` / `glossary.md` fora do diretório
de build.

Visões de trace traduzidas não são uma task de build: conecte um `TranslationSubscriber` do módulo de glossário ao pipeline de eventos (locale + `glossary.json` commitado, localizado via `narrativetrace.glossary.path` ou o classpath) e cada execução — de teste ou de produção — emite seu stream traduzido ao vivo, para o logger `narrativetrace.i18n.<locale>` ou como arquivos Markdown por trace. A antiga task `translateTraces` e a propriedade `translationLocales` foram aposentadas em favor deste formato de pipeline.

### `approveNarratives`

Aceita mudanças estruturais intencionais no [modo de aprovação](#approval): promove cada arquivo `*.received.nt` revisado sob `approvedDir` para sua baseline `*.approved.nt`.

```bash
./gradlew approveNarratives
```

- **Grupo**: `verification`
- Sempre seguro de executar — imprime `Approved: <path>` para cada baseline promovida, ou `No received narratives to approve.` quando não há nada para promover
- Nunca executa testes: revise os arquivos received primeiro, aprove e então rode a suíte novamente até ficar verde

## Requisitos

O plugin verifica seu ambiente quando é aplicado, e falha com uma linha
nomeando o requisito, em vez de deixar o build chegar a um erro confuso
mais tarde:

- **Gradle 8.0 ou mais recente.** Desenvolvido e testado contra o 8.14.2.
- **Java 17 ou mais recente** — o toolchain configurado quando o build define um,
  senão a JVM que está executando o Gradle. O NarrativeTrace é escrito em Java 17
  (records, interfaces seladas, switches com pattern matching), então isso é um
  requisito de nível de linguagem, não uma preferência.

Nenhuma das duas verificações roda quando `enabled.set(false)`, e uma string de
versão que o plugin não consegue interpretar é tratada como aceitável — um
palpite nunca deve interromper um build.

## Resolução de Versão

O plugin detecta automaticamente sua versão a partir do JAR do plugin em tempo de execução, e essa versão é usada para todas as dependências gerenciadas do NarrativeTrace. Plugin e bibliotecas são lançados em lockstep a partir de uma única versão em `gradle.properties`, então o plugin sempre instala bibliotecas exatamente da sua própria versão.

Se a versão não puder ser detectada (por exemplo, ao executar a partir do código-fonte sem o arquivo de properties), o plugin recorre a `0.0.0-unknown`.

Para fixar uma versão diferente — por exemplo, para fazer dogfooding de um `-SNAPSHOT` local cujas bibliotecas estão à frente da versão embutida no plugin — defina `libraryVersion`:

```kotlin
narrativeTrace {
    libraryVersion.set("0.2.0-SNAPSHOT")
}
```

Quando definida, toda dependência gerenciada do NarrativeTrace é resolvida nessa versão; quando não definida (o padrão), a versão embutida é usada e o comportamento não muda.

## Receitas Comuns

### Tracing somente em testes (padrão)

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.0"
}
```

Adiciona todas as dependências em `testImplementation`. O código de produção não tem nenhuma classe do NarrativeTrace no classpath.

### Tracing em produção com filtro de servlet

```kotlin
narrativeTrace {
    scope.set("production")
    mode.set("spring")
    modules {
        springWeb.set(true)
        slf4j.set(true)
    }
}
```

Adiciona dependências em `implementation` para que o filtro de servlet e a auto-configuração do Spring fiquem disponíveis em tempo de execução.

### Instrumentação baseada em agente

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        packages.set(listOf("com.example.app"))
    }
}
```

O plugin cria uma configuração `narrativeTraceAgent`, resolve o JAR do agente e adiciona `-javaagent:path/to/agent.jar=com.example.app` a todas as tasks Test.

### CI com portas de clareza rigorosas

```kotlin
narrativeTrace {
    tracingLevel.set("NARRATIVE")
    clarity {
        minScore.set(0.80)
        maxHighIssues.set(0)
    }
}
```

`./gradlew check` falha se algum cenário tiver clareza abaixo de 0.80 ou qualquer problema HIGH.

### Narrativas com testes de aprovação

```kotlin
narrativeTrace {
    approval.set(true)
}
```

A primeira execução escreve a estrutura livre de valores de cada cenário como `src/test/narratives/<TestClass>/<scenario>.received.nt` e falha; revise, execute `./gradlew approveNarratives`, commite os arquivos `*.approved.nt`. A partir daí, qualquer desvio estrutural em um teste que passa — uma chamada nova, uma chamada removida, um resultado alterado — faz o build falhar com um diff legível até ser explicitamente aprovado. As baselines são livres de valores, então nunca vazam dados de runtime e sobrevivem a mudanças que afetam somente dados.

### Desativar em um subprojeto

```kotlin
narrativeTrace {
    enabled.set(false)
}
```

Nada acontece — sem dependências, sem tasks, sem flags de compilador.

### Consumindo um checkout local (composite build)

Antes de os artefatos estarem em um repositório que você consiga resolver — ou quando você quiser testar uma mudança no NarrativeTrace contra o seu próprio código — aponte seu projeto para um checkout irmão. Isso precisa das **duas** metades, e nenhuma delas é opcional:

```kotlin
// settings.gradle.kts
pluginManagement {
    // 1. Resolve o próprio plugin: `id("ai.narrativetrace")` sem versão.
    includeBuild("../narrative-trace-java")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// 2. Substitui as coordenadas de biblioteca `ai.narrativetrace:*` que o plugin
//    adiciona por você. Sem isso, o build falha ao resolver artefatos que estão
//    bem ali, em disco.
includeBuild("../narrative-trace-java")

rootProject.name = "my-app"
```

```kotlin
// build.gradle.kts
plugins {
    java
    id("ai.narrativetrace")  // sem versão — o build incluído a fornece
}

repositories { mavenCentral() }
```

Por que as duas: `pluginManagement { includeBuild(...) }` e um `includeBuild(...)` de nível superior são dois mecanismos diferentes. O primeiro torna resolvível o *marker* do plugin; o segundo faz o Gradle substituir por dependências de projeto as coordenadas de *biblioteca* que o `DependencyConfigurator` adiciona (`ai.narrativetrace:narrativetrace-core` e afins). Com apenas o primeiro, o plugin é aplicado e o build então falha na resolução de dependências.

Este é um comportamento normal do Gradle, não algo que o NarrativeTrace faz, mas é o plugin quem coloca essas coordenadas de biblioteca no seu build, então é aí que você se depara com isso.

Duas coisas que vale a pena saber:

- **Nada precisa ser publicado.** Não execute `publishToMavenLocal` — a substituição do composite troca as coordenadas antes da resolução, então uma publicação local só adicionaria uma cópia obsoleta que pode encobrir suas alterações.
- **A versão não precisa coincidir.** A substituição é por grupo e nome do módulo, então o `0.2.0-SNAPSHOT` do build incluído satisfaz qualquer versão que o plugin peça. Se você preferir resolver artefatos reais e pular o composite, defina `libraryVersion` — veja [Resolução de Versão](#resolução-de-versão).

### Configuração multiprojeto

Aplique o plugin somente nos subprojetos que têm testes:

```kotlin
// settings.gradle.kts
rootProject.name = "my-app"
include("core", "web", "shared")
```

```kotlin
// core/build.gradle.kts
plugins {
    java
    id("ai.narrativetrace")
}
```

Cada subprojeto ganha suas próprias tasks `clarityCheck` e `clarityScan`.

## DSL do Groovy

Todos os exemplos acima usam o DSL do Kotlin. O equivalente em Groovy:

```groovy
plugins {
    id 'ai.narrativetrace' version '0.2.0'
}

narrativeTrace {
    mode = 'proxy'
    testFramework = 'junit5'
    scope = 'test'

    modules {
        slf4j = true
    }

    clarity {
        minScore = 0.80
        maxHighIssues = 0
    }
}
```

## Validação

O plugin valida todas as propriedades string em tempo de configuração (dentro de `afterEvaluate`). Valores inválidos produzem uma mensagem de erro clara:

```
> Invalid narrativeTrace mode 'invalid'. Valid values: proxy, agent, spring
> Invalid narrativeTrace scope 'compile'. Valid values: test, production
> Invalid narrativeTrace format 'xml'. Valid values: markdown, text, mermaid, plantuml
> Invalid narrativeTrace tracingLevel 'VERBOSE'. Valid values: OFF, ERRORS, SUMMARY, NARRATIVE, DETAIL
```

## Referência Completa do DSL

Ponto de partida para copiar e colar, com todas as propriedades mostradas:

```kotlin
narrativeTrace {
    enabled.set(true)                          // padrão: true
    mode.set("proxy")                          // "proxy" (padrão) | "agent" | "spring"
    testFramework.set("junit5")               // "junit5" (padrão) | "junit4"
    scope.set("test")                          // "test" (padrão) | "production"
    format.set("markdown")                     // "markdown" | "text" | "mermaid" | "plantuml"
    tracingLevel.set("DETAIL")                 // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
    outputDir.set(layout.buildDirectory.dir("narrativetrace"))
    glossary.set(false)                        // padrão: false — coleta glossary.json ao final da suíte
    approval.set(false)                        // padrão: false — verifica a estrutura contra baselines commitadas
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))
    // libraryVersion.set("0.2.0-SNAPSHOT")    // padrão: versão embutida do plugin — sobrescreva para dogfooding de um snapshot

    modules {                                  // ativação granular e opcional (tudo false por padrão)
        slf4j.set(false)
        micrometer.set(false)
        servlet.set(false)
        springWeb.set(false)                   // implica servlet
    }

    agent {                                    // só relevante quando mode = "agent"
        packages.set(listOf("com.example.app"))
    }

    clarity {
        minScore.set(0.80)                     // padrão: 0.0 (sem gate)
        maxHighIssues.set(0)                   // padrão: Integer.MAX_VALUE (sem gate)
        maxSuiteIssues.set(0)                  // padrão: Integer.MAX_VALUE (somente consultivo)
        warnOnly.set(false)                    // padrão: false
    }
}
```

## Veja Também

- [Guia de Instalação](guia-de-instalacao.md) — configuração manual sem o plugin, caminhos de integração
- [Guia de Configuração](guia-de-configuracao.md) — níveis de tracing, configuração de JUnit/Spring/SLF4J
- [Guia de Clareza](guia-de-clareza.md) — modelo de pontuação, componentes de NLP, formato do relatório de clareza
- [Guia de Integração com Spring](guia-de-integracao-com-spring.md) — `@EnableNarrativeTrace`, propagação com `@Async`, filtro de servlet
- [Guia de Anotações](guia-de-anotacoes.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`
