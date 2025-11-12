import { NativeModules, NativeEventEmitter } from 'react-native';

const { AcsUsb } = NativeModules;

if (!AcsUsb) {
  console.warn('AcsUsb native module is not available.');
}

const eventEmitter = AcsUsb ? new NativeEventEmitter(AcsUsb) : null;

/**
 * USB Adapter for backward compatibility with react-native-acs-usb
 * This provides the exact same API as the original module
 */
class UsbAdapter {
  constructor() {
    this._listeners = new Map();
  }

  /**
   * Setup card reader product IDs
   * @param {string} enterReaderID - Product ID for enter reader
   * @param {string} exitReaderID - Product ID for exit reader
   */
  setupCardReader(enterReaderID, exitReaderID) {
    if (!AcsUsb) {
      console.warn('USB module not available');
      return;
    }
    AcsUsb.setupCardReader(enterReaderID, exitReaderID);
  }

  /**
   * Connect to a USB device
   * @param {string} productID - Product ID of the device
   * @returns {Promise<string>} Device name
   */
  connectDevice(productID) {
    if (!AcsUsb) {
      return Promise.reject(new Error('USB module not available'));
    }
    return AcsUsb.connectDevice(productID);
  }

  /**
   * Get list of available product IDs
   * @returns {Promise<number[]>} Array of product IDs
   */
  getProductIDs() {
    if (!AcsUsb) {
      return Promise.resolve([]);
    }
    return AcsUsb.getProductIDs();
  }

  /**
   * Open a device by product ID
   * @param {string} productID - Product ID of the device
   */
  openDevice(productID) {
    if (!AcsUsb) {
      console.warn('USB module not available');
      return;
    }
    AcsUsb.openDevice(productID);
  }

  /**
   * Close a device by product ID
   * @param {string} productID - Product ID of the device
   */
  closeDevice(productID) {
    if (!AcsUsb) {
      console.warn('USB module not available');
      return;
    }
    AcsUsb.closeDevice(productID);
  }

  /**
   * Update reader light
   * @param {number} productID - Product ID of the device
   * @param {string} commandString - Hex command string (e.g., "E0 00 00 40 01")
   */
  updateLight(productID, commandString) {
    if (!AcsUsb) {
      console.warn('USB module not available');
      return;
    }
    AcsUsb.updateLight(productID, commandString);
  }

  /**
   * Update tapping status
   * @param {boolean} status - Tapping status
   */
  updateIsTapping(status) {
    if (!AcsUsb) {
      console.warn('USB module not available');
      return;
    }
    AcsUsb.updateIsTapping(status);
  }

  /**
   * Subscribe to log messages
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onLog(callback) {
    return this._addEventListener('onLog', callback);
  }

  /**
   * Subscribe to card UID events
   * @param {function} callback - Called with {uid: string, productID: string}
   * @returns {function} Unsubscribe function
   */
  onCardUID(callback) {
    return this._addEventListener('onCardUID', callback);
  }

  /**
   * Subscribe to enter reader open status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onEnterReaderOpenChange(callback) {
    return this._addEventListener('onEnterReaderOpenChange', callback);
  }

  /**
   * Subscribe to enter reader connect status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onEnterReaderConnectChange(callback) {
    return this._addEventListener('onEnterReaderConnectChange', callback);
  }

  /**
   * Subscribe to exit reader open status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onExitReaderOpenChange(callback) {
    return this._addEventListener('onExitReaderOpenChange', callback);
  }

  /**
   * Subscribe to exit reader connect status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onExitReaderConnectChange(callback) {
    return this._addEventListener('onExitReaderConnectChange', callback);
  }

  /**
   * Subscribe to tapping status changes
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onIsTappingChange(callback) {
    return this._addEventListener('onIsTappingChange', callback);
  }

  /**
   * Subscribe to tapping alert events
   * @param {function} callback
   * @returns {function} Unsubscribe function
   */
  onTappingAlert(callback) {
    return this._addEventListener('onTappingAlert', callback);
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

// Export singleton instance for backward compatibility
export default new UsbAdapter();
