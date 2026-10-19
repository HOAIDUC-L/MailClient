package com.mailapp.model;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Represents an email message with headers and body.
 */
public class EmailMessage {
    private String messageId;
    private String from;
    private List<String> to = new ArrayList<>();
    private List<String> cc = new ArrayList<>();
    private String subject;
    private String date;
    private String contentType = "text/plain; charset=UTF-8";
    private String body;
    private String rawContent;
    private long sizeInBytes;

    public EmailMessage() {
        this.messageId = "<" + UUID.randomUUID() + "@localhost>";
        this.date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now());
    }

    public EmailMessage(String from, List<String> to, List<String> cc, String subject, String body) {
        this();
        this.from = from;
        if (to != null) this.to.addAll(to);
        if (cc != null) this.cc.addAll(cc);
        this.subject = subject != null ? subject : "";
        this.body = body != null ? body : "";
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public List<String> getTo() {
        return to;
    }

    public void setTo(List<String> to) {
        this.to = to != null ? new ArrayList<>(to) : new ArrayList<>();
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc != null ? new ArrayList<>(cc) : new ArrayList<>();
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
        if (rawContent != null) {
            this.sizeInBytes = rawContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public void setSizeInBytes(long sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    /**
     * Returns all recipient addresses (To + Cc).
     */
    public List<String> getAllRecipients() {
        List<String> all = new ArrayList<>(to);
        all.addAll(cc);
        return all;
    }

    @Override
    public String toString() {
        return "EmailMessage{" +
                "messageId='" + messageId + '\'' +
                ", from='" + from + '\'' +
                ", to=" + to +
                ", subject='" + subject + '\'' +
                ", date='" + date + '\'' +
                ", size=" + sizeInBytes +
                '}';
    }
}
