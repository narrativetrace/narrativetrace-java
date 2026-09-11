<!-- source: documentation/spring-integration-guide.md blob 0d6314bf97ad | translated: 2026-08-31 | reviewed: 2026-09-03 -->
# Spring 集成指南

[English](../spring-integration-guide.md) | [Español](../es/guia-de-integracion-con-spring.md) | **简体中文**

本指南介绍如何将 NarrativeTrace 集成到 Spring Boot 应用中,从基础的 Bean 追踪,到生产环境的请求生命周期,再到使用 `@Async` 时的跨线程传播。

## 模块

| 模块 | 用途 |
|---|---|
| `narrativetrace-spring` | `@EnableNarrativeTrace` —— 通过 `BeanPostProcessor` 自动包装符合条件的 Bean |
| `narrativetrace-servlet` | `NarrativeTraceFilter` —— 按请求管理追踪生命周期(不依赖 Spring) |
| `narrativetrace-spring-web` | 为 Servlet 过滤器提供 Spring 自动配置,支持可插拔的导出器 |
| `narrativetrace-micrometer` | `NarrativeTraceThreadLocalAccessor` —— 通过 Micrometer context-propagation 实现跨线程追踪传播 |

## 1. 基础 Bean 追踪

添加依赖:

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring:0.2.1")
```

在你的配置类上启用追踪:

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.order", "com.example.payment"})
public class AppConfig { }
```

省略 `basePackages` 时,默认使用被注解类所在的包(与 `@ComponentScan` 一致)。

### Logger 名称

当 classpath 上存在 `narrativetrace-slf4j` 时,自动创建的上下文会自动经由 SLF4J 叙述(`Slf4jTraceEventListener` 位于管道的同步路径上)。配置 logger 名称:

```java
@EnableNarrativeTrace(loggerName = "myapp.traces")
```

默认值为 `"narrativetrace"`。设为 `""` 可禁用自动的 SLF4J 包装。

### 服务身份

当 Span 和导出的追踪需要携带稳定的进程身份时,在注解上配置
服务身份元数据:

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

这些值会被写入每一个新创建的 `SpanContext`。SLF4J MDC、JSON 导出和
OpenTelemetry 等下游集成随后即可暴露 `service.name`、`service.version` 和
`service.environment` 用于关联。

### 幕后发生了什么

一个 `BeanPostProcessor`(顺序为 `HIGHEST_PRECEDENCE`)会将符合条件的 Bean 包装进 JDK 动态代理,把方法进入、返回值和异常记录到共享的 `NarrativeContext` 中。

**符合条件的规则:**
- Bean 必须至少实现一个接口
- Bean 的类必须位于已配置的包中
- Spring 框架接口(例如 `BeanFactoryAware`、`InitializingBean`)会被排除

`NarrativeContext` Bean 会自动提供。当 classpath 上存在 `narrativetrace-slf4j` 且 `loggerName` 非空时,它会以该 logger 经由 SLF4J 叙述。定义你自己的 `narrativeContext` Bean 即可覆盖(例如用于 Micrometer 集成——见第 3 节)。

### BPP 顺序

追踪用的 `BeanPostProcessor` 以 `HIGHEST_PRECEDENCE` 运行,这意味着追踪代理是**最内层**的包装。与 Spring 的 `@Async` 代理组合时,层次结构为:

```
Async 代理(外层) → 追踪代理(内层) → 真实 Bean
```

这确保追踪在真正的异步线程上运行,而不是调用方线程。

## 2. 生产环境请求追踪

