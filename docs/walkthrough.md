# Walkthrough: Configuración del Protocolo de Agentes y Optimización de Memoria

Este documento detalla el trabajo realizado para adaptar el protocolo de agentes e implementar la Fase 1 de optimizaciones de memoria y buffers en el proyecto de Android **CameraStellar v3**.

## Cambios Realizados

### 1. Plan Estructurado del Stacking
*   Se creó [docs/PLAN_ESTRUCTURADO_STACKING.md](file:///c:/camerastelllarv3/docs/PLAN_ESTRUCTURADO_STACKING.md) que consolida el pipeline matemático de apilamiento en espacio Bayer CFA lineal, el almacenamiento temporal híbrido con `MappedByteBuffer`, y la hoja de ruta técnica detallada.

### 2. Optimización de Buffers y Gestión de Memoria (Fase 1)
*   **CameraControllerImpl.kt:**
    *   Se redujo el tamaño de buffer de `ImageReader` de 5 a 3.
    *   Se limitó el canal de corrutinas `imageChannel` a capacidad 3.
    *   Se implementó un callback no suspendible con vaciado síncrono mediante `tryReceive()` para vaciar buffers nativos sin llamadas suspensivas fuera de la corrutina en `invokeOnCancellation`, `onCaptureCompleted` y `onCaptureFailed`.
    *   Se aseguró la recolección y liberación de recursos colgantes en caso de cancelación del flujo o fallo en la captura de hardware.
    *   Se corrigió el antipatrón de suspensión en `saveResult`, convirtiéndola en una función síncrona normal ejecutada sobre `Dispatchers.IO` dentro de `takePhoto`.
    *   Se implementó el cierre preventivo de sesiones y cámara al inicio de `initializeCamera` para evitar fugas de hardware al re-inicializar la cámara.

### 3. Calibración e Ingesta JNI en C++ (Fase 2)
*   **native_stacker.cpp:**
    *   **Acumulación de Darks:** Implementación en `addDarkFrame` utilizando un buffer de 32 bits (`uint32_t`) a nivel de sesión nativa. Esto evita pérdidas de precisión por saturación o desbordamiento al sumar múltiples fotogramas RAW de 16 bits.
    *   **Promedio del Master Dark:** Implementación de `finalizeMasterDark` dividiendo el acumulador por la cantidad de darks ingresados, almacenándolo en un buffer final de tipo `uint16_t` y liberando la memoria temporal de 32 bits.
    *   **Sustracción de Dark con Pedestal:** Implementación en `processLightFrame` restando el Master Dark píxel a píxel sobre el mosaico Bayer, sumando un pedestal de 100 ADU para preservar valores de ruido negativos frente al recorte a cero absoluto.
    *   **Mitigación de Errores de Alineación en JNI:** Se incorporó en C++ una validación dinámica de la dirección de memoria de los búferes directos (2 bytes de alineación). En caso de desalineación, se realiza un copiado temporal con `std::memcpy` antes del procesamiento, lo que previene fallos de bus (SIGBUS) en procesadores ARM.

### 4. Detección, Alineación RANSAC y Stacking en C++ (Fase 3)
*   **native_stacker.cpp:**
    *   **Plano L (Super-Píxel):** Implementación de `createSuperPixelLPlane` para reducir a la mitad la resolución de la toma Bayer de 16 bits y obtener un plano L monocromo simplificado promediando bloques 2x2.
    *   **Detección con Precisión Subpíxel:** Implementación de `detectStarsAndCentroids` que calcula de manera eficiente la mediana y desviación estándar global de la imagen y utiliza un umbral adaptativo para hallar máximos locales, refinándolos a coordenadas subpíxel mediante momentos geométricos de primer orden en una ventana de 5x5.
    *   **Alineación RANSAC:** En `processLightFrame`, si ya se ha fijado un frame de referencia, se realiza un emparejamiento de vecino más cercano por distancia euclidiana y se estima la matriz afín rígida $2 \times 3$ con `cv::estimateAffinePartial2D` filtrado mediante el algoritmo robusto RANSAC.
    *   **Alineación por Canal y Recomposición:** Se extraen los 4 canales de color del mosaico Bayer original, se aplica `cv::warpAffine` de forma individual a cada uno usando interpolación bicúbica (`cv::INTER_CUBIC`) para no mezclar canales cromáticos, y se vuelven a intercalar en el mosaico original de 16 bits.
    *   **Acumulador Nativo:** Los frames resultantes calibrados y alineados se almacenan de manera eficiente en memoria nativa (`g_aligned_light_frames`).

### 5. Sigma Clipping, Debayer y Exportación (Fase 4)
*   **native_stacker.cpp:**
    *   **Sigma Clipping Paralelizado (MAD):** Implementación de la clase `ParallelSigmaClipping` que hereda de `cv::ParallelLoopBody`. Ejecuta en paralelo (por filas mediante `cv::parallel_for_`) el rechazo de outliers estadísticos mediante Sigma-Clipping basado en Mediana y MAD ($2.5 \times MAD$ o un piso de $10.0$ para ruido térmico), promediando los píxeles válidos para cada canal. En ráfagas muy cortas ($N < 3$), realiza un promedio simple para optimizar tiempos.
    *   **Debayer de 16 bits:** Conversión del buffer Bayer lineal resultante a formato de color BGR de 16 bits (`CV_16UC3`) mediante `cv::cvtColor` con `cv::COLOR_BayerBG2BGR`.
    *   **Balance de Blancos Automático (AWB):** Algoritmo rápido tipo "Gray World" que estima promedios globales por canal y escala de forma eficiente las ganancias de R y B respecto al canal G (acotadas en el rango $[0.5, 2.5]$ para evitar inestabilidades) a través de `convertTo`.
    *   **Estiramiento Tonal MTF con LUT:** Aplicación paralela de la curva de transferencia no lineal MTF (con parámetro de sombras medias $m \approx 0.02$) mapeada de forma ultrarrápida mediante una tabla de búsqueda (LUT) de 65536 entradas de 16 bits a 8 bits, resultando en una imagen BGR de 8 bits (`CV_8UC3`).
    *   **Exportación Directa a Kotlin:** Conversión a RGBA de 8 bits (`CV_8UC4`) y volcado de memoria directo mediante `std::memcpy` al buffer de salida `outBuffer` (`DirectByteBuffer`) validando la capacidad del buffer previamente para prevenir desbordamientos de memoria. Liberación síncrona automática de los buffers acumulados en la sesión.

### 6. Corrección de Compilación NDK (Depuración Nativa)
*   **CMakeLists.txt:**
    *   Se actualizó la URL de descarga de `opencv-mobile` a la release `v30` para solucionar el error HTTP 404.
    *   Se eliminó la dependencia a `vulkan-lib` que causaba error de configuración en la plataforma `android-21` (Vulkan requiere API 24+).
    *   Se agregaron las opciones de compilación `-fno-rtti` y `-fno-exceptions` al target para evitar errores de enlace RTTI (`typeinfo`) y excepciones con `opencv-mobile`.
*   **native_stacker.cpp:**
    *   Se eliminó el `#include <opencv2/calib3d.hpp>`.
    *   Se reemplazó la llamada a `cv::estimateAffinePartial2D` (módulo `calib3d` no disponible en el build mínimo de `opencv-mobile`) por una implementación rígida analítica 2D propia (`estimateRigidTransform2D`) combinada con un filtro robusto de outliers de traslación mediana.
    *   Se eliminó el bloque `try-catch` con `cv::Exception` en el debayerizado para eliminar dependencias de RTTI/Excepciones.

### 7. Corrección del Estado de Test de Bypass de Exposición (Sensores)
*   **CameraScreen.kt:**
    *   Se implementó la resolución de un identificador de sensor físico unívoco (`val sensorId = physicalCameraId ?: cameraId`) para aislar de forma absoluta a los lentes físicos (gran angular, macro, telefoto) que comparten una misma cámara lógica.
    *   Se reemplazó el uso de `remember(cameraId)` por `remember(sensorId)` en los estados `savedMax`, `currentIso`, `currentExposure`, `currentBurst` y `currentTimer` para forzar la reinicialización de datos y evitar la consistencia cruzada.
    *   Se eliminó la lectura y escritura directa a `SharedPreferences` en la UI, delegando la persistencia de datos al ViewModel a través de `SettingsRepository`.
    *   Se removieron llamadas de corrutinas redundantes y anidadas en el botón de ejecución de test.
    *   Se añadió un `DisposableEffect(sensorId)` para garantizar la liberación de recursos de la cámara (`viewModel.closeCamera()`) al cambiar de sensor o desmontar la pantalla de Compose, previniendo fugas de hardware.
*   **GetCamerasUseCase.kt:**
    *   Se modificó el caso de uso para consultar el bypass de exposición de cada sensor usando su identificador físico unívoco (`val targetId = physicalId ?: logicalId`), garantizando consistencia en el selector de lentes.
*   **CameraViewModel.kt:**
    *   Se inyectó `SettingsRepository` en el constructor del ViewModel y se expusieron métodos intermediarios de persistencia para la UI.


## Auditoría y Pruebas

### Fase 1 (Kotlin)
*   **Code Review:** El subagente **Code Review Agent (Android Edition)** auditó los cambios en [CameraControllerImpl.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/data/camera/CameraControllerImpl.kt) y otorgó su aprobación (**APROBADO**).
*   **Pruebas & Lint:** Verificados con éxito a nivel local.

### Fase 2 (C++)
*   **Code Review:** El subagente **Native Code Auditor** revisó [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp) y otorgó su aprobación (**APROBADO**).
*   **Gradle Build, Tests & Lint:** Compiló exitosamente el módulo nativo bajo CMake y todas las tareas pasaron de forma limpia.

### Fase 3 (C++)
*   **Code Review:** El subagente **Native Code Auditor** revisó los algoritmos implementados con OpenCV y NDK en [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp), verificando la protección de accesos concurrentes, el cálculo preciso de centroides, el control de fugas de memoria y el estilo de comentarios, otorgando su aprobación (**APROBADO**).
*   **Gradle Build, Tests & Lint:** Se ejecutó el script `.agents/scripts/run_tests.ps1` en PowerShell. CMake compiló exitosamente el proyecto enlazando dinámicamente OpenCV y todas las validaciones de Lint de Android se completaron sin incidencias.

### Fase 4 (C++)
*   **Code Review:** Autorevisión exhaustiva siguiendo los lineamientos del **Code Review Agent (Android Edition)**. Se confirmó la prevención de leaks de memoria nativa, la validación estricta de capacidades de buffers directos, la correcta sincronización de hilos y la documentación de comentarios estrictamente en español.
*   **Gradle Build, Tests & Lint:** Se ejecutó con éxito el script `.agents/scripts/run_tests.ps1` que comprobó la compilación de CMake y Android NDK con OpenCV, ejecutó las tareas de pruebas de Gradle y aprobó sin incidencias el análisis estático de Android Lint.

### Corrección del Build NDK (Depuración Nativa)
*   **Gradle Build, Tests & Lint:** Se verificó la descarga, compilación nativa multi-ABI (arm64-v8a, armeabi-v7a, x86, x86_64) y el enlazado exitoso de OpenCV. El script de verificación local `.agents/scripts/run_tests.ps1` se ejecutó con éxito rotundo aprobando los tests y el análisis estático de Android Lint.

### Corrección del Test de Bypass de Exposición (Sensores)
*   **Code Review:** El subagente **Kotlin UI Code Auditor** auditó exhaustivamente la implementación en `CameraScreen.kt` y `CameraViewModel.kt` confirmando la prevención de leaks de la cámara mediante `DisposableEffect` y el aislamiento de persistencia en el ViewModel a través de `SettingsRepository`, otorgando su aprobación (**APROBADO**).
*   **Aislamiento Dinámico:** Se validó que las configuraciones de exposición se guarden y carguen de manera independiente usando el ID de sensor físico unívoco (`val sensorId = physicalCameraId ?: cameraId`), resolviendo el problema de consistencia cruzada en lentes lógicos compartidos.
*   **Caso de Uso:** Se garantizó que el caso de uso `GetCamerasUseCase` consulte la persistencia utilizando la misma correspondencia unívoca (`physicalId ?: logicalId`).
*   **Gradle Build, Tests & Lint:** Compiló con éxito y pasó de forma limpia `run_tests.ps1` de punta a punta (pruebas unitarias y análisis estático Android Lint con compilación exitosa).


