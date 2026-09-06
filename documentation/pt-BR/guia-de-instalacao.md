<!-- source: documentation/installation-guide.md blob bc69d31d48cf | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Guia de instalação do NarrativeTrace Java

[English](../installation-guide.md) | [Español](../es/guia-de-instalacion.md) | **Português** | [简体中文](../zh-CN/安装指南.md)

Este guia cobre a instalação e o wiring do NarrativeTrace Java em um projeto JVM.

## Pré-requisitos

- Java 17+
- Build com Gradle

## Compatibilidade

Versões a partir da 0.2.0. "Incluída" significa que o módulo depende dela e o
Gradle a resolve para você; "sua" significa que o módulo compila contra ela,
mas não depende dela — você já a tem, e o NarrativeTrace usa qualquer versão
que você trouxer.

| Requisito | Versão | Quem fornece |
|---|---|---|
| Java | **17+** — compilado e testado na 17; o JDK 21 é exercitado por um job de CI agendado (os testes de threads virtuais só rodam lá) | sua |
| Gradle (para o plugin) | **8.0+** — desenvolvido e testado contra a 8.14.2 | sua |
| JUnit 5 | 5.11.4 (`narrativetrace-junit5` expõe `junit-jupiter-api` como `api`) | incluída |
| JUnit 4 | 4.13.2 | incluída |
| Spring Framework | 6.2.3 — ou seja, Spring Boot 3.x | incluída |
| Micronaut | 4.7.6 | incluída |
| Jakarta Servlet | 6.0 (`narrativetrace-servlet`) | sua |
| SLF4J | 2.0.16 | incluída |
| Micrometer context-propagation | 1.1.2 (`narrativetrace-micrometer`; também necessário para o `ContextPropagatingTaskDecorator`) | incluída pelo módulo micrometer, sua se você usar o decorator sem ele |
| OpenTelemetry API | 1.46.0 (`narrativetrace-opentelemetry`) | sua |
| ASM | 9.7.1 — empacotado (shaded) dentro de `narrativetrace-agent`, nunca no seu classpath | incluída |

### Plataformas de execução

A tabela acima é sobre versões; esta é sobre *onde a biblioteca é executada*.

| Plataforma | Suportado | Notas |
|---|---|---|
| JVMs de servidor e desktop (HotSpot, OpenJ9, GraalVM na JVM) | **Sim** | O alvo testado |
| Android | **Não** | Não testado, e não apenas por falta de testes — veja abaixo |
| iOS e outras plataformas Apple | **N/A** | Use a edição Swift |
| GraalVM native image | **Não testado** | Os caminhos de proxy e agente dependem de reflection; nenhum metadado de reachability é distribuído hoje |

**Por que o Android é um "não" e não um "ainda não".** Três mecanismos
degradam *silenciosamente* sob a minificação do R8/ProGuard: a descoberta via
SPI perde suas entradas `META-INF/services`, então as extensões nunca
carregam; o bootstrap do pipeline resolve o listener do SLF4J pelo nome,
então a narração síncrona desaparece; e a renderização de valores usa
reflection sobre campos e getters, então uma narrativa é renderizada como
`→ a.b(c: "x")` em vez de nomes legíveis. Nenhum desses casos falha
ruidosamente, o que faz de "parecia funcionar num build de debug" o pior
resultado possível. Um suporte honesto ao Android exigiria regras de keep
para o consumidor distribuídas junto com o produto e um CI real para Android,
e nenhum dos dois existe ainda. O `narrativetrace-agent` nunca pode funcionar
lá de jeito nenhum — o Android não tem `java.lang.instrument`.

A biblioteca não *trava* mais em um runtime sem `java.lang.ProcessHandle` (o
process id é simplesmente reportado como ausente, o que o schema permite),
mas não travar não é a mesma coisa que ser suportado.

O plugin impõe as duas primeiras linhas no momento da aplicação (apply): um
Gradle não suportado ou um build direcionado a uma versão do Java abaixo da
17 falha imediatamente, nomeando o requisito, em vez de falhar depois, dentro
da biblioteca.

Dois modos de falha que vale a pena nomear, porque nenhuma das mensagens de
erro aponta para a causa:

- **`arg0`, `arg1` em vez dos nomes dos parâmetros** — a flag do compilador
  `-parameters` está faltando. É um requisito obrigatório, não um detalhe
  agradável; veja o passo 1 abaixo.
