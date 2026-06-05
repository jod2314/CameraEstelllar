# Walkthrough: Configuración del Protocolo de Agentes para Android

Este documento detalla el trabajo realizado para adaptar el protocolo de agentes web e inicializar el desarrollo orquestado en el proyecto de Android **CameraStellar v3**.

## Cambios Realizados

### 1. Reglas Permanentes de Desarrollo
*   Se creó [GEMINI.md](file:///c:/camerastelllarv3/GEMINI.md) en la raíz del proyecto para definir los principios del repositorio (responder siempre en español, arquitectura MVVM/Clean, control de hilos en Coroutines y prevención de fugas de memoria).
*   Se especificó un prompt adaptado para el **Code Review Agent (Android Edition)**.

### 2. Infraestructura de Agentes
*   Se creó [.agents/stack.config.md](file:///c:/camerastelllarv3/.agents/stack.config.md) detallando el stack tecnológico real (Kotlin 2.0.21, Hilt 2.50, CMake 3.22.1, etc.).
*   Se creó [.agents/AGENT_CATALOG.md](file:///c:/camerastelllarv3/.agents/AGENT_CATALOG.md) catalogando los subagentes disponibles (`android_architect`, `camera2_expert`, `ndk_expert` y `code_reviewer`).

### 3. Validación y Pruebas
*   Se implementó [.agents/scripts/run_tests.ps1](file:///c:/camerastelllarv3/.agents/scripts/run_tests.ps1) en PowerShell para Windows.
*   **Correcciones en el código para pasar el build:**
    *   **Compose:** Se corrigió un error de sintaxis en `CameraScreen.kt` cambiando `RowArrangement.SpaceEvenly` por `Arrangement.SpaceEvenly`.
    *   **Unit Tests:** Se eliminó un archivo de test de instrumentación obsoleto (`InstrumentedTests.kt`) mal ubicado en el directorio de pruebas unitarias locales (`src/test/`), y se recreó correctamente en `src/androidTest/` con su paquete e imports correspondientes (`com.stelllar.camera`).
    *   **Android Lint (Recursos):** Se creó el layout por defecto `layout/fragment_camera.xml` para evitar crasheos en modo portrait y pasar las advertencias críticas de Lint.
    *   **Android Lint (API):** Se suprimió la advertencia de `OutputConfiguration` (API 24+) en minSdkVersion 21 mediante `@SuppressLint("NewApi")` en la clase `SensorProber.kt`.

## Resultados de Validación
*   Se ejecutó el script `run_tests.ps1` localmente y todas las pruebas compilaron y pasaron con éxito.
