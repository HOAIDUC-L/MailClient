package com.mailapp;

import com.mailapp.account.AccountManager;
import com.mailapp.client.pop3.Pop3Client;
import com.mailapp.client.smtp.SmtpClient;
import com.mailapp.model.EmailMessage;
import com.mailapp.model.MailServerConfig;
import com.mailapp.server.pop3.Pop3Server;
import com.mailapp.server.smtp.SmtpServer;
import com.mailapp.storage.FileMailStorage;
import com.mailapp.ui.MainFrame;
import com.mailapp.util.ProtocolLogger;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Main application entry point.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0) {
            String command = args[0].trim().toLowerCase();
            if (command.equals("--demo") || command.equals("-d")) {
                runCliDemo();
                return;
            } else if (command.equals("--server") || command.equals("-s")) {
                runHeadlessServer();
                return;
            } else if (command.equals("--help") || command.equals("-h")) {
                printHelp();
                return;
            }
        }

        // GUI Mode
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Headless environment detected (no GUI display).");
            System.out.println("Running automated CLI end-to-end demo...");
            runCliDemo();
        } else {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            SwingUtilities.invokeLater(() -> {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            });
        }
    }

    private static void printHelp() {
        System.out.println("Java Mail Application - SMTP Client, POP3 Client & Local Mail Server");
        System.out.println("Usage:");
        System.out.println("  java -jar mail-client-server-1.0.0.jar             # Launch Java Swing GUI");
        System.out.println("  java -jar mail-client-server-1.0.0.jar --server    # Launch headless Local Mail Server");
        System.out.println("  java -jar mail-client-server-1.0.0.jar --demo      # Run end-to-end CLI demonstration");
        System.out.println("  java -jar mail-client-server-1.0.0.jar --help      # Show this help message");
    }

    public static void runHeadlessServer() {
        try {
            MailServerConfig config = new MailServerConfig();
            AccountManager accountManager = new AccountManager(config.getAccountsFilePath());
            FileMailStorage storage = new FileMailStorage(config.getMailboxesDirectory());
            storage.init();

            SmtpServer smtpServer = new SmtpServer(config, storage, accountManager);
            Pop3Server pop3Server = new Pop3Server(config, storage, accountManager);

            smtpServer.start();
            pop3Server.start();

            ProtocolLogger.log(ProtocolLogger.LogType.SYSTEM, "Local Mail Server is active. Press Ctrl+C to stop.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down servers...");
                smtpServer.stop();
                pop3Server.stop();
            }));

            // Keep alive
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void runCliDemo() {
        try {
            System.out.println("==================================================");
            System.out.println("STARTING AUTOMATED CLI DEMO");
            System.out.println("==================================================");

            MailServerConfig config = new MailServerConfig();
            AccountManager accountManager = new AccountManager(config.getAccountsFilePath());
            FileMailStorage storage = new FileMailStorage(config.getMailboxesDirectory());
            storage.init();

            SmtpServer smtpServer = new SmtpServer(config, storage, accountManager);
            Pop3Server pop3Server = new Pop3Server(config, storage, accountManager);

            smtpServer.start();
            pop3Server.start();

            int smtpPort = smtpServer.getPort();
            int pop3Port = pop3Server.getPort();

            System.out.println("[DEMO] 1. SMTP Server running on port " + smtpPort);
            System.out.println("[DEMO] 2. POP3 Server running on port " + pop3Port);

            // 1. SMTP Send from duc to lan
            System.out.println("\n[DEMO] 3. Sending email from 'duc' to 'lan' using low-level TCP Socket...");
            try (SmtpClient smtpClient = new SmtpClient()) {
                EmailMessage message = new EmailMessage(
                        "duc@localhost",
                        List.of("lan@localhost"),
                        List.of(),
                        "Test SMTP & POP3 Automation",
                        "Xin chào Lan,\nĐây là thư gửi tự động qua Socket SMTP và POP3.\n.Test dot-stuffing\nThành công!"
                );

                smtpClient.sendEmail("127.0.0.1", smtpPort, "duc", "duc123", false, message, false);
                System.out.println("[DEMO] 4. SMTP Email accepted by server!");
            }

            // 2. POP3 Retrieve by lan
            System.out.println("\n[DEMO] 5. Connecting POP3 as 'lan' to retrieve mail...");
            try (Pop3Client pop3Client = new Pop3Client()) {
                pop3Client.connect("127.0.0.1", pop3Port, 5000, false);
                pop3Client.login("lan", "lan123");
                long[] stat = pop3Client.stat();
                System.out.println("[DEMO] 6. POP3 STAT: " + stat[0] + " messages, " + stat[1] + " bytes");

                var list = pop3Client.list();
                if (!list.isEmpty()) {
                    int lastNum = list.get(list.size() - 1).getMessageNumber();
                    EmailMessage received = pop3Client.retrieveMessage(lastNum);
                    System.out.println("[DEMO] 7. Retrieved Email Content:");
                    System.out.println("   From: " + received.getFrom());
                    System.out.println("   Subject: " + received.getSubject());
                    System.out.println("   Date: " + received.getDate());
                    System.out.println("   Body:\n" + received.getBody());
                }

                pop3Client.quit();
                System.out.println("[DEMO] 8. POP3 Session finished successfully.");
            }

            // Cleanup servers
            smtpServer.stop();
            pop3Server.stop();

            System.out.println("\n==================================================");
            System.out.println("AUTOMATED DEMO FINISHED SUCCESSFULLY!");
            System.out.println("==================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
