<!-- source: documentation/lifecycle-guide.md blob 9fe7a7e9213d | translated: 2026-09-07 | reviewed: - -->
# NarrativeTrace ao longo do ciclo de desenvolvimento

[English](../lifecycle-guide.md) | [Español](../es/guia-del-ciclo-de-vida.md) | **Português** | [简体中文](../zh-CN/生命周期指南.md)

O NarrativeTrace não é uma ferramenta de tempo de testes com um modo de
produção acoplado, nem um tracer de produção que por acaso funciona nos
testes. É **um único mecanismo de captura cujas configuração e
consumidores mudam à medida que o código avança pelo ciclo**: o mesmo
trace que documenta um teste unitário durante o desenvolvimento funciona
como gate do build no CI, narra uma execução de aceitação contra um
ambiente implantado e flui pela sua pilha de logs em produção. Este guia
percorre as três etapas e depois as condensa em uma matriz de
configuração e uma postura de privacidade.

Os guias de nível de mecanismo ([instalação](guia-de-instalacao.md),
[configuração](guia-de-configuracao.md),
[anotações](guia-de-anotacoes.md), os guias de integração) explicam cada
peça; este guia explica **em que momento do seu processo cada peça vale a
pena**. Para uma sensação prática antes de continuar lendo, execute
`./demo.sh` na raiz do repositório.

---

## 1. Tempo de desenvolvimento — o loop interno

No tempo de desenvolvimento, o consumidor é **você, lendo**. O tracing
percorre os testes que você já escreve; nada extra é instrumentado e não
existe infraestrutura de vida longa.

- **Integração de testes sem configuração.** Com a extensão do JUnit 5
  no classpath (descoberta via ServiceLoader; as regras do JUnit 4 são
  declaradas explicitamente) e `narrativetrace.output=true`, cada teste
  escreve seu trace como um artefato revisável:
  `build/narrativetrace/traces/<TestClass>/<test>.md`, mais um `.json`
  canônico e um diagrama Mermaid por cenário. O [plugin do
  Gradle](guia-do-plugin-de-gradle.md) faz o wiring das dependências, a
  flag de compilador `-parameters` e as propriedades da JVM de testes em
  um único bloco `narrativeTrace { }`.
- **Os testes que falham contam sua própria história.** Em um teste
  vermelho, o trace completo capturado é impresso como a narrativa da
  falha — o que foi chamado, com quais valores, onde parou — antes de
  você recorrer ao depurador.
- **Feedback de nomenclatura enquanto ainda é barato.** A pontuação de
  clareza lê os traces capturados e relata a qualidade dos nomes de
  métodos/classes/parâmetros por cenário (`clarity-report.md`, resumo no
  console). Nesta etapa, é consultiva: um espelho, não um gate.
- **O vocabulário cresce a partir do código.** Com
  `narrativetrace.glossary=true`, a suíte coleta termos do domínio no
  `glossary.json` versionado — a curadoria acontece na revisão de
  código, como qualquer outro artefato.

O hábito que esta etapa constrói é o ponto central: **o trace é a
primeira coisa que você lê**, antes das instruções de log, antes do
depurador. Tudo o que vem depois no ciclo reutiliza essa mesma captura
legível.

## 2. Tempo de testes — CI, integração, aceitação, smoke

No tempo de testes, os consumidores são **máquinas e revisores**: o gate
do build, os arquivos de artefatos, outras plataformas, ferramentas de
IA e testers observando um ambiente implantado.

- **Traces como artefatos de build.** Os arquivos por teste da etapa 1
  são artefatos de CI: arquive `build/narrativetrace/` e uma pipeline
  com falha carrega sua própria narrativa. Revisores leem o que o código
  fez, não o que quem fez o commit diz que ele faz.
- **Quality gates.** O `clarityCheck` roda dentro de `./gradlew check`:
  os limiares (`clarity.minScore`, `clarity.maxHighIssues`,
  `clarity.maxSuiteIssues`) transformam o espelho da etapa 1 em um gate
  de build, com `warnOnly` como modo de lançamento suave. O
  `clarityScan` pontua classes compiladas sem executar os testes, para
  pipelines que separam as duas coisas.
- **Exportações legíveis por máquina.** Duas flags opt-in ampliam a
  audiência: `narrativetrace.canonicalJson=true` escreve, por teste,
  arrays de entradas do schema 1.2 (o contrato que as demais
  implementações e os fixtures de conformidade consomem);
  `narrativetrace.structuralJson=true`
  escreve o artefato estrutural sem valores (ADR-002 Nível 1) que pode
  ser entregue a ferramentas de revisão com IA com exposição zero de
  dados.
