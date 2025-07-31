import { NativeModules } from 'react-native';
import { DEFAULT_CHUNK_SIZE } from './constants';

interface CryptoModuleInterface {
  decryptFile(
    inputUri: string,
    outputUri: string,
    keyBase64: string,
    ivBase64: string,
    chunkSize?: number
  ): Promise<string>;
  
  decryptTextContent(
    encryptedContentBase64: string,
    keyBase64: string,
    ivBase64: string,
    chunkSize?: number
  ): Promise<string>;
  
  encryptTextContent(
    textContent: string,
    keyBase64: string,
    ivBase64: string,
    chunkSize?: number
  ): Promise<string>;
  
  encryptDataStreaming(
    inputDataBase64: string,
    keyBase64: string,
    ivBase64: string,
    chunkSize?: number
  ): Promise<{
    encryptedChunks: string[];
    totalChunks: number;
    totalProcessed: number;
  }>;
}

const { CryptoModule } = NativeModules;

// Create a wrapper that handles optional chunkSize parameter
const CryptoModuleWrapper: CryptoModuleInterface = {
  decryptFile: async (inputUri: string, outputUri: string, keyBase64: string, ivBase64: string, chunkSize?: number): Promise<string> => {
    // For file operations, chunkSize is not used, so call the original method
    return CryptoModule.decryptFile(inputUri, outputUri, keyBase64, ivBase64);
  },

  decryptTextContent: async (encryptedContentBase64: string, keyBase64: string, ivBase64: string, chunkSize?: number): Promise<string> => {
    // For text operations, chunkSize is not used, so call the original method
    return CryptoModule.decryptTextContent(encryptedContentBase64, keyBase64, ivBase64);
  },

  encryptTextContent: async (textContent: string, keyBase64: string, ivBase64: string, chunkSize?: number): Promise<string> => {
    // For text operations, chunkSize is not used, so call the original method
    return CryptoModule.encryptTextContent(textContent, keyBase64, ivBase64);
  },

  encryptDataStreaming: async (inputDataBase64: string, keyBase64: string, ivBase64: string, chunkSize?: number): Promise<{
    encryptedChunks: string[];
    totalChunks: number;
    totalProcessed: number;
  }> => {
    // Use the provided chunkSize or default to 64KB
    const actualChunkSize = chunkSize || DEFAULT_CHUNK_SIZE;
    return CryptoModule.encryptDataStreaming(inputDataBase64, keyBase64, ivBase64, actualChunkSize);
  }
};

export default CryptoModuleWrapper;
