<!-- source: documentation/annotations-guide.md blob fec2e7b0e04d | translated: 2026-09-17 | reviewed: - -->
# Guia de anotações do NarrativeTrace para Java

[English](../annotations-guide.md) | [Español](../es/guia-de-anotaciones.md) | **Português** | [简体中文](../zh-CN/注解指南.md)

Este guia lista todas as anotações disponíveis no NarrativeTrace para Java e explica quando e como usar cada uma.

O NarrativeTrace segue a filosofia **O código é o log**: os nomes de métodos, os nomes de parâmetros e os valores de retorno já deveriam comunicar por si só a história da execução. Mantenha a lógica de negócio limpa e expressiva antes de mais nada, e use as anotações de forma excepcional, não por padrão. Adicione anotações apenas quando elas agregarem valor adicional concreto, como narração direcionada, contexto específico de erro ou ocultação de dados sensíveis.

## Inventário de anotações

| Anotação | Módulo | Alvo | Finalidade |
|---|---|---|---|
| `@Narrated` | `narrativetrace-core` | Método | Adiciona texto de narração legível por humanos a um método traceado. |
| `@OnError` | `narrativetrace-core` | Método | Adiciona texto de erro contextual quando um método lança uma exceção. |
| `@NotTraced` | `narrativetrace-core` | Parâmetro, campo, componente de record | Marca um valor como oculto na saída do trace; também é respeitado em campos e componentes de record durante a introspecção reflexiva. |
| `@NarrativeSummary` | `narrativetrace-core` | Método | Fornece uma renderização personalizada de valores para objetos nos traces. |
| `@NarrativeElements` | `narrativetrace-api` | Tipo | Declara que a própria iteração de um tipo `Iterable` é pura, para que a renderização o percorra. |
| `@EnableNarrativeTrace` | `narrativetrace-spring` | Tipo (`@Configuration`) | Habilita o tracing com auto-proxy do Spring para os pacotes selecionados. |

## Anotações principais

### `@Narrated`

`@Narrated` é uma válvula de escape, não a forma padrão de adicionar
narração — o caminho padrão é inteiramente derivado do próprio nome do
método, de seus parâmetros e do resultado. Recorrer a um template é um
sinal, o mesmo que a pontuação de clareza existe para detectar: significa
que o código não está dizendo por si mesmo o que faz. Antes de escrever um,
pergunte-se se o nome do método é o problema real — um nome melhor conserta
todo trace que passar por esse método, não só esta linha.

Use-o, deliberadamente, em métodos quando quiser uma frase explícita no trace em vez de depender apenas do nome do método + parâmetros.

```java
public interface OrderService {
    @Narrated("Placing order of {quantity} units for customer {customerId}")
    OrderResult placeOrder(String customerId, int quantity);
}
```

Como funciona:

- Os marcadores do template usam os nomes dos parâmetros (por exemplo, `{customerId}`).
- Funciona com tracing por proxy e com tracing baseado em agente.
- Enriquece a saída do trace em todos os níveis com texto de narração legível por humanos.

### `@OnError`

Use `@OnError` para anexar mensagens específicas de contexto a exceções.

```java
public interface PaymentService {
    @OnError(value = "Payment declined for {customerId}, amount was {amount}",
             exception = PaymentDeclinedException.class)
    @OnError(value = "Temporary payment failure for {customerId}",
             exception = ExternalServiceException.class)
    PaymentConfirmation charge(String customerId, double amount, @NotTraced String token);
}
```

Como funciona:

- Você pode declarar várias anotações `@OnError` no mesmo método (ela é repetível).
- Se várias corresponderem, o tipo de exceção mais específico é escolhido.
- Um `@OnError("...")` isolado equivale a `exception = Throwable.class`.

A anotação contêiner `@OnErrors` existe por trás do `@OnError` repetível. No código normal, você nunca a escreve diretamente — apenas empilhe várias anotações `@OnError`.

### `@NotTraced`

Use `@NotTraced` em **parâmetros, campos ou componentes de record** sensíveis para que seus valores sejam ocultados em todos os lugares.

```java
public interface AuthService {
    Session login(String username, @NotTraced String password);
}

// Também em um campo ou componente de record aninhado dentro de um objeto traceado:
record Card(String last4, @NotTraced String pan) {}
```

Como funciona:

- O nome permanece visível; o valor é substituído por `[REDACTED]` em todos os renderizadores e exportadores.
- Em um campo ou componente de record, a ocultação acontece durante a introspecção reflexiva, de modo que um segredo aninhado dentro de um DTO traceado fica oculto sem apagar o objeto inteiro.
- Casos de uso típicos: senhas, tokens, segredos, dados de cartão.

