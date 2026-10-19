package com.mailapp.account;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordHasherTest {

    @Test
    public void testSaltGeneration() {
        String salt1 = PasswordHasher.generateSalt();
        String salt2 = PasswordHasher.generateSalt();

        assertNotNull(salt1);
        assertNotNull(salt2);
        assertNotEquals(salt1, salt2);
        assertEquals(32, salt1.length()); // 16 bytes in hex = 32 chars
    }

    @Test
    public void testHashAndVerify() {
        String password = "mySecretPassword123";
        String salt = PasswordHasher.generateSalt();

        String hash = PasswordHasher.hashPassword(password, salt);
        assertNotNull(hash);

        assertTrue(PasswordHasher.verifyPassword(password, salt, hash));
        assertFalse(PasswordHasher.verifyPassword("wrongPassword", salt, hash));
        assertFalse(PasswordHasher.verifyPassword("", salt, hash));
        assertFalse(PasswordHasher.verifyPassword(null, salt, hash));
    }
}
