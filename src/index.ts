import { NativeModules } from 'react-native';

interface CryptoModuleInterface {
  decryptFile(
    inputUri: string,
    outputUri: string,
    keyBase64: string,
    ivBase64: string,
    chunkSize?: number // Optional with default
  ): Promise<string>;
  
  decryptTextContent(
    encryptedContentBase64: string,
    keyBase64: string,
    ivBase64: string,
    chunkSize?: number // Optional with default
  ): Promise<string>;
  
  encryptTextContent(
    textContent: string,
    keyBase64: string,
    ivBase64: string,
    chunkSize?: number // Optional with default
  ): Promise<string>;
  
  encryptDataStreaming(
    inputDataBase64: string,
    keyBase64: string,
    ivBase64: string,
    chunkSize?: number // Optional with default
  ): Promise<{
    encryptedChunks: string[];
    totalChunks: number;
    totalProcessed: number;
  }>;
}

const CryptoModule: CryptoModuleInterface = NativeModules.CryptoModule;

export default CryptoModule;
export { DEFAULT_CHUNK_SIZE, AES_BLOCK_SIZE } from './constants';
