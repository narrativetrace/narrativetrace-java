<!-- source: documentation/clarity-guide.md blob 500fd784f720 | translated: 2026-09-11 | reviewed: - -->
# Guia de Clareza do NarrativeTrace Java

[English](../clarity-guide.md) | [Español](../es/guia-de-claridad.md) | **Português** | [简体中文](../zh-CN/清晰度指南.md)

Se o trace é o código, então a qualidade do trace é a qualidade do código. O módulo de clareza analisa os nomes dos seus métodos, classes e parâmetros, e pontua o quão bem eles comunicam a intenção.

## Início rápido

```java
var analyzer = new ClarityAnalyzer();
var result = analyzer.analyze(context.captureTrace());

var renderer = new ClarityReportRenderer();
System.out.println(renderer.render("Order Placement", result));
```

Com o JUnit 5, os relatórios de clareza são gerados automaticamente por padrão — sem necessidade de código, sem necessidade de configuração. `narrativetrace.output=false` desativa isso junto com todos os outros artefatos de trace.

## O que é pontuado

A clareza produz uma única pontuação geral (0.0–1.0) a partir de cinco componentes ponderados:

| Componente | Peso | O que mede |
|---|---|---|
| Nomes de métodos | 30% | Qualidade do verbo, especificidade dos tokens, abreviações, contagem de tokens |
| Nomes de parâmetros | 25% | Especificidade de domínio vs. tokens genéricos/sem significado |
| Nomes de classes | 20% | Qualidade do sufixo de papel, especificidade do prefixo |
| Estrutural | 15% | Penalidades por contagem de parâmetros e profundidade de chamadas |
| Coesão | 10% | Se os métodos se alinham com o sufixo de papel da classe |

## A pontuação na prática

### Nomes de métodos

O primeiro token é tratado como um verbo. Verbos de domínio pontuam mais alto; verbos genéricos pontuam mais baixo:

| Categoria de verbo | Exemplos | Pontuação |
|---|---|---|
| Domínio | `calculate`, `validate`, `reserve`, `dispatch` | 0.60 |
| Padrão | `create`, `find`, `delete`, `update` | 0.45 |
| Prefixo booleano | `is`, `has`, `can`, `contains` | 1.00 |
| Genérico | `get`, `set`, `process`, `handle`, `execute` | 0.10 |

Métodos com vários tokens, como `reserveInventory`, pontuam mais alto do que métodos de um único token, como `reserve`, porque os tokens adicionais aumentam a especificidade.

### Nomes de classes

Um sufixo de papel é esperado. Sufixos de padrão de projeto e funcionais pontuam bem quando combinados com um prefixo de domínio:

| Padrão | Pontuação | Por quê |
|---|---|---|
| `OrderService` | 1.0 | Prefixo de domínio + sufixo funcional |
| `Service` | 0.0 | Sem prefixo — sem significado |
| `DataProcessor` | Baixa | Prefixo vago + sufixo genérico |
| `BookingManager` | Média | Prefixo de domínio, mas `Manager` é genérico |

### Nomes de parâmetros

Nomes específicos de domínio pontuam alto; nomes genéricos pontuam baixo:

| Nível | Exemplos | Pontuação |
|---|---|---|
| Específico de domínio | `customerId`, `checkInDate`, `roomCategory` | 0.80+ |
| Genérico tipado | `id`, `name`, `count`, `status` | 0.50 |
| Vago | `data`, `info`, `result`, `object` | 0.10 |
| Sem significado | `x`, `foo`, `val`, `temp` | 0.00 |

### Penalidades estruturais

Métodos com mais de 4 parâmetros ou profundidade de chamada além de 5 são penalizados. Cada parâmetro excedente custa 0.1; cada nível de profundidade excedente custa 0.05.

### Coesão

Os métodos são verificados contra os verbos esperados para o sufixo de papel da classe. Espera-se que uma classe `Repository` tenha métodos como `find`, `save`, `delete`, `count`. Um método como `renderReport` em um `GuestRepository` é sinalizado como desalinhado.

## Problemas e severidade

Os problemas de clareza são relatados como itens ordenados por impacto:

| Severidade | Limiar | Exemplos |
|---|---|---|
| HIGH | pontuação ≤ 0.20 | `DataProcessor.execute(data)` |
| MEDIUM | pontuação ≤ 0.50 | `BookingManager.handleBooking(name, type)` |
| LOW | pontuação > 0.50 | Uso pontual de abreviação |

Problemas duplicados (mesma categoria e mesmo elemento) são deduplicados com uma contagem de ocorrências. Os problemas são ordenados pela pontuação de impacto (peso da severidade × ocorrências).

