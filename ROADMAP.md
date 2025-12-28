# Ruta de Trabajo Profesional - CameraEstellar

Esta guía detalla el proceso de inicialización para garantizar un entorno de desarrollo robusto y profesional.

## Paso 1: Instalación de Dependencias Base

### 1.1 Java Development Kit (JDK)
React Native requiere una versión específica de Java.
- **Acción:** Descargar e instalar **JDK 17** (Microsoft Build of OpenJDK o Eclipse Temurin son recomendados).
- **Verificación:** En la terminal: `java -version`. Debe decir "17.x.x".

### 1.2 Android Studio (Crucial)
Aunque programemos en VS Code, necesitamos el motor de Android.
- **Descargar:** [Android Studio Iguana (o superior)](https://developer.android.com/studio).
- **Durante la instalación:** Asegúrate de marcar "Android Virtual Device".
- **Configuración del SDK Manager (Settings > Languages & Frameworks > Android SDK):**
    1.  Pestaña **SDK Platforms**: Marca `Android 14.0 (UpsideDownCake)` o la más reciente estable.
    2.  Pestaña **SDK Tools**: Marca las siguientes casillas (¡Importante!):
        - [x] Android SDK Build-Tools
        - [x] Android SDK Command-line Tools (latest)
        - [x] Android Emulator
        - [x] Android SDK Platform-Tools
        - [x] **NDK (Side by side)** (Requerido para el motor de C++ de la cámara).
        - [x] **CMake** (Requerido para compilar código nativo).

### 1.3 Variables de Entorno (Windows)
Windows necesita saber dónde está el SDK.
- Abre "Editar las variables de entorno del sistema".
- Crea una variable de usuario llamada `ANDROID_HOME`.
- Valor: Generalmente es `C:\Users\TU_USUARIO\AppData\Local\Android\Sdk`.
- En la variable `Path`, agrega: `%ANDROID_HOME%\platform-tools`.

## Paso 2: Inicialización del Proyecto (VS Code)

### 2.1 Git
1. Abrir terminal en `C:\CameraEstelllar`.
2. Ejecutar: `git init`.
3. Crear `.gitignore` (lo haremos automáticamente).

### 2.2 React Native (CLI)
No usaremos Expo Go para tener control total.
- Comando: `npx @react-native-community/cli init CameraEstellar --version latest`
*Nota: Esto creará una subcarpeta. Moveremos los archivos a la raíz si es necesario para mantener el repo limpio.*

## Paso 3: Primera Ejecución
1. Conectar dispositivo Android físico (con Depuración USB activa) o iniciar Emulador.
2. Comando: `npm start` (Metro Bundler).
3. Comando: `npm run android`.
