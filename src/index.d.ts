declare module 'react-native-acs-reader' {
  /**
   * Reader mode type
   */
  export type ReaderMode = 'bluetooth' | 'usb';

  /**
   * Bluetooth device information
   */
  export interface BluetoothDevice {
    name: string;
    address: string;
  }

  /**
   * Card UID data
   */
  export interface CardData {
    uid: string;
    deviceId?: string;
  }

  /**
   * USB device information
   */
  export interface UsbDevice {
    productId: number;
    deviceName: string;
  }

  /**
   * Common event listener unsubscribe function
   */
  export type UnsubscribeFunction = () => void;

  /**
   * Main unified ACS Reader class
   */
  export class AcsReader {
    /**
     * Create a new ACS Reader instance
     * @param mode - The connection mode ('bluetooth' or 'usb')
     */
    constructor(mode: ReaderMode);

    /**
     * Get the current mode
     */
    readonly mode: ReaderMode;

    // === Common Methods ===

    /**
     * Request necessary permissions for the reader
     * @returns Promise that resolves to true if permissions granted
     */
    requestPermissions(): Promise<boolean>;

    /**
     * Check if permissions are granted
     * @returns Promise that resolves to true if permissions are granted
     */
    hasPermissions(): Promise<boolean>;

    /**
     * Connect to a specific device
     * @param deviceId - Device address (Bluetooth) or product ID (USB)
     * @returns Promise that resolves when connected
     */
    connect(deviceId: string): Promise<boolean | string>;

    /**
     * Disconnect from the current device
     * @returns Promise that resolves when disconnected
     */
    disconnect(): Promise<void>;

    /**
     * Listen for card detection events
     * @param callback - Function called when card is detected
     * @returns Unsubscribe function
     */
    onCardDetected(callback: (data: CardData) => void): UnsubscribeFunction;

    /**
     * Listen for connection status changes
     * @param callback - Function called when connection status changes
     * @returns Unsubscribe function
     */
    onConnectionStatusChange(callback: (isConnected: boolean) => void): UnsubscribeFunction;

    /**
     * Remove all event listeners
     */
    removeAllListeners(): void;

    // === Bluetooth-specific Methods ===

    /**
     * Start scanning for Bluetooth devices
     * Only available in Bluetooth mode
     * @returns Promise that resolves when scanning starts
     */
    startScanning(): Promise<void>;

    /**
     * Stop scanning for Bluetooth devices
     * Only available in Bluetooth mode
     * @returns Promise that resolves when scanning stops
     */
    stopScanning(): Promise<void>;

    /**
     * Listen for scanning status changes
     * Only available in Bluetooth mode
     * @param callback - Function called when scanning status changes
     * @returns Unsubscribe function
     */
    onScanningStatusChange(callback: (isScanning: boolean) => void): UnsubscribeFunction;

    /**
     * Listen for discovered devices
     * Only available in Bluetooth mode
     * @param callback - Function called when devices are discovered
     * @returns Unsubscribe function
     */
    onDevicesDiscovered(callback: (devices: BluetoothDevice[]) => void): UnsubscribeFunction;

    /**
     * Make the reader beep
     * Only available in Bluetooth mode
     * @returns Promise that resolves when beep command is sent
     */
    beep(): Promise<void>;

    /**
     * Authenticate with the reader
     * Only available in Bluetooth mode
     * @returns Promise that resolves when authenticated
     */
    authenticate(): Promise<void>;

    /**
     * Start automatic card polling
     * Only available in Bluetooth mode
     * @returns Promise that resolves when polling starts
     */
    startPolling(): Promise<void>;

    /**
     * Stop automatic card polling
     * Only available in Bluetooth mode
     * @returns Promise that resolves when polling stops
     */
    stopPolling(): Promise<void>;

    /**
     * Disable reader sleep mode
     * Only available in Bluetooth mode
     * @returns Promise that resolves when sleep is disabled
     */
    disableSleep(): Promise<void>;

    /**
     * Manually request card UID
     * Only available in Bluetooth mode
     * @returns Promise that resolves when UID is requested
     */
    requestUid(): Promise<void>;

    // === USB-specific Methods ===

    /**
     * Get list of available USB devices
     * Only available in USB mode
     * @returns Promise that resolves to array of USB devices
     */
    getDevices(): Promise<UsbDevice[]>;

    /**
     * Open a USB device for communication
     * Only available in USB mode
     * @param productId - Product ID of the device
     * @returns Promise that resolves when device is opened
     */
    openDevice(productId: string): Promise<void>;

    /**
     * Close a USB device
     * Only available in USB mode
     * @param productId - Product ID of the device
     * @returns Promise that resolves when device is closed
     */
    closeDevice(productId: string): Promise<void>;

    /**
     * Control the device LED
     * Only available in USB mode
     * @param productId - Product ID of the device
     * @param commandString - Hex command string for LED control
     * @returns Promise that resolves when command is sent
     */
    updateLight(productId: number, commandString: string): Promise<void>;

    /**
     * Listen for log messages
     * Only available in USB mode
     * @param callback - Function called with log messages
     * @returns Unsubscribe function
     */
    onLog(callback: (message: string) => void): UnsubscribeFunction;
  }

  /**
   * Default export - AcsReader class
   */
  const AcsReaderDefault: typeof AcsReader;
  export default AcsReaderDefault;

  // === Backward Compatibility Adapters ===

  /**
   * Bluetooth adapter for backward compatibility with react-native-acs-bluetooth
   */
  export class BluetoothAdapter {
    requestPermissions(): Promise<boolean>;
    hasPermissions(): Promise<boolean>;
    startScanningForDevices(): Promise<void>;
    stopScanningForDevices(): Promise<void>;
    connect(address: string): Promise<boolean>;
    disconnect(): Promise<void>;
    beep(): Promise<void>;
    authenticate(): Promise<void>;
    startPolling(): Promise<void>;
    stopPolling(): Promise<void>;
    disableSleep(): Promise<void>;
    requestUid(): Promise<void>;
    onScanningStatusChange(callback: (isScanning: boolean) => void): UnsubscribeFunction;
    onDevicesDiscovered(callback: (devices: BluetoothDevice[]) => void): UnsubscribeFunction;
    onConnectionStatusChange(callback: (isConnected: boolean) => void): UnsubscribeFunction;
    onCardDetected(callback: (uid: string) => void): UnsubscribeFunction;
    removeAllListeners(): void;
  }

  /**
   * USB adapter for backward compatibility with react-native-acs-usb
   */
  export class UsbAdapter {
    setupCardReader(enterReaderID: string, exitReaderID: string): void;
    connectDevice(productID: string): Promise<string>;
    getProductIDs(): Promise<number[]>;
    openDevice(productID: string): void;
    closeDevice(productID: string): void;
    updateLight(productID: number, commandString: string): void;
    updateIsTapping(status: boolean): void;
    onLog(callback: (message: string) => void): UnsubscribeFunction;
    onCardUID(callback: (data: { uid: string; productID: string }) => void): UnsubscribeFunction;
    onEnterReaderOpenChange(callback: (isOpen: boolean) => void): UnsubscribeFunction;
    onEnterReaderConnectChange(callback: (isConnected: boolean) => void): UnsubscribeFunction;
    onExitReaderOpenChange(callback: (isOpen: boolean) => void): UnsubscribeFunction;
    onExitReaderConnectChange(callback: (isConnected: boolean) => void): UnsubscribeFunction;
    onIsTappingChange(callback: (isTapping: boolean) => void): UnsubscribeFunction;
    onTappingAlert(callback: (alert: boolean) => void): UnsubscribeFunction;
    removeAllListeners(): void;
  }
}
