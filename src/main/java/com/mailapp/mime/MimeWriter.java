package com.mailapp.mime;

import com.mailapp.model.EmailMessage;
import com.mailapp.util.EmailValidator;

/**
 * Serializes EmailMessage objects to standard RFC 5322 / MIME format (.eml).
 */
public class MimeWriter {

    private static final String CRLF = "\r\n";

    private MimeWriter() {}

    /**
     * Converts an EmailMessage object into a standard .eml string with CRLF line endings.
     */
    public static String writeToEml(EmailMessage message) {
        StringBuilder sb = new StringBuilder();

        // 1. Message-ID
        if (message.getMessageId() != null && !message.getMessageId().isBlank()) {
            EmailValidator.validateHeaderNoCrlf("Message-ID", message.getMessageId());
            sb.append("Message-ID: ").append(message.getMessageId()).append(CRLF);
        }

        // 2. Date
        if (message.getDate() != null && !message.getDate().isBlank()) {
            EmailValidator.validateHeaderNoCrlf("Date", message.getDate());
            sb.append("Date: ").append(message.getDate()).append(CRLF);
        }

        // 3. From
        if (message.getFrom() != null && !message.getFrom().isBlank()) {
            EmailValidator.validateHeaderNoCrlf("From", message.getFrom());
            sb.append("From: ").append(message.getFrom()).append(CRLF);
        }

        // 4. To
        if (message.getTo() != null && !message.getTo().isEmpty()) {
            String toList = String.join(", ", message.getTo());
            EmailValidator.validateHeaderNoCrlf("To", toList);
            sb.append("To: ").append(toList).append(CRLF);
        }

        // 5. Cc
        if (message.getCc() != null && !message.getCc().isEmpty()) {
            String ccList = String.join(", ", message.getCc());
            EmailValidator.validateHeaderNoCrlf("Cc", ccList);
            sb.append("Cc: ").append(ccList).append(CRLF);
        }

        // 6. Subject (RFC 2047 encoded if contains Unicode)
        if (message.getSubject() != null) {
            String encodedSubject = MimeHeaderEncoder.encodeHeader(message.getSubject());
            EmailValidator.validateHeaderNoCrlf("Subject", encodedSubject);
            sb.append("Subject: ").append(encodedSubject).append(CRLF);
        }

        // 7. MIME Headers
        sb.append("MIME-Version: 1.0").append(CRLF);
        sb.append("Content-Type: ").append(message.getContentType() != null ? message.getContentType() : "text/plain; charset=UTF-8").append(CRLF);
        sb.append("Content-Transfer-Encoding: 8bit").append(CRLF);

        // Header and Body separator
        sb.append(CRLF);

        // Body with CRLF normalization
        String body = message.getBody() != null ? message.getBody() : "";
        String normalizedBody = body.replace("\r\n", "\n").replace("\r", "\n").replace("\n", CRLF);
        sb.append(normalizedBody);

        return sb.toString();
    }
}
