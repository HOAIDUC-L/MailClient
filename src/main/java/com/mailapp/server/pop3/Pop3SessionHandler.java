package com.mailapp.server.pop3;

import com.mailapp.account.AccountManager;
import com.mailapp.model.MailServerConfig;
import com.mailapp.model.Pop3MessageInfo;
import com.mailapp.storage.MailStorage;
import com.mailapp.util.EmailValidator;
import com.mailapp.util.ProtocolLogger;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles a single POP3 client session adhering to RFC 1939 state transitions:
 * AUTHORIZATION -> TRANSACTION -> UPDATE.
 */
public class Pop3SessionHandler implements Runnable {

    private final Socket socket;
    private final MailServerConfig config;
    private final MailStorage mailStorage;
    private final AccountManager accountManager;

    private BufferedReader reader;
    private BufferedWriter writer;

    private Pop3State state = Pop3State.AUTHORIZATION;
    private String pendingUser = null;
    private String authenticatedUser = null;
    private boolean mailboxLocked = false;
    private List<Pop3MessageInfo> messageList = new ArrayList<>();

    public Pop3SessionHandler(Socket socket, MailServerConfig config, MailStorage mailStorage, AccountManager accountManager) {
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

            sendResponse("+OK POP3 server ready");

            String line;
            while ((line = reader.readLine()) != null) {
                ProtocolLogger.logServerReceive(ProtocolLogger.LogType.POP3_SERVER, ProtocolLogger.maskSensitive(line));
                if (processCommand(line)) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            try {
                sendResponse("-ERR Timeout, closing connection");
            } catch (Exception ignored) {}
        } catch (IOException e) {
            // Connection closed or broken
        } finally {
            cleanup();
        }
    }

    private boolean processCommand(String line) throws IOException {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            sendResponse("-ERR Empty command");
            return false;
        }

