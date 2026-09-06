<!-- source: documentation/micronaut-integration-guide.md blob 6ba6a6f99491 | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Guia de integração com Micronaut

[English](../micronaut-integration-guide.md) | [Español](../es/guia-de-integracion-con-micronaut.md) | **Português** | [简体中文](../zh-CN/Micronaut集成指南.md)

Este guia cobre a integração do NarrativeTrace em uma aplicação Micronaut, desde o tracing básico de beans até o ciclo de vida de requisições HTTP em produção.

## Módulos

| Módulo | Propósito |
|---|---|
| `narrativetrace-micronaut` | Encapsula automaticamente os beans elegíveis por meio de um `BeanCreatedEventListener` — descoberto automaticamente no classpath |
| `narrativetrace-micronaut-http` | `HttpServerFilter` reativo para o ciclo de vida do trace por requisição |

## 1. Tracing básico de beans

Adicione a dependência:

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut:0.2.0")
```

Configure os pacotes base em `application.yml`:

```yaml
narrativetrace:
  base-packages:
    - com.example.order
    - com.example.payment
```

Não é necessária nenhuma anotação de habilitação. O `@Factory` e o `BeanCreatedEventListener` são descobertos automaticamente no classpath.

### O que acontece

Um `BeanCreatedEventListener<Any>` encapsula os beans elegíveis em proxies dinâmicos do JDK que registram a entrada de métodos, os valores de retorno e as exceções em um `NarrativeContext` compartilhado.

**Regras de elegibilidade:**
- A classe do bean precisa estar em um pacote base configurado
- O bean precisa implementar pelo menos uma interface em um pacote configurado
- Os beans sem interfaces correspondentes são deixados intactos
- Um `base-packages` vazio não encapsula nada (padrão seguro)

Um bean `NarrativeContext` é fornecido automaticamente (marcado como `@Secondary`). Quando `narrativetrace-slf4j` está no classpath e `loggerName` não está vazio, ele é conectado ao logging de eventos do SLF4J. Defina seu próprio `@Bean NarrativeContext` para sobrescrever esse comportamento.

### Propriedades de configuração

| Propriedade | Tipo | Padrão | Propósito |
|---|---|---|---|
| `narrativetrace.base-packages` | `List<String>` | `[]` | Prefixos de pacote para o emparelhamento de beans/interfaces |
| `narrativetrace.logger-name` | `String` | `"narrativetrace"` | Nome do logger SLF4J (vazio desativa o wiring do SLF4J) |
| `narrativetrace.service-name` | `String` | `""` | Identidade do serviço — estampada nos spans |
| `narrativetrace.service-version` | `String` | `""` | Identidade do serviço — estampada nos spans |
| `narrativetrace.environment` | `String` | `""` | Identidade do serviço — estampada nos spans |

### Sobrescrevendo o contexto padrão

Declare seu próprio `@Bean NarrativeContext` e o padrão `@Secondary` é substituído:

```kotlin
@Factory
class MyConfig {
    @Bean
    @Singleton
    fun narrativeContext(): NarrativeContext {
        // a narração do SLF4J é anexada automaticamente quando narrativetrace-slf4j está presente;
        // o argumento de pipeline a encaminha para um nome de logger personalizado.
        return ThreadLocalNarrativeContext(
            NarrativeTraceConfig(), PipelineBootstrap.createDefault("myapp.traces"))
    }
}
```

## 2. Tracing de requisições HTTP em produção

Adicione o módulo do filtro HTTP:

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.0")
```

O filtro HTTP reativo (`HttpServerFilter`) é registrado automaticamente ao estar no classpath — nenhuma configuração adicional é necessária.

### Ciclo de vida do filtro

1. Reinicia o contexto (limpa o estado obsoleto)
2. Estampa os metadados da requisição (método HTTP, caminho, endereço remoto)
3. Prossegue pela cadeia de filtros via `Mono.from(chain.proceed(request))`
4. Captura a árvore de traces acumulada
5. Exporta via o `TraceExporter` configurado
6. Reinicia novamente (limpeza via `doFinally`)

O filtro corresponde a todos os caminhos (`@Filter("/**")`).

Enquanto a requisição está ativa, o filtro preenche campos de correlação persistentes no MDC, incluindo
`traceId`, `traceName`, `httpMethod`, `httpRoute` e `clientIp`. `traceName` é um alias determinístico
de três palavras derivado do ID do trace, pensado para uma correlação legível no log.

### Exportador padrão

Por padrão, os traces são exportados via `Slf4jTraceExporter` (JSON para o SLF4J no nível INFO). O nome do logger de exportação é derivado da propriedade `loggerName` configurada: `<loggerName>.export` (por exemplo, `narrativetrace.export`).

Para usar um exportador personalizado, registre um bean `TraceExporter`:

```kotlin
@Factory
class MyExporterConfig {
    @Bean
    @Singleton
    fun traceExporter(): TraceExporter = TraceExporter { tree, requestContext ->
        // Envie para a sua plataforma de observabilidade
    }
}
```

