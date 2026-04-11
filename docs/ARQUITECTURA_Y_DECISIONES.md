# CameraStellar v3 - Análisis Arquitectónico y Registro de Decisiones

Este documento detalla la estructura base de la aplicación, cómo está discriminada la lógica actualmente y establece las pautas arquitectónicas para el desarrollo futuro, en concordancia con el plan maestro (`GEMINI.md`).

## 1. Estructura Actual del Proyecto

El proyecto sigue una arquitectura de **Single-Activity** (Actividad Única) soportada por el componente de Navegación de Jetpack (Navigation Component) y ViewBinding.

### Jerarquía de Paquetes Base (`app/src/main/java/com/stelllar/camera`)

*   **Raíz:**
    *   `CameraActivity.kt`: El punto de entrada principal. Actúa como contenedor (host) estricto para la navegación de fragmentos. No contiene lógica de negocio.
*   **`/fragments` (Capa de Presentación y Lógica Acoplada):**
    *   `PermissionsFragment.kt`: Gestiona la validación y solicitud de permisos críticos (Cámara, Almacenamiento, Ubicación).
    *   `SelectorFragment.kt`: Interfaz para listar y seleccionar los diferentes sensores físicos/lógicos disponibles antes de iniciar la captura.
    *   `CameraFragment.kt`: **El núcleo actual de la aplicación.** En este estado del desarrollo, concentra la inicialización de la API Camera2, la gestión de `CameraCaptureSession`, los callbacks del sensor y la actualización de la UI del visor.
    *   `ImageViewerFragment.kt`: Responsable de la visualización de las capturas (JPEG/DNG) post-procesamiento.
*   **`/utils` (Herramientas Transversales):**
    *   `AutoFitSurfaceView.kt`: Componente UI personalizado para mantener el aspect ratio correcto del visor sin distorsión.
    *   `OrientationLiveData.kt`: Proveedor de datos reactivo para detectar cambios en la orientación física del dispositivo e integrarlos en los metadatos EXIF/DNG.

## 2. Análisis de la Discriminación Lógica Actual

### Estado Presente (Acoplamiento en Fragmentos)
Actualmente, la aplicación presenta una alta concentración de responsabilidades dentro de la capa de presentación. Específicamente, `CameraFragment.kt` actúa como un "God Object" (Objeto Dios) temporal, manejando:
1.  **Ciclo de vida de la UI** (SurfaceHolder callbacks).
2.  **Lógica de Dominio** (Negociación HAL v2, configuración de parámetros RAW/Manual).
3.  **Lógica de Datos/Hardware** (Manejo de hilos `HandlerThread`, `ImageReader`, escritura de archivos DNG).

*Nota sobre `CameraController V3.1`:* Aunque la bitácora del proyecto menciona este componente como el motor de captura robusto, la exploración indica que sus conceptos (escaneo inclusivo, fallback automático) están embebidos dentro del flujo del fragmento de cámara actual o estructurados en utilidades cercanas, más que en una capa de dominio separada y abstracta.

### El Riesgo del Estado Actual
Mantener la lógica de la API Camera2 (especialmente el manejo asíncrono y los threads de captura) directamente en el Fragment generará problemas inminentes en las **Fases 3 y 4** (Puente NDK y Motor Vulkan) del roadmap, dificultando la inyección de dependencias, las pruebas unitarias y provocando fugas de memoria (memory leaks) si el ciclo de vida de la UI se destruye mientras un procesamiento en la GPU está activo.

## 3. Proyección y Decisiones Arquitectónicas (Para el Futuro)

Para alcanzar los hitos de Astrofotografía Computacional, la arquitectura **DEBE** evolucionar hacia **MVVM (Model-View-ViewModel)** o **Clean Architecture**.

### A. Capa de Presentación (UI)
*   **Componentes:** `Fragments` y `Activities`.
*   **Responsabilidad:** Únicamente observar estados (`StateFlow` / `LiveData`) emitidos por el ViewModel y renderizar la interfaz. Enviar intenciones (Intents/Events) del usuario al ViewModel (ej. "Botón de captura presionado", "ISO cambiado").
*   **Regla Estricta:** Cero lógica de cámara o de guardado de archivos.

### B. Capa de Presentación Lógica (ViewModels)
*   **Componentes:** `CameraViewModel`.
*   **Responsabilidad:** Mantener el estado de la UI de forma reactiva (ej. `isCapturing`, `currentIso`, `countdownTimer`). Coordinar la comunicación entre la UI y los Casos de Uso del Dominio.

### C. Capa de Dominio (Casos de Uso)
*   **Componentes:** `CaptureNightSkyUseCase`, `AlignStarsUseCase`.
*   **Responsabilidad:** Orquestar el flujo de la aplicación. Por ejemplo, al solicitar una captura, este caso de uso instruirá a la capa de datos que capture N imágenes en ráfaga y luego instruirá al motor NDK que las apile.

### D. Capa de Datos / Infraestructura (Hardware y NDK)
*   **Componentes:**
    *   `CameraRepository` o `CameraController`: Interfaz que abstrae **toda** la interacción con `CameraManager` y la API Camera2.
    *   `ImageProcessor` / `VulkanEngine`: Envoltorios (Wrappers) JNI para las llamadas a C++ y shaders de Vulkan.
*   **Responsabilidad:** Interacción directa con el hardware, el sistema de archivos (DNG/JPEG) y la GPU. Debe ejecutarse en hilos de fondo estables (ej. Coroutines con `Dispatchers.IO` o `Dispatchers.Default`), completamente independientes del ciclo de vida de la vista.

## 4. Registro de Decisiones Importantes (ADR - Architecture Decision Records)

*   **ADR 001 - API de Cámara:** Uso exclusivo de **Camera2 API** nativa en lugar de CameraX. *Razón:* CameraX abstrae demasiado el hardware, impidiendo el control absoluto de la duración del frame, exposición manual al nivel de nanosegundos y la inyección de `SessionParameters` necesarios para evadir los límites HAL AIDL de Android 13+.
*   **ADR 002 - Formato de Salida Principal:** **DNG (RAW)**. *Razón:* Requerimos la linealidad del sensor y la ausencia de reducción de ruido computacional del dispositivo para que nuestros propios algoritmos de apilamiento en Vulkan funcionen correctamente.
*   **ADR 003 - Interoperabilidad C++:** Uso de **JNI (Java Native Interface)** directo para comunicación con OpenCV y Vulkan. *Razón:* Rendimiento. Se evitará la copia profunda de búferes de memoria (Zero-Copy) pasando los `HardwareBuffer` o punteros directos desde el `ImageReader` al NDK.

## 5. Recomendación Inmediata de Refactorización
Antes de iniciar la Fase 3 (Puente NDK y OpenCV), se recomienda encarecidamente extraer toda la lógica de inicialización y sesión de `CameraFragment.kt` hacia una clase dedicada `CameraController` (inyectada preferiblemente vía Hilt o Dagger), dejando al fragmento únicamente con la responsabilidad de configurar el `AutoFitSurfaceView` y responder a los clics del usuario.