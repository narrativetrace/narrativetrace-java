<!-- source: documentation/dual-path-pipeline.md blob b676bcf539d7 | translated: 2026-09-10 | reviewed: - -->
# O pipeline de eventos de caminho duplo

[English](../dual-path-pipeline.md) | [Español](../es/canalizacion-de-doble-ruta.md) | **Português** | [简体中文](../zh-CN/双路径事件管道.md)

Cada chamada rastreada publica seus eventos exatamente uma vez, através
de um único pipeline. O pipeline padrão — `DualPathPipeline`, em todas
as distribuições — entrega cada evento duas vezes, por dois caminhos
com garantias deliberadamente opostas. Esta página é o contrato de
ambos: o que cada caminho promete, o que cada um custa, e o que
acontece sob carga e depois de uma queda do processo.

## A forma

```
 método rastreado (thread chamadora)
      │
      ▼
 captura: valores são renderizados e OCULTADOS aqui, uma vez   ← antes que qualquer outra coisa os veja
      │
      ▼
 publicação (um TraceEvent)
      ├──────────────► caminho síncrono: narração SLF4J, inline,
      │                a escrita termina antes de o método retornar
      └──────────────► caminho com buffer: anel limitado → armazém de eventos,
                       árvore de trace, exportadores, ouvintes SPI
```

Um evento, duas entregas. Cada caminho é isolado do outro e de você:
uma exceção lançada por qualquer dos consumidores é contida e nunca se
torna uma falha da aplicação.

## O caminho síncrono — narração que sobrevive a uma queda

O ouvinte SLF4J executa inline, na thread que faz a chamada. A escrita
no log termina antes de o método rastreado retornar ao seu chamador,
então a narração é exatamente tão durável — e custa exatamente o mesmo
— quanto uma chamada de log escrita à mão. Se o processo morre na
instrução seguinte, tudo o que foi narrado até ali já está no seu
fluxo de logs.

Os níveis por tipo de evento são configuráveis; os padrões são
`ENTRY`/`RETURN` em TRACE e `EXCEPTION` em WARN, no logger
`narrativetrace`. Formatação, appenders e qualquer bloqueio pertencem
ao seu backend de logging — o ouvinte em si não mantém estado mutável
compartilhado além do contexto por thread.

## O caminho com buffer — captura que nunca bloqueia

O caminho com buffer sustenta `captureTrace()` e tudo o que é
construído sobre ele: a árvore de trace, os exportadores e os ouvintes
conectados pelo SPI. Seu portador é um buffer em anel limitado — 65.536
posições por padrão (`narrativetrace.buffer.capacity`), cerca de
1,75 MB, orçado em aproximadamente 300 bytes por evento na saturação —
drenado por uma única thread consumidora. Ele nunca cresce, e nunca
bloqueia a thread chamadora.

Sob pressão ele descarta carga em vez de aplicar contrapressão:

| Ocupação | Comportamento |
|---|---|
| até 70% | processamento completo — armazém, árvore, assinantes |
| 70–90% | descarte: eventos são drenados e desprezados em lotes |
| acima de 90% | emergência: tudo é desprezado até a pressão passar |

O próprio anel sobrescreve sua posição mais antiga em vez de crescer,
de modo que um produtor sempre pode escrever. Perder eventos na
saturação é o comportamento pretendido neste caminho — a alternativa é
suas threads de requisição esperando pela observabilidade.

## A perda é contada, nunca silenciosa

Todo evento descartado é contado — sobrescritas do anel, lotes
desprezados e descartes por contrapressão de assinantes, todos — e
somado em `BufferedEventConsumer.droppedCount()`. O rodapé do próprio
trace informa a perda, e é omitido apenas quando nada foi perdido: um
trace curto nunca é silenciosamente indistinguível de um tranquilo.

## O que uma queda custa

Os dois caminhos respondem à pergunta da queda de formas diferentes, de
propósito:

- **Caminho síncrono:** nada já narrado se perde. A escrita aconteceu
  antes de o método continuar.
- **Caminho com buffer:** a árvore de trace em andamento é melhor
  esforço. Eventos ainda no anel no momento de uma queda se perdem, e
  essa é a troca documentada por nunca bloquear um chamador.

## A ocultação acontece antes da bifurcação

Os valores de parâmetros são renderizados — e ocultados — uma vez, na
captura, antes de o evento ser publicado. Um valor negado por nome, por
anotação ou pela própria forma nunca entra no evento, então nenhum
caminho, nenhum exportador e nenhum ouvinte SPI jamais pode vê-lo. O
contrato completo, superfície por superfície, está em
[Privacidade e Ocultação](privacidade-e-ocultacao.md).

## Onde os consumidores se conectam

Duas costuras, ambas API pública:

- **`TraceEventListener`** — por evento, descoberto via
  `ServiceLoader`, entregue na thread consumidora do caminho com
  buffer. Um ouvinte que lança exceção é reportado uma vez e
  desabilitado pelo resto da vida da JVM; um consumidor que se comporta
  mal nunca derruba o pipeline junto.
- **`TraceExporter`** — por trace completado, em um limite de
  requisição (o filtro de servlet o invoca com a árvore pronta).

## Configuração

| Chave | Efeito |
|---|---|
| `narrativetrace.pipeline` | Seleciona pelo nome uma topologia de pipeline registrada; sem definição, constrói a topologia padrão de caminho duplo descrita aqui |
| `narrativetrace.buffer.capacity` | Tamanho do anel do caminho com buffer |
| `narrativetrace.narration` | `off` veta o ouvinte de narração SLF4J |

O pipeline é um componente atrás de uma única interface: esta página
documenta a topologia padrão, e tudo acima — ocultação em tempo de
captura, perda contada, isolamento de consumidores — vale independente
de qual topologia um deployment selecione. Todas as chaves são lidas
uma única vez na inicialização; veja o
[Guia de Configuração](guia-de-configuracao.md) para todas as
superfícies de configuração.
