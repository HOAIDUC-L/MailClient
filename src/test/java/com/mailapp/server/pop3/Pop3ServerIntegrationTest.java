package com.mailapp.server.pop3;

import com.mailapp.account.AccountManager;
import com.mailapp.client.pop3.Pop3Client;
import com.mailapp.client.pop3.Pop3Response;
import com.mailapp.model.EmailMessage;
import com.mailapp.model.MailServerConfig;
import com.mailapp.storage.FileMailStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class Pop3ServerIntegrationTest {

    private Pop3Server server;
    private FileMailStorage storage;
    private AccountManager accountManager;
    private int serverPort;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws IOException {
        MailServerConfig config = new MailServerConfig();
        config.setPop3Host("127.0.0.1");
        config.setPop3Port(0); // Dynamic ephemeral port

        accountManager = new AccountManager(tempDir.resolve("accounts.json"));
        storage = new FileMailStorage(tempDir.resolve("mailboxes"));
        storage.init();

        // Seed 2 emails for 'lan'
        storage.saveEmail("lan", "From: duc@localhost\r\nSubject: Mail One\r\n\r\nBody One\r\n.Dot Line");
        storage.saveEmail("lan", "From: duc@localhost\r\nSubject: Mail Two\r\n\r\nBody Two");

        server = new Pop3Server(config, storage, accountManager);
        server.start();
        serverPort = server.getPort();
        assertTrue(server.isRunning());
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testAuthenticationSuccessAndFailure() throws IOException {
        try (Pop3Client client = new Pop3Client()) {
            client.connect("127.0.0.1", serverPort, 5000, false);

            // Bad password
            Pop3Response badAuth = client.login("lan", "wrongPassword");
            assertFalse(badAuth.isOk());

            // Good password
            Pop3Response goodAuth = client.login("lan", "lan123");
            assertTrue(goodAuth.isOk());

            client.quit();
        }
    }

    @Test
    public void testStatListUidlRetr() throws IOException {
        try (Pop3Client client = new Pop3Client()) {
            client.connect("127.0.0.1", serverPort, 5000, false);
            client.login("lan", "lan123");

            // STAT
            long[] stat = client.stat();
            assertEquals(2, stat[0]);
            assertTrue(stat[1] > 0);

            // LIST
            var list = client.list();
            assertEquals(2, list.size());
            assertEquals(1, list.get(0).getMessageNumber());
            assertEquals(2, list.get(1).getMessageNumber());

            // UIDL
            var uidls = client.uidl();
            assertEquals(2, uidls.size());
            assertNotNull(uidls.get(1));
            assertNotNull(uidls.get(2));

            // RETR message 1
            EmailMessage msg1 = client.retrieveMessage(1);
            assertEquals("Mail One", msg1.getSubject());
            assertTrue(msg1.getBody().contains(".Dot Line"));

            client.quit();
        }
    }

    @Test
    public void testDeleAndRsetDoesNotDelete() throws IOException {
        try (Pop3Client client = new Pop3Client()) {
            client.connect("127.0.0.1", serverPort, 5000, false);
            client.login("lan", "lan123");

            Pop3Response deleResp = client.dele(1);
            assertTrue(deleResp.isOk());

            // STAT should now report 1 active message
            long[] statAfterDele = client.stat();
            assertEquals(1, statAfterDele[0]);

            // RSET to unmark
            Pop3Response rsetResp = client.rset();
            assertTrue(rsetResp.isOk());

            // STAT should report 2 active messages again
            long[] statAfterRset = client.stat();
            assertEquals(2, statAfterRset[0]);

            client.quit();
        }

        // Verify storage still has both messages
        assertEquals(2, storage.getMessageCount("lan"));
    }

    @Test
    public void testAbruptDisconnectDoesNotDeleteMarkedEmails() throws IOException {
        try (Pop3Client client = new Pop3Client()) {
            client.connect("127.0.0.1", serverPort, 5000, false);
            client.login("lan", "lan123");

            client.dele(1);
            // Close connection without QUIT (abrupt disconnect)
            client.close();
        }

        // Verify storage still has both messages because QUIT (UPDATE state) was never executed!
        assertEquals(2, storage.getMessageCount("lan"));
    }

    @Test
    public void testDeleWithQuitCommitsDeletion() throws IOException {
        try (Pop3Client client = new Pop3Client()) {
            client.connect("127.0.0.1", serverPort, 5000, false);
            client.login("lan", "lan123");

            client.dele(1);
            Pop3Response quitResp = client.quit();
            assertTrue(quitResp.isOk());
        }

        // Verify storage now has only 1 message
        assertEquals(1, storage.getMessageCount("lan"));
    }

    @Test
    public void testExclusiveMailboxLocking() throws IOException {
        Pop3Client client1 = new Pop3Client();
        client1.connect("127.0.0.1", serverPort, 5000, false);
        Pop3Response login1 = client1.login("lan", "lan123");
        assertTrue(login1.isOk());

        // Second client tries to open the same mailbox 'lan'
        try (Pop3Client client2 = new Pop3Client()) {
            client2.connect("127.0.0.1", serverPort, 5000, false);
            Pop3Response login2 = client2.login("lan", "lan123");
            assertFalse(login2.isOk(), "Second concurrent login must be rejected due to mailbox lock");
            assertTrue(login2.getMessage().contains("locked") || login2.getMessage().contains("IN-USE"));
        }

        // When client 1 signs off, lock is released
        client1.quit();

        // Now client 2 can connect and login
        try (Pop3Client client2 = new Pop3Client()) {
            client2.connect("127.0.0.1", serverPort, 5000, false);
            Pop3Response login2 = client2.login("lan", "lan123");
            assertTrue(login2.isOk(), "Login should succeed after first client releases mailbox lock");
            client2.quit();
        }
    }
}
