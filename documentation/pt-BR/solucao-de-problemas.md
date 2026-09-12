<!-- source: documentation/troubleshooting.md blob 43f2124e9828 | translated: 2026-09-12 | reviewed: - -->
# Solução de problemas

[English](../troubleshooting.md) | Español | **Português** | [简体中文](../zh-CN/故障排查.md)

Sintoma → causa → correção, para os modos de falha que as pessoas
realmente encontram. Algumas entradas são a explicação completa; outras
apontam para o guia que já traz mais detalhe em vez de repeti-lo aqui —
um único lugar por fato.

## Parâmetros aparecem como `arg0`, `arg1`

**Causa:** a flag de compilador `-parameters` está ausente, então a
classe compilada não carrega nomes reais de parâmetros para o
NarrativeTrace ler.

**Correção:**

```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
```

O plugin do Gradle adiciona isso automaticamente — isso só importa para
uma configuração manual.

## `Cannot create Launcher without at least one TestEngine`, ou `Could not start Gradle Test Executor 1: Failed to load JUnit Platform`

**Causa:** `useJUnitPlatform()` precisa, no classpath de execução dos
testes, tanto de um engine do JUnit 5 quanto de
`org.junit.platform:junit-platform-launcher`. O Gradle 8 fornece ele mesmo
uma versão do launcher quando só o engine é declarado — um comportamento
obsoleto sobre o qual ele avisa em toda execução — e o Gradle 9 remove essa
gestão automática por completo, então o *processo* de teste falha antes de
qualquer engine, extensão ou classe de teste rodar. Reproduzido contra uma
build real do Gradle 9.0.0: a segunda mensagem acima é o texto exato dela.

**Correção:**

```kotlin
dependencies {
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
```

O plugin do Gradle adiciona os dois automaticamente *(since 0.2.2, unreleased)* — isso só importa para
uma configuração manual.

## Nenhum arquivo de saída de trace

**Causa:** a saída é gravada por padrão *(since 0.2.2, unreleased)* em `build/narrativetrace`, então um
arquivo ausente geralmente significa uma destas: `narrativetrace.output=false`
está definido em algum lugar (`junit-platform.properties`, uma propriedade de
sistema, ou `enabled.set(false)` no plugin do Gradle); o trace estava vazio
porque nenhuma chamada passou por um proxy/agente rastreado; ou
`narrativetrace.outputDir` aponta para outro lugar diferente de onde você
está olhando.

**Correção:** confirme que a propriedade não está como `false`, e verifique
`build/narrativetrace/` (ou o `narrativetrace.outputDir` configurado) — veja
o [Guia de Configuração](guia-de-configuracao.md) para cada propriedade e seu
padrão.

## Não vejo nada no meu terminal

**Causa:** o resumo por teste e o rodapé da suíte são escritos na saída
padrão do processo de teste. Por padrão, a task `test` do Gradle captura
isso no relatório XML/HTML — um `./gradlew test` simples não mostra nada
em um terminal padrão, mesmo que todo arquivo em
`build/narrativetrace/` seja escrito corretamente.

**Correção:** ative o log de standard-stream na task `test`:

```kotlin
tasks.test {
    testLogging.showStandardStreams = true
}
```

