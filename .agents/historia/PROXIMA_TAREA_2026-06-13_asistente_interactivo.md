# PROXIMA_TAREA: Creación del Script de Asistencia Continua (asistente_telegram.ps1)

## Objetivo Claro
Implementar el script interactivo alternativo `asistente_telegram.ps1` en la carpeta `/telegram` que funcione mediante un bucle continuo de 2 segundos para procesar comandos avanzados (/ayuda, /informes, /nota [texto], /recuerdame [minutos] [mensaje], y /cmd [comando]), usando el Token y ChatId correctos y rutas portables.

---

## Pasos Técnicos

### Paso 1: Crear asistente_telegram.ps1 en /telegram
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [NEW] [asistente_telegram.ps1](file:///C:/camerastelllarv3/telegram/asistente_telegram.ps1)
*   **Criterio de Aceptación:** Implementar un bucle infinito `while($true)` que lea `telegram_state.json` en cada ciclo (cada 2 segundos), consulte `getUpdates`, filtre por ChatId y ejecute comandos avanzados:
    *   `/ayuda`: Menú de ayuda.
    *   `/informes`: Reporte de compilación/Lint del proyecto.
    *   `/nota [texto]`: Guarda una nota en `telegram/notas.txt`.
    *   `/recuerdame [minutos] [mensaje]`: Programa un recordatorio asíncrono que envía un mensaje tras $N minutos.
    *   `/cmd [comando]`: Ejecuta comandos de consola en la PC local y devuelve la salida por Telegram de forma remota.
*   **Punto de Rollback:** Eliminar `asistente_telegram.ps1` mediante Git.

### Paso 2: Verificar sintaxis del script en PowerShell
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (validación local)
*   **Criterio de Aceptación:** Asegurar que el script compile y que los comandos locales no contengan errores léxicos o sintácticos de PowerShell en Windows.
*   **Punto de Rollback:** Revertir cambios mediante Git.

### Paso 3: Sincronizar en Git e Hitos
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [HITOS.md](file:///C:/camerastelllarv3/docs/HITOS.md), [CHANGELOG.md](file:///C:/camerastelllarv3/docs/CHANGELOG.md), [walkthrough.md](file:///C:/camerastelllarv3/docs/walkthrough.md)
*   **Criterio de Aceptación:** Añadir `asistente_telegram.ps1` en master y registrar el hito con su hash de commit.
*   **Punto de Rollback:** Resetear Git al último commit estable.
