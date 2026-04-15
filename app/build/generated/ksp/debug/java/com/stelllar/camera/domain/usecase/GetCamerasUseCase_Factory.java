package com.stelllar.camera.domain.usecase;

import android.content.Context;
import com.stelllar.camera.domain.repository.SettingsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class GetCamerasUseCase_Factory implements Factory<GetCamerasUseCase> {
  private final Provider<Context> contextProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  public GetCamerasUseCase_Factory(Provider<Context> contextProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public GetCamerasUseCase get() {
    return newInstance(contextProvider.get(), settingsRepositoryProvider.get());
  }

  public static GetCamerasUseCase_Factory create(Provider<Context> contextProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new GetCamerasUseCase_Factory(contextProvider, settingsRepositoryProvider);
  }

  public static GetCamerasUseCase newInstance(Context context,
      SettingsRepository settingsRepository) {
    return new GetCamerasUseCase(context, settingsRepository);
  }
}
