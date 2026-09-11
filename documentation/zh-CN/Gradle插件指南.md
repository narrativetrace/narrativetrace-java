<!-- source: documentation/gradle-plugin-guide.md blob cf519cb92da0 | translated: 2026-09-09 | reviewed: - -->
# NarrativeTrace Gradle 插件指南

[English](../gradle-plugin-guide.md) | [Español](../es/guia-del-plugin-de-gradle.md) | **简体中文**

`ai.narrativetrace` Gradle 插件是在 Gradle 项目中使用 NarrativeTrace 的推荐方式。它负责依赖管理、编译器参数、测试配置和质量门禁——全部通过一个 DSL 配置块完成。

## 目录

- [快速开始](#快速开始)
- [插件自动完成的工作](#插件自动完成的工作)
- [属性](#属性) — [enabled](#enabled) | [mode](#mode) | [testFramework](#testframework) | [scope](#scope) | [format](#format) | [tracingLevel](#tracinglevel) | [outputDir](#outputdir) | [approval](#approval) | [approvedDir](#approveddir)
- [Modules 块](#modules-块)
- [Agent 块](#agent-块)
- [Clarity 块](#clarity-块)
- [任务](#任务) — [clarityCheck](#claritycheck) | [clarityScan](#clarityscan) | [glossaryScan](#glossaryscan) | [approveNarratives](#approvenarratives)
- [环境要求](#环境要求)
- [版本解析](#版本解析)
- [常用配置示例](#常用配置示例)
- [Groovy DSL](#groovy-dsl)
- [校验](#校验)
- [完整 DSL 参考](#完整-dsl-参考)

## 快速开始

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

就这么简单。运行 `./gradlew test`,追踪输出就会出现在 `build/narrativetrace/` 中。

你无需添加任何 JUnit 依赖。在默认的 `testFramework = "junit5"` 下,插件会把测试任务切换到 JUnit Platform,*并且*把 Jupiter 引擎(`org.junit.jupiter:junit-jupiter-engine`,固定为 NarrativeTrace 所测试的版本)加到 `testRuntimeOnly`,因为没有引擎时该平台拒绝启动。你仍然可以声明自己的 JUnit 版本——Gradle 的冲突解析会选择两者中较高的那个。

## 插件自动完成的工作

| 操作 | 详情 |
|---|---|
| 添加 `-parameters` 编译器参数 | 作用于所有 `JavaCompile` 任务,若已存在则跳过 |
| 添加依赖 | 依据 `mode`、`modules` 和 `testFramework`;版本从插件 JAR 中自动检测 |
| 添加 JUnit Platform 引擎 | 仅当 `testFramework = "junit5"`:`testRuntimeOnly org.junit.jupiter:junit-jupiter-engine:5.11.4`,让第一次 `gradle test` 能够运行,而不是以 *"Cannot create Launcher without at least one TestEngine"* 失败。JUnit 4 不会获得引擎——它并未切换到该平台 |
| 设置测试 JVM 属性 | `narrativetrace.output=true`、`narrativetrace.outputDir`,以及可选的 `narrativetrace.format`、`narrativetrace.level`、术语表属性对(`glossary=true`)和审批属性对(`approval=true`) |
| 注册 `clarityCheck` 任务 | 读取 `clarity-results.json`,强制执行阈值,并接入 `check` 生命周期 |
| 注册 `clarityScan` 任务 | 基于编译后的类进行独立的清晰度分析(无需运行测试) |
| 注册 `glossaryScan` 任务 | 基于编译后的类独立采集术语表,包括注解模板 |
| 注册 `approveNarratives` 任务 | 将已审阅的 `*.received.nt` 叙事提升为 `*.approved.nt` 基线 |
| 配置 Agent 的 JVM 参数 | 当 `mode = "agent"` 时:解析 Agent JAR,并向 Test 任务添加 `-javaagent` |

## 属性

### `enabled`

控制插件是否生效。当为 `false` 时,不注册任何任务、不添加任何依赖、不设置任何编译器参数。

```kotlin
narrativeTrace {
    enabled.set(false)  // 在此子项目中禁用
}
```

默认值:`true`

### `mode`

选择拦截策略。它决定插件添加哪个核心库依赖。

| 模式 | 添加的依赖(除 core + clarity + diagrams + 测试框架之外) |
|---|---|
| `proxy` | `narrativetrace-proxy` — JDK 动态代理,基于接口 |
| `agent` | `narrativetrace-agent` — 字节码插桩,无需接口 |
| `spring` | `narrativetrace-spring` + `narrativetrace-proxy` — 通过 Spring BeanPostProcessor 自动包装 |

默认值:`"proxy"`

**proxy 模式**最简单:在测试代码中用 `NarrativeTraceProxy.trace()` 包装服务。

**agent 模式**会额外创建一个 `narrativeTraceAgent` 配置,解析 Agent JAR,并向所有 `Test` 任务添加 `-javaagent`。使用 `agent { }` 块指定要插桩的包。

**spring 模式**添加 Spring BeanPostProcessor,自动包装符合条件的 Bean。在你的配置类上使用 `@EnableNarrativeTrace`。

### `testFramework`

| 取值 | 添加的依赖 |
|---|---|
| `"junit5"` | `narrativetrace-junit5` |
| `"junit4"` | `narrativetrace-junit4` |

默认值:`"junit5"`

### `scope`

控制库依赖被添加到哪个 Gradle 配置。

| Scope | 库依赖 | 测试框架依赖 |
|---|---|---|
| `"test"` | `testImplementation` | `testImplementation` |
| `"production"` | `implementation` | `testImplementation`(始终如此) |

默认值:`"test"`

在运行中的应用里部署 NarrativeTrace 时(例如配合 Servlet 过滤器做请求级追踪),使用 `"production"`。无论 scope 取何值,测试框架依赖始终保留在 `testImplementation` 上。

### `format`

追踪文件的输出格式。只有在显式设置时才会转发给测试任务——如果省略,JUnit 扩展会使用它自己的默认值(markdown)。

| 取值 | 说明 |
|---|---|
| `"markdown"` | 带 Mermaid 图表、适合人类阅读的 Markdown |
| `"text"` | 带缩进的纯文本 |
| `"mermaid"` | 仅 Mermaid 时序图 |
| `"plantuml"` | 仅 PlantUML 时序图 |

没有默认约定——省略时交由 JUnit 扩展决定。

### `tracingLevel`

控制追踪捕获的细节程度。只有在显式设置时才会转发给测试任务。

| 取值 | 行为 |
|---|---|
| `"OFF"` | 不捕获任何追踪 |
| `"ERRORS"` | 仅捕获异常路径 |
| `"SUMMARY"` | 根入口、最深叶节点以及完整的异常链 |
| `"NARRATIVE"` | 完整调用流,但不记录参数值 |
| `"DETAIL"` | 完整调用流,包含参数值和返回值 |

没有默认约定——省略时交由运行时决定。

### `outputDir`

存放追踪文件、清晰度报告和图表的目录。

默认值:`layout.buildDirectory.dir("narrativetrace")`(即 `build/narrativetrace/`)

### `approval`

结构叙事的审批模式。为 `true` 时,插件把 `narrativetrace.approval=true` 和 `narrativetrace.approvedDir` 转发给测试任务:**通过**的测试若其追踪结构与已提交的 `*.approved.nt` 基线不同,将以可读的 diff 失败,并把当前结构作为 `*.received.nt` 写到基线旁供审阅。用 [`approveNarratives`](#approvenarratives) 任务接受有意的变更。

默认值:`false`

```kotlin
narrativeTrace {
    approval.set(true)
}
```

### `approvedDir`

已提交叙事基线的目录,布局为 `<dir>/<TestClassSimpleName>/<artifact_name>.approved.nt` —— 与其他所有按测试生成的产物遵循相同的类目录与产物身份规则,因此运行多次的方法(参数化、重复)每次调用都有自己的基线。

默认值:`layout.projectDirectory.dir("src/test/narratives")`

## Modules 块

针对额外 NarrativeTrace 模块的细粒度可选启用。全部默认为 `false`。

```kotlin
narrativeTrace {
    modules {
        slf4j.set(true)        // narrativetrace-slf4j
        micrometer.set(true)   // narrativetrace-micrometer
        servlet.set(true)      // narrativetrace-servlet
        springWeb.set(true)    // narrativetrace-spring-web + narrativetrace-servlet
    }
}
```

| Flag | 构件 | 说明 |
|---|---|---|
| `slf4j` | `narrativetrace-slf4j` | 将追踪事件路由到 SLF4J/logback |
| `micrometer` | `narrativetrace-micrometer` | 通过 Micrometer context-propagation 实现跨线程追踪传播 |
| `servlet` | `narrativetrace-servlet` | 提供按请求管理追踪生命周期的 Servlet 过滤器 |
| `springWeb` | `narrativetrace-spring-web` + `narrativetrace-servlet` | Servlet 过滤器的 Spring 自动配置;会自动添加 servlet 模块 |

在 `mode` 不是 `"spring"` 时设置 `springWeb` 会产生一条警告(但不会失败)。

## Agent 块

仅在 `mode = "agent"` 时相关。配置字节码 Agent 要插桩哪些包。

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        packages.set(listOf("com.example.app", "com.example.shared"))
    }
}
```

当 packages 为空(默认值)时,Agent 会插桩所有类。各个包在 `-javaagent` 参数中以 `;` 连接。

Agent JAR 在测试执行时才从专用的 `narrativeTraceAgent` Gradle 配置中延迟解析。

## Clarity 块

配置 `clarityCheck` 质量门禁。

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.80)       // 任一场景得分低于 0.80 即失败
        maxHighIssues.set(0)     // 任一场景存在 HIGH 问题即失败
        maxSuiteIssues.set(0)    // 存在任何套件级问题(如词汇违规)即失败
        warnOnly.set(true)       // 只记录警告而不失败
    }
}
```

### `minScore`

最低总体清晰度评分(0.0–1.0)。任何低于该阈值的场景都会使构建失败。

默认值:`0.0`(无门禁)

### `maxHighIssues`

每个场景允许的 HIGH 严重级别问题的最大数量。超过即导致构建失败。

默认值:`Integer.MAX_VALUE`(无门禁)

### `maxSuiteIssues`

套件级问题的最大允许数量——即属于整个运行而非某个具体场景的问题,例如术语表采集产生的 `non-canonical-term` 词汇违规。超过即导致构建失败;低于阈值时仅记录为警告。词汇违规仅在运行前已存在提交的 `glossary.json` 时才会被报告。

默认值:`Integer.MAX_VALUE`(仅咨询)

### `warnOnly`

为 `true` 时,超出阈值只产生警告,而不会导致构建失败。

默认值:`false`

## 任务

### `clarityCheck`

读取 `build/narrativetrace/clarity-results.json`(由 JUnit 扩展在 `test` 期间生成),并强制执行配置的阈值。

- **依赖于**:`test`
- **接入**:`check`(随 `./gradlew check` 自动运行)
- **静默跳过**:当 `clarity-results.json` 不存在时(例如没有运行任何测试)

### `clarityScan`

在不运行测试的情况下分析编译后类的命名清晰度。使用反射扫描 class 文件并生成清晰度报告。

- **依赖于**:`classes`
- **Classpath**:`testRuntimeClasspath`(需要 clarity 模块)
- **参数**:`--classes-dir` 和 `--output-dir`,由插件配置推导
- **输出**:`outputDir` 中的 `clarity-scan-report.md` 和 `clarity-scan-results.json` —— 刻意区别于测试运行产物(`clarity-report.md` / `clarity-results.json`),因此扫描绝不会覆盖 `clarityCheck` 门禁所读取的文件
- **范围**:公共和包级私有类型;私有嵌套类、匿名类、局部类和 lambda 类作为实现细节被跳过

独立运行:

```bash
./gradlew clarityScan
```

### `glossaryScan`

基于编译后的类采集领域术语表,无需运行测试。
这是**唯一**会采集 `@Narrated` / `@OnError` 模板的模式:
捕获到的追踪中,叙述文本已经插入了参数的运行时值,
若从那里采集,就会把运行时数据写进一个纳入版本控制的文件。

- **依赖于**:`classes`
- **Classpath**:`testRuntimeClasspath`(需要术语表模块)
- **参数**:`--classes-dir`(`build/classes/java/main`)、`--glossary-dir`(仓库根目录)、`--output-dir` 来自插件配置

```bash
./gradlew glossaryScan
```

若想改为在测试运行期间采集——来自真实追踪、不含模板——在扩展上启用它:

```kotlin
narrativeTrace {
    glossary.set(true)
}
```

这会设置 `narrativetrace.glossary=true`,并把 `narrativetrace.glossaryDir`
指向仓库根目录。它默认关闭,因为它会把 `glossary.json` / `glossary.md`
写到构建目录之外。

翻译后的追踪视图不是构建任务:把术语表模块的 `TranslationSubscriber` 挂到事件管道上(locale + 提交的 `glossary.json`,通过 `narrativetrace.glossary.path` 或 classpath 定位),每次运行——无论测试还是生产——都会实时输出翻译流:写入 `narrativetrace.i18n.<locale>` logger,或写为按追踪拆分的 Markdown 文件。早先的 `translateTraces` 任务和 `translationLocales` 属性已被这种管道形态取代并移除。

### `approveNarratives`

在[审批模式](#approval)下接受有意的结构变更:把 `approvedDir` 下每个已审阅的 `*.received.nt` 文件提升为其 `*.approved.nt` 基线。

```bash
./gradlew approveNarratives
```

- **分组**:`verification`
- 随时运行都安全 —— 每提升一个基线打印 `Approved: <路径>`;没有可提升的文件时打印 `No received narratives to approve.`
- 从不运行测试:先审阅 received 文件,再批准,然后重新把套件跑绿

## 环境要求

插件在被应用时检查运行环境,并以一行文字写明要求后失败,而不是让构建在稍后撞上
一个看不懂的错误:

- **Gradle 8.0 或更高。** 针对 8.14.2 开发与测试。
- **Java 17 或更高** —— 构建设置了 toolchain 时以其为准,否则取运行 Gradle 的
  JVM。NarrativeTrace 用 Java 17 编写(record、密封接口、模式匹配 switch),
  因此这是语言层面的要求,而非偏好。

`enabled.set(false)` 时两项检查都不会运行;插件无法解析的版本字符串一律视为可
接受 —— 猜测绝不该拦住别人的构建。

## 版本解析

插件在运行时从插件 JAR 中自动检测自身版本,所有受管理的 NarrativeTrace 依赖都使用该版本。

如果无法检测到版本(例如从源码运行且没有 properties 文件),插件会回退到 `0.0.0-unknown`。

若要固定为其他版本——例如对本地 `-SNAPSHOT` 做 dogfooding,而其库版本领先于插件内嵌的版本——请设置 `libraryVersion`：

```kotlin
narrativeTrace {
    libraryVersion.set("0.2.0-SNAPSHOT")
}
```

设置后,所有受管理的 NarrativeTrace 依赖都会解析为该版本；未设置时(默认),使用内嵌版本,行为保持不变。

## 常用配置示例

### 仅在测试中追踪(默认)

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

所有依赖都添加到 `testImplementation`。生产代码的 classpath 上没有任何 NarrativeTrace 类。

### 使用 Servlet 过滤器的生产环境追踪

```kotlin
narrativeTrace {
    scope.set("production")
    mode.set("spring")
    modules {
        springWeb.set(true)
        slf4j.set(true)
    }
}
```

将依赖添加到 `implementation`,使 Servlet 过滤器和 Spring 自动配置在运行时可用。

### 基于 Agent 的插桩

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        packages.set(listOf("com.example.app"))
    }
}
```

插件会创建 `narrativeTraceAgent` 配置,解析 Agent JAR,并向所有 Test 任务添加 `-javaagent:path/to/agent.jar=com.example.app`。

### 带严格清晰度门禁的 CI

```kotlin
narrativeTrace {
    tracingLevel.set("NARRATIVE")
    clarity {
        minScore.set(0.80)
        maxHighIssues.set(0)
    }
}
```

如果任一场景的清晰度低于 0.80,或存在任何 HIGH 问题,`./gradlew check` 就会失败。

### 审批测试的叙事

```kotlin
narrativeTrace {
    approval.set(true)
}
```

第一次运行把每个场景的无值结构写为 `src/test/narratives/<TestClass>/<场景>.received.nt` 并失败;审阅后运行 `./gradlew approveNarratives`,再提交 `*.approved.nt` 文件。此后,通过的测试中任何结构漂移 —— 新增调用、删除调用、结果种类变化 —— 都会以可读的 diff 使构建失败,直到被明确批准。基线不含值,因此绝不泄漏运行时数据,并且不受纯数据变化的影响。

### 在子项目中禁用

```kotlin
narrativeTrace {
    enabled.set(false)
}
```

什么都不会发生——没有依赖、没有任务、没有编译器参数。

### 消费本地检出（composite build）

在制品尚未发布到你能解析的仓库之前——或者当你想拿 NarrativeTrace 的某个改动对着自己的代码试一试时——把你的项目指向同级目录下的检出。这需要**两个**部分,缺一不可:

```kotlin
// settings.gradle.kts
pluginManagement {
    // 1. 解析插件本身:`id("ai.narrativetrace")` 无需版本号。
    includeBuild("../narrative-trace-java")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// 2. 替换插件替你添加的 `ai.narrativetrace:*` 库坐标。没有这一行,
//    构建会在解析那些明明就在磁盘上的制品时失败。
includeBuild("../narrative-trace-java")

rootProject.name = "my-app"
```

```kotlin
// build.gradle.kts
plugins {
    java
    id("ai.narrativetrace")  // 无版本号——由被包含的构建提供
}

repositories { mavenCentral() }
```

为什么两个都要:`pluginManagement { includeBuild(...) }` 与顶层的 `includeBuild(...)` 是两套不同的机制。前者让插件的 *marker* 可解析;后者让 Gradle 把 `DependencyConfigurator` 添加的*库*坐标(`ai.narrativetrace:narrativetrace-core` 等)替换为项目依赖。只写前者,插件能应用,构建随后会在依赖解析时失败。

这是 Gradle 的常规行为,并非 NarrativeTrace 的特殊之处;但正是插件把那些库坐标放进了你的构建,所以你会在这里遇到它。

有两点值得知道:

- **无需发布任何东西。** 不要运行 `publishToMavenLocal`:composite 替换发生在解析之前,本地发布只会多出一份可能遮蔽你改动的陈旧副本。
- **版本不必一致。** 替换按 group 与模块名进行,因此被包含构建的 `0.2.0-SNAPSHOT` 能满足插件请求的任何版本。如果你更愿意解析真实制品、跳过 composite,请改用 `libraryVersion`——参见[版本解析](#版本解析)。

### 多项目设置

只在包含测试的子项目中应用插件:

```kotlin
// settings.gradle.kts
rootProject.name = "my-app"
include("core", "web", "shared")
```

```kotlin
// core/build.gradle.kts
plugins {
    java
    id("ai.narrativetrace")
}
```

每个子项目都会获得自己的 `clarityCheck` 和 `clarityScan` 任务。

## Groovy DSL

上面的示例全部使用 Kotlin DSL。等价的 Groovy 写法:

```groovy
plugins {
    id 'ai.narrativetrace' version '0.2.1'
}

narrativeTrace {
    mode = 'proxy'
    testFramework = 'junit5'
    scope = 'test'

    modules {
        slf4j = true
    }

    clarity {
        minScore = 0.80
        maxHighIssues = 0
    }
}
```

## 校验

插件在配置阶段(`afterEvaluate` 内)校验所有字符串属性。无效值会产生清晰的错误信息:

```
> Invalid narrativeTrace mode 'invalid'. Valid values: proxy, agent, spring
> Invalid narrativeTrace scope 'compile'. Valid values: test, production
> Invalid narrativeTrace format 'xml'. Valid values: markdown, text, mermaid, plantuml
> Invalid narrativeTrace tracingLevel 'VERBOSE'. Valid values: OFF, ERRORS, SUMMARY, NARRATIVE, DETAIL
```

## 完整 DSL 参考

可直接复制粘贴的起点,展示了每一个属性:

```kotlin
narrativeTrace {
    enabled.set(true)                          // 默认: true
    mode.set("proxy")                          // "proxy" (默认) | "agent" | "spring"
    testFramework.set("junit5")               // "junit5" (默认) | "junit4"
    scope.set("test")                          // "test" (默认) | "production"
    format.set("markdown")                     // "markdown" | "text" | "mermaid" | "plantuml"
    tracingLevel.set("DETAIL")                 // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
    outputDir.set(layout.buildDirectory.dir("narrativetrace"))
    glossary.set(false)                        // 默认: false — 套件结束时采集 glossary.json
    approval.set(false)                        // 默认: false — 将结构与已提交的基线进行核对
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))
    // libraryVersion.set("0.2.0-SNAPSHOT")    // 默认:插件内嵌版本 —— 覆盖它以对 snapshot 做 dogfooding

    modules {                                  // 细粒度可选启用 (全部默认 false)
        slf4j.set(false)
        micrometer.set(false)
        servlet.set(false)
        springWeb.set(false)                   // 隐含启用 servlet
    }

    agent {                                    // 仅在 mode = "agent" 时相关
        packages.set(listOf("com.example.app"))
    }

    clarity {
        minScore.set(0.80)                     // 默认: 0.0 (无门禁)
        maxHighIssues.set(0)                   // 默认: Integer.MAX_VALUE (无门禁)
        maxSuiteIssues.set(0)                  // 默认: Integer.MAX_VALUE (仅咨询)
        warnOnly.set(false)                    // 默认: false
    }
}
```

## 另请参阅

- [安装指南](安装指南.md) — 不使用插件的手动设置、各集成路径
- [配置指南](配置指南.md) — 追踪级别,JUnit/Spring/SLF4J 配置
- [清晰度指南](清晰度指南.md) — 评分模型、NLP 组件、清晰度报告格式
- [Spring 集成指南](Spring集成指南.md) — `@EnableNarrativeTrace`、`@Async` 传播、Servlet 过滤器
- [注解指南](注解指南.md) — `@Narrated`、`@OnError`、`@NotTraced`、`@NarrativeSummary`
