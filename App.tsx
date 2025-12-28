import React, { useState, useEffect, useRef } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, Alert, PermissionsAndroid, Platform } from 'react-native';
import Slider from '@react-native-community/slider';
import { AstroCamera, AstroCameraRef } from './AstroCamera'; // Importamos nuestro componente nativo

function App(): React.JSX.Element {
  const [iso, setIso] = useState(800);
  const [shutter, setShutter] = useState(0.05);
  const [focusDist, setFocusDist] = useState(0.0); // 0.0 = Infinito
  const [timerDelay, setTimerDelay] = useState(0); // 0, 3, 10
  const [isCountingDown, setIsCountingDown] = useState(false);
  const [count, setCount] = useState(0);
  
  const [hasPermission, setHasPermission] = useState(false);
  const cameraRef = useRef<AstroCameraRef>(null);

  // ... (useEffect for permissions remains same)

  useEffect(() => {
    const requestPermission = async () => {
      if (Platform.OS === 'android') {
        try {
          // Solicitamos múltiples permisos para cubrir diferentes versiones de Android
          const permissionsToRequest = [
            PermissionsAndroid.PERMISSIONS.CAMERA,
            PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE,
            // Para Android 13+ (API 33)
            PermissionsAndroid.PERMISSIONS.READ_MEDIA_IMAGES, 
          ];

          const granted = await PermissionsAndroid.requestMultiple(permissionsToRequest);

          // Verificamos si la CÁMARA (el más crítico) fue autorizado
          if (granted[PermissionsAndroid.PERMISSIONS.CAMERA] === PermissionsAndroid.RESULTS.GRANTED) {
            console.log('Permiso de cámara concedido');
            setHasPermission(true);
          } else {
            Alert.alert("Permiso denegado", "Es necesario el permiso de cámara para usar esta app.");
          }
          
          // Nota: WRITE_EXTERNAL_STORAGE puede ser denegado en Android 13+ y está bien, 
          // pero es necesario para Android < 13.
        } catch (err) {
          console.warn(err);
        }
      } else {
        setHasPermission(true);
      }
    };
    requestPermission();
  }, []);

  const handleCapture = () => {
    if (isCountingDown) return;

    if (timerDelay === 0) {
      triggerCapture();
    } else {
      startCountdown(timerDelay);
    }
  };

  const startCountdown = (seconds: number) => {
    setIsCountingDown(true);
    setCount(seconds);
    
    let current = seconds;
    const interval = setInterval(() => {
      current -= 1;
      setCount(current);
      if (current <= 0) {
        clearInterval(interval);
        setIsCountingDown(false);
        triggerCapture();
      }
    }, 1000);
  };

  const triggerCapture = () => {
    if (cameraRef.current) {
      cameraRef.current.takePicture();
    }
  };

  const toggleTimer = () => {
    if (timerDelay === 0) setTimerDelay(3);
    else if (timerDelay === 3) setTimerDelay(10);
    else setTimerDelay(0);
  };

  if (!hasPermission) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.text}>Solicitando permiso de cámara...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <AstroCamera
        ref={cameraRef}
        style={StyleSheet.absoluteFill}
        iso={iso}
        exposureSeconds={shutter}
        focusDistance={focusDist}
      />

      {isCountingDown && (
        <View style={styles.countdownOverlay}>
          <Text style={styles.countdownText}>{count}</Text>
        </View>
      )}

      <View style={styles.topControls}>
        <TouchableOpacity onPress={toggleTimer} style={styles.timerButton}>
          <Text style={styles.timerText}>
            {timerDelay === 0 ? '⏱ OFF' : `⏱ ${timerDelay}s`}
          </Text>
        </TouchableOpacity>
      </View>

      <View style={styles.controls}>
        <View style={styles.controlRow}>
          <Text style={styles.label}>ISO: {iso.toFixed(0)}</Text>
          <Slider
            style={styles.slider}
            minimumValue={50}
            maximumValue={3200}
            step={50}
            value={iso}
            onValueChange={setIso}
            minimumTrackTintColor="#00ff00"
            thumbTintColor="#00ff00"
          />
        </View>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Shutter: {shutter.toFixed(3)}s</Text>
          <Slider
            style={styles.slider}
            minimumValue={0.001}
            maximumValue={30.0}
            step={0.001}
            value={shutter}
            onValueChange={setShutter}
            minimumTrackTintColor="#00ccff"
            thumbTintColor="#00ccff"
          />
        </View>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Enfoque: {focusDist === 0 ? 'Infinito (Estrellas)' : focusDist.toFixed(2)}</Text>
          <Slider
            style={styles.slider}
            minimumValue={0.0}
            maximumValue={1.0} // En Android 1.0 suele ser macro, 0.0 infinito
            step={0.01}
            value={focusDist}
            onValueChange={setFocusDist}
            minimumTrackTintColor="#ff00ff"
            thumbTintColor="#ff00ff"
          />
        </View>

        <TouchableOpacity 
          style={[styles.captureButton, isCountingDown && {opacity: 0.5}]} 
          onPress={handleCapture}
          disabled={isCountingDown}
        >
          <View style={styles.captureInner} />
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: 'black' },
  topControls: { position: 'absolute', top: 50, right: 20 },
  timerButton: { backgroundColor: 'rgba(50,50,50,0.8)', padding: 10, borderRadius: 5 },
  timerText: { color: 'white', fontWeight: 'bold' },
  controls: { position: 'absolute', bottom: 30, width: '100%', padding: 20, backgroundColor: 'rgba(0,0,0,0.5)', alignItems: 'center' },
  controlRow: { marginBottom: 10, width: '100%' },
  label: { color: 'white', marginBottom: 5, fontWeight: 'bold' },
  slider: { width: '100%', height: 40 },
  centerContainer: { flex: 1, backgroundColor: 'black', justifyContent: 'center', alignItems: 'center' },
  text: { color: 'white' },
  captureButton: {
    width: 70, height: 70, borderRadius: 35, backgroundColor: 'white', justifyContent: 'center', alignItems: 'center', marginTop: 10,
  },
  captureInner: {
    width: 60, height: 60, borderRadius: 30, backgroundColor: 'white', borderWidth: 2, borderColor: 'black',
  },
  countdownOverlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.3)',
    zIndex: 10
  },
  countdownText: { fontSize: 100, color: 'white', fontWeight: 'bold' }
});

export default App;