O exportador padrão `@Secondary` é substituído automaticamente.

### Contexto de usuário (`RequestContextProvider`)

A autenticação e a multilocação geralmente vivem fora do contexto do trace — em um cabeçalho, em um principal de segurança ou em um atributo de sessão. O `RequestContextProvider` os copia da requisição uma vez por requisição, e o NarrativeTrace estampa `enduserId`, `sessionId` e `tenantId` em cada span que essa requisição vier a criar, e no MDC.

O Micronaut tem seu **próprio** tipo de provedor, `ai.narrativetrace.micronaut.http.RequestContextProvider`, tipado sobre o `HttpRequest` do Micronaut. Declare-o como singleton e o filtro o injeta:

```kotlin
import ai.narrativetrace.micronaut.http.RequestContextProvider
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton

@Singleton
class SecurityContextProvider : RequestContextProvider {
    override fun resolveUserContext(request: HttpRequest<*>): RequestContextProvider.UserContext? {
        val userId = request.headers["X-User-Id"] ?: return null  // anônimo — ausência, não um erro
        return RequestContextProvider.UserContext(
            enduserId = userId,
            sessionId = request.headers["X-Session-Id"],
            tenantId = request.headers["X-Tenant-Id"],
        )
    }
}
```

> **Importe o tipo do Micronaut, não o do jar da API.** `ai.narrativetrace.api.export.RequestContextProvider<REQUEST>` é uma interface diferente — genérica, e vinculada a `HttpServletRequest` pelas integrações de servlet e Spring Web. Um bean que implemente *essa* não é um candidato aqui: o filtro não injeta nada, é executado sem contexto de usuário, e não registra nada no log. O sintoma são campos `enduserId`/`tenantId` vazios em cada span, não um erro. Os dois tipos são deliberadamente separados; veja [API Surface](../api-surface.md).

Retornar `null` significa "nenhuma identidade para esta requisição", o que é normal para tráfego anônimo. Um provedor que lança uma exceção recebe o mesmo tratamento — a requisição prossegue sem contexto de usuário, porque a observabilidade nunca deve fazer uma requisição falhar.

### Tratamento de erros

- As falhas do exportador são silenciosamente engolidas — a observabilidade nunca deve fazer as requisições falharem
- Traces vazios (nenhum método traceado chamado) pulam a exportação por completo
- Erros na cadeia disparam a exportação com o código de status 500, e então o erro se propaga normalmente
- `doFinally` garante o reinício do contexto em caso de sucesso, erro ou cancelamento

## 3. Comparação de arquitetura com Spring

| Aspecto | Spring | Micronaut |
|---|---|---|
| Mecanismo de habilitação | Anotação `@EnableNarrativeTrace` | `@Factory` descoberto automaticamente no classpath |
| Configuração | Atributos da anotação | `application.yml` via `@ConfigurationProperties` |
| Encapsulamento de beans | `BeanPostProcessor` | `BeanCreatedEventListener<Any>` |
| Sobrescrita do bean padrão | Definir um bean `narrativeContext` | Declarar `@Bean NarrativeContext` (substitui o `@Secondary`) |
| Filtro HTTP | `Filter` de servlet + `@Configuration` do Spring | `HttpServerFilter` reativo (`@Filter("/**")`) |
| Sobrescrita do exportador | `ObjectProvider<TraceExporter>` | Padrão `@Secondary`, o `@Bean` do usuário sobrescreve |
| Resolução tardia | `BeanFactoryAware` | `ApplicationContext` + guarda de reentrância com ThreadLocal |

### Por que o listener usa o ApplicationContext

`BeanCreatedEventListener<Any>` dispara para **todas** as criações de beans, incluindo as próprias dependências do listener (`NarrativeContext`, `NarrativeTraceProperties`). Injetar esses beans por construtor causaria uma dependência circular ou um estouro de pilha. O listener os resolve de forma tardia a partir do `ApplicationContext`, com uma guarda de reentrância `ThreadLocal` que ignora o encapsulamento durante a resolução de dependências.

## 4. Testando beans do Micronaut

Para testar beans traceados com JUnit 5, você não precisa do módulo do Micronaut — use `NarrativeTraceProxy` diretamente:

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        var orders = NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, context);
        orders.placeOrder("C-1234");
    }
}
```

Use `@MicronautTest` apenas quando precisar testar a própria integração com o Micronaut (auto-wiring, encapsulamento pelo listener de beans, ciclo de vida do filtro HTTP).

## Veja também

- [Guia de instalação](guia-de-instalacao.md) — dependências, caminhos de integração, seleção de módulos
- [Guia de configuração](guia-de-configuracao.md) — níveis de tracing, detalhes de configuração do Micronaut
- [Guia de integração com Spring](guia-de-integracao-com-spring.md) — equivalente para Spring deste guia
- [Guia de anotações](guia-de-anotacoes.md) — `@Narrated`, `@OnError`, `@NotTraced`
