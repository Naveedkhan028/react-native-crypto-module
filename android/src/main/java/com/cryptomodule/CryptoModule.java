package com.cryptomodule;
import android.util.Log;
import android.util.Base64;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.Callback;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;
import fi.iki.elonen.NanoHTTPD;

public class CryptoModule extends ReactContextBaseJavaModule {
    private static final String TAG = "CryptoModule";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private StreamingHTTPServer httpServer;
    
    public CryptoModule(ReactApplicationContext reactContext) {
        super(reactContext);
        
        // Start HTTP server on initialization
        try {
            httpServer = new StreamingHTTPServer(0); // 0 = random available port
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.d(TAG, "🌐 NanoHTTPD server started on port: " + httpServer.getListeningPort());
        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to start HTTP server: " + e.getMessage());
        }
    }
    
    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        if (httpServer != null) {
            httpServer.stop();
            Log.d(TAG, "🛑 HTTP server stopped");
        }
    }
    
    // ✅ Inner class: HTTP server for streaming decrypted content
    private class StreamingHTTPServer extends NanoHTTPD {
        private Map<String, StreamConfig> activeStreams = new HashMap<>();
        
        public StreamingHTTPServer(int port) {
            super(port);
        }
        
        public void registerStream(String streamId, StreamConfig config) {
            activeStreams.put(streamId, config);
            Log.d(TAG, "✅ Registered stream: " + streamId);
        }
        
        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            String streamId = uri.substring(1); // Remove leading "/"
            
            Log.d(TAG, "🎬 HTTP request received for stream: " + streamId);
            
            StreamConfig config = activeStreams.get(streamId);
            if (config == null) {
                Log.e(TAG, "❌ Stream not found: " + streamId);
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not found");
            }
            
            try {
                // Download and decrypt the video
                byte[] decryptedData = downloadAndDecrypt(config);
                
                if (decryptedData == null) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Decryption failed");
                }
                
                Log.d(TAG, "📤 Serving " + decryptedData.length + " bytes to video player");
                
                Response response = newFixedLengthResponse(Response.Status.OK, "video/mp4", 
                    new java.io.ByteArrayInputStream(decryptedData), decryptedData.length);
                response.addHeader("Accept-Ranges", "bytes");
                response.addHeader("Access-Control-Allow-Origin", "*");
                
                return response;
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error serving stream: " + e.getMessage());
                e.printStackTrace();
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, 
                    "Error: " + e.getMessage());
            }
        }
        
        private byte[] downloadAndDecrypt(StreamConfig config) {
            try {
                Log.d(TAG, "📥 Downloading from: " + config.inputUri);
                
                // Setup HTTP connection
                URL url = new URL(config.inputUri);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                if (config.token != null && !config.token.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + config.token);
                }
                
                InputStream inputStream = connection.getInputStream();
                
                // Setup decryption
                byte[] keyBytes = Base64.decode(config.keyBase64, Base64.DEFAULT);
                byte[] ivBytes = Base64.decode(config.ivBase64, Base64.DEFAULT);
                
                SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
                IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
                
                // Read and decrypt in chunks
                ByteArrayOutputStream decryptedOutput = new ByteArrayOutputStream();
                byte[] buffer = new byte[16 * 1024]; // 16KB chunks
                int bytesRead;
                long totalDownloaded = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    totalDownloaded += bytesRead;
                    
                    // Decrypt chunk
                    byte[] chunkToDecrypt = Arrays.copyOf(buffer, bytesRead);
                    byte[] decryptedChunk = cipher.update(chunkToDecrypt);
                    
                    if (decryptedChunk != null && decryptedChunk.length > 0) {
                        decryptedOutput.write(decryptedChunk);
                    }
                    
                    if (totalDownloaded % (1024 * 1024) == 0) { // Log every 1MB
                        Log.d(TAG, "📥 Downloaded and decrypted: " + (totalDownloaded / 1024) + " KB");
                    }
                }
                
                // Finalize decryption (handle padding)
                byte[] finalBytes = cipher.doFinal();
                if (finalBytes != null && finalBytes.length > 0) {
                    decryptedOutput.write(finalBytes);
                }
                
                inputStream.close();
                connection.disconnect();
                
                Log.d(TAG, "✅ Total decrypted: " + decryptedOutput.size() + " bytes");
                
                return decryptedOutput.toByteArray();
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Download and decrypt failed: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
    }
    
    // ✅ Stream configuration holder
    private static class StreamConfig {
        String inputUri;
        String keyBase64;
        String ivBase64;
        String token;
        
        StreamConfig(String inputUri, String keyBase64, String ivBase64, String token) {
            this.inputUri = inputUri;
            this.keyBase64 = keyBase64;
            this.ivBase64 = ivBase64;
            this.token = token;
        }
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
    
    // ✅ NEW: Start progressive streaming via local HTTP server
    @ReactMethod
    public void decryptFileViaHTTPServer(String inputUri, String keyBase64, String ivBase64, String token, Promise promise) {
        try {
            Log.d(TAG, "=== DECRYPT VIA HTTP SERVER START ===");
            Log.d(TAG, "inputUri: " + inputUri);
            
            if (httpServer == null || !httpServer.isAlive()) {
                promise.reject("SERVER_NOT_RUNNING", "HTTP server is not running");
                return;
            }
            
            // Generate unique stream ID
            String streamId = UUID.randomUUID().toString();
            String localURL = "http://localhost:" + httpServer.getListeningPort() + "/" + streamId;
            
            Log.d(TAG, "🌐 Stream will be available at: " + localURL);
            
            // Register stream configuration
            StreamConfig config = new StreamConfig(inputUri, keyBase64, ivBase64, token);
            httpServer.registerStream(streamId, config);
            
            // Resolve with local HTTP URL
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putString("localURL", localURL);
            result.putString("streamId", streamId);
            
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create stream: " + e.getMessage());
            promise.reject("STREAM_CREATION_FAILED", e.getMessage());
        }
    }
    
    @ReactMethod
    public void decryptFile(String inputUri, String outputUri, String keyBase64, String ivBase64, int chunkSize, Promise promise) {
        try {
            Log.d(TAG, "=== NATIVE MODULE DEBUG ===");
            Log.d(TAG, "inputUri: " + inputUri);
            Log.d(TAG, "outputUri: " + outputUri);
            Log.d(TAG, "keyBase64 length: " + keyBase64.length());
            Log.d(TAG, "ivBase64 length: " + ivBase64.length());
            Log.d(TAG, "chunkSize: " + chunkSize);
            
            // Set default chunk size if not provided
            if (chunkSize <= 0) {
                chunkSize = 1024 * 1024; // Default 1MB
            }
            
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
    public void encryptDataStreaming(String inputDataBase64, String keyBase64, String ivBase64, int chunkSize, Promise promise) {
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
            
            if (chunkSize <= 0) {
                chunkSize = 1024 * 1024; // Default 1MB to match JavaScript
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
    public void encryptTextContent(String textContent, String keyBase64, String ivBase64, int chunkSize, Promise promise) {
        try {
            Log.d(TAG, "=== TEXT ENCRYPTION START ===");
            Log.d(TAG, "chunkSize: " + chunkSize);
            
            if (textContent == null || textContent.isEmpty()) {
                promise.reject("ENCRYPT_FAILED", "Invalid text content");
                return;
            }
            
            // Set default chunk size if not provided
            if (chunkSize <= 0) {
                chunkSize = 1024 * 1024; // Default 1MB
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
    public void decryptTextContent(String encryptedContentBase64, String keyBase64, String ivBase64, int chunkSize, Promise promise) {
        try {
            Log.d(TAG, "=== TEXT DECRYPTION START ===");
            Log.d(TAG, "chunkSize: " + chunkSize);
            
            if (encryptedContentBase64 == null || encryptedContentBase64.isEmpty()) {
                promise.reject("DECRYPT_FAILED", "Invalid encrypted content");
                return;
            }
            
            // Set default chunk size if not provided
            if (chunkSize <= 0) {
                chunkSize = 1024 * 1024; // Default 1MB
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
    // ✅ COMPLETE: Progressive streaming decryption with immediate chunk processing (matching iOS)
    @ReactMethod
    public void decryptFileWithStreaming(String inputUri, String outputUri, String keyBase64, String ivBase64, String token, int chunkSize, Promise promise) {
        try {
            Log.d(TAG, "=== STREAMING DECRYPTION START ===");
            Log.d(TAG, "inputUri: " + inputUri);
            Log.d(TAG, "outputUri: " + outputUri);
            Log.d(TAG, "chunkSize: " + chunkSize);
            
            // Set default chunk size if not provided
            if (chunkSize <= 0) {
                chunkSize = 1024 * 1024; // Default 1MB
            }
            
            // ✅ Force AES block alignment (16 bytes)
            chunkSize = (chunkSize / 16) * 16;
            Log.d(TAG, "chunkSize (aligned): " + chunkSize);
            
            // Convert file URIs to local paths
            String inputPath = convertFileUriToPath(inputUri);
            String outputPath = convertFileUriToPath(outputUri);
            
            // Convert base64 keys early
            byte[] keyBytes = Base64.decode(keyBase64, Base64.DEFAULT);
            byte[] ivBytes = Base64.decode(ivBase64, Base64.DEFAULT);
            
            if (keyBytes.length != 32) {
                promise.reject("DECRYPT_FAILED", "Invalid key data length: " + keyBytes.length);
                return;
            }
            
            if (ivBytes.length != 16) {
                promise.reject("DECRYPT_FAILED", "Invalid IV data length: " + ivBytes.length);
                return;
            }
            
            // ✅ Progressive streaming for HTTP URLs (matches iOS NSURLSessionDataDelegate)
            if (inputUri.startsWith("http")) {
                Log.d(TAG, "🚀 Starting progressive streaming from: " + inputUri);
                
                // Create output directory first
                File outputFile = new File(outputPath);
                File outputDir = outputFile.getParentFile();
                if (outputDir != null && !outputDir.exists()) {
                    if (!outputDir.mkdirs()) {
                        promise.reject("DECRYPT_FAILED", "Failed to create output directory");
                        return;
                    }
                }
                
                // Create output stream immediately
                FileOutputStream outputStream = new FileOutputStream(outputFile);
                
                // Create cipher for progressive decryption
                SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
                IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
                
                Log.d(TAG, "✅ Cipher created for AES-CBC streaming decryption");
                
                // Setup HTTP connection with progressive chunk reception
                URL url = new URL(inputUri);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                if (token != null && !token.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                
                InputStream inputStream = connection.getInputStream();
                
                // ✅ Allocate decryption buffer
                int bufferSize = chunkSize + 16; // Extra space for AES blocks
                byte[] inputBuffer = new byte[16 * 1024]; // 16KB network chunks (matches iOS)
                byte[] outputBuffer = new byte[bufferSize];
                
                long totalDownloaded = 0;
                int bytesRead;
                
                Log.d(TAG, "📡 Receiving and decrypting data progressively...");
                
                try {
                    // ✅ Read network chunks and decrypt immediately as they arrive
                    while ((bytesRead = inputStream.read(inputBuffer)) != -1) {
                        totalDownloaded += bytesRead;
                        
                        Log.d(TAG, String.format("📥 Received chunk: %d bytes, total so far: %d bytes", 
                            bytesRead, totalDownloaded));
                        
                        // ✅ Decrypt this chunk immediately using cipher.update()
                        int outputLength = cipher.update(inputBuffer, 0, bytesRead, outputBuffer);
                        
                        if (outputLength > 0) {
                            // ✅ Write decrypted data immediately
                            outputStream.write(outputBuffer, 0, outputLength);
                            
                            // ✅ CRITICAL: Force flush so JavaScript FileSystem.getInfoAsync() 
                            // sees the file growth immediately (matches iOS streamStatus)
                            outputStream.flush();
                            outputStream.getFD().sync(); // Force OS-level sync
                            
                            Log.d(TAG, String.format("✅ Decrypted and wrote: %d bytes (flushed)", outputLength));
                        }
                    }
                    
                    // ✅ Finalize decryption (handle padding removal)
                    int finalLength = cipher.doFinal(outputBuffer, 0);
                    if (finalLength > 0) {
                        outputStream.write(outputBuffer, 0, finalLength);
                        outputStream.flush();
                        Log.d(TAG, String.format("✅ Final padding removal: %d bytes", finalLength));
                    }
                    
                    Log.d(TAG, String.format("✅ Total downloaded and decrypted: %d bytes", totalDownloaded));
                    
                } finally {
                    inputStream.close();
                    outputStream.close();
                    connection.disconnect();
                }
                
                Log.d(TAG, "✅ Progressive streaming completed successfully!");
                
                // Verify output file
                if (outputFile.exists()) {
                    WritableMap result = Arguments.createMap();
                    result.putBoolean("success", true);
                    result.putString("localPath", outputUri);
                    result.putDouble("size", outputFile.length());
                    promise.resolve(result);
                } else {
                    promise.reject("DECRYPT_FAILED", "Output file verification failed");
                }
                return;
            }
            
            // ✅ For local files - standard streaming decryption
            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                Log.e(TAG, "Input file does not exist at path: " + inputPath);
                promise.reject("DECRYPT_FAILED", "Input file does not exist: " + inputPath);
                return;
            }
            
            // Create output directory if needed
            File outputFile = new File(outputPath);
            File outputDir = outputFile.getParentFile();
            if (outputDir != null && !outputDir.exists()) {
                if (!outputDir.mkdirs()) {
                    promise.reject("DECRYPT_FAILED", "Failed to create output directory");
                    return;
                }
            }
            
            // Create empty output file for polling detection
            new FileOutputStream(outputFile).close();
            Log.d(TAG, "✅ Created empty output file for streaming: " + outputPath);
            
            // ✅ Streaming decryption with proper padding handling
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            
            FileInputStream fis = new FileInputStream(inputFile);
            FileOutputStream fos = new FileOutputStream(outputFile);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            
            byte[] inputBuffer = new byte[chunkSize];
            byte[] outputBuffer = new byte[chunkSize + 16];
            
            long totalBytes = inputFile.length();
            long processedBytes = 0;
            
            Log.d(TAG, "Starting streaming decryption, total size: " + totalBytes);
            
            int bytesRead;
            
            while ((bytesRead = fis.read(inputBuffer)) != -1) {
                processedBytes += bytesRead;
                boolean isLastChunk = (processedBytes >= totalBytes);
                
                Log.d(TAG, String.format("Processing chunk: %d bytes, total: %d/%d, isLast: %b", 
                    bytesRead, processedBytes, totalBytes, isLastChunk));
                
                int outputLength;
                
                if (isLastChunk) {
                    // ✅ Final chunk - handle padding removal with doFinal
                    outputLength = cipher.doFinal(inputBuffer, 0, bytesRead, outputBuffer);
                    Log.d(TAG, "Final chunk decrypted: " + outputLength + " bytes");
                } else {
                    // ✅ Intermediate chunk - use update
                    outputLength = cipher.update(inputBuffer, 0, bytesRead, outputBuffer);
                    Log.d(TAG, "Intermediate chunk decrypted: " + outputLength + " bytes");
                }
                
                if (outputLength > 0) {
                    fos.write(outputBuffer, 0, outputLength);
                }
            }
            
            fis.close();
            fos.close();
            
            Log.d(TAG, "✅ Streaming decryption completed successfully");
            
            // Verify output file
            if (outputFile.exists()) {
                WritableMap result = Arguments.createMap();
                result.putBoolean("success", true);
                result.putString("localPath", outputUri);
                result.putDouble("size", outputFile.length());
                promise.resolve(result);
            } else {
                promise.reject("DECRYPT_FAILED", "Output file verification failed");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Streaming decryption failed: " + e.getMessage(), e);
            e.printStackTrace();
            promise.reject("DECRYPT_FAILED", "Streaming decryption failed: " + e.getMessage());
        }
    }
}
