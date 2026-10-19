package com.mailapp.server.smtp;

import com.mailapp.account.AccountManager;
import com.mailapp.client.smtp.SmtpClient;
import com.mailapp.client.smtp.SmtpResponse;
import com.mailapp.model.EmailMessage;
import com.mailapp.model.MailServerConfig;
import com.mailapp.storage.FileMailStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class SmtpServerIntegrationTest {

    private SmtpServer server;
    private FileMailStorage storage;
    private AccountManager accountManager;
    private int serverPort;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws IOException {
        MailServerConfig config = new MailServerConfig();
        config.setSmtpHost("127.0.0.1");
        config.setSmtpPort(0); // Dynamic ephemeral port

        accountManager = new AccountManager(tempDir.resolve("accounts.json"));
        storage = new FileMailStorage(tempDir.resolve("mailboxes"));
        storage.init();

        server = new SmtpServer(config, storage, accountManager);
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
    public void testSmtpProtocolInteractionAndDelivery() throws IOException {
        try (SmtpClient client = new SmtpClient()) {
            SmtpResponse greeting = client.connect("127.0.0.1", serverPort, 5000, false);
            assertEquals(220, greeting.getCode());

            SmtpResponse ehlo = client.sendEhlo("localhost");
            assertTrue(ehlo.isSuccess());

            // Test bad sequence: RCPT before MAIL
            SmtpResponse badRcpt = client.sendCommand("RCPT TO:<lan@localhost>");
            assertEquals(503, badRcpt.getCode());

            // Test MAIL FROM
            SmtpResponse fromResp = client.sendMailFrom("duc@localhost");
            assertTrue(fromResp.isSuccess());

            // Test non-existent user rejected with 550
            SmtpResponse notFoundResp = client.sendRcptTo("unknown_user_999@localhost");
            assertEquals(550, notFoundResp.getCode(), "Non-existent user must be rejected with 550");

            // Test existing user 'lan' accepted
            SmtpResponse validRcpt = client.sendRcptTo("lan@localhost");
            assertTrue(validRcpt.isSuccess());

            // Send DATA
            String testEml = "From: duc@localhost\r\n" +
                    "To: lan@localhost\r\n" +
                    "Subject: Integration Test\r\n" +
                    "\r\n" +
                    "Hello Lan,\r\n" +
                    ".Dotted line\r\n" +
                    "Bye!";

            SmtpResponse dataResp = client.sendData(testEml);
            assertTrue(dataResp.isSuccess());
            assertEquals(250, dataResp.getCode());

            // Send QUIT
            SmtpResponse quitResp = client.sendQuit();
            assertEquals(221, quitResp.getCode());
        }

        // Verify storage received email
        assertEquals(1, storage.getMessageCount("lan"));
        var msgs = storage.getMailboxMessages("lan");
        assertEquals(1, msgs.size());
        assertEquals("Integration Test", msgs.get(0).getSubject());

        String savedRaw = storage.readEmailRaw("lan", msgs.get(0).getUidl());
        assertTrue(savedRaw.contains("Hello Lan,"));
        assertTrue(savedRaw.contains(".Dotted line"));
    }

    @Test
    public void testHighLevelSendEmail() throws IOException {
        try (SmtpClient client = new SmtpClient()) {
            EmailMessage msg = new EmailMessage(
                    "duc@localhost",
                    List.of("lan@localhost"),
                    List.of(),
                    "High Level Subject",
                    "This is the body content."
            );
            SmtpResponse resp = client.sendEmail("127.0.0.1", serverPort, null, null, false, msg, false);
            assertTrue(resp.isSuccess());
        }

        assertEquals(1, storage.getMessageCount("lan"));
    }

    @Test
    public void testConcurrentSmtpConnections() throws Exception {
        int clientCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            final int id = i;
            futures.add(executor.submit(() -> {
                try (SmtpClient client = new SmtpClient()) {
                    EmailMessage msg = new EmailMessage(
                            "duc@localhost",
                            List.of("lan@localhost"),
                            List.of(),
                            "Concurrent Message #" + id,
                            "Content " + id
                    );
                    SmtpResponse resp = client.sendEmail("127.0.0.1", serverPort, null, null, false, msg, false);
                    return resp.isSuccess();
                } finally {
                    latch.countDown();
                }
            }));
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        for (Future<Boolean> f : futures) {
            assertTrue(f.get());
        }

        assertEquals(clientCount, storage.getMessageCount("lan"));
    }
}
