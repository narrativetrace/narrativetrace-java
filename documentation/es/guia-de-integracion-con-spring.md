<!-- source: documentation/spring-integration-guide.md blob cd2a81631cc6 | translated: 2026-08-31 | reviewed: 2026-09-03 -->
# Guía de integración con Spring

[English](../spring-integration-guide.md) | **Español** | [简体中文](../zh-CN/Spring集成指南.md)

Esta guía cubre cómo integrar NarrativeTrace en una aplicación Spring Boot, desde el tracing básico de beans hasta el ciclo de vida de peticiones en producción y la propagación entre hilos con `@Async`.

## Módulos

| Módulo | Propósito |
|---|---|
| `narrativetrace-spring` | `@EnableNarrativeTrace` — envuelve automáticamente los beans elegibles mediante un `BeanPostProcessor` |
| `narrativetrace-servlet` | `NarrativeTraceFilter` — ciclo de vida de traza por petición (sin dependencia de Spring) |
| `narrativetrace-spring-web` | Autoconfiguración de Spring para el filtro de servlet con exportador intercambiable |
| `narrativetrace-micrometer` | `NarrativeTraceThreadLocalAccessor` — propagación de trazas entre hilos mediante context-propagation de Micrometer |

## 1. Tracing básico de beans

Añade la dependencia:

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring:0.2.0")
```

Habilita el tracing en tu clase de configuración:

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.order", "com.example.payment"})
public class AppConfig { }
```

Cuando se omite `basePackages`, el valor por defecto es el paquete de la clase anotada (como en `@ComponentScan`).

### Nombre del logger

Cuando `narrativetrace-slf4j` está en el classpath, el contexto autocreado narra a través de SLF4J automáticamente (`Slf4jTraceEventListener` en la ruta síncrona de la tubería). Configura el nombre del logger:

```java
@EnableNarrativeTrace(loggerName = "myapp.traces")
```

El valor por defecto es `"narrativetrace"`. Usa `""` para desactivar el envoltorio SLF4J automático.

### Identidad del servicio

Configura los metadatos de identidad del servicio en la anotación cuando los spans y las trazas exportadas deban llevar
una identidad de proceso estable:

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

Estos valores se estampan en cada `SpanContext` creado. Las integraciones descendentes, como el MDC de
SLF4J, la exportación JSON y OpenTelemetry, pueden entonces exponer `service.name`, `service.version` y
`service.environment` para correlación.

### Qué ocurre

Un `BeanPostProcessor` (ordenado en `HIGHEST_PRECEDENCE`) envuelve los beans elegibles en proxies dinámicos JDK que registran la entrada a los métodos, los valores de retorno y las excepciones en un `NarrativeContext` compartido.

**Reglas de elegibilidad:**
- El bean debe implementar al menos una interfaz
- La clase del bean debe estar en un paquete configurado
- Las interfaces del framework Spring (p. ej., `BeanFactoryAware`, `InitializingBean`) quedan excluidas

Un bean `NarrativeContext` se proporciona automáticamente. Cuando `narrativetrace-slf4j` está en el classpath y `loggerName` no está vacío, narra a través de SLF4J bajo ese logger. Define tu propio bean `narrativeContext` para sobrescribirlo (p. ej., para la integración con Micrometer — ver la sección 3).

### Orden del BPP

El `BeanPostProcessor` de tracing se ejecuta en `HIGHEST_PRECEDENCE`, lo que significa que el proxy de traza es el envoltorio **más interno**. Al combinarse con el proxy `@Async` de Spring, las capas quedan así:

```
Proxy Async (externo) → Proxy de traza (interno) → Bean real
```

Esto garantiza que el tracing se ejecute en el hilo asíncrono real, no en el hilo llamador.

## 2. Tracing de peticiones en producción

