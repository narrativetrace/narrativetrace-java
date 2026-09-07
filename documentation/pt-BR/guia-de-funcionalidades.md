<!-- source: documentation/feature-guide.md blob d91db0adb13c | translated: 2026-09-07 | reviewed: - -->
# Guia de funcionalidades do NarrativeTrace

[English](../feature-guide.md) | [Español](../es/guia-de-funcionalidades.md) | **Português** | [简体中文](../zh-CN/功能指南.md)

**Escopo: Produto.** Este guia é o catálogo canônico das
funcionalidades do NarrativeTrace para todas as plataformas (Java,
TypeScript, Python, .NET).

O catálogo tem um único lar para que não se espalhe em cópias por
plataforma: uma decisão que vale em todas as plataformas é registrada
uma única vez, aqui; um mecanismo específico da implementação de uma
plataforma fica na documentação própria daquela plataforma. Cada linha
publicada cita as classes Java que a implementam (`module: main
classes`), para que quem lê vá direto da linha ao código deste
repositório. Uma linha descreve *o que* é a funcionalidade; o acordo
entre plataformas sobre *como* ela se comporta pertence aos fixtures de
conformidade sobre o schema JSON canônico, não à prosa deste
documento.

Organizado pelo que você quer realizar, não por módulo.

**Rótulos de status:**

- **Aberto** — lançado, Apache 2.0, em `narrativetrace-api`: as anotações, o
  modelo de eventos, a especificação do formato de saída e as SPIs. Um padrão
  aberto, para que qualquer implementação possa adotá-lo.
- **Gratuito** — lançado, código disponível (BSL 1.1, converte para Apache 2.0
  após quatro anos), disponível neste repositório. Gratuito para uso em
  produção.
- **Gratuito/fechado** — lançado gratuitamente, proprietário, construído fora
  deste repositório.
- **Pro** — lançado no NarrativeTrace Pro (tier comercial).
- **Em desenvolvimento** — em construção ativa; o design está definido.
- **Planejado** — especificado, ainda não iniciado; pode mudar.

A categoria sob a qual cada módulo deste repositório é lançado é declarada
em `licensing.properties`, e o build recusa um grafo de dependências que as
licenças não conseguem sustentar.

Última auditoria completa deste guia em relação ao código Java: **2026-08-18**.

---

## Capture a história do seu código (tracing essencial)

