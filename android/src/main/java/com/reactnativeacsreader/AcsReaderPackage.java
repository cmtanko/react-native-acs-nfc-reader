package com.reactnativeacsreader;

import androidx.annotation.NonNull;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import com.reactnativeacsreader.bluetooth.RNACSBluetoothModule;
import com.reactnativeacsreader.usb.AcsUsbModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified ACS Reader Package
 * Registers both Bluetooth and USB modules
 */
public class AcsReaderPackage implements ReactPackage {

    @NonNull
    @Override
    public List<NativeModule> createNativeModules(@NonNull ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();

        // Register Bluetooth module
        modules.add(new RNACSBluetoothModule(reactContext));

        // Register USB module
        modules.add(new AcsUsbModule(reactContext));

        return modules;
    }

    @NonNull
    @Override
    public List<ViewManager> createViewManagers(@NonNull ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }
}