- **Testes de aceitação e smoke observam um sistema implantado.** Aqui o
  tracing migra da JVM de testes para a aplicação sob teste, usando as
  integrações de produção desde cedo: o filtro de servlet, o wiring do
  Spring ou do Micronaut, ou — para um sistema que você não pode
  modificar — o agente Java
  (`-javaagent:narrativetrace-agent-<version>-standalone.jar`) anexado
  ao deploy. Um teste smoke então faz asserções sobre *comportamento que
  você consegue ler*: o fluxo narrado da requisição nos logs do
  container, correlacionado pelo `traceId` no MDC. O exemplo WildFly
  `narrativetrace-examples:ejb4` tem exatamente esse formato.
- **O mesmo schema em todo lugar.** Como os ambientes de aceitação
  emitem o mesmo stream canônico que os testes unitários, ferramentas
  escritas contra uma etapa funcionam contra a outra — essa é a paridade
  que o schema canônico existe para proteger.

## 3. Produção

Em produção, os consumidores são **operadores, ferramentas de log e —
por trás de roteamento explícito — sistemas de IA**. As restrições de
design mudam: custo, tolerância a perdas e privacidade dominam.

- **Escopo e custo.** `narrativeTrace { scope.set("production") }` move
  a biblioteca para o classpath de runtime. O nível de captura é o dial
  de custo — `OFF` (~1–2 ns por chamada) → `ERRORS` → `SUMMARY` →
  `NARRATIVE` → `DETAIL` — e é comutável em runtime
  (`config.setLevel(...)`), de modo que "aumentar a narração durante o
  incidente e baixar depois" é uma operação, não um deploy.
- **Dois gates independentes (ADR-008).** O nível de captura decide o
  que é registrado; sua configuração de logging decide o que é emitido e
  para onde. Os eventos narrativos fluem pela bridge do SLF4J sob o
  logger `narrativetrace` (ENTRY/RETURN em TRACE, EXCEPTION em WARN por
  padrão), então a configuração comum de appenders — não a configuração
  da biblioteca — os roteia, filtra e envia.
- **A identidade viaja no MDC, não no texto da mensagem.** A identidade
  do trace (`traceId`, `nt.class`, `nt.method`, `nt.package`), a
  identidade do serviço (`service.*`, host/pid/runtime quando
  `capture.resource` está ativo) e o modelo de atributos em três níveis
  (ADR-009) aparecem como chaves de MDC; seu padrão de log ou encoder
  JSON decide a visibilidade.
- **OpenTelemetry.** Os traces capturados são exportados como spans OTel
  (em lote ou com listener ao vivo) ao lado do seu tracing existente; o
  NarrativeTrace nunca reemite atributos de recurso que pertencem ao SDK
  cliente.
- **As visões derivadas ao vivo são subscribers.** O pipeline de melhor
  esforço (`BufferedEventConsumer`) expõe uma costura de publisher; tudo
  o que é derivado se conecta ali, fora da thread chamadora: o stream de
  narração traduzida (loggers `narrativetrace.i18n.<locale>` ou arquivos
  Markdown por trace, guiados pelo glossário) e o stream estrutural
  seguro para IA (`narrativetrace.ai.structural`, uma linha JSON sem
  valores por evento). Esse caminho **perde dados sob carga por design**
  — ele descarta em vez de bloquear a aplicação. O caminho síncrono do
  listener é o registro durável; as visões derivadas são conveniências
  construídas em cima.
- **Quem inicia uma thread de drenagem é dono do `close()`.** Toda
  integração aqui — Spring, o filtro de servlet, o agente, ambas as
  extensões de JUnit — retém eventos por meio de um consumer criado com
  `startConsumer=false`: sem thread em segundo plano, sem shutdown hook
  da JVM, nada o enraizando. Um contexto que sai de escopo é coletado
  como qualquer outro objeto, e um que não é fechado não vaza nada.
  Construa, em vez disso, um `BufferedEventConsumer` com sua própria
  thread de drenagem (`new BufferedEventConsumer()`, ou a forma `(int
  capacity)`) e o shutdown hook que ele registra enraíza o consumer, seu
  ring e tudo o que o ring retém até a JVM encerrar. Essa é a única
  forma em que o `close()` é obrigatório — `try`-with-resources é a
  maneira de expressar isso.
- **As flags de captura permanecem conservadoras.** `capture.resource`
  está ativa por padrão (desative-a em deploys sensíveis a hostname);
  `capture.sourceLocation` e `capture.instanceIds` estão desativadas por
  padrão. As flags controlam a captura, nunca o formato do schema —
  campos ausentes permanecem ausentes, e os consumers nunca ramificam
  com base na configuração.

