#import "RNACSBluetoothModule.h"
#import <CoreBluetooth/CoreBluetooth.h>
#import <React/RCTLog.h>

// ACS Reader BLE Service UUIDs (same as Android)
#define ACS_SERVICE_UUID @"0000fff0-0000-1000-8000-00805f9b34fb"
#define ACS_COMMAND_CHARACTERISTIC_UUID @"0000fff1-0000-1000-8000-00805f9b34fb"
#define ACS_RESPONSE_CHARACTERISTIC_UUID @"0000fff2-0000-1000-8000-00805f9b34fb"

// Master key for ACS reader authentication (same as Android)
#define MASTER_KEY @"41 43 52 31 32 35 35 55 2D 4A 31 20 41 75 74 68"

// Special card status codes
#define CARD_ABSENT @"1111"
#define CARD_FAULTY @"2222"

@interface RNACSBluetoothModule () <CBCentralManagerDelegate, CBPeripheralDelegate>

@property (nonatomic, strong) CBCentralManager *centralManager;
@property (nonatomic, strong) CBPeripheral *connectedPeripheral;
@property (nonatomic, strong) CBCharacteristic *commandCharacteristic;
@property (nonatomic, strong) CBCharacteristic *responseCharacteristic;
@property (nonatomic, assign) BOOL isScanning;
@property (nonatomic, assign) BOOL isConnected;
@property (nonatomic, assign) BOOL isAuthenticated;
@property (nonatomic, assign) BOOL isReaderReady;
@property (nonatomic, strong) NSMutableDictionary<NSString *, CBPeripheral *> *discoveredDevices;
@property (nonatomic, strong) dispatch_queue_t bleQueue;
@property (nonatomic, strong) NSTimer *pollingTimer;
@property (nonatomic, strong) NSString *lastCardUid;
@property (nonatomic, assign) NSTimeInterval lastCardDetectionTime;

@end

@implementation RNACSBluetoothModule

RCT_EXPORT_MODULE(RNACSBluetooth);

- (instancetype)init {
    self = [super init];
    if (self) {
        _discoveredDevices = [NSMutableDictionary dictionary];
        _bleQueue = dispatch_queue_create("com.reactnativeacsbluetooth.ble", DISPATCH_QUEUE_SERIAL);
        _centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:_bleQueue options:@{CBCentralManagerOptionShowPowerAlertKey: @NO}];
        _isScanning = NO;
        _isConnected = NO;
        _isAuthenticated = NO;
        _isReaderReady = NO;
        _lastCardUid = nil;
        _lastCardDetectionTime = 0;
    }
    return self;
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"onScanningStatusChange", @"onDevicesDiscovered", @"onConnectionStatusChange", @"onCardDetected"];
}

#pragma mark - CBCentralManagerDelegate

- (void)centralManagerDidUpdateState:(CBCentralManager *)central {
    if (central.state == CBManagerStatePoweredOn) {
        RCTLogInfo(@"Bluetooth is powered on");
    } else {
        RCTLogWarn(@"Bluetooth state: %ld", (long)central.state);
    }
}

- (void)centralManager:(CBCentralManager *)central
 didDiscoverPeripheral:(CBPeripheral *)peripheral
     advertisementData:(NSDictionary<NSString *,id> *)advertisementData
                  RSSI:(NSNumber *)RSSI {
    if (peripheral.name && peripheral.name.length > 0) {
        NSString *identifier = peripheral.identifier.UUIDString;
        if (![self.discoveredDevices objectForKey:identifier]) {
            [self.discoveredDevices setObject:peripheral forKey:identifier];
            [self sendDevicesDiscoveredEvent];
        }
    }
}

- (void)centralManager:(CBCentralManager *)central
  didConnectPeripheral:(CBPeripheral *)peripheral {
    RCTLogInfo(@"Connected to peripheral: %@", peripheral.name);
    self.isConnected = YES;
    self.isAuthenticated = NO;
    self.isReaderReady = NO;
    self.connectedPeripheral = peripheral;
    peripheral.delegate = self;
    [self sendConnectionStatusEvent:YES];
    
    // Discover services after connection
    [peripheral discoverServices:nil];
}

