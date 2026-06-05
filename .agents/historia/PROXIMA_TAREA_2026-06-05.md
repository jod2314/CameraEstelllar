# PROXIMA_TAREA: Fase 2 - Calibración e Ingesta JNI en C++

## Objetivo Claro
Implementar la lógica nativa en C++ para la acumulación e integración de dark frames (promedio del Master Dark) y la sustracción calibrada (Dark Frame Subtraction) en el mosaico Bayer de 16 bits sin demosaico previo, garantizando Zero-Copy en JNI.

---

## Pasos Técnicos

### Paso 1: Implementar acumulación robusta en addDarkFrame (C++)
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** Modificar `master_dark` para que sea un búfer acumulador de 32 bits (`uint32_t`) que prevenga desbordes al sumar múltiples frames. Acumular de forma segura los píxeles de 16 bits del DirectByteBuffer.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp` mediante Git.

### Paso 2: Implementar el promedio en finalizeMasterDark (C++)
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** Calcular el promedio dividiendo el acumulador de 32 bits por `num_dark_frames`, y almacenar el resultado en un búfer final de `uint16_t` para liberar la memoria de 32 bits sobrante.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp`.

### Paso 3: Implementar la sustracción en processLightFrame (C++)
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** Implementar la sustracción de píxeles en 16 bits restando el Master Dark, aplicando un pedestal constante (ej: 100 ADU) para evitar recortes a cero del ruido de lectura.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp`.

### Paso 4: Ejecutar Verificación y Pruebas Locales
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (ejecución del script).
*   **Criterio de Aceptación:** Correr el script `.agents/scripts/run_tests.ps1` exitosamente para verificar que compile el módulo nativo mediante CMake y no rompa firmas JNI en Kotlin/Java.
*   **Punto de Rollback:** No aplica.
