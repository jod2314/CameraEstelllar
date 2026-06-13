# PROXIMA_TAREA: Implementación del Servicio de Escucha (Long Polling) para Telegram Bot

## Objetivo Claro
Implementar el script de escucha continua y procesamiento de comandos (`listen_telegram.ps1`) en la carpeta `/telegram` utilizando la técnica de Long Polling (getUpdates con offset y timeout) para recibir, validar y responder comandos de forma asíncrona sin sobrecargar la CPU, e integrar comandos de control básicos (/ping, /status, /help, /stop).

---

## Pasos Técnicos

### Paso 1: Crear el script listen_telegram.ps1 en /telegram
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [NEW] [listen_telegram.ps1](file:///C:/camerastelllarv3/telegram/listen_telegram.ps1)
*   **Criterio de Aceptación:** Escribir un bucle de PowerShell (`while($true)`) que lea `telegram_state.json`, consulte mensajes nuevos usando Long Polling a `getUpdates?offset=[LastUpdateId+1]&timeout=30`, valide el ChatId autorizado, actualice el archivo de estado de inmediato y procese comandos de forma condicional.
*   **Punto de Rollback:** Eliminar el archivo `listen_telegram.ps1` usando Git.

### Paso 2: Implementar los comandos de control básicos
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [listen_telegram.ps1](file:///C:/camerastelllarv3/telegram/listen_telegram.ps1)
*   **Criterio de Aceptación:** Agregar soporte para:
    *   `/ping`: Responde con "¡Pong! Servicio de escucha activo en la PC."
    *   `/status`: Lee y envía un breve estado del build del proyecto.
    *   `/help`: Lista los comandos disponibles.
    *   `/stop`: Detiene el script de forma segura saliendo del bucle.
*   **Punto de Rollback:** Revertir cambios en `listen_telegram.ps1`.

### Paso 3: Verificar compilación y sintaxis en PowerShell
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (validación local)
*   **Criterio de Aceptación:** Ejecutar un análisis sintáctico local del script en la consola de PowerShell para garantizar que no existan llaves sin cerrar ni variables no declaradas.
*   **Punto de Rollback:** Revertir cambios mediante Git.

### Paso 4: Sincronizar en Git e Hitos
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [HITOS.md](file:///C:/camerastelllarv3/docs/HITOS.md), [CHANGELOG.md](file:///C:/camerastelllarv3/docs/CHANGELOG.md), [walkthrough.md](file:///C:/camerastelllarv3/docs/walkthrough.md)
*   **Criterio de Aceptación:** Confirmar la adición de `listen_telegram.ps1` en la rama master y registrar el hito correspondiente con su hash de commit.
*   **Punto de Rollback:** Resetear a la última versión limpia mediante Git.
