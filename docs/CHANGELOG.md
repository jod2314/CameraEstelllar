# CHANGELOG - CameraStellar v3

Todos los cambios notables en este proyecto serán documentados en este archivo.

## [Desbloqueado] - 2026-06-05

### Añadido
- **Fase 2 - Calibración e Ingesta JNI en C++:** Implementación nativa en [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp) de:
  - Acumulación robusta de dark frames en un buffer de 32 bits (`uint32_t`) para evitar desbordamientos aritméticos al integrar múltiples tomas.
  - Promediado de Master Dark en un buffer final de 16 bits (`uint16_t`) con liberación de la memoria temporal del acumulador.
  - Sustracción calibrada de darks (Dark Frame Subtraction) en espacio de color lineal (RAW Bayer CFA) sin demosaico.
  - Aplicación de un pedestal de 100 ADU en la sustracción para evitar recortes arbitrarios a cero del ruido de lectura.
  - Validación dinámica de alineación a 2 bytes para punteros directos de JNI con fallback seguro mediante `std::memcpy` para mitigar fallos de bus en arquitecturas ARM.
- **Fase 3 - Detección, Alineación RANSAC y Stacking en C++:** Implementación en [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp) de:
  - Reducción de resolución a la mitad para obtener el Plano L (Luminancia) monocromo promediando bloques 2x2 (Super-Pixel).
  - Algoritmo de detección de fuentes estelares brillantes con estimación de mediana de fondo del cielo y umbral adaptativo.
  - Estimación de centroides con precisión subpíxel aplicando momentos de primer orden sobre una ventana local de 5x5.
  - Emparejamiento de asterismos estelares por vecino más cercano y estimación robusta RANSAC de la matriz afín rígida (`cv::estimateAffinePartial2D`).
  - Separación en 4 canales de color monocromos (R, Gr, Gb, B) y distorsión bicúbica independiente (`cv::warpAffine` con `cv::INTER_CUBIC`) de cada canal para evitar solapamiento de color.
  - Recomposición e intercalado del mosaico Bayer en 16 bits para guardar los frames alineados resultantes en un vector de memoria nativa.


## [Desbloqueado] - 2026-06-04


### Añadido
- **Protocolo de Orquestación de Agentes:** Creación de [GEMINI.md](file:///c:/camerastelllarv3/GEMINI.md) adaptado para el desarrollo y ciclo de vida de agentes en Android/Kotlin.
- **Configuración del Stack:** Archivo [.agents/stack.config.md](file:///c:/camerastelllarv3/.agents/stack.config.md) especificando las versiones y herramientas autorizadas (Kotlin 2.0.21, Hilt 2.50, compileSdk 34, CMake, etc.).
- **Catálogo de Agentes:** Archivo [.agents/AGENT_CATALOG.md](file:///c:/camerastelllarv3/.agents/AGENT_CATALOG.md) definiendo los roles de `android_architect`, `camera2_expert`, `ndk_expert` y `code_reviewer`.
- **Script de Validación Local:** Script [.agents/scripts/run_tests.ps1](file:///c:/camerastelllarv3/.agents/scripts/run_tests.ps1) en PowerShell para automatizar unit tests y análisis estático con Gradle.
- **Compatibilidad de Diseño Portrait:** Archivo por defecto [layout/fragment_camera.xml](file:///c:/camerastelllarv3/app/src/main/res/layout/fragment_camera.xml) para evitar errores del compilador Lint de Android y crasheos en modo vertical.
- **Plan Estructurado de Stacking:** Creación de [PLAN_ESTRUCTURADO_STACKING.md](file:///c:/camerastelllarv3/docs/PLAN_ESTRUCTURADO_STACKING.md) detallando el pipeline matemático y la hoja de ruta de desarrollo nativo.

### Corregido
- **Error de Jetpack Compose:** Corrección de `RowArrangement.SpaceEvenly` por `Arrangement.SpaceEvenly` en [CameraScreen.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/compose/CameraScreen.kt).
- **Pruebas Unitarias Fuera de Lugar:** Se reubicó el archivo [InstrumentedTests.kt](file:///C:/camerastelllarv3/app/src/androidTest/java/com/stelllar/camera/InstrumentedTests.kt) al directorio de pruebas de instrumentación correcto (`src/androidTest/java`) y se actualizaron sus dependencias y assertions obsoletas.
- **Advertencias de API en SensorProber:** Se añadió `@SuppressLint("NewApi")` en [SensorProber.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/data/camera/SensorProber.kt) para silenciar las advertencias de compilación de la clase `OutputConfiguration` en la API 24 ( Nougat) bajo minSdkVersion 21.
- **Optimización de Memoria en CameraController:** Reducción de la capacidad del buffer de ImageReader a 3 y conversión del canal de corrutinas a capacidad 3 con vaciado síncrono mediante `tryReceive()` para evitar crasheos por OutOfMemory (OOM).
- **Prevención de Leaks de Hardware:** Cierre preventivo de sesiones anteriores en `initializeCamera`, suspensión cancelable en `createCaptureSession` y bloque de cancelación/fallo robusto en `captureImage` para liberar síncronamente buffers nativos.
- **Eliminación de Antipatrón de Suspensión:** Conversión de `saveResult` a función síncrona pura corriendo sobre `Dispatchers.IO` para evitar envolver E/S bloqueante en `suspendCoroutine`.

