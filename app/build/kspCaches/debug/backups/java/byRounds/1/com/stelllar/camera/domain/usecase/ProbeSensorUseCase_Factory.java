package com.stelllar.camera.domain.usecase;

import com.stelllar.camera.data.camera.SensorProber;
import com.stelllar.camera.domain.repository.SettingsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ProbeSensorUseCase_Factory implements Factory<ProbeSensorUseCase> {
  private final Provider<SensorProber> sensorProberProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  public ProbeSensorUseCase_Factory(Provider<SensorProber> sensorProberProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    this.sensorProberProvider = sensorProberProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public ProbeSensorUseCase get() {
    return newInstance(sensorProberProvider.get(), settingsRepositoryProvider.get());
  }

  public static ProbeSensorUseCase_Factory create(Provider<SensorProber> sensorProberProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new ProbeSensorUseCase_Factory(sensorProberProvider, settingsRepositoryProvider);
  }

  public static ProbeSensorUseCase newInstance(SensorProber sensorProber,
      SettingsRepository settingsRepository) {
    return new ProbeSensorUseCase(sensorProber, settingsRepository);
  }
}
