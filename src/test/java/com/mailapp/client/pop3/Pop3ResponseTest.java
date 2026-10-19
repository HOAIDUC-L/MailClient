package com.mailapp.client.pop3;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Pop3ResponseTest {

    @Test
    public void testOkResponse() {
        Pop3Response resp = new Pop3Response(true, "Mailbox ready (2 messages)");
        assertTrue(resp.isOk());
        assertEquals("Mailbox ready (2 messages)", resp.getMessage());
        assertTrue(resp.toString().startsWith("+OK"));
    }

    @Test
    public void testErrResponse() {
        Pop3Response resp = new Pop3Response(false, "Authentication failed");
        assertFalse(resp.isOk());
        assertEquals("Authentication failed", resp.getMessage());
        assertTrue(resp.toString().startsWith("-ERR"));
    }

    @Test
    public void testMultiLineResponse() {
        List<String> lines = List.of("1 1024", "2 2048");
        Pop3Response resp = new Pop3Response(true, "2 messages (3072 octets)", lines);
        assertTrue(resp.isOk());
        assertEquals(2, resp.getDataLines().size());
        assertEquals("1 1024", resp.getDataLines().get(0));
    }
}