Para trazar peticiones HTTP en una aplicación desplegada, añade el módulo del filtro de servlet:

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring-web:0.2.0")
```

Esto registra automáticamente `NarrativeTraceFilter` como un bean de Spring. El filtro sigue un ciclo de vida de reinicio-cadena-captura-exportación-reinicio en cada petición:

1. Reinicia el contexto (limpia el estado obsoleto)
2. Ejecuta la cadena de filtros (los beans descendentes se trazan mediante proxies)
3. Captura el árbol de trazas acumulado
4. Exporta mediante el `TraceExporter` configurado
5. Reinicia de nuevo

Mientras la petición está activa, el filtro también rellena campos de correlación persistentes en el MDC, incluyendo
`traceId`, `traceName`, `httpMethod`, `httpRoute` y `clientIp`. `traceName` es un alias determinista
de tres palabras derivado del ID de la traza, pensado para una correlación legible en los logs.

### Exportador por defecto

Por defecto, las trazas se exportan mediante `Slf4jTraceExporter` (JSON a SLF4J en nivel INFO). Para usar un exportador personalizado, registra un bean `TraceExporter`:

```java
@Bean
TraceExporter traceExporter() {
    return (tree, requestContext) -> {
        // Envía a tu plataforma de observabilidad
    };
}
```

El `RequestContext` proporciona `method`, `uri`, `statusCode` y `durationMillis`.

### Contexto de usuario (`RequestContextProvider`)

La autenticación y la multi-tenencia suelen vivir fuera del contexto de traza — en un principal de seguridad, en una cabecera o en un atributo de sesión. `RequestContextProvider` los copia de la petición una vez por petición, y NarrativeTrace estampa `enduserId`, `sessionId` y `tenantId` en cada span que esa petición genere, y en el MDC.

La interfaz es genérica sobre el tipo de petición del framework para que el jar de la API siga sin dependencias. Tanto el filtro de servlet como el cableado de Spring Web la vinculan a `HttpServletRequest` — **escribe esa vinculación explícitamente**:

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
            return null;  // petición anónima — ausencia, no un error
        }
        return new UserContext(
                principal.getName(),
                request.getRequestedSessionId(),
                request.getHeader("X-Tenant-Id"));
    }
}
```

`NarrativeTraceWebConfiguration` recoge el bean automáticamente — no hay nada más que cablear.

> **La vinculación es lo que hace localizable al bean.** El proveedor se resuelve por su tipo *vinculado*, `RequestContextProvider<HttpServletRequest>`. Un bean declarado en crudo (`implements RequestContextProvider`) o vinculado a otro tipo de petición simplemente no es candidato: la resolución de Spring, consciente de los genéricos, no lo encuentra, el filtro se ejecuta sin contexto de usuario y no se registra nada. El síntoma son campos `enduserId`/`tenantId` vacíos en cada span, no un error.

Devolver `null` significa «no hay identidad para esta petición», lo normal en tráfico anónimo. Un proveedor que lance una excepción recibe el mismo trato — la petición continúa sin contexto de usuario, porque la observabilidad nunca debe hacer fallar una petición.

**Sin Spring**, la misma implementación se pasa directamente al filtro:

```java
var filter = new NarrativeTraceFilter(context, exporter, new SecurityContextProvider());
```

## 3. Propagación entre hilos (`@Async`)

Cuando un método trazado despacha trabajo a otro hilo (mediante `@Async`, `CompletableFuture.supplyAsync` o un executor personalizado), el contexto de traza debe propagarse explícitamente. Sin esto, el trabajo asíncrono aparece como una traza desconectada en el hilo de trabajo.

### Cómo funciona