要在已部署的应用中追踪 HTTP 请求,添加 Servlet 过滤器模块:

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring-web:0.2.1")
```

这会自动将 `NarrativeTraceFilter` 注册为 Spring Bean。该过滤器在每个请求上遵循"重置—执行链—捕获—导出—重置"的生命周期:

1. 重置上下文(清除残留状态)
2. 执行过滤器链(下游 Bean 通过代理被追踪)
3. 捕获累积的追踪树
4. 通过配置的 `TraceExporter` 导出
5. 再次重置

请求处于活动状态期间,过滤器还会填充持久的 MDC 关联字段,包括
`traceId`、`traceName`、`httpMethod`、`httpRoute` 和 `clientIp`。`traceName` 是由追踪 ID
派生的确定性三词别名,便于在日志中进行可读的关联。

### 默认导出器

默认情况下,追踪通过 `Slf4jTraceExporter` 导出(以 INFO 级别将 JSON 写入 SLF4J)。要使用自定义导出器,注册一个 `TraceExporter` Bean:

```java
@Bean
TraceExporter traceExporter() {
    return (tree, requestContext) -> {
        // 发送到你的可观测性平台
    };
}
```

`RequestContext` 提供 `method`、`uri`、`statusCode` 和 `durationMillis`。

### 用户上下文（`RequestContextProvider`）

身份认证与租户信息通常存放在跟踪上下文之外——存放在安全主体、请求头或会话属性中。`RequestContextProvider` 会在每个请求中从请求对象上取用一次，随后 NarrativeTrace 会把 `enduserId`、`sessionId` 和 `tenantId` 打在该请求后续创建的每一个 span 上，并写入 MDC。

该接口对框架的请求类型做了泛型化，好让 API jar 保持零依赖。Servlet 过滤器与 Spring Web 装配都把它绑定到 `HttpServletRequest`——**请把这个绑定明确写出来**：

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
            return null;  // 匿名请求——是"没有"，而不是错误
        }
        return new UserContext(
                principal.getName(),
                request.getRequestedSessionId(),
                request.getHeader("X-Tenant-Id"));
    }
}
```

`NarrativeTraceWebConfiguration` 会自动取用该 Bean——无需其他装配。

> **绑定决定了这个 Bean 能否被找到。** 该提供者按其*绑定后的*类型 `RequestContextProvider<HttpServletRequest>` 解析。声明为裸类型（`implements RequestContextProvider`）或绑定到其他请求类型的 Bean 都不是候选者：Spring 感知泛型的解析不会匹配它，过滤器将在没有用户上下文的情况下运行，并且不会记录任何日志。症状是每个 span 上的 `enduserId`/`tenantId` 字段为空，而不是报错。

返回 `null` 表示"该请求没有身份信息"，这在匿名流量中很正常。抛出异常的提供者也按同样方式处理——请求会在没有用户上下文的情况下继续，因为可观测性绝不能让请求失败。

**不使用 Spring 时**，把同一个实现直接交给过滤器：

```java
var filter = new NarrativeTraceFilter(context, exporter, new SecurityContextProvider());
```

## 3. 跨线程传播(`@Async`)

当被追踪的方法把工作派发到另一个线程时(通过 `@Async`、`CompletableFuture.supplyAsync` 或自定义 executor),追踪上下文必须被显式传播。否则,异步工作会在工作线程上表现为一条断开的追踪。

### 工作原理

