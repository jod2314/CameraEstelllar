# Módulo NDK & OpenCV - Visión Computacional Nativa (Estándares 2025)

## Compatibilidad y Memoria (16 KB Alignment)
- **Compilación Obligatoria:** Todas las librerías compartidas (`.so`) deben compilarse con banderas de alineación a 16 KB (`-Wl,-z,max-page-size=16384`) en `CMakeLists.txt` para evitar crashes en dispositivos con procesadores modernos (Android 15+).
- **Asignación Dinámica:** Nunca uses tamaños de página hardcoded (como 4096 bytes). Usa `sysconf(_SC_PAGESIZE)` para reservas de memoria mapeada.

## Arquitectura JNI y Zero-Copy Moderno
- **AHardwareBuffer:** Para máxima eficiencia (Zero-Copy real entre el sensor, NDK y Vulkan), extrae los punteros directamente mediante `AImage_getHardwareBuffer` en lugar de copiar bytes a la memoria RAM de la JVM.
- **Memory Pooling:** Pre-asignación de memoria nativa para evitar `malloc/free` durante el procesamiento masivo de la ráfaga.

## Alineación Estelar (Registration)
- **Extracción de Centroides:** Cálculo de IWC (Intensity Weighted Center) con precisión sub-píxel.
- **Asterismos:** Construcción de triángulos de estrellas para emparejamiento geométrico.
- **RANSAC:** Detección y rechazo de valores atípicos (satélites, hot-pixels) en el cálculo de homografía.

## OpenCV Pipeline
1. Procesamiento directo sobre memoria mapeada (evitando TLB misses).
2. Reducción Bayer (Promedio 2x2).
3. Umbralización adaptativa para detección de blobs.
4. Búsqueda en KD-Tree para matching de asterismos.
5. Salida: Matriz de Homografía 3x3.