NarrativeTrace usa [context-propagation de Micrometer](https://github.com/micrometer-metrics/context-propagation) para llevar el estado de la traza a través de los límites entre hilos. La configuración tiene tres partes:

1. **Registrar el accessor** — le dice a Micrometer cómo tomar y restaurar una instantánea del estado `ThreadLocal` de NarrativeTrace
2. **Configurar un `TaskDecorator`** — envuelve los runnables asíncronos para que lleven la instantánea al hilo de trabajo
3. **Sobrescribir el bean `NarrativeContext`** — conecta el accessor con la instancia real del contexto

### Dependencias

```kotlin
implementation("ai.narrativetrace:narrativetrace-spring:0.2.0")
implementation("ai.narrativetrace:narrativetrace-micrometer:0.2.0")
```

### Configuración

```java
@Configuration
@EnableNarrativeTrace
@EnableAsync
public class AppConfig {

    @Bean
    NarrativeContext narrativeContext() {
        var tlc = new ThreadLocalNarrativeContext();

        // Regístralo en Micrometer para que context-propagation conozca NarrativeTrace
        var accessor = new NarrativeTraceThreadLocalAccessor(tlc);
        ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);

        return tlc; // la narración SLF4J se adjunta automáticamente cuando narrativetrace-slf4j está presente
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

### El `TaskDecorator`

El `TaskDecorator` es el puente que captura el contexto en el hilo llamador y lo restaura en el hilo de trabajo. `narrativetrace-spring` incluye uno — `ai.narrativetrace.spring.ContextPropagatingTaskDecorator` — y el fragmento anterior lo usa directamente. Lleva dos cosas, ambas capturadas cuando la tarea se *envía*:

| Parte | ¿Requerida para NarrativeTrace? | Propósito |
|---|---|---|
| `ContextSnapshot` de Micrometer | **Sí** | Propaga el estado de NarrativeTrace vía el `ThreadLocalAccessor` registrado |
| Captura/restauración del MDC | No | Propaga el MDC de SLF4J para correlación de logging (`traceId`, `traceName`, metadatos de la petición, etc.) |

La mitad del MDC está activada por defecto y restaura después el MDC propio del hilo de trabajo, también cuando la tarea lanza una excepción. Desactívala si tu aplicación no usa MDC, o si otro decorador ya se encarga de él:

```java
executor.setTaskDecorator(ContextPropagatingTaskDecorator.withoutMdc());
```

**Dependencias.** El decorador necesita `io.micrometer:context-propagation` en el classpath de ejecución — `narrativetrace-spring` lo declara `compileOnly`, así que añádelo tú (el módulo `narrativetrace-micrometer` que ya necesitas para el accessor lo arrastra — mira [Dependencias](#dependencias) más arriba). SLF4J es opcional: la propagación del MDC se desactiva sola cuando `org.slf4j.MDC` no está presente.

**Si ya tienes un `TaskDecorator`**, un executor solo tiene una ranura de decorador — compón ambos, o añade el snapshot de Micrometer al tuyo:

```java
public class MyDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        var snapshot = ContextSnapshotFactory.builder().build().captureAll();
        return () -> snapshot.wrap(runnable).run();
    }
}
```

### Verificar la propagación

Un método `@Async` debería aparecer como hijo en el árbol de trazas, aunque se ejecute en un hilo distinto:

```
OrderService.placeOrder(customerId: "C-1234")
  InventoryService.reserve(productId: "SKU-KB", quantity: 2) → ReservedInventory(...)
  PaymentService.charge(customerId: "C-1234", amount: 179.98) → Payment(...)
  NotificationService.sendConfirmation(orderId: "ORD-001") → true    ← se ejecuta en el hilo async-1
```

Dónde aparece depende de cuándo se envió la tarea, no de cuándo terminó: enviada
mientras `placeOrder` seguía ejecutándose, es hija de `placeOrder`; enviada
después de que `placeOrder` retornara, es la siguiente raíz de la misma traza.
En ambos casos el `captureTrace()` del llamador la reporta, igual que ya la
narraba el flujo de log síncrono (ADR-013).

Sin el `TaskDecorator`, la llamada de notificación no aparecería en la traza en absoluto.

### Salida diferida con CompletableFuture

Cuando un método trazado devuelve `CompletableFuture<T>`, el proxy gestiona automáticamente la finalización diferida:

1. El frame se separa de la pila activa mediante `detachFrame(handle)`
2. Se registra un callback `whenComplete` en el future
3. Cuando el future se resuelve, el frame se completa con el valor resuelto real (no con `<pending>`)

Esto funciona de forma transparente con métodos `@Async` que devuelven `CompletableFuture`. No se necesita configuración adicional — el proxy detecta el tipo de retorno y aplica la salida diferida automáticamente.

## 4. Probar beans de Spring

Para probar beans trazados con JUnit 5 no necesitas el módulo de Spring en absoluto — usa `NarrativeTraceProxy` directamente:

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
        // La salida de la traza es automática gracias a la extensión
    }
}
```

Este enfoque es más rápido que un contexto Spring completo y te da control directo sobre el cableado. Usa `@SpringBootTest` solo cuando necesites probar la propia integración con Spring (auto-wiring, orden del BPP, propagación con `@Async`).

Para un ejemplo funcional que arranca un contexto Spring real y verifica tanto el tracing de beans envueltos automáticamente como la propagación de hilos con `@Async`, consulta [`SpringIntegrationTest`](../../narrativetrace-examples/ecommerce/src/test/java/ai/narrativetrace/examples/ecommerce/SpringIntegrationTest.java).

## Ver también

- [Guía de instalación](guia-de-instalacion.md) — dependencias, rutas de integración, selección de módulos
- [Guía de configuración](guia-de-configuracion.md) — niveles de tracing, detalles de `@EnableNarrativeTrace`
- [Guía de integración con Micronaut](guia-de-integracion-con-micronaut.md) — equivalente de esta guía para Micronaut
- [Guía de anotaciones](guia-de-anotaciones.md) — `@Narrated`, `@OnError`, `@NotTraced`
