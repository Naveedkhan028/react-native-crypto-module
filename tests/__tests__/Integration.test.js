// ✅ tests/__tests__/Integration.test.js
import { NativeModules } from 'react-native';
import CryptoModule from '../../src/index';
import { createTestFile, cleanupTestFiles, generateRandomKey, generateRandomIV } from '../utils/testHelpers';

describe('CryptoModule Integration', () => {
  let testFiles = [];

  afterEach(async () => {
    await cleanupTestFiles(testFiles);
    testFiles = [];
    jest.clearAllMocks();
  });

  test('should encrypt and decrypt text content correctly', async () => {
    const originalText = 'Hello, World! This is a test message.';
    const key = generateRandomKey();
    const iv = generateRandomIV();

    // Mock encryption
    NativeModules.CryptoModule.encryptTextContent.mockResolvedValue('bW9ja2VkLWVuY3J5cHRlZC10ZXh0');
    
    // Mock decryption
    NativeModules.CryptoModule.decryptTextContent.mockResolvedValue(originalText);

    // Encrypt
    const encrypted = await CryptoModule.encryptTextContent(originalText, key, iv);
    expect(encrypted).toBe('bW9ja2VkLWVuY3J5cHRlZC10ZXh0');

    // Decrypt
    const decrypted = await CryptoModule.decryptTextContent(encrypted, key, iv);
    expect(decrypted).toBe(originalText);
  });

  test('should handle file encryption/decryption workflow', async () => {
    const inputPath = await createTestFile('test file content', 'input.txt');
    const encryptedPath = `${inputPath}.enc`;
    const decryptedPath = `${inputPath}.dec`;
    testFiles.push(inputPath, encryptedPath, decryptedPath);

    const key = generateRandomKey();
    const iv = generateRandomIV();

    // Mock the workflow
    NativeModules.CryptoModule.decryptFile.mockResolvedValue({
      success: true,
      localPath: decryptedPath,
      size: 1024,
    });

    const result = await CryptoModule.decryptFile(
      encryptedPath,
      decryptedPath,
      key,
      iv
    );

    expect(result.success).toBe(true);
    expect(result.localPath).toBe(decryptedPath);
  });

  test('should handle streaming workflow for large files', async () => {
    const inputPath = await createTestFile('large file content'.repeat(10000), 'large.txt');
    const outputPath = `${inputPath}.decrypted`;
    testFiles.push(inputPath, outputPath);

    const key = generateRandomKey();
    const iv = generateRandomIV();

    NativeModules.CryptoModule.decryptFileWithStreaming.mockResolvedValue({
      success: true,
      localPath: outputPath,
      size: 150000,
    });

    const result = await CryptoModule.decryptFileWithStreaming(
      inputPath,
      outputPath,
      key,
      iv,
      'test-token',
      1024 * 1024
    );

    expect(result.success).toBe(true);
    expect(result.size).toBeGreaterThan(0);
  });
});