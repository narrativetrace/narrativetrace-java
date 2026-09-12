<!-- source: documentation/structural-trace-format.md blob d3646cf3f74a | translated: 2026-09-12 | reviewed: - -->
# Formato de trace estrutural (`.nt`)

[English](../structural-trace-format.md) | [Español](../es/formato-de-traza-estructural.md) | **Português** | [简体中文](../zh-CN/结构化追踪格式.md)

O artefato de trace estrutural seguro para IA (ADR-002): um arquivo por
cenário de teste contendo apenas a *forma* do comportamento escrita
pelo desenvolvedor — zero valores em tempo de execução. Este formato é
**multiplataforma**: cada implementação do NarrativeTrace emite o formato
idêntico, o que permite que as baselines de aprovação e os fixtures de
conformidade viajem entre plataformas.

## Arquivos e nomenclatura (decisões multiplataforma, 2026-08-24)

| Arquivo | Papel |
|---|---|
| `build/narrativetrace/structural/<TestClass>/<scenario>.nt` | Emitido na trilha de markdown; o arquivo em disco é a **baseline do último verde** — uma execução não verde compara contra ele (delta no console, relatório de falha) mas nunca o sobrescreve. "Verde" é o veredito inteiro: um teste que passou mas cuja estrutura a aprovação *rejeitou* termina vermelho, então uma estrutura rejeitada nunca vira a baseline e reverter a mudança não reporta delta |
| `src/test/narratives/<TestClass>/<scenario>.approved.nt` | Baseline de aprovação commitada (`NarrativeApproval`; opt-in via `narrativetrace.approval=true`, diretório configurável via `narrativetrace.approvedDir`) — um teste que passa mas cuja estrutura difere falha com um diff legível |
| `<scenario>.received.nt` | Escrito ao lado da baseline quando a aprovação não coincide (ou quando ainda não existe uma baseline); revise-o e depois promova via a task `approveNarratives` do Gradle |
| `<scenario>.incomplete.nt` | O mesmo conteúdo, escrito no lugar de `.received.nt` quando a própria execução foi incompleta (o caminho de melhor esforço descartou eventos, ou recusou um escopo async no teto de adoção). `approveNarratives` o ignora pelo nome: uma execução curta nunca deve se tornar a baseline commitada, ou toda execução completa posterior seria lida como tendo *adicionado* chamadas. Uma execução assim é comparada por contenção de subsequência em vez de igualdade — ausências são toleradas e nomeadas, qualquer coisa adicionada ou reordenada ainda falha |

A extensão do formato vem por último (`.approved.nt`, convenção do
ApprovalTests) para que editores e visualizadores de diff se guiem por
`.nt`. Nota: `.nt` colide com RDF N-Triples em alguns mapas de realce
de sintaxe; registre uma substituição em `.gitattributes` onde isso
importar.

### Identidade de artefato (multiplataforma, 2026-09-09)

`<scenario>` acima é a **identidade de artefato** de uma invocação de
teste, e todos os runtimes a escrevem do mesmo jeito — um artefato
escrito por um runtime é encontrado sob o mesmo nome por outro:

- Um método de teste comum é o seu nome em slug: camel-case separado com
  `_`, em minúsculas, e tudo fora de `[a-z0-9_]` substituído por `_` —
  `customerPlacesOrder` → `customer_places_order`.
- Uma invocação de um método que roda mais de uma vez (parametrizado,
  repetido) acrescenta `-<índice>-<rótulo>`: o número da invocação em
  base 1, preenchido com zeros até três dígitos, e depois o nome exibido
  da invocação pela mesma regra de slug, com sequências de `_` colapsadas
  e as pontas aparadas — `equipment_can_be_found-002-find_tent`. Um
  rótulo cujo slug fica vazio é omitido, deixando
  `equipment_can_be_found-002`.
- `-` é o separador exatamente porque o alfabeto do slug não consegue
  produzi-lo. O índice — não o rótulo — é o que torna o esquema à prova
  de colisões: duas invocações sempre diferem nele, então nomes exibidos
  que só se distinguem por caracteres que um caminho não pode carregar
  (`find/TENT` versus `find TENT`) recebem arquivos distintos. O rótulo é
  o que torna o nome legível.
