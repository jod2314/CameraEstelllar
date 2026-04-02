# Fase 0: Hoja de Ruta Definitiva de Investigación y Desarrollo

Esta es la hoja de ruta definitiva y exhaustiva de investigación y desarrollo para CameraStellar v3, estructurada desde los cimientos del hardware hasta la optimización matemática extrema. 

## 🛠️ Stack Tecnológico Base Recomendado

- **Interfaz y Orquestación:** Kotlin, utilizando Corrutinas y ViewModel para evadir bloqueos en el hilo principal. Utilizarás HandlerThread en segundo plano para procesar callbacks pesados y evitar el cierre de la app.
- **Cómputo en Segundo Plano (Silo):** Un "Foreground Service" (Servicio de Primer Plano) de tipo `camera` y `mediaProcessing`.
- **Procesamiento de Imagen Cruda:** C++11/C++17 a través del Android NDK para manipular arreglos masivos de memoria "Off-Heap" eludiendo el Garbage Collector de Java.
- **Aceleración por GPU:** API Vulkan (mediante Compute Shaders en SPIR-V con precisión `float16_t`) para vectorizar la suma y filtrado de píxeles.

---

## 🗺️ Ruta de Trabajo e Investigación Arquitectónica

### Fase 1: Auditoría de Hardware y Capa de Abstracción (HAL)
Antes de tomar una sola foto, el motor debe ser capaz de interrogar a fondo al hardware, ya que el soporte manual varía según el nivel que el fabricante ha expuesto al sistema.
- **Investigación clave:** La clase `CameraCharacteristics`. Estudiar cómo leer el "pasaporte" de cada lente.
- **Niveles de Hardware:** Diferencias entre LEGACY, LIMITED, FULL y LEVEL_3. Para control manual total y captura RAW, descartar los dos primeros y exigir dispositivos FULL o LEVEL_3.
- **Cámaras Lógicas vs. Físicas (Multi-Camera API):** Desde Android 9 (API 28), los fabricantes agrupan lentes detrás de una cámara "lógica". Investiga el método `getPhysicalCameraIds()` para extraer lentes ocultos que no aparecen en la lista básica.

### Fase 2: Control Absoluto del Pipeline Camera2 (El "Manual Override")
El ciclo de la API Camera2 no toma fotos, lanza "solicitudes" (`CaptureRequest`) de captura asíncronas.
- **Investigación clave:** El bloqueo del "Algoritmo 3A" (Auto-Enfoque, Auto-Exposición y Auto-Balance de Blancos). Forzar la plantilla `TEMPLATE_MANUAL` y establecer `CONTROL_AE_MODE` y `CONTROL_MODE` a `OFF`.
- **Matemáticas de Exposición:** Control en nanosegundos (1s = 1,000,000,000 ns). Configurar la triada: `SENSOR_EXPOSURE_TIME` (tiempo de integración), `SENSOR_SENSITIVITY` (ganancia ISO analógica, idealmente limitada por `SENSOR_MAX_ANALOG_SENSITIVITY`) y fundamentalmente `SENSOR_FRAME_DURATION`.
- **Sesiones Modernas:** En lugar del método antiguo, investigar `SessionConfiguration` y `OutputConfiguration` para rutear los flujos de memoria hacia lentes físicos específicos de forma eficiente y sin re-inicializaciones.

### Fase 3: Captura Científica (RAW_SENSOR) y Extracción de Metadatos
Tu meta es obtener y preservar los fotones crudos.
- **Formato de Salida:** Instanciación de un `ImageReader` configurado en formato `ImageFormat.RAW_SENSOR`.
- **Transferencia Zero-Copy:** Al enviar este buffer de memoria RAW al NDK, no lo copies hacia Java. Investigar el uso de `AHardwareBuffer` o `DirectByteBuffer` para que C++ lea directamente de la memoria unificada del teléfono. Invocar `discardFreeBuffers` para liberar presión de RAM.
- **Empaquetado Digital:** La clase `DngCreator`. Fusiona la matriz de píxeles con la metadata específica (`TotalCaptureResult`), inyectando perfiles de calibración, iluminación y ruido.

### Fase 4: Evasión de Limitaciones de Fabricante ("Vendor Tags")
Muchos teléfonos limitan artificialmente el SDK público a exposiciones cortas.
- **Investigación NDK de Tags Ocultos:** Función nativa `ACameraMetadata_getAllTags()` en C++ para enumerar las llaves propietarias del sistema.
- **Modos de Operación:** Aprender a inyectar etiquetas ocultas para sobreescribir el modo de operación del teléfono (ej. `samsung.android.control.operation_mode`).
- **Fallback Computacional:** Si el driver bloquea rígidamente la solicitud, investigar el apilado (Stacking) de ráfagas usando filtros como Sigma-clipping.

### Fase 5: Evolución hacia Nuevos Estándares (Android 14, 15 y 16)
Asegurar que la aplicación aproveche optimizaciones recientes.
- **Low Light Boost (Android 15):** Investigar `CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY`.
- **Regla de Alineación de Memoria 16 KB (Android 15+):** Compilar módulos en C++ y librerías externas (como OpenCV) para que soporten paginación de memoria a 16 KB.
- **Exposición Híbrida y CCT (Android 16):** Controles híbridos para fijar ISO y dejar flotar exposición, además de definir color en Kelvin (CCT).
- **Ultra HDR y JPEG_R (Android 14):** Incrustar mapas de ganancia de iluminación mediante el nuevo estándar Ultra HDR.

---

## 🔍 Etiquetas Claves de Búsqueda (Keywords)
- `CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR`
- `CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEVEL_3`
- `ACameraMetadata_getConstEntry vendor tags`
- `ImageFormat.RAW_SENSOR AND DngCreator`
- `AHardwareBuffer zero-copy camera2`
- `SessionConfiguration and setPhysicalCameraId`
- `CaptureRequest.SENSOR_FRAME_DURATION bypass`
- `Camera2 NDK C++ capture RAW`
- `android.logicalMultiCamera.sensorSyncType`

## 🎯 Prioridad Inmediata
Hito 1: Ejecutar la Fase 1 y 2.
- Lograr que el `CameraManager` detecte si la máquina soporta hardware LEVEL_3 o FULL.
- Lograr imprimir por log todos los límites en nanosegundos para exposición (`SENSOR_INFO_EXPOSURE_TIME_RANGE`) e ISO puro (`SENSOR_MAX_ANALOG_SENSITIVITY`).
