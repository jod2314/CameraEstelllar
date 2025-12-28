import React, { useState, useEffect, useRef } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, Alert, PermissionsAndroid, Platform } from 'react-native';
import Slider from '@react-native-community/slider';
import { AstroCamera, AstroCameraRef } from './AstroCamera'; // Importamos nuestro componente nativo

function App(): React.JSX.Element {
  const [iso, setIso] = useState(800);
  const [shutter, setShutter] = useState(0.05); // 1/20s
  const [hasPermission, setHasPermission] = useState(false);
  const cameraRef = useRef<AstroCameraRef>(null);

  useEffect(() => {
    const requestPermission = async () => {
      if (Platform.OS === 'android') {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.CAMERA,
          {
            title: "Permiso de Cámara",
            message: "La app necesita acceso a la cámara para tomar fotos astronómicas.",
            buttonNeutral: "Preguntar luego",
            buttonNegative: "Cancelar",
            buttonPositive: "OK"
          }
        );
        if (granted === PermissionsAndroid.RESULTS.GRANTED) {
          setHasPermission(true);
        } else {
          Alert.alert("Permiso denegado", "No se puede usar la cámara sin permisos.");
        }
      } else {
        // iOS permissions handling would go here, but for now we focus on Android
        setHasPermission(true);
      }
    };

    requestPermission();
  }, []);

  const handleCapture = () => {
    if (cameraRef.current) {
      cameraRef.current.takePicture();
      Alert.alert("Capturando", "Tomando foto con exposición manual...");
    }
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
      {/* CÁMARA NATIVA JAVA */}
      <AstroCamera
        ref={cameraRef}
        style={StyleSheet.absoluteFill}
        iso={iso}
        exposureSeconds={shutter}
      />

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
            maximumValue={30.0} // Extendemos a 30 segundos para astro
            step={0.001}
            value={shutter}
            onValueChange={setShutter}
            minimumTrackTintColor="#00ccff"
            thumbTintColor="#00ccff"
          />
        </View>

        <TouchableOpacity style={styles.captureButton} onPress={handleCapture}>
          <View style={styles.captureInner} />
        </TouchableOpacity>
      </View>
      
      <View style={{position: 'absolute', top: 50, alignSelf: 'center'}}>
        <Text style={{color: 'white', fontWeight: 'bold'}}>MOTOR NATIVO CAMERA2</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: 'black' },
  controls: { position: 'absolute', bottom: 30, width: '100%', padding: 20, backgroundColor: 'rgba(0,0,0,0.5)', alignItems: 'center' },
  controlRow: { marginBottom: 10, width: '100%' },
  label: { color: 'white', marginBottom: 5, fontWeight: 'bold' },
  slider: { width: '100%', height: 40 },
  centerContainer: { flex: 1, backgroundColor: 'black', justifyContent: 'center', alignItems: 'center' },
  text: { color: 'white' },
  captureButton: {
    width: 70,
    height: 70,
    borderRadius: 35,
    backgroundColor: 'white',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 10,
  },
  captureInner: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: 'white',
    borderWidth: 2,
    borderColor: 'black',
  },
});

export default App;