**A introspecção reflexiva oculta por padrão, não vaza por padrão.** O NarrativeTrace faz reflexão sobre os campos de um objeto traceado — mas uma lista de negação integrada baseada em nomes (`RedactionPolicy`) oculta automaticamente os nomes sensíveis comuns (`password`, `cvv`, `ssn`, `token`, `secret`, `authorization`, `cardNumber`, `accountNumber`, `routingNumber`, `sessionId`, `jwt`, `cookie`, `pan`, `iban`, …), e os valores de um `Map` cuja chave corresponda, antes de qualquer valor ser renderizado. As chaves de um `Map` são renderizadas pelo mesmo caminho protegido que qualquer outro valor: os objetos usados como chave respeitam `@NotTraced` e a lista de negação em seus próprios campos (nunca seu `toString()` bruto), e as chaves do tipo string são sanitizadas e limitadas em comprimento. `@NotTraced` cobre campos sensíveis que a lista de negação não reconheceria pelo nome. Sobrescreva os padrões com `new ValueRenderer(…, RedactionPolicy.ofPatterns(...))` ou desative com `RedactionPolicy.DISABLED`.

**Dois segredos são ocultados pelo que são, não apenas pelo nome que têm.** A lista de negação baseada em nomes não consegue ver um bearer token passado como `value`, retornado como um `String` isolado, ou presente sem nome dentro de uma lista, então uma segunda regra, independente, observa os bytes. Exatamente três formatos são reconhecidos: um JWT (três segmentos base64url cujo primeiro começa com `eyJ`), um número de cartão (13–19 dígitos, separadores permitidos, que passa na verificação de Luhn), e uma string `Set-Cookie` (`nome=valor` seguido por um atributo de cookie como `Path`, `Max-Age` ou `HttpOnly`). Tudo o mais é renderizado normalmente — esta é uma lista curta de assinaturas estruturais, não uma heurística de entropia, porque um valor apagado por suposição é um buraco na sua narrativa que você não consegue ver. Um falso positivo é aceito deliberadamente: um identificador do comprimento de um número de cartão que por acaso satisfaz Luhn. Um número de pedido que não o satisfaz permanece visível. `RedactionPolicy.DISABLED` desativa essa regra junto com a lista de negação por nomes; `RedactionPolicy.ofPatterns(...)` substitui apenas os nomes e a mantém ativa.

**O `toString()` próprio de um tipo nunca é confiável enquanto o tipo tiver estado.** Uma classe ou `record` que declara campos de instância — próprios ou herdados — é percorrida campo a campo, em qualquer profundidade, consultando os dois mecanismos de ocultação por campo, seja lá o que seu `toString()` teria impresso. Um único tipo de valor mantém seu próprio texto: um tipo folha da plataforma, nomeado um a um numa lista que o renderizador mantém (`LocalDate`, `Duration`, `UUID`, `URI`, `Path`, `Pattern` e afins), cujo `toString()` é o formato do JDK, não código de aplicação. Uma classe que seu próprio código declara é código de aplicação, seja lá o que ela estenda, e uma classe sem nenhum campo legível não é por isso uma classe que não tenha nada a dizer — ela pode guardar seu estado numa tabela estática indexada pela instância, num `ClassValue` ou num `ThreadLocal` e imprimi-lo a partir do seu próprio `toString()` — então ela é renderizada como o nome do seu tipo, não como seu texto (2026-09-19, em toda a família). A única opção explícita para voltar a uma renderização cuidadosamente escrita é `@NarrativeSummary`, mais abaixo — e mesmo seu texto passa pela checagem de forma do valor, o escape de caracteres de controle e o limite de comprimento. Até 2026-09-11 a regra funcionava ao contrário, e isso deixava um simples `Login { username, password }` com um `toString()` escrito à mão imprimir a senha na profundidade zero, e qualquer `toString()` cuidadosamente escrito imprimir valores `@NotTraced` aninhados diretamente através da serialização em texto comum do Java. O custo é real e foi aceito: uma classe de valor com um `toString()` agradável e sem `@NarrativeSummary` agora é renderizada como um despejo de campos — `Amount{currency: "EUR", units: 10}` em vez de `EUR 10.00`. Adicione `@NarrativeSummary` aos tipos em que a leitura importa.

