package com.corvidlabs.algochat.storage

import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.digests.SHA256Digest
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Raised when password is required but not set.
 */
class PasswordRequiredError : Exception("Password is required for file key storage")

/**
 * Raised when decryption fails (wrong password).
 */
class DecryptionFailedError : Exception("Decryption failed - incorrect password or corrupted data")

/**
 * Raised when key data is invalid.
 */
class InvalidKeyDataError : Exception("Invalid key data format")

/**
 * File-based encryption key storage with password protection.
 *
 * Stores X25519 encryption keys encrypted with AES-256-GCM, using a password
 * derived key via PBKDF2. Keys are stored in `~/.algochat/keys/`.
 *
 * ## Storage Format
 *
 * Each key file contains:
 * - Salt: 32 bytes (random, for PBKDF2)
 * - Nonce: 12 bytes (random, for AES-GCM)
 * - Ciphertext: 32 bytes (encrypted private key)
 * - Tag: 16 bytes (authentication tag, included in ciphertext)
 *
 * ## Security
 *
 * - Uses PBKDF2 with 100,000 iterations for key derivation
 * - Uses AES-256-GCM for authenticated encryption
 * - Keys are stored with 600 permissions (owner read/write only)
 * - Salt is unique per key file
 *
 * Example usage:
 * ```kotlin
 * val storage = FileKeyStorage(password = "user-password")
 *
 * // Store a key
 * storage.store(privateKey, "ADDRESS...")
 *
 * // Retrieve
 * val key = storage.retrieve("ADDRESS...")
 * ```
 */
class FileKeyStorage(
    private var password: String? = null
) : EncryptionKeyStorage {

    companion object {
        /** PBKDF2 iteration count (OWASP recommendation for SHA256) */
        const val PBKDF2_ITERATIONS = 100_000

        /** Salt size in bytes */
        const val SALT_SIZE = 32

        /** AES-GCM nonce size in bytes */
        const val NONCE_SIZE = 12

        /** AES-GCM tag size in bits */
        const val TAG_SIZE_BITS = 128

        /** Directory name for key storage */
        const val DIRECTORY_NAME = ".algochat/keys"

        /** Minimum file size (salt + nonce + ciphertext + tag) */
        const val MIN_FILE_SIZE = 32 + 12 + 32 + 16 // 92 bytes
    }

    private val secureRandom = SecureRandom()
    private var cachedDerivedKey: ByteArray? = null
    private var cachedSalt: ByteArray? = null

    /**
     * Set the password for encryption/decryption.
     *
     * @param newPassword The password to use.
     */
    fun setPassword(newPassword: String) {
        password = newPassword
        cachedDerivedKey = null
        cachedSalt = null
    }

    /**
     * Clear the password and cached keys from memory.
     */
    fun clearPassword() {
        password = null
        cachedDerivedKey = null
        cachedSalt = null
    }

    override suspend fun store(
        privateKey: ByteArray,
        address: String,
        requireBiometric: Boolean
    ) {
        val pwd = password ?: throw PasswordRequiredError()

        // Ensure directory exists
        val directory = ensureDirectory()

        // Generate random salt and nonce
        val salt = ByteArray(SALT_SIZE).also { secureRandom.nextBytes(it) }
        val nonce = ByteArray(NONCE_SIZE).also { secureRandom.nextBytes(it) }

        // Derive encryption key from password
        val derivedKey = deriveKey(pwd, salt)

        // Encrypt the private key with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(derivedKey, "AES")
        val gcmSpec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(privateKey)

        // Combine: salt + nonce + ciphertext (includes tag)
        val fileData = salt + nonce + ciphertext

        // Write to file
        val filePath = keyFilePath(address, directory)
        filePath.writeBytes(fileData)

        // Set restrictive permissions (owner read/write only)
        setRestrictivePermissions(filePath)
    }

    override suspend fun retrieve(address: String): ByteArray {
        val pwd = password ?: throw PasswordRequiredError()

        val directory = getDirectory()
        val filePath = keyFilePath(address, directory)

        // Check if file exists
        if (!filePath.exists()) {
            throw KeyNotFoundError(address)
        }

        // Read the encrypted file
        val fileData = filePath.readBytes()

        // Validate minimum size
        if (fileData.size < MIN_FILE_SIZE) {
            throw InvalidKeyDataError()
        }

        // Parse: salt + nonce + ciphertext (includes tag)
        val salt = fileData.copyOfRange(0, SALT_SIZE)
        val nonce = fileData.copyOfRange(SALT_SIZE, SALT_SIZE + NONCE_SIZE)
        val ciphertext = fileData.copyOfRange(SALT_SIZE + NONCE_SIZE, fileData.size)

        // Derive decryption key from password
        val derivedKey = deriveKey(pwd, salt)

        // Decrypt
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(derivedKey, "AES")
            val gcmSpec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw DecryptionFailedError()
        }
    }

    override suspend fun hasKey(address: String): Boolean {
        val directory = getDirectory()
        val filePath = keyFilePath(address, directory)
        return filePath.exists()
    }

    override suspend fun delete(address: String) {
        val directory = getDirectory()
        val filePath = keyFilePath(address, directory)
        if (filePath.exists()) {
            filePath.delete()
        }
    }

    override suspend fun listStoredAddresses(): List<String> {
        val directory = getDirectory()
        if (!directory.exists()) {
            return emptyList()
        }

        return directory.listFiles()
            ?.filter { it.extension == "key" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    private fun getDirectory(): File {
        val homeDir = System.getProperty("user.home")
        return File(homeDir, DIRECTORY_NAME)
    }

    private fun ensureDirectory(): File {
        val directory = getDirectory()
        if (!directory.exists()) {
            directory.mkdirs()
        }
        // Try to set directory permissions to 700 (owner only)
        try {
            val path = directory.toPath()
            val perms = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            )
            Files.setPosixFilePermissions(path, perms)
        } catch (_: Exception) {
            // Ignore permission errors on some platforms
        }
        return directory
    }

    private fun keyFilePath(address: String, directory: File): File {
        return File(directory, "$address.key")
    }

    private fun deriveKey(pwd: String, salt: ByteArray): ByteArray {
        // Check cache
        if (cachedDerivedKey != null && cachedSalt?.contentEquals(salt) == true) {
            return cachedDerivedKey!!
        }

        // PBKDF2 with SHA256
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init(
            pwd.toByteArray(Charsets.UTF_8),
            salt,
            PBKDF2_ITERATIONS
        )
        val keyParam = generator.generateDerivedParameters(256) as KeyParameter
        val derivedKey = keyParam.key

        // Cache for this salt
        cachedDerivedKey = derivedKey
        cachedSalt = salt

        return derivedKey
    }

    private fun setRestrictivePermissions(file: File) {
        try {
            val path = file.toPath()
            val perms = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            )
            Files.setPosixFilePermissions(path, perms)
        } catch (_: Exception) {
            // Ignore permission errors on some platforms
        }
    }
}