        String[] parts = trimmed.split("\\s+", 2);
        String cmd = parts[0].toUpperCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (state) {
            case AUTHORIZATION:
                return handleAuthorizationCommand(cmd, arg);
            case TRANSACTION:
                return handleTransactionCommand(cmd, arg);
            case UPDATE:
                // Already in update state, no more commands accepted
                return true;
            default:
                sendResponse("-ERR Invalid state");
                return false;
        }
    }

    private boolean handleAuthorizationCommand(String cmd, String arg) throws IOException {
        switch (cmd) {
            case "USER":
                if (arg.isEmpty()) {
                    sendResponse("-ERR Missing username");
                } else {
                    pendingUser = EmailValidator.extractUsername(arg);
                    sendResponse("+OK User name accepted, send PASS");
                }
                return false;

            case "PASS":
                if (pendingUser == null) {
                    sendResponse("-ERR USER command required before PASS");
                    return false;
                }
                if (accountManager.authenticate(pendingUser, arg)) {
                    // Try acquiring exclusive mailbox lock
                    if (!mailStorage.acquireMailboxLock(pendingUser)) {
                        sendResponse("-ERR [IN-USE] Mailbox is locked by another session");
                        pendingUser = null;
                        return false;
                    }

                    mailboxLocked = true;
                    authenticatedUser = pendingUser;
                    pendingUser = null;
                    state = Pop3State.TRANSACTION;

                    // Load mailbox snapshot
                    messageList = mailStorage.getMailboxMessages(authenticatedUser);
                    sendResponse("+OK Mailbox ready (" + messageList.size() + " messages)");
                } else {
                    sendResponse("-ERR Authentication failed");
                    pendingUser = null;
                }
                return false;

            case "QUIT":
                sendResponse("+OK POP3 server signing off");
                return true;

            default:
                sendResponse("-ERR Command not valid in AUTHORIZATION state");
                return false;
        }
    }

    private boolean handleTransactionCommand(String cmd, String arg) throws IOException {
        switch (cmd) {
            case "STAT":
                handleStat();
                return false;

            case "LIST":
                handleList(arg);
                return false;

            case "UIDL":
                handleUidl(arg);
                return false;

            case "RETR":
                handleRetr(arg);
                return false;

            case "DELE":
                handleDele(arg);
                return false;

            case "RSET":
                handleRset();
                return false;

            case "NOOP":
                sendResponse("+OK");
                return false;

            case "QUIT":
                handleQuitTransaction();
                return true;

            default:
                sendResponse("-ERR Command unrecognized: " + cmd);
                return false;
        }
    }

    private void handleStat() throws IOException {
        int count = 0;
        long totalSize = 0;
        for (Pop3MessageInfo msg : messageList) {
            if (!msg.isMarkedForDeletion()) {
                count++;
                totalSize += msg.getSizeInBytes();
            }
        }
        sendResponse("+OK " + count + " " + totalSize);
    }

    private void handleList(String arg) throws IOException {
        if (!arg.isEmpty()) {
            // LIST <msgNum>
            Pop3MessageInfo msg = findActiveMessage(arg);
            if (msg == null) {
                sendResponse("-ERR No such message");
            } else {
                sendResponse("+OK " + msg.getMessageNumber() + " " + msg.getSizeInBytes());
            }
        } else {
            // LIST all
            int count = 0;
            long totalSize = 0;
            for (Pop3MessageInfo msg : messageList) {
                if (!msg.isMarkedForDeletion()) {
                    count++;
                    totalSize += msg.getSizeInBytes();
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("+OK ").append(count).append(" messages (").append(totalSize).append(" octets)\r\n");
            for (Pop3MessageInfo msg : messageList) {
                if (!msg.isMarkedForDeletion()) {
                    sb.append(msg.getMessageNumber()).append(" ").append(msg.getSizeInBytes()).append("\r\n");
                }
            }
            sb.append(".\r\n");
            sendResponseRaw(sb.toString());
        }
    }

    private void handleUidl(String arg) throws IOException {
        if (!arg.isEmpty()) {
            // UIDL <msgNum>
            Pop3MessageInfo msg = findActiveMessage(arg);
            if (msg == null) {
                sendResponse("-ERR No such message");
            } else {
                sendResponse("+OK " + msg.getMessageNumber() + " " + msg.getUidl());
            }
        } else {
            // UIDL all
            StringBuilder sb = new StringBuilder();
            sb.append("+OK\r\n");
            for (Pop3MessageInfo msg : messageList) {
                if (!msg.isMarkedForDeletion()) {
                    sb.append(msg.getMessageNumber()).append(" ").append(msg.getUidl()).append("\r\n");
                }
            }
            sb.append(".\r\n");
            sendResponseRaw(sb.toString());
        }
    }

    private void handleRetr(String arg) throws IOException {
        if (arg.isEmpty()) {
            sendResponse("-ERR Missing message number");
            return;
        }

        Pop3MessageInfo msg = findActiveMessage(arg);
        if (msg == null) {
            sendResponse("-ERR No such message");
            return;
        }

        try {
            String rawContent = mailStorage.readEmailRaw(authenticatedUser, msg.getUidl());
            long sizeBytes = rawContent.getBytes(StandardCharsets.UTF_8).length;
            sendResponse("+OK " + sizeBytes + " octets");

            // Stream lines with RFC 1939 dot-stuffing
            try (BufferedReader lineReader = new BufferedReader(new StringReader(rawContent))) {
                String line;
                while ((line = lineReader.readLine()) != null) {
                    if (line.startsWith(".")) {
                        // Dot-stuffing: prepend an extra dot
                        line = "." + line;
                    }
                    sendResponseRaw(line + "\r\n");
                }
            }
            // Terminator
            sendResponseRaw(".\r\n");

        } catch (Exception e) {
            sendResponse("-ERR Cannot read message: " + e.getMessage());
        }
    }

    private void handleDele(String arg) throws IOException {
        if (arg.isEmpty()) {
            sendResponse("-ERR Missing message number");
            return;
        }

        Pop3MessageInfo msg = findActiveMessage(arg);
        if (msg == null) {
            sendResponse("-ERR Message already deleted or invalid message number");
            return;
        }

        msg.setMarkedForDeletion(true);
        sendResponse("+OK message " + msg.getMessageNumber() + " marked for deletion");
    }

    private void handleRset() throws IOException {
        int count = 0;
        long totalSize = 0;
        for (Pop3MessageInfo msg : messageList) {
            msg.setMarkedForDeletion(false);
            count++;
            totalSize += msg.getSizeInBytes();
        }
        sendResponse("+OK maildrop has " + count + " messages (" + totalSize + " octets)");
    }

    private void handleQuitTransaction() throws IOException {
        state = Pop3State.UPDATE;
        List<String> toDelete = new ArrayList<>();
        for (Pop3MessageInfo msg : messageList) {
            if (msg.isMarkedForDeletion()) {
                toDelete.add(msg.getUidl());
            }
        }

        // Commit deletions
        if (!toDelete.isEmpty()) {
            try {
                mailStorage.deleteEmails(authenticatedUser, toDelete);
            } catch (Exception e) {
                ProtocolLogger.log(ProtocolLogger.LogType.POP3_SERVER,
                        "Error deleting committed emails: " + e.getMessage());
            }
        }

        // Release mailbox lock
        if (mailboxLocked && authenticatedUser != null) {
            mailStorage.releaseMailboxLock(authenticatedUser);
            mailboxLocked = false;
        }

        sendResponse("+OK POP3 server signing off (" + toDelete.size() + " messages deleted)");
    }

    private Pop3MessageInfo findActiveMessage(String arg) {
        try {
            int num = Integer.parseInt(arg);
            for (Pop3MessageInfo msg : messageList) {
                if (msg.getMessageNumber() == num && !msg.isMarkedForDeletion()) {
                    return msg;
                }
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private void cleanup() {
        // If aborted before UPDATE state, do not delete messages; simply release lock
        if (mailboxLocked && authenticatedUser != null) {
            mailStorage.releaseMailboxLock(authenticatedUser);
            mailboxLocked = false;
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    private void sendResponse(String line) throws IOException {
        sendResponseRaw(line + "\r\n");
    }

    private void sendResponseRaw(String raw) throws IOException {
        ProtocolLogger.logServerSend(ProtocolLogger.LogType.POP3_SERVER, raw);
        writer.write(raw);
        writer.flush();
    }
}
