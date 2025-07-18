
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
    public void decryptFile(String inputUri, String outputUri, String keyBase64, String ivBase64, Promise promise) {
        try {
            Log.d(TAG, "=== NATIVE MODULE DEBUG ===");
            Log.d(TAG, "inputUri: " + inputUri);
            Log.d(TAG, "outputUri: " + outputUri);
            Log.d(TAG, "keyBase64 length: " + keyBase64.length());
            Log.d(TAG, "ivBase64 length: " + ivBase64.length());
            
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
}