NarrativeTrace 使用 [Micrometer context-propagation](https://github.com/micrometer-metrics/context-propagation) 在线程边界之间携带追踪状态。配置分为三部分:

1. **注册 accessor** —— 告诉 Micrometer 如何对 NarrativeTrace 的 `ThreadLocal` 状态做快照与恢复
2. **配置 `TaskDecorator`** —— 包装异步 runnable,使其把快照带到工作线程
3. **覆盖 `NarrativeContext` Bean** —— 把 accessor 接到实际的上下文实例上

### 依赖

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring:0.2.1")
implementation("ai.narrativetrace:narrativetrace-micrometer:0.2.1")
```

### 配置

```java
@Configuration
@EnableNarrativeTrace
@EnableAsync
public class AppConfig {

    @Bean
    NarrativeContext narrativeContext() {
        var tlc = new ThreadLocalNarrativeContext();

        // 注册到 Micrometer, 使 context-propagation 感知 NarrativeTrace
        var accessor = new NarrativeTraceThreadLocalAccessor(tlc);
        ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);

        return tlc; // 当 narrativetrace-slf4j 存在时,SLF4J 叙述会自动挂接
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

### `TaskDecorator`

`TaskDecorator` 是一座桥梁:在调用方线程上捕获上下文,并在工作线程上恢复它。`narrativetrace-spring` 自带了一个 —— `ai.narrativetrace.spring.ContextPropagatingTaskDecorator`,上面的片段直接使用它。它带走两样东西,都在任务被*提交*时捕获:

| 部分 | NarrativeTrace 是否必需? | 用途 |
|---|---|---|
| Micrometer `ContextSnapshot` | **是** | 通过已注册的 `ThreadLocalAccessor` 传播 NarrativeTrace 状态 |
| MDC 的捕获与恢复 | 否 | 传播 SLF4J MDC 以做日志关联(`traceId`、`traceName`、请求元数据等) |

MDC 这一半默认开启,并在任务结束后恢复工作线程自己的 MDC —— 任务抛出异常时同样如此。若你的应用不使用 MDC,或另一个装饰器已经负责它,可以关闭:

```java
executor.setTaskDecorator(ContextPropagatingTaskDecorator.withoutMdc());
```

**依赖。** 该装饰器需要运行时 classpath 上有 `io.micrometer:context-propagation` —— `narrativetrace-spring` 以 `compileOnly` 声明它,因此需要你自己加入(你为 accessor 本就需要的 `narrativetrace-micrometer` 模块会带上它,见上文[依赖](#依赖))。SLF4J 是可选的:当 `org.slf4j.MDC` 不存在时,MDC 传播会自动关闭。

**如果你已经有一个 `TaskDecorator`**,executor 只有一个装饰器插槽 —— 把两者组合,或把 Micrometer 快照加进你自己的装饰器:

```java
public class MyDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        var snapshot = ContextSnapshotFactory.builder().build().captureAll();
        return () -> snapshot.wrap(runnable).run();
    }
}
```

### 验证传播

`@Async` 方法应当在追踪树中显示为子节点,即使它运行在不同的线程上:

```
OrderService.placeOrder(customerId: "C-1234")
  InventoryService.reserve(productId: "SKU-KB", quantity: 2) → ReservedInventory(...)
  PaymentService.charge(customerId: "C-1234", amount: 179.98) → Payment(...)
  NotificationService.sendConfirmation(orderId: "ORD-001") → true    ← 在 async-1 线程上运行
```

它落在哪里取决于任务何时被提交,而不是何时完成:在 `placeOrder` 仍在执行时提交,
它就是 `placeOrder` 的子节点;在 `placeOrder` 返回之后提交,它就是同一条追踪里的
下一个根节点。两种情况下调用方的 `captureTrace()` 都会报告它,与同步日志流已经
叙述的内容一致(ADR-013)。

没有 `TaskDecorator` 时,这个通知调用根本不会出现在追踪中。

### CompletableFuture 延迟退出

当被追踪的方法返回 `CompletableFuture<T>` 时,代理会自动处理延迟完成:

1. 通过 `detachFrame(handle)` 将该帧从活动栈中分离
2. 在 future 上注册一个 `whenComplete` 回调
3. future 完成时,该帧以实际解析出的值完成(而不是 `<pending>`)

这对返回 `CompletableFuture` 的 `@Async` 方法透明生效。无需额外配置——代理会检测返回类型并自动应用延迟退出。

## 4. 测试 Spring Bean

用 JUnit 5 测试被追踪的 Bean 时,完全不需要 Spring 模块——直接使用 `NarrativeTraceProxy` 即可:

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
        // 追踪输出由该扩展自动完成
    }
}
```

这种方式比完整的 Spring 上下文更快,并让你直接控制装配。只有在需要测试 Spring 集成本身(自动装配、BPP 顺序、`@Async` 传播)时才使用 `@SpringBootTest`。

要查看一个启动真实 Spring 上下文、并同时验证自动包装的 Bean 追踪与 `@Async` 线程传播的完整示例,见 [`SpringIntegrationTest`](../../narrativetrace-examples/ecommerce/src/test/java/ai/narrativetrace/examples/ecommerce/SpringIntegrationTest.java)。

## 另请参阅

- [安装指南](安装指南.md) —— 依赖、集成路径、模块选择
- [配置指南](配置指南.md) —— 追踪级别、`@EnableNarrativeTrace` 详解
- [Micronaut 集成指南](Micronaut集成指南.md) —— 本指南的 Micronaut 版本
- [注解指南](注解指南.md) —— `@Narrated`、`@OnError`、`@NotTraced`
