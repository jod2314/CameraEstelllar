import React, { useRef, useImperativeHandle, forwardRef } from 'react';
import { requireNativeComponent, ViewProps, UIManager, findNodeHandle } from 'react-native';

interface AstroCameraProps extends ViewProps {
  iso: number;
  exposureSeconds: number;
  focusDistance: number; // 0.0 = Infinito
  burstCount?: number;
  onCaptureStarted?: () => void;
  onCaptureEnded?: (event: { nativeEvent: { success: boolean; error?: string } }) => void;
}

export interface AstroCameraRef {
  takePicture: () => void;
}

const NativeCamera = requireNativeComponent<AstroCameraProps>('AstroCameraView');

export const AstroCamera = forwardRef<AstroCameraRef, AstroCameraProps>((props, ref) => {
  const nativeRef = useRef(null);

  const onCaptureStarted = () => {
    if (props.onCaptureStarted) {
      props.onCaptureStarted();
    }
  };

  const onCaptureEnded = (event: any) => {
    if (props.onCaptureEnded) {
      props.onCaptureEnded(event);
    }
  };

  useImperativeHandle(ref, () => ({
    takePicture: () => {
      console.log('AstroCamera: Intentando disparar...');
      const handle = findNodeHandle(nativeRef.current);
      if (handle) {
        console.log('AstroCamera: Handle encontrado, enviando comando takePicture');
        UIManager.dispatchViewManagerCommand(
          handle,
          'takePicture', 
          []
        );
      } else {
        console.warn('AstroCamera: No se encontró el handle de la cámara');
      }
    },
  }));

  return (
    <NativeCamera 
      ref={nativeRef} 
      {...props} 
      onCaptureStarted={onCaptureStarted}
      onCaptureEnded={onCaptureEnded}
    />
  );
});