## Notas por elemento

Os problemas são limitados por um limiar — eles listam apenas os nomes que caem abaixo de um corte de severidade. As notas são o oposto: **uma nota em linguagem simples por elemento, em toda pontuação**, de modo que um bom nome aprende *por que* pontua bem, e um nome fraco aprende *o que mudar*. Um 0.86 isolado deixa de ser um veredito sem direito a recurso.

Todo método, classe, parâmetro e componente de record ganha uma nota construída a partir dos mesmos dicionários que os avaliadores usam:

| Elemento | Pontuação | Nota |
|---|---|---|
| `OrderService.reserveInventory` | 0.95 | Domain verb 'reserve' + domain noun 'inventory' |
| `Service.processData` | 0.30 | Generic verb 'process' + vague noun 'data' |
| `OrderManager` | 0.62 | Generic suffix 'Manager' — prefer a precise role |
| `amount` | 0.50 | Broad noun 'amount' — qualify it (e.g., orderAmount) |

As notas trazem orientação concreta sempre que os dicionários podem fornecê-la. Um verbo genérico ou não reconhecido, pareado com um substantivo de domínio conhecido, ganha uma dica de renomeação `verbNoun` — `Generic verb 'process' + broad noun 'order' — consider: backorderOrder, cancelOrder, fulfillOrder` — e qualquer abreviação penalizada é explicada inline: `; spell out: chk → check`. Os accessors de record são pontuados pela rubrica de substantivos e formulados para componentes (`Domain-specific component 'customerId'`).

Notas nunca entram em `issues[]` — elogio não é uma ação a se tomar. Elas aparecem na tabela **Elements** do relatório e no array `elements` de `clarity-results.json` (schema 1.2).

## Seu próprio vocabulário, a partir do glossário que você já tem

Os dicionários integrados conhecem o inglês geral de software. Eles não sabem
que `fold` é um verbo do seu domínio, que `tranche` é um substantivo preciso, ou
que `fx` é a abreviatura aceita da sua equipe — e um nome que eles não conhecem
pontua como desconhecido, não como específico de domínio.

Você os ensina com o arquivo de vocabulário que o seu repositório já carrega: o
`glossary.json` commitado (ADR-012). Não existe um segundo arquivo de dicionário
para manter sincronizado.

| Entrada do glossário | Tipo | O que a clareza aprende |
|---|---|---|
| `settle trade` | `verb-phrase` | `settle` é um verbo de domínio; `trade` é um substantivo de domínio |
| `credit tranche` | `noun-phrase` | `credit` e `tranche` são substantivos de domínio |
| `fx` | `word` | `fx` é um substantivo de domínio |

Termos com várias palavras ensinam um token de cada vez, porque os
identificadores são pontuados um token de cada vez. Todo contexto delimitado
contribui: um identificador não carrega pacote, então o escopo por contexto
não pode se aplicar no momento da pontuação.

### A abreviatura aceita é declarada, não inferida

Os termos ensinam vocabulário. Eles **não** decidem que uma grafia curta é
aceitável por si só — commitar a frase `calc total` não diz nada sobre se um
método pode se chamar `calcTotal`. Essa decisão vive em sua própria seção de
nível raiz do `glossary.json` (schema 2):

```json
{
  "schemaVersion": 2,
  "contexts": { },
  "abbreviations": { "fx": "foreign exchange", "calc": "calculate" },
  "terms": [ ]
}
```

Uma abreviação listada é aceita — nunca penalizada na pontuação, nunca
cobrada para ser desenvolvida por extenso — e o canal de notas a ensina a
partir da sua própria expansão: `; project shorthand: fx → foreign exchange`.
Uma abreviação que você *não* listou mantém seu tratamento integrado, mesmo
que apareça dentro de uma frase commitada.

A seção é de propriedade humana: a coleta nunca a escreve, e um merge a
carrega intocada. Um glossário que não declara abreviações permanece em
`schemaVersion` 1, e seu arquivo é idêntico byte a byte ao que sempre foi.

### O que o glossário não pode fazer

Os dicionários integrados mantêm sua autoridade. Um projeto pode ensinar aos
avaliadores uma palavra que eles não conhecem; não pode sobrepor uma que eles
já conhecem.

- **Verbos genéricos continuam genéricos.** Commitar `process` ou `handle`
  não os promove — `Generic verb 'process'` continua aparecendo nas notas, e a
  pontuação do nome do método continua refletindo isso. O mesmo vale para os
  prefixos booleanos (`is`, `has`).
