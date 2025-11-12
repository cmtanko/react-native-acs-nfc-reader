import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import BluetoothReader from './BluetoothReader';
import UsbReader from './UsbReader';

/**
 * Unified ACS Reader class
 * Supports both Bluetooth and USB modes
 */
class AcsReader {
  /**
   * Create a new ACS Reader instance
   * @param {string} mode - 'bluetooth' or 'usb'
   */
  constructor(mode) {
    if (!mode || (mode !== 'bluetooth' && mode !== 'usb')) {
      throw new Error('Invalid mode. Must be "bluetooth" or "usb"');
    }

    this._mode = mode;
    this._implementation = null;

    // Initialize the appropriate implementation based on mode
    if (mode === 'bluetooth') {
      this._implementation = new BluetoothReader();
    } else if (mode === 'usb') {
      this._implementation = new UsbReader();
    }
  }

  /**
   * Get the current mode
   */
  get mode() {
    return this._mode;
  }

  // ===== Common Methods =====

  /**
   * Request necessary permissions
   * @returns {Promise<boolean>}
   */
  requestPermissions() {
    return this._implementation.requestPermissions();
  }

  /**
   * Check if permissions are granted
   * @returns {Promise<boolean>}
   */
  hasPermissions() {
    return this._implementation.hasPermissions();
  }

  /**
   * Connect to a device
   * @param {string} deviceId - Device address (Bluetooth) or product ID (USB)
   * @returns {Promise<boolean|string>}
   */
  connect(deviceId) {
    return this._implementation.connect(deviceId);
  }

  /**
   * Disconnect from the current device
   * @returns {Promise<void>}
   */
  disconnect() {
    return this._implementation.disconnect();
  }

  /**
   * Listen for card detection events
   * @param {function} callback - Called when card is detected
   * @returns {function} Unsubscribe function
   */
  onCardDetected(callback) {
    return this._implementation.onCardDetected(callback);
  }

  /**
   * Listen for connection status changes
   * @param {function} callback - Called when connection status changes
   * @returns {function} Unsubscribe function
   */
  onConnectionStatusChange(callback) {
    return this._implementation.onConnectionStatusChange(callback);
  }

  /**
   * Remove all event listeners
   */
  removeAllListeners() {
    return this._implementation.removeAllListeners();
  }

  // ===== Bluetooth-specific Methods =====

  /**
   * Start scanning for Bluetooth devices
   * Only available in Bluetooth mode
   * @returns {Promise<void>}
   */
  startScanning() {
    this._ensureBluetoothMode();
    return this._implementation.startScanning();
  }

  /**
   * Stop scanning for Bluetooth devices
   * Only available in Bluetooth mode
   * @returns {Promise<void>}
   */
  stopScanning() {
    this._ensureBluetoothMode();
    return this._implementation.stopScanning();
  }

  /**
   * Listen for scanning status changes
   * Only available in Bluetooth mode
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onScanningStatusChange(callback) {
    this._ensureBluetoothMode();
    return this._implementation.onScanningStatusChange(callback);
  }

  /**
   * Listen for discovered devices
   * Only available in Bluetooth mode
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onDevicesDiscovered(callback) {
    this._ensureBluetoothMode();
    return this._implementation.onDevicesDiscovered(callback);
  }

  /**
   * Make the reader beep
   * Only available in Bluetooth mode
   * @returns {Promise<void>}
   */
  beep() {
    this._ensureBluetoothMode();
    return this._implementation.beep();
  }

  /**
   * Authenticate with the reader
   * Only available in Bluetooth mode
   * @returns {Promise<void>}
   */
  authenticate() {
    this._ensureBluetoothMode();
    return this._implementation.authenticate();
  }

  /**
   * Start automatic card polling
   * Only available in Bluetooth mode
   * @returns {Promise<void>}
   */
  startPolling() {
    this._ensureBluetoothMode();
    return this._implementation.startPolling();
  }

  /**
   * Stop automatic card polling
   * Only available in Bluetooth mode
   * @returns {Promise<void>}
   */
  stopPolling() {
    this._ensureBluetoothMode();
    return this._implementation.stopPolling();
  }

  /**
   * Disable reader sleep mode
   * Only available in Bluetooth mode
   * @returns {Promise<void>}
   */
  disableSleep() {
    this._ensureBluetoothMode();
    return this._implementation.disableSleep();
  }

  /**
   * Manually request card UID
   * Only available in Bluetooth mode
   * @returns {Promise<void>}
   */
  requestUid() {
    this._ensureBluetoothMode();
    return this._implementation.requestUid();
  }

  // ===== USB-specific Methods =====

  /**
   * Get list of available USB devices
   * Only available in USB mode
   * @returns {Promise<Array>}
   */
  getDevices() {
    this._ensureUsbMode();
    return this._implementation.getDevices();
  }

  /**
   * Open a USB device for communication
   * Only available in USB mode
   * @param {string} productId
   * @returns {Promise<void>}
   */
  openDevice(productId) {
    this._ensureUsbMode();
    return this._implementation.openDevice(productId);
  }

  /**
   * Close a USB device
   * Only available in USB mode
   * @param {string} productId
   * @returns {Promise<void>}
   */
  closeDevice(productId) {
    this._ensureUsbMode();
    return this._implementation.closeDevice(productId);
  }

  /**
   * Control the device LED
   * Only available in USB mode
   * @param {number} productId
   * @param {string} commandString
   * @returns {Promise<void>}
   */
  updateLight(productId, commandString) {
    this._ensureUsbMode();
    return this._implementation.updateLight(productId, commandString);
  }

  /**
   * Listen for log messages
   * Only available in USB mode
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onLog(callback) {
    this._ensureUsbMode();
    return this._implementation.onLog(callback);
  }

  // ===== Helper Methods =====

  _ensureBluetoothMode() {
    if (this._mode !== 'bluetooth') {
      throw new Error('This method is only available in Bluetooth mode');
    }
  }

  _ensureUsbMode() {
    if (this._mode !== 'usb') {
      throw new Error('This method is only available in USB mode');
    }
  }
}

export default AcsReader;
