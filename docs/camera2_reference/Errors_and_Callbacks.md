# Gestión de Errores, Callbacks y Callbacks Críticos

## Descripción General
Esta sección cubre las excepciones, fallos de captura y escuchadores de estado.

## Análisis Técnico Analizado

### CameraAccessException
- **Tipos de Error Críticos:**
    - `CAMERA_DISABLED`: Fallo por política de permisos o hardware físicamente ocupado.
    - `CAMERA_DISCONNECTED`: El cable de la cámara se desconectó internamente (común en dispositivos antiguos).

### CaptureFailure
- **Razón del Fallo:** `REASON_ERROR` o `REASON_FLUSHED`.
- **Importancia:** En ráfagas de astrofotografía, un fallo `REASON_FLUSHED` suele indicar que el buffer de hardware se llenó.

### CameraCaptureSession.CaptureCallback
`onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber)`
- **Descripción:** Es el momento exacto en que se abre el obturador electrónico. Crucial para sincronizar timestamps con los buffers RAW.

`onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)`
- **Descripción:** Notificación de que los metadatos finales están listos.

## Hipervínculos y Referencias Externas
1. **Google Groups Discussion:** [Handling Capture Failures in RAW Burst](https://groups.google.com/g/android-camera-dev)
    - *Transcripción:* Debate sobre cómo reintentar capturas fallidas en modo manual sin perder la sincronización de la ráfaga.
2. **Technical Deep-dive:** [Camera Metadata Metadata-guide](https://source.android.com/devices/camera/metadata-guide)
    - *Transcripción:* Guía para entender cada clave de metadatos devuelta en el `CaptureCallback`.
