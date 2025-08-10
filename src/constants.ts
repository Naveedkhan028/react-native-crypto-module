/**
 * Default chunk size for all operations
 * 1MB (1024 * 1024 bytes) - consistent across all platforms
 */
export const DEFAULT_CHUNK_SIZE = 1048576; // 1MB

/**
 * AES block size in bytes (16 bytes for AES-256-CBC)
 */
export const AES_BLOCK_SIZE = 16;

/**
 * AES key size in bytes (32 bytes for AES-256-CBC)
 */
export const AES_KEY_SIZE = 32;

/**
 * AES IV size in bytes (16 bytes for AES-256-CBC)
 */
export const AES_IV_SIZE = 16;

/**
 * Aligned chunk size ensures that the chunk size is a multiple of the AES block size.
 * This is important for AES encryption/decryption to work correctly.
 */
export const ALIGNED_CHUNK_SIZE = Math.floor(DEFAULT_CHUNK_SIZE / AES_BLOCK_SIZE) * AES_BLOCK_SIZE;