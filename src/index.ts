import { NativeModules } from 'react-native';

interface CryptoModuleInterface {
  decryptFile(
    inputUri: string,
    outputUri: string,
    keyBase64: string,
    ivBase64: string
  ): Promise<string>;
}

const CryptoModule: CryptoModuleInterface = NativeModules.CryptoModule;

export default CryptoModule;