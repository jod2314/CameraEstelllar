# PROXIMA_TAREA: Fase 5 - Cierre de XML y Modernización Completa de UI

## Objetivo Claro
Migrar la aplicación a una interfaz 100% moderna de Jetpack Compose eliminando por completo fragmentos, SafeArgs, ViewBinding y archivos de layouts XML tradicionales de Android, integrando en su lugar navegación declarativa con Compose Navigation.

---

## Pasos Técnicos

### Paso 1: Configurar dependencias en build.gradle
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [build.gradle (app)](file:///c:/camerastelllarv3/app/build.gradle)
*   **Criterio de Aceptación:** Añadir `androidx.navigation:navigation-compose:2.7.7` a las dependencias. Desactivar `viewBinding = true` de `buildFeatures`. Remover el plugin `androidx.navigation.safeargs`. Sincronizar y compilar limpiamente.
*   **Punto de Rollback:** Revertir cambios en `build.gradle` mediante Git.

### Paso 2: Crear pantallas de Permisos y Visor en Compose
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [PermissionsScreen.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/compose/PermissionsScreen.kt) [NEW], [ImageViewerScreen.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/compose/ImageViewerScreen.kt) [NEW]
*   **Criterio de Aceptación:** Crear pantallas Componibles que implementen de manera estilizada y en español la lógica de solicitud/verificación de permisos nativos de Android y la carga asíncrona de capturas JPEG/DNG en pantalla completa usando Coil o Glide mediante `AndroidView` o integración nativa.
*   **Punto de Rollback:** Eliminar los archivos creados.

### Paso 3: Migrar CameraActivity e implementar Compose Navigation
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [CameraActivity.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/CameraActivity.kt)
*   **Criterio de Aceptación:** Eliminar el uso de `ActivityCameraBinding`. Definir un `NavHost` Componible en el `setContent` con rutas tipadas para `permissions`, `selector`, `camera` y `viewer`, conectando la lógica de selección de cámara y navegación del flujo completo.
*   **Punto de Rollback:** Revertir cambios en `CameraActivity.kt` mediante Git.

### Paso 4: Purgar archivos de Fragmentos y recursos XML antiguos
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Eliminar fragmentos y XMLs de layouts antiguos.
*   **Criterio de Aceptación:** Eliminar permanentemente los fragmentos antiguos (`CameraFragment.kt`, `SelectorFragment.kt`, `PermissionsFragment.kt`, `ImageViewerFragment.kt`), los layouts XML (`activity_camera.xml`, `fragment_camera.xml`) y el grafo de navegación XML (`nav_graph.xml`).
*   **Punto de Rollback:** Recuperar archivos mediante `git checkout` o `git restore`.

### Paso 5: Ejecutar Validación del Gate de Testing
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (ejecución del script).
*   **Criterio de Aceptación:** Correr el script `.agents/scripts/run_tests.ps1` exitosamente para garantizar que la aplicación compile sin dependencias XML obsoletas, sin warnings en Android Lint, y que todos los tests unitarios pasen sin incidencias.
*   **Punto de Rollback:** No aplica.