**A ocultação sobrevive a qualquer invólucro, em qualquer profundidade.** Um invólucro como `Optional`, `OptionalInt`/`OptionalLong`/`OptionalDouble`, `Future`, `AtomicReference`, `AtomicReferenceArray` ou um `Map.Entry` isolado imprime o `toString()` bruto do seu conteúdo se for tratado como um valor, então o NarrativeTrace o abre em vez disso e renderiza o que ele contém seguindo exatamente estas regras — que se aplicam de novo a tudo o que *isso* contiver. Um `AtomicReferenceArray` é renderizado exatamente como o `Object[]` que contém os mesmos elementos, e um `Map.Entry` isolado é renderizado como `key=value`, exatamente como seria dentro de um `Map`. `Optional<Card>` é renderizado como `Card(number: "4111", cvv: [REDACTED])`, nunca como `Optional[Card[number=4111, cvv=123]]`; um invólucro vazio é renderizado como `<empty>`. Isso importa porque `Optional<T>` é o tipo de retorno idiomático de uma busca, que é exatamente por onde os dados ocultados trafegam.

**A ocultação vence um template que a nomeia.** `@Narrated` e `@OnError` resolvem caminhos `{param.propriedade}` sobre os argumentos brutos, e um caminho que chega a um membro oculto se resolve como `[REDACTED]` — em qualquer profundidade, de modo que um membro oculto no meio de um caminho também oculta tudo o que é nomeado abaixo dele. O mesmo vale quando um marcador nomeia o objeto inteiro em vez de um caminho dentro dele: `{card}` é renderizado como `Card(number: "4111", cvv: [REDACTED])`, nunca com o `toString()` próprio do objeto, que não sabe nada sobre `@NotTraced`. Um marcador que nomeia um objeto sempre o renderiza através do mesmo renderizador que o captura, de modo que um template e um argumento capturado concordam sobre a aparência do valor: um record é narrado estruturalmente, como `Money(currency: "EUR", amount: 10)`, em ambos os casos. Uma classe comum também é narrada de forma estrutural, como `Amount{currency: "EUR", units: 10}`: «não oculta nada» nunca foi um fato que o renderizador pudesse comprovar, apenas «nenhum membro `@NotTraced` *aqui*». Para escolher a narração você mesmo — para um record ou para qualquer outra coisa — dê ao tipo um método `@NarrativeSummary`, que é respeitado aqui exatamente como em qualquer outro lugar. Nomear um caminho, ou um objeto, nunca enfraquece as regras que se aplicam diretamente ao valor. A terceira forma de marcador obedece a essas mesmas duas regras: um `{nome}` isolado que nomeia um valor diretamente é respondido pela lista de negação lendo essa chave exatamente como lê um nome de campo, e pelo formato do próprio valor, de modo que `@Narrated("login {password}")` e um JWT que chega como `{value}` são ambos renderizados como `[REDACTED]`. Se você precisar do valor em uma narrativa, remova `@NotTraced` do componente; essa remoção é a decisão deliberada e revisável, e ela aparece no diff.

### `@NarrativeSummary`

Use `@NarrativeSummary` em um método sem argumentos que retorna uma string curta de resumo para a renderização de valores.

```java
public record Customer(String id, String name, CustomerTier tier) {
    @NarrativeSummary
    public String toNarrativeSummary() {
        return "Customer[id=%s, tier=%s]".formatted(id, tier);
    }
}
```

Como funciona:

- `ValueRenderer` procura um método público anotado com `@NarrativeSummary` e sem parâmetros.
- Se encontrado, a saída desse método é usada nos traces — depois de passar pela checagem de
  forma do valor, o escape de caracteres de controle e o limite de comprimento, então um resumo
  que interpola um bearer token ainda é renderizado como `[REDACTED]`. Sua intenção escolhe o
  texto; ela não isenta os bytes.
- Se o método lançar uma exceção, o valor é renderizado como `<error: IllegalStateException>`
  — o nome do tipo da exceção e nada mais. A mensagem é excluída de propósito: ela costuma
  interpolar o próprio valor que falhou ao formatar.
- Se não for encontrado, a renderização percorre os campos do objeto. Apenas um tipo folha da
  plataforma mantém em vez disso seu próprio `toString()`; um tipo sem nenhum campo legível é
  renderizado como o nome do seu tipo.

### `@NarrativeElements`

A renderização lê o estado de um valor; ela nunca executa o próprio código do valor, com
exatamente três exceções sancionadas: `@NarrativeSummary`, o `toString()` próprio de uma folha
da plataforma — e esta. Use `@NarrativeElements` em um tipo cujo `Iterable#iterator()` é conhecido
por ser puro, para que a renderização o percorra.

```java
@NarrativeElements
public final class RecentOrders implements Iterable<Order> {
    public Iterator<Order> iterator() {
        return orders.iterator(); // puro — sem carregamento preguiçoso, sem contador, sem E/S
    }
}
```

