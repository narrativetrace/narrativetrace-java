<!-- source: documentation/what-to-commit.md blob b4f6e9e1ffe1 | translated: 2026-09-12 | reviewed: - -->
# O que commitar

[English](../what-to-commit.md) | Español | **Português** | [简体中文](../zh-CN/应提交的内容.md)

O NarrativeTrace escreve dois tipos de arquivo: artefatos gerados que
descrevem uma execução, e baselines revisadas que descrevem um contrato
pretendido. Faça commit do segundo tipo, não do primeiro.

Todo artefato de tempo de teste é gravado por padrão no diretório efêmero
`build/narrativetrace` *(since 0.2.2, unreleased)* — sem nenhuma configuração necessária,
`narrativetrace.output=false` desativa isso (veja o
[Guia de Configuração](guia-de-configuracao.md)). Efêmero é o ponto: ele vive
sob `build/`, então nunca precisa da disciplina de que trata esta página —
já está excluído, é regenerado a cada execução, e é seguro apagar a
qualquer momento.

| Artefato | Commit? | Por quê |
|---|---|---|
| `build/narrativetrace/traces/*.md` | Não | Regenerado a cada execução; geralmente um artefato de CI, não código-fonte |
| `build/narrativetrace/traces/*.json` | Não | O mesmo trace como JSON canônico — regenerado a cada execução |
| `build/narrativetrace/diagrams/*.mmd` | Não | Regenerado a cada execução |
| `build/narrativetrace/structural/*.nt` | Não | A baseline *local* do último verde contra a qual o delta do console e os relatórios de falha comparam — não é a baseline de aprovação (veja abaixo) |
| `build/narrativetrace/clarity-report.md` | Não | Um relatório gerado, não uma decisão — o `clarityCheck` lê o `clarity-results.json` ao lado dele, também gerado |
| `src/test/narratives/<Class>/<scenario>.approved.nt` | **Sim** | A baseline de aprovação revisada (só existe se o [modo de aprovação](formato-de-trace-estrutural.md) estiver ativo). Este é o único arquivo da lista que é uma decisão deliberada, não uma saída |
| `src/test/narratives/<Class>/<scenario>.received.nt` | Não | Escrito quando há divergência na aprovação, ou quando ainda não existe baseline. Revise-o, rode `./gradlew approveNarratives` para promovê-lo, depois apague-o ou deixe a task removê-lo — nunca faça commit do arquivo received em si |
| `src/test/narratives/<Class>/<scenario>.incomplete.nt` | Não | Escrito no lugar de `.received.nt` quando a própria execução foi incompleta (perda de melhor esforço, ou um escopo assíncrono recusado). O `approveNarratives` o ignora pelo nome de propósito — veja [Formato de trace estrutural](formato-de-trace-estrutural.md) |
| `glossary.json` / `glossary.md` | **Sim**, se a coleta do glossário for usada | Commitado na raiz do repositório por `glossaryScan` / `glossary.set(true)`; o arquivo commitado é o que a pontuação de clareza e as verificações de vocabulário leem de volta. "Um arquivo, um workflow de revisão" |

Tudo dentro de `build/` já está coberto pelo `.gitignore` distribuído
(`build/` é a primeira linha). `src/test/narratives/` não está — os
arquivos `.approved.nt` ali são feitos para serem rastreados, mas um
`.received.nt` ao lado de um deles não é excluído automaticamente. Se
seu time não for disciplinado em apagar um `.received.nt` já revisado
antes de commitar, adicione uma regra de exclusão explícita para ele:

```gitignore
src/test/narratives/**/*.received.nt
src/test/narratives/**/*.incomplete.nt
```

## A regra em uma frase

Se um arquivo só existe porque um teste rodou, ele é saída — não faça
commit dele. Se um arquivo existe porque um humano revisou e aceitou,
ele é uma baseline — faça commit dele, e espere que seus diffs sejam
lidos na revisão de código da mesma forma que o diff de um snapshot test
seria.

## Baselines de aprovação são livres de valores por construção

Um arquivo `.approved.nt` nunca contém valores de parâmetros ou de
retorno (ADR-002) — apenas estrutura de chamadas, nomes e tipos de
resultado. É isso que o torna seguro para commitar e estável entre
execuções: um valor de retorno mudando sem uma mudança estrutural nunca
toca a baseline, e revisar um diff nunca significa ler dados de runtime
em um pull request. Veja [Privacidade e
ocultação](privacidade-e-ocultacao.md) para o resto do que o
NarrativeTrace coloca — e não coloca — em um arquivo que seu time vai
compartilhar.

## Modo de aprovação, de ponta a ponta

```text
teste passa
   |
   v
compara a estrutura atual com a baseline aprovada
   |
   +-- igual     --> passa, nada é escrito
   +-- diferente --> escreve .received.nt e falha
                     |
                     v
                humano revisa o diff
                     |
                     v
              ./gradlew approveNarratives
                     |
                     v
              .approved.nt atualizado, faça commit dele
```

O estado de falha é deliberado: um teste que passa mas cuja *forma*
mudou — incluindo uma mudança que um agente de IA deslizou para dentro
de um refactor que, fora isso, estava correto — precisa ser observado e
explicitamente aprovado, não apenas compilar. Nada é aceito
silenciosamente, e nada é perdido silenciosamente: uma execução
incompleta escreve `.incomplete.nt` no lugar e é comparada por contenção
de subsequência em vez de igualdade, então uma execução curta nunca pode
se tornar a baseline commitada.