- (void)centralManager:(CBCentralManager *)central
didDisconnectPeripheral:(CBPeripheral *)peripheral
                  error:(NSError *)error {
    RCTLogInfo(@"Disconnected from peripheral: %@", peripheral.name);
    self.isConnected = NO;
    self.isAuthenticated = NO;
    self.isReaderReady = NO;
    self.connectedPeripheral = nil;
    self.commandCharacteristic = nil;
    self.responseCharacteristic = nil;
    
    // Stop polling timer
    if (self.pollingTimer) {
        [self.pollingTimer invalidate];
        self.pollingTimer = nil;
    }
    
    [self sendConnectionStatusEvent:NO];
    
    if (error) {
        RCTLogError(@"Disconnection error: %@", error.localizedDescription);
    }
}

- (void)centralManager:(CBCentralManager *)central
didFailToConnectPeripheral:(CBPeripheral *)peripheral
                  error:(NSError *)error {
    RCTLogError(@"Failed to connect to peripheral: %@", error.localizedDescription);
    self.isConnected = NO;
    [self sendConnectionStatusEvent:NO];
}

#pragma mark - CBPeripheralDelegate

- (void)peripheral:(CBPeripheral *)peripheral
didDiscoverServices:(NSError *)error {
    if (error) {
        RCTLogError(@"Error discovering services: %@", error.localizedDescription);
        return;
    }
    
    for (CBService *service in peripheral.services) {
        RCTLogInfo(@"Discovered service: %@", service.UUID);
        
        // Look for ACS service
        if ([service.UUID.UUIDString.lowercaseString isEqualToString:ACS_SERVICE_UUID.lowercaseString]) {
            [peripheral discoverCharacteristics:nil forService:service];
        }
    }
}

- (void)peripheral:(CBPeripheral *)peripheral
didDiscoverCharacteristicsForService:(CBService *)service
             error:(NSError *)error {
    if (error) {
        RCTLogError(@"Error discovering characteristics: %@", error.localizedDescription);
        return;
    }
    
    BOOL foundCommand = NO;
    BOOL foundResponse = NO;
    
    for (CBCharacteristic *characteristic in service.characteristics) {
        NSString *charUUID = characteristic.UUID.UUIDString.lowercaseString;
        RCTLogInfo(@"Discovered characteristic: %@ (properties: %lu)", charUUID, (unsigned long)characteristic.properties);
        
        // Command characteristic (write)
        if ([charUUID isEqualToString:ACS_COMMAND_CHARACTERISTIC_UUID.lowercaseString]) {
            self.commandCharacteristic = characteristic;
            foundCommand = YES;
            RCTLogInfo(@"Found command characteristic");
        }
        
        // Response characteristic (notify/read)
        if ([charUUID isEqualToString:ACS_RESPONSE_CHARACTERISTIC_UUID.lowercaseString]) {
            self.responseCharacteristic = characteristic;
            foundResponse = YES;
            RCTLogInfo(@"Found response characteristic");
            
            // Enable notifications (CRITICAL: Must enable before authentication)
            [peripheral setNotifyValue:YES forCharacteristic:characteristic];
        }
    }
    
    // Only proceed with authentication if both characteristics are found
    if (foundCommand && foundResponse && self.commandCharacteristic && self.responseCharacteristic) {
        RCTLogInfo(@"Both characteristics found, waiting for notifications to be enabled...");
        // Authentication will happen after notifications are enabled (in didUpdateNotificationStateForCharacteristic)
    }
}

- (void)peripheral:(CBPeripheral *)peripheral
didUpdateNotificationStateForCharacteristic:(CBCharacteristic *)characteristic
             error:(NSError *)error {
    if (error) {
        RCTLogError(@"Error updating notification state: %@", error.localizedDescription);
        return;
    }
    
    if (characteristic.isNotifying) {
        RCTLogInfo(@"Notifications enabled for characteristic: %@", characteristic.UUID);
        
        // If this is the response characteristic and we haven't authenticated yet, do it now
        NSString *charUUID = characteristic.UUID.UUIDString.lowercaseString;
        if ([charUUID isEqualToString:ACS_RESPONSE_CHARACTERISTIC_UUID.lowercaseString] && !self.isAuthenticated) {
            // Wait a bit for notifications to stabilize, then authenticate
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.3 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [self authenticateReader];
            });
        }
    }
}

