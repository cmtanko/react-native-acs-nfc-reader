package com.reactnativeacsreader.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.acs.bluetooth.BluetoothReader;
import com.acs.bluetooth.BluetoothReaderGattCallback;
import com.acs.bluetooth.BluetoothReaderManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.HashMap;
import java.util.Map;

public class RNACSBluetoothModule extends ReactContextBaseJavaModule {
    private static final String TAG = "RNACSBluetooth";
    private static final String MODULE_NAME = "RNACSBluetooth";

    // Master key for ACS reader authentication
    private static final String MASTER_KEY = "41 43 52 31 32 35 35 55 2D 4A 31 20 41 75 74 68";

    // Special card status codes
    private static final String CARD_ABSENT = "1111";
    private static final String CARD_FAULTY = "2222";

    private final ReactApplicationContext reactContext;
    private final Handler mainHandler;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothReader bluetoothReader;
    private BluetoothReaderManager bluetoothReaderManager;
    private BluetoothReaderGattCallback readerGattCallback;

    private boolean isScanning = false;
    private boolean isConnected = false;
    private boolean isReaderDetected = false;  // CRITICAL: Prevent infinite loop in onServicesDiscovered
    private Map<String, BluetoothDevice> discoveredDevices = new HashMap<>();

    public RNACSBluetoothModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.mainHandler = new Handler(Looper.getMainLooper());

        try {
            // Initialize Bluetooth
            BluetoothManager bluetoothManager = (BluetoothManager) reactContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                bluetoothAdapter = bluetoothManager.getAdapter();
                Log.d(TAG, "Bluetooth adapter initialized");
            } else {
                Log.e(TAG, "BluetoothManager is null");
            }

