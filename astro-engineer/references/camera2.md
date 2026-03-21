# Módulo Camera2 - Captura RAW de Alta Precisión

## Directrices de Configuración
- **Formato:** `ImageFormat.RAW_SENSOR`.
- **Modo 3A:** `CONTROL_MODE_OFF` para bloqueo total de exposición, enfoque y balance de blancos.
- **Ráfagas:** Uso de `captureBurst` con `CaptureRequest.Builder` basado en `TEMPLATE_MANUAL`.
- **Metadatos:** Uso obligatorio de `DngCreator` vinculando `TotalCaptureResult` específico de cada frame.

## Parámetros Críticos
- `SENSOR_EXPOSURE_TIME`: Nanosegundos exactos (Regla de los 500 corregida).
- `SENSOR_SENSITIVITY`: ISO Nativo para evitar ruido de cuantización digital.
- `LENS_FOCUS_DISTANCE`: 0.0f para infinito.

## Gestión de Memoria
- Utilizar `ImageReader` con un pool de buffers limitado (maxImages: 3-5).
- Cerrar cada `Image` inmediatamente tras procesar en el `HandlerThread`.
