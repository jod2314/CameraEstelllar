# CameraStellar v3 - Registro de Proyecto

Este archivo sirve como memoria del proyecto para el desarrollo de la aplicación Android CameraStellar.

## Resumen del Proyecto
- **Nombre:** CameraStellar v3
- **Plataforma:** Android (Nativo)
- **Lenguaje Principal:** Kotlin
- **Paquete:** `com.stellar.camera`
- **Sistema de Construcción:** Gradle
- **Objetivo:** App creativa para captura del cielo nocturno con múltiples modos de captura, superposición/apilado (stacking), correcciones basadas en métricas estandarizadas y detección avanzada de hardware.

## Estado Actual
- **Fase 0 (Investigación Exhaustiva):** Completada. Se ha documentado la hoja de ruta definitiva en `FASE_0_ROADMAP.md`.
- **Fase 1 y 2 (Auditoría de Hardware y Override Manual):** Implementado en `CameraController V3.1`. Se añadieron logs detallados para exposición, ISO, y detección correcta de niveles FULL y LEVEL_3.
- **Hito 1 (Cimentación):** Completado. App Nativa Kotlin con MVVM y Red Light Mode funcional.
- **Hito 2 (Motor de Captura Robusto):** Completado. `CameraController V3.1` con negociación HAL v2, fallback automático y soporte inclusivo de sensores (RAW y Manual de nivel FULL).
- **Hito 2.5 (UI Profesional):** Completado. Controles dinámicos de ISO, Exposición (1s-30s) y Ráfaga adaptativa.
- **Infraestructura:** Skill `astro-engineer` activo y motor de captura validado como instrumento científico fiable.

## Logros Técnicos
- **Escaneo Inclusivo de Sensores:** Algoritmo que detecta sensores físicos individuales con soporte RAW o Manual avanzado, evitando descartar hardware útil en gama media.
- **Negociación HAL v2 Segura:** Implementación de `SessionParameters` con validación de FPS y fallback a modo básico si la configuración avanzada es rechazada por el dispositivo.
- **Safe Parameter Injection:** Sistema de verificación de `availableCaptureRequestKeys` para evitar excepciones por parámetros no soportados por el sensor.
- **Sincronización de Timestamps:** Mecanismo de emparejamiento por nanosegundos entre buffers de imagen y resultados de metadatos del sensor.
- **Soporte Híbrido DNG/JPEG:** Guardado automático en formato DNG científico para sensores RAW y JPEG de alta fidelidad para sensores secundarios.
- **Acceso Directo a Sensores Físicos:** Bypass de cámaras lógicas para obtener la señal pura de cada lente (Ultra-Wide, Telephoto).

## Bitácora Arquitectónica y Correcciones Críticas
- **[2024-03-25] Sincronización HAL / AIDL para Larga Exposición:** Se ha diagnosticado y documentado un fallo crítico donde el sensor recorta exposiciones largas (ej. 4s a 0.2s). Esto se debe a las restricciones introducidas en el AIDL Camera HAL (Android 13+).
  - **Causa:** El tiempo de exposición (`SENSOR_EXPOSURE_TIME`) está estrictamente limitado por la duración del marco de la sesión (`SENSOR_FRAME_DURATION`). Si la sesión se crea sin parámetros explícitos, el HAL impone un límite de FPS alto (ej. 30 FPS, ~33ms max frame duration).
  - **Solución Obligatoria (Patrón Open Camera):**
    1. **Session Parameters:** Usar `SessionConfiguration.setSessionParameters()` *antes* de crear la sesión, preferiblemente con `TEMPLATE_MANUAL`.
    2. **Frame Duration:** Establecer `SENSOR_FRAME_DURATION` al máximo soportado (ej. 30s) en los parámetros de la sesión.
    3. **FPS Range:** Forzar un `CONTROL_AE_TARGET_FPS_RANGE` bajo (ej. `[1, 15]`).
    4. **Stream Use Cases (API 33+):** Definir explícitamente `STILL_CAPTURE` y `PREVIEW` para optimizar el pipeline de hardware.
    5. **Auditoría Física:** Extraer los resultados reales utilizando `TotalCaptureResult.getPhysicalCameraResult(physicalId)` o `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID` para confirmar que el silicio no recortó la exposición.

## Próximos Pasos (Fase 3 & 4)
- Integración de OpenCV nativo para alineación por asterismos.
- Implementación de Compute Shaders en Vulkan para apilamiento masivo on-device.

## Protocolos de Desarrollo
- **Regla Fundamental:** NO SE AVANZA a ninguna fase de procesamiento, IA, o características avanzadas (incluyendo C++/Vulkan) hasta que no tengamos una aplicación 100% funcional para tomar fotos. Este es el instrumento fundamental del proyecto.
- **Control de Versiones:** Cada cambio importante requiere un `git commit` con descripción concisa en español. **Eficiencia:** Optimización energética y de procesamiento desde la base.
- **Validación:** No se avanza al siguiente hito sin una base 100% funcional y libre de bugs.

## Hoja de Ruta Aprobada (Plan Maestro)
**Regla Estricta:** Ejecutar `git add .` y `git commit -m "[Hito X] Descripción"` al finalizar y validar cada fase.

1. **Fase 1: Cimentación (App Base Funcional)** - Proyecto estructurado, MVVM, gestión de permisos estricta, visor de cámara optimizado (sin memory leaks) y Git configurado.
2. **Fase 2: Motor de Captura RAW** - Control manual absoluto (Camera2), `HandlerThread` para evasión de ANRs, ráfagas continuas y guardado DNG (metadatos científicos).
3. **Fase 3: Puente NDK y Visión Computacional** - Transferencia JNI Zero-Copy, detección de estrellas (IWC) y alineación por asterismos usando OpenCV (C++).
4. **Fase 4: Motor de Apilamiento (GPU/Vulkan)** - Compute Shaders en FP16 para reproyección y Sigma-Clipping ultrarrápido on-device.
5. **Fase 5: UI Profesional y Efectos** - Red Light Mode nativo, métricas de enfoque en vivo (Varianza Laplaciana), Star Trails y procesamiento IA de cielo profundo.

## Preferencias de Desarrollo
- **Idioma:** Todas las respuestas y comentarios en el código deben ser en español.
