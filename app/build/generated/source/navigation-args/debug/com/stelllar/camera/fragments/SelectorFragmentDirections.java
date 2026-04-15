package com.stelllar.camera.fragments;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavDirections;
import com.stelllar.camera.R;
import java.lang.IllegalArgumentException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.HashMap;

public class SelectorFragmentDirections {
  private SelectorFragmentDirections() {
  }

  @NonNull
  public static ActionSelectorToCamera actionSelectorToCamera(@NonNull String cameraId,
      int pixelFormat) {
    return new ActionSelectorToCamera(cameraId, pixelFormat);
  }

  public static class ActionSelectorToCamera implements NavDirections {
    private final HashMap arguments = new HashMap();

    @SuppressWarnings("unchecked")
    private ActionSelectorToCamera(@NonNull String cameraId, int pixelFormat) {
      if (cameraId == null) {
        throw new IllegalArgumentException("Argument \"camera_id\" is marked as non-null but was passed a null value.");
      }
      this.arguments.put("camera_id", cameraId);
      this.arguments.put("pixel_format", pixelFormat);
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public ActionSelectorToCamera setCameraId(@NonNull String cameraId) {
      if (cameraId == null) {
        throw new IllegalArgumentException("Argument \"camera_id\" is marked as non-null but was passed a null value.");
      }
      this.arguments.put("camera_id", cameraId);
      return this;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public ActionSelectorToCamera setPixelFormat(int pixelFormat) {
      this.arguments.put("pixel_format", pixelFormat);
      return this;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public ActionSelectorToCamera setPhysicalCameraId(@Nullable String physicalCameraId) {
      this.arguments.put("physical_camera_id", physicalCameraId);
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    @NonNull
    public Bundle getArguments() {
      Bundle __result = new Bundle();
      if (arguments.containsKey("camera_id")) {
        String cameraId = (String) arguments.get("camera_id");
        __result.putString("camera_id", cameraId);
      }
      if (arguments.containsKey("pixel_format")) {
        int pixelFormat = (int) arguments.get("pixel_format");
        __result.putInt("pixel_format", pixelFormat);
      }
      if (arguments.containsKey("physical_camera_id")) {
        String physicalCameraId = (String) arguments.get("physical_camera_id");
        __result.putString("physical_camera_id", physicalCameraId);
      } else {
        __result.putString("physical_camera_id", null);
      }
      return __result;
    }

    @Override
    public int getActionId() {
      return R.id.action_selector_to_camera;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public String getCameraId() {
      return (String) arguments.get("camera_id");
    }

    @SuppressWarnings("unchecked")
    public int getPixelFormat() {
      return (int) arguments.get("pixel_format");
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public String getPhysicalCameraId() {
      return (String) arguments.get("physical_camera_id");
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
          return true;
      }
      if (object == null || getClass() != object.getClass()) {
          return false;
      }
      ActionSelectorToCamera that = (ActionSelectorToCamera) object;
      if (arguments.containsKey("camera_id") != that.arguments.containsKey("camera_id")) {
        return false;
      }
      if (getCameraId() != null ? !getCameraId().equals(that.getCameraId()) : that.getCameraId() != null) {
        return false;
      }
      if (arguments.containsKey("pixel_format") != that.arguments.containsKey("pixel_format")) {
        return false;
      }
      if (getPixelFormat() != that.getPixelFormat()) {
        return false;
      }
      if (arguments.containsKey("physical_camera_id") != that.arguments.containsKey("physical_camera_id")) {
        return false;
      }
      if (getPhysicalCameraId() != null ? !getPhysicalCameraId().equals(that.getPhysicalCameraId()) : that.getPhysicalCameraId() != null) {
        return false;
      }
      if (getActionId() != that.getActionId()) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + (getCameraId() != null ? getCameraId().hashCode() : 0);
      result = 31 * result + getPixelFormat();
      result = 31 * result + (getPhysicalCameraId() != null ? getPhysicalCameraId().hashCode() : 0);
      result = 31 * result + getActionId();
      return result;
    }

    @Override
    public String toString() {
      return "ActionSelectorToCamera(actionId=" + getActionId() + "){"
          + "cameraId=" + getCameraId()
          + ", pixelFormat=" + getPixelFormat()
          + ", physicalCameraId=" + getPhysicalCameraId()
          + "}";
    }
  }
}
