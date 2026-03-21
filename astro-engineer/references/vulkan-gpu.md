# Módulo Vulkan - Aceleración por GPU (Compute Shaders)

## Estrategia de Cómputo
- **Zero-Copy:** `AHardwareBuffer` + `VK_ANDROID_external_memory_android_hardware_buffer`.
- **Precisión:** Uso de `float16_t` (FP16) para duplicar el rendimiento en GPUs móviles (Adreno/Mali).
- **Pipeline:** Compute Pipeline asíncrono puro (sin rasterización).

## Kernels (SPIR-V)
- **Calibración:** Sustracción de Dark frames y división por Flat fields.
- **Reproyección:** Aplicación de la matriz de homografía con interpolación bilineal por hardware.
- **Stacking:** Acumulador paralelo con Sigma-Clipping para rechazo de satélites.
