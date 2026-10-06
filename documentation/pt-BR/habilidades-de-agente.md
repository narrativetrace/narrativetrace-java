<!-- source: documentation/agent-skills.md blob 93cebb3e6215 | translated: 2026-10-04 | reviewed: - -->
# Habilidades de agente

O NarrativeTrace traz **habilidades** (*skills*): procedimentos carregáveis por um agente que
executam comandos testados e condicionam a conclusão a uma etapa `verify`, em vez de uma
documentação que um agente pode ou não ler. Uma habilidade é propositalmente enxuta — a lógica de
verificação, diagnóstico ou geração vive em código de biblioteca testado; o trabalho da própria
habilidade é saber quando agir, invocar esse código testado e interpretar o resultado no contexto.

## Quatro habilidades: configuração, diagnóstico, clareza e relatos

- **`add-narrative-tracing`** — instala o NarrativeTrace em um projeto e o leva ao primeiro trace:
  instala com o toolchain real, envolve uma classe com `NarrativeTraceProxy.trace`, renderiza e
  executa o primeiro trace, e então conecta um logger real (`narrativetrace-slf4j` mais Logback).
  Executa `narrativetrace-doctor` e passa o bastão — a costura entre as duas habilidades — e fecha
  pré-visualizando `narrativetraceInit`, para que a próxima sessão encontre estas habilidades já
  instaladas. Ela pré-visualiza e nunca aplica: uma habilidade que escrevesse em `AGENTS.md` por
  iniciativa própria seria exatamente o hook de pós-instalação que o instalador existe para evitar.
- **`narrativetrace-doctor`** — apenas diagnóstico, e **somente leitura**: nunca edita, gera ou
  apaga um arquivo. Executa a CLI testada, lê seu relatório, e percorre as partes que a saída
  simples de uma CLI não consegue cobrir sozinha: provar a ocultação em um teste, ler um trace
  renderizado antes de fazer asserções contra ele, e o fluxo de traces de aprovação (marcado como
  ainda não estudado — sua própria célula de avaliação continua pendente). Cada achado que ele lê
  também nomeia a habilidade que corrige aquela classe de problema (`skill` no relatório JSON,
  `skill:` abaixo da correção na forma em texto), de modo que um agente com um relatório em mãos
  sabe qual destes procedimentos seguir em seguida; um achado que nenhuma habilidade corrige diz
  isso com um `null`.
- **`add-narrativetrace-clarity`** — executa uma primeira varredura estática de nomes, verifica
  os artefatos recém-gerados, explica problemas ordenados por impacto e notas por elemento,
  e adiciona um quality gate explícito somente quando solicitado.
- **`narrativetrace-feedback`** — relata um defeito no próprio NarrativeTrace: uma verificação do
  doctor que está errada ou cuja correção não funciona, um passo de habilidade que não pode ser
  seguido, uma redação do prompt de instalação que levou ao lugar errado, ou a biblioteca se
  comportando mal em um projeto corretamente configurado. O verbo testado por trás dele redige o
  relatório a partir do projeto (as coordenadas de instalação, o próprio relatório JSON do doctor
  e, no máximo, um trace estrutural) e **se recusa a escrever um relatório que carregue um valor
  dos seus traces**, nomeando a regra que o recusou, para que haja algo concreto a corrigir em vez
  de um aviso a ignorar. A habilidade então mostra o rascunho inteiro e pergunta uma única vez se
  ele deve ser publicado. Não envia nada a lugar algum e não publica nada sem uma resposta dada em
  um turno próprio.

Elas se combinam: um projeto totalmente novo começa com `add-narrative-tracing`; um projeto que já
tem o NarrativeTrace instalado, no qual algo não está funcionando, começa com
`narrativetrace-doctor`. Os dois caminhos terminam no doctor — a partir daí, o diagnóstico é dele.
`add-narrativetrace-clarity` cuida dos primeiros relatórios estáticos de nomes e da aplicação
opcional de limiares de clareza; não instala tracing nem colhe um glossário.
`narrativetrace-feedback` é onde um caminho termina quando o problema acaba sendo nosso e não do
projeto — a própria regra de encerramento do doctor aponta para ele.

## Instalando-as

Um único comando, no projeto onde você as quer:

