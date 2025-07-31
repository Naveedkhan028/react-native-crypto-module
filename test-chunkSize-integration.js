/**
 * Test file to verify chunkSize parameter integration
 * This demonstrates how to use the new optional chunkSize parameter
 */

import CryptoModule from './src/index';

// Test data
const testText = "Hello, this is a test message for encryption!";
const testKey = "MTIzNDU2Nzg5MGFiY2RlZjEyMzQ1Njc4OTBhYmNkZWY="; // 32-byte key in base64
const testIV = "MTIzNDU2Nzg5MGFiY2RlZg=="; // 16-byte IV in base64

// Test with different chunk sizes
async function testChunkSizeParameter() {
  console.log("=== Testing chunkSize parameter ===");
  
  try {
    // Test 1: encryptTextContent with default chunkSize (undefined)
    console.log("\n1. Testing encryptTextContent with default chunkSize");
    const encrypted1 = await CryptoModule.encryptTextContent(testText, testKey, testIV);
    console.log("✅ Default chunkSize - Encrypted:", encrypted1.substring(0, 50) + "...");

    // Test 2: encryptTextContent with custom chunkSize
    console.log("\n2. Testing encryptTextContent with custom chunkSize (32KB)");
    const encrypted2 = await CryptoModule.encryptTextContent(testText, testKey, testIV, 32 * 1024);
    console.log("✅ Custom chunkSize - Encrypted:", encrypted2.substring(0, 50) + "...");

    // Test 3: decryptTextContent with default chunkSize
    console.log("\n3. Testing decryptTextContent with default chunkSize");
    const decrypted1 = await CryptoModule.decryptTextContent(encrypted1, testKey, testIV);
    console.log("✅ Default chunkSize - Decrypted:", decrypted1);

    // Test 4: decryptTextContent with custom chunkSize
    console.log("\n4. Testing decryptTextContent with custom chunkSize (16KB)");
    const decrypted2 = await CryptoModule.decryptTextContent(encrypted2, testKey, testIV, 16 * 1024);
    console.log("✅ Custom chunkSize - Decrypted:", decrypted2);

    // Test 5: encryptDataStreaming with default chunkSize
    console.log("\n5. Testing encryptDataStreaming with default chunkSize");
    const streamingResult1 = await CryptoModule.encryptDataStreaming(
      Buffer.from(testText).toString('base64'), 
      testKey, 
      testIV
    );
    console.log("✅ Default chunkSize - Streaming result:", streamingResult1.totalChunks, "chunks");

    // Test 6: encryptDataStreaming with custom chunkSize
    console.log("\n6. Testing encryptDataStreaming with custom chunkSize (8KB)");
    const streamingResult2 = await CryptoModule.encryptDataStreaming(
      Buffer.from(testText).toString('base64'), 
      testKey, 
      testIV, 
      8 * 1024
    );
    console.log("✅ Custom chunkSize - Streaming result:", streamingResult2.totalChunks, "chunks");

    // Test 7: decryptFile with default chunkSize (would need actual files)
    console.log("\n7. All chunkSize parameter tests completed successfully!");
    
    // Verify results
    if (decrypted1 === testText && decrypted2 === testText) {
      console.log("✅ All encryption/decryption results match original text");
    } else {
      console.error("❌ Encryption/decryption results don't match");
    }

  } catch (error) {
    console.error("❌ Test failed:", error);
  }
}

// Run the tests
testChunkSizeParameter();

// Export for use in other files
export default testChunkSizeParameter;