- **Placeholders sem significado continuam sem significado.** `temp`, `foo` e
  companhia não são resgatados só por serem escritos no glossário.
- **Sinônimos obsoletos nunca são vocabulário.** Um alias existe para ser
  sinalizado; promovê-lo silenciaria o problema `non-canonical-term` para o
  qual foi declarado.
- **Termos `stale` não são vocabulário.** Marcar um termo como stale diz que
  a palavra saiu do domínio.

Só conta o arquivo *commitado*. Nada do que uma execução coleta realimenta
as pontuações dessa mesma execução — um vocabulário autoexpansível tornaria
as pontuações não determinísticas e autocertificadas. O commit é a aprovação
humana.

### Onde se aplica

| Superfície | Como o glossário é encontrado |
|---|---|
| Extensão do JUnit 5, regra do JUnit 4 | `narrativetrace.glossaryDir` (padrão: o diretório de trabalho; o plugin de Gradle o define como a raiz do repositório) |
| `clarityScan` | `--glossary-dir`, que o plugin de Gradle define como a raiz do repositório |

A leitura é incondicional — diferente da coleta, que é opt-in porque
reescreve arquivos fora do diretório de build. Um repositório sem
`glossary.json` pontua exatamente como pontuava antes de essa funcionalidade
existir, e um glossário que não pode ser lido degrada para os dicionários
integrados com um aviso, em vez de falhar a suíte.

## Saída do relatório

### Cenário único

```java
renderer.render("Guest books a room", result);
```

Produz um relatório em Markdown com uma tabela de pontuações, uma tabela de elementos e uma tabela de problemas (se houver):

```markdown
## Clarity Report — Guest books a room
Overall: 0.95 (high)

| Component  | Score |
|------------|-------|
| Method     | 0.98  |
| Class      | 1.00  |
| Parameter  | 0.90  |
| Structural | 1.00  |
| Cohesion   | 0.85  |

## Elements

| Element | Score | Note |
|---------|-------|------|
| `ReservationService.confirmReservation` | 0.98 | Domain verb 'confirm' + domain noun 'reservation' |
| `ReservationService` | 1.00 | Role suffix 'Service' |
| `guestId` | 0.80 | Domain-specific noun 'guestId' |
```

A tabela de elementos é renderizada para todo cenário — incluindo os de pontuação alta — enquanto a tabela de problemas fica restrita aos nomes abaixo do corte de severidade.

### Relatório da suíte

```java
renderer.renderSuiteReport(Map.of(
    "Guest books a room", result1,
    "Legacy data processing", result2
));
```

Produz um resumo ordenado de todos os cenários. Cenários com pontuação abaixo de 0.7 recebem um detalhamento com os problemas individuais; todo cenário — independentemente da pontuação — recebe uma tabela de elementos, de modo que nenhuma pontuação fica sem explicação.

## Integração com o JUnit 5

Por padrão (a menos que `narrativetrace.output=false` esteja em `junit-platform.properties`), a extensão do JUnit automaticamente:

1. Executa `ClarityAnalyzer.analyze()` no trace de cada teste
2. Escreve `clarity-report.md` no diretório de saída depois que todos os testes terminam
3. Imprime um resumo no console com a distribuição de pontuações:

```
NarrativeTrace — Suite complete
  2 scenarios recorded
  Clarity: 100% high | 0% moderate | 0% low
  Reports: build/narrativetrace
```

Nenhuma mudança de código necessária — apenas habilite a saída e rode seus testes.

## Aplicação no build com `clarityCheck`

O plugin de Gradle fornece uma task `clarityCheck` que faz o build falhar quando a qualidade da nomenclatura cai abaixo de um limiar. Isso torna a pontuação de clareza exigível, e não apenas consultiva.

### Configuração

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}

narrativeTrace {
    clarity {
        minScore.set(0.80)     // falha se algum cenário pontuar abaixo de 0.80
        maxHighIssues.set(0)   // falha se algum cenário tiver problemas de severidade HIGH
    }
}
```

### Como funciona

1. `./gradlew test` — a extensão do JUnit produz `build/narrativetrace/clarity-results.json`
2. `clarityCheck` lê o JSON e compara cada cenário com os limiares
3. `./gradlew check` executa `test` e `clarityCheck` automaticamente

### Saída de falha

Quando um cenário cai abaixo do limiar:

```
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':clarityCheck'.
> Clarity check failed:
    'Legacy data processing': score 0.45 < threshold 0.80
    'Legacy data processing': 3 HIGH issues (max 0)
