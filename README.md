# React Native Crypto Module

A React Native module for AES-256-CBC file decryption on iOS and Android.

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

```javascript
import CryptoModule from 'react-native-crypto-module';

// Decrypt a file
try {
  const decryptedUri = await CryptoModule.decryptFile(
    'file:///path/to/encrypted/file.enc',
    'file:///path/to/decrypted/file.txt',
    'base64EncodedKey',
    'base64EncodedIV'
  );
  console.log('Decrypted file:', decryptedUri);
} catch (error) {
  console.error('Decryption failed:', error);
}
```

## API

### `decryptFile(inputUri, outputUri, keyBase64, ivBase64)`

Decrypts a file using AES-256-CBC.

**Parameters:**
- `inputUri` (string): Path to the encrypted file
- `outputUri` (string): Path where the decrypted file will be saved
- `keyBase64` (string): Base64 encoded 32-byte key
- `ivBase64` (string): Base64 encoded 16-byte IV

**Returns:** Promise<string> - The output URI on success

## License

MIT