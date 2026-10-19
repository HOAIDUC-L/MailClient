package com.mailapp.ui;

import com.mailapp.account.Account;
import com.mailapp.account.AccountManager;
import com.mailapp.model.EmailMessage;
import com.mailapp.model.MailServerConfig;
import com.mailapp.model.Pop3MessageInfo;
import com.mailapp.server.pop3.Pop3Server;
import com.mailapp.server.smtp.SmtpServer;
import com.mailapp.storage.MailStorage;
import com.mailapp.util.ProtocolLogger;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Server Dashboard panel for monitoring and controlling the Local Mail Server.
 */
public class ServerDashboardPanel extends JPanel {

    private final MailServerConfig config;
    private final MailStorage mailStorage;
    private final AccountManager accountManager;
    private final SmtpServer smtpServer;
    private final Pop3Server pop3Server;

    // Controls
    private final JButton startSmtpBtn = new JButton("Start SMTP");
    private final JButton stopSmtpBtn = new JButton("Stop SMTP");
    private final JLabel smtpStatusBadge = new JLabel(" STOPPED ");
    private final JTextField smtpPortField = new JTextField("2525", 5);

    private final JButton startPop3Btn = new JButton("Start POP3");
    private final JButton stopPop3Btn = new JButton("Stop POP3");
    private final JLabel pop3StatusBadge = new JLabel(" STOPPED ");
    private final JTextField pop3PortField = new JTextField("1110", 5);

    private final JTextField storageDirField = new JTextField("data", 12);
    private final JButton openFolderBtn = new JButton("Open Storage Folder");

    // Accounts & Mailbox inspection
    private final JComboBox<String> mailboxCombo = new JComboBox<>();
    private final DefaultTableModel emailTableModel;
    private final JTable emailTable;
    private final JButton refreshDashboardBtn = new JButton("Refresh Data");
    private final JLabel accountListLabel = new JLabel("Demo Accounts: duc, lan");

    // Server log
    private final JTextArea serverLogArea = new JTextArea(7, 40);

