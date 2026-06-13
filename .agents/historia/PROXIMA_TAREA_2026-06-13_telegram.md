# PROXIMA_TAREA: Inicialización de Sistema Bidireccional de Telegram y Guía de Integración

## Objetivo Claro
Configurar la estructura modular y los scripts de PowerShell para la integración bidireccional con Telegram en la carpeta `/telegram` del proyecto, usando rutas relativas robustas para portabilidad, y crear una guía técnica para que el usuario pueda cambiar e integrar cualquier bot de Telegram diferente en el futuro.

---

## Pasos Técnicos

### Paso 1: Crear telegram_state.json en la subcarpeta /telegram
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [NEW] [telegram_state.json](file:///C:/camerastelllarv3/telegram/telegram_state.json)
*   **Criterio de Aceptación:** Inicializar el archivo JSON con el Token y ChatId proporcionados por el usuario, y un LastUpdateId inicial en 0.
*   **Punto de Rollback:** Eliminar el archivo si falla.

### Paso 2: Crear send_reply.ps1 con rutas relativas
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [NEW] [send_reply.ps1](file:///C:/camerastelllarv3/telegram/send_reply.ps1)
*   **Criterio de Aceptación:** Escribir el script de PowerShell para envío de mensajes de texto estructurado de forma que detecte dinámicamente `telegram_state.json` mediante `$PSScriptRoot` (haciendo el script portable y evitando rutas absolutas de otras máquinas).
*   **Punto de Rollback:** Eliminar el archivo si falla.

### Paso 3: Crear send_files.ps1 con rutas relativas
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [NEW] [send_files.ps1](file:///C:/camerastelllarv3/telegram/send_files.ps1)
*   **Criterio de Aceptación:** Escribir el script de PowerShell para envío de archivos adjuntos y reportes en PDF usando `curl.exe` y resolviendo la ruta de configuración con `$PSScriptRoot`.
*   **Punto de Rollback:** Eliminar el archivo si falla.

### Paso 4: Crear guía de migración paso a paso
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [NEW] [GUIA_MIGRACION_BOT.md](file:///C:/camerastelllarv3/telegram/GUIA_MIGRACION_BOT.md)
*   **Criterio de Aceptación:** Redactar una guía clara en español que explique al usuario los pasos exactos para crear un bot diferente con `@BotFather`, obtener su token y chat ID, y actualizar la configuración local.
*   **Punto de Rollback:** Eliminar el archivo si falla.

### Paso 5: Commit de los cambios en Git
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Modificaciones en el historial de Git (commits y docs/HITOS.md)
*   **Criterio de Aceptación:** Sincronizar todos los archivos creados en la rama master y registrar el hito.
*   **Punto de Rollback:** Resetear la rama local a la última versión limpia mediante Git.
