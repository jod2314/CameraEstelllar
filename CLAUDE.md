# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CameraEstelllar is a React Native astrophotography app for Android. It provides manual camera controls (ISO, long exposure up to 30s, focus distance, burst/stacking) using the Android Camera2 API with RAW/DNG output. All user-facing communication must be in **Spanish**.

## Build & Development Commands

```bash
# Install dependencies
npm install

# Start Metro bundler
npx react-native start

# Build and run on Android device (physical device required for camera)
npx react-native run-android

# Android build only (from project root)
cd android && ./gradlew assembleDebug

# Clean Android build
cd android && ./gradlew clean
```

**Build requirements:** JDK 17, Android SDK API 36, Gradle 8.13, NDK 27.1.12297006. New Architecture (Fabric) and Hermes are enabled.

## Architecture

The app follows a **React Native + Native Android Bridge** pattern:

- **App.tsx** — Main UI with manual camera parameter sliders (ISO 50–3200, exposure 0.05–30s, focus 0–1, burst 1–30) and capture controls with countdown timer.
- **AstroCamera.tsx** — Native bridge component. Wraps the native view and exposes `takePicture()` via imperative ref handle. Forwards props (iso, exposureSeconds, focusDistance, burstCount) and events (onCaptureStarted, onCaptureEnded).

### Android Native Layer (`android/app/src/main/java/com/cameraestellar/`)

- **AstroCameraView.java** — Core camera logic (~627 lines). FrameLayout + TextureView using Camera2 API with TEMPLATE_MANUAL and CONTROL_MODE_OFF. Handles RAW/DNG capture, burst sequencing, deep hardware scan for physical camera IDs (Android 9+), and manual exposure override attempts beyond hardware limits. Uses 15-slot ImageReader buffer and ConcurrentHashMap for image/metadata synchronization. Saves to DCIM/AstroCamera.
- **AstroCameraViewManager.java** — SimpleViewManager bridging React props/commands to AstroCameraView.
- **AstroCameraModule.java** — Native module exposing `getCameraCapabilities()` (ISO range, exposure range, hardware level, physical cameras).
- **AstroCameraPackage.java** — Registers the view manager and module.
- **MainApplication.kt** — Manually registers AstroCameraPackage.

### Key Technical Details

- Camera uses TEMPLATE_MANUAL with all auto modes disabled (AE, AF, AWB, scene, effects)
- Preview exposure is clamped to max 66ms to prevent UI lag
- Focus distance 0.0 = infinity (default for astrophotography)
- Hardware exposure limits exist (~0.15s on some devices); code attempts to exceed them
- Burst mode captures n sequential frames for later stacking/alignment

## Development Roadmap

Phase 1 (camera driver, RAW capture, timer, memory management) is **complete**. Phase 2 is in progress:
- Burst sequencer: **implemented**
- Star alignment algorithms: planned (centroid detection, translation/rotation matrices)
- Stacking engine: planned (average, median, lighten modes)
- Sky mode presets: planned (Deep Sky, Milky Way, Star Trails)

See `PROJECT_MEMORY.md` for detailed session state and technical notes.
