# Documentación de NarrativeTrace

[English](README.md) | **Español** | [Português](LEIAME.md) | [简体中文](自述文件.md)

El índice de las guías de usuario de NarrativeTrace en español. La
documentación técnica y de diseño (superficie de la API, pruebas de
seguridad y de concurrencia) permanece solo en inglés — consulta el
[índice completo](README.md). La documentación
publicada completa — incluidas páginas sin fichero aquí — está en
[narrativetrace.ai/docs](https://narrativetrace.ai/docs.html) (en inglés).

## Primeros pasos

| Documento | Qué cubre |
|---|---|
| [Primeros 10 minutos](es/primeros-10-minutos.md) | Un servicio minúsculo, un test JUnit, ocho pasos hasta una traza real — cada comando y cada pieza de salida se ejecutaron de verdad contra este repositorio |
| [Experiencia de desarrollo](es/experiencia-de-desarrollo.md) | De un proyecto vacío a tu primera narrativa: configuración en una línea, artefactos por prueba, el lanzador de la demo, builds compuestos locales, la ocultación como parte del flujo de trabajo |
| [Guía de instalación](es/guia-de-instalacion.md) | Dependencias, las cinco vías de integración (proxy JDK, Spring, Micronaut, JUnit 5/4, agente Java), configuración de la salida de trazas, matriz de compatibilidad |
| [Eligiendo una integración](es/eligiendo-una-integracion.md) | Qué módulo necesitas en realidad: un diagrama de decisión más las salvedades de cada una de las cinco vías de conexión |
| [Guía del ciclo de vida](es/guia-del-ciclo-de-vida.md) | Dónde encaja NarrativeTrace en tu proceso: desarrollo, CI/aceptación, producción — y la postura de privacidad en cada etapa |
| [Guía de configuración](es/guia-de-configuracion.md) | Cada superficie de configuración: propiedades del sistema, `junit-platform.properties`, Gradle, Spring, Micronaut, SLF4J; flags de captura, claves MDC y valores por defecto de ocultación |
| [Guía de anotaciones](es/guia-de-anotaciones.md) | `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`, y el contrato de pureza que implican |
| [Privacidad y ocultación](es/privacidad-y-ocultacion.md) | El contrato de ocultación fila por fila verificado contra el código, la lista de garantías y no-garantías, y el modelo de pérdida en producción |
| [La canalización de eventos de doble ruta](es/canalizacion-de-doble-ruta.md) | El contrato de la canalización por defecto: la ruta síncrona de narración durable ante caídas, la ruta con búfer que nunca bloquea, la pérdida contada, y la ocultación en captura aguas arriba de ambas |
| [Qué commitear](es/que-commitear.md) | Qué ficheros generados son artefactos de CI y cuáles son baselines revisadas que commiteas |
| [Solución de problemas](es/solucion-de-problemas.md) | Síntoma → causa → solución para los modos de fallo que la gente realmente encuentra, desde parámetros `arg0` hasta un agente silencioso |

## Integración con frameworks

| Documento | Qué cubre |
|---|---|
| [Guía de integración con Spring](es/guia-de-integracion-con-spring.md) | Tracing de beans mediante `BeanPostProcessor`, el filtro de servlet, propagación de `@Async` con `ContextPropagatingTaskDecorator` |
| [Guía de integración con Micronaut](es/guia-de-integracion-con-micronaut.md) | Tracing de beans, el filtro HTTP reactivo, propiedades de configuración |
| [Guía del plugin de Gradle](es/guia-del-plugin-de-gradle.md) | Referencia del DSL, las puertas de calidad que registra el plugin, recetas |

## Análisis y salida

| Documento | Qué cubre |
|---|---|
| [Guía de claridad](es/guia-de-claridad.md) | El modelo de puntuación de cinco dimensiones, los componentes NLP detrás de él, integración con JUnit y la puerta `clarityCheck` |
| [Guía de funcionalidades](es/guia-de-funcionalidades.md) | El catálogo canónico: cada funcionalidad, su nivel, su implementación, su estado |
| [Formato de traza estructural](es/formato-de-traza-estructural.md) | El artefacto `.nt` libre de valores detrás del informe de deltas y las pruebas de aprobación — la especificación del formato multiplataforma |

## Manteniendo este índice honesto

Traducir un documento nuevo bajo `documentation/es/` implica añadirlo a
este índice en el mismo cambio. Un documento que no aparece aquí es
invisible para quien navegue el repositorio en español.