- **`NoClassDefFoundError` em tempo de execução** — falta no classpath de
  runtime uma dependência marcada como "sua". Os módulos compilam contra
  essas APIs deliberadamente, para que uma aplicação sem servlet ou sem OTel
  não carregue nenhuma dependência extra.

## Início rápido com o plugin Gradle

O plugin Gradle cuida de todo o wiring automaticamente — dependências, flags
do compilador e configuração da JVM de teste:

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.0"
}
```

Só isso — nenhuma dependência do JUnit é necessária também: com
`testFramework = "junit5"` o plugin coloca o Jupiter engine em
`testRuntimeOnly`, porque a JUnit Platform que ele configura se recusa a
iniciar sem um. Rode `./gradlew test` e a saída de traces aparece em
`build/narrativetrace/`.

Para impor limiares de qualidade dos nomes:

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.80)
        maxHighIssues.set(0)
    }
}
```

Agora `./gradlew check` falha se a pontuação de clareza de algum cenário
cair abaixo de 0.80 ou se houver qualquer problema de severidade HIGH.

Veja o [Guia do plugin de Gradle](guia-do-plugin-de-gradle.md) para a
referência completa da DSL, modos de interceptação, opt-in de módulos e
receitas.

## Configuração manual

As seções abaixo cobrem a instalação manual para projetos que não usam o
plugin Gradle.

### Pré-requisitos

- Metadados de parâmetros do compilador habilitados (`-parameters`)

## 1. Habilite a retenção de nomes de parâmetros

O NarrativeTrace usa os nomes dos parâmetros dos métodos na saída de
traces. Sem `-parameters`, os traces mostram `arg0`, `arg1`, etc.

```kotlin
// build.gradle.kts
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
```

## 2. Adicione as dependências

Comece com o stack mínimo e depois adicione somente as integrações que você
precisa.

```kotlin
dependencies {
    // Mínimo
    implementation("ai.narrativetrace:narrativetrace-core:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.2.0")

    // Integrações opcionais
    testImplementation("ai.narrativetrace:narrativetrace-junit5:0.2.0")
    testImplementation("ai.narrativetrace:narrativetrace-junit4:0.2.0")  // para JUnit 4
    implementation("ai.narrativetrace:narrativetrace-spring:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-slf4j:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-diagrams:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-clarity:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-opentelemetry:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-micrometer:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-agent:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-servlet:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-spring-web:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-micronaut:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.0")
}
```

## 3. Escolha um caminho de integração

### Opção A: proxy JDK (funciona em qualquer aplicação Java)

```java
var context = new ThreadLocalNarrativeContext();
var tracedOrderService = NarrativeTraceProxy.trace(orderService, OrderService.class, context);

tracedOrderService.placeOrder("C-1234", "SKU-KB", 2);
System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
context.reset();
```

Use isso quando os serviços são baseados em interface.

### Opção B: encapsulamento automático com Spring

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.myapp"})
public class AppConfig {}
```

Use isso quando você quiser que o pós-processamento de beans encapsule beans
elegíveis automaticamente.

### Opção B2: encapsulamento automático com Micronaut

```yaml
# src/main/resources/application.yml
narrativetrace:
  base-packages:
    - com.example.myapp
```

Nenhuma anotação de habilitação é necessária — o módulo
`narrativetrace-micronaut` é descoberto automaticamente no classpath. Todos
os beans cuja classe e interfaces correspondem aos pacotes configurados são
encapsulados em proxies de tracing.

Veja o [Guia de integração com Micronaut](guia-de-integracao-com-micronaut.md)
para a configuração do filtro HTTP e detalhes de configuração.

### Opção C: contexto automático do JUnit 5 + saída de traces

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        var orderService = NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);
        orderService.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

Funcionalidades:
- `NarrativeContext` por teste via injeção de parâmetro
- Impressão automática do trace de falha no console
- Nome do cenário derivado do nome do método de teste (`customerPlacesOrder`
  → "Customer places order")
- Com `narrativetrace.output=true`: grava `.md`, `.json`, `.mmd` por teste,
  além de um `clarity-report.md` no nível da suíte

### Opção D: contexto automático do JUnit 4 + saída de traces

```java
public class OrderServiceTest {
    @Rule
    public NarrativeTraceRule narrativeTrace = new NarrativeTraceRule();

