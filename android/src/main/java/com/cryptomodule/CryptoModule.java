
package com.cryptomodule;
import android.util.Log;
import android.util.Base64;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoModule extends ReactContextBaseJavaModule {
    private static final String TAG = "CryptoModule";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    
    public CryptoModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }
    
    @Override
    public String getName() {
        return "CryptoModule";
    }
    
    private String convertFileUriToPath(String fileUri) {
        if (fileUri.startsWith("file://")) {
            String path = fileUri.substring(7);
            Log.d(TAG, "Converted URI '" + fileUri + "' to path '" + path + "'");
            return path;
        }
        Log.d(TAG, "URI doesn't start with file://, using as-is: " + fileUri);
        return fileUri;
    }
    
    @ReactMethod
    public void decryptFile(String inputUri, String outputUri, String keyBase64, String ivBase64, Integer chunkSize, Promise promise) {
        try {
            Log.d(TAG, "=== NATIVE MODULE DEBUG ===");
            Log.d(TAG, "inputUri: " + inputUri);
            Log.d(TAG, "outputUri: " + outputUri);
            Log.d(TAG, "keyBase64 length: " + keyBase64.length());
            Log.d(TAG, "ivBase64 length: " + ivBase64.length());
            Log.d(TAG, "chunkSize: " + (chunkSize != null ? chunkSize : "default"));
            
            // Use default chunk size if not provided
            int actualChunkSize = chunkSize != null ? chunkSize : 64 * 1024; // Default 64KB
            
            // Convert file URIs to local paths
            String inputPath = convertFileUriToPath(inputUri);
            String outputPath = convertFileUriToPath(outputUri);
            
            Log.d(TAG, "Converted inputPath: " + inputPath);
            Log.d(TAG, "Converted outputPath: " + outputPath);
            
            // Validate inputs
            if (inputPath == null || inputPath.isEmpty()) {
                Log.e(TAG, "❌ Invalid inputPath");
                promise.reject("DECRYPT_FAILED", "Invalid input path");
                return;
            }
            
            if (outputPath == null || outputPath.isEmpty()) {
                Log.e(TAG, "❌ Invalid outputPath");
                promise.reject("DECRYPT_FAILED", "Invalid output path");
                return;
            }
            
            if (keyBase64 == null || keyBase64.isEmpty()) {
                Log.e(TAG, "❌ Invalid keyBase64");
                promise.reject("DECRYPT_FAILED", "Invalid key");
                return;
            }
            
            if (ivBase64 == null || ivBase64.isEmpty()) {
                Log.e(TAG, "❌ Invalid ivBase64");
                promise.reject("DECRYPT_FAILED", "Invalid IV");
                return;
            }
            
            // Check if input file exists
            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                Log.e(TAG, "❌ Input file does not exist at path: " + inputPath);
                promise.reject("DECRYPT_FAILED", "Input file does not exist: " + inputPath);
                return;
            }
            
            // Create output directory if needed
            File outputFile = new File(outputPath);
            File outputDir = outputFile.getParentFile();
            if (outputDir != null && !outputDir.exists()) {
                if (!outputDir.mkdirs()) {
                    Log.e(TAG, "❌ Failed to create output directory");
                    promise.reject("DECRYPT_FAILED", "Failed to create output directory");
                    return;
                }
            }
            
            // Convert base64 to bytes
            byte[] keyBytes = Base64.decode(keyBase64, Base64.DEFAULT);
            byte[] ivBytes = Base64.decode(ivBase64, Base64.DEFAULT);
            
            Log.d(TAG, "keyBytes length: " + keyBytes.length);
            Log.d(TAG, "ivBytes length: " + ivBytes.length);
            
            if (keyBytes.length != 32) {
                Log.e(TAG, "❌ Invalid key data - length: " + keyBytes.length + ", expected: 32");
                promise.reject("DECRYPT_FAILED", "Invalid key data length: " + keyBytes.length);
                return;
            }
            
            if (ivBytes.length != 16) {
                Log.e(TAG, "❌ Invalid IV data - length: " + ivBytes.length + ", expected: 16");
                promise.reject("DECRYPT_FAILED", "Invalid IV data length: " + ivBytes.length);
                return;
            }
            
            // Read input file
            FileInputStream fis = new FileInputStream(inputFile);
            byte[] inputData = new byte[(int) inputFile.length()];
            fis.read(inputData);
            fis.close();
            
            if (inputData.length == 0) {
                Log.e(TAG, "❌ Input file is empty");
                promise.reject("DECRYPT_FAILED", "Input file is empty");
                return;
            }
            
            Log.d(TAG, "✅ Input file read successfully, size: " + inputData.length + " bytes");
            
            // Perform AES-256-CBC decryption
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            
            Log.d(TAG, "Starting decryption...");
            Log.d(TAG, "Input data length: " + inputData.length);
            
            byte[] decryptedData = cipher.doFinal(inputData);
            
            Log.d(TAG, "✅ Decryption successful, output size: " + decryptedData.length + " bytes");
            
            // Write output file
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(decryptedData);
            fos.close();
            
            Log.d(TAG, "✅ File written successfully to: " + outputPath);
            
            // Verify the file was written
            if (outputFile.exists()) {
                Log.d(TAG, "✅ Output file verified, size: " + outputFile.length());
                promise.resolve(outputUri); // Return the original URI format
            } else {
                Log.e(TAG, "❌ Output file verification failed");
                promise.reject("DECRYPT_FAILED", "Output file verification failed");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Decryption failed: " + e.getMessage(), e);
            promise.reject("DECRYPT_FAILED", "Decryption failed: " + e.getMessage());
        }
    }
    @ReactMethod
    public void encryptDataStreaming(String inputDataBase64, String keyBase64, String ivBase64, Integer chunkSize, Promise promise) {
        try {
            Log.d(TAG, "=== STREAMING ENCRYPTION START ===");
            
            // Convert base64 inputs
            byte[] inputData = Base64.decode(inputDataBase64, Base64.DEFAULT);
            byte[] keyBytes = Base64.decode(keyBase64, Base64.DEFAULT);
            byte[] ivBytes = Base64.decode(ivBase64, Base64.DEFAULT);
            
            if (keyBytes.length != 32) {
                promise.reject("ENCRYPT_FAILED", "Invalid key length");
                return;
            }
            
            if (ivBytes.length != 16) {
                promise.reject("ENCRYPT_FAILED", "Invalid IV length");
                return;
            }
            
            int actualChunkSize = chunkSize != null ? chunkSize : 64 * 1024; // Default 64KB
            
            if (actualChunkSize <= 0) {
                actualChunkSize = 64 * 1024; // Default 64KB
            }
            
            final int AES_BLOCK_SIZE = 16;
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            
            List<String> encryptedChunks = new ArrayList<>();
            int totalLength = inputData.length;
            int totalProcessed = 0;
            
            for (int start = 0; start < totalLength; start += chunkSize) {
                boolean isLastChunk = (start + chunkSize >= totalLength);
                int currentChunkSize = Math.min(chunkSize, totalLength - start);
                
                byte[] chunk = Arrays.copyOfRange(inputData, start, start + currentChunkSize);
                
                Log.d(TAG, String.format("Processing chunk %d, size: %d, isLast: %b", 
                      (start / chunkSize + 1), chunk.length, isLastChunk));
                
                // For non-final chunks, ensure block alignment
                if (!isLastChunk) {
                    int alignedSize = (chunk.length / AES_BLOCK_SIZE) * AES_BLOCK_SIZE;
                    if (alignedSize < chunk.length) {
                        chunk = Arrays.copyOf(chunk, alignedSize);
                        Log.d(TAG, String.format("Aligned chunk to: %d bytes", chunk.length));
                    }
                }
                
                byte[] chunkOutput;
                if (isLastChunk) {
                    // Final chunk
                    chunkOutput = cipher.doFinal(chunk);
                } else {
                    // Regular chunk
                    chunkOutput = cipher.update(chunk);
                }
                
                if (chunkOutput != null && chunkOutput.length > 0) {
                    String chunkBase64 = Base64.encodeToString(chunkOutput, Base64.DEFAULT);
                    encryptedChunks.add(chunkBase64);
                    Log.d(TAG, String.format("Chunk encrypted output size: %d", chunkOutput.length));
                }
                
                totalProcessed += chunk.length;
            }
            
            Log.d(TAG, "✅ Streaming encryption completed");
            Log.d(TAG, String.format("Total chunks processed: %d", encryptedChunks.size()));
            
            WritableMap result = Arguments.createMap();
            WritableArray chunksArray = Arguments.createArray();
            for (String chunk : encryptedChunks) {
                chunksArray.pushString(chunk);
            }
            
            result.putArray("encryptedChunks", chunksArray);
            result.putInt("totalChunks", encryptedChunks.size());
            result.putInt("totalProcessed", totalProcessed);
            
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Streaming encryption failed", e);
            promise.reject("ENCRYPT_FAILED", "Streaming encryption failed: " + e.getMessage());
        }
    }

    @ReactMethod
    public void encryptTextContent(String textContent, String keyBase64, String ivBase64, Integer chunkSize, Promise promise) {
        try {
            Log.d(TAG, "=== TEXT ENCRYPTION START ===");
            Log.d(TAG, "chunkSize: " + (chunkSize != null ? chunkSize : "default"));
            
            if (textContent == null || textContent.isEmpty()) {
                promise.reject("ENCRYPT_FAILED", "Invalid text content");
                return;
            }
            
            // Convert base64 inputs
            byte[] keyBytes = Base64.decode(keyBase64, Base64.DEFAULT);
            byte[] ivBytes = Base64.decode(ivBase64, Base64.DEFAULT);
            
            if (keyBytes.length != 32) {
                promise.reject("ENCRYPT_FAILED", "Invalid key length");
                return;
            }
            
            if (ivBytes.length != 16) {
                promise.reject("ENCRYPT_FAILED", "Invalid IV length");
                return;
            }
            
            // Convert text to bytes
            byte[] textData = textContent.getBytes(StandardCharsets.UTF_8);
            
            Log.d(TAG, "Text data length: " + textData.length);
            
            // Perform AES-256-CBC encryption
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            
            byte[] encryptedBytes = cipher.doFinal(textData);
            
            // Convert to base64 string
            String encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
            
            Log.d(TAG, "✅ Text encryption successful");
            Log.d(TAG, "Original size: " + textData.length);
            Log.d(TAG, "Encrypted size: " + encryptedBytes.length);
            
            promise.resolve(encryptedBase64);
            
        } catch (Exception e) {
            Log.e(TAG, "Text encryption failed", e);
            promise.reject("ENCRYPT_FAILED", "Text encryption failed: " + e.getMessage());
        }
    }

    @ReactMethod
    public void decryptTextContent(String encryptedContentBase64, String keyBase64, String ivBase64, Integer chunkSize, Promise promise) {
        try {
            Log.d(TAG, "=== TEXT DECRYPTION START ===");
            Log.d(TAG, "chunkSize: " + (chunkSize != null ? chunkSize : "default"));
            
            if (encryptedContentBase64 == null || encryptedContentBase64.isEmpty()) {
                promise.reject("DECRYPT_FAILED", "Invalid encrypted content");
                return;
            }
            
            // Convert base64 inputs
            byte[] encryptedData = Base64.decode(encryptedContentBase64, Base64.DEFAULT);
            byte[] keyBytes = Base64.decode(keyBase64, Base64.DEFAULT);
            byte[] ivBytes = Base64.decode(ivBase64, Base64.DEFAULT);
            
            if (keyBytes.length != 32) {
                promise.reject("DECRYPT_FAILED", "Invalid key length");
                return;
            }
            
            if (ivBytes.length != 16) {
                promise.reject("DECRYPT_FAILED", "Invalid IV length");
                return;
            }
            
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedData);
            String decryptedString = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            Log.d(TAG, "✅ Text decryption successful");
            promise.resolve(decryptedString);
            
        } catch (Exception e) {
            Log.e(TAG, "Text decryption failed", e);
            promise.reject("DECRYPT_FAILED", "Text decryption failed: " + e.getMessage());
        }
    }
}
