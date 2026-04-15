package com.stelllar.camera.presentation;

import com.stelllar.camera.domain.usecase.GetCamerasUseCase;
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
public final class SelectorViewModel_Factory implements Factory<SelectorViewModel> {
  private final Provider<GetCamerasUseCase> getCamerasUseCaseProvider;

  public SelectorViewModel_Factory(Provider<GetCamerasUseCase> getCamerasUseCaseProvider) {
    this.getCamerasUseCaseProvider = getCamerasUseCaseProvider;
  }

  @Override
  public SelectorViewModel get() {
    return newInstance(getCamerasUseCaseProvider.get());
  }

  public static SelectorViewModel_Factory create(
      Provider<GetCamerasUseCase> getCamerasUseCaseProvider) {
    return new SelectorViewModel_Factory(getCamerasUseCaseProvider);
  }

  public static SelectorViewModel newInstance(GetCamerasUseCase getCamerasUseCase) {
    return new SelectorViewModel(getCamerasUseCase);
  }
}