- (void)peripheral:(CBPeripheral *)peripheral
didUpdateValueForCharacteristic:(CBCharacteristic *)characteristic
             error:(NSError *)error {
    if (error) {
        RCTLogError(@"Error reading characteristic value: %@", error.localizedDescription);
        return;
    }
    
    if (characteristic.value) {
        NSData *data = characteristic.value;
        NSString *hexString = [self dataToHexString:data];
        RCTLogInfo(@"Received data from characteristic %@: %@ (length: %lu)", characteristic.UUID, hexString, (unsigned long)data.length);
        
        // Check if this is an authentication response or card data
        if (!self.isAuthenticated) {
            // Might be authentication response - check and mark as authenticated
            RCTLogInfo(@"Received data before authentication - might be auth response");
            // After receiving any response, assume authentication succeeded
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.2 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                if (!self.isAuthenticated) {
                    self.isAuthenticated = YES;
                    [self setupReaderAfterAuthentication];
                }
            });
        } else {
            // Parse card UID from response
            [self parseCardData:data];
        }
    }
}

- (void)peripheral:(CBPeripheral *)peripheral
didWriteValueForCharacteristic:(CBCharacteristic *)characteristic
             error:(NSError *)error {
    if (error) {
        RCTLogError(@"Error writing characteristic: %@", error.localizedDescription);
    } else {
        RCTLogInfo(@"Successfully wrote to characteristic: %@", characteristic.UUID);
    }
}

#pragma mark - React Native Methods

RCT_EXPORT_METHOD(startScanningForDevices:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (self.centralManager.state != CBManagerStatePoweredOn) {
        reject(@"BLUETOOTH_ERROR", @"Bluetooth is not enabled", nil);
        return;
    }
    
    if (self.isScanning) {
        resolve(nil);
        return;
    }
    
    [self.discoveredDevices removeAllObjects];
    self.isScanning = YES;
    [self sendScanningStatusEvent:YES];
    
    // Scan for peripherals with ACS service UUID
    NSArray<CBUUID *> *serviceUUIDs = @[[CBUUID UUIDWithString:ACS_SERVICE_UUID]];
    [self.centralManager scanForPeripheralsWithServices:serviceUUIDs options:@{CBCentralManagerScanOptionAllowDuplicatesKey: @NO}];
    
    resolve(nil);
}

RCT_EXPORT_METHOD(stopScanningForDevices:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (!self.isScanning) {
        resolve(nil);
        return;
    }
    
    [self.centralManager stopScan];
    self.isScanning = NO;
    [self sendScanningStatusEvent:NO];
    resolve(nil);
}

RCT_EXPORT_METHOD(connect:(NSString *)address
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (self.centralManager.state != CBManagerStatePoweredOn) {
        reject(@"BLUETOOTH_ERROR", @"Bluetooth adapter not available", nil);
        return;
    }
    
    // Stop scanning before connecting
    if (self.isScanning) {
        [self.centralManager stopScan];
        self.isScanning = NO;
        [self sendScanningStatusEvent:NO];
    }
    
    // Find peripheral by identifier
    CBPeripheral *peripheral = nil;
    for (CBPeripheral *p in self.discoveredDevices.allValues) {
        if ([p.identifier.UUIDString isEqualToString:address] || 
            [p.name isEqualToString:address]) {
            peripheral = p;
            break;
        }
    }
    
    // If not found in discovered devices, try to find by UUID
    if (!peripheral) {
        NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:address];
        if (uuid) {
            NSArray<CBPeripheral *> *peripherals = [self.centralManager retrievePeripheralsWithIdentifiers:@[uuid]];
            if (peripherals.count > 0) {
                peripheral = peripherals[0];
            }
        }
    }
    
    if (!peripheral) {
        reject(@"DEVICE_ERROR", @"Device not found", nil);
        return;
    }
    
    // Connect to peripheral
    [self.centralManager connectPeripheral:peripheral options:nil];
    resolve(@YES);
}

RCT_EXPORT_METHOD(disconnect:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    // Stop polling timer
    if (self.pollingTimer) {
        [self.pollingTimer invalidate];
        self.pollingTimer = nil;
    }
    
    if (self.connectedPeripheral) {
        [self.centralManager cancelPeripheralConnection:self.connectedPeripheral];
    }
    self.isConnected = NO;
    self.isAuthenticated = NO;
    self.isReaderReady = NO;
    self.connectedPeripheral = nil;
    self.commandCharacteristic = nil;
    self.responseCharacteristic = nil;
    self.lastCardUid = nil;
    [self sendConnectionStatusEvent:NO];
    resolve(nil);
}

