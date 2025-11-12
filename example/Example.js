import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  Button,
  FlatList,
  StyleSheet,
  Alert,
  ScrollView,
} from 'react-native';
import AcsReader from 'react-native-acs-reader';

const ExampleApp = () => {
  // Mode selection
  const [mode, setMode] = useState('bluetooth'); // 'bluetooth' or 'usb'
  const [reader, setReader] = useState(null);

  // Bluetooth state
  const [isScanning, setIsScanning] = useState(false);
  const [devices, setDevices] = useState([]);
  const [isConnected, setIsConnected] = useState(false);

  // USB state
  const [usbDevices, setUsbDevices] = useState([]);

  // Common state
  const [lastCard, setLastCard] = useState(null);
  const [logs, setLogs] = useState([]);

  // Initialize reader when mode changes
  useEffect(() => {
    const newReader = new AcsReader(mode);
    setReader(newReader);

    // Clean up previous reader
    return () => {
      if (reader) {
        reader.removeAllListeners();
        reader.disconnect().catch(console.error);
      }
    };
  }, [mode]);

  // Set up event listeners
  useEffect(() => {
    if (!reader) return;

    // Common listeners
    const cardListener = reader.onCardDetected((data) => {
      addLog(`Card detected: ${data.uid}`);
      setLastCard(data);
    });

    const connectionListener = reader.onConnectionStatusChange((connected) => {
      addLog(`Connection status: ${connected ? 'Connected' : 'Disconnected'}`);
      setIsConnected(connected);
    });

    // Bluetooth-specific listeners
    let scanningListener;
    let devicesListener;

    if (mode === 'bluetooth') {
      scanningListener = reader.onScanningStatusChange((scanning) => {
        addLog(`Scanning status: ${scanning ? 'Scanning' : 'Stopped'}`);
        setIsScanning(scanning);
      });

      devicesListener = reader.onDevicesDiscovered((discoveredDevices) => {
        addLog(`Found ${discoveredDevices.length} devices`);
        setDevices(discoveredDevices);
      });
    }

    // USB-specific listeners
    let logListener;
    if (mode === 'usb') {
      logListener = reader.onLog((message) => {
        addLog(`USB: ${message}`);
      });
    }

    // Cleanup
    return () => {
      cardListener();
      connectionListener();
      if (scanningListener) scanningListener();
      if (devicesListener) devicesListener();
      if (logListener) logListener();
    };
  }, [reader, mode]);

  const addLog = (message) => {
    const timestamp = new Date().toLocaleTimeString();
    setLogs((prev) => [`[${timestamp}] ${message}`, ...prev].slice(0, 50));
  };

  // Request permissions
  const handleRequestPermissions = async () => {
    try {
      const granted = await reader.requestPermissions();
      addLog(`Permissions ${granted ? 'granted' : 'denied'}`);
    } catch (error) {
      addLog(`Error requesting permissions: ${error.message}`);
    }
  };

  // Bluetooth: Start scanning
  const handleStartScanning = async () => {
    try {
      await reader.startScanning();
      addLog('Started scanning for Bluetooth devices');
    } catch (error) {
      addLog(`Error starting scan: ${error.message}`);
    }
  };

  // Bluetooth: Stop scanning
  const handleStopScanning = async () => {
    try {
      await reader.stopScanning();
      addLog('Stopped scanning');
    } catch (error) {
      addLog(`Error stopping scan: ${error.message}`);
    }
  };

  // Bluetooth: Connect to device
  const handleBluetoothConnect = async (address) => {
    try {
      addLog(`Connecting to ${address}...`);
      await reader.connect(address);
      addLog(`Connected to ${address}`);
    } catch (error) {
      addLog(`Error connecting: ${error.message}`);
    }
  };

  // USB: Get devices
  const handleGetUsbDevices = async () => {
    try {
      const devices = await reader.getDevices();
      addLog(`Found ${devices.length} USB devices`);
      setUsbDevices(devices);
    } catch (error) {
      addLog(`Error getting USB devices: ${error.message}`);
    }
  };

  // USB: Connect to device
  const handleUsbConnect = async (productId) => {
    try {
      addLog(`Connecting to USB device ${productId}...`);
      const deviceName = await reader.connect(productId.toString());
      addLog(`Connected to ${deviceName}`);
    } catch (error) {
      addLog(`Error connecting to USB: ${error.message}`);
    }
  };

  // Disconnect
  const handleDisconnect = async () => {
    try {
      await reader.disconnect();
      addLog('Disconnected');
    } catch (error) {
      addLog(`Error disconnecting: ${error.message}`);
    }
  };

  // Bluetooth: Beep
  const handleBeep = async () => {
    try {
      await reader.beep();
      addLog('Beeped!');
    } catch (error) {
      addLog(`Error beeping: ${error.message}`);
    }
  };

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>ACS Reader Example</Text>

      {/* Mode Selection */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Mode</Text>
        <View style={styles.buttonRow}>
          <Button
            title="Bluetooth"
            onPress={() => setMode('bluetooth')}
            color={mode === 'bluetooth' ? '#007AFF' : '#ccc'}
          />
          <View style={styles.buttonSpacer} />
          <Button
            title="USB"
            onPress={() => setMode('usb')}
            color={mode === 'usb' ? '#007AFF' : '#ccc'}
          />
        </View>
      </View>

      {/* Common Actions */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Common Actions</Text>
        <Button title="Request Permissions" onPress={handleRequestPermissions} />
        <View style={styles.buttonSpacer} />
        <Button
          title="Disconnect"
          onPress={handleDisconnect}
          disabled={!isConnected}
        />
      </View>

      {/* Bluetooth-specific */}
      {mode === 'bluetooth' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Bluetooth</Text>
          <Button
            title={isScanning ? 'Stop Scanning' : 'Start Scanning'}
            onPress={isScanning ? handleStopScanning : handleStartScanning}
          />
          <View style={styles.buttonSpacer} />
          <Button
            title="Beep"
            onPress={handleBeep}
            disabled={!isConnected}
          />

          {devices.length > 0 && (
            <>
              <Text style={styles.listTitle}>Discovered Devices:</Text>
              <FlatList
                data={devices}
                keyExtractor={(item) => item.address}
                renderItem={({ item }) => (
                  <View style={styles.listItem}>
                    <Text style={styles.deviceName}>{item.name}</Text>
                    <Text style={styles.deviceAddress}>{item.address}</Text>
                    <Button
                      title="Connect"
                      onPress={() => handleBluetoothConnect(item.address)}
                    />
                  </View>
                )}
              />
            </>
          )}
        </View>
      )}

      {/* USB-specific */}
      {mode === 'usb' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>USB</Text>
          <Button
            title="Get USB Devices"
            onPress={handleGetUsbDevices}
          />

          {usbDevices.length > 0 && (
            <>
              <Text style={styles.listTitle}>USB Devices:</Text>
              <FlatList
                data={usbDevices}
                keyExtractor={(item) => item.productId.toString()}
                renderItem={({ item }) => (
                  <View style={styles.listItem}>
                    <Text style={styles.deviceName}>{item.deviceName}</Text>
                    <Text style={styles.deviceAddress}>
                      Product ID: {item.productId}
                    </Text>
                    <Button
                      title="Connect"
                      onPress={() => handleUsbConnect(item.productId)}
                    />
                  </View>
                )}
              />
            </>
          )}
        </View>
      )}

      {/* Last Card */}
      {lastCard && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Last Card</Text>
          <Text style={styles.cardUid}>UID: {lastCard.uid}</Text>
          {lastCard.deviceId && (
            <Text style={styles.cardDevice}>Device: {lastCard.deviceId}</Text>
          )}
        </View>
      )}

      {/* Logs */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Logs</Text>
        <View style={styles.logsContainer}>
          {logs.map((log, index) => (
            <Text key={index} style={styles.logText}>
              {log}
            </Text>
          ))}
        </View>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
  },
  section: {
    backgroundColor: 'white',
    padding: 15,
    marginBottom: 15,
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  buttonSpacer: {
    height: 10,
    width: 10,
  },
  listTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginTop: 15,
    marginBottom: 10,
  },
  listItem: {
    padding: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  deviceName: {
    fontSize: 16,
    fontWeight: '600',
  },
  deviceAddress: {
    fontSize: 14,
    color: '#666',
    marginBottom: 5,
  },
  cardUid: {
    fontSize: 16,
    fontFamily: 'monospace',
    marginBottom: 5,
  },
  cardDevice: {
    fontSize: 14,
    color: '#666',
  },
  logsContainer: {
    maxHeight: 200,
    backgroundColor: '#f9f9f9',
    padding: 10,
    borderRadius: 5,
  },
  logText: {
    fontSize: 12,
    fontFamily: 'monospace',
    marginBottom: 3,
  },
});

export default ExampleApp;
