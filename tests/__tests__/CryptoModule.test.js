// ✅ tests/__tests__/CryptoModule.test.js
import { NativeModules } from 'react-native';
import CryptoModule from '../../src/index';
import {
  createTestFile,
  createBinaryTestFile,
  generateRandomKey,
  generateRandomIV,
  createLargeTestData,
  validateFileExists,
  getFileSize,
  cleanupTestFiles,
} from '../utils/testHelpers';

const { CryptoModule: MockedModule } = NativeModules;

describe('CryptoModule', () => {
  let testFiles = [];

  afterEach(async () => {
    await cleanupTestFiles(testFiles);
    testFiles = [];
    jest.clearAllMocks();
  });

  describe('Module Availability', () => {
    test('should export CryptoModule correctly', () => {
      expect(CryptoModule).toBeDefined();
      expect(typeof CryptoModule).toBe('object');
    });

    test('should have all required methods', () => {
      const requiredMethods = [
        'decryptFile',
        'decryptFileWithStreaming',
        'decryptTextContent',
        'encryptTextContent',
        'encryptDataStreaming',
      ];

      requiredMethods.forEach(method => {
        expect(MockedModule[method]).toBeDefined();
        expect(typeof MockedModule[method]).toBe('function');
      });
    });
  });

  describe('Text Content Encryption/Decryption', () => {
    test('should encrypt text content successfully', async () => {
      const testText = global.TEST_DATA.sampleText;
      const key = global.TEST_DATA.base64Key;
      const iv = global.TEST_DATA.base64IV;

      MockedModule.encryptTextContent.mockResolvedValue('U2FtcGxlRW5jcnlwdGVkVGV4dA==');

      const result = await CryptoModule.encryptTextContent(testText, key, iv);

      expect(MockedModule.encryptTextContent).toHaveBeenCalledWith(
        testText,
        key,
        iv,
        undefined // chunkSize is optional
      );
      expect(result).toBe('U2FtcGxlRW5jcnlwdGVkVGV4dA==');
    });

    test('should decrypt text content successfully', async () => {
      const encryptedText = global.TEST_DATA.sampleEncryptedText;
      const key = global.TEST_DATA.base64Key;
      const iv = global.TEST_DATA.base64IV;
      const expectedDecrypted = global.TEST_DATA.sampleText;

      MockedModule.decryptTextContent.mockResolvedValue(expectedDecrypted);

      const result = await CryptoModule.decryptTextContent(encryptedText, key, iv);

      expect(MockedModule.decryptTextContent).toHaveBeenCalledWith(
        encryptedText,
        key,
        iv,
        undefined
      );
      expect(result).toBe(expectedDecrypted);
    });

    test('should handle empty text content', async () => {
      MockedModule.encryptTextContent.mockResolvedValue('');

      const result = await CryptoModule.encryptTextContent('', global.TEST_DATA.base64Key, global.TEST_DATA.base64IV);

      expect(result).toBe('');
    });

    test('should handle invalid key format', async () => {
      MockedModule.encryptTextContent.mockRejectedValue(new Error('Invalid key format'));

      await expect(
        CryptoModule.encryptTextContent('test', 'invalid-key', global.TEST_DATA.base64IV)
      ).rejects.toThrow('Invalid key format');
    });
  });

  describe('File Encryption/Decryption', () => {
    test('should decrypt file successfully', async () => {
      const inputPath = await createTestFile('encrypted content', 'test-encrypted.txt');
      const outputPath = `${inputPath}.decrypted`;
      testFiles.push(inputPath, outputPath);

      MockedModule.decryptFile.mockResolvedValue({
        success: true,
        localPath: outputPath,
        size: 1024,
      });

      const result = await CryptoModule.decryptFile(
        inputPath,
        outputPath,
        global.TEST_DATA.base64Key,
        global.TEST_DATA.base64IV
      );

      expect(MockedModule.decryptFile).toHaveBeenCalledWith(
        inputPath,
        outputPath,
        global.TEST_DATA.base64Key,
        global.TEST_DATA.base64IV,
        undefined
      );
      expect(result.success).toBe(true);
      expect(result.localPath).toBe(outputPath);
    });

    test('should handle streaming decryption', async () => {
      const inputPath = await createTestFile('large encrypted content', 'test-large.enc');
      const outputPath = `${inputPath}.decrypted`;
      testFiles.push(inputPath, outputPath);

      MockedModule.decryptFileWithStreaming.mockResolvedValue({
        success: true,
        localPath: outputPath,
        size: 5242880, // 5MB
      });

      const result = await CryptoModule.decryptFileWithStreaming(
        inputPath,
        outputPath,
        global.TEST_DATA.base64Key,
        global.TEST_DATA.base64IV,
        'test-token',
        global.TEST_DATA.chunkSize
      );

      expect(MockedModule.decryptFileWithStreaming).toHaveBeenCalledWith(
        inputPath,
        outputPath,
        global.TEST_DATA.base64Key,
        global.TEST_DATA.base64IV,
        'test-token',
        global.TEST_DATA.chunkSize
      );
      expect(result.success).toBe(true);
    });

    test('should handle file not found error', async () => {
      MockedModule.decryptFile.mockRejectedValue(new Error('Input file does not exist'));

      await expect(
        CryptoModule.decryptFile(
          '/non/existent/file.enc',
          '/tmp/output.txt',
          global.TEST_DATA.base64Key,
          global.TEST_DATA.base64IV
        )
      ).rejects.toThrow('Input file does not exist');
    });
  });

  describe('Streaming Encryption', () => {
    test('should encrypt data in chunks', async () => {
      const largeData = createLargeTestData(2); // 2MB
      const base64Data = Buffer.from(largeData).toString('base64');

      MockedModule.encryptDataStreaming.mockResolvedValue({
        encryptedChunks: ['chunk1', 'chunk2', 'chunk3'],
        totalChunks: 3,
        totalProcessed: largeData.length,
      });

      const result = await CryptoModule.encryptDataStreaming(
        base64Data,
        global.TEST_DATA.base64Key,
        global.TEST_DATA.base64IV,
        global.TEST_DATA.chunkSize
      );

      expect(MockedModule.encryptDataStreaming).toHaveBeenCalledWith(
        base64Data,
        global.TEST_DATA.base64Key,
        global.TEST_DATA.base64IV,
        global.TEST_DATA.chunkSize
      );
      expect(result.totalChunks).toBe(3);
      expect(result.encryptedChunks).toHaveLength(3);
    });

    test('should handle chunk size validation', async () => {
      MockedModule.encryptDataStreaming.mockRejectedValue(new Error('Invalid chunk size'));

      await expect(
        CryptoModule.encryptDataStreaming(
          'data',
          global.TEST_DATA.base64Key,
          global.TEST_DATA.base64IV,
          0 // Invalid chunk size
        )
      ).rejects.toThrow('Invalid chunk size');
    });
  });

  describe('Parameter Validation', () => {
    test('should validate key length', async () => {
      MockedModule.encryptTextContent.mockRejectedValue(new Error('Invalid key length'));

      await expect(
        CryptoModule.encryptTextContent(
          'test',
          'short-key', // Invalid key
          global.TEST_DATA.base64IV
        )
      ).rejects.toThrow('Invalid key length');
    });

    test('should validate IV length', async () => {
      MockedModule.encryptTextContent.mockRejectedValue(new Error('Invalid IV length'));

      await expect(
        CryptoModule.encryptTextContent(
          'test',
          global.TEST_DATA.base64Key,
          'short-iv' // Invalid IV
        )
      ).rejects.toThrow('Invalid IV length');
    });

    test('should use default chunk size when not provided', async () => {
      MockedModule.encryptTextContent.mockResolvedValue('encrypted');

      await CryptoModule.encryptTextContent(
        'test',
        global.TEST_DATA.base64Key,
        global.TEST_DATA.base64IV
        // No chunk size provided
      );

      expect(MockedModule.encryptTextContent).toHaveBeenCalledWith(
        'test',
        global.TEST_DATA.base64Key,
        global.TEST_DATA.base64IV,
        undefined
      );
    });
  });

  describe('Error Handling', () => {
    test('should handle memory allocation failures', async () => {
      MockedModule.decryptFile.mockRejectedValue(new Error('Memory allocation failed'));

      await expect(
        CryptoModule.decryptFile(
          'input.txt',
          'output.txt',
          global.TEST_DATA.base64Key,
          global.TEST_DATA.base64IV
        )
      ).rejects.toThrow('Memory allocation failed');
    });

    test('should handle network errors for remote files', async () => {
      MockedModule.decryptFileWithStreaming.mockRejectedValue(new Error('Network error'));

      await expect(
        CryptoModule.decryptFileWithStreaming(
          'http://example.com/file.enc',
          'output.txt',
          global.TEST_DATA.base64Key,
          global.TEST_DATA.base64IV,
          'token',
          global.TEST_DATA.chunkSize
        )
      ).rejects.toThrow('Network error');
    });

    test('should handle cryptographic errors', async () => {
      MockedModule.decryptTextContent.mockRejectedValue(new Error('Decryption failed'));

      await expect(
        CryptoModule.decryptTextContent(
          'invalid-encrypted-data',
          global.TEST_DATA.base64Key,
          global.TEST_DATA.base64IV
        )
      ).rejects.toThrow('Decryption failed');
    });
  });
});