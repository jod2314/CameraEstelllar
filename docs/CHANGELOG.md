# CHANGELOG - CameraStellar v3

Todos los cambios notables en este proyecto serán documentados en este archivo.

## [Desbloqueado] - 2026-06-04

### Añadido
- **Protocolo de Orquestación de Agentes:** Creación de [GEMINI.md](file:///c:/camerastelllarv3/GEMINI.md) adaptado para el desarrollo y ciclo de vida de agentes en Android/Kotlin.
- **Configuración del Stack:** Archivo [.agents/stack.config.md](file:///c:/camerastelllarv3/.agents/stack.config.md) especificando las versiones y herramientas autorizadas (Kotlin 2.0.21, Hilt 2.50, compileSdk 34, CMake, etc.).
- **Catálogo de Agentes:** Archivo [.agents/AGENT_CATALOG.md](file:///c:/camerastelllarv3/.agents/AGENT_CATALOG.md) definiendo los roles de `android_architect`, `camera2_expert`, `ndk_expert` y `code_reviewer`.
- **Script de Validación Local:** Script [.agents/scripts/run_tests.ps1](file:///c:/camerastelllarv3/.agents/scripts/run_tests.ps1) en PowerShell para automatizar unit tests y análisis estático con Gradle.
- **Compatibilidad de Diseño Portrait:** Archivo por defecto [layout/fragment_camera.xml](file:///c:/camerastelllarv3/app/src/main/res/layout/fragment_camera.xml) para evitar errores del compilador Lint de Android y crasheos en modo vertical.

### Corregido
- **Error de Jetpack Compose:** Corrección de `RowArrangement.SpaceEvenly` por `Arrangement.SpaceEvenly` en [CameraScreen.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/presentation/compose/CameraScreen.kt).
- **Pruebas Unitarias Fuera de Lugar:** Se reubicó el archivo [InstrumentedTests.kt](file:///C:/camerastelllarv3/app/src/androidTest/java/com/stelllar/camera/InstrumentedTests.kt) al directorio de pruebas de instrumentación correcto (`src/androidTest/java`) y se actualizaron sus dependencias y assertions obsoletas.
- **Advertencias de API en SensorProber:** Se añadió `@SuppressLint("NewApi")` en [SensorProber.kt](file:///c:/camerastelllarv3/app/src/main/java/com/stelllar/camera/data/camera/SensorProber.kt) para silenciar las advertencias de compilación de la clase `OutputConfiguration` en la API 24 ( Nougat) bajo minSdkVersion 21.
