// ✅ tests/utils/testHelpers.js
import * as FileSystem from 'expo-file-system';
import { Buffer } from 'buffer';

export const createTestFile = async (content, filename) => {
  const path = `${FileSystem.documentDirectory}${filename}`;
  await FileSystem.writeAsStringAsync(path, content, {
    encoding: FileSystem.EncodingType.UTF8,
  });
  return path;
};

export const createBinaryTestFile = async (data, filename) => {
  const path = `${FileSystem.documentDirectory}${filename}`;
  const base64Data = Buffer.from(data).toString('base64');
  await FileSystem.writeAsStringAsync(path, base64Data, {
    encoding: FileSystem.EncodingType.Base64,
  });
  return path;
};

export const generateRandomKey = (length = 32) => {
  const array = new Uint8Array(length);
  for (let i = 0; i < length; i++) {
    array[i] = Math.floor(Math.random() * 256);
  }
  return Buffer.from(array).toString('base64');
};

export const generateRandomIV = () => {
  return generateRandomKey(16);
};

export const createLargeTestData = (sizeInMB = 5) => {
  const chunkSize = 1024; // 1KB chunks
  const totalChunks = (sizeInMB * 1024 * 1024) / chunkSize;
  const chunks = [];
  
  for (let i = 0; i < totalChunks; i++) {
    chunks.push('A'.repeat(chunkSize));
  }
  
  return chunks.join('');
};

export const validateFileExists = async (path) => {
  const fileInfo = await FileSystem.getInfoAsync(path);
  return fileInfo.exists;
};

export const getFileSize = async (path) => {
  const fileInfo = await FileSystem.getInfoAsync(path);
  return fileInfo.exists ? fileInfo.size : 0;
};

export const cleanupTestFiles = async (paths) => {
  for (const path of paths) {
    try {
      await FileSystem.deleteAsync(path, { idempotent: true });
    } catch (error) {
      console.warn(`Failed to cleanup ${path}:`, error);
    }
  }
};