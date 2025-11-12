import { NativeModules, NativeEventEmitter, PermissionsAndroid, Platform } from 'react-native';

const { RNACSBluetooth } = NativeModules;

if (!RNACSBluetooth) {
  throw new Error('RNACSBluetooth native module is not available. Make sure the library is linked correctly.');
}

const eventEmitter = new NativeEventEmitter(RNACSBluetooth);

/**
 * Bluetooth Reader Implementation
 * Wraps the native Bluetooth module for ACS card readers
 */
class BluetoothReader {
  constructor() {
    this._listeners = [];
  }

  /**
   * Request necessary permissions for BLE operations
   * @returns {Promise<boolean>}
   */
  async requestPermissions() {
    if (Platform.OS === 'android') {
      try {
        const granted = await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        ]);

        return (
          granted['android.permission.BLUETOOTH_SCAN'] === PermissionsAndroid.RESULTS.GRANTED &&
          granted['android.permission.BLUETOOTH_CONNECT'] === PermissionsAndroid.RESULTS.GRANTED &&
          granted['android.permission.ACCESS_FINE_LOCATION'] === PermissionsAndroid.RESULTS.GRANTED
        );
      } catch (err) {
        console.warn('Error requesting permissions:', err);
        return false;
      }
    }

    return true;
  }

  /**
   * Check if permissions are granted
   * @returns {Promise<boolean>}
   */
  async hasPermissions() {
    if (Platform.OS === 'android') {
      try {
        const hasBluetoothScan = await PermissionsAndroid.check(
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN
        );
        const hasBluetoothConnect = await PermissionsAndroid.check(
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT
        );
        const hasLocation = await PermissionsAndroid.check(
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
        );

        return hasBluetoothScan && hasBluetoothConnect && hasLocation;
      } catch (err) {
        console.warn('Error checking permissions:', err);
        return false;
      }
    }

    return true;
  }

  /**
   * Start scanning for Bluetooth devices
   * @returns {Promise<void>}
   */
  startScanning() {
    return RNACSBluetooth.startScanningForDevices();
  }

  /**
   * Stop scanning for Bluetooth devices
   * @returns {Promise<void>}
   */
  stopScanning() {
    return RNACSBluetooth.stopScanningForDevices();
  }

  /**
   * Connect to a Bluetooth device
   * @param {string} address - MAC address or UUID
   * @returns {Promise<boolean>}
   */
  connect(address) {
    return RNACSBluetooth.connect(address);
  }

  /**
   * Disconnect from the current device
   * @returns {Promise<void>}
   */
  disconnect() {
    return RNACSBluetooth.disconnect();
  }

  /**
   * Make the reader beep
   * @returns {Promise<void>}
   */
  beep() {
    return RNACSBluetooth.beep();
  }

  /**
   * Authenticate with the reader
   * @returns {Promise<void>}
   */
  authenticate() {
    return RNACSBluetooth.authenticate();
  }

  /**
   * Start automatic card polling
   * @returns {Promise<void>}
   */
  startPolling() {
    return RNACSBluetooth.startPolling();
  }

  /**
   * Stop automatic card polling
   * @returns {Promise<void>}
   */
  stopPolling() {
    return RNACSBluetooth.stopPolling();
  }

  /**
   * Disable sleep mode
   * @returns {Promise<void>}
   */
  disableSleep() {
    return RNACSBluetooth.disableSleep();
  }

  /**
   * Request card UID
   * @returns {Promise<void>}
   */
  requestUid() {
    return RNACSBluetooth.requestUid();
  }

  // Event Listeners

  /**
   * Listen for scanning status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onScanningStatusChange(callback) {
    const subscription = eventEmitter.addListener('onScanningStatusChange', callback);
    this._listeners.push(subscription);
    return () => subscription.remove();
  }

  /**
   * Listen for discovered devices
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onDevicesDiscovered(callback) {
    const subscription = eventEmitter.addListener('onDevicesDiscovered', callback);
    this._listeners.push(subscription);
    return () => subscription.remove();
  }

  /**
   * Listen for connection status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onConnectionStatusChange(callback) {
    const subscription = eventEmitter.addListener('onConnectionStatusChange', callback);
    this._listeners.push(subscription);
    return () => subscription.remove();
  }

  /**
   * Listen for card detection events
   * @param {function} callback - Receives {uid: string}
   * @returns {function} Unsubscribe function
   */
  onCardDetected(callback) {
    const subscription = eventEmitter.addListener('onCardDetected', (uid) => {
      callback({ uid });
    });
    this._listeners.push(subscription);
    return () => subscription.remove();
  }

  /**
   * Remove all event listeners
   */
  removeAllListeners() {
    this._listeners.forEach(listener => listener.remove());
    this._listeners = [];
  }
}

export default BluetoothReader;
