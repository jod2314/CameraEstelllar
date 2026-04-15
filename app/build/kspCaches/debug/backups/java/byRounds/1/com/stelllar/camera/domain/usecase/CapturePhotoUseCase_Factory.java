package com.stelllar.camera.domain.usecase;

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
public final class CapturePhotoUseCase_Factory implements Factory<CapturePhotoUseCase> {
  private final Provider<CameraRepository> cameraRepositoryProvider;

  public CapturePhotoUseCase_Factory(Provider<CameraRepository> cameraRepositoryProvider) {
    this.cameraRepositoryProvider = cameraRepositoryProvider;
  }

  @Override
  public CapturePhotoUseCase get() {
    return newInstance(cameraRepositoryProvider.get());
  }

  public static CapturePhotoUseCase_Factory create(
      Provider<CameraRepository> cameraRepositoryProvider) {
    return new CapturePhotoUseCase_Factory(cameraRepositoryProvider);
  }

  public static CapturePhotoUseCase newInstance(CameraRepository cameraRepository) {
    return new CapturePhotoUseCase(cameraRepository);
  }
}
