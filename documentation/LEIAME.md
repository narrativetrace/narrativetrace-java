# Documentação do NarrativeTrace

[English](README.md) | [Español](LEAME.md) | **Português** | [简体中文](自述文件.md)

O índice dos guias de usuário do NarrativeTrace em português. A documentação
técnica e de design (superfície da API, testes de segurança e de
concorrência) permanece apenas em inglês — consulte o
[índice completo](README.md). A documentação publicada completa — incluindo
páginas sem arquivo correspondente aqui — está em
[narrativetrace.ai/docs](https://narrativetrace.ai/docs.html) (em inglês).

## Comece por aqui

| Documento | O que cobre |
|---|---|
| [Primeiros 10 Minutos](pt-BR/primeiros-10-minutos.md) | Um serviço minúsculo, um teste JUnit, oito passos até um trace real — cada comando e cada saída executados de verdade contra este repositório |
| [Experiência de Desenvolvimento](pt-BR/experiencia-de-desenvolvimento.md) | De um projeto vazio à primeira narrativa: configuração em uma linha, artefatos por teste, o lançador de demo, builds compostos locais, a ocultação como parte do fluxo de trabalho |
| [Guia de Instalação](pt-BR/guia-de-instalacao.md) | Dependências, os cinco caminhos de integração (proxy JDK, Spring, Micronaut, JUnit 5/4, agente Java), configuração da saída de traces, matriz de compatibilidade |
| [Escolhendo uma Integração](pt-BR/escolhendo-uma-integracao.md) | Qual módulo você realmente precisa: um diagrama de decisão mais as ressalvas de cada um dos cinco caminhos de integração |
| [Guia do Ciclo de Vida](pt-BR/guia-do-ciclo-de-vida.md) | Onde o NarrativeTrace se encaixa no seu processo: desenvolvimento, CI/aceitação, produção — e a postura de privacidade em cada etapa |
| [Guia de Configuração](pt-BR/guia-de-configuracao.md) | Cada superfície de configuração: propriedades do sistema, `junit-platform.properties`, Gradle, Spring, Micronaut, SLF4J; flags de captura, chaves MDC e valores padrão de ocultação |
| [Guia de Anotações](pt-BR/guia-de-anotacoes.md) | `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`, e o contrato de pureza que elas implicam |
| [Privacidade e Ocultação](pt-BR/privacidade-e-ocultacao.md) | O contrato de ocultação linha a linha verificado contra o código, a lista de garantias e não garantias, e o modelo de perda em produção |
| [O pipeline de eventos de caminho duplo](pt-BR/pipeline-de-caminho-duplo.md) | O contrato do pipeline padrão: o caminho síncrono de narração durável a quedas, o caminho com buffer que nunca bloqueia, a perda contada, e a ocultação na captura a montante de ambos |
| [O Que Commitar](pt-BR/o-que-commitar.md) | Quais arquivos gerados são artefatos de CI e quais são baselines revisadas que você deve commitar |
| [Solução de Problemas](pt-BR/solucao-de-problemas.md) | Sintoma → causa → correção para os modos de falha que as pessoas realmente encontram, de parâmetros `arg0` a um agente silencioso |

## Integração com frameworks

| Documento | O que cobre |
|---|---|
| [Guia de Integração com Spring](pt-BR/guia-de-integracao-com-spring.md) | Tracing de beans via `BeanPostProcessor`, o filtro de servlet, propagação de `@Async` com `ContextPropagatingTaskDecorator` |
| [Guia de Integração com Micronaut](pt-BR/guia-de-integracao-com-micronaut.md) | Tracing de beans, o filtro HTTP reativo, propriedades de configuração |
| [Guia do Plugin Gradle](pt-BR/guia-do-plugin-de-gradle.md) | Referência do DSL, os quality gates que o plugin registra, receitas |

## Análise e saída

| Documento | O que cobre |
|---|---|
| [Guia de Clareza](pt-BR/guia-de-clareza.md) | O modelo de pontuação de cinco dimensões, os componentes de NLP por trás dele, integração com JUnit e o gate `clarityCheck` |
| [Guia de Funcionalidades](pt-BR/guia-de-funcionalidades.md) | O catálogo canônico: cada funcionalidade, seu nível, sua implementação, seu status |
| [Formato de Trace Estrutural](pt-BR/formato-de-trace-estrutural.md) | O artefato `.nt` livre de valores por trás do relatório de deltas e dos testes de aprovação — a especificação do formato multiplataforma |

## Mantendo este índice honesto

Traduzir um novo documento em `documentation/pt-BR/` significa adicioná-lo a
este índice na mesma mudança. Um documento que não aparece aqui é invisível
para quem navega o repositório em português.