| Funcionalidade | Status | Implementação Java | Notas |
|---|---|---|---|
| Captura automática de narrativa — nomes de método, classe e parâmetros, valores de retorno, tempos, erros; zero instruções de log | Gratuito | `proxy: NarrativeTraceProxy` · `agent: NarrativeClassFileTransformer` · `core: NarrativeContext, TraceEvent` | Tier 1: sem anotações, sem configuração. Nomes de parâmetros exigem `-parameters` (o plugin do Gradle o adiciona) |
| Anotações de enriquecimento — `@Narrated`, `@OnError`/`@OnErrors` com templates `{param}`, `@NarrativeSummary` | Gratuito | `core: Narrated, OnError, NarrativeSummary, TemplateParser` | Placeholders não resolvidos são reportados em tempo de teste (`TemplateWarningCollector`); a narração é renderizada em toda chamada traçada, inclusive chamadas folha. [annotations-guide.md](guia-de-anotacoes.md) |
| Ocultação de dados sensíveis — `@NotTraced` (todas as saídas, sempre) + regras de ocultação baseadas em nome | Gratuito | `core: NotTraced, RedactionPolicy` | Lista de negação por substring, sem distinção de maiúsculas/minúsculas, sobre nomes de campos; padrões integrados (password, token, ssn, …) + conjuntos personalizados. As chaves de `Map` são renderizadas pelo mesmo caminho protegido (ocultação + limites), nunca com o `toString()` bruto. Os wrappers de conteúdo único (`Optional`, os opcionais primitivos, `Future`, `AtomicReference`) e os contêineres com forma de lista ou de par (`AtomicReferenceArray`, um `Map.Entry` avulso) são abertos e seu conteúdo é renderizado pelas mesmas regras, de modo que a ocultação não se perde um contêiner mais fundo |
| Contrato de conclusão void — métodos void não carregam valor renderizado (`null`, nunca a string "null") | Gratuito | captura em `proxy` + `agent`; respeitado por todos os renderers/exportadores | Markdown/texto não renderizam nada, o JSON omite `returnValue`, os diagramas renderizam ✓, o SLF4J registra "← completed"; um `"null"` renderizado sempre significa um retorno null real |
| Blindagem contra injeção na saída — valores renderizados não conseguem forjar linhas de log nem quebrar a sintaxe de Markdown/diagramas | Gratuito | `core: MarkdownEscape, ControlEscape` · `diagrams: DiagramText` | Saneamento de caracteres de controle, escape de HTML, alargamento dinâmico dos delimitadores de código (code fences) |
| Cinco níveis de captura (OFF → ERRORS → SUMMARY → NARRATIVE → DETAIL), alteráveis em runtime | Gratuito | `core: TracingLevel, NarrativeTraceConfig` | OFF custa ~1–2 ns; SUMMARY/NARRATIVE suprimem os valores de parâmetros |
| Arquitetura de níveis com duas comportas — o nível de captura e o nível de log são independentes | Gratuito | `core: NarrativeTraceConfig` · `slf4j: Slf4jTraceEventListener` | ADR-008. [configuration-guide.md](guia-de-configuracao.md) |
| Resolução de configuração — propriedade de sistema → `narrativetrace.properties`, falha imediata em configuração duplicada | Gratuito | `core: ConfigResolver, DuplicateConfigurationException` | Dois arquivos de configuração no classpath são um erro definitivo, não uma precedência silenciosa |
| Captura de concorrência — grupos fork/join e fire-and-forget, enxerto entre threads, threads virtuais, diagnósticos de tempo de espera e de assincronia sequencial | Gratuito | `core: ForkGroup, FireAndForgetGroup, ContextSnapshot` · `render: SequentialAsyncDetector` | Verificado com jcstress; a análise de tempo de espera e de assincronia sequencial ocorre na renderização (saída Markdown). `captureTrace()` é limitado ao thread — capture no thread que está gravando ou enxerte via `ContextSnapshot.wrap()` |
| Identidade do trace — traceId, nomes de trace legíveis por humanos, derivação de storyId/chapterId | Gratuito | `core: TraceId, SpanContext` · `export: CanonicalEntryMapper` | Alinhada ao schema canônico |
| Escalonamento de trace para loops de alto fan-out — amostragem, limites de largura/profundidade, modo streaming | Planejado (Gratuito) | — | Até lá: opt-out com `TracingLevel.OFF` em loops quentes |

## Conecte ao seu stack (integrações)