RCT_EXPORT_METHOD(beep:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (!self.connectedPeripheral || !self.commandCharacteristic) {
        reject(@"READER_ERROR", @"Reader not connected", nil);
        return;
    }
    
    // Long beep command: E0 00 00 28 01 50
    NSData *beepCommand = [self hexStringToData:@"E0 00 00 28 01 50"];
    CBCharacteristicWriteType writeType = (self.commandCharacteristic.properties & CBCharacteristicWriteWithResponse) 
        ? CBCharacteristicWriteWithResponse 
        : CBCharacteristicWriteWithoutResponse;
    
    [self.connectedPeripheral writeValue:beepCommand 
                        forCharacteristic:self.commandCharacteristic 
                                     type:writeType];
    resolve(nil);
}

RCT_EXPORT_METHOD(authenticate:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (!self.connectedPeripheral || !self.commandCharacteristic) {
        reject(@"READER_ERROR", @"Reader not connected", nil);
        return;
    }
    
    [self authenticateReader];
    resolve(nil);
}

RCT_EXPORT_METHOD(startPolling:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (!self.connectedPeripheral || !self.commandCharacteristic) {
        reject(@"READER_ERROR", @"Reader not connected", nil);
        return;
    }
    
    // Auto polling command: E0 00 00 40 01
    NSData *pollCommand = [self hexStringToData:@"E0 00 00 40 01"];
    CBCharacteristicWriteType writeType = (self.commandCharacteristic.properties & CBCharacteristicWriteWithResponse) 
        ? CBCharacteristicWriteWithResponse 
        : CBCharacteristicWriteWithoutResponse;
    
    [self.connectedPeripheral writeValue:pollCommand 
                        forCharacteristic:self.commandCharacteristic 
                                     type:writeType];
    resolve(nil);
}

RCT_EXPORT_METHOD(stopPolling:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (!self.connectedPeripheral || !self.commandCharacteristic) {
        reject(@"READER_ERROR", @"Reader not connected", nil);
        return;
    }
    
    // Stop auto polling: E0 00 00 40 00
    NSData *stopCommand = [self hexStringToData:@"E0 00 00 40 00"];
    CBCharacteristicWriteType writeType = (self.commandCharacteristic.properties & CBCharacteristicWriteWithResponse) 
        ? CBCharacteristicWriteWithResponse 
        : CBCharacteristicWriteWithoutResponse;
    
    [self.connectedPeripheral writeValue:stopCommand 
                        forCharacteristic:self.commandCharacteristic 
                                     type:writeType];
    resolve(nil);
}

RCT_EXPORT_METHOD(disableSleep:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (!self.connectedPeripheral || !self.commandCharacteristic) {
        reject(@"READER_ERROR", @"Reader not connected", nil);
        return;
    }
    
    // Disable sleep: E0 00 00 48 00
    NSData *sleepCommand = [self hexStringToData:@"E0 00 00 48 00"];
    CBCharacteristicWriteType writeType = (self.commandCharacteristic.properties & CBCharacteristicWriteWithResponse) 
        ? CBCharacteristicWriteWithResponse 
        : CBCharacteristicWriteWithoutResponse;
    
    [self.connectedPeripheral writeValue:sleepCommand 
                        forCharacteristic:self.commandCharacteristic 
                                     type:writeType];
    resolve(nil);
}

RCT_EXPORT_METHOD(requestUid:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (!self.connectedPeripheral || !self.commandCharacteristic) {
        reject(@"READER_ERROR", @"Reader not connected", nil);
        return;
    }
    
    // Get UID APDU: FF CA 00 00 00
    NSData *uidCommand = [self hexStringToData:@"FF CA 00 00 00"];
    CBCharacteristicWriteType writeType = (self.commandCharacteristic.properties & CBCharacteristicWriteWithResponse) 
        ? CBCharacteristicWriteWithResponse 
        : CBCharacteristicWriteWithoutResponse;
    
    [self.connectedPeripheral writeValue:uidCommand 
                        forCharacteristic:self.commandCharacteristic 
                                     type:writeType];
    resolve(nil);
}

#pragma mark - Helper Methods

- (void)authenticateReader {
    if (!self.connectedPeripheral || !self.commandCharacteristic) {
        RCTLogError(@"Cannot authenticate - peripheral or command characteristic not available");
        return;
    }
    
    if (self.isAuthenticated) {
        RCTLogInfo(@"Already authenticated, skipping");
        return;
    }
    
    RCTLogInfo(@"Authenticating reader with master key...");
    
    // Send authentication command (master key)
    NSData *authCommand = [self hexStringToData:MASTER_KEY];
    CBCharacteristicWriteType writeType = (self.commandCharacteristic.properties & CBCharacteristicWriteWithResponse) 
        ? CBCharacteristicWriteWithResponse 
        : CBCharacteristicWriteWithoutResponse;
    
    [self.connectedPeripheral writeValue:authCommand 
                        forCharacteristic:self.commandCharacteristic 
                                     type:writeType];
    
    RCTLogInfo(@"Authentication command sent, waiting for response...");
    
    // Set a timeout to assume authentication succeeded if no explicit response
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.8 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        if (!self.isAuthenticated) {
            RCTLogInfo(@"No explicit auth response, assuming success and proceeding...");
            self.isAuthenticated = YES;
            [self setupReaderAfterAuthentication];
        }
    });
}