```bash
./gradlew narrativetraceInit --diff
```

Isso PRÉ-VISUALIZA a instalação e não escreve nada. Ele informa o plano — com `--json`, o mesmo plano
como um envelope sobre o qual um script pode decidir:

```json
{
  "carrier": "ai.narrativetrace:narrativetrace-cli:0.2.5",
  "actions": [
    {"kind": "create", "path": ".agents/skills/narrativetrace-doctor/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/add-narrative-tracing/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/add-narrativetrace-clarity/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/narrativetrace-feedback/SKILL.md", "status": "planned"},
    {"kind": "create", "path": "AGENTS.md", "status": "planned"}
  ],
  "exitCode": 0
}
```

Leia o plano e então execute o mesmo comando sem a flag para aplicá-lo. Nada é escrito até você
fazer isso; não há hook de pós-instalação nem passo de build que instale habilidades por trás de
você. O que ele escreve:

- `.agents/skills/<nome>/SKILL.md` para cada habilidade do catálogo — sempre, com qualquer agente.
- `.claude/skills/<nome>/SKILL.md` também, quando o projeto tem um diretório `.claude/` ou um
  `CLAUDE.md` (ou com `--vendor claude`; `--vendor none` desliga isso).
- uma seção marcada em `AGENTS.md`, criada se o arquivo não existir, substituída entre seus próprios
  marcadores se uma execução anterior escreveu uma. Um `AGENTS.md` que já existe sem os nossos
  marcadores exige `--write-existing`, para que uma primeira execução nunca acrescente texto a um
  arquivo que você não esperava que fosse tocado.
- uma única linha de importação `@AGENTS.md` em um `CLAUDE.md` existente, e nunca um `CLAUDE.md`
  próprio.

