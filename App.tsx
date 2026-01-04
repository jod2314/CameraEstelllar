import React, { useState, useEffect, useRef } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, Alert, PermissionsAndroid, Platform, ActivityIndicator } from 'react-native';
import Slider from '@react-native-community/slider';
import { AstroCamera, AstroCameraRef } from './AstroCamera'; // Importamos nuestro componente nativo

function App(): React.JSX.Element {
  const HARDWARE_LIMIT_EXPOSURE = 0.15; // Límite físico detectado (ejemplo, debe venir de capacidades)

  const [iso, setIso] = useState(800);
  const [targetShutter, setTargetShutter] = useState(0.15); // Tiempo total deseado
  const [focusDist, setFocusDist] = useState(0.0);
  const [timerDelay, setTimerDelay] = useState(0); 
  const [isCountingDown, setIsCountingDown] = useState(false);
  const [count, setCount] = useState(0);
  const [isCapturing, setIsCapturing] = useState(false);
  
  // Lógica simplificada: SIEMPRE disparo único, respetando el tiempo que pida el usuario.
  // Ignoramos el límite reportado para intentar forzar al hardware.
  const realExposure = targetShutter;
  const burstCount = 1; 
  const isStackingNeeded = false; // Desactivado permanentemente por preferencia de usuario

  const [hasPermission, setHasPermission] = useState(false);
  const cameraRef = useRef<AstroCameraRef>(null);

  useEffect(() => {
    const requestPermission = async () => {
        if (Platform.OS === 'android') {
            try {
              const permissionsToRequest = [
                PermissionsAndroid.PERMISSIONS.CAMERA,
                PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE,
                PermissionsAndroid.PERMISSIONS.READ_MEDIA_IMAGES, 
              ];
              const granted = await PermissionsAndroid.requestMultiple(permissionsToRequest);
              // Verificamos principalmente CAMERA, los otros pueden variar según versión de Android
              if (granted[PermissionsAndroid.PERMISSIONS.CAMERA] === PermissionsAndroid.RESULTS.GRANTED) {
                setHasPermission(true);
              }
            } catch (err) { console.warn(err); }
        } else { setHasPermission(true); }
    };
    requestPermission();
  }, []);

  const handleCapture = () => {
    if (isCountingDown || isCapturing) return;

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
    console.log(`App: Disparando... Total ${targetShutter}s (Burst: ${burstCount} x ${realExposure}s)`);
    if (cameraRef.current) {
      cameraRef.current.takePicture();
    }
  };
  
  const onCaptureStarted = () => {
    setIsCapturing(true);
  };

  const onCaptureEnded = (event: any) => {
    setIsCapturing(false);
    if (!event.nativeEvent.success) {
        Alert.alert("Error", event.nativeEvent.error);
    }
  };

  const toggleTimer = () => {
    if (timerDelay === 0) setTimerDelay(3);
    else if (timerDelay === 3) setTimerDelay(10);
    else setTimerDelay(0);
  };

  if (!hasPermission) {
    return <View style={styles.centerContainer}><Text style={styles.text}>Permiso requerido</Text></View>;
  }

  return (
    <View style={styles.container}>
      <AstroCamera
        ref={cameraRef}
        style={StyleSheet.absoluteFill}
        iso={iso}
        exposureSeconds={realExposure} // Siempre enviamos lo que el hardware soporta
        focusDistance={focusDist}
        burstCount={burstCount} // Enviamos la cantidad necesaria para simular el tiempo total
        onCaptureStarted={onCaptureStarted}
        onCaptureEnded={onCaptureEnded}
      />

      {isCountingDown && (
        <View style={styles.countdownOverlay}>
          <Text style={styles.countdownText}>{count}</Text>
        </View>
      )}
      
      {isCapturing && (
        <View style={styles.loadingOverlay}>
            <ActivityIndicator size="large" color="#00ff00" />
            <Text style={styles.loadingText}>
              {burstCount > 1 
                ? `Adquiriendo ${burstCount} cuadros...\n(${(burstCount * realExposure).toFixed(1)}s Efectivos)` 
                : 'Capturando...'}
            </Text>
        </View>
      )}

      <View style={styles.topControls}>
        <TouchableOpacity onPress={toggleTimer} style={styles.timerButton}>
          <Text style={styles.timerText}>
            {timerDelay === 0 ? '⏱ OFF' : `⏱ ${timerDelay}s`}
          </Text>
        </TouchableOpacity>
        {isStackingNeeded && (
             <View style={{backgroundColor: 'rgba(255,0,0,0.5)', padding: 5, borderRadius: 5, marginTop: 5}}>
                 <Text style={{color:'white', fontSize: 10}}>MODO STACKING ACTIVADO</Text>
             </View>
        )}
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
          <Text style={styles.label}>
            Tiempo Total: {targetShutter.toFixed(1)}s 
            {isStackingNeeded ? ` (${burstCount}x RAW)` : ''}
          </Text>
          <Slider
            style={styles.slider}
            minimumValue={0.05}
            maximumValue={30.0} // Permitimos seleccionar hasta 30s
            step={0.1}
            value={targetShutter}
            onValueChange={setTargetShutter}
            minimumTrackTintColor={isStackingNeeded ? "#ff4444" : "#00ccff"}
            thumbTintColor={isStackingNeeded ? "#ff4444" : "#00ccff"}
          />
        </View>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Enfoque: {focusDist === 0 ? 'Infinito' : focusDist.toFixed(2)}</Text>
          <Slider
            style={styles.slider}
            minimumValue={0.0}
            maximumValue={1.0}
            step={0.01}
            value={focusDist}
            onValueChange={setFocusDist}
            minimumTrackTintColor="#ff00ff"
            thumbTintColor="#ff00ff"
          />
        </View>

        <TouchableOpacity 
          style={[styles.captureButton, (isCountingDown || isCapturing) && {opacity: 0.5}]} 
          onPress={handleCapture}
          disabled={isCountingDown || isCapturing}
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
  countdownText: { fontSize: 100, color: 'white', fontWeight: 'bold' },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.7)',
    zIndex: 20
  },
  loadingText: { color: '#00ff00', marginTop: 10, fontWeight: 'bold' }
});

export default App;
