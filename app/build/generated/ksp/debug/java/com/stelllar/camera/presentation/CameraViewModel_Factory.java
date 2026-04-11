package com.stelllar.camera.presentation;

import com.stelllar.camera.domain.repository.CameraRepository;
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
public final class CameraViewModel_Factory implements Factory<CameraViewModel> {
  private final Provider<CameraRepository> cameraRepositoryProvider;

  public CameraViewModel_Factory(Provider<CameraRepository> cameraRepositoryProvider) {
    this.cameraRepositoryProvider = cameraRepositoryProvider;
  }

  @Override
  public CameraViewModel get() {
    return newInstance(cameraRepositoryProvider.get());
  }

  public static CameraViewModel_Factory create(
      Provider<CameraRepository> cameraRepositoryProvider) {
    return new CameraViewModel_Factory(cameraRepositoryProvider);
  }

  public static CameraViewModel newInstance(CameraRepository cameraRepository) {
    return new CameraViewModel(cameraRepository);
  }
}
