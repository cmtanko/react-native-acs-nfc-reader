# react-native-acs-reader

Unified React Native module for ACS card readers supporting both **Bluetooth** and **USB** connections.

This module combines the functionality of `react-native-acs-bluetooth` and `react-native-acs-usb` into a single package, allowing you to work with ACS NFC card readers using either connection type through a unified API.

## Features

- ✅ **Unified API** - Single package for both Bluetooth and USB modes
- ✅ **TypeScript Support** - Full TypeScript definitions included
- ✅ **Cross-platform** - Works on both iOS and Android
- ✅ **Bluetooth Mode** - Full support for ACS Bluetooth readers (ACR1255U-J1, etc.)
- ✅ **USB Mode** - Support for ACS USB readers (ACR122U, ACR1281U, etc.) on Android
- ✅ **Backward Compatible** - Migration adapters for existing codebases
- ✅ **Event-driven** - Real-time card detection and status updates

## Supported Readers

### Bluetooth Mode
- ACR1255U-J1 Bluetooth NFC Reader
- Other ACS Bluetooth-enabled readers

### USB Mode (Android only)
- ACR122U USB NFC Reader
- ACR1281U-C2 Dual Interface Reader
- ACR1252U USB NFC Reader III
- Other ACS USB readers

## Installation

```bash
npm install react-native-acs-reader
# or
yarn add react-native-acs-reader
```

### Additional Setup

#### Android

Add the package to your `MainApplication.java`:

```java
import com.reactnativeacsreader.AcsReaderPackage;

@Override
protected List<ReactPackage> getPackages() {
  return Arrays.<ReactPackage>asList(
      new MainReactPackage(),
      new AcsReaderPackage()  // Add this line
  );
}
```

#### iOS

Run pod install:

```bash
cd ios && pod install
```

For Bluetooth mode, add the following to your `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app needs Bluetooth to communicate with card readers</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app needs Bluetooth to communicate with card readers</string>
```

## Usage

### Basic Example - Bluetooth Mode

```javascript
import AcsReader from 'react-native-acs-reader';

// Create a Bluetooth reader instance
const reader = new AcsReader('bluetooth');

// Request permissions
const hasPermissions = await reader.requestPermissions();

if (hasPermissions) {
  // Set up card detection listener
  reader.onCardDetected((data) => {
    console.log('Card UID:', data.uid);
  });

  // Start scanning for devices
  await reader.startScanning();

  // Listen for discovered devices
  reader.onDevicesDiscovered((devices) => {
    console.log('Found devices:', devices);
    // devices = [{ name: 'ACR1255U-J1', address: 'XX:XX:XX:XX:XX:XX' }]
  });

  // Connect to a device
  await reader.connect(deviceAddress);

  // The reader will automatically:
  // - Authenticate
  // - Enable card polling
  // - Start detecting cards
}
```

### Basic Example - USB Mode

```javascript
import AcsReader from 'react-native-acs-reader';

// Create a USB reader instance
const reader = new AcsReader('usb');

// Request permissions
await reader.requestPermissions();

// Get available USB devices
const devices = await reader.getDevices();
console.log('Available devices:', devices);
// devices = [{ productId: 8197, deviceName: 'ACS Reader (8197)' }]

// Connect to a device
const deviceName = await reader.connect(devices[0].productId.toString());
console.log('Connected to:', deviceName);

// Listen for card detection
reader.onCardDetected((data) => {
  console.log('Card UID:', data.uid);
  console.log('Device ID:', data.deviceId);
});

// Open the device for reading
await reader.openDevice(devices[0].productId.toString());
```

### Advanced Bluetooth Features

```javascript
const reader = new AcsReader('bluetooth');

// Manual authentication
await reader.authenticate();

// Control card polling
await reader.startPolling();
await reader.stopPolling();

// Make the reader beep
await reader.beep();

// Disable sleep mode
await reader.disableSleep();

// Manually request card UID
await reader.requestUid();

// Connection status
reader.onConnectionStatusChange((isConnected) => {
  console.log('Connected:', isConnected);
});

// Scanning status
reader.onScanningStatusChange((isScanning) => {
  console.log('Scanning:', isScanning);
});
```

### Advanced USB Features

```javascript
const reader = new AcsReader('usb');

// Control LED (product ID, hex command)
await reader.updateLight(8197, 'E0 00 00 40 01'); // Turn on
await reader.updateLight(8197, 'E0 00 00 40 00'); // Turn off

// Listen for logs
reader.onLog((message) => {
  console.log('Reader log:', message);
});

// Close device
await reader.closeDevice(productId.toString());
```

### Cleanup