    @Test
    public void customerPlacesOrder() {
        NarrativeContext context = narrativeTrace.context();
        var orderService = NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);
        orderService.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

Funcionalidades:
- `NarrativeContext` por teste via `narrativeTrace.context()`
- Impressão automática do trace de falha no console
- Nome do cenário derivado do nome do método de teste (`customerPlacesOrder`
  → "Customer places order")
- Com `-Dnarrativetrace.output=true`: grava `.md`, `.json`, `.mmd` por teste
- Adicione `@ClassRule` com `NarrativeTraceClassRule` para um
  `clarity-report.md` no nível da suíte e um resumo no console

> **Vendo o trace de falha no seu terminal:** a "impressão do trace de falha
> no console" mencionada acima é escrita na saída padrão (standard output) do
> processo de teste, que o Gradle captura no relatório XML/HTML — um
> terminal comum não mostra nada. Para exibi-la ao vivo no console, habilite
> o logging de standard-stream na task `test`:
>
> ```kotlin
> tasks.test {
>     testLogging.showStandardStreams = true
> }
> ```
>
> Os arquivos de trace em `build/narrativetrace/` são gravados de qualquer
> forma; isso afeta apenas o que o console mostra.

A configuração usa propriedades de sistema (o JUnit 4 não tem
`junit-platform.properties`):
- `narrativetrace.output` — `true`/`false` (padrão: `false`)
- `narrativetrace.outputDir` — caminho (padrão: `build/narrativetrace`)
- `narrativetrace.format` — `markdown`/`text`/`mermaid`/`plantuml` (padrão: `markdown`)

### Opção E: agente Java (sem wiring de proxy)

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.* -jar your-app.jar
```

Use isso quando você quiser instrumentação de bytecode para classes sob
prefixos de pacote selecionados. Quando nenhum argumento de CLI é fornecido,
o agente recorre a `narrativetrace.properties` no classpath.

Formato do argumento do agente: `packages=<pkg1>;<pkg2>;...`

Padrões de pacote suportam wildcards:

| Padrão | Corresponde a |
|---|---|
| `com.example.*` | Todas as classes sob `com.example` e subpacotes |
| `com.example.**` | O mesmo que `.*` (ambos correspondem a todos os subpacotes) |
| `com.example` | O mesmo que `com.example.*` (prefixo simples, com verificação de limites) |

Múltiplos pacotes:

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.app.*;com.example.shared.* -jar app.jar
```

Os separadores de pacotes são ponto e vírgula (`;`), não vírgulas. Chaves
desconhecidas são ignoradas; chaves duplicadas são rejeitadas.

#### A narração precisa de um provedor SLF4J — o agente nunca traz um

O jar `-standalone` do agente empacota tudo que precisa, *exceto* um backend
de logging. Isso é deliberado: o stack de logging do seu host é seu, e um
agente que contrabandeasse um segundo provedor entraria em conflito com o
que você já tem.

Assim, um host mínimo sem nenhum provedor no classpath vê o SLF4J dizer
isso, uma vez, ao iniciar:

```text
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
```

Nada está quebrado — os traces continuam sendo capturados, e
`captureTrace()` continua retornando-os — mas nada é escrito em um log. Duas
soluções de uma linha, dependendo do que você quer:

| Você quer | Faça isso |
|---|---|
| Narração nos seus logs | Coloque um provedor no classpath (`logback-classic`, `slf4j-simple`, …), ou passe `loggingJars=/caminho/para/provedor.jar` quando o host não tiver um classpath acessível |
| Nenhuma narração | Anexe com `loggerName=` (vazio), ou defina `-Dnarrativetrace.narration=off` |

Com a narração desligada, o agente nunca toca no SLF4J — ele inicia em
silêncio.


## 4. Configure a saída de traces

### JUnit 5 (recomendado): `junit-platform.properties`

Adicione `src/test/resources/junit-platform.properties`:

```properties
narrativetrace.output=true
narrativetrace.format=markdown
```

Nenhum wiring do Gradle é necessário. Esse arquivo é exclusivo de teste e
nunca toca em produção.

### Gradle: `gradle.properties` (alternativa)

Defina as configurações de saída de traces em um único lugar:

```properties
# gradle.properties
narrativetrace.output=true
narrativetrace.format=markdown
```

Depois, encaminhe para a JVM de teste em `build.gradle.kts`:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    listOf("narrativetrace.output", "narrativetrace.outputDir", "narrativetrace.format")
        .forEach { key ->
            (findProperty(key) as? String)?.let { systemProperty(key, it) }
        }
}
```

### Sobrescrita via CLI

Propriedades de sistema sobrescrevem todas as outras fontes:

```bash
./gradlew test -Dnarrativetrace.output=true
./gradlew test -Pnarrativetrace.format=text
```

### Java puro / agente: `narrativetrace.properties`

Adicione um arquivo ao classpath (por exemplo,
`src/main/resources/narrativetrace.properties`):

```properties
narrativetrace.packages=com.example.app.*;com.example.shared.*
```

## 5. Valide a instalação

Rode os testes:

```bash
./gradlew test
```

Se `junit-platform.properties` tiver `narrativetrace.output=true`, os
arquivos de trace são gravados automaticamente.

Estrutura de saída esperada:

```
build/narrativetrace/
├── traces/
│   └── OrderServiceTest/
│       ├── customer_places_order.md
│       ├── customer_places_order.json
│       └── ...
├── diagrams/
│   └── OrderServiceTest/
│       ├── customer_places_order.mmd
│       └── ...
└── clarity-report.md
```

### O que aparece na saída gerada

Quando um trace tem contexto de span, os arquivos gerados incluem tanto o
trace ID bruto quanto um nome do trace, determinístico e legível, derivado
dele.

O frontmatter do Markdown inclui:

```yaml
trace_id: 4bf92f3577b34da6a3ce929d0e0e4736
trace_name: bold elk soars
```

A exportação JSON inclui:

```json
{
  "trace": {
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
    "traceName": "bold elk soars"
  }
}
```

O `traceId` em hex continua sendo o identificador de referência. `traceName`
é um alias legível para logs, dashboards e discussões da equipe.

## Referência de seleção de módulos

| Módulo | Quando adicioná-lo |
|---|---|
| `narrativetrace-core` | Sempre obrigatório |
| `narrativetrace-proxy` | Tracing baseado em interface via proxies JDK |
| `narrativetrace-junit5` | Extensão do JUnit 5 e emissão de arquivos de trace |
| `narrativetrace-junit4` | Regra e regra de classe do JUnit 4 para saída de traces |
| `narrativetrace-spring` | Encapsulamento automático de beans do Spring via `@EnableNarrativeTrace` |
| `narrativetrace-slf4j` | Emite eventos de narrativa para o logger do SLF4J |
| `narrativetrace-diagrams` | Renderizadores Mermaid / PlantUML |
| `narrativetrace-clarity` | Análise e relatório de clareza dos nomes |
| `narrativetrace-opentelemetry` | Exporta árvores de trace como spans do OpenTelemetry, ou criação de spans ao vivo via decorator |
| `narrativetrace-micrometer` | Propagação de trace entre threads via context-propagation do Micrometer |
| `narrativetrace-agent` | Instrumentação via agente Java |
| `narrativetrace-servlet` | Filtro de servlet para produção — ciclo de vida do trace por requisição e exportação (sem Spring) |
| `narrativetrace-spring-web` | `@Configuration` do Spring fazendo auto-wiring do filtro de servlet com exportador plugável |
| `narrativetrace-micronaut` | Encapsulamento automático de beans do Micronaut via `BeanCreatedEventListener` |
| `narrativetrace-micronaut-http` | Filtro HTTP reativo do Micronaut para o ciclo de vida do trace por requisição |

## Veja também

- [Guia do plugin de Gradle](guia-do-plugin-de-gradle.md) — referência
  completa da DSL, modos de interceptação, receitas, DSL Groovy
- [Guia de configuração](guia-de-configuracao.md) — níveis de tracing,
  configuração de JUnit/Gradle/Spring/SLF4J
- [Guia de integração com Spring](guia-de-integracao-com-spring.md) —
  tracing de beans, filtro de servlet, propagação de `@Async`, testes
- [Guia de integração com Micronaut](guia-de-integracao-com-micronaut.md) —
  tracing de beans, filtro HTTP, propriedades de configuração
- [Guia de anotações](guia-de-anotacoes.md) — `@Narrated`, `@OnError`,
  `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`
- [Guia de clareza](guia-de-clareza.md) — modelo de pontuação, componentes de
  NLP, integração com JUnit
