#import "AcsUsb.h"

@implementation AcsUsb

RCT_EXPORT_MODULE()

- (NSArray<NSString *> *)supportedEvents
{
    return @[
        @"onLog",
        @"onCardUID",
        @"onEnterReaderOpenChange",
        @"onEnterReaderConnectChange",
        @"onExitReaderOpenChange",
        @"onExitReaderConnectChange",
        @"onIsTappingChange",
        @"onTappingAlert"
    ];
}

RCT_EXPORT_METHOD(setupCardReader:(NSString *)enterReaderID
                  exitReaderID:(NSString *)exitReaderID)
{
    // iOS implementation not available
    // ACS USB readers are primarily Android-focused
    NSLog(@"ACS USB: iOS not supported");
}

RCT_EXPORT_METHOD(connectDevice:(NSString *)productID
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    reject(@"NOT_SUPPORTED", @"ACS USB is not supported on iOS", nil);
}

RCT_EXPORT_METHOD(getProductIDs:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    resolve(@[]);
}

RCT_EXPORT_METHOD(openDevice:(NSString *)productID)
{
    NSLog(@"ACS USB: iOS not supported");
}

RCT_EXPORT_METHOD(closeDevice:(NSString *)productID)
{
    NSLog(@"ACS USB: iOS not supported");
}

RCT_EXPORT_METHOD(updateLight:(nonnull NSNumber *)productID
                  commandString:(NSString *)commandString)
{
    NSLog(@"ACS USB: iOS not supported");
}

RCT_EXPORT_METHOD(updateIsTapping:(BOOL)status)
{
    NSLog(@"ACS USB: iOS not supported");
}

+ (BOOL)requiresMainQueueSetup
{
    return NO;
}

@end
