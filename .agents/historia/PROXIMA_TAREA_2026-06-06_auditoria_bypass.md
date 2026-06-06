# PROXIMA_TAREA: Auditoría y Aislamiento Completo de Bypass por sensorId Unívoco

## Objetivo Claro
Resolver de forma definitiva las colisiones de identificadores y la consistencia cruzada del bypass de exposición entre sensores físicos y lógicos mediante la unificación de la clave de persistencia (`physicalCameraId ?: cameraId`) en el caso de uso `ProbeSensorUseCase`, e instrumentar logs de auditoría exhaustivos con la etiqueta `"AUDIT"` en todo el flujo para diagnosticar y verificar el aislamiento del hardware.

---

## Pasos Técnicos

### Paso 1: Corregir e instrumentar ProbeSensorUseCase.kt
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [ProbeSensorUseCase.kt](file:///C:/camerastelllarv3/app/src/main/java/com/stelllar/camera/domain/usecase/ProbeSensorUseCase.kt)
*   **Criterio de Aceptación:** Modificar la línea 14 para guardar el bypass utilizando `val sensorId = physicalCameraId ?: cameraId` de forma que los sensores físicos con la misma cámara lógica no colisionen. Añadir un log de auditoría detallado con la etiqueta `"AUDIT"`.
*   **Punto de Rollback:** Revertir cambios en `ProbeSensorUseCase.kt` mediante Git.

### Paso 2: Agregar logs en CameraViewModel.kt
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [CameraViewModel.kt](file:///C:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/CameraViewModel.kt)
*   **Criterio de Aceptación:** Instrumentar con `android.util.Log.d("AUDIT", ...)` los métodos `saveMaxExposureNs` and `getMaxExposureNs` para auditar la clave y el valor exacto de exposición leídos/escritos por cada `sensorId`.
*   **Punto de Rollback:** Revertir cambios en `CameraViewModel.kt` mediante Git.

### Paso 3: Asegurar actualización y agregar logs en CameraScreen.kt
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [CameraScreen.kt](file:///C:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/compose/CameraScreen.kt)
*   **Criterio de Aceptación:** 
    *   Añadir logs con `android.util.Log.d("AUDIT", ...)` en la inicialización de `sensorId`, `remember(sensorId)` y durante los clicks del botón.
    *   Implementar un `LaunchedEffect(sensorId)` que fuerce la recarga de `savedMax` e inicialice `currentExposure` basándose en el sensor seleccionado activo para evitar que Compose retenga estados antiguos del sensor anterior.
*   **Punto de Rollback:** Revertir cambios en `CameraScreen.kt` mediante Git.

### Paso 4: Agregar logs en SensorProber.kt
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [SensorProber.kt](file:///C:/camerastelllarv3/app/src/main/java/com/stelllar/camera/data/camera/SensorProber.kt)
*   **Criterio de Aceptación:** Añadir logs con `android.util.Log.d("AUDIT", ...)` indicando el inicio del test, los identificadores lógico y físico, y el resultado final antes del retorno.
*   **Punto de Rollback:** Revertir cambios en `SensorProber.kt` mediante Git.

### Paso 5: Compilar y Ejecutar Validación de Pruebas
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (validación de build)
*   **Criterio de Aceptación:** Ejecutar el script local `.agents/scripts/run_tests.ps1` exitosamente para garantizar que no existan errores sintácticos, problemas de importación de `android.util.Log` o fallos en el análisis estático de Android Lint.
*   **Punto de Rollback:** Revertir todos los cambios de esta sesión mediante Git.
