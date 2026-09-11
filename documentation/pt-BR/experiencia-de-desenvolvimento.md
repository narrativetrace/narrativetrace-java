<!-- source: documentation/developer-experience.md blob 70a5b233295a | translated: 2026-09-11 | reviewed: - -->
# Experiência de desenvolvimento

[English](../developer-experience.md) | [Español](../es/experiencia-de-desarrollo.md) | **Português** | [简体中文](../zh-CN/开发者体验.md)

O que é preciso para ir de um projeto vazio até a leitura da sua primeira
narrativa, e onde vive cada parte dessa experiência. Esta página descreve o
que já está disponível hoje; as melhorias planejadas são acompanhadas em um
backlog privado.

## Configuração em uma linha

Aplique o plugin do Gradle e escreva um teste. O plugin adiciona os
artefatos do NarrativeTrace, configura a JUnit Platform e fornece o
mecanismo de testes Jupiter — a configuração mínima documentada executa um
teste real em verde sem nenhuma dependência adicional. Consulte o guia de
instalação para o trecho exato de acordo com o seu estilo de build.

## Narrativas por teste

O módulo `narrativetrace-junit5` escreve uma narrativa para cada teste
conforme ele é executado. Os artefatos ficam ao lado da saída do build nos
formatos canônicos (texto de narrativa `.nt`, JSON estrutural e canônico,
documentos de capítulo) — os mesmos formatos que toda implementação do
NarrativeTrace emite, validados pelos esquemas incluídos neste repositório.

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
