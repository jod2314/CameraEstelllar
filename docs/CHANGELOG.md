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
- **Fase 4 - Sigma Clipping, Debayer y Exportación:** Implementación nativa en [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp) de:
  - Algoritmo de rechazo robusto de outliers mediante Sigma Clipping paralelizado (`ParallelSigmaClipping` heredando de `cv::ParallelLoopBody`) utilizando mediana y MAD con rango de $2.5 \times MAD$ y piso mínimo de $10.0$ ADU.
  - Debayer de 16 bits a BGR (`CV_16UC3`) con `cv::COLOR_BayerBG2BGR` mediante OpenCV.
  - Balance de Blancos Automático (AWB) rápido de tipo "Gray World" escalando y recortando de forma eficiente mediante la función `convertTo`.
  - Estiramiento Tonal Midtone Transfer Function (MTF) para astrofotografía ($m \approx 0.02$) optimizado mediante una tabla de búsqueda (LUT) global precargada de 65536 entradas y ejecutada en paralelo con `ParallelLUTApply`.
  - Exportación directa a Kotlin de la matriz RGBA resultante de 8 bits validando la capacidad del buffer de salida y liberando preventivamente todos los frames alineados acumulados en memoria nativa.
- **Fase 5 - Cierre de XML y Modernización Completa de UI:** Modernización arquitectónica de la interfaz de usuario en Compose puro y Compose Navigation:
  - Migración de [CameraActivity.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/CameraActivity.kt) a Compose mediante `setContent` con un `NavHost` central para la navegación declarativa del flujo.
  - Implementación de [PermissionsScreen.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/compose/PermissionsScreen.kt) en Compose con lógica asíncrona de solicitud y tema oscuro estelar.
  - Creación de [ImageViewerScreen.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/compose/ImageViewerScreen.kt) en Compose usando Glide de forma asíncrona y liberando de forma segura los recursos nativos en `onRelease` de `AndroidView`.
  - Remoción de ViewBinding y SafeArgs en [build.gradle (app)](file:///c:/camerastelllarv3/app/build.gradle) y dependencias gradle asociadas.
  - Purgado de layouts XML obsoletos (`activity_camera.xml`, `fragment_camera.xml` en retrato y landscape) y del grafo de navegación `nav_graph.xml`.
- **Corrección de Compilación NDK (Depuración Nativa):** Solución del build del módulo C++:
  - Reparación de URL de descarga rota (HTTP 404) de `opencv-mobile` actualizándola a la release estable `v30` (OpenCV 4.10.0).
  - Eliminación de la dependencia y enlace a `vulkan-lib` incompatible con `minSdkVersion 21` (API level 21).
  - Reemplazo de la función `cv::estimateAffinePartial2D` (módulo `calib3d` no disponible en `opencv-mobile` por defecto) por una alternativa analítica rígida 2D propia (`estimateRigidTransform2D`) con filtrado robusto de outliers de traslación mediana.
  - Desactivación de RTTI (`-fno-rtti`) y excepciones (`-fno-exceptions`) en `CMakeLists.txt` para solucionar errores de enlace RTTI (`typeinfo`) y remoción de bloques `try-catch` con `cv::Exception` en `native_stacker.cpp`.




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

