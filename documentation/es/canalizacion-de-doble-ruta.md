<!-- source: documentation/dual-path-pipeline.md blob b676bcf539d7 | translated: 2026-09-10 | reviewed: - -->
# La canalización de eventos de doble ruta

[English](../dual-path-pipeline.md) | **Español** | [Português](../pt-BR/pipeline-de-caminho-duplo.md) | [简体中文](../zh-CN/双路径事件管道.md)

Cada llamada trazada publica sus eventos exactamente una vez, a través
de una única canalización. La canalización por defecto —
`DualPathPipeline`, en todas las distribuciones — entrega cada evento
dos veces, por dos rutas con garantías deliberadamente opuestas. Esta
página es el contrato de ambas: qué promete cada ruta, qué cuesta cada
una, y qué ocurre bajo carga y después de una caída del proceso.

## La forma

```
 método trazado (hilo llamador)
      │
      ▼
 captura: los valores se renderizan y se OCULTAN aquí, una vez   ← antes de que nada más los vea
      │
      ▼
 publicación (un TraceEvent)
      ├──────────────► ruta síncrona: narración SLF4J, en línea,
      │                la escritura termina antes de que el método retorne
      └──────────────► ruta con búfer: anillo acotado → almacén de eventos,
                       árbol de traza, exportadores, oyentes SPI
```

Un evento, dos entregas. Cada ruta está aislada de la otra y de usted:
una excepción lanzada por cualquiera de los consumidores queda
contenida y nunca se convierte en un fallo de la aplicación.

## La ruta síncrona — narración que sobrevive a una caída

El oyente SLF4J se ejecuta en línea, en el hilo que hace la llamada. La
escritura en el log termina antes de que el método trazado retorne a su
llamador, así que la narración es exactamente tan durable — y cuesta
exactamente lo mismo — que una llamada de log escrita a mano. Si el
proceso muere en la instrucción siguiente, todo lo narrado hasta ese
momento ya está en su flujo de logs.

Los niveles por tipo de evento son configurables; los valores por
defecto son `ENTRY`/`RETURN` en TRACE y `EXCEPTION` en WARN, en el
logger `narrativetrace`. El formateo, los appenders y cualquier bloqueo
pertenecen a su backend de logging — el oyente en sí no mantiene estado
mutable compartido más allá del contexto por hilo.

## La ruta con búfer — captura que nunca bloquea

La ruta con búfer respalda `captureTrace()` y todo lo construido sobre
él: el árbol de traza, los exportadores y los oyentes conectados por el
SPI. Su portador es un búfer de anillo acotado — 65.536 ranuras por
defecto (`narrativetrace.buffer.capacity`), aproximadamente 1,75 MB,
presupuestado en unos 300 bytes por evento en saturación — drenado por
un único hilo consumidor. Nunca crece, y nunca bloquea al hilo
llamador.

Bajo presión descarta carga en lugar de aplicar contrapresión:

| Llenado | Comportamiento |
|---|---|
| hasta 70% | procesamiento completo — almacén, árbol, suscriptores |
| 70–90% | descarte: los eventos se drenan y desechan por lotes |
| más de 90% | emergencia: se desecha todo hasta que la presión baje |

El anillo mismo sobrescribe su ranura más antigua en lugar de crecer,
de modo que un productor siempre puede escribir. Perder eventos en
saturación es el comportamiento pretendido en esta ruta — la
alternativa es que sus hilos de petición esperen por la observabilidad.

## La pérdida se cuenta, nunca es silenciosa

Cada evento descartado se cuenta — sobrescrituras del anillo, lotes
desechados y descartes por contrapresión de suscriptores por igual — y
se suma en `BufferedEventConsumer.droppedCount()`. El pie de la propia
traza informa la pérdida, y se omite solo cuando no se perdió nada: una
traza corta nunca es silenciosamente indistinguible de una tranquila.

## Qué cuesta una caída

Las dos rutas responden a la pregunta de la caída de manera distinta, a
propósito:

- **Ruta síncrona:** nada ya narrado se pierde. La escritura ocurrió
  antes de que el método continuara.
- **Ruta con búfer:** el árbol de traza en vuelo es de mejor esfuerzo.
  Los eventos que aún estaban en el anillo en el momento de la caída se
  pierden, y ese es el intercambio documentado por no bloquear nunca a
  un llamador.

## La ocultación ocurre antes de la bifurcación

Los valores de parámetros se renderizan — y se ocultan — una vez, en la
captura, antes de que el evento se publique. Un valor denegado por
nombre, por anotación o por su propia forma nunca entra en el evento,
así que ninguna ruta, ningún exportador y ningún oyente SPI puede
verlo jamás. El contrato completo, superficie por superficie, está en
[Privacidad y ocultación](privacidad-y-ocultacion.md).

## Dónde se conectan los consumidores

Dos costuras, ambas API pública:

- **`TraceEventListener`** — por evento, descubierto vía
  `ServiceLoader`, entregado en el hilo consumidor de la ruta con
  búfer. Un oyente que lanza una excepción se reporta una vez y queda
  deshabilitado por el resto de la vida de la JVM; un consumidor que se
  porta mal nunca tumba la canalización con él.
- **`TraceExporter`** — por traza completada, en un límite de petición
  (el filtro de servlet lo invoca con el árbol terminado).

## Configuración

| Clave | Efecto |
|---|---|
| `narrativetrace.pipeline` | Selecciona por nombre una topología de canalización registrada; sin definir, construye la topología de doble ruta por defecto descrita aquí |
| `narrativetrace.buffer.capacity` | Tamaño del anillo de la ruta con búfer |
| `narrativetrace.narration` | `off` veta el oyente de narración SLF4J |

La canalización es un componente detrás de una única interfaz: esta
página documenta la topología por defecto, y todo lo anterior — la
ocultación en tiempo de captura, la pérdida contada, el aislamiento de
consumidores — se mantiene sin importar qué topología seleccione un
despliegue. Todas las claves se leen una sola vez al arranque; consulte
la [Guía de configuración](guia-de-configuracion.md) para todas las
superficies de configuración.
