import AcsReader from './AcsReader';
import BluetoothAdapter from './adapters/BluetoothAdapter';
import UsbAdapter from './adapters/UsbAdapter';

// Export the main unified class
export default AcsReader;

// Export the class itself for custom instances
export { AcsReader };

// Export backward compatibility adapters
export { BluetoothAdapter, UsbAdapter };