| Funcionalidade | Status | Implementação Java | Notas |
|---|---|---|---|
| Encapsulamento com proxy dinâmico do JDK | Gratuito | `proxy: NarrativeTraceProxy` | `NarrativeTraceProxy.trace(...)`, sobrecargas para uma ou várias interfaces |
| Agente Java — instrumentação de bytecode, zero mudanças de código, filtragem por pacote | Gratuito | `agent: NarrativeTraceAgent, AgentConfig` | Filtre via argumentos do agente ou `narrativetrace.properties`. O jar com classificador `-standalone` empacota o core + a ponte SLF4J para anexar via `-javaagent` em hosts sem ferramenta de build (servidores de aplicação); o argumento de agente `loggingJars=` injeta um provedor SLF4J via `appendToSystemClassLoaderSearch` |
| Spring — `@EnableNarrativeTrace`, encapsulamento automático de beans | Gratuito | `spring: EnableNarrativeTrace, NarrativeTraceBeanPostProcessor` | [spring-integration-guide.md](guia-de-integracao-com-spring.md) |
| Micronaut — encapsulamento de beans + filtro HTTP reativo | Gratuito | `micronaut: NarrativeTraceBeanListener` · `micronaut-http: NarrativeTraceHttpFilter` | Módulos Kotlin-first. [micronaut-integration-guide.md](guia-de-integracao-com-micronaut.md) |
| Filtro de ciclo de vida de requisições servlet (sem exigir Spring) + wiring `@Configuration` do Spring Web | Gratuito | `servlet: NarrativeTraceFilter` · `spring-web: NarrativeTraceWebConfiguration` | `@Configuration` baseada em import, não auto-configuração do Boot |
| Continuidade de trace entre processos — `traceparent` do W3C lido em requisições de entrada, escrito em chamadas de saída | Gratuito | `core: Traceparent, NarrativeContext.adoptTraceparent/outboundTraceparent` · `servlet: NarrativeTraceFilter` · `micronaut-http: NarrativeTraceHttpFilter` | Uma única história através das fronteiras entre serviços. Os filtros adotam o cabeçalho de entrada; `outboundTraceparent()` fornece a qualquer cliente HTTP o valor a enviar. Degrau 1 do ADR-014: o span adotado é pai apenas do span raiz, e um cabeçalho malformado é ignorado em vez de falhar a requisição |
| Extensão JUnit 5 / regras JUnit 4 — cenários por teste | Gratuito | `junit5: NarrativeTraceExtension` · `junit4: NarrativeTraceClassRule, NarrativeTraceRule` | A autodetecção via ServiceLoader é exclusiva do JUnit 5; as regras do JUnit 4 são declaradas explicitamente |
| Plugin do Gradle — wiring de dependências, `-parameters`, tasks de clareza | Gratuito | `gradle-plugin: NarrativeTracePlugin, ClarityCheckTask` | [gradle-plugin-guide.md](guia-do-plugin-de-gradle.md) |
| Propagação de contexto entre threads via Micrometer (Spring Boot 3 / Reactor / `@Async`) | Gratuito | `micrometer: NarrativeTraceThreadLocalAccessor` | Um único `ThreadLocalAccessor` sob a chave `"narrativetrace"` |
| Apps de referência executáveis — e-commerce, comparação de nomenclatura no Minecraft, empréstimo de biblioteca (Kotlin), demo de clareza | Gratuito | `examples: ECommerceExample, MinecraftExample, LibraryExample, ClarityDemoExample` | Módulo somente de código-fonte com ~100 testes de exemplo |
| Servidores de aplicação Jakarta EE / EJB — tracing sem código de um WAR não modificado via o agente (verificado com WildFly) | Gratuito | `agent: NarrativeTraceAgent` · `examples: ejb4` | WAR EJB 4 livre de dependências, traçado apenas com `-javaagent` e o jar `-standalone`; receita executável e pegadinhas do WildFly em `narrativetrace-examples/ejb4` |
| Ponte de interceptor EJB `@AroundInvoke` (alternativa sem agente) | Planejado (Gratuito) | — | No backlog, condicionado à demanda — o caminho do agente acima é a forma lançada |
| Opção de contexto `ScopedValue` do Java 21+ | Planejado (Gratuito) | — | Backlog; `ThreadLocalNarrativeContext` já funciona com threads virtuais hoje |

## Leia a história (saídas)

