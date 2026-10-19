package com.mailapp.ui;

import com.mailapp.client.pop3.Pop3Client;
import com.mailapp.client.pop3.Pop3Response;
import com.mailapp.mime.MimeParser;
import com.mailapp.model.EmailMessage;
import com.mailapp.model.Pop3MessageInfo;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Swing panel for viewing and reading emails via POP3.
 */
public class Pop3ClientPanel extends JPanel {

    private final JTextField hostField = new JTextField("127.0.0.1", 10);
    private final JTextField portField = new JTextField("1110", 5);
    private final JTextField userField = new JTextField("lan", 8);
    private final JPasswordField passField = new JPasswordField("lan123", 8);
    private final JCheckBox sslCheck = new JCheckBox("SSL/TLS", false);

    private final JButton connectBtn = new JButton("Connect & Login");
    private final JButton refreshBtn = new JButton("Refresh");
    private final JButton readBtn = new JButton("Read Email");
    private final JButton deleteBtn = new JButton("Delete");
    private final JButton resetBtn = new JButton("Reset (RSET)");
    private final JButton disconnectBtn = new JButton("Disconnect (QUIT)");

    private final JTable emailTable;
    private final DefaultTableModel tableModel;

    private final JLabel fromLabel = new JLabel("From: -");
    private final JLabel toLabel = new JLabel("To: -");
    private final JLabel subjectLabel = new JLabel("Subject: -");
    private final JLabel dateLabel = new JLabel("Date: -");
    private final JTextArea emailBodyArea = new JTextArea();

    private final JLabel statusLabel = new JLabel("Status: Disconnected");
    private final JTextArea logArea = new JTextArea(4, 30);

    private Pop3Client activeClient = null;
    private final List<Pop3MessageInfo> currentMessages = new ArrayList<>();

    public Pop3ClientPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Top Panel: Connection controls
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        topPanel.setBorder(new TitledBorder("POP3 Server Connection"));

        topPanel.add(new JLabel("Host:"));
        topPanel.add(hostField);
        topPanel.add(new JLabel("Port:"));
        topPanel.add(portField);
        topPanel.add(new JLabel("User:"));
        topPanel.add(userField);
        topPanel.add(new JLabel("Pass:"));
        topPanel.add(passField);
        topPanel.add(sslCheck);
        topPanel.add(connectBtn);
        topPanel.add(disconnectBtn);

        add(topPanel, BorderLayout.NORTH);

