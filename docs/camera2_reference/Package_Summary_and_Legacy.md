# Resumen del Paquete Camera2 y API Legacy

## Descripción General
Esta sección resume el paquete `android.hardware.camera2` y la API `android.hardware.Camera` (Legacy).

## Análisis Técnico Analizado

### Package Summary (camera2)
- **Concepto Principal:** Pipeline basado en flujos de datos (Stream-based pipeline).
- **Flujo de Trabajo:**
    1. El dispositivo envía metadatos.
    2. El dispositivo recibe una solicitud (`Request`).
    3. El dispositivo produce buffers de imagen.

### CameraConstrainedHighSpeedCaptureSession
- **Uso:** Grabación de video a alta velocidad (60, 120, 240 FPS).
- **Restricción:** Solo se permiten superficies de salida (`Surface`) del mismo tamaño.

### Camera (API Antigua)
- **Estado:** Obsoleta. No soporta control manual de larga exposición ni RAW.
- **Importancia:** Solo para soporte de dispositivos extremadamente antiguos (Android 4.4 o inferior). CameraStellar v3 **no la utiliza** para garantizar calidad científica.

## Hipervínculos y Referencias Externas
1. **Camera Concepts:** [Comparing Camera1 vs Camera2](https://developer.android.com/training/camera2/camera1-vs-camera2)
    - *Transcripción:* Comparativa técnica sobre por qué el modelo asíncrono de Camera2 es superior para aplicaciones científicas.
2. **Performance Deep-dive:** [High Speed Recording Architecture](https://source.android.com/devices/camera/camera3_requests_hal#high_speed_streams)
    - *Transcripción:* Detalle sobre cómo el hardware gestiona buffers de alta velocidad para no saturar el bus de datos del sistema.
