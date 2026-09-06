<!-- source: documentation/micronaut-integration-guide.md blob 6ba6a6f99491 | translated: 2026-08-31 | reviewed: 2026-09-03 -->
# Micronaut 集成指南

[English](../micronaut-integration-guide.md) | [Español](../es/guia-de-integracion-con-micronaut.md) | **简体中文**

本指南介绍如何将 NarrativeTrace 集成到 Micronaut 应用中，从基础的 Bean 追踪到生产环境的 HTTP 请求生命周期。

## 模块

| 模块 | 用途 |
|---|---|
| `narrativetrace-micronaut` | 通过 `BeanCreatedEventListener` 自动包装符合条件的 Bean——在类路径上自动发现 |
| `narrativetrace-micronaut-http` | 用于按请求管理追踪生命周期的响应式 `HttpServerFilter` |

## 1. 基础 Bean 追踪

添加依赖：

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut:0.2.0")
```

在 `application.yml` 中配置基础包：

```yaml
narrativetrace:
  base-packages:
    - com.example.order
    - com.example.payment
```

无需任何启用注解。`@Factory` 和 `BeanCreatedEventListener` 会在类路径上被自动发现。

### 工作原理

`BeanCreatedEventListener<Any>` 会把符合条件的 Bean 包装进 JDK 动态代理，由代理将方法进入、返回值和异常记录到共享的 `NarrativeContext` 中。

**符合条件的规则：**
- Bean 的类必须位于已配置的基础包中
- Bean 必须至少实现一个位于已配置包中的接口
- 没有匹配接口的 Bean 保持原样
- `base-packages` 为空时不包装任何 Bean（安全的默认值）

系统会自动提供一个 `NarrativeContext` Bean（标记为 `@Secondary`）。当 `narrativetrace-slf4j` 位于类路径上且 `loggerName` 非空时，它会接入 SLF4J 事件日志。定义你自己的 `@Bean NarrativeContext` 即可覆盖。

### 配置属性

| 属性 | 类型 | 默认值 | 用途 |
|---|---|---|---|
| `narrativetrace.base-packages` | `List<String>` | `[]` | 用于匹配 Bean/接口的包前缀 |
| `narrativetrace.logger-name` | `String` | `"narrativetrace"` | SLF4J 日志器名称（留空则禁用 SLF4J 接入） |
| `narrativetrace.service-name` | `String` | `""` | 服务标识——打在 Span 上 |
| `narrativetrace.service-version` | `String` | `""` | 服务标识——打在 Span 上 |
| `narrativetrace.environment` | `String` | `""` | 服务标识——打在 Span 上 |

### 覆盖默认上下文

声明你自己的 `@Bean NarrativeContext`，`@Secondary` 默认实现即被替换：

```kotlin
@Factory
class MyConfig {
    @Bean
    @Singleton
    fun narrativeContext(): NarrativeContext {
        // 当 narrativetrace-slf4j 存在时,SLF4J 叙述会自动挂接;
        // pipeline 参数将其路由到自定义 logger 名称。
        return ThreadLocalNarrativeContext(
            NarrativeTraceConfig(), PipelineBootstrap.createDefault("myapp.traces"))
    }
}
```

## 2. 生产环境 HTTP 请求追踪

添加 HTTP 过滤器模块：

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.0")
```

响应式 HTTP 过滤器（`HttpServerFilter`）位于类路径上即会自动注册——无需额外配置。

### 过滤器生命周期

1. 重置上下文（清除残留状态）
2. 打上请求元数据（HTTP 方法、路径、远程地址）
3. 通过 `Mono.from(chain.proceed(request))` 继续执行过滤器链
4. 捕获累积的追踪树
5. 通过已配置的 `TraceExporter` 导出
6. 再次重置（通过 `doFinally` 清理）

过滤器匹配所有路径（`@Filter("/**")`）。

请求处于活动状态期间，过滤器会填充持久的 MDC 关联字段，包括
`traceId`、`traceName`、`httpMethod`、`httpRoute` 和 `clientIp`。`traceName` 是由追踪 ID 派生的
确定性三词别名，便于在日志中进行可读的关联。

### 默认导出器

默认情况下，追踪通过 `Slf4jTraceExporter` 导出（以 INFO 级别向 SLF4J 输出 JSON）。导出日志器的名称由已配置的 `loggerName` 属性派生：`<loggerName>.export`（例如 `narrativetrace.export`）。

要使用自定义导出器，请注册一个 `TraceExporter` Bean：

```kotlin
@Factory
class MyExporterConfig {
    @Bean
    @Singleton
    fun traceExporter(): TraceExporter = TraceExporter { tree, requestContext ->
        // 发送到你的可观测性平台
    }
}
```

