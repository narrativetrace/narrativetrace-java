# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# 演示启动器的按场景接线说明,简体中文版:每个场景的追踪是如何配置的。
# 基准表(英文)是 wiring.awk;两表的键完全一致(即示例打印的场景标题原文),
# `demoWiringCheck` 构建任务对键做双向一致性检查。
#
# 标识符、类名与属性名保持原文 — 与翻译后的追踪视图一致,代码保留其原始名称。

BEGIN {
  # ---- 渲染器如何选择 — 在第一个渲染小节处打印一次 ----

  wiring["--- Trace tree ---"] = \
    "渲染器无需配置:没有默认值、没有注册表、没有设置项。捕获产生一个 TraceTree,\n" \
    "你调用想要的渲染器即可 — 这里只有一行代码,\n" \
    "new IndentedTextRenderer().render(trace);ProseRenderer、MarkdownRenderer 和\n" \
    "下面的图表渲染器都是同样的用法。NarrativeRenderer 只有一个方法\n" \
    "(String render(TraceTree)),所以你自己的渲染器就是一个 lambda。\n" \
    "实时的 → ← !! 行根本不是渲染器:那是 DualPathPipeline 里的\n" \
    "Slf4jTraceEventListener 在事件发生时逐条格式化 — 不写任何渲染代码就能得到的\n" \
    "唯一视图,也正是日志工具所摄取的内容。\n" \
    "配置只在一个地方选择渲染器,即测试写出的追踪文件:\n" \
    "-Dnarrativetrace.output=true 搭配 -Dnarrativetrace.format=markdown|text|mermaid|\n" \
    "plantuml(默认 markdown;Gradle 插件的 narrativeTrace { format = … } 设置的\n" \
    "是同一个属性)。"

  # ---- ecommerce(Spring AOP、注解、异步传播) ----

  wiring["Scenario 1: Successful Order + Async Notification"] = \
    "接线:ECommerceConfig 上的 @EnableNarrativeTrace — Spring 在启动时把每个服务\n" \
    "bean 包进追踪代理,因此 DefaultOrderService 完全不含追踪代码。树中的 // 行来自\n" \
    "OrderService.placeOrder 上的 @Narrated;cardToken 打印为 [REDACTED],因为该参数\n" \
    "带有 @NotTraced。异步通知经由 taskExecutor bean 上的\n" \
    "ContextPropagatingTaskDecorator 汇入同一条追踪。"

  wiring["Scenario 2: Payment Failure — Inventory Leak Bug"] = \
    "接线:与场景 1 完全相同 — 没有为捕获或记录这次失败添加任何东西。代理记录抛出的\n" \
    "PaymentDeclinedException 并自行回卷树;!! 之后括号里的文字来自\n" \
    "PaymentService.charge 上的 @OnError。"

  wiring["Scenario 3: Flaky External Service"] = \
    "接线:这里没有 Spring — NarrativeTraceProxy.trace(impl,\n" \
    "NotificationService.class, context) 在运行时用 JDK 动态代理包装一个普通对象。\n" \
    "与上面的 bean 共用同一个 NarrativeContext,所以两次调用落在同一条追踪里:\n" \
    "注解和容器只是便利,不是前提。"

  wiring["Scenario 4: Unknown Customer"] = \
    "接线:未改动 — 这些行由同样的 Spring 代理产生,只是渲染方式不同。\n" \
    "CustomerService.findCustomer 上的 @OnError 提供括号内的消息;前缀里 MDC 的\n" \
    "traceId 由示例设置,与 Micrometer 的做法一致。"

  wiring["Scenario 5: Out of Stock"] = \
    "接线:同样的 Spring 代理;InventoryService.reserve 上的 @OnError 提供括号内的\n" \
    "消息。下方的图是同一棵已捕获的树交给 PlantUmlSequenceDiagramRenderer、再由\n" \
    "AsciiSequenceDiagram 绘制 — 不是重新运行。"

  wiring["Scenario 6: Explicit Async Trace Capture"] = \
    "接线:taskExecutor bean 上的 ContextPropagatingTaskDecorator(ECommerceConfig)\n" \
    "把 MDC 和叙事上下文带到工作线程;narrativeContext() 中注册的 Micrometer\n" \
    "NarrativeTraceThreadLocalAccessor 让该 executor 运行的一切都自动获得这种传播。"

  # ---- clarity(普通代理;被测变量是命名,不是配置) ----

  wiring["Scenario 1: Guest Books a Room (Excellent Naming)"] = \
    "接线:没有 Spring、没有注解 — 每个服务都用 NarrativeTraceProxy.trace(impl,\n" \
    "Iface.class, context) 包装,DualPathPipeline(new Slf4jTraceEventListener())\n" \
    "把事件变成实时行。四个场景接线完全相同;变化的只有命名质量。"

  wiring["Scenario 2: Booking via Manager (Adequate Naming)"] = \
    "接线:context.reset() 之后同样的代理装配 — 这次只追踪一个接口。场景之间配置\n" \
    "没有任何变化;变的是名字。"

  wiring["Scenario 3: Legacy Data Processing (Poor Naming)"] = \
    "接线:还是同样的代理装配。追踪器只能报告代码对自己的称呼:进去是泛化的名字,\n" \
    "出来就是泛化的追踪 — 任何配置都救不了这个场景。"

  wiring["Scenario 4: Guest Repository (Cohesion Mismatch)"] = \
    "接线:同样的代理装配,一个仓储接口。内聚性是事后依据捕获的树来评判的,\n" \
    "所以下面这些互不相关的调用就是分析器的全部依据。"

  wiring["CLARITY ANALYSIS REPORT"] = \
    "接线:上面捕获的四棵树被传给 ClarityAnalyzer,ClarityReportRenderer 为每个场景\n" \
    "打印一份报告外加套件摘要。分析读取的是已捕获的追踪 — 没有额外插桩,\n" \
    "代码也没有第二次运行。"

  # ---- minecraft(两半接线完全相同;只有词汇不同) ----

  wiring["Refactored: Player Joins World"] = \
    "接线:没有 Spring、没有注解 — 每个接口都用 NarrativeTraceProxy.trace(impl,\n" \
    "Iface.class, context) 包装,DualPathPipeline(new Slf4jTraceEventListener())\n" \
    "把事件变成普通的 SLF4J 日志行。"

  wiring["Unrefactored: Player Joins World"] = \
    "接线:与上面的装配逐字节相同 — 同样的代理、同样的管道、reset() 之后同一个\n" \
    "上下文。不同的只有类名和方法名,而这正是重点所在。"

  # ---- library(Kotlin,同一套代理 API,注解标在接口上) ----

  wiring["Scenario 1: Successful Book Borrow"] = \
    "接线:Kotlin,没有 Spring — NarrativeTraceProxy.trace(...) 包装 CatalogService、\n" \
    "MemberService 和 LendingService。LendingService.borrowBook 上的 @Narrated 提供\n" \
    "// 行;cardNumber 因参数上的 @NotTraced 打印为 [REDACTED]。"

  wiring["Scenario 2: Book Unavailable"] = \
    "接线:context.reset() 之后同样的三个代理。BookUnavailableException 由\n" \
    "LendingService 自己抛出,代理在返回途中记录它 — 这条路径上没有任何错误处理\n" \
    "代码,它变化时也没有东西需要同步维护。"
}
