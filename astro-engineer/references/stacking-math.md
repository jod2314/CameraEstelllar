# Métricas y Modelos Matemáticos

## Validación de Calidad (Metrología)
- **SNR (Signal-to-Noise Ratio):** Comparación de señal estelar vs. varianza del fondo.
- **Nitidez:** Varianza del Laplaciano (pico máximo = enfoque perfecto).
- **FWHM (Full Width at Half Maximum):** Ajuste de curva Gaussiana para evaluar el "seeing".

## Algoritmos de Apilamiento
- **Sigma-Clipping:** Rechazo de píxeles > κ×σ.
- **Maximum Intensity:** Para Star Trails.
- **Mean/Median:** Mejora de SNR proporcional a √N.
