<!-- source: documentation/agent-skills.md blob 88c83f42536c | translated: 2026-09-14 | reviewed: - -->
# Habilidades de agente

*(since 0.2.3)*

O NarrativeTrace traz **habilidades** (*skills*): procedimentos carregáveis por um agente que
executam comandos testados e condicionam a conclusão a uma etapa `verify`, em vez de uma
documentação que um agente pode ou não ler. Uma habilidade é propositalmente enxuta — a lógica de
verificação, diagnóstico ou geração vive em código de biblioteca testado; o trabalho da própria
habilidade é saber quando agir, invocar esse código testado e interpretar o resultado no contexto.

## Duas habilidades: configuração e diagnóstico

- **`add-narrative-tracing`** — instala o NarrativeTrace em um projeto e o leva ao primeiro trace:
  instala com o toolchain real, envolve uma classe com `NarrativeTraceProxy.trace`, renderiza e
  executa o primeiro trace, e então conecta um logger real (`narrativetrace-slf4j` mais Logback).
  Termina executando `narrativetrace-doctor` e passando o bastão — a costura entre as duas
  habilidades.
- **`narrativetrace-doctor`** — apenas diagnóstico, e **somente leitura**: nunca edita, gera ou
  apaga um arquivo. Executa a CLI testada, lê seu relatório, e percorre as partes que a saída
  simples de uma CLI não consegue cobrir sozinha: provar a ocultação em um teste, ler um trace
  renderizado antes de fazer asserções contra ele, e o fluxo de traces de aprovação (marcado como
  ainda não estudado — sua própria célula de avaliação continua pendente).

Elas se combinam: um projeto totalmente novo começa com `add-narrative-tracing`; um projeto que já
tem o NarrativeTrace instalado, no qual algo não está funcionando, começa com
`narrativetrace-doctor`. Os dois caminhos terminam no doctor — a partir daí, o diagnóstico é dele.
Uma habilidade futura vai cuidar da geração (escrever o teste que prova a ocultação, que hoje o
doctor só consegue pedir para você adicionar).

## Instalando-as

- **Claude Code**: os arquivos `SKILL.md` renderizados ficam em
  [`.claude/skills/narrativetrace-doctor/`](../../.claude/skills/narrativetrace-doctor/SKILL.md) e
  [`.claude/skills/add-narrative-tracing/`](../../.claude/skills/add-narrative-tracing/SKILL.md)
  neste repositório. Copie qualquer um dos dois diretórios para o `.claude/skills/<nome>/` do seu
  próprio projeto e o Claude a reconhece sozinho, invocável pelo nome (`narrativetrace-doctor` /
  `add-narrative-tracing`) diretamente.
- **Codex**: o mesmo catálogo também renderiza
  [`.agents/skills/narrativetrace-doctor/`](../../.agents/skills/narrativetrace-doctor/SKILL.md) e
  [`.agents/skills/add-narrative-tracing/`](../../.agents/skills/add-narrative-tracing/SKILL.md) —
  a disposição que o Codex CLI documenta para as habilidades próprias de um projeto. Seu
  frontmatter carrega apenas `name` e `description` (o Codex não documenta chaves `when_to_use`
  nem `allowed-tools`); o corpo da página é idêntico. Copie qualquer um dos dois diretórios para o
  `.agents/skills/<nome>/` do seu próprio projeto e o Codex a reconhece da mesma forma.
- **Qualquer agente, qualquer plataforma**: todo agente que lê `AGENTS.md` vê o aviso sempre ativo
  que o próprio `AGENTS.md` deste repositório carrega entre seus marcadores
  `<!-- narrativetrace:skills:start -->` — o nome e a descrição das duas habilidades, para que um
  agente que nunca pensou em procurar ainda assim saiba que elas existem.
- **Gemini** roda contra o mesmo catálogo em uma cadência esporádica e limitada por cota (veja
  [Tier B — testes com LLM](../../narrativetrace-skills/evals/README.md)) em vez de a cada commit;
  ainda não tem uma disposição própria renderizada — copiar os arquivos do Claude ou do Codex
  acima é, por ora, o caminho mais próximo.

## Como são construídas

Nenhuma das duas habilidades é editada manualmente.
`narrativetrace-skills/src/main/java/ai/narrativetrace/skills/catalogue/AddNarrativeTracingSkill.java`
e `.../NarrativeTraceDoctorSkill.java` são as duas fontes da verdade; seus passos tipados
renderizam `.claude/skills/add-narrative-tracing/SKILL.md`,
`.claude/skills/narrativetrace-doctor/SKILL.md`, seus espelhos do Codex em
`.agents/skills/add-narrative-tracing/SKILL.md` e
`.agents/skills/narrativetrace-doctor/SKILL.md`, e a própria seção de `AGENTS.md` deste
repositório — um teste de deriva (`RenderDriftTest`, ligado ao `./gradlew check`) falha a build no
momento em que qualquer uma das cinco se desvia da fonte tipada. O `name:` renderizado de uma
habilidade é sempre seu nome canônico, nunca um "segmento claude" abreviado — um diretório
`.claude/skills/` ou `.agents/skills/` mantido no próprio repositório é um espaço de nomes plano,
sem prefixo de plugin atrás do qual se esconder, então o nome precisa se autoidentificar
globalmente por conta própria. Uma segunda suíte (`SkillReplayer`, Tier A2) reproduz mecanicamente
cada comando e cada `verify` verificável por máquina que um passo nomeia contra o fixture
`sixty-seconds`, hoje mesmo, de forma determinística, sem LLM — estar verde significa que as
instruções são literalmente executáveis agora, não apenas prosa plausível. Um lint de Nível A
mantém fora das duas páginas citações a notas de planejamento privadas, ao irmão Pro deste
repositório, a nomes de arquivos de CI e a hashes do git: as frases de justificativa são
publicadas, a citação que nomeia a fonte não.

## Veja também

- [`narrativetrace-cli`](../../narrativetrace-cli/) — o verbo `doctor` que o `narrativetrace-doctor` executa
- [Veja um trace em 60 segundos](sessenta-segundos.md) — o passo a passo de instalação e primeiro trace de onde vêm os passos do `add-narrative-tracing`
- [O que commitar](o-que-commitar.md) — o estado de traces de aprovação que a quarta etapa do doctor verifica
- [Tier B — testes com LLM](../../narrativetrace-skills/evals/README.md) — a disposição de casos neutra em relação ao motor, a política esporádica de Codex/Gemini, e a matriz de promoção
