# PROXIMA_TAREA: Aislar Bypass de Exposición por Sensor Físico (sensorId unívoco)

## Objetivo Claro
Garantizar que las pruebas de bypass y la configuración del tiempo de exposición estén 100% aisladas e independientes para cada sensor físico individual (evitando consistencia cruzada entre sensores lógicos compartidos) mediante la resolución dinámica de un `sensorId` unívoco (`physicalCameraId ?: cameraId`).

---

## Pasos Técnicos

### Paso 1: Modificar estados en CameraScreen.kt usando sensorId
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [CameraScreen.kt](file:///C:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/compose/CameraScreen.kt)
*   **Criterio de Aceptación:** Definir `val sensorId = physicalCameraId ?: cameraId`. Reemplazar todas las referencias de `cameraId` por `sensorId` en los bloques `remember(sensorId)`, `LaunchedEffect`, `DisposableEffect` y en las llamadas de persistencia del ViewModel (`getMaxExposureNs(sensorId)` y `saveMaxExposureNs(sensorId, ...)`).
*   **Punto de Rollback:** Revertir los cambios en `CameraScreen.kt` mediante Git.

### Paso 2: Modificar obtención de bypass en GetCamerasUseCase.kt
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [GetCamerasUseCase.kt](file:///C:/camerastelllarv3/app/src/main/java/com/stelllar/camera/domain/usecase/GetCamerasUseCase.kt)
*   **Criterio de Aceptación:** Modificar la línea 94 para obtener el bypass usando `val targetId = physicalId ?: logicalId` al construir la información y subtítulo de cada sensor en la lista de selección.
*   **Punto de Rollback:** Revertir cambios en `GetCamerasUseCase.kt` mediante Git.

### Paso 3: Verificar Compilación de la Aplicación y Tests
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (compilación y pruebas)
*   **Criterio de Aceptación:** Correr el script `.agents/scripts/run_tests.ps1` exitosamente para garantizar que todas las pruebas y validaciones de Android Lint pasen limpiamente y la compilación sea exitosa.
*   **Punto de Rollback:** Revertir cambios mediante Git.