    public ServerDashboardPanel(
            MailServerConfig config,
            MailStorage mailStorage,
            AccountManager accountManager,
            SmtpServer smtpServer,
            Pop3Server pop3Server) {

        this.config = config;
        this.mailStorage = mailStorage;
        this.accountManager = accountManager;
        this.smtpServer = smtpServer;
        this.pop3Server = pop3Server;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Top Panel: Server Controls
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(new TitledBorder("Local Mail Server Control"));

        // Row 1: SMTP Server Controls
        JPanel smtpRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        smtpRow.add(new JLabel("SMTP Server:"));
        smtpStatusBadge.setOpaque(true);
        smtpStatusBadge.setBackground(Color.LIGHT_GRAY);
        smtpStatusBadge.setForeground(Color.BLACK);
        smtpRow.add(smtpStatusBadge);
        smtpRow.add(new JLabel("Port:"));
        smtpRow.add(smtpPortField);
        smtpRow.add(startSmtpBtn);
        smtpRow.add(stopSmtpBtn);

        smtpRow.add(Box.createHorizontalStrut(20));
        smtpRow.add(new JLabel("Storage:"));
        smtpRow.add(storageDirField);
        smtpRow.add(openFolderBtn);
        topPanel.add(smtpRow);

        // Row 2: POP3 Server Controls
        JPanel pop3Row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        pop3Row.add(new JLabel("POP3 Server:"));
        pop3StatusBadge.setOpaque(true);
        pop3StatusBadge.setBackground(Color.LIGHT_GRAY);
        pop3StatusBadge.setForeground(Color.BLACK);
        pop3Row.add(pop3StatusBadge);
        pop3Row.add(new JLabel("Port:"));
        pop3Row.add(pop3PortField);
        pop3Row.add(startPop3Btn);
        pop3Row.add(stopPop3Btn);

        pop3Row.add(Box.createHorizontalStrut(20));
        pop3Row.add(accountListLabel);
        topPanel.add(pop3Row);

        add(topPanel, BorderLayout.NORTH);

        // 2. Center Panel: Mailbox Explorer
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(new TitledBorder("Mailbox Inspector (Direct Disk View)"));

        JPanel mailboxNav = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        mailboxNav.add(new JLabel("Select Mailbox:"));
        mailboxCombo.setPreferredSize(new Dimension(150, 25));
        mailboxNav.add(mailboxCombo);
        mailboxNav.add(refreshDashboardBtn);
        centerPanel.add(mailboxNav, BorderLayout.NORTH);

        String[] cols = {"File / UID", "From", "Subject", "Date", "Size (bytes)"};
        emailTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        emailTable = new JTable(emailTableModel);
        centerPanel.add(new JScrollPane(emailTable), BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // 3. Bottom Panel: Server Live Log
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(new TitledBorder("Server Activity Log"));

        serverLogArea.setEditable(false);
        serverLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        serverLogArea.setBackground(new Color(248, 248, 248));
        bottomPanel.add(new JScrollPane(serverLogArea), BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        // Initial states
        updateServerStatus();
        reloadMailboxList();

        // Listeners
        startSmtpBtn.addActionListener(e -> onStartSmtp());
        stopSmtpBtn.addActionListener(e -> onStopSmtp());
        startPop3Btn.addActionListener(e -> onStartPop3());
        stopPop3Btn.addActionListener(e -> onStopPop3());
        refreshDashboardBtn.addActionListener(e -> {
            reloadMailboxList();
            updateAccountsLabel();
        });
        mailboxCombo.addActionListener(e -> loadSelectedMailboxMessages());
        openFolderBtn.addActionListener(e -> onOpenStorageFolder());

        // ProtocolLogger server hook
        ProtocolLogger.addGlobalListener(msg -> {
            if (msg.contains("SMTP_SERVER") || msg.contains("POP3_SERVER") || msg.contains("SYSTEM")) {
                SwingUtilities.invokeLater(() -> serverLogArea.append(msg + "\n"));
            }
        });
    }

    private void updateServerStatus() {
        boolean smtpRunning = smtpServer.isRunning();
        startSmtpBtn.setEnabled(!smtpRunning);
        stopSmtpBtn.setEnabled(smtpRunning);
        smtpPortField.setEnabled(!smtpRunning);
        if (smtpRunning) {
            smtpStatusBadge.setText(" RUNNING :" + smtpServer.getPort() + " ");
            smtpStatusBadge.setBackground(new Color(40, 167, 69));
            smtpStatusBadge.setForeground(Color.WHITE);
        } else {
            smtpStatusBadge.setText(" STOPPED ");
            smtpStatusBadge.setBackground(Color.GRAY);
            smtpStatusBadge.setForeground(Color.WHITE);
        }

        boolean pop3Running = pop3Server.isRunning();
        startPop3Btn.setEnabled(!pop3Running);
        stopPop3Btn.setEnabled(pop3Running);
        pop3PortField.setEnabled(!pop3Running);
        if (pop3Running) {
            pop3StatusBadge.setText(" RUNNING :" + pop3Server.getPort() + " ");
            pop3StatusBadge.setBackground(new Color(40, 167, 69));
            pop3StatusBadge.setForeground(Color.WHITE);
        } else {
            pop3StatusBadge.setText(" STOPPED ");
            pop3StatusBadge.setBackground(Color.GRAY);
            pop3StatusBadge.setForeground(Color.WHITE);
        }
    }

    private void onStartSmtp() {
        try {
            int port = Integer.parseInt(smtpPortField.getText().trim());
            config.setSmtpPort(port);
            smtpServer.start();
            updateServerStatus();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể khởi động SMTP Server: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onStopSmtp() {
        smtpServer.stop();
        updateServerStatus();
    }

    private void onStartPop3() {
        try {
            int port = Integer.parseInt(pop3PortField.getText().trim());
            config.setPop3Port(port);
            pop3Server.start();
            updateServerStatus();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể khởi động POP3 Server: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onStopPop3() {
        pop3Server.stop();
        updateServerStatus();
    }

    public void reloadMailboxList() {
        String currentSelection = (String) mailboxCombo.getSelectedItem();
        mailboxCombo.removeAllItems();

        List<String> mailboxes = mailStorage.listAllMailboxes();
        // Ensure default demo users are visible in combo even if empty
        if (!mailboxes.contains("duc")) mailboxCombo.addItem("duc");
        if (!mailboxes.contains("lan")) mailboxCombo.addItem("lan");

        for (String m : mailboxes) {
            if (!m.equals("duc") && !m.equals("lan")) {
                mailboxCombo.addItem(m);
            }
        }

        if (currentSelection != null) {
            mailboxCombo.setSelectedItem(currentSelection);
        } else if (mailboxCombo.getItemCount() > 0) {
            mailboxCombo.setSelectedIndex(0);
        }
        loadSelectedMailboxMessages();
    }

    private void loadSelectedMailboxMessages() {
        String selectedMailbox = (String) mailboxCombo.getSelectedItem();
        emailTableModel.setRowCount(0);
        if (selectedMailbox == null) return;

        try {
            List<Pop3MessageInfo> msgs = mailStorage.getMailboxMessages(selectedMailbox);
            for (Pop3MessageInfo m : msgs) {
                emailTableModel.addRow(new Object[]{
                        m.getUidl(),
                        m.getFrom() != null ? m.getFrom() : "-",
                        m.getSubject() != null ? m.getSubject() : "(No Subject)",
                        m.getDate() != null ? m.getDate() : "-",
                        m.getSizeInBytes()
                });
            }
        } catch (Exception e) {
            serverLogArea.append("Lỗi đọc danh sách thư: " + e.getMessage() + "\n");
        }
    }

    private void updateAccountsLabel() {
        List<Account> accounts = accountManager.getAllAccounts();
        StringBuilder sb = new StringBuilder("Accounts: ");
        for (Account a : accounts) {
            sb.append(a.getUsername()).append(", ");
        }
        if (!accounts.isEmpty()) {
            sb.setLength(sb.length() - 2);
        }
        accountListLabel.setText(sb.toString());
    }

    private void onOpenStorageFolder() {
        try {
            Path path = Paths.get(storageDirField.getText().trim()).toAbsolutePath();
            File file = path.toFile();
            if (!file.exists()) {
                file.mkdirs();
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Hệ điều hành không hỗ trợ Desktop.open.\nĐường dẫn thư mục: " + file.getAbsolutePath(),
                        "Thư mục lưu trữ", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể mở thư mục: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