| Funcionalidade | Status | Implementação Java | Notas |
|---|---|---|---|
| Renderers de texto indentado e Markdown (conectados à saída de testes); renderer de prosa (API de biblioteca) | Gratuito | `core: IndentedTextRenderer, MarkdownRenderer, ProseRenderer` | Prosa ainda não é uma opção de `narrativetrace.format` — apenas API + exemplos. Markdown renderiza os retornos do pai inline na linha de entrada (sem repetição de fechamento) |
| Referências de valor no trace — deduplicação endereçada por conteúdo de valores capturados repetidos com rótulos legíveis (`‹Hotel›=full` na primeira emissão, `‹Hotel›` depois) | Gratuito | `core: ValueReferenceIndex` (via `MarkdownRenderer`) | Rótulos a partir do campo de identidade do valor estruturado (name/id/description/…), nunca um campo ocultado; a igualdade de bytes certifica a mesmidade — qualquer diferença é renderizada por completo; a contenção dentro de outros valores capturados conta e é substituída |
| Deltas de valor dentro do trace — uma recaptura da mesma entidade, alterada, é renderizada como um diff contra a referência (`‹Dinner›′{amount: 100.0→92.0, currency: "USD"→"EUR"}`) | Gratuito | `core: ValueDelta` (via `ValueReferenceIndex`, `MarkdownRenderer`) | "Mesma entidade" é o mesmo nome de tipo estruturado mais um campo de identidade igual — a mesma escada que nomeia o rótulo; bytes renderizados diferentes significam que mudou. O diff é calculado a partir dos dois valores estruturados e nomeia apenas os campos escalares alterados (string, inteiro, decimal, booleano, instante, null), nunca reconstruindo um render plano a partir de um estruturado. Tudo o que ele não consegue expressar — um objeto ou lista aninhada alterada, um conjunto de campos diferente, um valor sem campo de identidade — é renderizado por completo exatamente como antes, sem carimbar um rótulo em uma definição à qual nada volta a se referir. Uma variante alterada que por sua vez se repete é definida COMO o diff (`‹Dinner·2›=‹Dinner›′{…}`), então ainda ganha um rótulo reutilizável, e uma iteração de loop dobrada é nomeada pelo seu diff na linha `×k more`. Os decimais viajam como `double`: um `BigDecimal` capturado como `100.00` imprime `100.0` no diff, enquanto a linha de referência ainda mostra o texto original. Diferente dos diffs entre execuções, a baseline está dentro do mesmo documento, então o artefato permanece autocontido. Somente apresentação e somente Markdown |
| Dobra de loops — condensa subárvores irmãs repetidas de mesma forma em Markdown | Gratuito | `core: LoopFold, StructuralTraceRenderer#subtreeKey` (via `MarkdownRenderer`) | Uma sequência máxima de ≥2 irmãos sequenciais consecutivos estruturalmente idênticos renderiza a primeira iteração por completo, depois uma única linha `×k more: ‹Dinner›, ‹Taxi› — same flow (validate ✓ → record ✓) — 12ms total, 2–5ms each`. "Mesma forma" é a projeção estrutural sem valores (reutilizando o oráculo do item 8): assinaturas, forma dos filhos e tipos de resultado iguais — uma chamada divergente, uma única exceção isolada, ou uma divergência de tipo de resultado é renderizada por completo fora da dobra (a anomalia é o sinal); uma sequência de iterações que lançam de forma idêntica ainda se dobra. Cada iteração dobrada é nomeada pelo seu primeiro argumento distintivo — como um diff `‹ref›′{…}` quando o documento já define essa entidade, ou então pela escada de identidade (`ValueReferenceIndex`, consistente com usos posteriores de `‹ref›`) — posicional `#n` quando nenhum campo de identidade se aplica; as durações são agregadas (total + faixa, nunca por iteração); os rótulos têm limite de 6 e a cauda de fluxo, de 8 com `…`. Somente apresentação e somente Markdown — JSON, `.nt`, diagramas e visualizações traduzidas permanecem inalterados; a concorrência (grupos fork, fire-and-forget) nunca se dobra |
| Arquivos de trace por teste — Markdown com todos os detalhes com frontmatter YAML + JSON canônico + diagrama Mermaid por cenário | Gratuito | `core: TraceTestSupport, TraceFileWriter, FrontmatterBuilder` | `build/narrativetrace/traces/<Class>/<test>.md` + `.json` + `diagrams/` |
| Arquivos de trace estrutural seguros para IA — artefato separado sem valores por teste, além de um stream estrutural ao vivo | Gratuito | `core: StructuralTraceRenderer, StructuralProjection, TraceTestSupport` · `slf4j: StructuralSubscriber` | `structural/<Class>/<scenario>.nt` por cenário — apenas nomes, hierarquia e tipos de resultado; determinístico (byte-idêntico para comportamento idêntico); especificação do formato: [structural-trace-format.md](formato-de-trace-estrutural.md). `narrativetrace.structuralJson=true` emite adicionalmente `<test>.structural.json` — entradas do schema 1.2 com todo campo de valor de runtime elidido (ADR-002 Nível 1; os acréscimos do 1.2 têm forma de identidade e sobrevivem à projeção); `StructuralSubscriber` na costura do pipeline emite a mesma projeção ao vivo como linhas JSON no logger `narrativetrace.ai.structural` |
| Exportação JSON canônica (schema de árvore de capítulos) | Gratuito | `core: JsonExporter, ChapterExporter, CanonicalEntry, CanonicalEntryMapper` | O `.json` por teste é conectado e validado contra o schema `chapter-tree.schema.json` por um gate de conformidade sobre o escritor real; `narrativetrace.canonicalJson=true` grava adicionalmente um `.canonical.json` por teste (entradas canônicas planas — o formato para ports/fixtures de conformidade). `nt.schemaVersion` é global entre as entradas e o envelope de capítulo — 1.2 em todo lugar, carimbado a partir de `CanonicalEntry.SCHEMA_VERSION` (= 1.1 + campos de identidade de amplitude de captura). A identidade do capítulo é antecipada (eager): `trace_id`, `nt.storyId` e `nt.chapterId` são sempre gravados — o trace id é adotado, herdado ou gerado (nunca uma constante compartilhada), a história é derivada da primeira chamada de nível raiz e o capítulo é igual a ela — resolvidos uma única vez por árvore, de modo que um capítulo e suas próprias entradas sempre nomeiam o mesmo trace. O exportador de capítulo é API de biblioteca ainda sem um chamador em produção |
| Diagramas de sequência por cenário — Mermaid + PlantUML | Gratuito | `diagrams: MermaidSequenceDiagramRenderer, PlantUmlSequenceDiagramRenderer` | Mermaid emitido automaticamente por cenário; PlantUML via `narrativetrace.format=plantuml` (substitui o trace em Markdown) |
| Resumos de teste no console com pontuações de clareza + narrativas de falha | Gratuito | `core: ConsoleSummaryReporter` · `output: TraceTestSupport` | Em caso de falha, o relatório localiza a mudança: o delta estrutural contra o último verde quando existe uma baseline (resumo + diff legível), o trace completo caso contrário; os caminhos de trace são impressos como links `file://` clicáveis |
| Delta narrativo no loop de testes — delta estrutural de uma linha após cada execução + modo de aprovação com baselines commitadas | Gratuito | `core: StructuralDelta, ScenarioDelta, NarrativeApproval` · `junit5/junit4` · `gradle-plugin: approveNarratives` | O `.nt` em disco é a baseline do último verde — execuções com falha comparam contra ela, mas nunca a sobrescrevem. O rodapé da suíte termina com `Since last green: 4 scenarios unchanged · 1 new · 1 changed: "…" (+4 calls X.y)`. Modo de aprovação (`narrativetrace.approval=true`; DSL do plugin `approval.set(true)`, baselines por padrão em `src/test/narratives/<Class>/<scenario>.approved.nt`): um teste que passa mas cuja estrutura difere de sua baseline commitada falha com um diff legível, a estrutura atual fica ao lado como `.received.nt`, e `approveNarratives` promove os arquivos revisados. O mecanismo é Gratuito pela decisão de tier de 2026-08-23; o diff semântico, a revisão por PR-bot e a análise de drift são Pro |
| Resumos de fluxo — caminhos agregados + frequências por ponto de entrada | Pro | Repositório Pro | Fronteira do core Gratuito imposta com ArchUnit (`ArchitectureTest`) |
| Diffs de migração — comparação comportamental antes/depois | Pro | Repositório Pro | |
| Grafos de dependência em runtime (sempre chamado vs condicional) | Pro | Repositório Pro | |
| Diagramas de sequência agregados — todos os ramos observados em um único diagrama, `alt/else` + contagens de frequência | Em desenvolvimento (Pro) | Repositório Pro | |
| Diagramas de atividade agregados | Planejado (Pro) | — | |
| Saídas Pro sem configuração — adicione os jars Pro e relatórios/diagramas agregados aparecem na próxima execução de testes, sem wiring | Em desenvolvimento (Pro) | Repositório Pro | Via as costuras ServiceLoader do core (ADR-010) |

