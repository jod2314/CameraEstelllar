package com.stelllar.camera.presentation;

import com.stelllar.camera.domain.repository.CameraRepository;
import com.stelllar.camera.domain.usecase.CapturePhotoUseCase;
import com.stelllar.camera.domain.usecase.ProbeSensorUseCase;
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

  private final Provider<CapturePhotoUseCase> capturePhotoUseCaseProvider;

  private final Provider<ProbeSensorUseCase> probeSensorUseCaseProvider;

  public CameraViewModel_Factory(Provider<CameraRepository> cameraRepositoryProvider,
      Provider<CapturePhotoUseCase> capturePhotoUseCaseProvider,
      Provider<ProbeSensorUseCase> probeSensorUseCaseProvider) {
    this.cameraRepositoryProvider = cameraRepositoryProvider;
    this.capturePhotoUseCaseProvider = capturePhotoUseCaseProvider;
    this.probeSensorUseCaseProvider = probeSensorUseCaseProvider;
  }

  @Override
  public CameraViewModel get() {
    return newInstance(cameraRepositoryProvider.get(), capturePhotoUseCaseProvider.get(), probeSensorUseCaseProvider.get());
  }

  public static CameraViewModel_Factory create(Provider<CameraRepository> cameraRepositoryProvider,
      Provider<CapturePhotoUseCase> capturePhotoUseCaseProvider,
      Provider<ProbeSensorUseCase> probeSensorUseCaseProvider) {
    return new CameraViewModel_Factory(cameraRepositoryProvider, capturePhotoUseCaseProvider, probeSensorUseCaseProvider);
  }

  public static CameraViewModel newInstance(CameraRepository cameraRepository,
      CapturePhotoUseCase capturePhotoUseCase, ProbeSensorUseCase probeSensorUseCase) {
    return new CameraViewModel(cameraRepository, capturePhotoUseCase, probeSensorUseCase);
  }
}
