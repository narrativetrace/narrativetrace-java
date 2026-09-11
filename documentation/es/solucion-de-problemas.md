<!-- source: documentation/troubleshooting.md blob 8bf50960f149 | translated: 2026-09-03 | reviewed: 2026-09-03 -->
# Solución de problemas

[English](../troubleshooting.md) | **Español** | [Português](../pt-BR/solucao-de-problemas.md) | [简体中文](../zh-CN/故障排查.md)

Síntoma → causa → solución, para los modos de fallo que la gente realmente
encuentra. Algunas entradas son la explicación completa; otras apuntan a la
guía que ya lleva el detalle en lugar de repetirlo aquí — un solo sitio por
cada hecho.

## Los parámetros aparecen como `arg0`, `arg1`

**Causa:** falta el flag del compilador `-parameters`, así que la clase
compilada no lleva nombres reales de parámetros que NarrativeTrace pueda
leer.

**Solución:**

```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
```

El plugin de Gradle añade esto automáticamente — esto solo importa para una
configuración manual.

## No hay ficheros de salida de traza

**Causa:** la salida está desactivada por defecto fuera del plugin.

**Solución:** añade a `src/test/resources/junit-platform.properties`:

```properties
narrativetrace.output=true
```

O ejecuta con `-Dnarrativetrace.output=true`. El plugin de Gradle configura
esto por ti.

## No veo nada en mi terminal

**Causa:** el resumen por test y el pie de página de la suite se escriben
en la salida estándar del proceso de test. La tarea `test` de Gradle
captura eso hacia el informe XML/HTML por defecto — un `./gradlew test` a
secas no muestra nada en un terminal normal, aunque cada fichero bajo
`build/narrativetrace/` se escriba correctamente.

**Solución:** activa el registro de flujos estándar en la tarea `test`:

```kotlin
tasks.test {
    testLogging.showStandardStreams = true
}
```

