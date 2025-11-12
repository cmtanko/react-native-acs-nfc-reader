import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const { AcsUsb } = NativeModules;

if (!AcsUsb) {
  console.warn('AcsUsb native module is not available. USB mode may not work properly.');
}

const eventEmitter = AcsUsb ? new NativeEventEmitter(AcsUsb) : null;

/**
 * USB Reader Implementation
 * Wraps the native USB module for ACS card readers
 * Simplified to single reader support
 */
class UsbReader {
  constructor() {
    this._listeners = new Map();
    this._connectedProductId = null;
  }

  /**
   * Request necessary permissions
   * USB permissions are handled by the OS automatically
   * @returns {Promise<boolean>}
   */
  async requestPermissions() {
    if (Platform.OS === 'android') {
      return true; // USB permissions are requested automatically on Android
    }
    return false; // iOS doesn't support USB host mode
  }

  /**
   * Check if permissions are granted
   * @returns {Promise<boolean>}
   */
  async hasPermissions() {
    if (Platform.OS === 'android') {
      return true;
    }
    return false;
  }

  /**
   * Get list of available USB devices
   * @returns {Promise<Array>} Array of {productId, deviceName}
   */
  async getDevices() {
    if (!AcsUsb) {
      throw new Error('USB module not available');
    }

    try {
      const productIds = await AcsUsb.getProductIDs();
      return productIds.map(id => ({
        productId: id,
        deviceName: `ACS Reader (${id})`
      }));
    } catch (error) {
      console.error('Error getting devices:', error);
      return [];
    }
  }

  /**
   * Connect to a USB device
   * @param {string} deviceId - Product ID as string
   * @returns {Promise<string>} Device name
   */
  async connect(deviceId) {
    if (!AcsUsb) {
      throw new Error('USB module not available');
    }

    try {
      const deviceName = await AcsUsb.connectDevice(deviceId);
      this._connectedProductId = deviceId;

      // Auto-open the device after connecting
      AcsUsb.openDevice(deviceId);

      return deviceName || `Connected to ${deviceId}`;
    } catch (error) {
      console.error('Error connecting to device:', error);
      throw error;
    }
  }

  /**
   * Disconnect from the current device
   * @returns {Promise<void>}
   */
  async disconnect() {
    if (!AcsUsb || !this._connectedProductId) {
      return;
    }

    try {
      AcsUsb.closeDevice(this._connectedProductId);
      this._connectedProductId = null;
    } catch (error) {
      console.error('Error disconnecting:', error);
    }
  }

  /**
   * Open a USB device for communication
   * @param {string} productId
   * @returns {Promise<void>}
   */
  async openDevice(productId) {
    if (!AcsUsb) {
      throw new Error('USB module not available');
    }

    AcsUsb.openDevice(productId);
  }

  /**
   * Close a USB device
   * @param {string} productId
   * @returns {Promise<void>}
   */
  async closeDevice(productId) {
    if (!AcsUsb) {
      throw new Error('USB module not available');
    }

    AcsUsb.closeDevice(productId);
  }

  /**
   * Control the device LED
   * @param {number} productId
   * @param {string} commandString - Hex command string
   * @returns {Promise<void>}
   */
  async updateLight(productId, commandString) {
    if (!AcsUsb) {
      throw new Error('USB module not available');
    }

    AcsUsb.updateLight(productId, commandString);
  }

  // Event Listeners

  /**
   * Listen for connection status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onConnectionStatusChange(callback) {
    // USB module has separate events for open/connect
    // We'll map the "connect" event to connection status
    return this._addEventListener('onEnterReaderConnectChange', callback);
  }

  /**
   * Listen for card detection events
   * @param {function} callback - Receives {uid: string, deviceId: string}
   * @returns {function} Unsubscribe function
   */
  onCardDetected(callback) {
    return this._addEventListener('onCardUID', (data) => {
      callback({
        uid: data.uid,
        deviceId: data.productID
      });
    });
  }

  /**
   * Listen for log messages
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onLog(callback) {
    return this._addEventListener('onLog', callback);
  }

  /**
   * Remove all event listeners
   */
  removeAllListeners() {
    this._listeners.forEach((subscription) => {
      subscription.remove();
    });
    this._listeners.clear();
  }

  /**
   * Internal method to add event listener
   * @private
   */
  _addEventListener(eventName, callback) {
    if (!eventEmitter) {
      console.warn('Event emitter not available');
      return () => {};
    }

    const subscription = eventEmitter.addListener(eventName, callback);
    const key = `${eventName}_${Date.now()}`;
    this._listeners.set(key, subscription);

    return () => {
      subscription.remove();
      this._listeners.delete(key);
    };
  }
}

export default UsbReader;
