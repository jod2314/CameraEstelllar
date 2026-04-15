package com.stelllar.camera.data.camera;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class SensorProber_Factory implements Factory<SensorProber> {
  private final Provider<Context> contextProvider;

  private final Provider<CameraManager> cameraManagerProvider;

  public SensorProber_Factory(Provider<Context> contextProvider,
      Provider<CameraManager> cameraManagerProvider) {
    this.contextProvider = contextProvider;
    this.cameraManagerProvider = cameraManagerProvider;
  }

  @Override
  public SensorProber get() {
    return newInstance(contextProvider.get(), cameraManagerProvider.get());
  }

  public static SensorProber_Factory create(Provider<Context> contextProvider,
      Provider<CameraManager> cameraManagerProvider) {
    return new SensorProber_Factory(contextProvider, cameraManagerProvider);
  }

  public static SensorProber newInstance(Context context, CameraManager cameraManager) {
    return new SensorProber(context, cameraManager);
  }
}
