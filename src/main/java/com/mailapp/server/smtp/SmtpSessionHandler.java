package com.mailapp.server.smtp;

import com.mailapp.account.AccountManager;
import com.mailapp.model.MailServerConfig;
import com.mailapp.storage.MailStorage;
import com.mailapp.util.EmailValidator;
import com.mailapp.util.ProtocolLogger;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles a single client SMTP session over TCP socket.
 */
public class SmtpSessionHandler implements Runnable {

    private final Socket socket;
    private final MailServerConfig config;
    private final MailStorage mailStorage;
    private final AccountManager accountManager;

    private BufferedReader reader;
    private BufferedWriter writer;

    private boolean helod = false;
    private String clientHeloName = "";
    private boolean authenticated = false;
    private String authenticatedUser = null;

    private String mailFrom = null;
    private final List<String> rcptTo = new ArrayList<>();
    private final StringBuilder dataBuffer = new StringBuilder();

    public SmtpSessionHandler(Socket socket, MailServerConfig config, MailStorage mailStorage, AccountManager accountManager) {
        this.socket = socket;
        this.config = config;
        this.mailStorage = mailStorage;
        this.accountManager = accountManager;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(config.getSocketTimeoutMs());
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            sendResponse("220 " + config.getSmtpHost() + " ESMTP Service ready");

            String line;
            while ((line = reader.readLine()) != null) {
                ProtocolLogger.logServerReceive(ProtocolLogger.LogType.SMTP_SERVER, line);
                if (processCommand(line)) {
                    // Session requested to terminate (QUIT)
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            try {
                sendResponse("421 4.4.2 Timeout: closing connection");
            } catch (Exception ignored) {}
        } catch (IOException e) {
            // Connection reset or closed
        } finally {
            closeSocket();
        }
    }

    private boolean processCommand(String line) throws IOException {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            sendResponse("500 5.5.1 Syntax error, empty command");
            return false;
        }

        String upper = trimmed.toUpperCase();

        if (upper.startsWith("EHLO")) {
            handleEhlo(trimmed);
            return false;
        } else if (upper.startsWith("HELO")) {
            handleHelo(trimmed);
            return false;
        } else if (upper.startsWith("AUTH")) {
            handleAuth(trimmed);
            return false;
        } else if (upper.startsWith("MAIL FROM:")) {
            handleMailFrom(trimmed);
            return false;
        } else if (upper.startsWith("RCPT TO:")) {
            handleRcptTo(trimmed);
            return false;
        } else if (upper.equals("DATA")) {
            handleData();
            return false;
        } else if (upper.equals("RSET")) {
            resetMailState();
            sendResponse("250 2.0.0 OK Reset");
            return false;
        } else if (upper.equals("NOOP")) {
            sendResponse("250 2.0.0 OK");
            return false;
        } else if (upper.equals("QUIT")) {
            sendResponse("221 2.0.0 " + config.getSmtpHost() + " Service closing transmission channel");
            return true;
        } else {
            sendResponse("500 5.5.1 Command unrecognized: " + trimmed);
            return false;
        }
    }

    private void handleEhlo(String line) throws IOException {
        String[] parts = line.split("\\s+", 2);
        clientHeloName = parts.length > 1 ? parts[1] : "client";
        helod = true;
        resetMailState();

        sendResponseRaw("250-" + config.getSmtpHost() + "\r\n" +
                "250-AUTH LOGIN PLAIN\r\n" +
                "250-8BITMIME\r\n" +
                "250-SIZE " + config.getMaxMessageSizeBytes() + "\r\n" +
                "250 OK\r\n");
    }

    private void handleHelo(String line) throws IOException {
        String[] parts = line.split("\\s+", 2);
        clientHeloName = parts.length > 1 ? parts[1] : "client";
        helod = true;
        resetMailState();

        sendResponse("250 " + config.getSmtpHost() + " OK");
    }

    private void handleAuth(String line) throws IOException {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            sendResponse("504 5.5.4 Unrecognized authentication type");
            return;
        }

