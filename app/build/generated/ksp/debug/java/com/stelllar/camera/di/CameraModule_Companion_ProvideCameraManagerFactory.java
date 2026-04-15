package com.stelllar.camera.di;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class CameraModule_Companion_ProvideCameraManagerFactory implements Factory<CameraManager> {
  private final Provider<Context> contextProvider;

  public CameraModule_Companion_ProvideCameraManagerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public CameraManager get() {
    return provideCameraManager(contextProvider.get());
  }

  public static CameraModule_Companion_ProvideCameraManagerFactory create(
      Provider<Context> contextProvider) {
    return new CameraModule_Companion_ProvideCameraManagerFactory(contextProvider);
  }

  public static CameraManager provideCameraManager(Context context) {
    return Preconditions.checkNotNullFromProvides(CameraModule.Companion.provideCameraManager(context));
  }
}