- (void)setupReaderAfterAuthentication {
    if (!self.isAuthenticated || self.isReaderReady) {
        return;
    }
    
    RCTLogInfo(@"Setting up reader after authentication...");
    
    // Start auto polling
    NSData *pollCommand = [self hexStringToData:@"E0 00 00 40 01"];
    CBCharacteristicWriteType writeType = (self.commandCharacteristic.properties & CBCharacteristicWriteWithResponse) 
        ? CBCharacteristicWriteWithResponse 
        : CBCharacteristicWriteWithoutResponse;
    [self.connectedPeripheral writeValue:pollCommand 
                        forCharacteristic:self.commandCharacteristic 
                                     type:writeType];
    RCTLogInfo(@"Auto polling started");
    
    // Disable sleep
    NSData *sleepCommand = [self hexStringToData:@"E0 00 00 48 00"];
    [self.connectedPeripheral writeValue:sleepCommand 
                        forCharacteristic:self.commandCharacteristic 
                                     type:writeType];
    RCTLogInfo(@"Sleep mode disabled");
    
    // Long beep to confirm reader is ready
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.3 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        NSData *beepCommand = [self hexStringToData:@"E0 00 00 28 01 50"];
        [self.connectedPeripheral writeValue:beepCommand 
                            forCharacteristic:self.commandCharacteristic 
                                         type:writeType];
        RCTLogInfo(@"Reader ready beep sent");
    });
    
    self.isReaderReady = YES;
    
    // Start periodic polling for card status (similar to Android's card status listener)
    [self startCardStatusPolling];
}

- (void)startCardStatusPolling {
    // Poll for card status every 500ms (similar to Android's auto-polling)
    if (self.pollingTimer) {
        [self.pollingTimer invalidate];
    }
    
    self.pollingTimer = [NSTimer scheduledTimerWithTimeInterval:0.5
                                                          target:self
                                                        selector:@selector(pollCardStatus)
                                                        userInfo:nil
                                                         repeats:YES];
}

- (void)pollCardStatus {
    if (!self.isConnected || !self.isReaderReady || !self.commandCharacteristic) {
        return;
    }
    
    // Request card UID (this will trigger card detection if present)
    // Get UID APDU: FF CA 00 00 00
    NSData *uidCommand = [self hexStringToData:@"FF CA 00 00 00"];
    CBCharacteristicWriteType writeType = (self.commandCharacteristic.properties & CBCharacteristicWriteWithResponse) 
        ? CBCharacteristicWriteWithResponse 
        : CBCharacteristicWriteWithoutResponse;
    
    [self.connectedPeripheral writeValue:uidCommand 
                        forCharacteristic:self.commandCharacteristic 
                                     type:writeType];
}

