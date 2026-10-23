<!-- source: documentation/installation-guide.md blob 1d2fc8538014 | translated: 2026-10-23 | reviewed: - -->
# Guia de instalação do NarrativeTrace Java

[English](../installation-guide.md) | [Español](../es/guia-de-instalacion.md) | **Português** | [简体中文](../zh-CN/安装指南.md)

Este guia cobre a instalação e o wiring do NarrativeTrace Java em um projeto JVM.

## Pré-requisitos

- Java 17+
- Build com Gradle

## Compatibilidade

Estas são as versões com as quais esta release é compilada e testada.
"Incluída" significa que o módulo depende dela e o
Gradle a resolve para você; "sua" significa que o módulo compila contra ela,
mas não depende dela — você já a tem, e o NarrativeTrace usa qualquer versão
que você trouxer.

| Requisito | Versão | Quem fornece |
|---|---|---|
| Java | **17+** — compilado e testado na 17; o JDK 21 é exercitado por um job de CI agendado (os testes de threads virtuais só rodam lá) | sua |
| Gradle (para o plugin) | **8.0+** — desenvolvido e testado contra a 8.14.2 | sua |
| JUnit 5 | 5.11.4 (`narrativetrace-junit5` expõe `junit-jupiter-api` como `api`) | incluída |
| JUnit 4 | 4.13.2 | incluída |
| TestNG | 7.10.2 | incluída |
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
do compilador e configuração da JVM de teste.