## Mantenha seu stack de logging (logging + observabilidade)

| Funcionalidade | Status | Implementação Java | Notas |
|---|---|---|---|
| Ponte SLF4J — eventos de narrativa através dos seus appenders existentes, níveis de log por tipo de evento, stream síncrono à prova de falhas | Gratuito | `slf4j: Slf4jTraceEventListener` · `core: DualPathPipeline` | Logger `narrativetrace`; padrões ENTRY/RETURN=TRACE, EXCEPTION=WARN |
| Enriquecimento de MDC — modelo de atributos de três níveis (resource / trace / span), campos persistentes com escopo de requisição | Gratuito | `core: AttributeTier, SpanContext` · `servlet: NarrativeTraceFilter` | ADR-009 |
| Coexistência com logs escritos manualmente | Gratuito | — | Remova-os no seu próprio ritmo |
| Descarte ruidoso — uma captura que perdeu eventos diz isso na narrativa que escreve | Gratuito | `api: TraceLoss, TraceTree.loss()` · `core: LossFooter, BoundedEventBuffer, BufferedEventConsumer` | O caminho com buffer é de melhor esforço por design, então nunca fica em silêncio: os eventos descartados são contados (sobrescritas do anel, descartes da drenagem adaptativa e quedas de subscriber, igualmente) e todo formato com um slot de rodapé carrega uma linha nomeando a contagem e `narrativetrace.buffer.capacity`. Texto, prosa, Markdown (blockquote + `incomplete: true` no frontmatter), Mermaid e PlantUML (sintaxe de comentário). O artefato estrutural `.nt` deliberadamente **não** carrega isso — é a baseline de aprovação e precisa permanecer byte-idêntico para comportamento idêntico. Uma captura limpa não diz absolutamente nada |
| Exportação de spans para OpenTelemetry — em lote pós-captura e listener ao vivo, atributos tipados, eventos de negócio nos spans pai | Gratuito | `opentelemetry: TraceSpanExporter, OtelTraceEventListener, SpanContextAttributeMapper` | O listener ao vivo limita os spans ativos com TTL |
| MDC ciente de PII — campos de identidade convertidos em hash para tokens pesquisáveis (`@MdcField(pii = true)`) | Planejado (Pro) | — | |
| Identidade de infraestrutura — contexto k8s / nuvem / container autodetectado como atributos de resource | Planejado (Pro) | — | |
| Chaves OTel semânticas — `@SpanAttribute("order.total_usd")`, `@SpanEvent("order.shipped")` | Planejado (Pro) | — | |
| Opções de pipeline de alta taxa de transferência — LMAX Disruptor; modo durável/WAL do Chronicle Queue | Planejado (Pro) | — | Veja o ADR-006 |

