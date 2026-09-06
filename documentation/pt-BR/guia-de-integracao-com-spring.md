<!-- source: documentation/spring-integration-guide.md blob cd2a81631cc6 | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Guia de integração com Spring

[English](../spring-integration-guide.md) | [Español](../es/guia-de-integracion-con-spring.md) | **Português** | [简体中文](../zh-CN/Spring集成指南.md)

Este guia cobre a integração do NarrativeTrace em uma aplicação Spring Boot, desde o tracing básico de beans até o ciclo de vida de requisições em produção e a propagação entre threads com `@Async`.

## Módulos

| Módulo | Propósito |
|---|---|
| `narrativetrace-spring` | `@EnableNarrativeTrace` — encapsula automaticamente os beans elegíveis por meio de um `BeanPostProcessor` |
| `narrativetrace-servlet` | `NarrativeTraceFilter` — ciclo de vida do trace por requisição (sem dependência do Spring) |
| `narrativetrace-spring-web` | Autoconfiguração do Spring para o filtro de servlet com exportador plugável |
| `narrativetrace-micrometer` | `NarrativeTraceThreadLocalAccessor` — propagação do trace entre threads via context-propagation do Micrometer |

## 1. Tracing básico de beans

Adicione a dependência:

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring:0.2.0")
```

Habilite o tracing na sua classe de configuração:

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.order", "com.example.payment"})
public class AppConfig { }
```

Quando `basePackages` é omitido, o padrão é o pacote da classe anotada (como em `@ComponentScan`).

### Nome do logger

Quando `narrativetrace-slf4j` está no classpath, o contexto criado automaticamente narra através do SLF4J automaticamente (`Slf4jTraceEventListener` na via síncrona do pipeline). Configure o nome do logger:

```java
@EnableNarrativeTrace(loggerName = "myapp.traces")
```

O padrão é `"narrativetrace"`. Defina como `""` para desativar o encapsulamento automático do SLF4J.

### Identidade do serviço

Configure os metadados de identidade do serviço na anotação quando os spans e os traces exportados
precisarem carregar uma identidade de processo estável:

```java
@Configuration
@EnableNarrativeTrace(
    basePackages = {"com.example.order", "com.example.payment"},
    serviceName = "order-service",
    serviceVersion = "2.0.0",
    environment = "production"
)
public class AppConfig { }
```

Esses valores são estampados em cada `SpanContext` criado. Integrações posteriores, como o MDC do
SLF4J, a exportação JSON e o OpenTelemetry, podem então expor `service.name`, `service.version` e
`service.environment` para correlação.

### O que acontece

Um `BeanPostProcessor` (ordenado em `HIGHEST_PRECEDENCE`) encapsula os beans elegíveis em proxies dinâmicos do JDK que registram a entrada de métodos, os valores de retorno e as exceções em um `NarrativeContext` compartilhado.

**Regras de elegibilidade:**
- O bean precisa implementar pelo menos uma interface
- A classe do bean precisa estar em um pacote configurado
- As interfaces do framework Spring (por exemplo, `BeanFactoryAware`, `InitializingBean`) são excluídas

Um bean `NarrativeContext` é fornecido automaticamente. Quando `narrativetrace-slf4j` está no classpath e `loggerName` não está vazio, ele narra através do SLF4J sob esse logger. Defina seu próprio bean `narrativeContext` para sobrescrever esse comportamento (por exemplo, para a integração com o Micrometer — veja a seção 3).

### Ordem do BPP

O `BeanPostProcessor` de tracing é executado em `HIGHEST_PRECEDENCE`, o que significa que o proxy de trace é o invólucro **mais interno**. Ao ser combinado com o proxy `@Async` do Spring, as camadas ficam assim:

```
Proxy Async (externo) → Proxy de trace (interno) → Bean real
```

Isso garante que o tracing seja executado na thread assíncrona real, não na thread chamadora.

## 2. Tracing de requisições em produção