Reexecutar é seguro: uma ação que escreveria o que já está lá é descartada, então um projeto em dia
não planeja nada. `./gradlew narrativetraceUninstall` remove exatamente o que foi instalado — uma
página só quando ela carrega o selo do próprio instalador, um arquivo só quando o instalador o criou
e nada seu sobrou dentro dele. A referência completa de tarefas e flags está no
[Guia do plugin de Gradle](guia-do-plugin-de-gradle.md#narrativetraceinit).

Sem o plugin de Gradle, o mesmo instalador é um verbo do launcher:
`narrativetrace init --dry-run` pré-visualiza e `narrativetrace init` aplica (a CLI mantém
`--dry-run`, onde nada o encobre; a tarefa de Gradle não pode, porque o próprio `--dry-run` do
Gradle pula todas as tarefas do grafo). Executado através do plugin, o plano acima nomeia
`ai.narrativetrace:narrativetrace-skills` como seu portador em vez do launcher — as mesmas páginas
saindo do arquivo que o projeto já baixou.

### À mão, o caminho alternativo

Copiar também funciona, e é o único caminho para um agente sem build próprio:

- **Claude Code**: os arquivos `SKILL.md` renderizados ficam em
  [`.claude/skills/narrativetrace-doctor/`](../../.claude/skills/narrativetrace-doctor/SKILL.md) e
  [`.claude/skills/add-narrative-tracing/`](../../.claude/skills/add-narrative-tracing/SKILL.md) e
  [`.claude/skills/add-narrativetrace-clarity/`](../../.claude/skills/add-narrativetrace-clarity/SKILL.md)
  neste repositório. Copie o diretório adequado para `.claude/skills/<nome>/` no seu projeto;
  o Claude reconhece a habilidade automaticamente e permite invocá-la pelo nome.
- **Codex**: o mesmo catálogo também renderiza
  [`.agents/skills/narrativetrace-doctor/`](../../.agents/skills/narrativetrace-doctor/SKILL.md) e
  [`.agents/skills/add-narrative-tracing/`](../../.agents/skills/add-narrative-tracing/SKILL.md) e
  [`.agents/skills/add-narrativetrace-clarity/`](../../.agents/skills/add-narrativetrace-clarity/SKILL.md) —
  a disposição que o Codex CLI documenta para as habilidades próprias de um projeto. Seu
  frontmatter carrega apenas `name` e `description` (o Codex não documenta chaves `when_to_use`
  nem `allowed-tools`); o corpo da página é idêntico. Copie o diretório adequado para o
  `.agents/skills/<nome>/` do seu próprio projeto e o Codex a reconhece da mesma forma.
- **Qualquer agente, qualquer plataforma**: todo agente que lê `AGENTS.md` vê o aviso sempre ativo
  que o próprio `AGENTS.md` deste repositório carrega entre seus marcadores
  `<!-- narrativetrace:skills:start -->` — o nome e a descrição das quatro habilidades, para que um
  agente que nunca pensou em procurar ainda assim saiba que elas existem.
- **Gemini** roda contra o mesmo catálogo em uma cadência esporádica e limitada por cota (veja
  [Tier B — testes com LLM](../../narrativetrace-skills-catalogue/evals/README.md)) em vez de a cada commit;
  ainda não tem uma disposição própria renderizada — copiar os arquivos do Claude ou do Codex
  acima é, por ora, o caminho mais próximo.

### O doctor informa sobre isso

Uma de suas verificações, `config.skills-installed`, lê cada
`SKILL.md` instalado e o carimbo que o instalador deixou nele, e falha quando as habilidades não
estão presentes, quando alguma delas falta, ou quando o carimbo nomeia uma versão diferente da que
o projeto resolve — de modo que uma cópia desatualizada aparece como um achado em vez de como um
agente seguindo em silêncio as instruções da versão anterior. Um diretório situado no caminho de
uma habilidade sem esse carimbo é reportado como de outra pessoa e nunca contado como instalado.
Quando o portador não pode ser resolvido de forma alguma — uma build sem rede, ou uma sem
repositório que o forneça — a verificação diz que não consegue saber e passa: estar sem rede não é
um defeito.

## A partir de um registro

Um projeto pode carregar estas habilidades sem que ninguém aqui jamais execute o instalador, em um
de três estados:

1. **Instaladas pelo `init`** — commitadas, as do time. O único estado que
   `config.skills-installed` aprova: as páginas carregam a linha de procedência e correspondem à
   versão que este projeto resolve.
2. **Uma instalação pessoal a partir de um registro** (um cache de plugins do Claude Code) — só
   sua. Invisível para o doctor por design: ele diagnostica o projeto, e uma instalação pessoal
   não alcança nenhum colega de time nem nenhum outro agente.
3. **Uma instalação de registro dentro do projeto** (`npx skills add`, `gemini skills install
   --scope workspace`) — as próprias páginas renderizadas deste repositório, deixadas por um
   registro em vez de pelo `init`, então ainda não carregam linha de procedência.

Para experimentá-las você mesmo, sem tocar no projeto:

```text
/plugin marketplace add narrativetrace/narrativetrace-java
/plugin install narrativetrace-java@narrativetrace-java
```

depois rode `./gradlew narrativetraceInit --diff`, leia o diff, e rode sem a flag para que o
`AGENTS.md` aponte para elas.

Para instalá-las no projeto a partir do registro de padrão aberto:

```text
npx skills add narrativetrace/narrativetrace-java
```

depois rode `./gradlew narrativetraceInit --diff`, leia o diff, e rode sem a flag para que o
`AGENTS.md` aponte para elas.

Uma página deixada por um registro nunca é recusada só por estar ali. O `init` a compara, byte a
byte exceto a linha de procedência, com o que ele mesmo teria renderizado. Uma idêntica à própria
página desta versão fica **adotada** — o plano diz isso, em vez de "substituída", porque quem lê
precisa saber que nada de ninguém foi sobrescrito. Este é o próprio texto do plano, citado, nunca
redigitado aqui:

```java
  private static final String ADOPTED =
      "adopted: identical to this carrier's page, so only the provenance line is added";
```

Uma página que diverge — outra versão, ou editada à mão — mantém a recusa comum para a qual o
`--force` existe. O `npx skills add` também deixa `.claude/skills/<nome>` como um link simbólico
para a página de padrão aberto; o `init` nunca escreve através de um link assim. Um link cujo
destino ele adotaria ou já é dele é substituído por um diretório real com o sabor certo; qualquer
outro link é recusado, porque `--force` cobre conteúdo, nunca um link.

E esta é a própria correção do doctor, citada do mesmo jeito, para um projeto onde as páginas
estão presentes mas não carregam nada disso:

```java
  /** Both spellings of the same command: Gradle owns {@code --dry-run}, so the task says diff. */
  private static final String INIT_COMMANDS =
      "./gradlew narrativetraceInit --diff (or narrativetrace init --dry-run)";

  /** What a skill directory that is present but not ours is called in a message. */
  private static final String NOT_OURS = " (there, but not ours)";

  /**
   * What a page with no provenance line most often IS: a registry install (D5 state 3) — `npx
   * skills add`, or a workspace skills install — of this repository's own rendered pages. Naming
   * the case matters because the obvious reading of "not ours" is "somebody else's work", which
   * invites a `--force` nobody needs: `init` ADOPTS a page identical to this release's.
   */
  private static final String FROM_A_REGISTRY =
      " Pages that are there without our line usually came from a registry (npx skills add, a"
          + " plugin or workspace install). Run "
          + INIT_COMMANDS
          + ", read the diff, then run it without the flag — a page identical to this release's is"
          + " adopted, and no --force is needed.";
```

## Como são construídas

Nenhuma habilidade é editada manualmente.
`narrativetrace-skills-catalogue/src/main/java/ai/narrativetrace/skills/catalogue/` contém as quatro fontes
da verdade; seus passos tipados geram quatro páginas do Claude, quatro do Codex, a cópia que o portador
guarda de ambos os sabores mais o seu índice `catalogue.json` — o que o jar publicado entrega ao
`narrativetraceInit` — a seção de `AGENTS.md` deste repositório, e o listing
`.claude-plugin/marketplace.json` que torna este repositório um marketplace de plugins do Claude
Code: um teste de deriva (`RenderDriftTest`, ligado ao `./gradlew check`) faz o build falhar quando
qualquer uma dessas dezenove saídas diverge da fonte tipada, e um segundo o faz falhar se qualquer
outra coisa aparecer dentro do portador. O `name:` renderizado de uma
habilidade é sempre seu nome canônico, nunca um "segmento claude" abreviado — um diretório
`.claude/skills/` ou `.agents/skills/` mantido no próprio repositório é um espaço de nomes plano,
sem prefixo de plugin atrás do qual se esconder, então o nome precisa se autoidentificar
globalmente por conta própria. Uma segunda suíte (`SkillReplayer`, Tier A2) reproduz mecanicamente
cada comando e cada `verify` verificável por máquina que um passo nomeia contra o fixture real
correspondente, incluindo o consumidor independente de Clarity, hoje mesmo, de forma
determinística, sem LLM — estar verde significa que as
instruções são literalmente executáveis agora, não apenas prosa plausível. Um lint de Nível A
mantém fora das páginas renderizadas citações a notas de planejamento privadas, ao irmão Pro deste
repositório, a nomes de arquivos de CI e a hashes do git: as frases de justificativa são
publicadas, a citação que nomeia a fonte não. Um segundo lint guarda o único campo do frontmatter
cuja ausência é uma funcionalidade: uma habilidade cujos passos podem tornar algo público — hoje,
`narrativetrace-feedback` — não deve declarar nenhum `allowed-tools`, porque esse campo
pré-aprova as ferramentas que lista durante o turno que carrega a habilidade, e uma habilidade de
relato que pré-aprovasse o seu próprio comando de relato deixaria o ambiente de perguntar
justamente onde perguntar é o ponto.

## Veja também

- [`narrativetrace-cli`](../../narrativetrace-cli/) — o verbo `doctor` que o `narrativetrace-doctor` executa
- [Privacidade e ocultação](privacidade-e-ocultacao.md) — a deny-list e as formas de valor com as quais as regras sem-valores do relato de problema são medidas
- [Veja um trace em 60 segundos](sessenta-segundos.md) — o passo a passo de instalação e primeiro trace de onde vêm os passos do `add-narrative-tracing`
- [O que commitar](o-que-commitar.md) — o estado de traces de aprovação que a quarta etapa do doctor verifica
- [Tier B — testes com LLM](../../narrativetrace-skills-catalogue/evals/README.md) — a disposição de casos neutra em relação ao motor, a política esporádica de Codex/Gemini, e a matriz de promoção
