import React, { useRef, useImperativeHandle, forwardRef } from 'react';
import { requireNativeComponent, ViewProps, UIManager, findNodeHandle } from 'react-native';

interface AstroCameraProps extends ViewProps {
  iso: number;
  exposureSeconds: number;
}

export interface AstroCameraRef {
  takePicture: () => void;
}

const NativeCamera = requireNativeComponent<AstroCameraProps>('AstroCameraView');

export const AstroCamera = forwardRef<AstroCameraRef, AstroCameraProps>((props, ref) => {
  const nativeRef = useRef(null);

  useImperativeHandle(ref, () => ({
    takePicture: () => {
      const handle = findNodeHandle(nativeRef.current);
      if (handle) {
        UIManager.dispatchViewManagerCommand(
          handle,
          UIManager.getViewManagerConfig('AstroCameraView').Commands.takePicture.toString(),
          []
        );
      }
    },
  }));

  return <NativeCamera ref={nativeRef} {...props} />;
});