- O nome é estável entre execuções, máquinas e processos, que é o que
  permite commitar o `.approved.nt` de uma invocação. Quando um nome
  excede o limite de 255 bytes por elemento de caminho, a metade de
  *método* é truncada e recebe oito caracteres hexadecimais do
  `String.hashCode` do Java sobre o slug completo — é especificado,
  portanto idêntico em toda parte; um hash por processo invalidaria em
  silêncio cada baseline que tocasse.

Como nomes de artefato são derivados e não anunciados, uma execução
também escreve `<outputDir>/manifest.json`: uma linha por cenário
rastreado nomeando seu teste, seu número de invocação e cada arquivo que
lhe pertence. Leia isso quando você conhece o cenário e quer o arquivo.

> O cabeçalho `scenario:` de uma invocação **não** é o nome exibido dela *(since 0.2.2, unreleased)*.
> Um template `@ParameterizedTest(name = …)` interpola argumentos no nome
> exibido, então este artefato — o que não carrega valores — é titulado
> pelo método e pelo número da invocação: `Equipment can be found #2`. Um
> método que roda uma única vez mantém o nome exibido que sempre teve, de
> modo que nenhuma baseline commitada se move. O *nome de arquivo*
> continua carregando o rótulo em forma de slug, porque é isso que
> distingue duas invocações em disco, e o `manifest.json` — um índice que
> cobre também os artefatos com valores — nomeia o cenário como o runner
> o exibiu. Mantenha segredos fora dos templates de nome exibido.

## Conteúdo

```
scenario: Weekend trip settles with three transfers

- TripSettlementService.recordExpense(tripName, expense)
  - ExpenseValidator.ensureValid(expense)
  - TripLedger.recordExpense(tripName, expense)
- TripSettlementService.settleTrip(tripName) → value
  - TripLedger.expensesOf(tripName) → value
  ~ fork [2]
    - BalanceCalculator.computeBalances(expenses) → value
    - StockService.check() → value
```

- **Cabeçalho:** `scenario: <humanized test name>` + linha em branco. Nada
  mais — sem resultado, sem ids/nomes de trace, sem datas. Uma invocação
  de um método que roda mais de uma vez é `scenario: <nome humanizado do
  método> #<índice>`: os argumentos de um template de nome exibido nunca
  chegam até ele.
- **Linha de chamada:** `ClassName.methodName(paramName, paramName)` —
  apenas nomes, na ordem de captura, com dois espaços de indentação
  por nível de profundidade.
- **Tipos de resultado:** retorno não-void ` → value`; void: nada (o
  contrato de retorno nulo); lançamento ` !! ExceptionSimpleName` (o
  tipo é estrutura; a mensagem é um valor e nunca aparece); enter sem
  correspondência ` ?? incomplete`.
- **Concorrência:** grupos fork são renderizados como `~ fork [n]` e o
  trabalho adotado de uma instantânea de contexto propagada (`@Async`
  do Spring, Micrometer, qualquer `snapshot.activate()` manual) é
  renderizado como `~ async [n]`, ambos com membros **ordenados por
  `Class.method`** — a ordem de captura entre threads é uma escolha do
  agendador, não comportamento, então o artefato expressa o conjunto e
  o aninhamento do trabalho concorrente, mas nunca sua ordem. Os
  grupos async são indexados pelo span que os lança, de modo que cada
  filho async de uma chamada forma um único grupo, e eles também
  aparecem no nível raiz quando o trabalho sobrevive a quem o chamou.
  Fire-and-forget é renderizado como `~ fire-and-forget` + filhos.
  Nomes/ids de thread nunca aparecem.
- **Excluído por design:** todos os valores de argumento/retorno, as
  mensagens de exceção, as durações, os timestamps, a identidade de
  thread, os ids de trace/span, os nomes de trace, os resultados de
  execução e a narração (a narração resolvida incorpora valores; o
  *template* de narração se junta quando `nt.narrationTemplate` chegar
  com a Fase 6 do glossário).