Dê ao Maven Central a primeira prioridade em `pluginManagement` — o plugin e seu marcador são
publicados no Maven Central a cada versão, enquanto a listagem no Gradle Plugin Portal aguarda a
aprovação de um novo id de plugin pela Gradle, então é o Central que torna uma versão
recém-publicada resolvível no mesmo dia em que ela sai:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
plugins {
    id("ai.narrativetrace") version "0.5.0"
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
    implementation("ai.narrativetrace:narrativetrace-core:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.5.0")

    // Integrações opcionais
    testImplementation("ai.narrativetrace:narrativetrace-junit5:0.5.0")
    testImplementation("ai.narrativetrace:narrativetrace-junit4:0.5.0")  // para JUnit 4
    testImplementation("ai.narrativetrace:narrativetrace-testng:0.5.0")  // para TestNG
    implementation("ai.narrativetrace:narrativetrace-spring:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-slf4j:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-diagrams:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-clarity:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-opentelemetry:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-micrometer:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-agent:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-servlet:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-spring-web:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-micronaut:0.5.0")
    implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.5.0")
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
- Grava `.md`, `.json`, `.mmd` por teste por padrão, além de um
  `clarity-report.md` no nível da suíte — defina `narrativetrace.output=false`
  para desativar

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
- Grava `.md`, `.json`, `.mmd` por teste por padrão — defina
  `-Dnarrativetrace.output=false` para desativar
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
- `narrativetrace.output` — `true`/`false` (padrão: `true`)
- `narrativetrace.outputDir` — caminho (padrão: `build/narrativetrace`)
- `narrativetrace.format` — `markdown`/`text`/`mermaid`/`plantuml` (padrão: `markdown`)

### Opção D2: contexto automático do TestNG + saída de traces

Com o plugin do Gradle, `narrativeTrace { testFramework.set("testng") }` é toda a configuração: ele adiciona
`narrativetrace-testng` em `testImplementation`, muda a task `test` para `useTestNG()` e passa as propriedades
de sistema `narrativetrace.*`; o listener se anexa sozinho. Declare `org.testng:testng` você mesmo, como faria
com o JUnit 4 (a dependência `api` do módulo traz o TestNG 7.10.2 se você não o fizer). Sem o plugin:

Adicione `narrativetrace-testng` em `testImplementation` e nada mais: o service loader do TestNG
registra o listener (`NarrativeTraceListener`) em toda execução — `useTestNG()` do Gradle, Maven
surefire, a IDE. `@Listeners(NarrativeTraceListener.class)` em uma classe de teste também o
registra explicitamente; com os dois ao mesmo tempo, cada teste continua sendo rastreado uma vez.

```java
public class OrderServiceTest {
    private final OrderService orderService = NarrativeTraceProxy.trace(
        new DefaultOrderService(), OrderService.class, NarrativeTraceListener.context());

    @Test
    public void customerPlacesOrder() {
        orderService.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

O que é escrito — os mesmos arquivos das integrações do JUnit:
- Um trace por método de teste em `build/narrativetrace/traces/<TestClass>/`: `.md`, `.json`, um diagrama de sequência e o `.nt` estrutural
- Um teste que falha imprime seu relatório de falha e, na saída de erro padrão, `narrativetrace: trace of <Class.method> → <caminho absoluto>`
- Quando a execução termina: `manifest.json`, `INDEX.md` (testes que falham primeiro), `clarity-report.md` e o rodapé do console, que repete os ponteiros dos testes que falharam
- Uma linha de data provider, uma repetição de `invocationCount` ou uma instância de `@Factory` é uma invocação com seus próprios arquivos — `<method>-001.md`, rotulada `Class.method[#1]`
- Os traces de aprovação (`narrativetrace.approval=true`) comparam cada teste com seu `.approved.nt` versionado, como no JUnit

A configuração usa as mesmas propriedades de sistema do JUnit 4 (`narrativetrace.output`, `narrativetrace.outputDir`,
`narrativetrace.format`, `narrativetrace.level`, `narrativetrace.approval`, `narrativetrace.approvedDir`).

Limites:
- `NarrativeTraceListener.context()` é como um teste obtém o contexto — o TestNG não consegue injetá-lo como parâmetro do método de teste
- Os arquivos da execução são escritos quando a execução do TestNG termina; o TestNG não tem um fim por classe, então nada é escrito por classe
- Um único pipeline atende toda a JVM de testes; cada teste vê apenas as chamadas feitas na própria thread enquanto roda, incluindo `parallel="methods"`
- Um teste com `timeOut` executa seu corpo em uma thread separada do TestNG, então suas chamadas não aparecem no trace
- Uma linha de data provider é rotulada pelo número, nunca pelos argumentos: a renderização nunca executa o `toString` dos seus objetos
- Sem coleta do glossário (`narrativetrace.glossary`) — essa etapa pertence à extensão do JUnit 5

### Opção E: agente Java (sem wiring de proxy)

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.* -jar your-app.jar
```

Use isso quando você quiser instrumentação de bytecode para classes sob
prefixos de pacote selecionados. Quando nenhum argumento de CLI é fornecido,
o agente recorre a `narrativetrace.properties` no classpath. Anexado a uma JVM de testes que também
carrega a extensão do `narrativetrace-junit5` ou as regras do `narrativetrace-junit4`, as capturas
do agente chegam aos mesmos arquivos por teste que um teste rastreado por proxy produz — sem proxy,
sem anotação na classe instrumentada (veja [Guia de configuração §3](guia-de-configuracao.md#3-configuração-para-java-puro--agente-narrativetraceproperties)).

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
silêncio. **Sem um provedor SLF4J no classpath, o agente também fica em
silêncio** — ele continua capturando, mas a narração vai para um logger NOP.

#### Classes que não enxergam o runtime do agente carregam sem instrumentação

Casar com o filtro de pacotes é necessário, mas não suficiente. O bytecode
instrumentado chama `ai.narrativetrace.agent.AgentRuntime` pelo nome, então uma
classe só é tecida quando o class loader que a definiu consegue resolver esse
tipo. Dois tipos de classe não conseguem:

- **classes injetadas no bootstrap** — o mock maker inline do mockito, por
  exemplo, injeta seu despachante de métodos no class loader bootstrap
- **loaders isolados** que não delegam ao loader pelo qual o agente chegou

Essas carregam sem instrumentação, e o agente diz isso uma vez por loader:

```text
narrativetrace-agent: cannot instrument org/example/Injected (ai.narrativetrace.agent.AgentRuntime is not visible from the bootstrap class loader); classes from that loader load uninstrumented
```

Uma vez por loader, nunca uma vez por classe — um loader quente não deve inundar
o seu fluxo de erros. Todo o resto na mesma JVM continua sendo rastreado
normalmente; se as classes que importam para você estão entre as não
instrumentadas, estreite `packages=` para que o filtro pare de casar com elas,
ou rastreie-as pelo proxy.

#### Classes geradas e feitas pelo compilador nunca são instrumentadas

Algumas classes dentro de um pacote correspondido nunca são uma fronteira de
negócio — são bytecode que um gerador ou o compilador fez, não código que
você escreveu. Essas são excluídas incondicionalmente, sem nenhum
interruptor para reativá-las:

- um proxy dinâmico JDK (`$Proxy0`, `$Proxy17`, …)
- uma classe cujo nome carrega a marca própria de um gerador: `$$` (o
  `$$EnhancerByCGLIB$$` do CGLIB, o `$$SpringCGLIB$$` do Spring, a classe
  oculta própria de uma lambda), `$ByteBuddy$` (a nomenclatura padrão do
  Byte Buddy), ou `$MockitoMock$` (o mock maker inline do Mockito)
- uma classe oculta, pelo seu sufixo `/0x…` atribuído pela JVM
- um método bridge ou um método sintético — uma duplicata gerada pelo
  compilador de um método real, ou um acessor que ninguém escreveu

Isso é silencioso — nenhuma linha no `stderr` — porque é o caso esperado e
estrutural; a pertinência nunca depende do nome de pacote de um fornecedor,
então adicionar uma nova biblioteca de mocking ou de proxy às suas
dependências não exige nenhuma mudança de configuração aqui.

#### Excluir pacotes: `exclude=<pkg1>;<pkg2>`

Internos de bibliotecas e frameworks — nunca sua própria fronteira de
negócio, mas nomeados por pacote em vez de por forma — são excluídos ao
lado de `packages=`:

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.*,exclude=org.mockito -jar your-app.jar
```

`exclude=` sempre vence: uma classe que corresponde tanto a `packages=`
quanto a `exclude=` não é instrumentada, mesmo quando o pacote excluído está
dentro de um pacote correspondido (`packages=com.example`,
`exclude=com.example.generated` — uma classe sob `com.example.generated`
não é instrumentada; todo o resto sob `com.example` continua sendo). Lista
padrão embutida, aplicada sempre que `exclude=` (e `narrativetrace.exclude`
no caminho de fallback por propriedades) estiver totalmente ausente:

| Prefixo | Motivo |
|---|---|
| `org.mockito` | O dispatcher da biblioteca de mocking e as subclasses de mock geradas |
| `net.bytebuddy` | O gerador de código por trás do Mockito, do Hibernate e de muitas ferramentas de proxy |
| `org.junit` | Helpers próprios do test runner |
| `org.testng` | Helpers do outro test runner JVM mainstream |
| `net.sf.cglib` | Internos próprios do CGLIB clássico |
| `org.springframework.cglib` | O CGLIB reempacotado pelo Spring |
| `org.aspectj` | O runtime de outro weaver |
| `datadog` | O runtime de outro agente APM — evita instrumentação recursiva quando implantado junto |
| `io.opentelemetry.javaagent` | O outro agente APM mais provável de ser implantado junto |

A presença da chave decide: `exclude=` ausente significa que esse padrão
se aplica; `exclude=` presente — mesmo sem nada depois — declara a lista
inteira explicitamente, substituindo o padrão (vazio desativa a lista 2
por completo). Para estender o padrão em vez de
substituí-lo, copie esses nove prefixos para o seu próprio valor de
`exclude=` e adicione os seus junto com eles. Respeita delimitadores como
`packages=` (`org.acme` nunca corresponde a `org.acmeExtra`); uma chave
`exclude=` duplicada é rejeitada como uma chave `packages=` duplicada. Uma
classe correspondida e excluída é reportada uma vez por prefixo excluído,
nunca uma vez por classe:

```text
narrativetrace-agent: cannot instrument org/mockito/internal/Foo (excluded package org/mockito/); classes under that package load uninstrumented
```


## 4. Configure a saída de traces

A saída de traces é gravada em `build/narrativetrace` por padrão — não há
nada para ativar. As seções abaixo servem para mudar o formato ou desativar.

### JUnit 5 (recomendado): `junit-platform.properties`

Adicione `src/test/resources/junit-platform.properties`:

```properties
narrativetrace.format=markdown
```

Para desativar completamente, defina `narrativetrace.output=false` no mesmo
arquivo. Nenhum wiring do Gradle é necessário em nenhum dos dois casos. Esse
arquivo é exclusivo de teste e nunca toca em produção.

### Gradle: `gradle.properties` (alternativa)

Defina as configurações de saída de traces em um único lugar:

```properties
# gradle.properties
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
./gradlew test -Dnarrativetrace.output=false
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

Os arquivos de trace são gravados automaticamente, a menos que
`junit-platform.properties` defina `narrativetrace.output=false`.

Estrutura de saída esperada:

```text
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
| `narrativetrace-api` | Nunca — chega de forma transitiva com `narrativetrace-core`; dependa dele diretamente somente quando você publicar uma biblioteca que implemente uma SPI sem precisar do runtime |
| `narrativetrace-core` | Sempre obrigatório |
| `narrativetrace-proxy` | Tracing baseado em interface via proxies JDK |
| `narrativetrace-junit5` | Extensão do JUnit 5 e emissão de arquivos de trace |
| `narrativetrace-junit4` | Regra e regra de classe do JUnit 4 para saída de traces |
| `narrativetrace-testng` | Listener do TestNG para saída de traces, registrado pelo service loader do TestNG |
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
