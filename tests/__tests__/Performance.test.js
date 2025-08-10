// ✅ tests/__tests__/Performance.test.js
import { NativeModules } from 'react-native';
import CryptoModule from '../../src/index';
import { createLargeTestData, generateRandomKey, generateRandomIV } from '../utils/testHelpers';

const { CryptoModule: MockedModule } = NativeModules;

describe('CryptoModule Performance', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('Large File Handling', () => {
    test('should handle 10MB file encryption', async () => {
      const largeData = createLargeTestData(10); // 10MB
      const base64Data = Buffer.from(largeData).toString('base64');
      
      const startTime = Date.now();
      
      MockedModule.encryptDataStreaming.mockResolvedValue({
        encryptedChunks: new Array(10).fill('encrypted-chunk'),
        totalChunks: 10,
        totalProcessed: largeData.length,
      });

      const result = await CryptoModule.encryptDataStreaming(
        base64Data,
        generateRandomKey(),
        generateRandomIV(),
        1024 * 1024 // 1MB chunks
      );

      const endTime = Date.now();
      const duration = endTime - startTime;

      expect(result.totalChunks).toBe(10);
      expect(duration).toBeLessThan(5000); // Should complete within 5 seconds
    });

    test('should handle concurrent operations', async () => {
      MockedModule.encryptTextContent.mockResolvedValue('encrypted');
      
      const promises = [];
      for (let i = 0; i < 5; i++) {
        promises.push(
          CryptoModule.encryptTextContent(
            `test-${i}`,
            generateRandomKey(),
            generateRandomIV()
          )
        );
      }

      const startTime = Date.now();
      const results = await Promise.all(promises);
      const endTime = Date.now();

      expect(results).toHaveLength(5);
      expect(endTime - startTime).toBeLessThan(3000); // Should complete within 3 seconds
    });
  });

  describe('Memory Usage', () => {
    test('should not exceed memory limits for large files', async () => {
      const initialMemory = process.memoryUsage();
      
      const largeData = createLargeTestData(50); // 50MB
      const base64Data = Buffer.from(largeData).toString('base64');

      MockedModule.encryptDataStreaming.mockResolvedValue({
        encryptedChunks: new Array(50).fill('encrypted-chunk'),
        totalChunks: 50,
        totalProcessed: largeData.length,
      });

      await CryptoModule.encryptDataStreaming(
        base64Data,
        generateRandomKey(),
        generateRandomIV(),
        1024 * 1024 // 1MB chunks
      );

      const finalMemory = process.memoryUsage();
      const memoryIncrease = finalMemory.heapUsed - initialMemory.heapUsed;

      // Memory increase should be reasonable (less than 100MB)
      expect(memoryIncrease).toBeLessThan(100 * 1024 * 1024);
    });
  });
});