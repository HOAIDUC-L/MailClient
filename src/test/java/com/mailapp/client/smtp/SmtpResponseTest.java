package com.mailapp.client.smtp;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SmtpResponseTest {

    @Test
    public void testSingleLineResponse() {
        SmtpResponse resp = new SmtpResponse(250, "OK", List.of("250 OK"));
        assertEquals(250, resp.getCode());
        assertEquals("OK", resp.getMessage());
        assertTrue(resp.isSuccess());
        assertTrue(resp.isPositiveCompletion());
        assertFalse(resp.isPermanentNegative());
    }

    @Test
    public void testMultiLineResponse() {
        SmtpResponse resp = new SmtpResponse(250, "mail.example.com\nAUTH LOGIN PLAIN\n8BITMIME\nOK",
                List.of("250-mail.example.com", "250-AUTH LOGIN PLAIN", "250-8BITMIME", "250 OK"));

        assertEquals(250, resp.getCode());
        assertTrue(resp.getMessage().contains("AUTH LOGIN PLAIN"));
        assertEquals(4, resp.getRawLines().size());
        assertTrue(resp.isSuccess());
    }

    @Test
    public void testErrorResponses() {
        SmtpResponse tempErr = new SmtpResponse(451, "Local error", List.of("451 Local error"));
        assertTrue(tempErr.isTransientNegative());
        assertFalse(tempErr.isSuccess());

        SmtpResponse permErr = new SmtpResponse(550, "User not found", List.of("550 User not found"));
        assertTrue(permErr.isPermanentNegative());
        assertFalse(permErr.isSuccess());
    }
}
