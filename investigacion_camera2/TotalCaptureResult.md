# Transcripción Técnica: TotalCaptureResult

**Descripción:**
Es el paquete final de metadatos devuelto por el hardware de la cámara tras completar una captura. A diferencia de `CaptureResult`, este objeto garantiza que todos los metadatos finales (incluyendo los de post-procesamiento) están disponibles.

**Campos Críticos para Astrofotografía:**
- **SENSOR_EXPOSURE_TIME:** Tiempo real que el sensor estuvo expuesto. Vital para confirmar si el hardware respetó los 30s pedidos o si el HAL lo recortó.
- **SENSOR_SENSITIVITY:** El valor ISO real aplicado.
- **SENSOR_TIMESTAMP:** Marca de tiempo en nanosegundos del inicio de la exposición. Fundamental para alinear frames en algoritmos de apilado (stacking).
- **SENSOR_NOISE_PROFILE:** Coeficientes de ruido (S, O) específicos del sensor para el ISO actual. Permite modelar matemáticamente el ruido para una reducción de ruido más precisa.
- **LENS_FOCUS_DISTANCE:** Distancia de enfoque. En astrofotografía debe ser consistente con el enfoque al infinito.
- **STATISTICS_LENS_SHADING_MAP:** Mapa de corrección de viñeteo. Permite corregir la caída de luz en las esquinas de los frames RAW.

**Notas de Implementación:**
- Se obtiene a través de `CameraCaptureSession.CaptureCallback#onCaptureCompleted`.
- Puede contener resultados de cámaras físicas individuales si se usa una cámara lógica (`getPhysicalCameraResults()`).

**Enlaces de Profundización Identificados:**
- [Android Camera2 Pipeline Model](https://developer.android.com/training/camera2/pipeline-model)
- [Capturing RAW Images Guide](https://developer.android.com/training/camera2/raw-capture)