```javascript
// Always clean up listeners when done
reader.removeAllListeners();

// Disconnect
await reader.disconnect();
```

## API Reference

### Constructor

```typescript
const reader = new AcsReader(mode: 'bluetooth' | 'usb');
```

### Common Methods (Both Modes)

| Method | Description | Returns |
|--------|-------------|---------|
| `requestPermissions()` | Request necessary permissions | `Promise<boolean>` |
| `hasPermissions()` | Check if permissions are granted | `Promise<boolean>` |
| `connect(deviceId)` | Connect to a device | `Promise<boolean \| string>` |
| `disconnect()` | Disconnect from device | `Promise<void>` |
| `onCardDetected(callback)` | Listen for card detection | `UnsubscribeFunction` |
| `onConnectionStatusChange(callback)` | Listen for connection changes | `UnsubscribeFunction` |
| `removeAllListeners()` | Remove all event listeners | `void` |

### Bluetooth-Specific Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `startScanning()` | Start BLE scanning | `Promise<void>` |
| `stopScanning()` | Stop BLE scanning | `Promise<void>` |
| `onScanningStatusChange(callback)` | Listen for scanning status | `UnsubscribeFunction` |
| `onDevicesDiscovered(callback)` | Listen for discovered devices | `UnsubscribeFunction` |
| `beep()` | Make reader beep | `Promise<void>` |
| `authenticate()` | Authenticate with reader | `Promise<void>` |
| `startPolling()` | Start card polling | `Promise<void>` |
| `stopPolling()` | Stop card polling | `Promise<void>` |
| `disableSleep()` | Disable sleep mode | `Promise<void>` |
| `requestUid()` | Request card UID | `Promise<void>` |

### USB-Specific Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `getDevices()` | Get available USB devices | `Promise<UsbDevice[]>` |
| `openDevice(productId)` | Open device for communication | `Promise<void>` |
| `closeDevice(productId)` | Close device | `Promise<void>` |
| `updateLight(productId, command)` | Control LED | `Promise<void>` |
| `onLog(callback)` | Listen for log messages | `UnsubscribeFunction` |

## Migration Guide

If you're migrating from the separate `react-native-acs-bluetooth` or `react-native-acs-usb` modules, we provide backward compatibility adapters.

### Migrating from react-native-acs-bluetooth

```javascript
// Old code:
import AcsBluetooth from 'react-native-acs-bluetooth';

// New code (Option 1 - using adapter):
import { BluetoothAdapter as AcsBluetooth } from 'react-native-acs-reader';

// New code (Option 2 - using new API):
import AcsReader from 'react-native-acs-reader';
const reader = new AcsReader('bluetooth');
```

### Migrating from react-native-acs-usb

```javascript
// Old code:
import AcsUsb from 'react-native-acs-usb';

// New code (Option 1 - using adapter):
import { UsbAdapter as AcsUsb } from 'react-native-acs-reader';

// New code (Option 2 - using new API):
import AcsReader from 'react-native-acs-reader';
const reader = new AcsReader('usb');
```

The adapters maintain 100% backward compatibility with the original APIs, so your existing code should work without modifications.

## Platform Support

| Feature | Android | iOS |
|---------|---------|-----|
| Bluetooth | ✅ | ✅ |
| USB | ✅ | ❌ |

> **Note**: iOS does not support USB host mode, so USB readers only work on Android.

## Common Product IDs

| Reader Model | Product ID | Connection |
|--------------|------------|------------|
| ACR122U | 8197 | USB |
| ACR1281U-C2 | 8199 | USB |
| ACR1252U | 8216 | USB |
| ACR1255U-J1 | 8217 | Bluetooth |

## Troubleshooting

### Bluetooth Issues

**Problem**: Unable to discover devices
- Ensure Bluetooth is enabled
- Check location permissions (required on Android for BLE scanning)
- Make sure the reader is powered on and in pairing mode

**Problem**: Connection drops frequently
- Check signal strength
- Disable battery optimization for your app
- Try using `autoConnect: true` in connect options

### USB Issues

**Problem**: Device not detected
- Verify the device is properly connected
- Check USB host mode is supported on your device
- Ensure USB permissions are granted

**Problem**: "USB module not available"
- iOS does not support USB host mode
- Make sure the library is properly linked

## Requirements

- React Native >= 0.60
- Android SDK >= 21
- iOS >= 11.0

## Dependencies

The module includes the following ACS SDKs:
- `acsbt-1.0.0preview8.jar` (Bluetooth)
- `acssmc-1.1.5.jar` (USB)

## License

MIT

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues and questions, please open an issue on GitHub.

## Credits

This module combines and builds upon:
- react-native-acs-bluetooth
- react-native-acs-usb
