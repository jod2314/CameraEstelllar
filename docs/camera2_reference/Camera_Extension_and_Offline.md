# CameraExtensionSession y CameraOfflineSession

## Descripción General
`CameraExtensionSession` (Android 12+) permite acceso a efectos del fabricante (ej. Modo Noche, HDR). `CameraOfflineSession` permite capturas asíncronas de larga duración.

## Métodos Técnicos Analizados

### CameraExtensionSession
`void capture(CaptureRequest request, Executor executor, CameraExtensionSession.ExtensionCaptureCallback callback)`
- **Descripción:** Ejecuta una captura con el "Modo Noche" nativo del fabricante.
- **Importancia:** Útil para comparar nuestro procesado RAW contra el procesado computacional nativo de marcas como Samsung o Google (Pixel).

### CameraOfflineSession
`void switchToOffline(Collection<Surface> offlineSurfaces, Executor executor, CameraOfflineSession.CameraOfflineSessionCallback callback)`
- **Descripción:** Desconecta la sesión del visor y permite que el hardware termine de procesar las imágenes en segundo plano.
- **Caso de Uso:** Captura masiva de ráfagas para apilado sin que la UI se bloquee.

## Hipervínculos y Referencias Externas
1. **Camera Concepts:** [Camera Extensions Guide](https://developer.android.com/training/camera2/camera-extensions)
    - *Transcripción:* Guía sobre cómo implementar el modo nocturno del fabricante sin gestionar manualmente el RAW.
2. **Technical Deep-dive:** [Offline Processing Model](https://source.android.com/devices/camera/offline-sessions)
    - *Transcripción:* Explicación de cómo el HAL gestiona los buffers de memoria cuando la aplicación se mueve al fondo durante una larga exposición.
