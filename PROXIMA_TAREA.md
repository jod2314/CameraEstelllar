# PROXIMA_TAREA: Fase 4 - Sigma Clipping, Debayer y Exportación

## Objetivo Claro
Implementar la lógica final en C++ (OpenCV) para realizar el apilamiento robusto por Sigma Clipping (basado en MAD) paralelizado mediante hilos, aplicar debayerización de 16 bits y balance de blancos automático, realizar el estiramiento de histograma mediante el algoritmo de astrofotografía MTF y transferir los píxeles resultantes en formato RGBA de 8 bits a la capa de Kotlin.

---

## Pasos Técnicos

### Paso 1: Implementar la clase ParallelSigmaClipping [x]
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** Implementar una clase que herede de `cv::ParallelLoopBody` y que realice de manera paralela por píxel la extracción de muestras, estimación de la mediana, cálculo de MAD, filtrado a $2.5 \times MAD$ y promedio de píxeles activos, almacenándolo en una matriz de 16 bits `CV_16UC1`.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp` mediante Git.

### Paso 2: Implementar Debayer y Balance de Blancos [x]
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** Convertir el buffer de Bayer resultante a una imagen de 16 bits BGR (`CV_16UC3`) y balancear de manera automática los canales de color calculando la media relativa y multiplicadores de ganancia para R y B respecto a G.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp` mediante Git.

### Paso 3: Programar el estiramiento tonal MTF y la conversión a 8 bits [x]
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** Aplicar la ecuación no lineal MTF ($m \approx 0.02$) sobre la imagen normalizada para expandir las sombras y resaltar las estrellas sin quemar sus núcleos, y convertir la imagen resultante a BGR de 8 bits (`CV_8UC3`).
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp` mediante Git.

### Paso 4: Exportación RGBA en finalizeStacking [x]
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** Convertir de BGR de 8 bits a RGBA de 8 bits (`CV_8UC4`) y volcar los bytes resultantes directamente al `outBuffer` de salida utilizando `std::memcpy`, asegurando validaciones de capacidad para prevenir leaks y fallos de segmentación.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp` mediante Git.

### Paso 5: Ejecutar Verificación y Pruebas Locales [x]
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (ejecución del script).
*   **Criterio de Aceptación:** Correr `.agents/scripts/run_tests.ps1` exitosamente para verificar la correcta compilación nativa del pipeline y que las firmas JNI estén totalmente integradas.
*   **Punto de Rollback:** No aplica.
