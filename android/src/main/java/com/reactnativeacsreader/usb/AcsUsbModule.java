package com.reactnativeacsreader.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.annotation.NonNull;

import com.acs.smartcard.Features;
import com.acs.smartcard.Reader;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class AcsUsbModule extends ReactContextBaseJavaModule {

    private static final String MODULE_NAME = "AcsUsb";
    private static final int ACS_VENDOR_ID = 1839;
    private static final String ACTION_USB_PERMISSION = "com.reactnativeacsreader.USB_PERMISSION";
    private static final String[] STATE_STRING = {"Unknown", "Absent", "Present", "Swallowed", "Powered", "Negotiable", "Specific"};

    // APDU Commands
    private static final String CMD_AUTO_POLLING_START = "E0 00 00 40 01";
    private static final String CMD_AUTO_POLLING_STOP = "E0 00 00 40 00";
    private static final String CMD_APDU_COMMAND_UID = "FF CA 00 00 00";
    private static final String CMD_APDU_COMMAND_ATS = "FF CA 01 00 00";
    private static final String CMD_SLEEP_COMMAND_DISABLE = "E0 00 00 48 04";

    private final ReactApplicationContext reactContext;
    private UsbManager usbManager;
    private Reader enterReader;
    private Reader exitReader;
    private UsbDevice enterUsbDevice;
    private UsbDevice exitUsbDevice;
    private PendingIntent permissionIntent;
    private IntentFilter intentFilter;

    private int enterReaderProductID = -1;
    private int exitReaderProductID = -1;
    private int deviceVendorId = -1;
    private int currentDeviceProductID = -1;

    private boolean isEnterReaderOpened = false;
    private boolean isExitReaderOpened = false;
    private boolean isEnterReaderConnected = false;
    private boolean isExitReaderConnected = false;
    private boolean isTapping = false;

    public AcsUsbModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        initializeUSB();
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    private void initializeUSB() {
        try {
            usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
            enterReader = new Reader(usbManager);
            exitReader = new Reader(usbManager);

            // Set up state change listeners
            Reader.OnStateChangeListener stateChangeCallback = new Reader.OnStateChangeListener() {
                @Override
                public void onStateChange(int slotNum, int prevState, int currState) {
                    handleStateChange(slotNum, prevState, currState);
                }
            };

            enterReader.setOnStateChangeListener(stateChangeCallback);
            exitReader.setOnStateChangeListener(stateChangeCallback);

            // Set up pending intent for USB permission
            int flags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ?
                PendingIntent.FLAG_MUTABLE : 0;
            permissionIntent = PendingIntent.getBroadcast(reactContext, 0,
                new Intent(ACTION_USB_PERMISSION), flags);

            // Register broadcast receiver
            registerReceiver();
        } catch (Exception e) {
            sendEvent("onLog", "Error initializing USB: " + e.getMessage());
        }
    }

    private void registerReceiver() {
        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_USB_PERMISSION);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        reactContext.registerReceiver(usbReceiver, intentFilter);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case ACTION_USB_PERMISSION:
                    synchronized (this) {
                        if (deviceVendorId == ACS_VENDOR_ID) {
                            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                            if (granted) {
                                openCard();
                            } else {
                                sendEvent("onLog", "USB permission denied");
                            }
                        }
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    attachDevice();
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    detachDevice();
                    break;
            }
        }
    };

    private void handleStateChange(int slotNum, int prevState, int currState) {
        if (prevState < Reader.CARD_UNKNOWN || prevState > Reader.CARD_SPECIFIC) {
            prevState = Reader.CARD_UNKNOWN;
        }
        if (currState < Reader.CARD_UNKNOWN || currState > Reader.CARD_SPECIFIC) {
            currState = Reader.CARD_UNKNOWN;
        }

        if (currState == 2 && prevState == 1) {
            if (!isTapping) {
                sendEvent("onLog", "Slot " + slotNum + ": " + STATE_STRING[prevState] + " -> " + STATE_STRING[currState]);

                if (enterReader.isOpened() && enterReader.getState(slotNum) == 2) {
                    isTapping = true;
                    sendEvent("onLog", "Card Present at Enter");
                    sendEvent("onIsTappingChange", true);
                    requestUid(slotNum, enterReaderProductID);
                } else if (exitReader.isOpened() && exitReader.getState(slotNum) == 2) {
                    isTapping = true;
                    sendEvent("onLog", "Card Present at Exit");
                    sendEvent("onIsTappingChange", true);
                    requestUid(slotNum, exitReaderProductID);
                } else {
                    sendEvent("onLog", "Reader close or tap on unknown reader");
                }
            } else {
                sendEvent("onTappingAlert", true);
            }
        }
    }

    private void requestUid(int slotNum, int productID) {
        byte[] command = hex2Bytes(CMD_APDU_COMMAND_UID);
        Reader reader = null;

        try {
            if (enterReaderProductID == productID) {
                reader = enterReader;
            } else if (exitReaderProductID == productID) {
                reader = exitReader;
            }

            if (reader != null && reader.isOpened()) {
                byte[] response = new byte[300];
                reader.control(slotNum, 3500, command, command.length, response, response.length);

                String uid = toHexString(response).replace(" ", "");
                sendEvent("onLog", "UID is " + uid);

                WritableMap responseData = Arguments.createMap();
                responseData.putString("uid", uid);
                responseData.putString("productID", String.valueOf(productID));

                sendEvent("onCardUID", responseData);

                // Reset tapping state
                isTapping = false;
                sendEvent("onIsTappingChange", false);
            } else {
                sendEvent("onLog", "Card Reader is not opened");
            }
        } catch (Exception e) {
            isTapping = false;
            sendEvent("onIsTappingChange", false);
            sendEvent("onLog", "Unable to get card UID, error: " + e.getMessage());
        }
    }

    @ReactMethod
    public void setupCardReader(String enterReaderID, String exitReaderID) {
        try {
            if (enterReaderID != null && !enterReaderID.isEmpty()) {
                enterReaderProductID = Integer.parseInt(enterReaderID);
            }
            if (exitReaderID != null && !exitReaderID.isEmpty()) {
                exitReaderProductID = Integer.parseInt(exitReaderID);
            }
            sendEvent("onLog", "Card readers setup - Enter: " + enterReaderProductID + ", Exit: " + exitReaderProductID);
        } catch (Exception e) {
            sendEvent("onLog", "Error setting up card readers: " + e.getMessage());
        }
    }

    @ReactMethod
    public void connectDevice(String productID, Promise promise) {
        try {
            if (usbManager == null) {
                promise.reject("ERROR", "Unable to find device");
                return;
            }

            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            String deviceName = "";

            while (deviceIterator.hasNext()) {
                UsbDevice device = deviceIterator.next();
                if (device.getVendorId() == ACS_VENDOR_ID &&
                    device.getProductId() == Integer.parseInt(productID)) {

                    if (enterReaderProductID == Integer.parseInt(productID)) {
                        enterUsbDevice = device;
                        isEnterReaderConnected = true;
                        sendEvent("onEnterReaderConnectChange", true);
                    } else if (exitReaderProductID == Integer.parseInt(productID)) {
                        exitUsbDevice = device;
                        isExitReaderConnected = true;
                        sendEvent("onExitReaderConnectChange", true);
                    }

                    deviceName = device.getDeviceName();
                    deviceVendorId = device.getVendorId();
                    sendEvent("onLog", "Device Name: " + deviceName);
                    break;
                }
            }

            promise.resolve(deviceName);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getProductIDs(Promise promise) {
        try {
            WritableArray productIDs = Arguments.createArray();

            if (usbManager != null) {
                HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

                while (deviceIterator.hasNext()) {
                    UsbDevice device = deviceIterator.next();
                    if (device.getVendorId() == ACS_VENDOR_ID) {
                        productIDs.pushInt(device.getProductId());
                    }
                }
            }

            promise.resolve(productIDs);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void openDevice(String productID) {
        try {
            if (productID == null || productID.isEmpty()) {
                sendEvent("onLog", "No Device Product ID found");
                return;
            }

            UsbDevice device = null;
            int pID = Integer.parseInt(productID);

            if (enterReaderProductID == pID) {
                device = enterUsbDevice;
            } else if (exitReaderProductID == pID) {
                device = exitUsbDevice;
            }

            if (device != null) {
                sendEvent("onLog", "Start to request permission for product id " + productID);
                currentDeviceProductID = pID;

                if (usbManager.hasPermission(device)) {
                    openCard();
                } else {
                    usbManager.requestPermission(device, permissionIntent);
                }
            } else {
                sendEvent("onLog", "No register device found with Product ID: " + productID);
            }
        } catch (Exception e) {
            sendEvent("onLog", "Error opening device: " + e.getMessage());
        }
    }

    @ReactMethod
    public void closeDevice(String productID) {
        try {
            int pID = Integer.parseInt(productID);

            if (enterReaderProductID == pID) {
                if (enterReader.isOpened()) {
                    enterReader.close();
                    isEnterReaderOpened = false;
                    sendEvent("onEnterReaderOpenChange", false);
                }
            } else if (exitReaderProductID == pID) {
                if (exitReader.isOpened()) {
                    exitReader.close();
                    isExitReaderOpened = false;
                    sendEvent("onExitReaderOpenChange", false);
                }
            }
        } catch (Exception e) {
            sendEvent("onLog", "Error closing device: " + e.getMessage());
        }
    }

    @ReactMethod
    public void updateLight(int productID, String commandString) {
        int slotNum = 0;
        byte[] command = hex2Bytes(commandString);
        Reader reader = null;

        try {
            if (enterReaderProductID == productID) {
                reader = enterReader;
            } else if (exitReaderProductID == productID) {
                reader = exitReader;
            }

            if (reader != null && reader.isOpened()) {
                byte[] response = new byte[300];
                reader.control(slotNum, 3500, command, command.length, response, response.length);
                String result = toHexString(response).replace(" ", "");
                sendEvent("onLog", "Light update response: " + result);
            }
        } catch (Exception e) {
            sendEvent("onLog", "Unable to update light, error: " + e.getMessage());
        }
    }

    @ReactMethod
    public void updateIsTapping(boolean status) {
        isTapping = status;
        sendEvent("onIsTappingChange", status);
    }

    private void openCard() {
        if (currentDeviceProductID == -1) {
            sendEvent("onLog", "Unable to open card, no product ID found");
            return;
        }

        UsbDevice device = null;
        Reader reader = null;

        if (enterReaderProductID == currentDeviceProductID) {
            device = enterUsbDevice;
            reader = enterReader;
        } else if (exitReaderProductID == currentDeviceProductID) {
            device = exitUsbDevice;
            reader = exitReader;
        }

        if (device != null && reader != null) {
            try {
                reader.open(device);
                if (reader.isOpened()) {
                    updateReaderOpenStatus(true, currentDeviceProductID);
                    sendEvent("onLog", "Reader open for product id " + currentDeviceProductID);
                }
            } catch (Exception e) {
                sendEvent("onLog", "Unable to open card, error: " + e.getMessage());
            }
            currentDeviceProductID = -1;
        }
    }

    private void attachDevice() {
        try {
            if (usbManager != null) {
                HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

                while (deviceIterator.hasNext()) {
                    UsbDevice device = deviceIterator.next();

                    if (device.getProductId() == enterReaderProductID) {
                        if (!isEnterReaderConnected) {
                            sendEvent("onLog", "Attached Enter USB device");
                            isEnterReaderConnected = true;
                            sendEvent("onEnterReaderConnectChange", true);

                            String deviceName = connectDeviceSync(String.valueOf(enterReaderProductID));
                            if (!deviceName.isEmpty()) {
                                openDevice(String.valueOf(enterReaderProductID));
                            }
                        }
                    } else if (device.getProductId() == exitReaderProductID) {
                        if (!isExitReaderConnected) {
                            sendEvent("onLog", "Attached Exit USB device");
                            isExitReaderConnected = true;
                            sendEvent("onExitReaderConnectChange", true);

                            String deviceName = connectDeviceSync(String.valueOf(exitReaderProductID));
                            if (!deviceName.isEmpty()) {
                                openDevice(String.valueOf(exitReaderProductID));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            sendEvent("onLog", "Error handling attached device: " + e.getMessage());
        }
    }

    private void detachDevice() {
        try {
            if (usbManager != null) {
                HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                ArrayList<Integer> productIDs = new ArrayList<>();

                Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
                while (deviceIterator.hasNext()) {
                    UsbDevice device = deviceIterator.next();
                    if (device.getProductId() == enterReaderProductID ||
                        device.getProductId() == exitReaderProductID) {
                        if (!productIDs.contains(device.getProductId())) {
                            productIDs.add(device.getProductId());
                        }
                    }
                }

                if (!productIDs.contains(enterReaderProductID) && isEnterReaderConnected) {
                    sendEvent("onLog", "Enter Card Reader detached");
                    isEnterReaderConnected = false;
                    isEnterReaderOpened = false;
                    sendEvent("onEnterReaderConnectChange", false);
                    sendEvent("onEnterReaderOpenChange", false);
                    enterReader.close();
                }

                if (!productIDs.contains(exitReaderProductID) && isExitReaderConnected) {
                    sendEvent("onLog", "Exit Card Reader detached");
                    isExitReaderConnected = false;
                    isExitReaderOpened = false;
                    sendEvent("onExitReaderConnectChange", false);
                    sendEvent("onExitReaderOpenChange", false);
                    exitReader.close();
                }
            }
        } catch (Exception e) {
            sendEvent("onLog", "Error handling detached device: " + e.getMessage());
        }
    }

    private String connectDeviceSync(String productID) {
        if (usbManager == null) {
            return "";
        }

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        String deviceName = "";

        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (device.getVendorId() == ACS_VENDOR_ID &&
                device.getProductId() == Integer.parseInt(productID)) {

                if (enterReaderProductID == Integer.parseInt(productID)) {
                    enterUsbDevice = device;
                } else if (exitReaderProductID == Integer.parseInt(productID)) {
                    exitUsbDevice = device;
                }

                deviceName = device.getDeviceName();
                deviceVendorId = device.getVendorId();
                break;
            }
        }
        return deviceName;
    }

    private void updateReaderOpenStatus(boolean status, int productID) {
        if (enterReaderProductID == productID) {
            isEnterReaderOpened = status;
            sendEvent("onEnterReaderOpenChange", status);
            if (!isEnterReaderConnected) {
                isEnterReaderConnected = true;
                sendEvent("onEnterReaderConnectChange", true);
            }
        } else if (exitReaderProductID == productID) {
            isExitReaderOpened = status;
            sendEvent("onExitReaderOpenChange", status);
            if (!isExitReaderConnected) {
                isExitReaderConnected = true;
                sendEvent("onExitReaderConnectChange", true);
            }
        }
    }

    private byte[] hex2Bytes(String hexStr) {
        hexStr = hexStr.replace(" ", "");
        ArrayList<Byte> result = new ArrayList<>();

        if (hexStr.length() % 2 == 0) {
            for (int i = 0; i < hexStr.length(); i += 2) {
                String hex = hexStr.substring(i, i + 2).toUpperCase();
                int num = Integer.parseInt(hex, 16);
                result.add((byte) num);
            }
        }

        byte[] byteArray = new byte[result.size()];
        for (int i = 0; i < result.size(); i++) {
            byteArray[i] = result.get(i);
        }
        return byteArray;
    }

    private String toHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }

        StringBuilder hexStr = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, 4); i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if (hex.length() == 1) {
                hexStr.append('0');
            }
            hexStr.append(hex);
        }

        return hexStr.toString().toUpperCase();
    }

    private void sendEvent(String eventName, Object params) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        try {
            if (enterReader != null && enterReader.isOpened()) {
                enterReader.close();
            }
            if (exitReader != null && exitReader.isOpened()) {
                exitReader.close();
            }
            reactContext.unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