Isso é um comportamento do Gradle, não algo que o NarrativeTrace faz —
veja [Guia de instalação § Opção
D](guia-de-instalacao.md#opção-d-contexto-automático-do-junit-4--saída-de-traces)
para a mesma observação na receita do JUnit 4.

## Proxy lança `ClassCastException`

**Causa:** o alvo não implementa a interface passada para
`NarrativeTraceProxy.trace(...)`.

**Correção:** garanta que a classe concreta implemente essa interface, e
passe o `Class` da interface, não o da implementação.

## Beans do Spring não sendo traceados

**Causa:** ou o pacote do bean está fora de `basePackages`, ou o bean
não implementa nenhuma interface — o `narrativetrace-spring` usa o mesmo
proxy dinâmico JDK que o `narrativetrace-proxy`, que não consegue
encapsular uma classe sem interface. Um bean sem interface é deixado
intocado silenciosamente; isso não é um erro.

**Correção:** adicione o pacote a
`@EnableNarrativeTrace(basePackages = ...)`, e dê ao bean uma interface,
caso ele não tenha uma.

## Filtro HTTP do Micronaut não injeta contexto de usuário

**Causa:** um bean implementando `RequestContextProvider` foi escrito
contra o tipo errado. O NarrativeTrace distribui duas interfaces com
nomes parecidos: `ai.narrativetrace.api.export.RequestContextProvider<REQUEST>`
(genérica, agnóstica de framework, a que o filtro de servlet e o Spring
Web vinculam a `HttpServletRequest`) e uma específica do Micronaut que o
filtro HTTP de fato consulta. Um bean implementando o tipo do jar de API
silenciosamente não é um candidato — o filtro não injeta nada e roda sem
contexto de usuário, sem erro nenhum. O sintoma é todo span mostrando os
campos `enduserId`/`tenantId` vazios.

**Correção:** implemente o tipo do Micronaut, não o do jar de API.
Detalhes completos — incluindo por que os dois tipos permanecem
separados — em [Guia de integração com
Micronaut](guia-de-integracao-com-micronaut.md) e [API
Surface](../api-surface.md).

## A pontuação de clareza parece errada

**Causa:** normalmente um nome genérico que a análise de NLP sinaliza —
`get`, `set`, `process`, `handle`, `data`, `info`, `temp` e similares
pontuam baixo independentemente do contexto.

**Correção:** revise a lista de problemas em `clarity-report.md` e
substitua o nome sinalizado por um específico do domínio (`getData()` →
`fetchOrderHistory()`). Veja o [Guia de clareza](guia-de-clareza.md) para
o modelo de pontuação completo.

## Traces entre threads estão vazios

**Causa:** `captureTrace()`/`events()` têm escopo de thread — eles
retornam apenas o trace da thread chamadora. Ou o contexto nunca foi
propagado para a thread assíncrona, ou `captureTrace()` foi chamado em
uma thread diferente daquela que registrou os eventos.

**Correção:**

```java
var snapshot = context.snapshot();
executor.submit(snapshot.wrap(() -> service.process()));
```

Ou registre `NarrativeTraceThreadLocalAccessor` com o Micrometer para
propagação automática. Se você só precisa do próprio trabalho
bifurcado, chame `captureTrace()` dentro da task, na thread de gravação.

## O agente não instrumenta classes

**Causa:** o filtro `packages=` não corresponde, ou as regras de
fronteira não correspondem ao que você esperava — `com.example` casa
com `com.example.*`, nunca com `com.exampleExtra`.

**Correção:** verifique o argumento `packages=`; tanto wildcards
(`com.example.*`) quanto múltiplos pacotes (separados por ponto e
vírgula, `packages=com.a.*;com.b.*`) são suportados. Não há lista de
exclusão — apenas inclusão.

## O agente gera traces, mas nada aparece nos meus logs

**Causa:** o agente deliberadamente não traz nenhum provedor SLF4J
próprio — um host mínimo (um app server sem classpath alcançável, na
maioria das vezes) não tem por onde escrever a narração, então o SLF4J
registra um aviso único e recorre a um logger no-op. Nada está quebrado:
`captureTrace()` ainda retorna o trace, e os arquivos em
`build/narrativetrace/` (se a saída estiver ativada) não são afetados —
apenas a narração de log ao vivo fica silenciosa.

**Correção:** coloque um provedor no classpath (`logback-classic`,
`slf4j-simple`, …), ou passe `loggingJars=/path/to/provider.jar` para um
host sem classpath alcançável. Para silenciar a narração de propósito em
vez disso, conecte com `loggerName=` (vazio) ou
`-Dnarrativetrace.narration=off`. Receita completa em [Guia de
instalação § A narração precisa de um provedor
SLF4J](guia-de-instalacao.md#a-narração-precisa-de-um-provedor-slf4j--o-agente-nunca-traz-um).

## `@NotTraced` falha ao compilar: "package ai.narrativetrace.api.annotation does not exist"

**Causa:** o `scope` padrão do plugin do Gradle é `"test"`, então o
`narrativetrace-api` — onde vivem todas as anotações — fica apenas em
`testImplementation`. Referenciar `@NotTraced` (ou `@Narrated`,
`@OnError`, `@NarrativeSummary`) a partir de uma classe sob
`src/main/java` falha ao compilar antes mesmo de qualquer teste rodar.

**Correção:** adicione o jar da API em `compileOnly` (ele não tem
nenhuma dependência de runtime própria):

```kotlin
dependencies {
    compileOnly("ai.narrativetrace:narrativetrace-api:0.2.1")
}
```

Ou defina `scope.set("production")` se o NarrativeTrace for feito para
rodar em produção de qualquer forma. Veja [Guia de Configuração § DSL do
plugin do Gradle](guia-de-configuracao.md#dsl-do-plugin-do-gradle), onde
`scope` está documentado.

## O modo de aprovação escreveu `.received.nt`

**Causa:** este é o comportamento pretendido, não uma falha — ou ainda
não existe baseline `.approved.nt` para o cenário, ou a estrutura de um
teste que passa se desviou da que está commitada.

**Correção:** revise o diff do `.received.nt`, e se a nova forma estiver
correta, promova-o:

```bash
./gradlew approveNarratives
```

Nunca renomeie manualmente para o lugar sem antes ler o diff — é
exatamente isso que o modo de aprovação de revisão existe para forçar.
Formato completo e layout dos arquivos em [Formato de trace
estrutural](formato-de-trace-estrutural.md); o que fazer com cada
arquivo no dia a dia está em [O que commitar](o-que-commitar.md).
