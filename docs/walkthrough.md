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

## Auditoría y Pruebas

### Fase 1 (Kotlin)
*   **Code Review:** El subagente **Code Review Agent (Android Edition)** auditó los cambios en [CameraControllerImpl.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/data/camera/CameraControllerImpl.kt) y otorgó su aprobación (**APROBADO**).
*   **Pruebas & Lint:** Verificados con éxito a nivel local.

### Fase 2 (C++)
*   **Code Review:** El subagente **Native Code Auditor** revisó [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp), verificando la seguridad en el manejo de memoria JNI, la prevención de fugas y desbordamientos, y el cumplimiento estricto de comentarios en español, otorgando su aprobación (**APROBADO**).
*   **Gradle Build, Tests & Lint:** Se ejecutó el script de verificación local `.agents/scripts/run_tests.ps1` bajo PowerShell. Compiló exitosamente el módulo nativo mediante CMake (sin discrepancias de enlazado JNI) y todas las tareas de Gradle, pruebas unitarias y el análisis de Lint Debug pasaron al 100% sin advertencias ni errores bloqueantes.

