package com.mailapp.account;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

/**
 * Utility for hashing and verifying passwords using PBKDF2WithHmacSHA256.
 */
public class PasswordHasher {

    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    /**
     * Generates a cryptographically secure random salt in hex string format.
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Hashes the password with the given salt.
     */
    public static String hashPassword(String password, String saltHex) {
        if (password == null || saltHex == null) {
            throw new IllegalArgumentException("Password and salt must not be null");
        }
        byte[] salt = HexFormat.of().parseHex(saltHex);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error hashing password", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Verifies if a raw password matches the expected hash using constant-time comparison.
     */
    public static boolean verifyPassword(String password, String saltHex, String expectedHash) {
        if (password == null || saltHex == null || expectedHash == null) {
            return false;
        }
        String actualHash = hashPassword(password, saltHex);
        return MessageDigest.isEqual(
                HexFormat.of().parseHex(actualHash),
                HexFormat.of().parseHex(expectedHash)
        );
    }
}
