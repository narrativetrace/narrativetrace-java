<!-- source: documentation/choosing-an-integration.md blob bd89ddac9a5f | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Escolhendo uma integração

[English](../choosing-an-integration.md) | Español | **Português** | [简体中文](../zh-CN/选择集成方式.md)

O NarrativeTrace tem um único modelo de captura — um evento de
entrada/saída publicado através do pipeline — alcançado por cinco
mecanismos de anexação diferentes. Esta página responde "de qual módulo eu
realmente preciso", primeiro como uma tabela de consulta, depois como um
diagrama de decisão, e por fim com as ressalvas que cada caminho tem.

## Você quer... / Comece com...

| Você quer | Comece com |
|---|---|
| Traces em testes, com o mínimo de wiring | Plugin do Gradle + `narrativetrace-junit5` |
| O mesmo, no JUnit 4 | `narrativetrace-junit4` |
| Para escolher exatamente o que é encapsulado, em Java puro | `narrativetrace-proxy` (proxy JDK dinâmico) |
| Beans do Spring capturados automaticamente | `narrativetrace-spring` |
| Ciclo de vida de requisições HTTP do Spring em produção | `narrativetrace-spring-web` (conecta o `narrativetrace-servlet`) |
| Qualquer aplicação servlet, sem Spring | `narrativetrace-servlet` |
| Beans e requisições do Micronaut | `narrativetrace-micronaut` + `narrativetrace-micronaut-http` |
| **Zero mudanças de código** — uma aplicação que você não pode ou não quer modificar | `narrativetrace-agent` (agente Java) |
| Visibilidade assíncrona entre threads (`@Async`, Reactor, executors) | `narrativetrace-micrometer`, ou `ContextSnapshot` manualmente |
| Traces no seu fluxo de logs de produção | `narrativetrace-slf4j` |
| Spans do OpenTelemetry | `narrativetrace-opentelemetry` |

