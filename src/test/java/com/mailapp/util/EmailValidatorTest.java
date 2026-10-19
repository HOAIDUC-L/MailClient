package com.mailapp.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EmailValidatorTest {

    @Test
    public void testCrlfInjectionDetection() {
        assertFalse(EmailValidator.containsCrlf("Normal Subject Text"));
        assertTrue(EmailValidator.containsCrlf("Subject\r\nBcc: evil@hacker.com"));
        assertTrue(EmailValidator.containsCrlf("Subject\nBcc: evil@hacker.com"));
        assertTrue(EmailValidator.containsCrlf("Subject\rBcc: evil@hacker.com"));

        assertThrows(IllegalArgumentException.class, () ->
                EmailValidator.validateHeaderNoCrlf("Subject", "Bad\r\nHeader"));
    }

    @Test
    public void testExtractCleanAddressAndUsername() {
        assertEquals("duc@localhost", EmailValidator.extractCleanAddress("<duc@localhost>"));
        assertEquals("duc@localhost", EmailValidator.extractCleanAddress("Duc <duc@localhost>"));
        assertEquals("lan", EmailValidator.extractCleanAddress("lan"));

        assertEquals("duc", EmailValidator.extractUsername("duc@localhost"));
        assertEquals("lan", EmailValidator.extractUsername("<lan@127.0.0.1>"));
        assertEquals("admin", EmailValidator.extractUsername("Admin"));
    }

    @Test
    public void testPathTraversalPrevention() {
        assertTrue(EmailValidator.isValidUsername("duc"));
        assertTrue(EmailValidator.isValidUsername("lan.user-01_test"));

        // Path traversal attempts must be rejected
        assertFalse(EmailValidator.isValidUsername("../etc/passwd"));
        assertFalse(EmailValidator.isValidUsername("..\\windows\\system32"));
        assertFalse(EmailValidator.isValidUsername("duc/subfolder"));
        assertFalse(EmailValidator.isValidUsername("duc\\subfolder"));
        assertFalse(EmailValidator.isValidUsername(".."));
        assertFalse(EmailValidator.isValidUsername(""));
        assertFalse(EmailValidator.isValidUsername(null));
    }
}
