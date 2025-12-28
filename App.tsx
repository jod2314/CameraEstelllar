import React, { useEffect, useState, useRef } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, Alert, Switch, ActivityIndicator } from 'react-native';
import { Camera, useCameraDevice, useCameraPermission, useCameraFormat } from 'react-native-vision-camera';
import { CameraRoll } from "@react-native-camera-roll/camera-roll";
import Slider from '@react-native-community/slider';

function App(): React.JSX.Element {
  const device = useCameraDevice('back');
  const { hasPermission, requestPermission } = useCameraPermission();
  const camera = useRef<Camera>(null);
  
  const [isCapturing, setIsCapturing] = useState(false);
  const [isManualMode, setIsManualMode] = useState(false); 
  const [cameraKey, setCameraKey] = useState(0); // Usado para forzar reinicio de cámara

  // Valores Manuales
  const [iso, setIso] = useState(800);
  const [shutterSec, setShutterSec] = useState(0.05); // 1/20s

  const format = useCameraFormat(device, [
    { photoResolution: 'max' } 
  ]);

  useEffect(() => {
    if (!hasPermission) requestPermission();
  }, [hasPermission, requestPermission]);

  // Cuando cambiamos de modo, forzamos la destrucción y recreación de la cámara
  const toggleMode = (val: boolean) => {
    setIsManualMode(val);
    setCameraKey(prev => prev + 1); // Esto es la "Bomba Nuclear" que reinicia el componente
  };

  const takePhoto = async () => {
    if (camera.current == null) return;
    try {
      setIsCapturing(true);
      const photo = await camera.current.takePhoto({
        flash: 'off',
        enableShutterSound: true,
      });
      await CameraRoll.save(`file://${photo.path}`, { type: 'photo' });
      Alert.alert('Foto guardada');
    } catch (error) {
      Alert.alert('Error', 'Error captura');
    } finally {
      setIsCapturing(false);
    }
  };

  if (!hasPermission || !device) return <View style={styles.black}><Text style={styles.text}>Cargando...</Text></View>;

  return (
    <View style={styles.container}>
      {/* Usamos 'key' para forzar remontaje */}
      <Camera
        key={cameraKey} 
        ref={camera}
        style={StyleSheet.absoluteFill}
        device={device}
        format={format}
        isActive={true}
        photo={true}
        // MODOS EXCLUSIVOS
        exposure={isManualMode ? undefined : 0} // Si es manual, exposure DEBE ser undefined
        iso={isManualMode ? iso : undefined}
        frameDuration={isManualMode ? shutterSec * 1000 : undefined} 
      />
      
      <View style={styles.topBar}>
        <Text style={styles.modeLabel}>
          {isManualMode ? 'MODO PRO (MANUAL)' : 'MODO AUTO'}
        </Text>
        <Switch 
          value={isManualMode} 
          onValueChange={toggleMode}
          trackColor={{ false: "#767577", true: "#81b0ff" }}
          thumbColor={isManualMode ? "#0066cc" : "#f4f3f4"}
        />
      </View>

      {/* Indicador de carga cuando reiniciamos la cámara */}
      <View style={{position: 'absolute', top: 100, alignSelf: 'center'}}>
          <Text style={{color: 'yellow', fontSize: 10}}>Reinicio forzado al cambiar modo</Text>
      </View>

      {isManualMode && (
        <View style={styles.controlsContainer}>
          <Text style={styles.label}>ISO: {iso.toFixed(0)}</Text>
          <Slider
            style={styles.slider}
            minimumValue={50}
            maximumValue={3200}
            step={50}
            value={iso}
            onValueChange={setIso} // Nota: Cambiar esto en tiempo real NO reinicia la cámara, solo el switch de modo
            minimumTrackTintColor="#00ff00"
            thumbTintColor="#00ff00"
          />

          <Text style={styles.label}>Obturador: {shutterSec.toFixed(3)}s</Text>
          <Slider
            style={styles.slider}
            minimumValue={0.001}
            maximumValue={0.5}
            step={0.001}
            value={shutterSec}
            onValueChange={setShutterSec}
            minimumTrackTintColor="#00ccff"
            thumbTintColor="#00ccff"
          />
        </View>
      )}

      <View style={styles.shutterContainer}>
        <TouchableOpacity 
          style={styles.shutterButton} 
          onPress={takePhoto}
          disabled={isCapturing}
        >
          <View style={[styles.shutterInternal, isCapturing && { backgroundColor: 'red' }]} />
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: 'black' },
  black: { flex: 1, backgroundColor: 'black', justifyContent: 'center' },
  text: { color: 'white', textAlign: 'center' },
  topBar: { 
    position: 'absolute', top: 50, left: 20, right: 20, 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.6)', padding: 10, borderRadius: 20, zIndex: 10
  },
  modeLabel: { color: 'white', fontWeight: 'bold', fontSize: 16 },
  controlsContainer: { position: 'absolute', bottom: 130, left: 20, right: 20, backgroundColor: 'rgba(0,0,0,0.5)', padding: 15, borderRadius: 10 },
  label: { color: 'white', marginBottom: 5, fontWeight: 'bold' },
  slider: { width: '100%', height: 40, marginBottom: 10 },
  shutterContainer: { position: 'absolute', bottom: 40, width: '100%', alignItems: 'center' },
  shutterButton: { width: 70, height: 70, borderRadius: 35, borderWidth: 3, borderColor: 'white', justifyContent: 'center', alignItems: 'center' },
  shutterInternal: { width: 54, height: 54, borderRadius: 27, backgroundColor: 'white' },
});

export default App;