Como funciona:

- Sem a anotação, a renderização percorre um `Collection`/`Map` apenas quando o tipo é
  definido pela plataforma — um `ArrayList` simples, um `HashMap` simples — ou, para uma
  subclasse do usuário de um deles, através do próprio estado desse ancestral, nunca o
  `iterator()`/`entrySet()` sobrescrito da subclasse. Uma implementação de `Collection`/`Map`
  escrita à mão, e qualquer `Iterable` isolado que não seja nenhum dos dois, é renderizada
  como um objeto (ou um marcador de tipo limitado para um `Iterable` isolado) sem jamais
  chamar `iterator()`.
- `@NarrativeElements` é a opção explícita além de tudo isso: o próprio `iterator()` do tipo
  declarado é chamado, sob a mesma proteção de renderização que todo outro hook usa, com o
  mesmo limite de itens de coleção de qualquer outro percurso de elementos.
- Se o iterador lançar uma exceção, o valor é renderizado como `<error: IllegalStateException>`
  — o nome do tipo da exceção e nada mais, exatamente como o modo de falha de qualquer outro hook.
- A anotação se aplica exatamente ao tipo declarado, não às suas subclasses.

## O contrato de pureza — efeitos colaterais durante o tracing

O NarrativeTrace pode invocar um pequeno conjunto fixo de caminhos de código nos seus objetos ao renderizar
um trace. Mantenha esses membros **puros** — livres de efeitos colaterais como carregamento preguiçoso, contadores
de acesso, preenchimento de cache ou E/S — exatamente como você faria para um depurador ou um serializador.

O que é invocado, e o que não é:

- **A introspecção de campos nunca chama seu código.** Para qualquer objeto que tenha estado,
  o `ValueRenderer` lê seus *campos* por reflexão — uma leitura pura de memória. Um getter que
  incrementa um contador ou carrega dados de forma preguiçosa não é tocado pela introspecção,
  e o acessor gerado de um record também não: o valor de um componente de record é lido a
  partir do seu campo de apoio, então um acessor com efeito colateral ou que lança uma exceção
  também nunca é invocado.
- **Coleções e mapas são percorridos por origem, não por formato.** Um `Collection`/`Map`
  definido pela plataforma percorre seu próprio iterator/entrySet; uma subclasse do usuário de
  `ArrayList` ou `HashMap` percorre o próprio estado desse ancestral, nunca a sobrescrita da
  subclasse; uma implementação escrita à mão é introspectada como qualquer outro objeto.
  `@NarrativeElements` é a única opção explícita além disso, para um tipo cuja própria
  iteração é declarada pura.
- **O que o NarrativeTrace realmente invoca:** um método `@NarrativeSummary`, o `toString()` de
  um tipo folha da plataforma, o próprio `iterator()` de um tipo declarado com
  `@NarrativeElements`, e qualquer caminho de propriedade que você nomear em um template
  `@Narrated`/`@OnError` (`{order.total}` se resolve chamando primeiro o método acessor direto
  `total()` e depois o getter JavaBean `getTotal()`). Esses são os únicos lugares onde código
  do usuário é executado durante a renderização.
- **A invocação é limitada e isolada.** A saída tem limites (comprimento de string, itens de
  coleção, profundidade de introspecção), um getter, resumo, iterator ou `toString()` que lança
  uma exceção nunca pode fazer a chamada de negócio traceada falhar (os templates recorrem ao literal
  `{placeholder}`; a parte que falhou de uma renderização recorre a `<error: TypeName>`, nomeando
  o tipo da exceção e nunca sua mensagem), e os valores são renderizados de forma eager no
  ponto de chamada — qualquer efeito colateral acontece uma única vez, em um ponto determinístico, na thread chamadora.
- **A renderização nunca força uma computação adiada.** Um `Future` só é desembrulhado quando está
  `isDone()`; nada é bloqueado ou disparado.

Se um membro não puder ser puro, anote-o com `@NotTraced` — o valor de um membro oculto nunca é
lido — ou dê ao tipo um `@NarrativeSummary` para que você controle exatamente o que é acessado.
Um `toString()` curado não cumpre mais esse propósito em um tipo que tem campos: ele não chega
a ser chamado, justamente para que não possa imprimir além de uma ocultação. Em `TracingLevel.OFF`
(e para valores de parâmetros em `SUMMARY`), nenhuma renderização de argumento acontece, então
nenhum código do usuário é tocado no caminho crítico.

## Anotações do Spring

### `@EnableNarrativeTrace`

