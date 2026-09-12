<!-- source: documentation/privacy-and-redaction.md blob f1658f8ad5f8 | translated: 2026-09-12 | reviewed: - -->
# Privacidade e ocultação

[English](../privacy-and-redaction.md) | Español | **Português** | [简体中文](../zh-CN/隐私与脱敏.md)

Esta biblioteca roda dentro do seu processo e escreve arquivos que seu
time vai compartilhar — artefatos de CI, baselines commitadas, linhas de
log de produção. Esta página é a versão linha a linha desse contrato: o
que oculta, onde alcança e onde não alcança, e o que o NarrativeTrace
garante versus o que ele nem chega a reivindicar.

**A saída de trace em tempo de teste é gravada por padrão** — a extensão do
JUnit 5 e a integração do JUnit 4 gravam os artefatos `.md`/`.json`/`.mmd`
de cada teste no diretório efêmero `build/narrativetrace` (ignorado pelo
git, regenerado a cada execução) sem nenhuma configuração;
`narrativetrace.output=false` desativa isso *(since 0.2.2, unreleased)*. Esse padrão não muda o que é
ocultado nem como — todo artefato passa pelo mesmo `ValueRenderer` e pela
mesma lista de negação descrita abaixo, quer a gravação tenha sido ativada
por padrão ou explicitamente. Veja o
[Guia de Configuração](guia-de-configuracao.md) para cada propriedade, e
[O que commitar](o-que-commitar.md) para entender por que nada disso
pertence ao controle de versão.

## Ocultação, superfície por superfície

| Superfície | Pode desativar a ocultação embutida? |
|---|---|
| Saída padrão do JUnit (proxy, agente, Spring, Micronaut, servlet, SLF4J) | Não |
| Narração do agente | Não |
| `ValueRenderer` customizado que seu próprio código constrói | Sim — apenas passando `RedactionPolicy.DISABLED` explicitamente ao construtor |
| `@NotTraced` | Não aplicável — é a própria coisa que faz a ocultação, e sempre vence |
| `.nt` estrutural | Não aplicável — não carrega valores para ocultar, para começo de conversa |

Verificado contra o código, não inferido a partir da documentação: toda
integração distribuída — `NarrativeTraceProxy` e `AgentRuntime` (os dois
lugares onde a captura de fato acontece) — e todo módulo de framework
construído sobre eles constroem seu `ValueRenderer` da mesma forma, como
um campo `private static final` sem ponto de injeção:

```java
private static final ValueRenderer VALUE_RENDERER = new ValueRenderer();
```

`new ValueRenderer()` usa `RedactionPolicy.DEFAULT` por padrão. Não
existe flag de configuração, system property ou knob de DSL do plugin
que alcance essa constante — a única forma de obter
`RedactionPolicy.DISABLED` é código de aplicação que constrói sua
própria instância de `ValueRenderer` diretamente, contornando todo
caminho de integração distribuído. Isso é um ato deliberado e revisável
no seu próprio código-fonte, não um estado de configuração que um deploy
possa alternar silenciosamente.

## O que a lista de negação captura, e o que tem prioridade sobre ela

Dois mecanismos de ocultação independentes se aplicam a todo valor
renderizado reflexivamente:

1. **`@NotTraced`** em um parâmetro, campo ou componente de record —
   sempre oculta, incondicionalmente, em todo lugar.
2. **A lista de negação baseada em nome** (`RedactionPolicy.DEFAULT`) —
   compara **nomes de parâmetro, nomes de campo e nomes de componente de
   record** com um conjunto embutido e multilíngue (`password`, `token`,
   `cvv`, `ssn`, `secret`, `authorization`, `cardNumber`, e seus
   equivalentes em espanhol, português, francês, alemão e chinês, entre
   outros), mais uma segunda verificação independente sobre a *forma* do
   próprio valor (um JWT, um número de cartão válido pelo algoritmo de
   Luhn, uma string `Set-Cookie`, um número de identidade nacional que
   passa seu próprio checksum, um número de Seguro Social dos EUA com
   hífens), de modo que um bearer token passado sob um nome não
   reconhecido ainda assim é capturado.

   Nomes de parâmetro foram adicionados em 2026-09-10. Até então esse eixo
   alcançava apenas campos e componentes de record, então um método que
   recebia `String password` o imprimia por completo a menos que o
   parâmetro levasse `@NotTraced` — enquanto o README afirmava o
   contrário. A decisão agora acontece na **captura**, nos metadados por
   método que tanto o proxy quanto o agente já armazenam em cache, o que
   traz duas consequências que vale a pena conhecer: a busca não custa
   nada por chamada rastreada, e um valor negado nunca entra no
   `TraceEvent`, então ele não pode alcançar o caminho de auditoria, o
   consumidor com buffer nem um listener conectado através do SPI do
   pipeline. Ele nunca é renderizado e depois substituído — um segredo
   formatado e descartado existiu do mesmo jeito, como string.

