<!-- source: documentation/privacy-and-redaction.md blob 11cb3181e5e4 | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Privacidade e ocultação

[English](../privacy-and-redaction.md) | Español | **Português** | [简体中文](../zh-CN/隐私与脱敏.md)

Esta biblioteca roda dentro do seu processo e escreve arquivos que seu
time vai compartilhar — artefatos de CI, baselines commitadas, linhas de
log de produção. Esta página é a versão linha a linha desse contrato: o
que oculta, onde alcança e onde não alcança, e o que o NarrativeTrace
garante versus o que ele nem chega a reivindicar.

## Ocultação, superfície por superfície

| Superfície | Pode desativar a ocultação embutida? |
|---|---|
| Saída padrão do JUnit (proxy, agente, Spring, Micronaut, servlet, SLF4J) | Não |
| Narração do agente | Não |
| `ValueRenderer` customizado que seu próprio código constrói | Sim — apenas passando `RedactionPolicy.DISABLED` explicitamente ao construtor |
| `@NotTraced` | Não aplicável — é a própria coisa que faz a ocultação, e sempre vence |
| `.nt` estrutural | Não aplicável — não carrega valores para ocultar, para começo de conversa |

Verificado contra o código, não inferido a partir da documentação: toda
integração distribuída — `NarrativeTraceProxy` e `AgentRuntime` (os dois
lugares onde a captura de fato acontece) — e todo módulo de framework
construído sobre eles constroem seu `ValueRenderer` da mesma forma, como
um campo `private static final` sem ponto de injeção:

```java
private static final ValueRenderer VALUE_RENDERER = new ValueRenderer();
```

`new ValueRenderer()` usa `RedactionPolicy.DEFAULT` por padrão. Não
existe flag de configuração, system property ou knob de DSL do plugin
que alcance essa constante — a única forma de obter
`RedactionPolicy.DISABLED` é código de aplicação que constrói sua
própria instância de `ValueRenderer` diretamente, contornando todo
caminho de integração distribuído. Isso é um ato deliberado e revisável
no seu próprio código-fonte, não um estado de configuração que um deploy
possa alternar silenciosamente.

## O que a lista de negação captura, e o que tem prioridade sobre ela

Dois mecanismos de ocultação independentes se aplicam a todo valor
renderizado reflexivamente:

1. **`@NotTraced`** em um parâmetro, campo ou componente de record —
   sempre oculta, incondicionalmente, em todo lugar.
2. **A lista de negação baseada em nome** (`RedactionPolicy.DEFAULT`) —
   compara nomes de campos com um conjunto embutido e multilíngue
   (`password`, `token`, `cvv`, `ssn`, `secret`, `authorization`,
   `cardNumber`, e seus equivalentes em espanhol, português, francês e
   chinês, entre outros), mais uma segunda verificação independente
   sobre a *forma* do próprio valor (um JWT, um número de cartão válido
   pelo algoritmo de Luhn, uma string `Set-Cookie`), de modo que um
   bearer token passado sob um nome não reconhecido ainda assim é
   capturado.

Um **`toString()` cuidadosamente escrito** normalmente é preferido em
vez da introspecção reflexiva — mas uma classe que declara um campo
`@NotTraced` é introspectada mesmo assim, então a anotação é respeitada
em vez do que aquele `toString()` teria impresso. A lista de negação
baseada em nome *não* tem o mesmo poder de sobrepor: ela só se aplica
quando o NarrativeTrace já está introspectando campos, então uma classe
com seu próprio `toString()` e nenhum membro `@NotTraced` é confiada
como está escrita. Somente a anotação explícita tem prioridade sobre um
`toString()` cuidadosamente escrito.

A ocultação também **sobrevive um nível de container de profundidade**
— `Optional`, `Future`, `AtomicReference`, `AtomicReferenceArray` e um
`Map.Entry` isolado são abertos em vez de renderizados via seu próprio
`toString()`, então um valor ocultado dentro de um deles permanece
ocultado em vez de vazar pelo wrapper. E ela **prevalece sobre um
template de narração que o nomeia**: `{param.property}` em
`@Narrated`/`@OnError` resolve um caminho até um membro ocultado como
`[REDACTED]`, em toda profundidade ao longo do caminho, nunca o valor
literal.