        // 2. Center Panel: Split Pane (Top: Table, Bottom: Reader)
        String[] columnNames = {"#", "From", "Subject", "Date", "Size (bytes)", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        emailTable = new JTable(tableModel);
        emailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        emailTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        emailTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        emailTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        emailTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        emailTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        emailTable.getColumnModel().getColumn(5).setPreferredWidth(90);

        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));
        tablePanel.setBorder(new TitledBorder("Mailbox Messages"));
        tablePanel.add(new JScrollPane(emailTable), BorderLayout.CENTER);

        // Action Toolbar above table
        JPanel tableToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        tableToolbar.add(refreshBtn);
        tableToolbar.add(readBtn);
        tableToolbar.add(deleteBtn);
        tableToolbar.add(resetBtn);
        tablePanel.add(tableToolbar, BorderLayout.NORTH);

        // Email Reader Component
        JPanel readerPanel = new JPanel(new BorderLayout(5, 5));
        readerPanel.setBorder(new TitledBorder("Email Content"));

        JPanel headerPanel = new JPanel(new GridLayout(4, 1, 2, 2));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        headerPanel.add(fromLabel);
        headerPanel.add(toLabel);
        headerPanel.add(subjectLabel);
        headerPanel.add(dateLabel);
        readerPanel.add(headerPanel, BorderLayout.NORTH);

        emailBodyArea.setEditable(false);
        emailBodyArea.setLineWrap(true);
        emailBodyArea.setWrapStyleWord(true);
        emailBodyArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        readerPanel.add(new JScrollPane(emailBodyArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, readerPanel);
        splitPane.setResizeWeight(0.45);
        add(splitPane, BorderLayout.CENTER);

        // 3. Bottom Panel: Status & Local Log
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(new TitledBorder("Status & Protocol Log"));

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setBackground(new Color(245, 245, 245));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(500, 80));
        bottomPanel.add(logScroll, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        // Update initial button states
        updateButtonStates(false);

        // Listeners
        connectBtn.addActionListener(e -> onConnect());
        disconnectBtn.addActionListener(e -> onDisconnect());
        refreshBtn.addActionListener(e -> onRefresh());
        readBtn.addActionListener(e -> onReadSelected());
        deleteBtn.addActionListener(e -> onDeleteSelected());
        resetBtn.addActionListener(e -> onReset());

        emailTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && emailTable.getSelectedRow() >= 0) {
                onReadSelected();
            }
        });
    }

    private void updateButtonStates(boolean connected) {
        connectBtn.setEnabled(!connected);
        disconnectBtn.setEnabled(connected);
        refreshBtn.setEnabled(connected);
        readBtn.setEnabled(connected);
        deleteBtn.setEnabled(connected);
        resetBtn.setEnabled(connected);
    }

    private void onConnect() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port không hợp lệ", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        connectBtn.setEnabled(false);
        statusLabel.setText("Status: Đang kết nối...");
        logArea.setText("");

        SwingWorker<List<Pop3MessageInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Pop3MessageInfo> doInBackground() throws Exception {
                activeClient = new Pop3Client();
                activeClient.connect(host, port, 10000, sslCheck.isSelected());
                appendLog("Connected to POP3 server.");

                Pop3Response loginResp = activeClient.login(user, pass);
                if (!loginResp.isOk()) {
                    throw new IOException("Đăng nhập thất bại: " + loginResp.getMessage());
                }
                appendLog("Login successful: " + loginResp.getMessage());

                // Fetch message list
                return fetchMessages();
            }

            @Override
            protected void done() {
                try {
                    List<Pop3MessageInfo> msgs = get();
                    updateButtonStates(true);
                    statusLabel.setText("Status: Đã kết nối hộp thư '" + user + "' (" + msgs.size() + " thư)");
                    statusLabel.setForeground(new Color(0, 128, 0));
                    renderTable(msgs);
                } catch (Exception e) {
                    if (activeClient != null) {
                        activeClient.close();
                        activeClient = null;
                    }
                    updateButtonStates(false);
                    statusLabel.setText("Status: Lỗi: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                    appendLog("Lỗi: " + e.getMessage());
                    JOptionPane.showMessageDialog(Pop3ClientPanel.this,
                            "Không thể kết nối hoặc xác thực POP3:\n" + e.getMessage(),
                            "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private List<Pop3MessageInfo> fetchMessages() throws Exception {
        if (activeClient == null || !activeClient.isConnected()) {
            return Collections.emptyList();
        }

        List<Pop3MessageInfo> list = activeClient.list();
        Map<Integer, String> uidls = activeClient.uidl();

        for (Pop3MessageInfo info : list) {
            String uid = uidls.get(info.getMessageNumber());
            if (uid != null) {
                info.setUidl(uid);
            }

            // Quick peek headers for list display
            try {
                String raw = activeClient.retr(info.getMessageNumber());
                EmailMessage parsed = MimeParser.parseEml(raw);
                info.setFrom(parsed.getFrom());
                info.setSubject(parsed.getSubject());
                info.setDate(parsed.getDate());
            } catch (Exception ignored) {}
        }

        currentMessages.clear();
        currentMessages.addAll(list);
        return list;
    }

    private void renderTable(List<Pop3MessageInfo> msgs) {
        tableModel.setRowCount(0);
        for (Pop3MessageInfo info : msgs) {
            tableModel.addRow(new Object[]{
                    info.getMessageNumber(),
                    info.getFrom() != null ? info.getFrom() : "-",
                    info.getSubject() != null ? info.getSubject() : "(No Subject)",
                    info.getDate() != null ? info.getDate() : "-",
                    info.getSizeInBytes(),
                    info.isMarkedForDeletion() ? "MARKED DELETED" : "OK"
            });
        }
    }

    private void onRefresh() {
        if (activeClient == null || !activeClient.isConnected()) return;

        SwingWorker<List<Pop3MessageInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Pop3MessageInfo> doInBackground() throws Exception {
                return fetchMessages();
            }

            @Override
            protected void done() {
                try {
                    List<Pop3MessageInfo> msgs = get();
                    renderTable(msgs);
                    statusLabel.setText("Status: Đã làm mới hộp thư (" + msgs.size() + " thư)");
                } catch (Exception e) {
                    appendLog("Lỗi refresh: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onReadSelected() {
        int selectedRow = emailTable.getSelectedRow();
        if (selectedRow < 0 || activeClient == null) return;

        int msgNum = (int) tableModel.getValueAt(selectedRow, 0);

        SwingWorker<EmailMessage, Void> worker = new SwingWorker<>() {
            @Override
            protected EmailMessage doInBackground() throws Exception {
                String raw = activeClient.retr(msgNum);
                return MimeParser.parseEml(raw);
            }

            @Override
            protected void done() {
                try {
                    EmailMessage msg = get();
                    fromLabel.setText("From: " + msg.getFrom());
                    toLabel.setText("To: " + String.join(", ", msg.getTo()));
                    subjectLabel.setText("Subject: " + msg.getSubject());
                    dateLabel.setText("Date: " + msg.getDate());
                    emailBodyArea.setText(msg.getBody());
                    emailBodyArea.setCaretPosition(0);
                    appendLog("RETR " + msgNum + " OK (" + msg.getSizeInBytes() + " bytes)");
                } catch (Exception e) {
                    appendLog("Lỗi đọc thư: " + e.getMessage());
                    JOptionPane.showMessageDialog(Pop3ClientPanel.this,
                            "Không thể đọc email: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onDeleteSelected() {
        int selectedRow = emailTable.getSelectedRow();
        if (selectedRow < 0 || activeClient == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn email cần xóa từ danh sách!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int msgNum = (int) tableModel.getValueAt(selectedRow, 0);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Lưu ý chuẩn POP3: Lệnh DELE chỉ đánh dấu thư để xóa trong phiên hiện tại.\n" +
                        "Thư sẽ chỉ được xóa thực sự trên đĩa khi kết thúc phiên bằng lệnh QUIT (Disconnect).\n" +
                        "Bạn có chắc muốn đánh dấu xóa thư #" + msgNum + " không?",
                "Xác nhận đánh dấu xóa email",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        SwingWorker<Pop3Response, Void> worker = new SwingWorker<>() {
            @Override
            protected Pop3Response doInBackground() throws Exception {
                return activeClient.dele(msgNum);
            }

            @Override
            protected void done() {
                try {
                    Pop3Response resp = get();
                    if (resp.isOk()) {
                        tableModel.setValueAt("MARKED DELETED", selectedRow, 5);
                        for (Pop3MessageInfo m : currentMessages) {
                            if (m.getMessageNumber() == msgNum) {
                                m.setMarkedForDeletion(true);
                                break;
                            }
                        }
                        appendLog("DELE " + msgNum + ": " + resp.getMessage());
                        statusLabel.setText("Status: Đã đánh dấu xóa thư #" + msgNum + " (chờ commit khi QUIT)");
                    } else {
                        JOptionPane.showMessageDialog(Pop3ClientPanel.this,
                                "Không thể đánh dấu xóa: " + resp.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    appendLog("Lỗi DELE: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onReset() {
        if (activeClient == null) return;

        SwingWorker<Pop3Response, Void> worker = new SwingWorker<>() {
            @Override
            protected Pop3Response doInBackground() throws Exception {
                return activeClient.rset();
            }

            @Override
            protected void done() {
                try {
                    Pop3Response resp = get();
                    if (resp.isOk()) {
                        for (int i = 0; i < tableModel.getRowCount(); i++) {
                            tableModel.setValueAt("OK", i, 5);
                        }
                        for (Pop3MessageInfo m : currentMessages) {
                            m.setMarkedForDeletion(false);
                        }
                        appendLog("RSET: " + resp.getMessage());
                        statusLabel.setText("Status: Đã hủy các đánh dấu xóa (RSET thành công)");
                    }
                } catch (Exception e) {
                    appendLog("Lỗi RSET: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onDisconnect() {
        if (activeClient == null) {
            updateButtonStates(false);
            return;
        }

        disconnectBtn.setEnabled(false);
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    Pop3Response quitResp = activeClient.quit();
                    appendLog("QUIT response: " + quitResp.getMessage());
                } catch (Exception e) {
                    appendLog("Disconnect notice: " + e.getMessage());
                    activeClient.close();
                }
                activeClient = null;
                return null;
            }

            @Override
            protected void done() {
                updateButtonStates(false);
                statusLabel.setText("Status: Đã ngắt kết nối và hoàn tất phiên POP3 (UPDATE committed)");
                statusLabel.setForeground(Color.BLACK);
                tableModel.setRowCount(0);
                fromLabel.setText("From: -");
                toLabel.setText("To: -");
                subjectLabel.setText("Subject: -");
                dateLabel.setText("Date: -");
                emailBodyArea.setText("");
            }
        };
        worker.execute();
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }
}
