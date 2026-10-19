package com.mailapp.model;

import java.nio.file.Path;

/**
 * Metadata about an email message in a POP3 mailbox session.
 */
public class Pop3MessageInfo {
    private int messageNumber; // 1-based index in session
    private String uidl;         // Unique ID (e.g. filename without extension or messageId)
    private long sizeInBytes;
    private boolean markedForDeletion;
    private Path filePath;
    private String subject;
    private String from;
    private String date;

    public Pop3MessageInfo(int messageNumber, String uidl, long sizeInBytes, Path filePath) {
        this.messageNumber = messageNumber;
        this.uidl = uidl;
        this.sizeInBytes = sizeInBytes;
        this.filePath = filePath;
        this.markedForDeletion = false;
    }

    public int getMessageNumber() {
        return messageNumber;
    }

    public void setMessageNumber(int messageNumber) {
        this.messageNumber = messageNumber;
    }

    public String getUidl() {
        return uidl;
    }

    public void setUidl(String uidl) {
        this.uidl = uidl;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public void setSizeInBytes(long sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    public boolean isMarkedForDeletion() {
        return markedForDeletion;
    }

    public void setMarkedForDeletion(boolean markedForDeletion) {
        this.markedForDeletion = markedForDeletion;
    }

    public Path getFilePath() {
        return filePath;
    }

    public void setFilePath(Path filePath) {
        this.filePath = filePath;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return messageNumber + " " + sizeInBytes + (markedForDeletion ? " [DELETED]" : "");
    }
}
