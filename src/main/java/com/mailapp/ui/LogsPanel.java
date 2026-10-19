package com.mailapp.ui;

import com.mailapp.util.ProtocolLogger;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

/**
 * Tab panel that displays real-time protocol and system logs.
 */
public class LogsPanel extends JPanel {

    private final JTextArea logTextArea = new JTextArea();

    public LogsPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton clearBtn = new JButton("Clear Logs");
        clearBtn.addActionListener(e -> logTextArea.setText(""));
        toolBar.add(clearBtn);

        toolBar.add(Box.createHorizontalStrut(10));
        JCheckBox autoScrollCheck = new JCheckBox("Auto-scroll", true);
        toolBar.add(autoScrollCheck);

        add(toolBar, BorderLayout.NORTH);

        // Log text area
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logTextArea.setBackground(new Color(248, 249, 250));

        DefaultCaret caret = (DefaultCaret) logTextArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        autoScrollCheck.addActionListener(e -> {
            if (autoScrollCheck.isSelected()) {
                caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
            } else {
                caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            }
        });

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        add(scrollPane, BorderLayout.CENTER);

        // Register listener with ProtocolLogger
        ProtocolLogger.addGlobalListener(msg -> SwingUtilities.invokeLater(() -> {
            logTextArea.append(msg + "\n");
        }));
    }

    public void appendLog(String log) {
        SwingUtilities.invokeLater(() -> logTextArea.append(log + "\n"));
    }
}
