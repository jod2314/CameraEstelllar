# Módulo NDK & OpenCV - Visión Computacional Nativa

## Arquitectura JNI
- **Zero-Copy:** Uso de `DirectByteBuffer` y `env->GetDirectBufferAddress`.
- **Memory Pooling:** Pre-asignación de memoria nativa para evitar `malloc` en ráfagas.

## Alineación Estelar (Registration)
- **Extracción de Centroides:** Cálculo de IWC (Intensity Weighted Center) con precisión sub-píxel.
- **Asterismos:** Construcción de triángulos de estrellas para emparejamiento geométrico.
- **RANSAC:** Detección y rechazo de valores atípicos (satélites, hot-pixels) en el cálculo de homografía.

## OpenCV Pipeline
1. Reducción Bayer (Promedio 2x2).
2. Umbralización adaptativa para detección de blobs.
3. Búsqueda en KD-Tree para matching de asterismos.
4. Salida: Matriz de Homografía 3x3.
