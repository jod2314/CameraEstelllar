package com.cameraestellar;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.util.Range;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;

public class AstroCameraModule extends ReactContextBaseJavaModule {
    private final ReactApplicationContext reactContext;

    AstroCameraModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
    }

    @Override
    public String getName() {
        return "AstroCameraModule";
    }

    @ReactMethod
    public void checkConnection() {
        Log.d("AstroCamera", "Conexión nativa verificada.");
    }

    @ReactMethod
    public void getCameraCapabilities(Promise promise) {
        CameraManager manager = (CameraManager) reactContext.getSystemService(Context.CAMERA_SERVICE);
        WritableArray camerasArray = Arguments.createArray();

        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);

                // Solo nos interesa la cámara trasera (LENS_FACING_BACK = 1)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    WritableMap camInfo = Arguments.createMap();
                    camInfo.putString("id", cameraId);

                    // 1. Nivel de Hardware
                    Integer level = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    camInfo.putString("hardwareLevel", getLevelString(level));

                    // 2. Rango ISO
                    Range<Integer> isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                    if (isoRange != null) {
                        camInfo.putInt("minIso", isoRange.getLower());
                        camInfo.putInt("maxIso", isoRange.getUpper());
                    }

                    // 3. Rango Shutter (Exposición) en Nanosegundos
                    Range<Long> timeRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                    if (timeRange != null) {
                        camInfo.putDouble("minShutterSec", timeRange.getLower() / 1_000_000_000.0);
                        camInfo.putDouble("maxShutterSec", timeRange.getUpper() / 1_000_000_000.0);
                    }

                    // 4. Modos de Auto-Exposición Disponibles
                    int[] aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
                    boolean supportsManual = false;
                    WritableArray modesArray = Arguments.createArray();
                    if (aeModes != null) {
                        for (int mode : aeModes) {
                            modesArray.pushInt(mode);
                            if (mode == CameraMetadata.CONTROL_AE_MODE_OFF) {
                                supportsManual = true;
                            }
                        }
                    }
                    camInfo.putArray("aeModes", modesArray);
                    camInfo.putBoolean("supportsManualExposure", supportsManual);

                    camerasArray.pushMap(camInfo);
                }
            }
            promise.resolve(camerasArray);
        } catch (CameraAccessException e) {
            promise.reject("CAMERA_ERROR", e.getMessage());
        }
    }

    private String getLevelString(Integer level) {
        if (level == null) return "UNKNOWN";
        switch (level) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: return "LIMITED";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL: return "FULL";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: return "LEGACY";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3: return "LEVEL_3";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL: return "EXTERNAL";
            default: return "UNKNOWN (" + level + ")";
        }
    }
}