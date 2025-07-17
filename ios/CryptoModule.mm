#import "CryptoModule.h"
#import <React/RCTBridgeModule.h>
#import <CommonCrypto/CommonCryptor.h>

@implementation CryptoModule

// ✅ This is crucial for module registration
RCT_EXPORT_MODULE();

// ✅ Make sure this is set to run on background queue
- (dispatch_queue_t)methodQueue
{
  return dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0);
}

// ✅ Add this to explicitly state no main queue setup needed
+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

// Helper method to convert file URI to local path
- (NSString *)convertFileUriToPath:(NSString *)fileUri {
  if ([fileUri hasPrefix:@"file://"]) {
    NSString *path = [fileUri substringFromIndex:7];
    path = [path stringByRemovingPercentEncoding];
    NSLog(@"Converted URI '%@' to path '%@'", fileUri, path);
    return path;
  }
  NSLog(@"URI doesn't start with file://, using as-is: %@", fileUri);
  return fileUri;
}

RCT_REMAP_METHOD(decryptFile,
                 inputUri:(NSString *)inputUri
                 outputUri:(NSString *)outputUri
                 keyBase64:(NSString *)keyBase64
                 ivBase64:(NSString *)ivBase64
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
  NSLog(@"=== NATIVE MODULE DEBUG ===");
  NSLog(@"inputUri: %@", inputUri);
  NSLog(@"outputUri: %@", outputUri);
  NSLog(@"keyBase64 length: %lu", (unsigned long)keyBase64.length);
  NSLog(@"ivBase64 length: %lu", (unsigned long)ivBase64.length);
  
  // Convert file URIs to local paths
  NSString *inputPath = [self convertFileUriToPath:inputUri];
  NSString *outputPath = [self convertFileUriToPath:outputUri];
  
  NSLog(@"Converted inputPath: %@", inputPath);
  NSLog(@"Converted outputPath: %@", outputPath);
  
  // Validate inputs
  if (!inputPath || inputPath.length == 0) {
    NSLog(@"❌ Invalid inputPath");
    reject(@"DECRYPT_FAILED", @"Invalid input path", nil);
    return;
  }
  
  if (!outputPath || outputPath.length == 0) {
    NSLog(@"❌ Invalid outputPath");
    reject(@"DECRYPT_FAILED", @"Invalid output path", nil);
    return;
  }
  
  if (!keyBase64 || keyBase64.length == 0) {
    NSLog(@"❌ Invalid keyBase64");
    reject(@"DECRYPT_FAILED", @"Invalid key", nil);
    return;
  }
  
  if (!ivBase64 || ivBase64.length == 0) {
    NSLog(@"❌ Invalid ivBase64");
    reject(@"DECRYPT_FAILED", @"Invalid IV", nil);
    return;
  }
  
  // Check if input file exists
  NSFileManager *fileManager = [NSFileManager defaultManager];
  if (![fileManager fileExistsAtPath:inputPath]) {
    NSLog(@"❌ Input file does not exist at path: %@", inputPath);
    reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Input file does not exist: %@", inputPath], nil);
    return;
  }
  
  // Create output directory if needed
  NSString *outputDir = [outputPath stringByDeletingLastPathComponent];
  NSError *dirError = nil;
  if (![fileManager createDirectoryAtPath:outputDir withIntermediateDirectories:YES attributes:nil error:&dirError]) {
    if (dirError) {
      NSLog(@"❌ Failed to create output directory: %@", dirError.localizedDescription);
      reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Failed to create output directory: %@", dirError.localizedDescription], nil);
      return;
    }
  }
  
  // Convert base64 to data
  NSData *keyData = [[NSData alloc] initWithBase64EncodedString:keyBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  NSData *ivData = [[NSData alloc] initWithBase64EncodedString:ivBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  
  NSLog(@"keyData length: %lu", (unsigned long)keyData.length);
  NSLog(@"ivData length: %lu", (unsigned long)ivData.length);
  
  if (!keyData || keyData.length != 32) {
    NSLog(@"❌ Invalid key data - length: %lu, expected: 32", (unsigned long)keyData.length);
    reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Invalid key data length: %lu", (unsigned long)keyData.length], nil);
    return;
  }
  
  if (!ivData || ivData.length != 16) {
    NSLog(@"❌ Invalid IV data - length: %lu, expected: 16", (unsigned long)ivData.length);
    reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Invalid IV data length: %lu", (unsigned long)ivData.length], nil);
    return;
  }
  
  // Read input file
  NSError *fileError = nil;
  NSData *inputData = [NSData dataWithContentsOfFile:inputPath options:NSDataReadingMappedIfSafe error:&fileError];
  
  if (fileError) {
    NSLog(@"❌ File read error: %@", fileError.localizedDescription);
    reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Failed to read input file: %@", fileError.localizedDescription], nil);
    return;
  }
  
  if (!inputData || inputData.length == 0) {
    NSLog(@"❌ Input file is empty");
    reject(@"DECRYPT_FAILED", @"Input file is empty", nil);
    return;
  }
  
  NSLog(@"✅ Input file read successfully, size: %lu bytes", (unsigned long)inputData.length);
  
  // Perform AES-256-CBC decryption
  size_t outLength;
  NSMutableData *decryptedData = [NSMutableData dataWithLength:inputData.length + kCCBlockSizeAES128];
  
  NSLog(@"Starting decryption...");
  NSLog(@"Input data length: %lu", (unsigned long)inputData.length);
  NSLog(@"Buffer length: %lu", (unsigned long)decryptedData.length);
  
  CCCryptorStatus result = CCCrypt(
    kCCDecrypt,                    // operation
    kCCAlgorithmAES,              // algorithm
    kCCOptionPKCS7Padding,        // options
    keyData.bytes, keyData.length, // key
    ivData.bytes,                 // iv
    inputData.bytes, inputData.length, // input
    decryptedData.mutableBytes, decryptedData.length, // output
    &outLength                    // output length
  );
  
  NSLog(@"Decryption result: %d", result);
  NSLog(@"Output length: %zu", outLength);
  
  if (result == kCCSuccess) {
    decryptedData.length = outLength;
    NSLog(@"✅ Decryption successful, output size: %lu bytes", (unsigned long)decryptedData.length);
    
    NSError *writeError = nil;
    BOOL success = [decryptedData writeToFile:outputPath options:NSDataWritingAtomic error:&writeError];
    
    if (writeError) {
      NSLog(@"❌ Write error: %@", writeError.localizedDescription);
      reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Failed to write output file: %@", writeError.localizedDescription], nil);
    } else if (success) {
      NSLog(@"✅ File written successfully to: %@", outputPath);
      
      // Verify the file was written
      if ([fileManager fileExistsAtPath:outputPath]) {
        NSDictionary *attrs = [fileManager attributesOfItemAtPath:outputPath error:nil];
        NSLog(@"✅ Output file verified, size: %@", attrs[NSFileSize]);
        resolve(outputUri); // Return the original URI format
      } else {
        NSLog(@"❌ Output file verification failed");
        reject(@"DECRYPT_FAILED", @"Output file verification failed", nil);
      }
    } else {
      NSLog(@"❌ Failed to write output file - unknown error");
      reject(@"DECRYPT_FAILED", @"Failed to write output file", nil);
    }
  } else {
    NSLog(@"❌ Decryption failed with status: %d", result);
    
    // Provide more specific error messages
    NSString *errorMessage;
    switch (result) {
      case kCCParamError:
        errorMessage = @"Parameter error - check key/IV format";
        break;
      case kCCBufferTooSmall:
        errorMessage = @"Buffer too small";
        break;
      case kCCMemoryFailure:
        errorMessage = @"Memory allocation failure";
        break;
      case kCCAlignmentError:
        errorMessage = @"Input size not aligned to block size";
        break;
      case kCCDecodeError:
        errorMessage = @"Invalid input data or wrong key/IV";
        break;
      case kCCUnimplemented:
        errorMessage = @"Function not implemented";
        break;
      default:
        errorMessage = [NSString stringWithFormat:@"Decryption failed with code: %d", result];
        break;
    }
    
    NSLog(@"Detailed error: %@", errorMessage);
    reject(@"DECRYPT_FAILED", errorMessage, nil);
  }
}

@end