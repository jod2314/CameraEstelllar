# Auditoría Técnica: CameraStellar v3 Modern Architecture

## 1. Resumen Ejecutivo
Se ha realizado una transición exitosa de una arquitectura basada en hilos legacy (HandlerThread/BlockingQueue) a una arquitectura **reactiva moderna** fundamentada en **Kotlin Coroutines**, **StateFlow** e inyección de dependencias con **Hilt**. El sistema de captura ha sido auditado para maximizar el rendimiento en astrofotografía.

---

## 2. Análisis del Motor de Captura (Camera2)

### A. Gestión de Exposición (El Bypass de 30s)
*   **Estado Actual:** El sistema inyecta `30,000,000,000ns` (30 segundos) directamente en `SENSOR_EXPOSURE_TIME`.
*   **Hallazgo de Auditoría:** Investigaciones confirman que la API Camera2 recorta (clipping) cualquier valor fuera del rango reportado por `SENSOR_INFO_EXPOSURE_TIME_RANGE`.
*   **Optimización Implementada:**
    *   Sincronización obligatoria: `SENSOR_FRAME_DURATION` se iguala al tiempo de exposición. Sin esto, el hardware ignora la exposición larga para mantener los FPS.
    *   **Estrategia de Bypass Real:** Si el sensor reporta menos de 30s como límite, el bypass actual actúa como un "forzado al límite máximo". Para superar este límite físico, la arquitectura está preparada para evolucionar hacia **Burst Stacking** (captura de ráfagas RAW y suma por software).

### B. Eficiencia de Memoria
*   **Uso de Channels:** Se utiliza un `Channel<Image>(Channel.UNLIMITED)`. 
*   **Riesgo:** En ráfagas rápidas de RAW (DNG), esto podría agotar la RAM (Heap).
*   **Recomendación:** Limitar el buffer del canal a `capacity = 3` o usar `CONGESTED` para astrofotografía donde la precisión de cada frame es vital.

---

## 3. Auditoría de Estructura y Parámetros

| Componente | Estado | Evaluación |
| :--- | :--- | :--- |
| **Inyección (Hilt)** | ✅ Correcto | Singleton para el controlador, facilitando el acceso global seguro. |
| **Concurrencia** | ✅ Excelente | Uso de `Dispatchers.IO` para guardado de archivos y `Default` para procesamiento de imagen. |
| **UI State** | ✅ Correcto | `StateFlow` asegura que la UI siempre refleje el estado real de la cámara. |
| **Lifecycle** | ✅ Mejorado | Los hilos se reinician dinámicamente, corrigiendo el bug de pantalla negra. |

---

## 4. Parámetros de Cámara Auditados (Configuración Óptima)

Para garantizar el acceso a los 30 segundos si el hardware lo permite:
1.  **CONTROL_AE_MODE:** Debe estar en `OFF`. (Implementado)
2.  **CONTROL_AF_MODE:** Debe estar en `OFF` durante la captura de larga exposición para evitar que el motor de enfoque consuma ciclos de procesamiento o energía. (Implementado)
3.  **SENSOR_FRAME_DURATION:** Debe ser >= `SENSOR_EXPOSURE_TIME`. (Implementado)
4.  **BLACK_LEVEL_LOCK:** Recomendado activar para astrofotografía para evitar variaciones de nivel de negro entre tomas. (Pendiente)

---

## 5. Próximos Pasos Arquitectónicos

1.  **Capa de Dominio (UseCases):** Actualmente `CameraViewModel` habla directamente con el Repositorio. Se recomienda introducir `GetCameraStreamUseCase` y `CapturePhotoUseCase`.
2.  **Motor de Stacking:** Preparar el pipeline para recibir múltiples `Image` RAW y delegar a un `NativeStacker` (C++/OpenCV) para superar los 30s de límite físico mediante suma de frames.
3.  **Migración a Compose:** Reemplazar `XML` para reducir el acoplamiento y mejorar la reactividad de los controles manuales.

---
**Documento de Memoria del Proyecto - v3.1**
*Fecha de Auditoría: 14 de Abril de 2026*
*Arquitecto: Astro-Engineer Gemini*