            // Initialize BluetoothReaderManager with detection listener
            bluetoothReaderManager = new BluetoothReaderManager();
            bluetoothReaderManager.setOnReaderDetectionListener(new BluetoothReaderManager.OnReaderDetectionListener() {
                @Override
                public void onReaderDetection(BluetoothReader reader) {
                    Log.d(TAG, "Reader detected via callback");
                    onReaderDetected(reader);
                }
            });
            Log.d(TAG, "ACS Bluetooth module initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ACS Bluetooth module: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Called when reader is detected
     */
    private void onReaderDetected(BluetoothReader reader) {
        if (reader == null) {
            Log.e(TAG, "Reader detection failed - reader is null");
            return;
        }

        Log.d(TAG, "Setting up reader listeners");
        bluetoothReader = reader;

        // CRITICAL: Set listeners in EXACT order as NativeScript implementation!
        // 1. APDU Response Listener (FIRST!)
        Log.d(TAG, "1. Setting APDU response listener...");
        BluetoothReader.OnResponseApduAvailableListener apduListener = new BluetoothReader.OnResponseApduAvailableListener() {
            @Override
            public void onResponseApduAvailable(BluetoothReader reader, byte[] apdu, int errorCode) {
                Log.e(TAG, "!!!!! APDU RESPONSE CALLBACK TRIGGERED !!!!! errorCode: " + errorCode + ", apdu: " + (apdu != null ? bytesToHex(apdu) : "null"));
                Log.d(TAG, "APDU Response - errorCode: " + errorCode + ", apdu length: " + (apdu != null ? apdu.length : 0));

                try {
                    if (errorCode == BluetoothReader.ERROR_SUCCESS) {
                        if (apdu != null && apdu.length > 0) {
                            String fullResponse = bytesToHex(apdu);
                            Log.d(TAG, "APDU Response (full): " + fullResponse);
                            Log.d(TAG, "APDU Response (raw bytes): " + java.util.Arrays.toString(apdu));

                            // Extract only first 8 characters (4 bytes = UID) to match NativeScript behavior
                            String uidHex = fullResponse.length() >= 8 ? fullResponse.substring(0, 8) : fullResponse;
                            Log.d(TAG, "Card UID: " + uidHex);
                            sendCardDetectedEvent(uidHex);
                        } else {
                            Log.e(TAG, "APDU response is null or empty");
                        }
                    } else {
                        Log.e(TAG, "APDU error code: " + errorCode + " (ERROR_SUCCESS = " + BluetoothReader.ERROR_SUCCESS + ")");
                        // Even if there's an error, try to extract UID if apdu is not null
                        if (apdu != null && apdu.length > 0) {
                            String fullResponse = bytesToHex(apdu);
                            Log.d(TAG, "APDU Response despite error (full): " + fullResponse);
                            String uidHex = fullResponse.length() >= 8 ? fullResponse.substring(0, 8) : fullResponse;
                            Log.d(TAG, "Card UID (from error response): " + uidHex);
                            sendCardDetectedEvent(uidHex);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception in APDU callback: " + e.getMessage(), e);
                    e.printStackTrace();
                }
            }
        };
        bluetoothReader.setOnResponseApduAvailableListener(apduListener);
        Log.d(TAG, "APDU response listener set. Listener object: " + (apduListener != null ? "not null" : "NULL"));

        // DIAGNOSTIC: Verify listener was actually set by trying to trigger a test
        Log.d(TAG, "DIAGNOSTIC: BluetoothReader object state:");
        Log.d(TAG, "  - Reader instance: " + (bluetoothReader != null ? "not null" : "NULL"));
        Log.d(TAG, "  - Listener reference: " + (apduListener != null ? "not null" : "NULL"));
        Log.d(TAG, "CRITICAL: If APDU responses don't work, they might come through escape response channel!");

        // 2. Authentication Complete Listener
        Log.d(TAG, "2. Setting authentication listener...");
        bluetoothReader.setOnAuthenticationCompleteListener(new BluetoothReader.OnAuthenticationCompleteListener() {
            @Override
            public void onAuthenticationComplete(BluetoothReader reader, int errorCode) {
                if (errorCode == BluetoothReader.ERROR_SUCCESS) {
                    Log.d(TAG, "Authentication successful - starting polling and beeping");

                    // Start auto polling
                    byte[] pollCommand = hexStringToByteArray("E0 00 00 40 01");
                    reader.transmitEscapeCommand(pollCommand);

                    // Disable sleep
                    byte[] sleepCommand = hexStringToByteArray("E0 00 00 48 00");
                    reader.transmitEscapeCommand(sleepCommand);

                    // Long beep to confirm reader is ready
                    byte[] beepCommand = hexStringToByteArray("E0 00 00 28 01 50");
                    reader.transmitEscapeCommand(beepCommand);

                    Log.d(TAG, "Reader ready for card scanning");
                } else {
                    Log.e(TAG, "Authentication failed: " + errorCode);
                }
            }
        });

        // 3. Enable Notification Complete Listener
        Log.d(TAG, "3. Setting notification listener...");
        bluetoothReader.setOnEnableNotificationCompleteListener(new BluetoothReader.OnEnableNotificationCompleteListener() {
            @Override
            public void onEnableNotificationComplete(BluetoothReader reader, int errorCode) {
                if (errorCode == BluetoothReader.ERROR_SUCCESS) {
                    Log.d(TAG, "Notifications enabled - authenticating");
                    // Automatically authenticate
                    byte[] authCommand = hexStringToByteArray(MASTER_KEY);
                    reader.authenticate(authCommand);
                } else {
                    Log.e(TAG, "Failed to enable notifications: " + errorCode);
                }
            }
        });

        // 4. Card Status Change Listener
        Log.d(TAG, "4. Setting card status listener...");
        bluetoothReader.setOnCardStatusChangeListener(new BluetoothReader.OnCardStatusChangeListener() {
            @Override
            public void onCardStatusChange(BluetoothReader reader, int status) {
                Log.d(TAG, "Card status changed: " + status);

                if (status == BluetoothReader.CARD_STATUS_PRESENT) {
                    Log.d(TAG, "Card present - waiting before requesting UID");
                    // Wait a bit for card to settle before sending APDU command
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Requesting UID after delay");
                            // Automatically request UID when card is present
                            byte[] uidCommand = hexStringToByteArray("FF CA 00 00 00");
                            Log.d(TAG, "Transmitting APDU command: " + bytesToHex(uidCommand));
                            Log.d(TAG, "APDU command bytes: " + java.util.Arrays.toString(uidCommand));
                            Log.d(TAG, "Reader object: " + (reader != null ? "not null" : "NULL"));
                            Log.d(TAG, "Checking if APDU listener is set...");
                            
                            // Verify the listener is still set
                            if (bluetoothReader != null && bluetoothReader == reader) {
                                Log.d(TAG, "Reader instance matches, listener should be active");
                            } else {
                                Log.e(TAG, "Reader instance mismatch! bluetoothReader: " + (bluetoothReader != null ? "not null" : "NULL") + ", reader: " + (reader != null ? "not null" : "NULL"));
                            }
                            
                            boolean success = reader.transmitApdu(uidCommand);
                            Log.d(TAG, "TransmitApdu returned: " + success);
                            if (!success) {
                                Log.e(TAG, "Failed to transmit APDU command for UID");
                            } else {
                                Log.d(TAG, "APDU command sent successfully, waiting for response...");
                                Log.d(TAG, "Expected callbacks:");
                                Log.d(TAG, "  1. onResponseApduAvailable (preferred) OR");
                                Log.d(TAG, "  2. onEscapeResponseAvailable (fallback)");

                                // Add another delay to check if response comes
                                mainHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d(TAG, "Checking for APDU response after delay...");
                                        Log.e(TAG, "WARNING: No APDU/Escape response received after 100ms!");
                                        Log.e(TAG, "This could mean:");
                                        Log.e(TAG, "  1. Card is not responding to APDU command");
                                        Log.e(TAG, "  2. Listener callbacks are not being triggered");
                                        Log.e(TAG, "  3. Response is coming through a different channel");
                                    }
                                }, 100);
                            }
                        }
                    }, 200); // Wait 200ms for card to settle
                } else if (status == BluetoothReader.CARD_STATUS_ABSENT) {
                    Log.d(TAG, "Card absent");
                    sendCardDetectedEvent(CARD_ABSENT);
                } else {
                    Log.d(TAG, "Card faulty or unknown status: " + status);
                    sendCardDetectedEvent(CARD_FAULTY);
                }
            }
        });

        // 5. Escape Response Listener (LAST!)
        Log.d(TAG, "5. Setting escape response listener...");
        bluetoothReader.setOnEscapeResponseAvailableListener(new BluetoothReader.OnEscapeResponseAvailableListener() {
            @Override
            public void onEscapeResponseAvailable(BluetoothReader reader, byte[] response, int errorCode) {
                Log.e(TAG, "!!!!! ESCAPE RESPONSE CALLBACK TRIGGERED !!!!! errorCode: " + errorCode);
                if (response != null) {
                    String hexResponse = bytesToHex(response);
                    Log.d(TAG, "Escape response (HEX): " + hexResponse + ", errorCode: " + errorCode);
                    Log.d(TAG, "Escape response (RAW): " + java.util.Arrays.toString(response));
                    Log.d(TAG, "Escape response length: " + response.length + " bytes");

                    // IMPORTANT: Some APDU responses come through escape channel instead!
                    // Check if this might be an APDU response (starts with card UID, typically 4-7 bytes)
                    // APDU responses for UID command typically return: UID (4-7 bytes) + SW1 (0x90) + SW2 (0x00)
                    if (response.length >= 4 && response.length <= 10) {
                        Log.d(TAG, "*** Possible APDU/UID response detected in escape response! ***");
                        // Extract UID (first 4-7 bytes, excluding status words if present)
                        int uidLength = response.length >= 6 ? response.length - 2 : response.length;
                        String uidHex = hexResponse.substring(0, uidLength * 2);
                        Log.d(TAG, "Extracted UID from escape response: " + uidHex);
                        sendCardDetectedEvent(uidHex);
                    } else {
                        Log.d(TAG, "Escape response does not look like a UID (length: " + response.length + ")");
                    }
                } else {
                    Log.d(TAG, "Escape response is null, errorCode: " + errorCode);
                }
            }
        });

        Log.d(TAG, "All listeners set in NativeScript order, enabling notifications");
        // Enable notifications
        if (!bluetoothReader.enableNotification(true)) {
            Log.e(TAG, "Failed to enable notifications");
        } else {
            Log.d(TAG, "Notifications enable initiated");
        }
    }

    /**
     * Start scanning for BLE devices
     */
    @ReactMethod
    public void startScanningForDevices(Promise promise) {
        if (bluetoothAdapter == null) {
            promise.reject("BLUETOOTH_ERROR", "Bluetooth adapter not initialized");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            promise.reject("BLUETOOTH_DISABLED", "Bluetooth is not enabled");
            return;
        }

        if (isScanning) {
            promise.resolve(null);
            return;
        }

        discoveredDevices.clear();
        isScanning = true;
        sendScanningStatusEvent(true);

        try {
            bluetoothAdapter.startLeScan(leScanCallback);
            promise.resolve(null);
        } catch (Exception e) {
            isScanning = false;
            sendScanningStatusEvent(false);
            promise.reject("SCAN_ERROR", "Failed to start scanning: " + e.getMessage());
        }
    }

    /**
     * Stop scanning for BLE devices
     */
    @ReactMethod
    public void stopScanningForDevices(Promise promise) {
        if (!isScanning) {
            promise.resolve(null);
            return;
        }

        try {
            bluetoothAdapter.stopLeScan(leScanCallback);
            isScanning = false;
            sendScanningStatusEvent(false);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("SCAN_ERROR", "Failed to stop scanning: " + e.getMessage());
        }
    }

    /**
     * Connect to ACS Bluetooth card reader
     */
    @ReactMethod
    public void connect(String address, Promise promise) {
        if (bluetoothAdapter == null) {
            promise.reject("BLUETOOTH_ERROR", "Bluetooth adapter not available");
            return;
        }

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                promise.reject("DEVICE_ERROR", "Device not found");
                return;
            }

            // Stop scanning before connecting
            if (isScanning) {
                bluetoothAdapter.stopLeScan(leScanCallback);
                isScanning = false;
                sendScanningStatusEvent(false);
            }

            // Reset reader detection flag for new connection
            isReaderDetected = false;

            // CRITICAL FIX: Create custom callback that overrides onServicesDiscovered
            readerGattCallback = new BluetoothReaderGattCallback() {
                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);

                    // CRITICAL: Only detect reader once to prevent infinite loop
                    if (isReaderDetected) {
                        Log.d(TAG, "Services discovered but reader already detected, skipping");
                        return;
                    }

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Services discovered - NOW detecting reader");
                        isReaderDetected = true;  // Set flag BEFORE calling detectReader
                        // NOW it's safe to detect the reader
                        boolean detected = bluetoothReaderManager.detectReader(gatt, readerGattCallback);
                        Log.d(TAG, "Detect reader returned: " + detected);
                    } else {
                        Log.e(TAG, "Service discovery failed with status: " + status);
                    }
                }
            };

            // Set connection state change listener
            readerGattCallback.setOnConnectionStateChangeListener(new BluetoothReaderGattCallback.OnConnectionStateChangeListener() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "GATT error: " + status);
                        isConnected = false;
                        sendConnectionStatusEvent(false);
                        return;
                    }

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "Connected to GATT server");
                        isConnected = true;
                        sendConnectionStatusEvent(true);

                        // CRITICAL FIX: Only initiate service discovery, don't detect reader yet
                        // Reader detection MUST happen AFTER services are discovered
                        Log.d(TAG, "Initiating service discovery...");
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "Disconnected from GATT server");
                        isConnected = false;
                        isReaderDetected = false;  // Reset flag for next connection
                        sendConnectionStatusEvent(false);
                        bluetoothReader = null;
                        if (gatt != null) {
                            gatt.close();
                        }
                    }
                }
            });

            // Connect to GATT (autoConnect = true for better stability)
            bluetoothGatt = device.connectGatt(reactContext, true, readerGattCallback);
            promise.resolve(true);

        } catch (Exception e) {
            promise.reject("CONNECTION_ERROR", "Failed to connect: " + e.getMessage());
        }
    }

    /**
     * Disconnect from reader
     */
    @ReactMethod
    public void disconnect(Promise promise) {
        try {
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
            bluetoothReader = null;
            isConnected = false;
            sendConnectionStatusEvent(false);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("DISCONNECT_ERROR", "Failed to disconnect: " + e.getMessage());
        }
    }

    /**
     * Make reader beep
     */
    @ReactMethod
    public void beep(Promise promise) {
        if (bluetoothReader == null) {
            promise.reject("READER_ERROR", "Reader not connected");
            return;
        }

        try {
            // Long beep command: E0 00 00 28 01 50
            byte[] beepCommand = hexStringToByteArray("E0 00 00 28 01 50");
            bluetoothReader.transmitEscapeCommand(beepCommand);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("BEEP_ERROR", "Failed to beep: " + e.getMessage());
        }
    }

    /**
     * Authenticate with master key
     */
    @ReactMethod
    public void authenticate(Promise promise) {
        if (bluetoothReader == null) {
            promise.reject("READER_ERROR", "Reader not connected");
            return;
        }

        try {
            byte[] authCommand = hexStringToByteArray(MASTER_KEY);
            bluetoothReader.authenticate(authCommand);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("AUTH_ERROR", "Failed to authenticate: " + e.getMessage());
        }
    }

    /**
     * Start auto-polling for cards
     */
    @ReactMethod
    public void startPolling(Promise promise) {
        if (bluetoothReader == null) {
            promise.reject("READER_ERROR", "Reader not connected");
            return;
        }

        try {
            // Auto polling command: E0 00 00 40 01
            byte[] pollCommand = hexStringToByteArray("E0 00 00 40 01");
            bluetoothReader.transmitEscapeCommand(pollCommand);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("POLL_ERROR", "Failed to start polling: " + e.getMessage());
        }
    }

    /**
     * Stop auto-polling
     */
    @ReactMethod
    public void stopPolling(Promise promise) {
        if (bluetoothReader == null) {
            promise.reject("READER_ERROR", "Reader not connected");
            return;
        }

        try {
            // Stop auto polling: E0 00 00 40 00
            byte[] stopCommand = hexStringToByteArray("E0 00 00 40 00");
            bluetoothReader.transmitEscapeCommand(stopCommand);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("POLL_ERROR", "Failed to stop polling: " + e.getMessage());
        }
    }

    /**
     * Disable sleep mode
     */
    @ReactMethod
    public void disableSleep(Promise promise) {
        if (bluetoothReader == null) {
            promise.reject("READER_ERROR", "Reader not connected");
            return;
        }

        try {
            byte[] sleepCommand = hexStringToByteArray("E0 00 00 48 00");
            bluetoothReader.transmitEscapeCommand(sleepCommand);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("SLEEP_ERROR", "Failed to disable sleep: " + e.getMessage());
        }
    }

    /**
     * Request card UID
     */
    @ReactMethod
    public void requestUid(Promise promise) {
        if (bluetoothReader == null) {
            promise.reject("READER_ERROR", "Reader not connected");
            return;
        }

        try {
            // Get UID APDU: FF CA 00 00 00
            byte[] uidCommand = hexStringToByteArray("FF CA 00 00 00");
            bluetoothReader.transmitApdu(uidCommand);
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("UID_ERROR", "Failed to request UID: " + e.getMessage());
        }
    }

    // BLE Scan Callback
    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (device != null && device.getName() != null && !device.getName().isEmpty()) {
                if (!discoveredDevices.containsKey(device.getAddress())) {
                    discoveredDevices.put(device.getAddress(), device);
                    sendDevicesDiscoveredEvent();
                }
            }
        }
    };

    // Event Emitters
    private void sendEvent(String eventName, @Nullable Object data) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, data);
    }

    private void sendScanningStatusEvent(boolean isScanning) {
        sendEvent("onScanningStatusChange", isScanning);
    }

    private void sendConnectionStatusEvent(boolean isConnected) {
        sendEvent("onConnectionStatusChange", isConnected);
    }

    private void sendCardDetectedEvent(String uid) {
        sendEvent("onCardDetected", uid);
    }

    private void sendDevicesDiscoveredEvent() {
        WritableArray devices = Arguments.createArray();
        for (BluetoothDevice device : discoveredDevices.values()) {
            WritableMap deviceMap = Arguments.createMap();
            deviceMap.putString("name", device.getName());
            deviceMap.putString("address", device.getAddress());
            devices.pushMap(deviceMap);
        }
        sendEvent("onDevicesDiscovered", devices);
    }

    // Utility Methods
    private static byte[] hexStringToByteArray(String hexString) {
        String[] hexBytes = hexString.split(" ");
        byte[] bytes = new byte[hexBytes.length];
        for (int i = 0; i < hexBytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hexBytes[i], 16);
        }
        return bytes;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
