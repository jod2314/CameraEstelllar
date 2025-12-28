import React, { useEffect, useState, useRef } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, Alert, Platform } from 'react-native';
import { Camera, useCameraDevice, useCameraPermission } from 'react-native-vision-camera';
import { CameraRoll } from "@react-native-camera-roll/camera-roll";

function App(): React.JSX.Element {
  const device = useCameraDevice('back');
  const { hasPermission, requestPermission } = useCameraPermission();
  const camera = useRef<Camera>(null);
  const [isCapturing, setIsCapturing] = useState(false);

  useEffect(() => {
    if (!hasPermission) {
      requestPermission();
    }
  }, [hasPermission, requestPermission]);

  const takePhoto = async () => {
    if (camera.current == null) return;
    
    try {
      setIsCapturing(true);
      const photo = await camera.current.takePhoto({
        flash: 'off',
        enableShutterSound: true,
      });

      await CameraRoll.save(`file://${photo.path}`, {
        type: 'photo',
      });

      Alert.alert('¡Éxito!', 'Foto guardada en la galería.');
    } catch (error) {
      console.error('Error al capturar:', error);
      Alert.alert('Error', 'No se pudo capturar la imagen.');
    } finally {
      setIsCapturing(false);
    }
  };

  if (!hasPermission) {
    return (
      <View style={styles.container}>
        <Text style={styles.text}>No tenemos permiso para usar la cámara.</Text>
      </View>
    );
  }

  if (device == null) {
    return (
      <View style={styles.container}>
        <Text style={styles.text}>No se encontró una cámara trasera disponible.</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Camera
        ref={camera}
        style={StyleSheet.absoluteFill}
        device={device}
        isActive={true}
        photo={true}
      />
      
      <View style={styles.overlay}>
        <Text style={styles.title}>CameraEstellar</Text>
        <Text style={styles.subtitle}>Fase 1: Captura Básica</Text>
      </View>

      <View style={styles.bottomControls}>
        <TouchableOpacity 
          style={[styles.shutterButton, isCapturing && { opacity: 0.5 }]} 
          onPress={takePhoto}
          disabled={isCapturing}
        >
          <View style={styles.shutterInternal} />
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
  },
  text: {
    color: 'white',
    fontSize: 18,
    textAlign: 'center',
  },
  overlay: {
    position: 'absolute',
    top: 50,
    left: 20,
  },
  title: {
    color: 'white',
    fontSize: 24,
    fontWeight: 'bold',
  },
  subtitle: {
    color: '#00ff00',
    fontSize: 14,
  },
  bottomControls: {
    position: 'absolute',
    bottom: 50,
    width: '100%',
    alignItems: 'center',
  },
  shutterButton: {
    width: 80,
    height: 80,
    borderRadius: 40,
    borderWidth: 4,
    borderColor: 'white',
    justifyContent: 'center',
    alignItems: 'center',
  },
  shutterInternal: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: 'white',
  },
});

export default App;
