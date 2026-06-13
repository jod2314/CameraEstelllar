# PROXIMA_TAREA: Automatización y Despacho de Comandos de IA mediante Telegram (Asistente Remoto)

## Objetivo Claro
Configurar una arquitectura asíncrona de "Asistente Remoto" donde el bot de Telegram encolará las instrucciones libres del usuario y un cron periódico (`schedule`) despertará al Agente de IA (yo) cada 2 minutos para procesar las tareas pendientes (como compilar, editar código o consultar logs) y devolver los resultados directamente a tu celular.

---

## Pasos Técnicos

### Paso 1: Crear get_pending_messages.ps1 en /telegram
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [NEW] [get_pending_messages.ps1](file:///C:/camerastelllarv3/telegram/get_pending_messages.ps1)
*   **Criterio de Aceptación:** Escribir un script de PowerShell que consulte `getUpdates` con el offset correcto, filtre los mensajes del `ChatId` autorizado, guarde el nuevo `LastUpdateId` para no duplicar tareas y devuelva los mensajes nuevos en formato JSON para que el agente de IA los procese.
*   **Punto de Rollback:** Eliminar el script usando Git.

### Paso 2: Configurar el servicio de sondeo periódico de fondo (Cron Schedule)
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (configuración del cron del Sandbox de Antigravity)
*   **Criterio de Aceptación:** Programar un temporizador recurrente cron mediante la herramienta `schedule` de la plataforma para ejecutarse cada 2 minutos (`*/2 * * * *`) con un prompt que me despierte para "revisar y procesar comandos pendientes de Telegram".
*   **Punto de Rollback:** Cancelar el cron task usando `manage_task`.

### Paso 3: Instrumentar procesamiento condicional en el Agente
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (es lógica interna de mi ejecución al ser despertado)
*   **Criterio de Aceptación:** Cuando el cron me despierte, ejecutaré `get_pending_messages.ps1`. Si hay mensajes, los interpretaré como órdenes del usuario y los ejecutaré en tu PC (ej: compilar con gradle, auditar código, etc.) y le responderé a tu bot con el log del resultado.
*   **Punto de Rollback:** Detener la escucha condicional.

### Paso 4: Sincronizar en Git e Hitos
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [HITOS.md](file:///C:/camerastelllarv3/docs/HITOS.md), [CHANGELOG.md](file:///C:/camerastelllarv3/docs/CHANGELOG.md), [walkthrough.md](file:///C:/camerastelllarv3/docs/walkthrough.md)
*   **Criterio de Aceptación:** Registrar los nuevos scripts y la arquitectura del asistente remoto de Telegram en los archivos de control y empujar a master.
*   **Punto de Rollback:** Revertir los archivos modificados mediante Git.
