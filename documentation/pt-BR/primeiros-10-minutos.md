<!-- source: documentation/first-10-minutes.md blob d65efd810272 | translated: 2026-09-11 | reviewed: - -->
# Veja um trace em 60 segundos

[English](../first-10-minutes.md) | Español | **Português** | [简体中文](../zh-CN/前10分钟.md)

Sem instruções de log, sem framework de teste, sem arquivo para abrir
depois: um `main` de Java simples, uma chamada, e o trace aparece no seu
terminal. Tudo abaixo foi executado de verdade contra os artefatos
publicados no Maven Central — a saída está colada, não imaginada.

## 1. Projeto novo, adicione a dependência

Java 17+. Dois artefatos — o núcleo do runtime e o proxy JDK que encapsula
uma interface simples:

```kotlin
// build.gradle.kts
plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.narrativetrace:narrativetrace-core:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.2.1")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")   // sem isso, os traces mostram arg0, arg1
}

application {
    mainClass.set("com.example.orders.Main")
}
```

## 2. O programa

Uma interface e uma implementação — o proxy encapsula a interface, então
`OrderService` precisa de uma:

```java
// src/main/java/com/example/orders/OrderService.java
package com.example.orders;

public interface OrderService {
    String placeOrder(String customerId, String productId, int quantity);
}
```

```java
// src/main/java/com/example/orders/DefaultOrderService.java
package com.example.orders;

public class DefaultOrderService implements OrderService {
    @Override
    public String placeOrder(String customerId, String productId, int quantity) {
        return "ORD-" + customerId + "-" + productId + "-" + quantity;
    }
}
```

```java
// src/main/java/com/example/orders/Main.java
package com.example.orders;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;

public class Main {
    public static void main(String[] args) {
        var context = new ThreadLocalNarrativeContext();
        OrderService service = NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, context);

        service.placeOrder("C-1234", "SKU-KB", 2);

        System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
        context.reset();
    }
}
```

`ThreadLocalNarrativeContext` é onde um trace se acumula.
`NarrativeTraceProxy.trace(...)` encapsula a implementação real por trás da
interface, então toda chamada através de `service` é capturada.
`captureTrace()` devolve a árvore pronta; `IndentedTextRenderer` a
transforma em texto.

## 3. Execute

Ainda não existe `./gradlew` neste diretório — gere o wrapper uma vez
(precisa do Gradle instalado; veja [gradle.org/install](https://gradle.org/install/)):

```bash
gradle wrapper
```

```bash
./gradlew run
```

```text
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-KB", quantity: 2) → "ORD-C-1234-SKU-KB-2" — 3ms
```

Essa é a saída real, sem edição, da execução acima. A duração (`3ms`) é a
única coisa que vai variar na sua máquina e entre execuções.

Você não escreveu uma única instrução de log. A narrativa veio do nome do
método (`placeOrder`), dos nomes dos parâmetros (`customerId`,
`productId`, `quantity`) e do valor que o método retornou — nada mais.

## O que acabou de acontecer

- **`ThreadLocalNarrativeContext`** é o contexto de gravação — um por
  thread, retendo o que quer que rode nele até você lê-lo de volta.
- **`NarrativeTraceProxy.trace(...)`** encapsula `OrderService` em um proxy
  dinâmico da JDK: toda chamada através da referência encapsulada é
  capturada antes de chegar à implementação real.
- **`context.captureTrace()` + um renderizador** transformam as chamadas
  capturadas em saída. `IndentedTextRenderer` é o renderizador de texto
  simples usado acima; a mesma árvore também é renderizada como Markdown,
  JSON ou um diagrama de sequência Mermaid — veja [Formato de Trace
  Estrutural](../structural-trace-format.md) (em inglês) e o [Guia do
  Plugin de Gradle](guia-do-plugin-de-gradle.md) para os demais formatos e
  como o plugin os grava em disco por teste.

## Envie para o seu logger

Mesmo projeto, uma dependência e um arquivo de configuração — o
`Main.java` acima não muda. O `narrativetrace-slf4j` se conecta sozinho ao
pipeline assim que entra no classpath, então este é todo o diff:

```diff
 dependencies {
     implementation("ai.narrativetrace:narrativetrace-core:0.2.1")
     implementation("ai.narrativetrace:narrativetrace-proxy:0.2.1")
+    runtimeOnly("ai.narrativetrace:narrativetrace-slf4j:0.2.1")
+    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")
 }
```

```xml
<!-- src/main/resources/logback.xml -->
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [%logger] - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="narrativetrace" level="TRACE" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

```bash
./gradlew run
```

```text
22:24:53.632 TRACE [narrativetrace] - → OrderService.placeOrder(customerId: "C-1234", productId: "SKU-KB", quantity: 2)
22:24:53.637 TRACE [narrativetrace] - ← returned: "ORD-C-1234-SKU-KB-2"
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-KB", quantity: 2) → "ORD-C-1234-SKU-KB-2" — 8ms
```

A duração varia como antes. O mesmo trace agora chega à biblioteca de
logging que você já usa — Logback, escolhido aqui por ser o backend SLF4J
mais comum — enquanto a linha do console continua imprimindo exatamente
como antes. Configuração completa (níveis de log, nomes de logger, campos
MDC) no [Guia de Configuração, §7](guia-de-configuracao.md#7-configuração-do-slf4j).

## A seguir

| Você quer | Vá para |
|---|---|
| Isso rodando dentro da sua suíte de testes, um arquivo por cenário, automaticamente | [Guia de Instalação](guia-de-instalacao.md) e o [Guia do Plugin de Gradle](guia-do-plugin-de-gradle.md) |
| Manter um valor — um token, uma senha — fora do trace | [Privacidade e Ocultação](privacidade-e-ocultacao.md) |
| Pontuar se seus nomes realmente se leem como uma narrativa | [Guia de Clareza](guia-de-clareza.md) |
| Todo parâmetro de configuração, JUnit/Gradle/Spring/Micronaut/SLF4J igualmente | [Guia de Configuração](guia-de-configuracao.md) |
| Um caminho de integração diferente do proxy JDK acima (Spring, Micronaut, agente java, sem interface) | [Escolhendo uma Integração](escolhendo-uma-integracao.md) |
| Algo acima não funcionou como mostrado | [Solução de Problemas](solucao-de-problemas.md) |