```

### Modo somente aviso

Para uma adoção gradual, use `warnOnly` para registrar violações sem falhar o build:

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.70)
        warnOnly.set(true)
    }
}
```

### Problemas no nível da suíte

Problemas que pertencem à execução inteira, e não a um cenário específico — hoje, as violações de vocabulário `non-canonical-term` relatadas pela coleta do glossário — aparecem na seção **Suite Issues** do relatório e no array `suiteIssues` de nível superior do JSON. Eles nunca afetam as pontuações dos cenários. A verificação de vocabulário só é disparada quando existe um `glossary.json` commitado antes da execução; um projeto sem glossário nunca recebe problemas de suíte.

Por padrão, os problemas de suíte são apenas consultivos: `clarityCheck` os registra como avisos sem falhar o build. Ative uma barreira rígida com `maxSuiteIssues`:

```kotlin
narrativeTrace {
    clarity {
        maxSuiteIssues.set(0)   // falha em qualquer problema no nível da suíte
    }
}
```

### Contrato JSON

O arquivo `clarity-results.json` é o contrato entre a execução dos testes e a task `clarityCheck`:

```json
{
  "version": "1.2",
  "scenarios": [
    {
      "name": "Customer places order",
      "overallScore": 0.85,
      "methodNameScore": 0.90,
      "classNameScore": 0.95,
      "parameterNameScore": 0.80,
      "structuralScore": 1.00,
      "cohesionScore": 0.70,
      "issues": [
        {
          "category": "param-name",
          "element": "data",
          "suggestion": "Use a domain-specific name",
          "severity": "MEDIUM",
          "occurrences": 2,
          "impactScore": 4.0
        }
      ],
      "elements": [
        {
          "kind": "parameter",
          "element": "data",
          "score": 0.10,
          "note": "Vague name 'data' — say what it holds"
        }
      ]
    }
  ],
  "suiteIssues": [
    {
      "category": "non-canonical-term",
      "element": "billing.OverdraftService.openAccountWithOverdraft",
      "suggestion": "use canonical term 'overdraft account' → rename to openOverdraftAccount",
      "severity": "MEDIUM",
      "occurrences": 2,
      "impactScore": 4.00
    }
  ]
}
```

O array `suiteIssues` de nível superior (schema 1.1) contém os problemas de nível de suíte; ele está sempre presente, vazio quando a execução não produziu nenhum. O array `elements` de cada cenário (schema 1.2) carrega uma nota por elemento em toda pontuação. As duas adições são puramente aditivas: arquivos com schema 1.0/1.1 sem esses campos continuam sendo aceitos pelo `clarityCheck` e pelos consumidores do JSON.

## Demo

Execute a demo de clareza de reservas de hotel para ver a pontuação em quatro níveis de qualidade:

```bash
./gradlew :narrativetrace-examples:clarity:run
```

A demo traça quatro cenários com nomenclatura progressivamente pior — de `ReservationService.confirmReservation(guestId, roomCategory)` (excelente) até `DataProcessor.execute(data, val)` (fraco) — e gera um relatório de clareza da suíte mostrando as diferenças de pontuação.

## Componentes de NLP

O módulo de clareza usa NLP escrito à mão, sem dependências externas:

| Componente | Finalidade |
|---|---|
| `IdentifierTokenizer` | Divide camelCase e snake_case em tokens |
| `VerbDictionary` | Categoriza mais de 200 verbos (domínio, padrão, genérico, booleano) |
| `RoleSuffixDictionary` | Classifica sufixos de classe (padrão de projeto, funcional, genérico) |
| `GenericTokenDetector` | Classifica a especificidade dos tokens (sem significado → específico de domínio) |
| `AbbreviationDictionary` | Pontua mais de 140 abreviações em três níveis (universal, bem conhecida, ambígua) |
| `MorphologyAnalyzer` | Detecta classes gramaticais via sufixos (-tion, -ize, -able) |
| `CohesionScorer` | Verifica o alinhamento verbo-método com as expectativas do papel da classe |
| `ElementNoteComposer` | Transforma o mesmo conhecimento dos dicionários em uma nota didática por elemento, em toda pontuação |
| `DomainVocabulary` | As palavras próprias do projeto, lidas do glossário commitado; estende todos os dicionários acima sem sobrepô-los |

## Veja também

- [Guia de Configuração](guia-de-configuracao.md) — níveis de tracing, configuração da saída
- [Guia de Anotações](guia-de-anotacoes.md) — `@Narrated`, `@OnError`, `@NotTraced`
- [Guia de Instalação](guia-de-instalacao.md) — dependências e caminhos de integração
