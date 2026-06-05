# Stack Tecnológico - CameraStellar v3

Este documento define las tecnologías, herramientas y versiones autorizadas para el desarrollo del proyecto **CameraStellar v3**.

## 📱 Entorno de Ejecución y Plataforma
*   **Plataforma:** Android Nativo
*   **Android SDK:**
    *   `compileSdk`: 34
    *   `targetSdk`: 34
    *   `minSdkVersion`: 21 (Android 5.0 Lollipop)

## 🛠️ Herramientas de Compilación y JDK
*   **JDK/Java:** Versión 17 (JVM target 17 para compilación)
*   **Sistema de Construcción:** Gradle
*   **Android Gradle Plugin (AGP):** 8.13.2
*   **C++ Compiler / CMake:**
    *   CMake: 3.22.1
    *   C++ Standard: C++17 (`-std=c++17`) con optimizaciones `-O3 -flto`
    *   STL de Android: `c++_shared`

## 🧰 Lenguajes y Frameworks Core
*   **Kotlin:** 2.0.21
*   **Kotlin Symbol Processing (KSP):** 2.0.21-1.0.27
*   **Jetpack Compose:** Habilitado (BOM `2024.06.00`), compiler integrado mediante plugin Kotlin Compose.
*   **Android ViewBinding:** Habilitado para fragmentos tradicionales.

## 📦 Dependencias y Librerías Críticas
*   **Asincronía:** Kotlin Coroutines Android (`1.7.3`)
*   **Navegación:** Jetpack Navigation Component Fragment & UI KTX (`2.7.7`) con Safe Args.
*   **Inyección de Dependencias:** Dagger Hilt (`2.50`)
*   **Carga de Imágenes:** Glide con integración KSP (`4.16.0`)
*   **Metadatos de Imagen:** AndroidX ExifInterface (`1.3.7`)
*   **Logging:** Timber (`5.0.1`)
*   **Testing:** JUnit 4 (`4.13.2`), AndroidX Test JUnit (`1.2.1`), Espresso Core (`3.6.1`)
