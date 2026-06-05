# PROXIMA_TAREA: Fase 1 - Optimización de Gestión de Memoria y Buffer (Android/Kotlin)

## Objetivo Claro
Modificar el sistema de buffers del controlador de cámara en Kotlin para limitar los canales de imagen, asegurar la liberación síncrona y robusta de memoria de cada toma, y sentar las bases estables de memoria para evitar crasheos por OutOfMemory durante ráfagas de apilamiento RAW.

---

## Pasos Técnicos

### Paso 1: Limitar la capacidad del buffer de imagen en CameraControllerImpl
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [CameraControllerImpl.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/data/camera/CameraControllerImpl.kt)
*   **Criterio de Aceptación:** Modificar el canal de imágenes a `capacity = 3` o aplicar un buffer limitado (`Channel.RENDEZVOUS` / `capacity = 3` con descarte o bloqueo seguro).
*   **Punto de Rollback:** Revertir cambios en `CameraControllerImpl.kt` mediante Git.

### Paso 2: Asegurar la liberación síncrona de ImageReader
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [CameraControllerImpl.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/data/camera/CameraControllerImpl.kt)
*   **Criterio de Aceptación:** Verificar que cada objeto `Image` devuelto por el `ImageReader` se cierre explícita y síncronamente con `image.close()` en el hilo de fondo correspondiente inmediatamente después de extraer sus datos o pasar su buffer a la cola nativa.
*   **Punto de Rollback:** Revertir cambios en `CameraControllerImpl.kt`.

### Paso 3: Ejecutar Verificación y Pruebas Locales
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (ejecución del script).
*   **Criterio de Aceptación:** Correr el script `.agents/scripts/run_tests.ps1` exitosamente sin fallos de compilación ni de Lint en Kotlin/Android.
*   **Punto de Rollback:** No aplica.