Para tracing de requisições HTTP em uma aplicação implantada, adicione o módulo do filtro de servlet:

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring-web:0.2.0")
```

Isso registra automaticamente o `NarrativeTraceFilter` como um bean do Spring. O filtro segue um ciclo de vida de reinício-cadeia-captura-exportação-reinício em cada requisição:

1. Reinicia o contexto (limpa o estado obsoleto)
2. Executa a cadeia de filtros (os beans posteriores são traceados via proxies)
3. Captura a árvore de traces acumulada
4. Exporta via o `TraceExporter` configurado
5. Reinicia novamente

Enquanto a requisição está ativa, o filtro também preenche campos de correlação persistentes no MDC, incluindo
`traceId`, `traceName`, `httpMethod`, `httpRoute` e `clientIp`. `traceName` é um alias determinístico
de três palavras derivado do ID do trace, pensado para uma correlação legível nos logs.

### Exportador padrão

Por padrão, os traces são exportados via `Slf4jTraceExporter` (JSON para o SLF4J no nível INFO). Para usar um exportador personalizado, registre um bean `TraceExporter`:

```java
@Bean
TraceExporter traceExporter() {
    return (tree, requestContext) -> {
        // Envie para a sua plataforma de observabilidade
    };
}
```

O `RequestContext` fornece `method`, `uri`, `statusCode` e `durationMillis`.

### Contexto de usuário (`RequestContextProvider`)

A autenticação e a multilocação geralmente vivem fora do contexto do trace — em um principal de segurança, em um cabeçalho ou em um atributo de sessão. O `RequestContextProvider` os copia da requisição uma vez por requisição, e o NarrativeTrace estampa `enduserId`, `sessionId` e `tenantId` em cada span que essa requisição vier a criar, e no MDC.

A interface é genérica sobre o tipo de requisição do framework para que o jar da API permaneça livre de dependências. Tanto o filtro de servlet quanto o wiring do Spring Web a vinculam a `HttpServletRequest` — **escreva essa vinculação explicitamente**:

```java
import ai.narrativetrace.api.export.RequestContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
class SecurityContextProvider implements RequestContextProvider<HttpServletRequest> {

    @Override
    public UserContext resolveUserContext(HttpServletRequest request) {
        var principal = request.getUserPrincipal();
        if (principal == null) {
            return null;  // requisição anônima — ausência, não um erro
        }
        return new UserContext(
                principal.getName(),
                request.getRequestedSessionId(),
                request.getHeader("X-Tenant-Id"));
    }
}
```

`NarrativeTraceWebConfiguration` detecta o bean automaticamente — não há mais nada para conectar.

> **A vinculação é o que torna o bean localizável.** O provedor é resolvido pelo seu tipo *vinculado*, `RequestContextProvider<HttpServletRequest>`. Um bean declarado de forma bruta (`implements RequestContextProvider`) ou vinculado a algum outro tipo de requisição simplesmente não é um candidato: a resolução do Spring, que é sensível a generics, não o encontra, o filtro é executado sem contexto de usuário, e nada é registrado no log. O sintoma são campos `enduserId`/`tenantId` vazios em cada span, não um erro.

Retornar `null` significa "nenhuma identidade para esta requisição", o que é normal para tráfego anônimo. Um provedor que lança uma exceção recebe o mesmo tratamento — a requisição prossegue sem contexto de usuário, porque a observabilidade nunca deve fazer uma requisição falhar.

**Sem o Spring**, a mesma implementação vai direto para o filtro:

```java
var filter = new NarrativeTraceFilter(context, exporter, new SecurityContextProvider());
```

## 3. Propagação entre threads (`@Async`)

Quando um método traceado despacha trabalho para outra thread (via `@Async`, `CompletableFuture.supplyAsync`, ou um executor personalizado), o contexto do trace precisa ser propagado explicitamente. Sem isso, o trabalho assíncrono aparece como um trace desconectado na thread de trabalho.

### Como funciona

O NarrativeTrace usa o [context-propagation do Micrometer](https://github.com/micrometer-metrics/context-propagation) para carregar o estado do trace através dos limites entre threads. A configuração tem três partes:

1. **Registrar o accessor** — informa ao Micrometer como capturar/restaurar o snapshot do estado `ThreadLocal` do NarrativeTrace
2. **Configurar um `TaskDecorator`** — encapsula os runnables assíncronos para que carreguem o snapshot até a thread de trabalho
3. **Sobrescrever o bean `NarrativeContext`** — conecta o accessor à instância real do contexto

### Dependências

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring:0.2.0")
implementation("ai.narrativetrace:narrativetrace-micrometer:0.2.0")
```

### Configuração

