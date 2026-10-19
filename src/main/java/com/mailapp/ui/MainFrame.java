package com.mailapp.ui;

import com.mailapp.account.AccountManager;
import com.mailapp.model.MailServerConfig;
import com.mailapp.server.pop3.Pop3Server;
import com.mailapp.server.smtp.SmtpServer;
import com.mailapp.storage.FileMailStorage;
import com.mailapp.storage.MailStorage;
import com.mailapp.util.ProtocolLogger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * Main application frame containing tabs for SMTP Client, POP3 Client,
 * Server Dashboard, and Protocol Logs.
 */
public class MainFrame extends JFrame {

    private final MailServerConfig config;
    private final AccountManager accountManager;
    private final MailStorage mailStorage;
    private final SmtpServer smtpServer;
    private final Pop3Server pop3Server;

    public MainFrame() {
        super("Java Mail Client & Local Server - SMTP / POP3");

        // Initialize Core Services
        this.config = new MailServerConfig();
        this.accountManager = new AccountManager(config.getAccountsFilePath());
        this.mailStorage = new FileMailStorage(config.getMailboxesDirectory());
        try {
            this.mailStorage.init();
        } catch (IOException e) {
            ProtocolLogger.log(ProtocolLogger.LogType.SYSTEM, "Failed to initialize mail storage: " + e.getMessage());
        }

        this.smtpServer = new SmtpServer(config, mailStorage, accountManager);
        this.pop3Server = new Pop3Server(config, mailStorage, accountManager);

        // Build UI
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 720);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        SmtpClientPanel smtpPanel = new SmtpClientPanel();
        Pop3ClientPanel pop3Panel = new Pop3ClientPanel();
        ServerDashboardPanel dashboardPanel = new ServerDashboardPanel(config, mailStorage, accountManager, smtpServer, pop3Server);
        LogsPanel logsPanel = new LogsPanel();

        tabbedPane.addTab("SMTP Client", smtpPanel);
        tabbedPane.addTab("POP3 Client", pop3Panel);
        tabbedPane.addTab("Server Dashboard", dashboardPanel);
        tabbedPane.addTab("Protocol Logs", logsPanel);

        // Default open to Server Dashboard or SMTP Client
        tabbedPane.setSelectedIndex(2); // Start on Server Dashboard for convenient server startup

        add(tabbedPane, BorderLayout.CENTER);

        // Handle safe window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
    }

    private void onClose() {
        if (smtpServer.isRunning() || pop3Server.isRunning()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Máy chủ email cục bộ đang chạy. Bạn có muốn dừng máy chủ và thoát ứng dụng?",
                    "Xác nhận thoát", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        smtpServer.stop();
        pop3Server.stop();
        dispose();
        System.exit(0);
    }
}
