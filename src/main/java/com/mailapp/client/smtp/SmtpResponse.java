package com.mailapp.client.smtp;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed response from an SMTP server.
 */
public class SmtpResponse {
    private final int code;
    private final String message;
    private final List<String> rawLines;

    public SmtpResponse(int code, String message, List<String> rawLines) {
        this.code = code;
        this.message = message;
        this.rawLines = rawLines != null ? new ArrayList<>(rawLines) : new ArrayList<>();
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getRawLines() {
        return rawLines;
    }

    public boolean isPositiveCompletion() {
        return code >= 200 && code < 300;
    }

    public boolean isPositiveIntermediate() {
        return code >= 300 && code < 400;
    }

    public boolean isTransientNegative() {
        return code >= 400 && code < 500;
    }

    public boolean isPermanentNegative() {
        return code >= 500 && code < 600;
    }

    public boolean isSuccess() {
        return code >= 200 && code < 400;
    }

    @Override
    public String toString() {
        return code + " " + message;
    }
}
