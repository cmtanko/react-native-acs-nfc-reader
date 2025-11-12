import { NativeModules, NativeEventEmitter, PermissionsAndroid, Platform } from 'react-native';

const { RNACSBluetooth } = NativeModules;

if (!RNACSBluetooth) {
  throw new Error('RNACSBluetooth native module is not available. Make sure the library is linked correctly.');
}

const eventEmitter = new NativeEventEmitter(RNACSBluetooth);

/**
 * Bluetooth Adapter for backward compatibility with react-native-acs-bluetooth
 * This provides the exact same API as the original module
 */
class BluetoothAdapter {
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
   * Check if necessary permissions are granted
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
   * Start scanning for BLE devices
   * @returns {Promise<void>}
   */
  startScanningForDevices() {
    return RNACSBluetooth.startScanningForDevices();
  }

  /**
   * Stop scanning for BLE devices
   * @returns {Promise<void>}
   */
  stopScanningForDevices() {
    return RNACSBluetooth.stopScanningForDevices();
  }

  /**
   * Connect to an ACS Bluetooth card reader
   * @param {string} address - MAC address of the device
   * @returns {Promise<boolean>}
   */
  connect(address) {
    return RNACSBluetooth.connect(address);
  }

  /**
   * Disconnect from the current reader
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
   * Authenticate with the reader using master key
   * @returns {Promise<void>}
   */
  authenticate() {
    return RNACSBluetooth.authenticate();
  }

  /**
   * Start auto-polling for cards
   * @returns {Promise<void>}
   */
  startPolling() {
    return RNACSBluetooth.startPolling();
  }

  /**
   * Stop auto-polling for cards
   * @returns {Promise<void>}
   */
  stopPolling() {
    return RNACSBluetooth.stopPolling();
  }

  /**
   * Disable sleep mode on the reader
   * @returns {Promise<void>}
   */
  disableSleep() {
    return RNACSBluetooth.disableSleep();
  }

  /**
   * Request card UID from reader
   * @returns {Promise<void>}
   */
  requestUid() {
    return RNACSBluetooth.requestUid();
  }

  /**
   * Subscribe to scanning status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onScanningStatusChange(callback) {
    const subscription = eventEmitter.addListener('onScanningStatusChange', callback);
    this._listeners.push(subscription);
    return () => subscription.remove();
  }

  /**
   * Subscribe to scan results
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onDevicesDiscovered(callback) {
    const subscription = eventEmitter.addListener('onDevicesDiscovered', callback);
    this._listeners.push(subscription);
    return () => subscription.remove();
  }

  /**
   * Subscribe to reader connection status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onConnectionStatusChange(callback) {
    const subscription = eventEmitter.addListener('onConnectionStatusChange', callback);
    this._listeners.push(subscription);
    return () => subscription.remove();
  }

  /**
   * Subscribe to card UID updates
   * @param {function} callback - Called with UID string
   * @returns {function} Unsubscribe function
   */
  onCardDetected(callback) {
    const subscription = eventEmitter.addListener('onCardDetected', callback);
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

// Export singleton instance for backward compatibility
export default new BluetoothAdapter();