Esto es un comportamiento de Gradle, no algo que haga NarrativeTrace —
consulta
[Guía de instalación § Opción D](guia-de-instalacion.md#opción-d-contexto-automático-de-junit-4--salida-de-trazas)
para la misma nota en la receta de JUnit 4.

## El proxy lanza `ClassCastException`

**Causa:** el objetivo no implementa la interfaz que se pasa a
`NarrativeTraceProxy.trace(...)`.

**Solución:** asegúrate de que la clase concreta implementa esa interfaz, y
pasa la `Class` de la interfaz, no la de la implementación.

## Los beans de Spring no se están trazando

**Causa:** o el paquete del bean está fuera de `basePackages`, o el bean no
implementa ninguna interfaz — `narrativetrace-spring` usa el mismo proxy
dinámico JDK que `narrativetrace-proxy`, que no puede envolver una clase
sin interfaz. Un bean sin interfaz se deja intacto en silencio; esto no es
un error.

**Solución:** añade el paquete a
`@EnableNarrativeTrace(basePackages = ...)`, y dale una interfaz al bean si
no tiene una.

## El filtro HTTP de Micronaut no inyecta contexto de usuario

**Causa:** un bean que implementa `RequestContextProvider` se escribió
contra el tipo equivocado. NarrativeTrace distribuye dos interfaces con
nombres parecidos: `ai.narrativetrace.api.export.RequestContextProvider<REQUEST>`
(genérica, agnóstica de framework, a la que se vinculan el filtro de
servlet y Spring Web para `HttpServletRequest`) y una específica de
Micronaut que el filtro HTTP realmente busca. Un bean que implementa el
tipo del jar de la API no es candidato en silencio — el filtro no inyecta
nada y se ejecuta sin contexto de usuario, sin ningún error en absoluto. El
síntoma es que cada span muestra los campos `enduserId`/`tenantId` vacíos.

**Solución:** implementa el tipo de Micronaut, no el del jar de la API.
Detalle completo — incluido por qué los dos tipos se mantienen separados —
en [Guía de integración con Micronaut](guia-de-integracion-con-micronaut.md)
y [API Surface](../api-surface.md).

## La puntuación de claridad parece incorrecta

**Causa:** normalmente un nombre genérico que el análisis NLP señala —
`get`, `set`, `process`, `handle`, `data`, `info`, `temp` y similares
puntúan bajo sin importar el contexto.

**Solución:** revisa la lista de incidencias en `clarity-report.md` y
sustituye el nombre señalado por uno específico del dominio
(`getData()` → `fetchOrderHistory()`). Consulta la
[Guía de claridad](guia-de-claridad.md) para el modelo de puntuación
completo.

## Las trazas entre hilos aparecen vacías

**Causa:** `captureTrace()`/`events()` están acotados por hilo — solo
devuelven la traza del hilo que llama. O el contexto nunca se propagó al
hilo asíncrono, o `captureTrace()` se llamó en un hilo distinto al que
registró los eventos.

**Solución:**

```java
var snapshot = context.snapshot();
executor.submit(snapshot.wrap(() -> service.process()));
```

O registra `NarrativeTraceThreadLocalAccessor` con Micrometer para
propagación automática. Si solo necesitas el trabajo delegado en sí,
llama a `captureTrace()` dentro de la tarea, en el hilo que registra.

## El agente no instrumenta clases

**Causa:** el filtro `packages=` no coincide, o las reglas de frontera no
coinciden con lo que esperabas — `com.example` coincide con
`com.example.*`, nunca con `com.exampleExtra`.

**Solución:** revisa el argumento `packages=`; se admiten tanto comodines
(`com.example.*`) como varios paquetes (separados por punto y coma,
`packages=com.a.*;com.b.*`). No hay lista de exclusión — solo inclusión.

## El agente traza pero no aparece nada en mis logs

**Causa:** el agente deliberadamente no trae ningún proveedor SLF4J propio
— un host mínimo (lo más habitual, un servidor de aplicaciones sin
classpath alcanzable) no tiene nada por donde escribir la narración, así
que SLF4J registra una advertencia única y usa por defecto un logger no-op.
Nada está roto: `captureTrace()` sigue devolviendo la traza, y los
ficheros bajo `build/narrativetrace/` (si la salida está activada) no se
ven afectados — solo la narración de log en vivo queda en silencio.

**Solución:** pon un proveedor en el classpath (`logback-classic`,
`slf4j-simple`, …), o pasa `loggingJars=/ruta/al/proveedor.jar` para un
host sin classpath alcanzable. Para silenciar la narración a propósito en
su lugar, adjunta con `loggerName=` (vacío) o
`-Dnarrativetrace.narration=off`. Receta completa en
[Guía de instalación § La narración necesita un proveedor SLF4J](guia-de-instalacion.md#la-narración-necesita-un-proveedor-slf4j--el-agente-nunca-trae-uno).

## `@NotTraced` falla al compilar: "package ai.narrativetrace.api.annotation does not exist"

**Causa:** el `scope` por defecto del plugin de Gradle es `"test"`, así que
`narrativetrace-api` — donde vive cada anotación — se sitúa solo en
`testImplementation`. Referenciar `@NotTraced` (o `@Narrated`, `@OnError`,
`@NarrativeSummary`) desde una clase bajo `src/main/java` falla al
compilar antes de que llegue a correr ningún test.

**Solución:** añade el jar de la API en `compileOnly` (no tiene
dependencias de runtime propias):

```kotlin
dependencies {
    compileOnly("ai.narrativetrace:narrativetrace-api:0.2.1")
}
```

O define `scope.set("production")` si NarrativeTrace está pensado para
correr en producción de todos modos. Consulta
[Primeros 10 minutos § 7](primeros-10-minutos.md#7-añade-nottraced-y-observa-la-ocultación),
donde esto se encuentra y se arregla de la misma forma.

## El modo aprobación escribió `.received.nt`

**Causa:** este es el comportamiento esperado, no un fallo — o todavía no
existe ninguna baseline `.approved.nt` para el escenario, o la estructura
de un test que pasa se ha desviado de la que está commiteada.

**Solución:** revisa el diff de `.received.nt`, y si la forma nueva es
correcta, promociónala:

```bash
./gradlew approveNarratives
```

Nunca lo renombres a mano sin leer antes el diff — eso es exactamente lo
que el modo aprobación existe para forzar a revisar. Formato completo y
disposición de ficheros en
[Formato de traza estructural](formato-de-traza-estructural.md); qué hacer
con cada fichero día a día está en [Qué commitear](que-commitear.md).
