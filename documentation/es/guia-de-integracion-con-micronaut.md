<!-- source: documentation/micronaut-integration-guide.md blob 6ba6a6f99491 | translated: 2026-08-31 | reviewed: 2026-09-03 -->
# Guía de integración con Micronaut

[English](../micronaut-integration-guide.md) | **Español** | [简体中文](../zh-CN/Micronaut集成指南.md)

Esta guía cubre la integración de NarrativeTrace en una aplicación Micronaut, desde el tracing básico de beans hasta el ciclo de vida de peticiones HTTP en producción.

## Módulos

| Módulo | Propósito |
|---|---|
| `narrativetrace-micronaut` | Envuelve automáticamente los beans elegibles mediante `BeanCreatedEventListener` — se autodescubre en el classpath |
| `narrativetrace-micronaut-http` | `HttpServerFilter` reactivo para el ciclo de vida de la traza por petición |

## 1. Tracing básico de beans

Añade la dependencia:

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut:0.2.0")
```

Configura los paquetes base en `application.yml`:

```yaml
narrativetrace:
  base-packages:
    - com.example.order
    - com.example.payment
```

No se necesita ninguna anotación de habilitación. El `@Factory` y el `BeanCreatedEventListener` se autodescubren en el classpath.

### Qué sucede

Un `BeanCreatedEventListener<Any>` envuelve los beans elegibles en proxies dinámicos JDK que registran la entrada a los métodos, los valores de retorno y las excepciones en un `NarrativeContext` compartido.

**Reglas de elegibilidad:**
- La clase del bean debe estar en un paquete base configurado
- El bean debe implementar al menos una interfaz de un paquete configurado
- Los beans sin interfaces coincidentes se dejan intactos
- Un `base-packages` vacío no envuelve nada (valor por defecto seguro)

Se proporciona automáticamente un bean `NarrativeContext` (marcado como `@Secondary`). Cuando `narrativetrace-slf4j` está en el classpath y `loggerName` no está vacío, se conecta con el logging de eventos de SLF4J. Define tu propio `@Bean NarrativeContext` para reemplazarlo.

### Propiedades de configuración

| Propiedad | Tipo | Valor por defecto | Propósito |
|---|---|---|---|
| `narrativetrace.base-packages` | `List<String>` | `[]` | Prefijos de paquete para el emparejamiento de beans/interfaces |
| `narrativetrace.logger-name` | `String` | `"narrativetrace"` | Nombre del logger SLF4J (vacío desactiva la conexión con SLF4J) |
| `narrativetrace.service-name` | `String` | `""` | Identidad del servicio — estampada en los spans |
| `narrativetrace.service-version` | `String` | `""` | Identidad del servicio — estampada en los spans |
| `narrativetrace.environment` | `String` | `""` | Identidad del servicio — estampada en los spans |

### Reemplazar el contexto por defecto

Declara tu propio `@Bean NarrativeContext` y el `@Secondary` por defecto queda reemplazado:

```kotlin
@Factory
class MyConfig {
    @Bean
    @Singleton
    fun narrativeContext(): NarrativeContext {
        // La narración SLF4J se adjunta automáticamente cuando narrativetrace-slf4j está presente;
        // el argumento de tubería la encamina a un nombre de logger personalizado.
        return ThreadLocalNarrativeContext(
            NarrativeTraceConfig(), PipelineBootstrap.createDefault("myapp.traces"))
    }
}
```

## 2. Tracing de peticiones HTTP en producción

Añade el módulo del filtro HTTP:

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.0")
```

El filtro HTTP reactivo (`HttpServerFilter`) se registra automáticamente al estar en el classpath — no se necesita configuración adicional.

### Ciclo de vida del filtro

1. Reinicia el contexto (limpia el estado obsoleto)
2. Estampa los metadatos de la petición (método HTTP, ruta, dirección remota)
3. Continúa por la cadena de filtros mediante `Mono.from(chain.proceed(request))`
4. Captura el árbol de trazas acumulado
5. Exporta mediante el `TraceExporter` configurado
6. Reinicia de nuevo (limpieza mediante `doFinally`)

El filtro coincide con todas las rutas (`@Filter("/**")`).

Mientras la petición está activa, el filtro rellena campos de correlación persistentes en el MDC, incluidos
`traceId`, `traceName`, `httpMethod`, `httpRoute` y `clientIp`. `traceName` es un alias determinista
de tres palabras derivado del ID de la traza, pensado para una correlación legible en el log.

### Exportador por defecto

Por defecto, las trazas se exportan mediante `Slf4jTraceExporter` (JSON a SLF4J en nivel INFO). El nombre del logger de exportación se deriva de la propiedad `loggerName` configurada: `<loggerName>.export` (p. ej., `narrativetrace.export`).

Para usar un exportador personalizado, registra un bean `TraceExporter`:

```kotlin
@Factory
class MyExporterConfig {
    @Bean
    @Singleton
    fun traceExporter(): TraceExporter = TraceExporter { tree, requestContext ->
        // Envía a tu plataforma de observabilidad
    }
}
```

El exportador por defecto `@Secondary` se reemplaza automáticamente.

### Contexto de usuario (`RequestContextProvider`)

La autenticación y la multi-tenencia suelen vivir fuera del contexto de traza — en una cabecera, en un principal de seguridad o en un atributo de sesión. `RequestContextProvider` los copia de la petición una vez por petición, y NarrativeTrace estampa `enduserId`, `sessionId` y `tenantId` en cada span que esa petición genere, y en el MDC.

Micronaut tiene su **propio** tipo de proveedor, `ai.narrativetrace.micronaut.http.RequestContextProvider`, tipado sobre el `HttpRequest` de Micronaut. Decláralo como singleton y el filtro lo inyecta:

```kotlin
import ai.narrativetrace.micronaut.http.RequestContextProvider
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton

@Singleton
class SecurityContextProvider : RequestContextProvider {
    override fun resolveUserContext(request: HttpRequest<*>): RequestContextProvider.UserContext? {
        val userId = request.headers["X-User-Id"] ?: return null  // anónimo — ausencia, no un error
        return RequestContextProvider.UserContext(
            enduserId = userId,
            sessionId = request.headers["X-Session-Id"],
            tenantId = request.headers["X-Tenant-Id"],
        )
    }
}
```

> **Importa el tipo de Micronaut, no el del jar de la API.** `ai.narrativetrace.api.export.RequestContextProvider<REQUEST>` es una interfaz distinta — genérica, y vinculada a `HttpServletRequest` por las integraciones de servlet y Spring Web. Un bean que implemente *esa* no es candidato aquí: el filtro no inyecta nada, se ejecuta sin contexto de usuario y no registra nada. El síntoma son campos `enduserId`/`tenantId` vacíos en cada span, no un error. Los dos tipos están separados deliberadamente; consulta [API Surface](../api-surface.md).

Devolver `null` significa «no hay identidad para esta petición», lo normal en tráfico anónimo. Un proveedor que lance una excepción recibe el mismo trato — la petición continúa sin contexto de usuario, porque la observabilidad nunca debe hacer fallar una petición.

### Manejo de errores

- Los fallos del exportador se descartan silenciosamente — la observabilidad nunca debe hacer fallar las peticiones
- Las trazas vacías (sin métodos trazados invocados) omiten la exportación por completo
- Los errores de la cadena disparan la exportación con código de estado 500 y después el error se propaga con normalidad
- `doFinally` garantiza el reinicio del contexto en caso de éxito, error o cancelación

## 3. Comparación de arquitectura con Spring

| Aspecto | Spring | Micronaut |
|---|---|---|
| Mecanismo de habilitación | Anotación `@EnableNarrativeTrace` | `@Factory` autodescubierto en el classpath |
| Configuración | Atributos de la anotación | `application.yml` mediante `@ConfigurationProperties` |
| Envoltura de beans | `BeanPostProcessor` | `BeanCreatedEventListener<Any>` |
| Reemplazo del bean por defecto | Definir un bean `narrativeContext` | Declarar `@Bean NarrativeContext` (reemplaza al `@Secondary`) |
| Filtro HTTP | `Filter` de servlet + `@Configuration` de Spring | `HttpServerFilter` reactivo (`@Filter("/**")`) |
| Reemplazo del exportador | `ObjectProvider<TraceExporter>` | `@Secondary` por defecto, el `@Bean` del usuario lo reemplaza |
| Resolución diferida | `BeanFactoryAware` | `ApplicationContext` + guarda de reentrada con ThreadLocal |

### Por qué el listener usa ApplicationContext

`BeanCreatedEventListener<Any>` se dispara para **todas** las creaciones de beans, incluidas las dependencias del propio listener (`NarrativeContext`, `NarrativeTraceProperties`). Inyectar esos beans por constructor causaría una dependencia circular o un desbordamiento de pila. El listener los resuelve de forma diferida desde `ApplicationContext` con una guarda de reentrada `ThreadLocal` que omite la envoltura durante la resolución de dependencias.

## 4. Probar beans de Micronaut

Para probar beans trazados con JUnit 5 no necesitas el módulo de Micronaut — usa `NarrativeTraceProxy` directamente:

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

Usa `@MicronautTest` solo cuando necesites probar la propia integración con Micronaut (auto-conexión, envoltura por el listener de beans, ciclo de vida del filtro HTTP).

## Ver también

- [Guía de instalación](guia-de-instalacion.md) — dependencias, rutas de integración, selección de módulos
- [Guía de configuración](guia-de-configuracion.md) — niveles de tracing, detalles de configuración de Micronaut
- [Guía de integración con Spring](guia-de-integracion-con-spring.md) — equivalente de esta guía para Spring
- [Guía de anotaciones](guia-de-anotaciones.md) — `@Narrated`, `@OnError`, `@NotTraced`