## Melhore o código (diagnósticos de clareza)

| Funcionalidade | Status | Implementação Java | Notas |
|---|---|---|---|
| Pontuação de clareza — qualidade dos nomes de método / classe / parâmetro a partir da execução real | Gratuito | `clarity: ClarityAnalyzer` + `MethodNameScorer, ClassNameScorer, ParameterNameScorer, CohesionScorer` | [clarity-guide.md](guia-de-clareza.md); experimental |
| Relatório de clareza no nível da suíte com alvos de renomeação + notas por elemento + resultados legíveis por máquina | Gratuito | `clarity: ClarityReportRenderer, ClarityJsonExporter, ElementNoteComposer` | `clarity-report.md` + `clarity-results.json` (o contrato que `clarityCheck` e o ferramental de CI consomem). Uma tabela **Elements** / array `elements` dá uma nota didática por elemento em cada pontuação (a clareza é uma professora, não uma juíza), separada dos problemas controlados por limiar. O schema 1.2 de resultados adiciona `elements` por cenário (o 1.1 adicionou `suiteIssues`); os consumidores devem continuar aceitando arquivos 1.0/1.1 |
| Scanner independente — pontua classes compiladas sem executar testes (`clarityScan`, CLI) | Gratuito | `clarity: ClarityScanner, ClarityScannerMain` | CLI `--format markdown\|json\|both` |
| Vocabulário do projeto na pontuação — o glossário commitado estende os dicionários integrados | Gratuito | `clarity: DomainVocabulary, ProjectVocabularySource` · `glossary: GlossaryVocabulary, GlossaryAwareClarityScannerMain` · `junit5/junit4/gradle-plugin` | Um arquivo, um fluxo de revisão: os verbos do `glossary.json` commitado pontuam como verbos do domínio, seus substantivos como tokens do domínio, e sua seção `abbreviations` de nível raiz (schema 2) declara a abreviatura aceita — um token meramente aparecer dentro de uma frase commitada não qualifica. Uma abreviatura listada é aceita *e* soletrada a partir de sua expansão declarada. A leitura é incondicional (diferente da coleta); só o arquivo *commitado* conta, então uma execução não pode expandir seu próprio vocabulário. Os níveis integrados mantêm autoridade — verbos genéricos, prefixos booleanos, placeholders sem sentido, sinônimos obsoletos e termos `stale` nunca são promovidos |
| Gate de CI (`clarityCheck`) | Gratuito | `gradle-plugin: ClarityCheckTask` | Se junta a `check` e falha o build por padrão; defina `warnOnly` para modo somente aviso |
| Tendências históricas de clareza | Planejado (Pro) | — | |
| Renomeação assistida por IA com evidência de trace | Planejado (Pro) | — | |
| Detecção de código morto e ramos não testados a partir de traces | Planejado (Pro) | — | |
| Análise de redundância de log — quais logs escritos manualmente a narrativa torna desnecessários, com economia de tokens | Planejado (Pro) | — | |

