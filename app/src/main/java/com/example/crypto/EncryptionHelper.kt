package com.example.crypto

import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A highly robust Cryptographic Engine implementing:
 * 1. Hybrid RSA-2048 and AES-256 (CBC with PKCS5Padding) encryption.
 * 2. Secure Base64 encoding for storage safely in Room.
 * 3. Local KeyPair generation for peer-to-peer security validation.
 */
object EncryptionHelper {

    private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private const val AES_ALGORITHM = "AES/CBC/PKCS5Padding"
    private val secureRandom = SecureRandom()

    // Generate a secure AES-256 private key
    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, secureRandom)
        return keyGen.generateKey()
    }

    // Generate a secure RSA-2048 KeyPair (Public & Private keys)
    fun generateRSAKeyPair(): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048, secureRandom)
        return keyPairGen.genKeyPair()
    }

    // Encrypt simple text with AES-256-CBC
    fun encryptAES(plainText: String, secretKey: SecretKey): EncryptedPayload {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val iv = ByteArray(16)
        secureRandom.nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val cipherTextBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return EncryptedPayload(cipherTextBase64, ivBase64)
    }

    // Decrypt AES-256-CBC encrypted payload
    fun decryptAES(cipherTextBase64: String, ivBase64: String, secretKey: SecretKey): String {
        return try {
            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val ivSpec = IvParameterSpec(iv)
            val encryptedBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Error: Decryption Failed (${e.localizedMessage})"
        }
    }

    // Encrypt an AES SecretKey with an RSA Public Key (Hybrid Encryption)
    fun encryptSecretKeyWithRSA(secretKey: SecretKey, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance(RSA_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(secretKey.encoded)
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    // Decrypt an AES SecretKey with an RSA Private Key
    fun decryptSecretKeyWithRSA(encryptedKeyBase64: String, privateKey: PrivateKey): SecretKey {
        val cipher = Cipher.getInstance(RSA_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val encryptedBytes = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
        val decodedKeyBytes = cipher.doFinal(encryptedBytes)
        return SecretKeySpec(decodedKeyBytes, "AES")
    }

    // Convert keys to readable string representations for our "Crypto Inspector" UI
    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun privateKeyToString(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP).take(64) + "..."
    }

    // Helper data class representing symmetric encryption container
    data class EncryptedPayload(
        val cipherText: String,
        val iv: String
    )
}
