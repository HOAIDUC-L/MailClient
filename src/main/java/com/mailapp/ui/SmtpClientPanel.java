package com.mailapp.ui;

import com.mailapp.client.smtp.SmtpClient;
import com.mailapp.client.smtp.SmtpResponse;
import com.mailapp.model.EmailMessage;
import com.mailapp.util.EmailValidator;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * Swing panel for composing and sending emails via SMTP.
 */
public class SmtpClientPanel extends JPanel {

    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField("2525", 5);
    private final JTextField usernameField = new JTextField("duc", 10);
    private final JPasswordField passwordField = new JPasswordField("duc123", 10);
    private final JCheckBox authCheck = new JCheckBox("SMTP AUTH", false);
    private final JCheckBox sslCheck = new JCheckBox("SSL/TLS", false);

    private final JTextField fromField = new JTextField("duc@localhost", 20);
    private final JTextField toField = new JTextField("lan@localhost", 20);
    private final JTextField ccField = new JTextField("", 20);
    private final JTextField subjectField = new JTextField("Test SMTP & POP3 Mail", 20);
    private final JTextArea bodyArea = new JTextArea(8, 30);

    private final JButton testBtn = new JButton("Test Connection");
    private final JButton sendBtn = new JButton("Send Email");
    private final JButton clearBtn = new JButton("Clear");

    private final JLabel statusLabel = new JLabel("Status: Ready");
    private final JTextArea logArea = new JTextArea(6, 40);

    public SmtpClientPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Top Panel: Server Configuration
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        configPanel.setBorder(new TitledBorder("Server Connection Configuration"));

        configPanel.add(new JLabel("Host:"));
        configPanel.add(hostField);
        configPanel.add(new JLabel("Port:"));
        configPanel.add(portField);
        configPanel.add(authCheck);
        configPanel.add(new JLabel("User:"));
        configPanel.add(usernameField);
        configPanel.add(new JLabel("Pass:"));
        configPanel.add(passwordField);
        configPanel.add(sslCheck);
        configPanel.add(testBtn);

        add(configPanel, BorderLayout.NORTH);

