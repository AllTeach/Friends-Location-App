package com.example.crypto;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A highly robust Cryptographic Engine implemented in 100% pure Java, implementing:
 * 1. Hybrid RSA-2048 and AES-256 (CBC with PKCS5Padding) encryption.
 * 2. Secure Base64 encoding for safe database or view output.
 * 3. Local KeyPair generation for peer-to-peer security validation.
 */
public class EncryptionHelper {

    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final SecureRandom secureRandom = new SecureRandom();

    // Generate a secure AES-256 private key
    public static SecretKey generateAESKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("AES Key Generation failed", e);
        }
    }

    // Generate a secure RSA-2048 KeyPair (Public & Private keys)
    public static KeyPair generateRSAKeyPair() {
        try {
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
            keyPairGen.initialize(2048, secureRandom);
            return keyPairGen.genKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("RSA Key Generation failed", e);
        }
    }

    // Encrypt simple text with AES-256-CBC
    public static EncryptedPayload encryptAES(String plainText, SecretKey secretKey) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            String cipherTextBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
            String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);

            return new EncryptedPayload(cipherTextBase64, ivBase64);
        } catch (Exception e) {
            throw new RuntimeException("AES Encryption failed", e);
        }
    }

    // Decrypt AES-256-CBC encrypted payload
    public static String decryptAES(String cipherTextBase64, String ivBase64, SecretKey secretKey) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            byte[] encryptedBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Error: Decryption Failed (" + e.getLocalizedMessage() + ")";
        }
    }

    // Encrypt an AES SecretKey with an RSA Public Key (Hybrid Encryption)
    public static String encryptSecretKeyWithRSA(SecretKey secretKey, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(secretKey.getEncoded());
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("RSA Key Encryption failed", e);
        }
    }

    // Decrypt an AES SecretKey with an RSA Private Key
    public static SecretKey decryptSecretKeyWithRSA(String encryptedKeyBase64, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] encryptedBytes = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP);
            byte[] decodedKeyBytes = cipher.doFinal(encryptedBytes);
            return new SecretKeySpec(decodedKeyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("RSA Key Decryption failed", e);
        }
    }

    // Convert keys to readable string representations for our "Crypto Inspector" UI
    public static String publicKeyToString(PublicKey publicKey) {
        return Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
    }

    public static String privateKeyToString(PrivateKey privateKey) {
        String base64 = Base64.encodeToString(privateKey.getEncoded(), Base64.NO_WRAP);
        if (base64.length() > 64) {
            return base64.substring(0, 64) + "...";
        }
        return base64;
    }

    // Helper class representing symmetric encryption container
    public static class EncryptedPayload {
        public final String cipherText;
        public final String iv;

        public EncryptedPayload(String cipherText, String iv) {
            this.cipherText = cipherText;
            this.iv = iv;
        }
    }
}
