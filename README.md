# React Native Crypto Module

A high-performance React Native module for AES-256-CBC encryption and decryption on iOS and Android. Supports both file operations and text content with streaming capabilities for large data processing.

## Features

- 🔒 **AES-256-CBC encryption/decryption**
- 📁 **File encryption and decryption**
- 📝 **Text content encryption and decryption**
- 🚀 **Streaming encryption for large files**
- 📱 **iOS and Android support**
- ⚡ **Native performance with JavaScript fallback**
- 🔄 **Auto-linking support**

## Installation

```bash
npm install react-native-crypto-module
```

### iOS

```bash
cd ios && pod install
```

### Android

For React Native 0.60+ with autolinking, no additional steps required.

For React Native < 0.60:

1. Add to `android/settings.gradle`:
```gradle
include ':react-native-crypto-module'
project(':react-native-crypto-module').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-crypto-module/android')
```

2. Add to `android/app/build.gradle`:
```gradle
dependencies {
    implementation project(':react-native-crypto-module')
}
```

3. In `MainApplication.java`:
```java
import com.cryptomodule.CryptoModulePackage;

@Override
protected List<ReactPackage> getPackages() {
    return Arrays.<ReactPackage>asList(
        new MainReactPackage(),
        new CryptoModulePackage()
    );
}
```

## Usage

### Import the Module

```javascript
import { NativeModules } from 'react-native';
const { CryptoModule } = NativeModules;
```

### File Decryption

Decrypt encrypted files with native performance:

```javascript
try {
  const decryptedUri = await CryptoModule.decryptFile(
    'file:///path/to/encrypted/file.enc',
    'file:///path/to/decrypted/file.txt',
    'base64EncodedKey',
    'base64EncodedIV'
  );
  console.log('✅ Decrypted file:', decryptedUri);
} catch (error) {
  console.error('❌ Decryption failed:', error);
}
```

### Text Content Decryption

Decrypt encrypted text content:

```javascript
try {
  const decryptedText = await CryptoModule.decryptTextContent(
    'base64EncryptedText',
    'base64EncodedKey',
    'base64EncodedIV'
  );
  console.log('✅ Decrypted text:', decryptedText);
} catch (error) {
  console.error('❌ Text decryption failed:', error);
}
```

### Streaming Encryption

For large files, use streaming encryption to process data in chunks:

```javascript
try {
  const inputDataBase64 = 'base64EncodedLargeData';
  const chunkSize = 64 * 1024; // 64KB chunks
  
  const result = await CryptoModule.encryptDataStreaming(
    inputDataBase64,
    'base64EncodedKey',
    'base64EncodedIV',
    chunkSize
  );
  
  console.log('✅ Streaming encryption completed');
  console.log(`📦 Total chunks: ${result.totalChunks}`);
  console.log(`📊 Processed: ${result.totalProcessed} bytes`);
  
  // Access encrypted chunks
  const encryptedChunks = result.encryptedChunks;
} catch (error) {
  console.error('❌ Streaming encryption failed:', error);
}
```

### With Progress Tracking

Use with progress callbacks for user feedback:

```javascript
const progressCallback = (progress) => {
  console.log(`Progress: ${progress}%`);
  // Update your UI progress indicator
};

// Example with file decryption and progress
try {
  const decryptedUri = await decryptFileWithProgress(
    inputUri,
    outputUri,
    keyBase64,
    ivBase64,
    progressCallback
  );
} catch (error) {
  console.error('Operation failed:', error);
}
```

## API Reference

### File Operations

#### `decryptFile(inputUri, outputUri, keyBase64, ivBase64)`

Decrypts a file using AES-256-CBC.

**Parameters:**
- `inputUri` (string): Path to the encrypted file (supports `file://` URIs)
- `outputUri` (string): Path where the decrypted file will be saved
- `keyBase64` (string): Base64 encoded 32-byte AES key
- `ivBase64` (string): Base64 encoded 16-byte initialization vector

**Returns:** `Promise<string>` - The output URI on success

**Example:**
```javascript
const result = await CryptoModule.decryptFile(
  'file:///storage/encrypted.dat',
  'file:///storage/decrypted.txt',
  'your-base64-key',
  'your-base64-iv'
);
```