        // 2. Center Panel: Email Form & Body
        JPanel mailPanel = new JPanel(new BorderLayout(5, 5));
        mailPanel.setBorder(new TitledBorder("Compose Email"));

        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        fieldsPanel.add(new JLabel("From:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        fieldsPanel.add(fromField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        fieldsPanel.add(new JLabel("To:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        fieldsPanel.add(toField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        fieldsPanel.add(new JLabel("Cc:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0;
        fieldsPanel.add(ccField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        fieldsPanel.add(new JLabel("Subject:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0;
        fieldsPanel.add(subjectField, gbc);

        mailPanel.add(fieldsPanel, BorderLayout.NORTH);

        bodyArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        bodyArea.setText("Xin chào từ Java SMTP Client!\nĐây là email kiểm thử gửi qua TCP Socket.\n.Dot-stuffing test line\nTrân trọng.");
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setBorder(BorderFactory.createTitledBorder("Email Body (UTF-8)"));
        mailPanel.add(bodyScroll, BorderLayout.CENTER);

        // Buttons row
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        sendBtn.setFont(sendBtn.getFont().deriveFont(Font.BOLD));
        sendBtn.setBackground(new Color(40, 167, 69));
        sendBtn.setForeground(Color.WHITE);
        buttonRow.add(clearBtn);
        buttonRow.add(sendBtn);
        mailPanel.add(buttonRow, BorderLayout.SOUTH);

        add(mailPanel, BorderLayout.CENTER);

        // 3. Bottom Panel: Status & Protocol Log
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(new TitledBorder("Protocol Interaction Log & Result"));

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setBackground(new Color(245, 245, 245));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(500, 120));
        bottomPanel.add(logScroll, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        // Event listeners
        testBtn.addActionListener(e -> onTestConnection());
        sendBtn.addActionListener(e -> onSendEmail());
        clearBtn.addActionListener(e -> onClear());
    }

    private void onTestConnection() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port không hợp lệ", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        testBtn.setEnabled(false);
        statusLabel.setText("Status: Đang kiểm tra kết nối...");
        logArea.setText("");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                try (SmtpClient client = new SmtpClient()) {
                    SmtpResponse greeting = client.connect(host, port, 10000, sslCheck.isSelected());
                    appendLocalLog("Connected: " + greeting);
                    SmtpResponse ehlo = client.sendEhlo("localhost");
                    appendLocalLog("EHLO reply:\n" + ehlo.getMessage());
                    client.sendQuit();
                    appendLocalLog("QUIT completed.");
                    return "Kết nối SMTP Server thành công!";
                }
            }

            @Override
            protected void done() {
                testBtn.setEnabled(true);
                try {
                    String res = get();
                    statusLabel.setText("Status: " + res);
                    statusLabel.setForeground(new Color(0, 128, 0));
                } catch (Exception e) {
                    statusLabel.setText("Status: Kết nối thất bại: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                    appendLocalLog("Lỗi: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onSendEmail() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port không hợp lệ", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String from = fromField.getText().trim();
        String to = toField.getText().trim();
        String cc = ccField.getText().trim();
        String subject = subjectField.getText().trim();
        String body = bodyArea.getText();

        if (from.isEmpty() || to.isEmpty()) {
            JOptionPane.showMessageDialog(this, "From và To không được để trống!", "Lỗi nhập liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (EmailValidator.containsCrlf(from) || EmailValidator.containsCrlf(to) || EmailValidator.containsCrlf(subject)) {
            JOptionPane.showMessageDialog(this, "Header không được chứa ký tự xuống dòng (CR/LF)!", "Cảnh báo bảo mật", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> toList = Arrays.stream(to.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> ccList = cc.isEmpty() ? List.of() : Arrays.stream(cc.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        EmailMessage email = new EmailMessage(from, toList, ccList, subject, body);

        sendBtn.setEnabled(false);
        statusLabel.setText("Status: Đang gửi email...");
        statusLabel.setForeground(Color.BLACK);
        logArea.setText("");

        SwingWorker<SmtpResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected SmtpResponse doInBackground() throws Exception {
                try (SmtpClient client = new SmtpClient()) {
                    return client.sendEmail(
                            host,
                            port,
                            usernameField.getText().trim(),
                            new String(passwordField.getPassword()),
                            authCheck.isSelected(),
                            email,
                            sslCheck.isSelected()
                    );
                }
            }

            @Override
            protected void done() {
                sendBtn.setEnabled(true);
                try {
                    SmtpResponse resp = get();
                    if (resp.isSuccess()) {
                        statusLabel.setText("Status: Email đã được server chấp nhận (" + resp.getCode() + " OK)");
                        statusLabel.setForeground(new Color(0, 128, 0));
                        appendLocalLog("Thành công: " + resp);
                        JOptionPane.showMessageDialog(SmtpClientPanel.this,
                                "Gửi thư thành công!\nPhản hồi máy chủ: " + resp,
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setText("Status: Server từ chối: " + resp);
                        statusLabel.setForeground(Color.RED);
                        appendLocalLog("Server từ chối: " + resp);
                        JOptionPane.showMessageDialog(SmtpClientPanel.this,
                                "Gửi email thất bại:\n" + resp,
                                "Thất bại", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Status: Lỗi: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                    appendLocalLog("Lỗi gửi thư: " + e.getMessage());
                    JOptionPane.showMessageDialog(SmtpClientPanel.this,
                            "Lỗi kết nối / gửi thư:\n" + e.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onClear() {
        subjectField.setText("");
        bodyArea.setText("");
        ccField.setText("");
        logArea.setText("");
        statusLabel.setText("Status: Ready");
        statusLabel.setForeground(Color.BLACK);
    }

    private void appendLocalLog(String line) {
        SwingUtilities.invokeLater(() -> logArea.append(line + "\n"));
    }
}