## Fale a linguagem do domínio (glossário e tradução)

| Funcionalidade | Status | Implementação Java | Notas |
|---|---|---|---|
| Glossário de domínio — um único arquivo de linguagem ubíqua por repositório (JSON canônico + visualização Markdown), coletado de forma aditiva a partir dos traces em tempo de teste | Gratuito | `glossary: Glossary, GlossaryJsonWriter, GlossaryJsonReader, GlossaryMarkdownRenderer, GlossaryHarvester, GlossaryMerger, GlossarySuiteHarvest` · `junit5: NarrativeTraceExtension` | Opt-in: `narrativetrace.glossary=true` (`narrativeTrace { glossary.set(true) }`), já que escreve fora do diretório de build |
| Coleta do glossário a partir de classes compiladas — `glossaryScan`, o único modo que coleta templates `@Narrated`/`@OnError` | Gratuito | `glossary: GlossaryStaticScanner, GlossaryScannerMain` · `gradle-plugin: NarrativeTracePlugin` | Somente estático por design: um trace capturado carrega a narração com os valores de runtime já interpolados |
| Termos canônicos + sinônimos obsoletos — o uso não canônico é sinalizado na saída da execução e suprimido da coleta | Gratuito | `glossary: AliasIndex, VocabularyViolations, RenameSuggester, VocabularySummaryFormatter, NonCanonicalTermIssues, GlossaryUsageReport` | Um termo por conceito. As violações chegam ao console, ao `glossary-usage.json` e ao `clarity-report.md` ("Suite Issues") / `clarity-results.json` (`suiteIssues`, schema 1.1); o gate `clarityCheck` é somente aviso por padrão, falhando de forma definitiva via `clarity.maxSuiteIssues`. A verificação de vocabulário só dispara quando já existe um `glossary.json` commitado antes da execução |
| Contextos delimitados — vocabulário com escopo definido por contextos mapeados a pacotes | Gratuito | `glossary: BoundedContext, ContextResolver, TermNormalizer` | O mesmo termo pode diferir por contexto; mapeamento de pacotes por prefixo mais longo, consciente de delimitadores, com fallback `_unassigned`. As regras do `TermNormalizer` (lista de manutenção de terminações em s, radical estável, idempotência) são a identidade dos termos nos glossários persistidos — toda implementação do NarrativeTrace as adota ao pé da letra |
| Visualizações de tradução de trace — arquivos Markdown por trace renderizados ao vivo a partir do pipeline de eventos + glossário | Gratuito | `glossary: TraceTranslationView, TranslationSubscriber, GlossaryTranslator, GlossaryLoader` | Os valores nunca são traduzidos; um `<traceId>.md` por trace, o rodapé lista as lacunas do glossário |
| Stream traduzido ao vivo — loggers SLF4J com sufixo de locale, roteáveis por appender para o mesmo destino ou um separado | Gratuito | `glossary: TranslationSubscriber` | Caminho de melhor esforço; o stream canônico permanece intocado |
| Tradução do glossário e geração de definições assistidas por IA | Planejado (Pro) | — | Apenas termos do glossário; opt-in explícito |
| Diagnóstico de vazamento de termos entre contextos | Planejado (Pro) | — | |

## Deixe os agentes de IA verem a verdade do runtime (integração com IA)

| Funcionalidade | Status | Implementação Java | Notas |
|---|---|---|---|
| Traces estruturais seguros para IA — sem valores de runtime, zero superfície de injeção | Gratuito | `core: StructuralTraceRenderer, StructuralProjection` | Lançado: o artefato `.nt` distinto (ADR-002 completo) mais a projeção `.structural.json` sob flag, com sua segurança fixada por um teste de propriedade; a supressão de valores SUMMARY/NARRATIVE também existe na saída voltada para humanos |
| Documentação orientada a LLM (`llms.txt`, `llms-full.md`) | Gratuito | `documentation/llms.txt, llms-full.md` | |
| Ferramentas de análise MCP — traces de execução, grafo de dependências, ramificação, sugestões de renomeação, relatório de clareza, nível de captura, comparação de traces | Pro | Repositório Pro | Disponível como biblioteca hoje |
| Servidor MCP (transporte stdio — conecte Claude Code / Cursor diretamente) | Em desenvolvimento (Pro) | Repositório Pro | |
| Ferramentas de inteligência de runtime — perfil de desempenho, saúde da arquitetura, análise de lacunas de teste, contratos comportamentais, impacto de refatoração | Planejado (Pro) | — | |
| Saída de IA pseudonimizada (Nível 2) — tokens sintéticos, fluxo de dados preservado, valores reais destruídos | Planejado (Pro) | — | |
| Saída de IA seletiva / com todos os detalhes (Níveis 3–4) com saneamento `@UntrustedInput` | Planejado (Pro) | — | |