Use `@EnableNarrativeTrace` em uma classe de configuração do Spring para encapsular automaticamente beans com proxies do NarrativeTrace.

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.orders", "com.example.payments"})
public class AppConfig {
}
```

Atributos:

- `basePackages` — prefixos de pacote que limitam quais beans são encapsulados. O padrão é o pacote da classe anotada.
- `loggerName` — nome do logger SLF4J para eventos de trace (padrão `"narrativetrace"`). Quando `narrativetrace-slf4j` está no classpath, o contexto criado automaticamente narra através do SLF4J sob esse nome (`Slf4jTraceEventListener` na via síncrona do pipeline). Defina como `""` para desativar a narração.

Como funciona:

- Registra a configuração Spring do NarrativeTrace e o pós-processador de beans.
- Apenas os beans em `basePackages` são considerados.
- Os beans precisam implementar interfaces para serem colocados em proxy (proxies dinâmicos do JDK).
- Quando `narrativetrace-slf4j` está no classpath, o contexto criado automaticamente narra através do SLF4J automaticamente. Defina seu próprio bean `narrativeContext` para sobrescrever esse comportamento.

## Exemplo completo

```java
public interface TransferService {
    @Narrated("Transferring {amount} from {fromAccountId} to {toAccountId}")
    @OnError(value = "Transfer rejected for source account {fromAccountId}",
             exception = IllegalStateException.class)
    TransferResult transfer(
            String fromAccountId,
            String toAccountId,
            double amount,
            @NotTraced String authToken
    );
}
```

Este único método combina narração, contexto de erro direcionado e ocultação de parâmetros.

## Validação de templates

Os templates de `@Narrated` e `@OnError` usam marcadores `{paramName}` e `{param.property}`. Se um marcador não corresponder a nenhum parâmetro — por exemplo, `{custmerId}` em vez de `{customerId}` — o literal `{custmerId}` sobrevive na saída resolvida do trace.

O NarrativeTrace detecta isso automaticamente durante os testes. Após cada teste, a extensão do JUnit 5 e a regra do JUnit 4 percorrem cada nó do trace em busca de padrões `{...}` não resolvidos e imprimem um aviso:

```
WARNING: Unresolved template placeholder(s) detected:
  - OrderService.placeOrder: {custmerId} in narration
  - ExpenseService.pay: {expense.payer.name} in narration
      ↳ nested path not supported — only one property level resolves (e.g. {object.property})
```

Apenas um único nível de propriedade é resolvido: `{expense.payer}` chama `payer()` (ou `getPayer()`) sobre o argumento `expense`. Um caminho mais profundo como `{expense.payer.name}` nunca pode ser resolvido — o parser trata `payer.name` como um único acessor inexistente — então o marcador sobrevive literalmente em tempo de execução (de forma graciosa, nunca lança exceção). Como isso é um erro estrutural de escrita, e não um valor que por acaso era `null`, a validação em tempo de teste sinaliza caminhos de múltiplos níveis com a dica adicional `nested path not supported`.

**Um caminho oculto se resolve como `[REDACTED]`** — não como o valor, e não como o marcador literal. Um marcador que nomeia o próprio objeto (`{card}`) é recusado da mesma forma: o NarrativeTrace renderiza o objeto sem seus membros ocultos em vez de chamar seu `toString()`. As duas metades da regra de ocultação se aplicam ao longo do caminho: `@NotTraced` em um campo ou componente de record, e a lista de negação baseada em nomes (`password`, `cvv`, `token`, …) em uma propriedade que nenhuma anotação cobre. Como é o caminho inteiro que é recusado, isso vale também para um caminho de múltiplos níveis que nunca poderia se resolver de qualquer forma: `{order.card.cvv}` é renderizado como `[REDACTED]` em vez de sobreviver literalmente. Um marcador que não nomeia nenhum parâmetro, ou uma propriedade que seu proprietário não declara, ainda é preservado literalmente — isso é um erro de digitação de escrita, não há nenhum valor por trás dele, e o aviso acima ainda deve disparar.

Nenhuma configuração é necessária — os avisos aparecem na saída do console sempre que a extensão ou a regra estiver ativa. Isso detecta erros de digitação em nomes de marcadores (por exemplo, `{order.stauts}`) e caminhos de múltiplos níveis não resolvíveis assim que um teste exercita o método anotado.

## Veja também

- [Guia de instalação](guia-de-instalacao.md) — dependências, caminhos de integração, configuração da saída de trace
- [Guia de configuração](guia-de-configuracao.md) — níveis de tracing, configuração de JUnit/Gradle/Spring/SLF4J
- [Guia de clareza](guia-de-clareza.md) — modelo de pontuação, componentes de NLP, integração com JUnit
