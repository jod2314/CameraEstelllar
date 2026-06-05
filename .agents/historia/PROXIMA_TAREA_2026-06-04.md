# PROXIMA_TAREA: Configuración Inicial de la Manera de Trabajar (Agentes Android)

## Objetivo Claro
Configurar el protocolo de agentes, los archivos de contexto permanente e inicializar la infraestructura de verificación local en el repositorio de **CameraStellar v3** para habilitar el desarrollo estructurado guiado por agentes.

---

## Pasos Técnicos

### Paso 1: Configurar GEMINI.md del proyecto
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [GEMINI.md](file:///c:/camerastelllarv3/GEMINI.md)
*   **Criterio de Aceptación:** El archivo debe contener las pautas adaptadas de desarrollo en Android/Kotlin, directrices de diseño MVVM, y el protocolo de agentes locales (incluyendo el Code Reviewer de Android).
*   **Punto de Rollback:** Revertir cambios locales en `GEMINI.md` usando Git.

### Paso 2: Crear Configuración de Stack y Catálogo de Agentes
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a crear:**
    *   [.agents/stack.config.md](file:///c:/camerastelllarv3/.agents/stack.config.md)
    *   [.agents/AGENT_CATALOG.md](file:///c:/camerastelllarv3/.agents/AGENT_CATALOG.md)
*   **Criterio de Aceptación:** Ambos archivos deben existir. `stack.config.md` debe detallar el SDK de Android, Kotlin, Camera2, NDK y Gradle. `AGENT_CATALOG.md` debe catalogar a los agentes especialistas (Android Architect, Camera2 Expert, NDK Expert, Code Reviewer).
*   **Punto de Rollback:** Eliminar la carpeta `.agents/` mediante comando de limpieza.

### Paso 3: Configurar Script de Pruebas y Validación Local
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a crear:**
    *   [.agents/scripts/run_tests.ps1](file:///c:/camerastelllarv3/.agents/scripts/run_tests.ps1)
*   **Criterio de Aceptación:** El script PowerShell debe ser ejecutable y correr las tareas de Gradle (`testDebugUnitTest` y `lintDebug`), manejando correctamente los errores de entorno si no está configurada la variable `ANDROID_HOME` o Java en el PowerShell de forma global.
*   **Punto de Rollback:** Eliminar el archivo `.agents/scripts/run_tests.ps1`.
