import React, { useRef, useImperativeHandle, forwardRef } from 'react';
import { requireNativeComponent, ViewProps, UIManager, findNodeHandle } from 'react-native';

interface AstroCameraProps extends ViewProps {
  iso: number;
  exposureSeconds: number;
  focusDistance: number; // 0.0 = Infinito
}

export interface AstroCameraRef {
  takePicture: () => void;
}

const NativeCamera = requireNativeComponent<AstroCameraProps>('AstroCameraView');

export const AstroCamera = forwardRef<AstroCameraRef, AstroCameraProps>((props, ref) => {
  const nativeRef = useRef(null);

  useImperativeHandle(ref, () => ({
    takePicture: () => {
      console.log('AstroCamera: Intentando disparar...');
      const handle = findNodeHandle(nativeRef.current);
      if (handle) {
        console.log('AstroCamera: Handle encontrado, enviando comando takePicture');
        UIManager.dispatchViewManagerCommand(
          handle,
          'takePicture', // Usamos el nombre del comando directamente por claridad
          []
        );
      } else {
        console.warn('AstroCamera: No se encontró el handle de la cámara');
      }
    },
  }));

  return <NativeCamera ref={nativeRef} {...props} />;
});
