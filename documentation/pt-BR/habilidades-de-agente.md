<!-- source: documentation/agent-skills.md blob dd3b3ab7e75b | translated: 2026-09-13 | reviewed: - -->
# Habilidades de agente

*(since 0.2.2, unreleased)*

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
  [`.claude/skills/doctor/`](../../.claude/skills/doctor/SKILL.md) e
  [`.claude/skills/add-narrative-tracing/`](../../.claude/skills/add-narrative-tracing/SKILL.md)
  neste repositório. Copie qualquer um dos dois diretórios para o `.claude/skills/<nome>/` do seu
  próprio projeto e o Claude a reconhece sozinho, invocável pelo nome (`narrativetrace-doctor` /
  `add-narrative-tracing`) diretamente.
- **Qualquer agente, qualquer plataforma**: todo agente que lê `AGENTS.md` vê o aviso sempre ativo
  que o próprio `AGENTS.md` deste repositório carrega entre seus marcadores
  `<!-- narrativetrace:skills:start -->` — o nome e a descrição das duas habilidades, para que um
  agente que nunca pensou em procurar ainda assim saiba que elas existem.
- **Codex e Gemini** rodam contra o mesmo catálogo em uma cadência esporádica e limitada por cota
  (veja [Tier B — testes com LLM](../../narrativetrace-skills/evals/README.md)) em vez de a cada
  commit; um instalador empacotado para qualquer uma das duas plataformas está no roteiro, mas
  ainda não foi construído — hoje, copiar os arquivos renderizados é o caminho.

## Como são construídas

Nenhuma das duas habilidades é editada manualmente.
`narrativetrace-skills/src/main/java/ai/narrativetrace/skills/catalogue/AddNarrativeTracingSkill.java`
e `.../NarrativeTraceDoctorSkill.java` são as duas fontes da verdade; seus passos tipados
renderizam `.claude/skills/add-narrative-tracing/SKILL.md`, `.claude/skills/doctor/SKILL.md`, e a
própria seção de `AGENTS.md` deste repositório — um teste de deriva (`RenderDriftTest`, ligado ao
`./gradlew check`) falha a build no momento em que qualquer uma das três se desvia da fonte tipada.
Uma segunda suíte (`SkillReplayer`, Tier A2) reproduz mecanicamente cada comando e cada `verify`
verificável por máquina que um passo nomeia contra o fixture `sixty-seconds`, hoje mesmo, de forma
determinística, sem LLM — estar verde significa que as instruções são literalmente executáveis
agora, não apenas prosa plausível. Um lint de Nível A mantém fora das duas páginas citações a notas
de planejamento privadas, ao irmão Pro deste repositório, a nomes de arquivos de CI e a hashes do
git: as frases de justificativa são publicadas, a citação que nomeia a fonte não.

## Veja também

- [`narrativetrace-cli`](../../narrativetrace-cli/) — o verbo `doctor` que o `narrativetrace-doctor` executa
- [Veja um trace em 60 segundos](sessenta-segundos.md) — o passo a passo de instalação e primeiro trace de onde vêm os passos do `add-narrative-tracing`
- [O que commitar](o-que-commitar.md) — o estado de traces de aprovação que a quarta etapa do doctor verifica
- [Tier B — testes com LLM](../../narrativetrace-skills/evals/README.md) — a disposição de casos neutra em relação ao motor, a política esporádica de Codex/Gemini, e a matriz de promoção