```java
@Configuration
@EnableNarrativeTrace
@EnableAsync
public class AppConfig {

    @Bean
    NarrativeContext narrativeContext() {
        var tlc = new ThreadLocalNarrativeContext();

        // Registra no Micrometer para que o context-propagation conheça o NarrativeTrace
        var accessor = new NarrativeTraceThreadLocalAccessor(tlc);
        ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);

        return tlc; // a narração do SLF4J é anexada automaticamente quando narrativetrace-slf4j está presente
    }

    @Bean
    ThreadPoolTaskExecutor taskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setCorePoolSize(2);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

### O `TaskDecorator`

O `TaskDecorator` é a ponte que captura o contexto na thread chamadora e o restaura na thread de trabalho. O `narrativetrace-spring` já vem com um — `ai.narrativetrace.spring.ContextPropagatingTaskDecorator` — e o trecho acima o usa diretamente. Ele carrega duas coisas, ambas capturadas quando a tarefa é *submetida*:

| Parte | Necessário para o NarrativeTrace? | Finalidade |
|---|---|---|
| `ContextSnapshot` do Micrometer | **Sim** | Propaga o estado do NarrativeTrace via o `ThreadLocalAccessor` registrado |
| Captura/restauração do MDC | Não | Propaga o MDC do SLF4J para correlação de logging (`traceId`, `traceName`, metadados da requisição, etc.) |

A metade do MDC vem ativada por padrão e restaura depois o próprio MDC da thread de trabalho, inclusive quando a tarefa lança uma exceção. Desative-a quando sua aplicação não usa MDC, ou quando outro decorator já é o responsável por ele:

```java
executor.setTaskDecorator(ContextPropagatingTaskDecorator.withoutMdc());
```

**Dependências.** O decorator precisa de `io.micrometer:context-propagation` no classpath de execução — o `narrativetrace-spring` o declara como `compileOnly`, então adicione-o você mesmo (o módulo `narrativetrace-micrometer`, que você já precisa para o accessor, já o traz — veja [Dependências](#dependências) acima). O SLF4J é opcional: a propagação do MDC se desativa sozinha quando `org.slf4j.MDC` está ausente.

**Se você já tem um `TaskDecorator`**, um executor tem apenas um slot de decorator — combine os dois, ou adicione o snapshot do Micrometer ao seu:

```java
public class MyDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        var snapshot = ContextSnapshotFactory.builder().build().captureAll();
        return () -> snapshot.wrap(runnable).run();
    }
}
```

### Verificando a propagação

Um método `@Async` deve aparecer como filho na árvore de traces, mesmo que seja executado em uma thread diferente:

```
OrderService.placeOrder(customerId: "C-1234")
  InventoryService.reserve(productId: "SKU-KB", quantity: 2) → ReservedInventory(...)
  PaymentService.charge(customerId: "C-1234", amount: 179.98) → Payment(...)
  NotificationService.sendConfirmation(orderId: "ORD-001") → true    ← runs on async-1 thread
```

Onde ela aparece depende de quando a tarefa foi submetida, não de quando terminou:
submetida enquanto `placeOrder` ainda estava em execução, ela é filha de `placeOrder`;
submetida depois que `placeOrder` retornou, ela é a próxima raiz no mesmo trace.
De qualquer forma, o `captureTrace()` do chamador a reporta, condizente com o que o fluxo
de log síncrono já havia narrado (ADR-013).

Sem o `TaskDecorator`, a chamada de notificação não apareceria no trace de forma alguma.

### Saída adiada com CompletableFuture

Quando um método traceado retorna `CompletableFuture<T>`, o proxy trata automaticamente a conclusão adiada:

1. O frame é desanexado da pilha ativa via `detachFrame(handle)`
2. Um callback `whenComplete` é registrado no future
3. Quando o future se resolve, o frame é completado com o valor real resolvido (não `<pending>`)

Isso funciona de forma transparente com métodos `@Async` que retornam `CompletableFuture`. Nenhuma configuração adicional é necessária — o proxy detecta o tipo de retorno e aplica a saída adiada automaticamente.

## 4. Testando beans do Spring

Para testar beans traceados com JUnit 5, você não precisa do módulo do Spring — use `NarrativeTraceProxy` diretamente:

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {

    @Test
    void customerPlacesOrder(NarrativeContext context) {
        var customers = NarrativeTraceProxy.trace(
            new InMemoryCustomerService(), CustomerService.class, context);
        var orders = NarrativeTraceProxy.trace(
            new DefaultOrderService(customers), OrderService.class, context);

        orders.placeOrder("C-1234");
        // A saída do trace é automática por meio da extensão
    }
}
```

Essa abordagem é mais rápida do que um contexto Spring completo e dá a você controle direto sobre o wiring. Use `@SpringBootTest` apenas quando precisar testar a própria integração com o Spring (auto-wiring, ordem do BPP, propagação com `@Async`).

Para ver um exemplo funcional que inicializa um contexto Spring real e verifica tanto o tracing de beans auto-encapsulados quanto a propagação de threads com `@Async`, veja [`SpringIntegrationTest`](../../narrativetrace-examples/ecommerce/src/test/java/ai/narrativetrace/examples/ecommerce/SpringIntegrationTest.java).

## Veja também

- [Guia de instalação](guia-de-instalacao.md) — dependências, caminhos de integração, seleção de módulos
- [Guia de configuração](guia-de-configuracao.md) — níveis de tracing, detalhes do `@EnableNarrativeTrace`
- [Guia de integração com Micronaut](guia-de-integracao-com-micronaut.md) — equivalente para Micronaut deste guia
- [Guia de anotações](guia-de-anotacoes.md) — `@Narrated`, `@OnError`, `@NotTraced`