- (void)parseCardData:(NSData *)data {
    if (!data || data.length == 0) {
        return;
    }
    
    const uint8_t *bytes = (const uint8_t *)data.bytes;
    NSUInteger length = data.length;
    
    NSString *hexString = [self dataToHexString:data];
    RCTLogInfo(@"Parsing card data: %@ (length: %lu)", hexString, (unsigned long)length);
    
    // Extract UID from response - try multiple parsing methods (matching Android implementation)
    NSString *uid = nil;
    
    // Method 1: Check for PC/SC response format [0xD5, 0x4B, Status, UID...]
    if (length >= 4 && bytes[0] == 0xD5 && bytes[1] == 0x4B) {
        uint8_t status = bytes[2];
        RCTLogInfo(@"PC/SC format detected, status: 0x%02X", status);
        if (status == 0x00 && length >= 7) {
            uint8_t uidLength = bytes[3];
            if (uidLength > 0 && length >= 4 + uidLength) {
                NSMutableString *hexStr = [NSMutableString string];
                for (NSUInteger i = 4; i < 4 + uidLength && i < length; i++) {
                    [hexStr appendFormat:@"%02X", bytes[i]];
                }
                uid = [hexStr copy];
                RCTLogInfo(@"Extracted UID from PC/SC format: %@", uid);
            }
        }
    }
    
    // Method 2: Extract from bytes 5-9 (common ACS format)
    if (!uid && length >= 9) {
        NSMutableString *hexStr = [NSMutableString string];
        for (NSUInteger i = 5; i < MIN(9, length); i++) {
            [hexStr appendFormat:@"%02X", bytes[i]];
        }
        if (hexStr.length >= 4) {
            uid = [hexStr copy];
            RCTLogInfo(@"Extracted UID from bytes 5-9: %@", uid);
        }
    }
    
    // Method 3: Extract first 4 bytes (most common fallback)
    if (!uid && length >= 4) {
        NSMutableString *hexStr = [NSMutableString string];
        for (NSUInteger i = 0; i < MIN(4, length); i++) {
            [hexStr appendFormat:@"%02X", bytes[i]];
        }
        uid = [hexStr copy];
        RCTLogInfo(@"Extracted UID from first 4 bytes: %@", uid);
    }
    
    // Method 4: Extract from last 4 bytes (some readers put UID at the end)
    if (!uid && length >= 4) {
        NSMutableString *hexStr = [NSMutableString string];
        for (NSUInteger i = length - 4; i < length; i++) {
            [hexStr appendFormat:@"%02X", bytes[i]];
        }
        uid = [hexStr copy];
        RCTLogInfo(@"Extracted UID from last 4 bytes: %@", uid);
    }
    
    // Validate and send UID
    if (uid && uid.length >= 4) {
        // Extract first 8 characters (4 bytes) to match Android behavior
        if (uid.length > 8) {
            uid = [uid substringToIndex:8];
        }
        
        // Prevent duplicate reads (debouncing)
        NSTimeInterval currentTime = [[NSDate date] timeIntervalSince1970];
        if ([uid isEqualToString:self.lastCardUid] && 
            (currentTime - self.lastCardDetectionTime) < 2.0) {
            RCTLogInfo(@"Ignoring duplicate card read (too soon)");
            return;
        }
        
        // Check for special status codes
        if ([uid isEqualToString:@"00000000"] || [uid isEqualToString:@"FFFFFFFF"]) {
            RCTLogInfo(@"Card absent or invalid UID: %@", uid);
            [self sendCardDetectedEvent:CARD_ABSENT];
        } else {
            self.lastCardUid = uid;
            self.lastCardDetectionTime = currentTime;
            RCTLogInfo(@"Card UID detected: %@", uid);
            [self sendCardDetectedEvent:uid];
        }
    } else {
        RCTLogWarn(@"Could not extract valid UID from response");
    }
}

- (NSData *)hexStringToData:(NSString *)hexString {
    hexString = [hexString stringByReplacingOccurrencesOfString:@" " withString:@""];
    NSMutableData *data = [NSMutableData data];
    for (NSUInteger i = 0; i < hexString.length; i += 2) {
        NSString *byteString = [hexString substringWithRange:NSMakeRange(i, 2)];
        unsigned char byte = (unsigned char)strtoul([byteString UTF8String], NULL, 16);
        [data appendBytes:&byte length:1];
    }
    return data;
}

- (NSString *)dataToHexString:(NSData *)data {
    const uint8_t *bytes = (const uint8_t *)data.bytes;
    NSMutableString *hexString = [NSMutableString string];
    for (NSUInteger i = 0; i < data.length; i++) {
        [hexString appendFormat:@"%02X", bytes[i]];
    }
    return hexString;
}

#pragma mark - Event Emitters

- (void)sendScanningStatusEvent:(BOOL)isScanning {
    [self sendEventWithName:@"onScanningStatusChange" body:@(isScanning)];
}

- (void)sendConnectionStatusEvent:(BOOL)isConnected {
    [self sendEventWithName:@"onConnectionStatusChange" body:@(isConnected)];
}

- (void)sendCardDetectedEvent:(NSString *)uid {
    [self sendEventWithName:@"onCardDetected" body:uid];
}

- (void)sendDevicesDiscoveredEvent {
    NSMutableArray *devices = [NSMutableArray array];
    for (CBPeripheral *peripheral in self.discoveredDevices.allValues) {
        [devices addObject:@{
            @"name": peripheral.name ?: @"Unknown",
            @"address": peripheral.identifier.UUIDString
        }];
    }
    [self sendEventWithName:@"onDevicesDiscovered" body:devices];
}

@end

