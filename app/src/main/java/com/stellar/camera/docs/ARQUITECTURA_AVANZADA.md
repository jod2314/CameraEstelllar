# Análisis Forense y Arquitectura Avanzada (CameraStellar v3)

Este documento contiene la ingeniería forense y el diseño arquitectónico de bajo nivel (HAL, Sistema y Memoria) extraído de los logs del sistema, indispensable para asegurar la estabilidad de la captura científica.

## 1. Análisis Forense de los Logs: Resolviendo el Colapso del Hardware

### 1.1. Pánico del JankMonitor y Desfase de Timestamps
- **Síntoma:** Los logs se inundan de advertencias como `JankMonitorFacade: PHOTO > abs Δ(result sensor timestamp) = 80,00 ms > 70,00 ms` y `result sensor delay = 3,00 > 1,10`.
- **Causa:** Al forzar la dilatación del `SENSOR_FRAME_DURATION` para lograr exposiciones de 20-30 segundos, el monitor de rendimiento interno de Android asume que el hilo de la cámara se ha colgado (Jank) debido a la demora extrema en la devolución del fotograma.
- **Solución (Arquitectura):** Se debe aislar el pipeline usando `SessionConfiguration` y aislar la superficie del `ImageReader` en un "Silo de Procesamiento" (un Foreground Service) para asegurar que estos timeouts del sistema no aniquilen el proceso de la aplicación en segundo plano.

### 1.2. Caídas por OIS (Estabilización Óptica)
- **Síntoma:** Error recurrente `OisListener: Null pointer for OIS data. OIS API version: 2`.
- **Causa:** Al apagar los algoritmos 3A para captura RAW manual, algunos drivers (ej. Samsung) intentan consultar el giroscopio para la estabilización óptica y reciben un puntero nulo, desestabilizando todo el flujo HAL.
- **Solución (CaptureRequest):** Inyectar explícitamente `LENS_OPTICAL_STABILIZATION_MODE_OFF` en el `CaptureRequest.Builder` para evitar que el hardware intente acceder a datos OIS inexistentes durante exposiciones montadas en trípode.

## 2. Micro-Optimización del CaptureRequest (El "Bypass" Definitivo)

Para garantizar linealidad absoluta en el archivo RAW y evadir alteraciones algorítmicas de fábrica (ISP):

- **Filtros de Procesamiento:** 
  - Forzar `EDGE_MODE` a `EDGE_MODE_OFF` (o `EDGE_MODE_ZERO_SHUTTER_LAG` si se procesa en ZSL). 
  - Forzar `NOISE_REDUCTION_MODE` a `NOISE_REDUCTION_MODE_OFF`. (El Sigma-Clipping estadístico se hará off-device/GPU).
- **Mapeo de Tonos (Tone Mapping):** Fijar `TONEMAP_MODE` a `TONEMAP_MODE_FAST` o `OFF` para impedir que el ISP comprima el rango dinámico de las estrellas antes de entregar la matriz Bayer.
- **Aislamiento del ISO Analógico:** Para evitar la amplificación artificial de ruido digital, **NUNCA** usar el límite superior de `SENSOR_INFO_SENSITIVITY_RANGE` directamente. Se debe leer la llave `SENSOR_MAX_ANALOG_SENSITIVITY` y bloquear el ISO máximo de la UI/Motor en ese valor exacto.

## 3. Aprovechando el LensShadingMap para Calibración Científica (Flat-Field Correction)

En lugar de requerir que el usuario capture "Flat Frames" (fotos a una superficie blanca iluminada uniformemente) para corregir el viñeteo del lente, utilizamos datos internos del HAL.

- **La Técnica:** 
  - Fijar `SHADING_MODE` en `SHADING_MODE_OFF` (para no alterar los píxeles destructivamente).
  - Activar `STATISTICS_LENS_SHADING_MAP_MODE_ON`.
- **Extracción:** El `TotalCaptureResult` devolverá un mapa de coma flotante de muy baja resolución (ej. 40x30) con los coeficientes exactos de atenuación para cada canal de color Bayer (R, G_even, G_odd, B).
- **Procesamiento OpenCV:** Se pasa este mapa en miniatura a C++ (OpenCV), se interpola (`cv::resize` con interpolación bicúbica) al tamaño real del RAW (ej. 12MP o 48MP) y se invierten los valores. Multiplicar esta matriz por la imagen RAW produce una Corrección de Campo Plano perfecta en tiempo real.

## 4. El Reto Crítico de Android 15: Alineación de Memoria a 16 KB

A partir de Android 15 (API 35), el kernel en dispositivos de gama alta requiere que todas las librerías nativas (`.so`) estén compiladas con páginas de memoria de 16 KB (históricamente era de 4 KB). Fallar en esto provocará que el sistema aborte la app inmediatamente.

- **Solución (Build & CMake):** Modificar el código fuente nativo y dependencias inyectando la bandera del linker: `-Wl,-z,max-page-size=16384` y en CMakeLists.txt establecer:
  ```cmake
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
  ```
- Esto optimiza drásticamente el Translation Lookaside Buffer (TLB), vital para mover ráfagas masivas de memoria RAW en astrofotografía.

## 5. Transferencia Zero-Copy y AHardwareBuffer

Para el procesamiento de Stacking (Super-Resolución multi-trama), la extracción por la vía clásica Java (`Image.getPlanes()`) es inaceptable porque provoca latencia por el recolector de basura (GC) bloqueando la app al procesar ráfagas consecutivas.

- **Configuración Cero Copias:**
  - `ImageReader` debe inicializarse con la bandera de hardware: `HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE`.
  - Capa JNI (C++): Utilizar `AHardwareBuffer_Desc` y `AImage_getHardwareBuffer()` para mapear el puntero directo a la memoria unificada.
  - Enlazar la memoria a Vulkan o un `cv::Mat` (OpenCV).
- **Resultado:** La GPU (Vulkan) y la CPU (OpenCV) procesan la reducción de ruido y alineación accediendo a la misma memoria RAM unificada donde el sensor originalmente escribió la foto, con cero copias de memoria ("Zero-Copy").
