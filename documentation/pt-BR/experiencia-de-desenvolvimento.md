<!-- source: documentation/developer-experience.md blob 42c7f40d6c46 | translated: 2026-09-23 | reviewed: - -->
# Experiência de desenvolvimento

[English](../developer-experience.md) | [Español](../es/experiencia-de-desarrollo.md) | **Português** | [简体中文](../zh-CN/开发者体验.md)

O que é preciso para ir de um projeto vazio até a leitura da sua primeira
narrativa, e onde vive cada parte dessa experiência. Esta página descreve o
que já está disponível hoje; as melhorias planejadas são acompanhadas em um
backlog privado.

## Configuração em uma linha

Aplique o plugin do Gradle e escreva um teste. O plugin adiciona os
artefatos do NarrativeTrace, configura a JUnit Platform e fornece o
mecanismo de testes Jupiter para o modo padrão JUnit 5 — a configuração mínima documentada executa um
teste real em verde sem nenhuma dependência adicional. Consulte o guia de
instalação para o trecho exato de acordo com o seu estilo de build. Para produzir
narrativas, registre `NarrativeTraceExtension` no teste e execute chamadas rastreadas,
por exemplo com `NarrativeTraceProxy.trace`. O plugin fornece as dependências e a
configuração de testes; ele não registra a extensão.

Para uma suíte JUnit 4 existente, mantenha esse framework e configure
`narrativeTrace { testFramework.set("junit4") }`. Vincule uma
`@ClassRule NarrativeTraceClassRule` pública e estática a uma `@Rule` pública
criada por `classRule.testRule()`, e rastreie usando o contexto da regra por teste.
O plugin fornece a integração JUnit 4, mas não adiciona essas regras aos testes.

## Narrativas por teste

Com a extensão registrada e a saída habilitada (o padrão), o módulo
`narrativetrace-junit5` escreve narrativas para testes que capturam rastros.
Os artefatos ficam ao lado da saída do build nos
formatos canônicos (texto de narrativa `.nt`, JSON estrutural e canônico,
documentos de capítulo) — os mesmos formatos que toda implementação do
NarrativeTrace emite, validados pelos esquemas incluídos neste repositório.

A `NarrativeTraceRule` do JUnit 4 fornece contexto e saída equivalentes por teste.
Ela lê propriedades de sistema da JVM de testes em vez de `junit-platform.properties`.

## Feedback sobre nomes e habilidades de agente

Em um projeto consumidor com o plugin do Gradle aplicado, `./gradlew clean clarityScan`
pontua as classes de produção compiladas sem executar testes. Leia os arquivos
recém-gerados `clarity-scan-report.md` e `clarity-scan-results.json` no diretório
de saída configurado (padrão: `build/narrativetrace`) para ver as pontuações e as
notas por elemento.

Para analisar as chamadas executadas, a extensão do JUnit registrada analisa rastros
de teste não vazios e escreve `clarity-report.md` e `clarity-results.json` ao final
da suíte. No JUnit 4, a regra de classe vinculada escreve os mesmos relatórios após
cada classe, combinando as classes concluídas nessa JVM de testes. Uma regra por
teste independente não produz relatórios agregados do Clarity. Não existe uma
opção separada para habilitar o Clarity;
`narrativetrace.output=false` desabilita esses relatórios e os demais artefatos de rastro.

A verificação opcional em CI usa `./gradlew clean clarityCheck`. Sua entrada padrão
é o JSON gerado pelos testes. `minScore` compara o `overallScore` de cada cenário;
os limites de contagem de problemas são separados. Se o JSON estiver ausente, a
verificação é ignorada sem falhar: confirme que existem relatórios recentes e não
vazios antes de confiar em um build verde. Os resultados da análise estática por
si só não demonstram que essa verificação passou.

O guia de [habilidades de agente](habilidades-de-agente.md) oferece três pontos de entrada:

- `add-narrative-tracing` instala o rastreamento e produz o primeiro rastro.
- `narrativetrace-doctor` diagnostica uma configuração existente sem alterá-la.
- `add-narrativetrace-clarity` produz e explica um primeiro relatório de nomes,
  verifica os artefatos e adiciona uma verificação de qualidade quando solicitado.

Consulte o [guia de clareza](guia-de-clareza.md) para as opções de pontuação e verificação.

## Experimentando sem um projeto

`./demo.sh` inicia as aplicações de exemplo (e-commerce e companhia) e narra
cenários reais no console — a forma mais rápida de ver como é a saída antes
de fazer qualquer wiring.

## Consumindo um build local

Avaliar mudanças ainda não publicadas, ou construir uma integração contra
este repositório, usa os builds compostos do Gradle — e precisa de
**ambos** os papéis de inclusão: um `pluginManagement { includeBuild(...) }`
para que o plugin seja resolvido, e um `includeBuild(...)` de nível superior
para que as coordenadas de biblioteca que o plugin adiciona sejam
substituídas pelo seu checkout local. A documentação do plugin traz a
receita completa do `settings.gradle.kts`.

## Segurança enquanto você desenvolve

A ocultação é parte da experiência de desenvolvimento, não um acréscimo
tardio: um componente `@NotTraced` nunca aparece em nenhuma saída
renderizada — nem através do `toString()` de um wrapper, nem através de uma
coleção, nem através do `toString()` próprio, escrito à mão, de uma classe
que a contenha, nem através de um template de narração que nomeie seu
caminho. Se
uma narrativa precisar de um valor, o ato deliberado e revisável é remover a
anotação, nunca contorná-la.
