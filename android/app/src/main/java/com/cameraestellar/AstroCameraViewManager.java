package com.cameraestellar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

import java.util.Map;

public class AstroCameraViewManager extends SimpleViewManager<AstroCameraView> {
    public static final String REACT_CLASS = "AstroCameraView";
    public static final int COMMAND_TAKE_PICTURE = 1;

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    public AstroCameraView createViewInstance(ThemedReactContext context) {
        return new AstroCameraView(context);
    }

    @ReactProp(name = "iso")
    public void setIso(AstroCameraView view, int iso) {
        view.setIso(iso);
    }

    @ReactProp(name = "exposureSeconds")
    public void setExposureSeconds(AstroCameraView view, double seconds) {
        view.setExposure(seconds);
    }

    @ReactProp(name = "focusDistance")
    public void setFocusDistance(AstroCameraView view, float distance) {
        view.setFocusDistance(distance);
    }

    @ReactProp(name = "burstCount")
    public void setBurstCount(AstroCameraView view, int count) {
        view.setBurstCount(count);
    }

    @Override
    public Map<String, Integer> getCommandsMap() {
        return MapBuilder.of(
            "takePicture", COMMAND_TAKE_PICTURE
        );
    }

    @Override
    public Map getExportedCustomDirectEventTypeConstants() {
        return MapBuilder.builder()
            .put("topCaptureStarted", MapBuilder.of("registrationName", "onCaptureStarted"))
            .put("topCaptureEnded", MapBuilder.of("registrationName", "onCaptureEnded"))
            .build();
    }

    @Override
    public void receiveCommand(@NonNull AstroCameraView root, String commandId, @Nullable ReadableArray args) {
        Log.d(REACT_CLASS, "Comando recibido: " + commandId);
        
        // Intentar manejarlo como ID numérico primero
        try {
            int commandIdInt = Integer.parseInt(commandId);
            if (commandIdInt == COMMAND_TAKE_PICTURE) {
                root.takePicture();
                return;
            }
        } catch (NumberFormatException e) {
            // Si no es un número, verificar si es el nombre del comando
            if (commandId.equals("takePicture")) {
                root.takePicture();
                return;
            }
        }
        
        super.receiveCommand(root, commandId, args);
    }

    // Para compatibilidad con versiones anteriores de RN que usan int en lugar de String
    public void receiveCommand(@NonNull AstroCameraView root, int commandId, @Nullable ReadableArray args) {
        Log.d(REACT_CLASS, "Comando recibido (int): " + commandId);
        if (commandId == COMMAND_TAKE_PICTURE) {
            root.takePicture();
        }
    }
}
