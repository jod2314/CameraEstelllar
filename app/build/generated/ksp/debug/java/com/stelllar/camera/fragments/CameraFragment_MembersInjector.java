package com.stelllar.camera.fragments;

import com.stelllar.camera.data.camera.SensorProber;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@QualifierMetadata
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
public final class CameraFragment_MembersInjector implements MembersInjector<CameraFragment> {
  private final Provider<SensorProber> sensorProberProvider;

  public CameraFragment_MembersInjector(Provider<SensorProber> sensorProberProvider) {
    this.sensorProberProvider = sensorProberProvider;
  }

  public static MembersInjector<CameraFragment> create(
      Provider<SensorProber> sensorProberProvider) {
    return new CameraFragment_MembersInjector(sensorProberProvider);
  }

  @Override
  public void injectMembers(CameraFragment instance) {
    injectSensorProber(instance, sensorProberProvider.get());
  }

  @InjectedFieldSignature("com.stelllar.camera.fragments.CameraFragment.sensorProber")
  public static void injectSensorProber(CameraFragment instance, SensorProber sensorProber) {
    instance.sensorProber = sensorProber;
  }
}
