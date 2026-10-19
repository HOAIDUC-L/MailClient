package com.mailapp.model;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration options for the local SMTP and POP3 mail server.
 */
public class MailServerConfig {
    private String smtpHost = "127.0.0.1";
    private int smtpPort = 2525;
    private String pop3Host = "127.0.0.1";
    private int pop3Port = 1110;
    private Path dataDirectory = Paths.get("data");
    private boolean requireSmtpAuth = false;
    private long maxMessageSizeBytes = 10 * 1024 * 1024; // 10MB
    private int socketTimeoutMs = 60000; // 60 seconds

    public MailServerConfig() {}

    public MailServerConfig(int smtpPort, int pop3Port, Path dataDirectory) {
        this.smtpPort = smtpPort;
        this.pop3Port = pop3Port;
        this.dataDirectory = dataDirectory;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getPop3Host() {
        return pop3Host;
    }

    public void setPop3Host(String pop3Host) {
        this.pop3Host = pop3Host;
    }

    public int getPop3Port() {
        return pop3Port;
    }

    public void setPop3Port(int pop3Port) {
        this.pop3Port = pop3Port;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public void setDataDirectory(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public boolean isRequireSmtpAuth() {
        return requireSmtpAuth;
    }

    public void setRequireSmtpAuth(boolean requireSmtpAuth) {
        this.requireSmtpAuth = requireSmtpAuth;
    }

    public long getMaxMessageSizeBytes() {
        return maxMessageSizeBytes;
    }

    public void setMaxMessageSizeBytes(long maxMessageSizeBytes) {
        this.maxMessageSizeBytes = maxMessageSizeBytes;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public Path getAccountsFilePath() {
        return dataDirectory.resolve("accounts").resolve("accounts.json");
    }

    public Path getMailboxesDirectory() {
        return dataDirectory.resolve("mailboxes");
    }

    public Path getLogsDirectory() {
        return dataDirectory.resolve("logs");
    }
}
