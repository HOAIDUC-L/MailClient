package com.mailapp;

import com.mailapp.account.AccountManager;
import com.mailapp.client.pop3.Pop3Client;
import com.mailapp.client.pop3.Pop3Response;
import com.mailapp.client.smtp.SmtpClient;
import com.mailapp.client.smtp.SmtpResponse;
import com.mailapp.model.EmailMessage;
import com.mailapp.model.MailServerConfig;
import com.mailapp.server.pop3.Pop3Server;
import com.mailapp.server.smtp.SmtpServer;
import com.mailapp.storage.FileMailStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full end-to-end integration test executing the required project scenario:
 * 1. Start SMTP & POP3 servers on dynamic ports.
 * 2. Connect SMTP Client and send email from 'duc' to 'lan'.
 * 3. Verify .eml file created in 'lan' mailbox.
 * 4. Connect POP3 Client, login as 'lan', check STAT & LIST.
 * 5. RETR email and verify Unicode Subject & dot-stuffed body.
 * 6. Test DELE & RSET (not deleted).
 * 7. Test DELE & QUIT (committed delete).
 * 8. Send new email, restart servers, verify email persistence.
 */
public class EndToEndMailFlowIntegrationTest {

    private Path baseDir;
    private MailServerConfig config;
    private AccountManager accountManager;
    private FileMailStorage storage;
    private SmtpServer smtpServer;
    private Pop3Server pop3Server;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws IOException {
        baseDir = tempDir;
        config = new MailServerConfig();
        config.setSmtpHost("127.0.0.1");
        config.setSmtpPort(0); // Dynamic ephemeral port
        config.setPop3Host("127.0.0.1");
        config.setPop3Port(0); // Dynamic ephemeral port
        config.setDataDirectory(tempDir);

        accountManager = new AccountManager(config.getAccountsFilePath());
        storage = new FileMailStorage(config.getMailboxesDirectory());
        storage.init();

        smtpServer = new SmtpServer(config, storage, accountManager);
        pop3Server = new Pop3Server(config, storage, accountManager);

        smtpServer.start();
        pop3Server.start();

        assertTrue(smtpServer.isRunning());
        assertTrue(pop3Server.isRunning());
    }

    @AfterEach
    public void tearDown() {
        if (smtpServer != null) smtpServer.stop();
        if (pop3Server != null) pop3Server.stop();
    }

    @Test
    public void testCompleteMailFlowScenario() throws Exception {
        int smtpPort = smtpServer.getPort();
        int pop3Port = pop3Server.getPort();

        // Step 1: Send email from 'duc' to 'lan' via SMTP Client
        String subject = "Tiêu đề kiểm thử: Chúc mừng hoàn thành đồ án Java Socket!";
        String bodyText = "Chào bạn Lan,\nĐây là thư gửi trực tiếp qua giao thức SMTP Socket.\n.Dòng bắt đầu bằng dấu chấm\nChúc thành công!";

        EmailMessage outgoing = new EmailMessage(
                "duc@localhost",
                List.of("lan@localhost"),
                List.of(),
                subject,
                bodyText
        );

        try (SmtpClient smtpClient = new SmtpClient()) {
            SmtpResponse sendResult = smtpClient.sendEmail(
                    "127.0.0.1",
                    smtpPort,
                    "duc",
                    "duc123",
                    false,
                    outgoing,
                    false
            );
            assertTrue(sendResult.isSuccess(), "SMTP send must return 250 OK");
        }

        // Step 2: Verify storage on disk
        assertEquals(1, storage.getMessageCount("lan"));
        var storedMessages = storage.getMailboxMessages("lan");
        assertEquals(1, storedMessages.size());

        // Step 3: Connect POP3 Client as 'lan'
        try (Pop3Client pop3Client = new Pop3Client()) {
            pop3Client.connect("127.0.0.1", pop3Port, 5000, false);

            Pop3Response loginResp = pop3Client.login("lan", "lan123");
            assertTrue(loginResp.isOk());

            // Check STAT
            long[] stat = pop3Client.stat();
            assertEquals(1, stat[0]);
            assertTrue(stat[1] > 0);

            // Check LIST
            var list = pop3Client.list();
            assertEquals(1, list.size());
            assertEquals(1, list.get(0).getMessageNumber());

            // Check RETR
            EmailMessage received = pop3Client.retrieveMessage(1);
            assertEquals("duc@localhost", received.getFrom());
            assertEquals(subject, received.getSubject());
            assertTrue(received.getBody().contains("Chào bạn Lan,"));
            assertTrue(received.getBody().contains(".Dòng bắt đầu bằng dấu chấm"));

            // Test DELE and RSET
            pop3Client.dele(1);
            assertEquals(0, pop3Client.stat()[0]); // 0 active messages
            pop3Client.rset();
            assertEquals(1, pop3Client.stat()[0]); // Back to 1

            // Commit delete with QUIT
            pop3Client.dele(1);
            pop3Client.quit();
        }

        // Verify storage is empty after committed QUIT
        assertEquals(0, storage.getMessageCount("lan"));

        // Step 4: Send another email and test persistence across server restart
        try (SmtpClient smtpClient = new SmtpClient()) {
            EmailMessage persistentMsg = new EmailMessage(
                    "duc@localhost",
                    List.of("lan@localhost"),
                    List.of(),
                    "Thư lưu trữ bền vững",
                    "Nội dung này phải còn nguyên sau khi khởi động lại server."
            );
            smtpClient.sendEmail("127.0.0.1", smtpPort, "duc", "duc123", false, persistentMsg, false);
        }
        assertEquals(1, storage.getMessageCount("lan"));

        // Restart both servers
        smtpServer.stop();
        pop3Server.stop();

        SmtpServer newSmtpServer = new SmtpServer(config, storage, accountManager);
        Pop3Server newPop3Server = new Pop3Server(config, storage, accountManager);
        newSmtpServer.start();
        newPop3Server.start();

        try {
            int newPop3Port = newPop3Server.getPort();
            try (Pop3Client pop3Client = new Pop3Client()) {
                pop3Client.connect("127.0.0.1", newPop3Port, 5000, false);
                pop3Client.login("lan", "lan123");
                assertEquals(1, pop3Client.stat()[0], "Email must persist across server restarts");
                EmailMessage persistentRead = pop3Client.retrieveMessage(1);
                assertEquals("Thư lưu trữ bền vững", persistentRead.getSubject());
                pop3Client.quit();
            }
        } finally {
            newSmtpServer.stop();
            newPop3Server.stop();
        }
    }
}