        String mechanism = parts[1].toUpperCase();
        if ("LOGIN".equals(mechanism)) {
            // AUTH LOGIN
            sendResponse("334 VXNlcm5hbWU6"); // Base64 of 'Username:'
            String userB64 = reader.readLine();
            if (userB64 == null) return;
            ProtocolLogger.logServerReceive(ProtocolLogger.LogType.SMTP_SERVER, userB64);

            sendResponse("334 UGFzc3dvcmQ6"); // Base64 of 'Password:'
            String passB64 = reader.readLine();
            if (passB64 == null) return;
            ProtocolLogger.logServerReceive(ProtocolLogger.LogType.SMTP_SERVER, ProtocolLogger.maskSensitive(passB64));

            try {
                String user = new String(Base64.getDecoder().decode(userB64.trim()), StandardCharsets.UTF_8);
                String pass = new String(Base64.getDecoder().decode(passB64.trim()), StandardCharsets.UTF_8);

                if (accountManager.authenticate(user, pass)) {
                    authenticated = true;
                    authenticatedUser = user;
                    sendResponse("235 2.7.0 Authentication successful");
                } else {
                    sendResponse("535 5.7.8 Authentication credentials invalid");
                }
            } catch (Exception e) {
                sendResponse("501 5.5.2 Cannot decode base64 credentials");
            }
        } else if ("PLAIN".equals(mechanism)) {
            // AUTH PLAIN
            String b64Data;
            if (parts.length >= 3) {
                b64Data = parts[2];
            } else {
                sendResponse("334 ");
                b64Data = reader.readLine();
                if (b64Data == null) return;
                ProtocolLogger.logServerReceive(ProtocolLogger.LogType.SMTP_SERVER, ProtocolLogger.maskSensitive(b64Data));
            }

            try {
                byte[] decoded = Base64.getDecoder().decode(b64Data.trim());
                // Format: [authzid] \0 authcid \0 passwd
                List<String> tokens = new ArrayList<>();
                int start = 0;
                for (int i = 0; i < decoded.length; i++) {
                    if (decoded[i] == 0) {
                        tokens.add(new String(decoded, start, i - start, StandardCharsets.UTF_8));
                        start = i + 1;
                    }
                }
                tokens.add(new String(decoded, start, decoded.length - start, StandardCharsets.UTF_8));

                String user = "";
                String pass = "";
                if (tokens.size() >= 3) {
                    user = tokens.get(1);
                    pass = tokens.get(2);
                } else if (tokens.size() == 2) {
                    user = tokens.get(0);
                    pass = tokens.get(1);
                }

                if (!user.isEmpty() && accountManager.authenticate(user, pass)) {
                    authenticated = true;
                    authenticatedUser = user;
                    sendResponse("235 2.7.0 Authentication successful");
                } else {
                    sendResponse("535 5.7.8 Authentication credentials invalid");
                }
            } catch (Exception e) {
                sendResponse("501 5.5.2 Cannot decode base64 authentication string");
            }
        } else {
            sendResponse("504 5.5.4 Unsupported authentication mechanism");
        }
    }

    private void handleMailFrom(String line) throws IOException {
        if (!helod) {
            sendResponse("503 5.5.1 Polite people say HELO / EHLO first");
            return;
        }
        if (config.isRequireSmtpAuth() && !authenticated) {
            sendResponse("530 5.7.0 Authentication required");
            return;
        }

        int colon = line.indexOf(':');
        String addr = line.substring(colon + 1).trim();
        String cleanAddr = EmailValidator.extractCleanAddress(addr);

        if (cleanAddr.isEmpty()) {
            sendResponse("501 5.1.7 Bad sender address syntax");
            return;
        }

        resetMailState();
        mailFrom = cleanAddr;
        sendResponse("250 2.1.0 Sender OK: " + mailFrom);
    }

    private void handleRcptTo(String line) throws IOException {
        if (mailFrom == null) {
            sendResponse("503 5.5.1 Need MAIL FROM command before RCPT TO");
            return;
        }

        int colon = line.indexOf(':');
        String addr = line.substring(colon + 1).trim();
        String cleanAddr = EmailValidator.extractCleanAddress(addr);

        if (cleanAddr.isEmpty()) {
            sendResponse("501 5.1.3 Bad recipient address syntax");
            return;
        }

        String recipientUser = EmailValidator.extractUsername(cleanAddr);
        // Verify user exists in AccountManager (anti-open relay & user verification)
        if (!accountManager.accountExists(recipientUser)) {
            sendResponse("550 5.1.1 <" + cleanAddr + ">: User not found / Mailbox unavailable");
            return;
        }

        rcptTo.add(recipientUser);
        sendResponse("250 2.1.5 Recipient OK: " + recipientUser);
    }

    private void handleData() throws IOException {
        if (mailFrom == null) {
            sendResponse("503 5.5.1 Need MAIL FROM command");
            return;
        }
        if (rcptTo.isEmpty()) {
            sendResponse("503 5.5.1 Need at least one valid RCPT TO command");
            return;
        }

        sendResponse("354 Start mail input; end with <CRLF>.<CRLF>");

        dataBuffer.setLength(0);
        long currentSize = 0;
        boolean exceeded = false;

        String line;
        while ((line = reader.readLine()) != null) {
            // Check for end of DATA
            if (line.equals(".")) {
                break;
            }

            // Dot-unstuffing: RFC 5321 section 4.5.2
            if (line.startsWith("..")) {
                line = line.substring(1);
            }

            currentSize += line.getBytes(StandardCharsets.UTF_8).length + 2; // + CRLF
            if (currentSize > config.getMaxMessageSizeBytes()) {
                exceeded = true;
            }

            if (!exceeded) {
                dataBuffer.append(line).append("\r\n");
            }
        }

        if (exceeded) {
            sendResponse("552 5.3.4 Message size exceeds fixed maximum (" + config.getMaxMessageSizeBytes() + " bytes)");
            resetMailState();
            return;
        }

        // Save email to all recipient mailboxes
        String emlContent = dataBuffer.toString();
        boolean allSaved = true;
        List<String> savedUids = new ArrayList<>();

        for (String recipient : rcptTo) {
            try {
                String uid = mailStorage.saveEmail(recipient, emlContent);
                savedUids.add(uid);
            } catch (Exception e) {
                ProtocolLogger.log(ProtocolLogger.LogType.SMTP_SERVER,
                        "Failed to save email to mailbox '" + recipient + "': " + e.getMessage());
                allSaved = false;
            }
        }

        if (allSaved) {
            sendResponse("250 2.0.0 OK: message queued " + String.join(",", savedUids));
        } else {
            sendResponse("451 4.3.0 Requested action aborted: local error in processing");
        }

        resetMailState();
    }

    private void resetMailState() {
        mailFrom = null;
        rcptTo.clear();
        dataBuffer.setLength(0);
    }

    private void sendResponse(String line) throws IOException {
        sendResponseRaw(line + "\r\n");
    }

    private void sendResponseRaw(String raw) throws IOException {
        ProtocolLogger.logServerSend(ProtocolLogger.LogType.SMTP_SERVER, raw);
        writer.write(raw);
        writer.flush();
    }

    private void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }
}
