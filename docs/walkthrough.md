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
