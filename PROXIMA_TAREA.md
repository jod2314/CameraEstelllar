# PROXIMA_TAREA: Fase 3 - Detección, Alineación RANSAC y Stacking en C++

## Objetivo Claro
Implementar la lógica en C++ (OpenCV) para la detección de estrellas con precisión subpíxel en el plano L monocromo de bloques 2x2, estimar la alineación geométrica mediante correspondencias con RANSAC, interpolar de forma bicúbica los 4 planos de color CFA de manera independiente, y acumular las imágenes alineadas en memoria nativa.

---

## Pasos Técnicos

### Paso 1: Definir variables de estado y la función auxiliar de plano L [x]
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** Declarar `g_aligned_light_frames`, `g_reference_stars` y `g_is_reference_set` en `StackerSession`. Programar la función `createSuperPixelLPlane` para convertir el mosaico Bayer de 16 bits de $W \times H$ a una `cv::Mat` de luminancia de 16 bits y tamaño $W/2 \times H/2$ promediando bloques de $2 \times 2$.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp` mediante Git.

### Paso 2: Implementar detección de estrellas y centroides subpíxel [x]
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** Implementar la función `detectStarsAndCentroids` que calcule la mediana y desviación estándar global de la imagen para determinar un umbral adaptativo local, detecte máximos locales de intensidad y refine sus posiciones a precisión subpíxel mediante momentos de primer orden sobre una ventana de $5 \times 5$.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp` mediante Git.

### Paso 3: Estimar la matriz afín de alineación mediante RANSAC [x]
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** En `processLightFrame`, si `g_is_reference_set` es falso, registrar las estrellas detectadas como la referencia de la sesión. De lo contrario, emparejar las estrellas detectadas en el frame actual con las de referencia mediante el vecino más cercano en un radio de búsqueda, y estimar la matriz afín rígida $2 \times 3$ con `cv::estimateAffinePartial2D` filtrado con RANSAC.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp` mediante Git.

### Paso 4: Implementar la alineación por canal y recomposición Bayer [x]
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** [native_stacker.cpp](file:///c:/camerastelllarv3/app/src/main/cpp/native_stacker.cpp)
*   **Criterio de Aceptación:** En `processLightFrame`, dividir el frame actual de 16 bits en sus 4 canales de color independientes de tamaño $W/2 \times H/2$, aplicar a cada uno `cv::warpAffine` con la matriz afín calculada usando interpolación bicúbica (`cv::INTER_CUBIC`), intercalar los canales en un nuevo frame Bayer de $W \times H$ y almacenarlo en `g_aligned_light_frames`.
*   **Punto de Rollback:** Revertir cambios en `native_stacker.cpp` mediante Git.

### Paso 5: Ejecutar validación del Gate de Testing
*   **Asignado a:** `self` (Orquestador)
*   **Archivos a modificar:** Ninguno (ejecución del script).
*   **Criterio de Aceptación:** Correr el script `.agents/scripts/run_tests.ps1` exitosamente asegurando que CMake compile el módulo de OpenCV sin errores y que las firmas JNI se enlacen limpiamente sin warnings ni fallos de Android Lint.
*   **Punto de Rollback:** No aplica.
