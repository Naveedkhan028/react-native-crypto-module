// ✅ tests/setup.js
import { NativeModules } from 'react-native';

// Mock the native module for testing
NativeModules.CryptoModule = {
  decryptFile: jest.fn(),
  decryptFileWithStreaming: jest.fn(),
  decryptTextContent: jest.fn(),
  encryptTextContent: jest.fn(),
  encryptDataStreaming: jest.fn(),
};

// Mock console methods for cleaner test output
global.console = {
  ...console,
  log: jest.fn(),
  error: jest.fn(),
  warn: jest.fn(),
};

// Setup test data
global.TEST_DATA = {
  sampleText: 'Hello, World! This is a test message for encryption.',
  base64Key: 'SGVsbG9Xb3JsZEtleTEyMzQ1Njc4OTBBQkNERUZHSEk=', // 32 bytes
  base64IV: 'MTIzNDU2Nzg5MEFCQ0RFRg==', // 16 bytes
  sampleEncryptedText: 'U2FtcGxlRW5jcnlwdGVkVGV4dA==',
  chunkSize: 1048576, // 1MB
};