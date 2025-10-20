#import <React/RCTBridgeModule.h>
#import <Foundation/Foundation.h>
#import <GCDWebServer/GCDWebServer.h>
#import <GCDWebServer/GCDWebServerDataResponse.h>

@interface CryptoModule : NSObject <RCTBridgeModule, NSURLSessionDataDelegate>

@property (nonatomic, strong) GCDWebServer *webServer;
@property (nonatomic, strong) NSMutableDictionary *activeStreams;

@end