- **Codificação:** UTF-8, LF, quebra de linha final. Os identificadores
  passam por saneamento de caracteres de controle.

## Identidade nos irmãos JSON

O artefato `.nt` não carrega identidade alguma — é isso que o torna
determinístico byte a byte. Seus irmãos JSON carregam: os arrays de
entradas `<test>.canonical.json` / `<test>.structural.json` por teste,
o envelope de capítulo (`chapter.schema.json`) e o documento de árvore
de capítulos (`chapter-tree.schema.json`, que o capítulo incorpora em
`nt.chapterTree`). Os três emissores resolvem a identidade da mesma
forma, então um capítulo nunca pode nomear um trace enquanto a árvore
que ele contém nomeia outro. Ali os três campos de identidade estão
**sempre presentes**, seja qual for a forma como a captura foi feita:

| Campo | Como é resolvido |
|---|---|
| `trace_id` | Adotado de um `traceparent` de entrada, senão herdado do trace sob o qual a captura rodou, senão gerado — sempre um id real, único, com forma W3C (32 hex minúsculos, nunca todo zeros). Nunca uma constante compartilhada, e nunca regerado por entrada. |
| `nt.storyId` | Herdado quando o trace já carrega um, senão derivado da primeira chamada de nível raiz como `Class.method`. Nunca gerado. |
| `nt.chapterId` | Herdado quando o trace já carrega um, senão igual a `nt.storyId` — o capítulo deste serviço para aquela história. Nunca gerado. |

`nt.traceName` é derivado de `trace_id` (a frase de três palavras),
então sempre concorda com ele, e `service` recorre a
`unknown_service:java` quando nada o forneceu. Uma árvore cujos nós
perderam seu contexto de span — uma montada à mão, reproduzida a
partir de um artefato ou produzida por uma varredura estática — ainda
é exportada como o único trace que é: a identidade pertence à árvore,
é resolvida uma única vez, e é compartilhada pelo capítulo, pela
árvore que ele incorpora, e por cada entrada daquele capítulo.

Por isso o bloco `trace` do documento de árvore de capítulos sempre
carrega `traceId` e `traceName`. Seus campos restantes — `serviceName`,
`environment`, `httpMethod`, `httpRoute` e os identificadores com
escopo de requisição — só são escritos quando a árvore de fato
carregava um contexto de span do qual herdá-los: uma identidade gerada
sabe *qual* trace é esse e nada sobre quem o chamou, e inventar um
nome de serviço seria pior do que omitir um. `chapter-tree.schema.json`
marca o bloco inteiro como opcional, o que a forma sempre-presente
satisfaz; o schema é um piso, não o contrato entre os emissores.

Duas execuções do mesmo comportamento, portanto, produzem arquivos
`.nt` idênticos e `trace_id`s *diferentes*. Essa é a divisão
pretendida: campos cujo trabalho é agrupar ou descrever são derivados
e estáveis, campos cujo trabalho é ser únicos são gerados (ADR-014).
Uma comparação de conformidade entre execuções ou entre implementações
normaliza os campos únicos antes de comparar.

## Garantias

1. **Determinístico:** comportamento idêntico ⇒ arquivo idêntico byte
   a byte. Isso é o que torna o artefato a baseline de testes de
   aprovação e o formato de referência (golden format) dos fixtures de
   conformidade.
2. **Livre de valores:** superfície de prompt-injection zero, zero
   PII, tokens mínimos — seguro para entregar por padrão a um agente
   de IA (a saída de Nível 1 do tier gratuito).
3. **Divisão de trabalho:** o artefato afirma a *forma* comportamental;
   a correção dos valores continua sendo trabalho das asserções de
   teste. Uma mudança que apenas altera um valor de retorno com
   estrutura idêntica não muda o artefato — por design.

Implementado neste repositório por `core: StructuralTraceRenderer`, emitido
por `TraceTestSupport` ao lado dos companheiros `.md`/`.json`/`.mmd`.