### O `toString()` próprio de um tipo nunca é confiável enquanto o tipo tiver estado

Este é o invariante sobre o qual repousa o resto desta página, e vale a
pena afirmá-lo com clareza:

> **Uma classe ou `record` que declara campos de instância é percorrida
> campo a campo, em qualquer profundidade, consultando os dois mecanismos
> de ocultação por campo — seja lá o que seu próprio `toString()` teria
> impresso.**

Exatamente dois tipos de valor mantêm seu próprio texto. Um é uma classe
**sem nenhum campo de instância**: não há nada a ocultar nem nada a
percorrer. O outro é uma classe **definida pela plataforma** —
`LocalDate`, `Duration`, `UUID`, `URI` e afins — cujo `toString()` é o
formato do JDK, não código de aplicação, e que não pode declarar um dos
seus campos em primeiro lugar. Uma classe que *seu* código declara é
código de aplicação, seja lá o que ela estenda.

A única opção explícita para voltar a uma renderização cuidadosamente
escrita é **`@NarrativeSummary`**: um método sem argumentos que você
escreve *para* o trace, então sua saída é sua escolha. Nem mesmo essa é
confiável ao pé da letra — seu texto passa pela checagem de forma do
valor, o escape de caracteres de controle e o limite de comprimento,
exatamente como um parâmetro `String`, de modo que um resumo que interpola
um bearer token ainda é renderizado como `[REDACTED]`.

Até 2026-09-11 a regra funcionava ao contrário: qualquer `toString()` era
preferido em vez da introspecção, a menos que a classe declarasse um campo
`@NotTraced`. Essa checagem não consultava nem a lista de negação por
nome, nem os tipos dos campos, o que deixava dois canais abertos. Um
simples `Login { username, password }` com um `toString()` escrito à mão
imprimia a senha **na profundidade zero** — sem aninhamento, sem wrapper,
sem nenhuma anotação envolvida. E um `toString()` cuidadosamente escrito
em qualquer classe externa imprimia valores `@NotTraced` aninhados
diretamente através da serialização em texto comum do Java, porque a
própria classe externa não declarava nada sensível. Percorrer os campos
também coloca de volta o limite de profundidade e a proteção contra ciclos
na frente de cada valor: seu `toString()` costumava rodar fora de ambos.

**O custo é real e foi aceito.** Uma classe de valor com um `toString()`
agradável e sem `@NarrativeSummary` agora é renderizada como um despejo de
campos — `Amount{currency: "EUR", units: 10}` em vez de `EUR 10.00`. Mais
feio, e correto. Adicione `@NarrativeSummary` aos tipos em que a leitura
importa.

Um **parâmetro** é resolvido ainda mais cedo, e a diferença decorre de em
que momento a decisão é tomada. Um parâmetro cujo *nome* a lista de
negação nega é decidido na captura, antes de o argumento chegar a qualquer
renderer — então nada sobre o valor chega a ser chamado. A distinção não é
inconsistência: um nome de campo é descoberto *pela* introspecção,
enquanto um nome de parâmetro é conhecido a partir da assinatura do método
antes de o valor ser tocado de qualquer forma.

A ocultação sobrevive a **qualquer invólucro, em qualquer profundidade**
— `Optional`, `Future`, `AtomicReference`, `AtomicReferenceArray` e um
`Map.Entry` isolado são abertos em vez de renderizados via seu próprio
`toString()`, e o que eles contêm é renderizado seguindo exatamente essas
regras, que se aplicam de novo a tudo o que *isso* contiver. Uma **chave**
de `Map` é percorrida da mesma forma, de modo que uma chave composta não
consegue carregar um campo para fora pelo único lugar em que a
serialização em texto é mais difícil de evitar. E a ocultação **vence um
template de narração que a nomeia**: `{param.property}` em
`@Narrated`/`@OnError` resolve um caminho até um membro ocultado como
`[REDACTED]`, em toda profundidade ao longo do caminho, nunca o valor
literal.

### Quando a renderização de uma parte falha

Um valor cujo `toString()`, `@NarrativeSummary`, getter ou acessor lança
uma exceção custa apenas seu próprio espaço: essa parte é renderizada como
`<error: IllegalStateException>` — o **nome do tipo da exceção e nada
mais** — e o restante do valor é renderizado por completo. A mensagem é
excluída deliberadamente. Uma mensagem de exceção costuma interpolar o
próprio valor que falhou ao formatar (`"cannot render " + password`),
então um marcador que a carregasse transformaria o próprio caminho de
falha do renderizador em um vazamento.

Detalhes completos e exemplos resolvidos: [Guia de
anotações](guia-de-anotacoes.md).

## Garantias

- **Falhas de tracing são isoladas da execução hospedeira.** A gravação
  é isolada de exceções em todo caminho; ambos os consumers do pipeline
  engolem seus próprios erros. Um `toString()` que lança exceção, um
  buffer cheio ou um appender quebrado nunca mudam o que seu método
  retorna ou lança.
