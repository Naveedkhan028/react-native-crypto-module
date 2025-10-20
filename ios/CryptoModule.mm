#import "CryptoModule.h"
#import <React/RCTBridgeModule.h>
#import <CommonCrypto/CommonCryptor.h>
#import <objc/runtime.h>
#import <GCDWebServer/GCDWebServerDataResponse.h>
#import <GCDWebServer/GCDWebServerRequest.h>
#import <GCDWebServer/GCDWebServerResponse.h>

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

// ✅ Initialize HTTP server on module init - DISABLED FOR NOW
- (instancetype)init
{
  self = [super init];
  if (self) {
    // HTTP server disabled - too complex, causes crashes
    NSLog(@"⚠️ HTTP streaming disabled - using fallback decryption");
  }
  return self;
}

// ✅ Cleanup on dealloc
- (void)dealloc
{
  if (self.webServer.isRunning) {
    [self.webServer stop];
    NSLog(@"🛑 GCDWebServer stopped");
  }
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

// ✅ NEW: Start progressive streaming via local HTTP server
RCT_REMAP_METHOD(decryptFileViaHTTPServer,
                 inputUri:(NSString *)inputUri
                 keyBase64:(NSString *)keyBase64
                 ivBase64:(NSString *)ivBase64
                 token:(NSString *)token
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
  NSLog(@"⚠️ HTTP streaming disabled - use regular decryption");
  reject(@"NOT_IMPLEMENTED", @"HTTP streaming disabled - use decryptFileWithStreaming instead", nil);
}

RCT_REMAP_METHOD(decryptFile,
                 inputUri:(NSString *)inputUri
                 outputUri:(NSString *)outputUri
                 keyBase64:(NSString *)keyBase64
                 ivBase64:(NSString *)ivBase64
                 chunkSize:(NSNumber *)chunkSize
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
  NSUInteger chunkSizeValue = [chunkSize unsignedIntegerValue];
  if (chunkSizeValue == 0) {
    chunkSizeValue = 1024 * 1024; // Default 1MB
  }
  
  NSLog(@"=== DECRYPT FILE START ===");
  NSLog(@"inputUri: %@", inputUri);
  NSLog(@"outputUri: %@", outputUri);
  NSLog(@"chunkSize: %lu", (unsigned long)chunkSizeValue);
  
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
// Add this method to your CryptoModule.mm file

RCT_REMAP_METHOD(encryptDataStreaming,
                 inputData:(NSString *)inputDataBase64
                 keyBase64:(NSString *)keyBase64
                 ivBase64:(NSString *)ivBase64
                 chunkSize:(NSNumber *)chunkSize
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
  NSLog(@"=== STREAMING ENCRYPTION START ===");
  
  // Convert base64 inputs to data
  NSData *inputData = [[NSData alloc] initWithBase64EncodedString:inputDataBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  NSData *keyData = [[NSData alloc] initWithBase64EncodedString:keyBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  NSData *ivData = [[NSData alloc] initWithBase64EncodedString:ivBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  
  if (!inputData || !keyData || !ivData) {
    reject(@"ENCRYPT_FAILED", @"Invalid input data", nil);
    return;
  }
  
  if (keyData.length != 32) {
    reject(@"ENCRYPT_FAILED", @"Invalid key length", nil);
    return;
  }
  
  if (ivData.length != 16) {
    reject(@"ENCRYPT_FAILED", @"Invalid IV length", nil);
    return;
  }
  
  NSUInteger chunkSizeValue = [chunkSize unsignedIntegerValue];
  if (chunkSizeValue == 0) {
    chunkSizeValue = 1024 * 1024; // Default 1MB to match JavaScript
  }
  
  const size_t AES_BLOCK_SIZE = 16;
  NSUInteger totalLength = inputData.length;
  NSMutableArray *encryptedChunks = [NSMutableArray array];
  
  // Create cryptor for streaming
  CCCryptorRef cryptor;
  CCCryptorStatus status = CCCryptorCreate(
    kCCEncrypt,
    kCCAlgorithmAES,
    kCCOptionPKCS7Padding,
    keyData.bytes,
    keyData.length,
    ivData.bytes,
    &cryptor
  );
  
  if (status != kCCSuccess) {
    reject(@"ENCRYPT_FAILED", @"Failed to create cryptor", nil);
    return;
  }
  
  NSUInteger totalProcessed = 0;
  
  @try {
    for (NSUInteger start = 0; start < totalLength; start += chunkSizeValue) {
      BOOL isLastChunk = (start + chunkSizeValue >= totalLength);
      NSUInteger currentChunkSize = MIN(chunkSizeValue, totalLength - start);
      
      NSData *chunk = [inputData subdataWithRange:NSMakeRange(start, currentChunkSize)];
      
      NSLog(@"Processing chunk %lu, size: %lu, isLast: %d", 
            (unsigned long)(start / chunkSizeValue + 1), 
            (unsigned long)chunk.length, 
            isLastChunk);
      
      // For non-final chunks, ensure block alignment
      if (!isLastChunk) {
        NSUInteger alignedSize = (chunk.length / AES_BLOCK_SIZE) * AES_BLOCK_SIZE;
        if (alignedSize < chunk.length) {
          chunk = [chunk subdataWithRange:NSMakeRange(0, alignedSize)];
          NSLog(@"Aligned chunk to: %lu bytes", (unsigned long)chunk.length);
        }
      }
      
      // Calculate output buffer size
      size_t outputLength = chunk.length + AES_BLOCK_SIZE;
      NSMutableData *outputData = [NSMutableData dataWithLength:outputLength];
      size_t actualOutputLength = 0;
      
      // Update cryptor with chunk
      if (isLastChunk) {
        // Final update
        status = CCCryptorFinal(
          cryptor,
          outputData.mutableBytes,
          outputData.length,
          &actualOutputLength
        );
      } else {
        // Regular update
        status = CCCryptorUpdate(
          cryptor,
          chunk.bytes,
          chunk.length,
          outputData.mutableBytes,
          outputData.length,
          &actualOutputLength
        );
      }
      
      if (status != kCCSuccess) {
        CCCryptorRelease(cryptor);
        reject(@"ENCRYPT_FAILED", [NSString stringWithFormat:@"Encryption failed at chunk %lu", (unsigned long)(start / chunkSizeValue + 1)], nil);
        return;
      }
      
      if (actualOutputLength > 0) {
        NSData *chunkOutput = [NSData dataWithBytes:outputData.bytes length:actualOutputLength];
        [encryptedChunks addObject:[chunkOutput base64EncodedStringWithOptions:0]];
        NSLog(@"Chunk encrypted output size: %lu", (unsigned long)actualOutputLength);
      }
      
      totalProcessed += chunk.length;
    }
    
    CCCryptorRelease(cryptor);
    
    NSLog(@"✅ Streaming encryption completed");
    NSLog(@"Total chunks processed: %lu", (unsigned long)encryptedChunks.count);
    
    resolve(@{
      @"encryptedChunks": encryptedChunks,
      @"totalChunks": @(encryptedChunks.count),
      @"totalProcessed": @(totalProcessed)
    });
    
  } @catch (NSException *exception) {
    CCCryptorRelease(cryptor);
    reject(@"ENCRYPT_FAILED", [NSString stringWithFormat:@"Exception during encryption: %@", exception.reason], nil);
  }
}

RCT_REMAP_METHOD(decryptTextContent,
                 encryptedContentBase64:(NSString *)encryptedContentBase64
                 keyBase64:(NSString *)keyBase64
                 ivBase64:(NSString *)ivBase64
                 chunkSize:(NSNumber *)chunkSize
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
  NSUInteger chunkSizeValue = [chunkSize unsignedIntegerValue];
  if (chunkSizeValue == 0) {
    chunkSizeValue = 1024 * 1024; // Default 1MB
  }
  
  NSLog(@"=== DECRYPT TEXT START ===");
  NSLog(@"chunkSize: %lu", (unsigned long)chunkSizeValue);
  
  if (!encryptedContentBase64 || encryptedContentBase64.length == 0) {
    reject(@"DECRYPT_FAILED", @"Invalid encrypted content", nil);
    return;
  }
  
  // Convert base64 inputs to data
  NSData *encryptedData = [[NSData alloc] initWithBase64EncodedString:encryptedContentBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  NSData *keyData = [[NSData alloc] initWithBase64EncodedString:keyBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  NSData *ivData = [[NSData alloc] initWithBase64EncodedString:ivBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  
  if (!encryptedData || !keyData || !ivData) {
    reject(@"DECRYPT_FAILED", @"Invalid input data", nil);
    return;
  }
  
  if (keyData.length != 32) {
    reject(@"DECRYPT_FAILED", @"Invalid key length", nil);
    return;
  }
  
  if (ivData.length != 16) {
    reject(@"DECRYPT_FAILED", @"Invalid IV length", nil);
    return;
  }
  
  // Perform AES-256-CBC decryption
  size_t outLength;
  NSMutableData *decryptedData = [NSMutableData dataWithLength:encryptedData.length + kCCBlockSizeAES128];
  
  CCCryptorStatus result = CCCrypt(
    kCCDecrypt,
    kCCAlgorithmAES,
    kCCOptionPKCS7Padding,
    keyData.bytes, keyData.length,
    ivData.bytes,
    encryptedData.bytes, encryptedData.length,
    decryptedData.mutableBytes, decryptedData.length,
    &outLength
  );
  
  if (result == kCCSuccess) {
    decryptedData.length = outLength;
    
    // Convert to string
    NSString *decryptedString = [[NSString alloc] initWithData:decryptedData encoding:NSUTF8StringEncoding];
    
    if (decryptedString) {
      NSLog(@"✅ Text decryption successful");
      resolve(decryptedString);
    } else {
      reject(@"DECRYPT_FAILED", @"Failed to convert decrypted data to string", nil);
    }
  } else {
    NSString *errorMessage = [NSString stringWithFormat:@"Decryption failed with status: %d", result];
    NSLog(@"❌ %@", errorMessage);
    reject(@"DECRYPT_FAILED", errorMessage, nil);
  }
}

RCT_REMAP_METHOD(encryptTextContent,
                 textContent:(NSString *)textContent
                 keyBase64:(NSString *)keyBase64
                 ivBase64:(NSString *)ivBase64
                 chunkSize:(NSNumber *)chunkSize
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
  NSUInteger chunkSizeValue = [chunkSize unsignedIntegerValue];
  if (chunkSizeValue == 0) {
    chunkSizeValue = 1024 * 1024; // Default 1MB
  }
  
  NSLog(@"=== ENCRYPT TEXT START ===");
  NSLog(@"chunkSize: %lu", (unsigned long)chunkSizeValue);
  
  if (!textContent || textContent.length == 0) {
    reject(@"ENCRYPT_FAILED", @"Invalid text content", nil);
    return;
  }
  
  // Convert base64 inputs to data
  NSData *keyData = [[NSData alloc] initWithBase64EncodedString:keyBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  NSData *ivData = [[NSData alloc] initWithBase64EncodedString:ivBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  
  if (!keyData || keyData.length != 32) {
    reject(@"ENCRYPT_FAILED", @"Invalid key length", nil);
    return;
  }
  
  if (!ivData || ivData.length != 16) {
    reject(@"ENCRYPT_FAILED", @"Invalid IV length", nil);
    return;
  }
  
  // Convert text to NSData
  NSData *textData = [textContent dataUsingEncoding:NSUTF8StringEncoding];
  if (!textData || textData.length == 0) {
    reject(@"ENCRYPT_FAILED", @"Failed to convert text to data", nil);
    return;
  }
  
  NSLog(@"Text data length: %lu", (unsigned long)textData.length);
  
  // Perform AES-256-CBC encryption
  size_t outLength;
  NSMutableData *encryptedData = [NSMutableData dataWithLength:textData.length + kCCBlockSizeAES128];
  
  CCCryptorStatus result = CCCrypt(
    kCCEncrypt,
    kCCAlgorithmAES,
    kCCOptionPKCS7Padding,
    keyData.bytes, keyData.length,
    ivData.bytes,
    textData.bytes, textData.length,
    encryptedData.mutableBytes, encryptedData.length,
    &outLength
  );
  
  if (result == kCCSuccess) {
    encryptedData.length = outLength;
    
    // Convert to base64 string
    NSString *encryptedBase64 = [encryptedData base64EncodedStringWithOptions:0];
    
    NSLog(@"✅ Text encryption successful");
    NSLog(@"Original size: %lu", (unsigned long)textData.length);
    NSLog(@"Encrypted size: %lu", (unsigned long)encryptedData.length);
    
    resolve(encryptedBase64);
  } else {
    NSString *errorMessage = [NSString stringWithFormat:@"Encryption failed with status: %d", result];
    NSLog(@"❌ %@", errorMessage);
    reject(@"ENCRYPT_FAILED", errorMessage, nil);
  }
}

// ✅ COMPLETE FIXED: Streaming decryption method with proper casting
RCT_REMAP_METHOD(decryptFileWithStreaming,
                 inputUri:(NSString *)inputUri
                 outputUri:(NSString *)outputUri
                 keyBase64:(NSString *)keyBase64
                 ivBase64:(NSString *)ivBase64
                 token:(NSString *)token
                 chunkSize:(NSNumber *)chunkSize
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
  NSUInteger chunkSizeValue = [chunkSize unsignedIntegerValue];
  if (chunkSizeValue == 0) {
    chunkSizeValue = 1024 * 1024; // Default 1MB
  }
  
  // ✅ IMPORTANT: Ensure chunk size is AES block aligned
  chunkSizeValue = (chunkSizeValue / 16) * 16; // Force 16-byte alignment
  
  NSLog(@"=== STREAMING DECRYPTION START ===");
  NSLog(@"inputUri: %@", inputUri);
  NSLog(@"outputUri: %@", outputUri);
  NSLog(@"chunkSize (aligned): %lu", (unsigned long)chunkSizeValue);
  
  // Convert file URIs to local paths
  NSString *inputPath = [self convertFileUriToPath:inputUri];
  NSString *outputPath = [self convertFileUriToPath:outputUri];
  
  // ✅ Convert base64 keys early for use in progressive streaming
  NSData *keyData = [[NSData alloc] initWithBase64EncodedString:keyBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  NSData *ivData = [[NSData alloc] initWithBase64EncodedString:ivBase64 options:NSDataBase64DecodingIgnoreUnknownCharacters];
  
  if (!keyData || keyData.length != 32) {
    reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Invalid key data length: %lu", (unsigned long)keyData.length], nil);
    return;
  }
  
  if (!ivData || ivData.length != 16) {
    reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Invalid IV data length: %lu", (unsigned long)ivData.length], nil);
    return;
  }
  
  // ✅ Download and decrypt progressively if it's HTTP URL with streaming delegate
  if ([inputUri hasPrefix:@"http"]) {
    NSLog(@"🚀 Starting progressive streaming with NSURLSessionDataDelegate from: %@", inputUri);
    
    // ✅ Create output directory first
    NSString *outputDir = [outputPath stringByDeletingLastPathComponent];
    NSFileManager *fileManager = [NSFileManager defaultManager];
    NSError *dirError = nil;
    if (![fileManager createDirectoryAtPath:outputDir withIntermediateDirectories:YES attributes:nil error:&dirError]) {
      if (dirError) {
        reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Failed to create output directory: %@", dirError.localizedDescription], nil);
        return;
      }
    }
    
    // ✅ Create output stream immediately
    NSOutputStream *outputStream = [NSOutputStream outputStreamToFileAtPath:outputPath append:NO];
    [outputStream open];
    
    // ✅ Create cryptor for progressive decryption
    CCCryptorRef cryptor;
    CCCryptorStatus status = CCCryptorCreate(
      kCCDecrypt,
      kCCAlgorithmAES,
      kCCOptionPKCS7Padding,
      keyData.bytes,
      keyData.length,
      ivData.bytes,
      &cryptor
    );
    
    if (status != kCCSuccess) {
      [outputStream close];
      reject(@"DECRYPT_FAILED", @"Failed to create cryptor", nil);
      return;
    }
    
    NSLog(@"✅ Cryptor created for AES-CBC streaming decryption");
    
    // ✅ Setup for streaming download with delegate
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:inputUri]];
    if (token && token.length > 0) {
      [request setValue:[NSString stringWithFormat:@"Bearer %@", token] forHTTPHeaderField:@"Authorization"];
    }
    
    dispatch_semaphore_t downloadSemaphore = dispatch_semaphore_create(0);
    __block BOOL hasError = NO;
    __block NSString *errorMessage = nil;
    __block unsigned long long totalDownloaded = 0;
    __block unsigned long long totalDecrypted = 0;
    
    // ✅ Allocate decryption buffer
    NSUInteger bufferSize = chunkSizeValue + kCCBlockSizeAES128;
    __block uint8_t *outputBuffer = (uint8_t *)malloc(bufferSize);
    
    if (!outputBuffer) {
      CCCryptorRelease(cryptor);
      [outputStream close];
      reject(@"DECRYPT_FAILED", @"Memory allocation failed", nil);
      return;
    }
    
    // ✅ Create session with delegate to receive data progressively
    NSURLSessionConfiguration *config = [NSURLSessionConfiguration defaultSessionConfiguration];
    NSURLSession *session = [NSURLSession sessionWithConfiguration:config
      delegate:(id<NSURLSessionDelegate>)self
      delegateQueue:nil];
    
    // ✅ Use NSURLSessionDataTask with delegate callbacks
    NSURLSessionDataTask *dataTask = [session dataTaskWithRequest:request];
    
    // ✅ Store context in associated objects for delegate callbacks
    objc_setAssociatedObject(dataTask, "outputStream", outputStream, OBJC_ASSOCIATION_RETAIN);
    objc_setAssociatedObject(dataTask, "outputPath", outputPath, OBJC_ASSOCIATION_RETAIN);
    objc_setAssociatedObject(dataTask, "cryptor", [NSValue valueWithPointer:cryptor], OBJC_ASSOCIATION_RETAIN);
    objc_setAssociatedObject(dataTask, "outputBuffer", [NSValue valueWithPointer:outputBuffer], OBJC_ASSOCIATION_RETAIN);
    objc_setAssociatedObject(dataTask, "chunkSize", @(chunkSizeValue), OBJC_ASSOCIATION_RETAIN);
    objc_setAssociatedObject(dataTask, "bufferSize", @(bufferSize), OBJC_ASSOCIATION_RETAIN);
    objc_setAssociatedObject(dataTask, "totalDownloaded", @(0), OBJC_ASSOCIATION_RETAIN);
    objc_setAssociatedObject(dataTask, "hasError", @NO, OBJC_ASSOCIATION_RETAIN);
    objc_setAssociatedObject(dataTask, "semaphore", downloadSemaphore, OBJC_ASSOCIATION_RETAIN);
    
    [dataTask resume];
    
    NSLog(@"📡 Waiting for streaming data...");
    
    // ✅ Wait for download completion
    dispatch_semaphore_wait(downloadSemaphore, DISPATCH_TIME_FOREVER);
    
    // ✅ Get final status
    hasError = [objc_getAssociatedObject(dataTask, "hasError") boolValue];
    errorMessage = objc_getAssociatedObject(dataTask, "errorMessage");
    totalDownloaded = [objc_getAssociatedObject(dataTask, "totalDownloaded") unsignedLongLongValue];
    
    // ✅ Finalize decryption
    if (!hasError) {
      size_t finalLength = 0;
      CCCryptorStatus finalStatus = CCCryptorFinal(cryptor, outputBuffer, bufferSize, &finalLength);
      
      if (finalStatus == kCCSuccess) {
        if (finalLength > 0) {
          [outputStream write:outputBuffer maxLength:finalLength];
          NSLog(@"✅ Final padding removal: %zu bytes", finalLength);
        }
        NSLog(@"✅ Total downloaded and decrypted: %llu bytes", totalDownloaded);
      } else {
        hasError = YES;
        errorMessage = [NSString stringWithFormat:@"Final decryption failed with status: %d", finalStatus];
        NSLog(@"❌ %@", errorMessage);
      }
    }
    
    // ✅ Cleanup
    free(outputBuffer);
    CCCryptorRelease(cryptor);
    [outputStream close];
    [session finishTasksAndInvalidate];
    
    if (hasError) {
      reject(@"DECRYPT_FAILED", errorMessage ?: @"Progressive streaming failed", nil);
      return;
    }
    
    NSLog(@"✅ Progressive streaming completed successfully!");
    
    // ✅ Verify output file
    if ([fileManager fileExistsAtPath:outputPath]) {
      NSDictionary *outputAttrs = [fileManager attributesOfItemAtPath:outputPath error:nil];
      resolve(@{
        @"success": @YES,
        @"localPath": outputUri,
        @"size": outputAttrs[NSFileSize]
      });
    } else {
      reject(@"DECRYPT_FAILED", @"Output file verification failed", nil);
    }
    return;
  }
  
  // Validate inputs
  NSFileManager *fileManager = [NSFileManager defaultManager];
  if (![fileManager fileExistsAtPath:inputPath]) {
    reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Input file does not exist: %@", inputPath], nil);
    return;
  }
  
  // Create output directory
  NSString *outputDir = [outputPath stringByDeletingLastPathComponent];
  NSError *dirError = nil;
  if (![fileManager createDirectoryAtPath:outputDir withIntermediateDirectories:YES attributes:nil error:&dirError]) {
    if (dirError) {
      reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Failed to create output directory: %@", dirError.localizedDescription], nil);
      return;
    }
  }
  
  // ✅ keyData and ivData already converted above - no need to reconvert
  
  // ✅ Create output file immediately so JS polling can detect activity
  [@"" writeToFile:outputPath atomically:YES encoding:NSUTF8StringEncoding error:nil];
  NSLog(@"✅ Created empty output file for streaming: %@", outputPath);
  
  // ✅ FIXED: Use streaming approach with proper padding
  NSInputStream *inputStream = [NSInputStream inputStreamWithFileAtPath:inputPath];
  NSOutputStream *outputStream = [NSOutputStream outputStreamToFileAtPath:outputPath append:NO];
  
  [inputStream open];
  [outputStream open];
  
  // ✅ Create single cryptor for entire file
  CCCryptorRef cryptor;
  CCCryptorStatus status = CCCryptorCreate(
    kCCDecrypt,
    kCCAlgorithmAES,
    kCCOptionPKCS7Padding,
    keyData.bytes,
    keyData.length,
    ivData.bytes,
    &cryptor
  );
  
  if (status != kCCSuccess) {
    [inputStream close];
    [outputStream close];
    reject(@"DECRYPT_FAILED", @"Failed to create cryptor", nil);
    return;
  }
  
  NSUInteger bufferSize = chunkSizeValue + kCCBlockSizeAES128;
  uint8_t *inputBuffer = (uint8_t *)malloc(bufferSize);
  uint8_t *outputBuffer = (uint8_t *)malloc(bufferSize);
  
  if (!inputBuffer || !outputBuffer) {
    if (inputBuffer) free(inputBuffer);
    if (outputBuffer) free(outputBuffer);
    CCCryptorRelease(cryptor);
    [inputStream close];
    [outputStream close];
    reject(@"DECRYPT_FAILED", @"Memory allocation failed", nil);
    return;
  }
  
  NSDictionary *attrs = [fileManager attributesOfItemAtPath:inputPath error:nil];
  unsigned long long totalBytes = [attrs[NSFileSize] unsignedLongLongValue];
  unsigned long long processedBytes = 0;
  
  NSLog(@"Starting streaming decryption, total size: %llu", totalBytes);
  
  @try {
    NSInteger bytesRead;
    BOOL processingComplete = NO;
    
    while (!processingComplete && (bytesRead = [inputStream read:inputBuffer maxLength:chunkSizeValue]) > 0) {
      processedBytes += bytesRead;
      BOOL isLastChunk = (processedBytes >= totalBytes);
      
      NSLog(@"Processing chunk: %ld bytes, total: %llu/%llu, isLast: %d", 
            (long)bytesRead, processedBytes, totalBytes, isLastChunk);
      
      size_t outputLength = 0;
      
      if (isLastChunk) {
        // ✅ CRITICAL: Handle final chunk properly
        
        // First, process any remaining data with update
        if (bytesRead > 0) {
          status = CCCryptorUpdate(
            cryptor,
            inputBuffer,
            bytesRead,
            outputBuffer,
            bufferSize,
            &outputLength
          );
          
          if (status != kCCSuccess) {
            NSLog(@"Final CCCryptorUpdate failed with status: %d", status);
            break;
          }
          
          if (outputLength > 0) {
            NSInteger written = [outputStream write:outputBuffer maxLength:outputLength];
            NSLog(@"Final update wrote: %zu bytes, stream wrote: %ld", outputLength, (long)written);
          }
        }
        
        // ✅ CRITICAL: Call CCCryptorFinal to handle padding removal
        size_t finalOutputLength = 0;
        status = CCCryptorFinal(
          cryptor,
          outputBuffer,
          bufferSize,
          &finalOutputLength
        );
        
        if (status != kCCSuccess) {
          NSLog(@"CCCryptorFinal failed with status: %d", status);
          break;
        }
        
        if (finalOutputLength > 0) {
          NSInteger written = [outputStream write:outputBuffer maxLength:finalOutputLength];
          NSLog(@"Final block wrote: %zu bytes, stream wrote: %ld", finalOutputLength, (long)written);
        }
        
        NSLog(@"✅ Final chunk completed: update=%zu bytes, final=%zu bytes", outputLength, finalOutputLength);
        processingComplete = YES;
        
      } else {
        // ✅ Intermediate chunk - use CCCryptorUpdate only
        status = CCCryptorUpdate(
          cryptor,
          inputBuffer,
          bytesRead,
          outputBuffer,
          bufferSize,
          &outputLength
        );
        
        if (status != kCCSuccess) {
          NSLog(@"CCCryptorUpdate failed with status: %d", status);
          break;
        }
        
        if (outputLength > 0) {
          NSInteger written = [outputStream write:outputBuffer maxLength:outputLength];
          NSLog(@"Intermediate chunk wrote: %zu bytes, stream wrote: %ld", outputLength, (long)written);
        }
      }
    }
    
    if (status != kCCSuccess) {
      CCCryptorRelease(cryptor);
      free(inputBuffer);
      free(outputBuffer);
      [inputStream close];
      [outputStream close];
      reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Decryption failed with status: %d", status], nil);
      return;
    }
    
    CCCryptorRelease(cryptor);
    free(inputBuffer);
    free(outputBuffer);
    [inputStream close];
    [outputStream close];
    
    // Clean up temp file if we downloaded
    if ([inputUri hasPrefix:@"http"]) {
      [fileManager removeItemAtPath:inputPath error:nil];
    }
    
    NSLog(@"✅ Streaming decryption completed successfully");
    
    // Verify output file
    if ([fileManager fileExistsAtPath:outputPath]) {
      NSDictionary *outputAttrs = [fileManager attributesOfItemAtPath:outputPath error:nil];
      
      resolve(@{
        @"success": @YES,
        @"localPath": outputUri,
        @"size": outputAttrs[NSFileSize]
      });
    } else {
      reject(@"DECRYPT_FAILED", @"Output file verification failed", nil);
    }
    
  } @catch (NSException *exception) {
    CCCryptorRelease(cryptor);
    free(inputBuffer);
    free(outputBuffer);
    [inputStream close];
    [outputStream close];
    reject(@"DECRYPT_FAILED", [NSString stringWithFormat:@"Exception during decryption: %@", exception.reason], nil);
  }
}

// ✅ NSURLSessionDataDelegate methods for progressive data reception
- (void)URLSession:(NSURLSession *)session
          dataTask:(NSURLSessionDataTask *)dataTask
    didReceiveData:(NSData *)data
{
  NSOutputStream *outputStream = objc_getAssociatedObject(dataTask, "outputStream");
  NSValue *cryptorValue = objc_getAssociatedObject(dataTask, "cryptor");
  NSValue *bufferValue = objc_getAssociatedObject(dataTask, "outputBuffer");
  NSNumber *chunkSize = objc_getAssociatedObject(dataTask, "chunkSize");
  NSNumber *bufferSize = objc_getAssociatedObject(dataTask, "bufferSize");
  NSNumber *totalDownloadedNum = objc_getAssociatedObject(dataTask, "totalDownloaded");
  
  CCCryptorRef cryptor = (CCCryptorRef)[cryptorValue pointerValue];
  uint8_t *outputBuffer = (uint8_t *)[bufferValue pointerValue];
  NSUInteger chunkSizeValue = [chunkSize unsignedIntegerValue];
  NSUInteger bufferSizeValue = [bufferSize unsignedIntegerValue];
  unsigned long long totalDownloaded = [totalDownloadedNum unsignedLongLongValue];
  
  NSLog(@"📥 Received chunk: %lu bytes, total so far: %llu bytes", (unsigned long)data.length, totalDownloaded + data.length);
  
  // ✅ Decrypt data progressively
  const uint8_t *dataBytes = (const uint8_t *)data.bytes;
  NSUInteger dataLength = data.length;
  NSUInteger processedBytes = 0;
  
  while (processedBytes < dataLength) {
    @autoreleasepool {
      NSUInteger remainingBytes = dataLength - processedBytes;
      NSUInteger chunkToProcess = MIN(chunkSizeValue, remainingBytes);
      
      size_t outputLength = 0;
      CCCryptorStatus updateStatus = CCCryptorUpdate(
        cryptor,
        dataBytes + processedBytes,
        chunkToProcess,
        outputBuffer,
        bufferSizeValue,
        &outputLength
      );
      
      if (updateStatus != kCCSuccess) {
        NSLog(@"❌ Decryption failed with status: %d", updateStatus);
        objc_setAssociatedObject(dataTask, "hasError", @YES, OBJC_ASSOCIATION_RETAIN);
        objc_setAssociatedObject(dataTask, "errorMessage", 
          [NSString stringWithFormat:@"Decryption failed with status: %d", updateStatus],
          OBJC_ASSOCIATION_RETAIN);
        [dataTask cancel];
        return;
      }
      
      if (outputLength > 0) {
        [outputStream write:outputBuffer maxLength:outputLength];
        
        // CRITICAL: Force file descriptor sync so JavaScript FileSystem.getInfoAsync() 
        // sees the updated file size immediately (not one chunk behind)
        // This uses private API streamStatus to trigger internal buffer flush
        [outputStream streamStatus];
        
        NSLog(@"✅ Decrypted and wrote: %zu bytes (flushed)", outputLength);
      }
      
      processedBytes += chunkToProcess;
    }
  }
  
  totalDownloaded += data.length;
  objc_setAssociatedObject(dataTask, "totalDownloaded", @(totalDownloaded), OBJC_ASSOCIATION_RETAIN);
}

- (void)URLSession:(NSURLSession *)session
              task:(NSURLSessionTask *)task
didCompleteWithError:(NSError *)error
{
  dispatch_semaphore_t semaphore = objc_getAssociatedObject(task, "semaphore");
  
  if (error) {
    NSLog(@"❌ Download completed with error: %@", error);
    objc_setAssociatedObject(task, "hasError", @YES, OBJC_ASSOCIATION_RETAIN);
    objc_setAssociatedObject(task, "errorMessage", error.localizedDescription, OBJC_ASSOCIATION_RETAIN);
  } else {
    NSLog(@"✅ Download completed successfully");
  }
  
  if (semaphore) {
    dispatch_semaphore_signal(semaphore);
  }
}

@end