Esta é a mesma matriz que o [README raiz](../../README.md#choose-your-integration)
carrega; ela também vive aqui como a âncora para o diagrama e os detalhes
abaixo.

## A decisão

O relatório do qual esta página surgiu propunha um diagrama mais curto do
que o de baixo — sua versão tratava "não ser uma aplicação Spring/Micronaut"
como o único motivo para recorrer ao agente. Não é assim que as integrações
de framework realmente funcionam: tanto o `narrativetrace-spring` quanto o
`narrativetrace-micronaut` encapsulam beans com o mesmo proxy JDK dinâmico
que o `narrativetrace-proxy` usa, então um bean do Spring sem interface é
ignorado exatamente como um alvo de proxy comum seria (verificado em
`spring-integration-guide.md` e `micronaut-integration-guide.md`: ambos
afirmam que "o bean deve implementar ao menos uma interface"). O diagrama
abaixo tem esse ramo incluído.

```text
Você controla como o objeto é construído (Java puro, um teste)?
   |
   +-- sim --> o serviço implementa uma interface?
   |             |
   |             +-- sim --> proxy JDK (narrativetrace-proxy)
   |             +-- não --> agente Java (instrumentação de bytecode,
   |                         sem interface necessária)
   |
   +-- não --> é um bean do Spring ou do Micronaut?
                 |
                 +-- sim --> o bean implementa uma interface?
                 |             |
                 |             +-- sim --> integração de framework
                 |             |           (narrativetrace-spring /
                 |             |            narrativetrace-micronaut)
                 |             +-- não --> agente Java
                 |
                 +-- não --> agente Java (zero mudanças de código)
```

Dois caminhos convergem para o agente pelo mesmo motivo de fundo: ele é o
único mecanismo aqui que instrumenta bytecode diretamente e, por isso, nunca
chega a fazer a pergunta "tem uma interface?". A contrapartida é que o
agente não tem um equivalente à granularidade de exclusão do `basePackages`
do Spring nem ao opt-in por chamada do proxy — veja as ressalvas abaixo e
[Empilhar com outros wrappers](#empilhar-com-outros-wrappers) para o que
acontece quando ele se combina com outra coisa que também encapsula as
mesmas classes.

## Uma coisa que todo caminho compartilha

Os cinco mecanismos de anexação publicam através do mesmo `EventPipeline`
(`ai.narrativetrace.core.pipeline`); nenhum deles define sua própria noção
de chamada capturada. Escolher uma integração é uma questão de *como a
chamada é encapsulada*, nunca do que é registrado depois que ela é — uma
única captura canônica alimenta todos os caminhos de renderização (detalhe
humano, estrutura segura para IA, spans do OpenTelemetry, agregação), que
assim se mantêm em paridade por construção, não por convenção.

## Ressalvas de cada caminho

- **Proxy JDK** — o alvo precisa implementar a interface passada para
  `NarrativeTraceProxy.trace(...)`, ou a chamada lança `ClassCastException`
  no ponto de encapsulamento. Somente os métodos da interface ficam
  visíveis; uma chamada feita diretamente na instância concreta contorna o
  proxy por completo.
- **Spring / Micronaut** — mesmo requisito de interface, aplicado
  silenciosamente: um bean sem interface é deixado intocado, e não gera
  erro. Os pacotes-base são apenas de inclusão.
- **Agente Java** — todo método não privado e não abstrato dentro dos
  pacotes correspondentes é instrumentado; hoje não há opt-out por método
  nem lista de exclusão (`AgentConfig` interpreta `packages`, `loggerName`,
  `level`, `loggingJars` — nada além disso). A correspondência de pacotes
  respeita delimitadores, então `com.acme` nunca corresponde a
  `com.acmeExtra`. Em um host sem nenhum provedor de logging no classpath —
  na maioria das vezes, um app server sem nada instalado — o agente não
  narra nada até que você adicione um; veja
  [Solução de problemas](solucao-de-problemas.md#o-agente-gera-traces-mas-nada-aparece-nos-meus-logs).
- **Trabalho entre threads** — nenhum dos cinco caminhos acima mescla
  automaticamente as chamadas de uma thread assíncrona/executor ao trace
  pai. Use `ContextSnapshot.wrap(...)` manualmente, ou registre
  `NarrativeTraceThreadLocalAccessor` no Micrometer.

## Empilhar com outros wrappers

O NarrativeTrace raramente é a única coisa envolvendo um método: proxies
AOP, bibliotecas de contrato, interceptadores de container e outros agentes
de observabilidade podem se conectar à mesma chamada. Três regras valem em
todos os mecanismos de anexação acima:

- **Um frame de trace por travessia de fronteira de negócio.** O objetivo
  é narrar as chamadas que seu código faz, não o maquinário ao redor delas
  — métodos bridge, stubs de view gerados pelo container, decoradores
  gerados por outra biblioteca, ou a própria renderização do
  NarrativeTrace não são o alvo.
- **A ordem de aninhamento muda como um trace *é lido*, nunca o que ele
  *retorna ou lança*.** Qual wrapper fica mais interno ou mais externo pode
  mudar o formato da árvore de chamadas registrada, mas o registro é
  isolado de exceções em todos os caminhos e nunca substitui um resultado
  — o resultado de negócio ou a exceção que quem chama vê é sempre o real.
- **O escopo é somente de inclusão, em todo lugar.** O `packages=` do
  agente, os pacotes-base do Spring e do Micronaut, e a interface alvo
  explícita do proxy respeitam todos um limite de delimitador (`com.acme`
  nunca corresponde a `com.acmeExtra`), e nenhum deles tem lista de
  exclusão ainda — uma classe que vive dentro dos seus pacotes
  correspondentes é envolvida, seja ela sua ou de outra biblioteca.

## Limites de plataforma

Todo caminho acima pressupõe uma JVM de servidor ou desktop. O Android não é
suportado hoje (três mecanismos separados — descoberta via SPI, a busca do
listener do SLF4J e a renderização reflexiva de valores — degradam
silenciosamente sob R8/ProGuard em vez de falhar de forma clara), e a imagem
nativa do GraalVM não é testada (os caminhos de proxy e de agente dependem
de reflection sem metadados de reachability incluídos). Detalhes completos:
[Guia de Instalação § Compatibilidade](guia-de-instalacao.md#compatibilidade).

## Receitas

Todo caminho da matriz tem uma receita completa e pronta para copiar e
colar no [Guia de Instalação](guia-de-instalacao.md) — esta página responde
*qual*, aquela responde *como*.