- **As saídas padrão respeitam a ocultação.** Veja a tabela acima —
  nenhuma integração distribuída expõe uma forma de contorná-la.
- **O artefato estrutural `.nt` não tem nenhum valor de runtime.**
  Apenas nomes, hierarquia de chamadas e tipos de resultado — superfície
  zero para prompt injection, e isso é um property test (ADR-002), não
  uma política que alguém poderia esquecer de aplicar. O cabeçalho
  `scenario:` dele está coberto por isso: uma invocação de um
  `@ParameterizedTest` é titulada `<método> #<índice>`, nunca o nome
  exibido no qual um template `name = "…"` interpolou seus argumentos *(since 0.2.2, unreleased)*.
  Como o artefato se *chama* é outra questão — veja a não garantia
  abaixo.
- **O caminho de análise em buffer pode descartar eventos, mas sempre
  reporta a perda.** Ele nunca bloqueia quem chama e nunca cresce além
  do seu limite; uma captura que perdeu eventos imprime a contagem em
  seu próprio rodapé em vez de subnotificar silenciosamente.

## Não garantias

- **Nenhuma alegação de "overhead zero".** O tracing faz trabalho, e
  trabalho custa algo — veja a [seção de performance do
  README](../../README.md#performance) para os números medidos.
- **Sem tracing de métodos privados.** Ambos os caminhos de captura
  enxergam apenas métodos de interface (proxy) ou métodos não privados
  (agente); o ramo de um método privado é inferido a partir de quais de
  *suas* chamadas aparecem no trace.
- **Sem tracing automático de um bean sem interface, em nenhum caminho
  baseado em proxy.** Proxy, Spring e Micronaut envolvem tudo via um
  proxy dinâmico JDK, e uma classe sem interface é deixada intocada —
  silenciosamente, não como um erro. O agente Java é o caminho que não
  tem essa limitação.
- **Ainda sem lista de exclusão para o agente.** O `AgentConfig`
  interpreta exatamente `packages`, `loggerName`, `level` e
  `loggingJars` — só inclusão, nada para recortar uma exceção de dentro
  de um pacote incluído.
- **Nenhuma ocultação dos *nomes* dos testes.** O nome exibido de um teste
  é texto escrito por quem desenvolve, e um template
  `@ParameterizedTest(name = "find {0}")` interpola seus argumentos nele.
  Esse nome chega ao *nome de arquivo* do artefato
  (`equipment_can_be_found-002-find_tent.md` — o rótulo em forma de slug é
  o que distingue duas invocações em disco), ao `manifest.json` da
  execução e ao título dos artefatos que carregam valores. Nenhuma lista
  de negação é consultada para qualquer um deles: aqui um nome é um
  identificador, não um valor capturado. Mantenha segredos fora dos
  templates de nome exibido — o cabeçalho do `.nt` livre de valores é o
  único lugar em que isso é resolvido para você, por não usar o nome
  exibido de forma alguma.
- **Ainda sem suporte a Android ou GraalVM native-image.** Detalhes
  completos, incluindo *por que* Android é um "não" em vez de um "ainda
  não", em [Guia de instalação §
  Compatibilidade](guia-de-instalacao.md#compatibilidade).

## O modelo de perda em produção, visualmente

Todo evento é escrito de forma síncrona no stream de log durável e,
*além disso*, publicado em um buffer em memória de melhor esforço e
tamanho limitado — as duas metades do pipeline de caminho duplo carregam
obrigações opostas por design:

```text
caminho de log síncrono (SLF4J, inline na thread chamadora)
   não pode falhar a aplicação hospedeira
   não pode ser sufocado pela falha do caminho de análise
   durável — este é o registro

caminho de análise em buffer (anel limitado, drenado para o store retido)
   pode descartar eventos sob carga, e sempre avisa disso
   nunca pode bloquear quem chama
   nunca pode crescer além do seu limite (65.536 slots por padrão)
   melhor esforço — isto é análise, não auditoria
```

Perder o buffer perde a fidelidade de análise daquela execução. Perder o
stream de log perde o registro. Essa assimetria é o motivo de serem dois
caminhos com dois comportamentos de falha diferentes, em vez de um único
caminho com um único trade-off.

## O que esta página não cobre

Onde o NarrativeTrace se encaixa em cada etapa do seu pipeline —
desenvolvimento, CI/aceitação, produção — está no [Guia do ciclo de
vida](guia-do-ciclo-de-vida.md). O que acontece quando ele se combina
com proxies AOP, bibliotecas de contrato ou outro agente envolvendo as
mesmas classes — a ordem de aninhamento pode mudar como um trace *é
lido*, nunca o que um método *retorna ou lança*, porque o registro nunca
substitui um resultado — está em [Escolhendo uma integração § Empilhar
com outros
wrappers](escolhendo-uma-integracao.md#empilhar-com-outros-wrappers).
