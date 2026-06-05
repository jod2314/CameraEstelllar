# PROXIMA_TAREA: Corregir Persistencia Cruzada de Test de Bypass entre Sensores

## Objetivo Claro
Resolver el bug de estado en Jetpack Compose que hace que los resultados del test de bypass de exposición, ISO y exposición manual se mantengan o se propaguen a otros sensores al cambiar de cámara en `CameraScreen.kt`, asegurando que cada sensor mantenga su propio estado aislado mediante el uso correcto de `remember(cameraId)`.

---

## Pasos Técnicos

### Paso 1: Modificar estados en CameraScreen.kt
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [CameraScreen.kt](file:///C:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp) (Nota: la ruta real es [CameraScreen.kt](file:///C:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/compose/CameraScreen.kt))
*   **Criterio de Aceptación:** Modificar la definición de las variables de estado `savedMax`, `currentIso`, `currentExposure`, `currentBurst` y `currentTimer` en `CameraScreen.kt` para utilizar `remember(cameraId)` en lugar de `remember` genérico.
*   **Punto de Rollback:** Revertir los cambios en `CameraScreen.kt` mediante Git.

### Paso 2: Verificar Compilación de la Aplicación
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (compilación)
*   **Criterio de Aceptación:** Compilar el proyecto con `./gradlew :app:assembleDebug` y cerciorarse de que no haya errores de compilación de Kotlin ni Compose.
*   **Punto de Rollback:** No aplica.

### Paso 3: Ejecutar Validación del Gate de Testing y Commit
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (ejecución de scripts)
*   **Criterio de Aceptación:** Correr el script `.agents/scripts/run_tests.ps1` exitosamente para garantizar que todas las pruebas y validaciones de Android Lint pasen limpiamente. Realizar commit con la descripción correspondiente en español.
*   **Punto de Rollback:** Revertir los cambios mediante Git.