### Text Operations

#### `decryptTextContent(encryptedContentBase64, keyBase64, ivBase64)`

Decrypts encrypted text content.

**Parameters:**
- `encryptedContentBase64` (string): Base64 encoded encrypted text
- `keyBase64` (string): Base64 encoded 32-byte AES key
- `ivBase64` (string): Base64 encoded 16-byte initialization vector

**Returns:** `Promise<string>` - The decrypted text content

**Example:**
```javascript
const decrypted = await CryptoModule.decryptTextContent(
  'base64EncryptedText',
  'your-base64-key',
  'your-base64-iv'
);
```

### Streaming Operations

#### `encryptDataStreaming(inputDataBase64, keyBase64, ivBase64, chunkSize)`

Encrypts large data using streaming approach for memory efficiency.

**Parameters:**
- `inputDataBase64` (string): Base64 encoded input data
- `keyBase64` (string): Base64 encoded 32-byte AES key
- `ivBase64` (string): Base64 encoded 16-byte initialization vector
- `chunkSize` (number): Size of each chunk in bytes (default: 65536)

**Returns:** `Promise<Object>` - Result object with encrypted chunks

**Result Object:**
```javascript
{
  encryptedChunks: string[], // Array of base64 encoded encrypted chunks
  totalChunks: number,       // Total number of chunks processed
  totalProcessed: number     // Total bytes processed
}
```

**Example:**
```javascript
const result = await CryptoModule.encryptDataStreaming(
  inputDataBase64,
  keyBase64,
  ivBase64,
  64 * 1024 // 64KB chunks
);

// Combine chunks if needed
const combinedEncrypted = result.encryptedChunks.join('');
```

## Error Handling

All methods return promises that reject with descriptive error messages:

```javascript
try {
  const result = await CryptoModule.decryptFile(/* ... */);
} catch (error) {
  switch (error.code) {
    case 'DECRYPT_FAILED':
      console.error('Decryption failed:', error.message);
      break;
    case 'FILE_NOT_FOUND':
      console.error('Input file not found:', error.message);
      break;
    case 'INVALID_KEY':
      console.error('Invalid encryption key:', error.message);
      break;
    default:
      console.error('Unknown error:', error.message);
  }
}
```

## Key Requirements

- **Key Size:** 32 bytes (256 bits) for AES-256
- **IV Size:** 16 bytes (128 bits)
- **Encoding:** All keys and IVs must be base64 encoded
- **Algorithm:** AES-256-CBC with PKCS7 padding

## Performance Benefits

- **Native Implementation:** Uses platform-specific crypto libraries (CommonCrypto on iOS, javax.crypto on Android)
- **Streaming Support:** Process large files without loading everything into memory
- **Chunk Processing:** Configurable chunk sizes for optimal memory usage
- **Fallback Support:** Graceful fallback to JavaScript implementation when native module is unavailable

## Platform Support

- **iOS:** 12.0+
- **Android:** API Level 21+
- **React Native:** 0.60+

## Security Notes

- Always use cryptographically secure random keys and IVs
- Store keys securely using platform keychain/keystore
- Never hardcode encryption keys in your application
- Use different IVs for each encryption operation

## Troubleshooting

### Module Not Found

If you get "Module not found" errors:

1. Ensure the module is properly installed: `npm install react-native-crypto-module`
2. For iOS: Run `cd ios && pod install`
3. For Android: Clean and rebuild your project
4. Restart Metro bundler

### Build Errors

For Android build errors:
- Ensure Java 17+ is being used
- Check that the module files are in the correct package structure

For iOS build errors:
- Verify Xcode and CocoaPods are up to date
- Clean build folder and retry

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT License - see the [LICENSE](LICENSE) file for details.

---

## Changelog

### v2.0.0
- ✨ Added streaming encryption support
- ✨ Added text content decryption
- 🚀 Improved performance with native implementations
- 📱 Enhanced error handling and logging
- 🔧 Better auto-linking support

### v1.0.0
- 🎉 Initial release with file decryption support