`@Secondary` 默认导出器会被自动替换。

### 用户上下文（`RequestContextProvider`）

身份认证与租户信息通常存放在跟踪上下文之外——存放在请求头、安全主体或会话属性中。`RequestContextProvider` 会在每个请求中从请求对象上取用一次，随后 NarrativeTrace 会把 `enduserId`、`sessionId` 和 `tenantId` 打在该请求后续创建的每一个 span 上，并写入 MDC。

Micronaut 有**自己的**提供者类型 `ai.narrativetrace.micronaut.http.RequestContextProvider`，以 Micronaut 的 `HttpRequest` 为参数类型。把它声明为单例，过滤器就会注入它：

```kotlin
import ai.narrativetrace.micronaut.http.RequestContextProvider
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton

@Singleton
class SecurityContextProvider : RequestContextProvider {
    override fun resolveUserContext(request: HttpRequest<*>): RequestContextProvider.UserContext? {
        val userId = request.headers["X-User-Id"] ?: return null  // 匿名——是"没有"，而不是错误
        return RequestContextProvider.UserContext(
            enduserId = userId,
            sessionId = request.headers["X-Session-Id"],
            tenantId = request.headers["X-Tenant-Id"],
        )
    }
}
```

> **要导入 Micronaut 的类型，而不是 API jar 里的那个。** `ai.narrativetrace.api.export.RequestContextProvider<REQUEST>` 是另一个接口——它是泛型的，并由 servlet 与 Spring Web 集成绑定到 `HttpServletRequest`。实现*那个*接口的 Bean 在这里不是候选者：过滤器不会注入任何东西，将在没有用户上下文的情况下运行，也不会记录任何日志。症状是每个 span 上的 `enduserId`/`tenantId` 字段为空，而不是报错。这两个类型是有意分开的，参见 [API Surface](../api-surface.md)。

返回 `null` 表示"该请求没有身份信息"，这在匿名流量中很正常。抛出异常的提供者也按同样方式处理——请求会在没有用户上下文的情况下继续，因为可观测性绝不能让请求失败。

### 错误处理

- 导出器故障会被静默吞掉——可观测性绝不能导致请求失败
- 空追踪（没有任何被追踪的方法被调用）会完全跳过导出
- 过滤器链出错时会以状态码 500 触发导出，随后错误照常向上传播
- `doFinally` 保证无论成功、出错还是取消，上下文都会被重置

## 3. 与 Spring 的架构对比

| 关注点 | Spring | Micronaut |
|---|---|---|
| 启用机制 | `@EnableNarrativeTrace` 注解 | 类路径上自动发现的 `@Factory` |
| 配置方式 | 注解属性 | 通过 `@ConfigurationProperties` 读取 `application.yml` |
| Bean 包装 | `BeanPostProcessor` | `BeanCreatedEventListener<Any>` |
| 覆盖默认 Bean | 定义名为 `narrativeContext` 的 Bean | 声明 `@Bean NarrativeContext`（替换 `@Secondary`） |
| HTTP 过滤器 | Servlet `Filter` + Spring `@Configuration` | 响应式 `HttpServerFilter`（`@Filter("/**")`） |
| 覆盖导出器 | `ObjectProvider<TraceExporter>` | `@Secondary` 提供默认实现，用户的 `@Bean` 覆盖之 |
| 延迟解析 | `BeanFactoryAware` | `ApplicationContext` + ThreadLocal 重入保护 |

### 为什么监听器使用 ApplicationContext

`BeanCreatedEventListener<Any>` 会在**所有** Bean 创建时触发，其中包括监听器自身的依赖（`NarrativeContext`、`NarrativeTraceProperties`）。若通过构造函数注入这些 Bean，会造成循环依赖或栈溢出。因此监听器从 `ApplicationContext` 延迟解析它们，并借助 `ThreadLocal` 重入保护在依赖解析期间跳过包装。

## 4. 测试 Micronaut Bean

在 JUnit 5 中测试被追踪的 Bean 时，你并不需要 Micronaut 模块——直接使用 `NarrativeTraceProxy` 即可：

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

只有当你需要测试 Micronaut 集成本身（自动装配、Bean 监听器包装、HTTP 过滤器生命周期）时，才使用 `@MicronautTest`。

## 另请参阅

- [安装指南](安装指南.md)——依赖、集成路径、模块选择
- [配置指南](配置指南.md)——追踪级别、Micronaut 配置细节
- [Spring 集成指南](Spring集成指南.md)——本指南的 Spring 对应版本
- [注解指南](注解指南.md)——`@Narrated`、`@OnError`、`@NotTraced`