Detalhes completos e exemplos resolvidos: [Guia de
anotações](guia-de-anotacoes.md).

## Garantias

- **Falhas de tracing são isoladas da execução hospedeira.** A gravação
  é isolada de exceções em todo caminho; ambos os consumers do pipeline
  engolem seus próprios erros. Um `toString()` que lança exceção, um
  buffer cheio ou um appender quebrado nunca mudam o que seu método
  retorna ou lança.
- **As saídas padrão respeitam a ocultação.** Veja a tabela acima —
  nenhuma integração distribuída expõe uma forma de contorná-la.
- **O artefato estrutural `.nt` não tem nenhum valor de runtime.**
  Apenas nomes, hierarquia de chamadas e tipos de resultado — superfície
  zero para prompt injection, e isso é um property test (ADR-002), não
  uma política que alguém poderia esquecer de aplicar.
- **O caminho de análise em buffer pode descartar eventos, mas sempre
  reporta a perda.** Ele nunca bloqueia quem chama e nunca cresce além
  do seu limite; uma captura que perdeu eventos imprime a contagem em
  seu próprio rodapé em vez de subnotificar silenciosamente.

## Não garantias

- **Nenhuma alegação de "overhead zero".** O tracing faz trabalho, e
  trabalho custa algo — veja a [seção de performance do
  README](../../README.md#performance) para os números medidos.
- **Sem tracing de métodos privados.** Ambos os caminhos de captura
  enxergam apenas métodos de interface (proxy) ou métodos não privados
  (agente); o ramo de um método privado é inferido a partir de quais de
  *suas* chamadas aparecem no trace.
- **Sem tracing automático de um bean sem interface, em nenhum caminho
  baseado em proxy.** Proxy, Spring e Micronaut envolvem tudo via um
  proxy dinâmico JDK, e uma classe sem interface é deixada intocada —
  silenciosamente, não como um erro. O agente Java é o caminho que não
  tem essa limitação.
- **Ainda sem lista de exclusão para o agente.** O `AgentConfig`
  interpreta exatamente `packages`, `loggerName`, `level` e
  `loggingJars` — só inclusão, nada para recortar uma exceção de dentro
  de um pacote incluído.
- **Ainda sem suporte a Android ou GraalVM native-image.** Detalhes
  completos, incluindo *por que* Android é um "não" em vez de um "ainda
  não", em [Guia de instalação §
  Compatibilidade](guia-de-instalacao.md#compatibilidade).

## O modelo de perda em produção, visualmente

Todo evento é escrito de forma síncrona no stream de log durável e,
*além disso*, publicado em um buffer em memória de melhor esforço e
tamanho limitado — as duas metades do pipeline de caminho duplo carregam
obrigações opostas por design:

```text
caminho de log síncrono (SLF4J, inline na thread chamadora)
   não pode falhar a aplicação hospedeira
   não pode ser sufocado pela falha do caminho de análise
   durável — este é o registro

caminho de análise em buffer (anel limitado, drenado para o store retido)
   pode descartar eventos sob carga, e sempre avisa disso
   nunca pode bloquear quem chama
   nunca pode crescer além do seu limite (65.536 slots por padrão)
   melhor esforço — isto é análise, não auditoria
```

Perder o buffer perde a fidelidade de análise daquela execução. Perder o
stream de log perde o registro. Essa assimetria é o motivo de serem dois
caminhos com dois comportamentos de falha diferentes, em vez de um único
caminho com um único trade-off.

## O que esta página não cobre

Onde o NarrativeTrace se encaixa em cada etapa do seu pipeline —
desenvolvimento, CI/aceitação, produção — está no [Guia do ciclo de
vida](guia-do-ciclo-de-vida.md). O que acontece quando ele se combina
com proxies AOP, bibliotecas de contrato ou outro agente envolvendo as
mesmas classes — a ordem de aninhamento pode mudar como um trace *é
lido*, nunca o que um método *retorna ou lança*, porque o registro nunca
substitui um resultado — está em [Escolhendo uma integração § Empilhar
com outros
wrappers](escolhendo-uma-integracao.md#empilhar-com-outros-wrappers).