## Prove o que aconteceu (auditoria e conformidade — Pro)

| Funcionalidade | Status | Implementação Java | Notas |
|---|---|---|---|
| Anotações de auditoria e SecOps — `@AuditEvent`, `@SecurityEvent`, `@AuditActor`, `@AuditEntityId`, `@AuditField` | Pro | Repositório Pro | |
| Inferência determinística — resolução de ação, ator, entidade e resultado | Pro | Repositório Pro | |
| Motor de políticas — `AUDIT_RELAXED` / `AUDIT_STRICT` / `SECOPS_STRICT`, filtragem por classificação | Pro | Repositório Pro | |
| Mascaramento de campos — `LAST4`, `REDACT`, `HASH` | Pro | Repositório Pro | |
| Verificador de governança — validação de conformidade de anotações em tempo de build | Pro | Repositório Pro | |
| Eventos JSON estruturados com versão de schema + correlação de trace | Pro | Repositório Pro | |
| Entrega durável — sink síncrono na trilha de logging, roteamento audit/secops | Em desenvolvimento (Pro) | Repositório Pro | Veja o ADR-005 para o porquê deste caminho |
| Interceptação Spring para anotações de auditoria | Em desenvolvimento (Pro) | Repositório Pro | |
| Task Gradle de governança + ação de CI | Em desenvolvimento (Pro) | Repositório Pro | |
| Rastreabilidade de controles — `controls={"AU-05"}`, registro de controles, mapeamento entre frameworks (PCI-DSS / SOC 2 / NIST / ISO), relatórios de cobertura | Em desenvolvimento (Pro) | Repositório Pro | |
| Artefatos de conformidade — exportação de relatório de governança, schema de eventos de auditoria, evidência por release | Em desenvolvimento (Pro) | Repositório Pro | |
| Fábricas de políticas para padrões de conformidade, mascaramento de PAN (`FIRST6_LAST4`), IP de origem | Planejado (Pro) | — | |
| Adaptadores de sink — Pangea, WorkOS, SIEM/HEC | Planejado (Pro) | — | |
| Encadeamento de hash com evidência de violação + verificador | Planejado (Pro) | — | |
| Pseudonimização de ator para GDPR (direito ao esquecimento) | Planejado (Pro) | — | |
| Exportação OCSF para interoperabilidade com SIEM | Planejado (Pro) | — | |

---

## Mantendo este guia honesto

Este guia existe para que nenhuma funcionalidade se perca entre
código, planos e documentos de visão — e para que nada seja lido como
lançado quando não está. Regras:

1. Toda funcionalidade visível ao usuário aparece aqui, exatamente uma
   vez, com um status.
2. Uma funcionalidade só passa para **Gratuito**/**Pro** quando está
   integrada, testada e documentada. "Em desenvolvimento" significa
   que o design está definido e o trabalho está agendado; "Planejado"
   significa apenas especificado.
3. Mudanças que adicionam ou promovem uma funcionalidade devem
   atualizar este arquivo no mesmo commit.
4. **O código decide.** Toda linha Gratuita lançada cita as classes
   Java que a implementam (`module: main classes`). Quando o guia e o
   código discordam, o código está certo e o guia é o bug — corrija a
   linha e registre a data de auditoria no cabeçalho.
5. **Um único catálogo, nunca uma cópia por plataforma.** Este guia diz
   o que é o produto; cada plataforma registra seus próprios mecanismos
   em seu próprio repositório. O status lançado/pendente por plataforma
   pertence à matriz de status dos mantenedores, um registro de
   trabalho privado — não a cópias deste catálogo.