## A matriz de etapa × configuração

| | Desenvolvimento | Testes / CI / aceitação | Produção |
|---|---|---|---|
| **Consumidor principal** | O desenvolvedor, lendo | Gates, artefatos, máquinas, testers | Operadores, pilha de logs, streams de IA roteados |
| **Integração** | JUnit 5/4 via o plugin do Gradle | O mesmo, mais filtros/agente em ambientes implantados | Proxy / Spring / Micronaut / servlet / agente; `scope = "production"` |
| **Nível de captura** | `DETAIL` | `DETAIL` | Baseline `SUMMARY` ou `NARRATIVE`; `DETAIL` sob demanda; `OFF`/`ERRORS` em caminhos quentes |
| **Saídas** | `.md` + `.json` + diagramas por teste, narrativas de falha | O mesmo que artefatos de CI; `canonicalJson` / `structuralJson`; logs de container na aceitação | Stream SLF4J + MDC; spans OTel; streams de subscribers (i18n, estrutural) |
| **Gates** | Nenhum — a clareza é consultiva | Limiares do `clarityCheck`, suas próprias asserções sobre traces | Nenhum — observabilidade, não imposição |
| **Modelo de perda** | Completo (captura síncrona no teste) | Completo no teste; implantado = modelo de produção | Caminho síncrono durável; caminho de subscribers de melhor esforço, descarta sob carga |
| **Ocultação** | **Sempre ativa** | **Sempre ativa** | **Sempre ativa** |

## Postura de privacidade ao longo do ciclo

A última linha da matriz é deliberada e vale a pena declarar como regra:

**A ocultação é incondicional. Não existe etapa, flag ou propriedade que
desative o `@NotTraced` ou as regras de ocultação baseadas em nome — por
decisão, não por omissão** (decisão do responsável, 2026-08-16). O
raciocínio:

- O valor de "as saídas são seguras" está em ser um *invariante*, não um
  estado de configuração. Todo artefato que esta biblioteca escreve —
  traces de teste, arquivos de CI, logs de container, streams de IA —
  pode ser compartilhado sem antes auditar quais flags estavam ativas
  quando foi produzido.
- "Os dados de teste são sintéticos" está exatamente errado nos
  ambientes onde um interruptor de desligar seria mais tentador:
  sistemas de aceitação e staging são rotineiramente povoados com dados
  no formato de produção.
- Artefatos de teste viajam — para o controle de versão, para tickets e
  para ferramentas de IA. Essas são as saídas que a ocultação existe
  para proteger.

Quando um valor ocultado bloqueia a depuração, as respostas suportadas
são, em ordem: fazer asserções sobre o comportamento em vez do valor
secreto; usar os **tipos declarados** capturados (schema 1.2) para
diagnosticar a forma sem revelar o dado; e criar fixtures obviamente
falsos cujos *nomes* carregam a informação
(`"card-token-for-decline-path"` é ocultado, mas o nome e o tipo do
parâmetro ainda narram). Uma **via de escape por teste, explicitamente
anotada** (visível na revisão de código, conectada apenas pela extensão
do JUnit — nunca pela cadeia de configuração de produção — e que
estampa um aviso bem visível em qualquer arquivo que toque) é uma
direção de design registrada, condicionada à demanda: será construída
quando um usuário real esbarrar nesse limite, e um interruptor global
não será construído de jeito nenhum.

Note o que a ocultação *não* precisa carregar sozinha: os níveis
`SUMMARY` e `NARRATIVE` suprimem todos os valores de parâmetros, o
artefato estrutural elide arquiteturalmente todo campo de valor, e os
valores renderizados não conseguem forjar linhas de log nem quebrar a
sintaxe de Markdown/diagramas (blindagem contra injeção). A ocultação é
uma camada de uma postura, e cada etapa do ciclo escolhe as camadas de
que precisa.

## Para onde ir a seguir

- [Guia de instalação](guia-de-instalacao.md) — coloque a etapa 1 rodando em minutos
- [Guia de configuração](guia-de-configuracao.md) — todas as chaves referenciadas acima
- [Guia do plugin do Gradle](guia-do-plugin-de-gradle.md) — o DSL `narrativeTrace { }` e os gates
- [Guia de clareza](guia-de-clareza.md) — o modelo de pontuação por trás do gate
- [Guia de funcionalidades](guia-de-funcionalidades.md) — o catálogo completo, com tier e status por funcionalidade
