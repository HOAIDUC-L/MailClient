package com.mailapp.client.pop3;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed response from a POP3 server (+OK or -ERR).
 */
public class Pop3Response {
    private final boolean ok;
    private final String message;
    private final List<String> dataLines;

    public Pop3Response(boolean ok, String message) {
        this(ok, message, new ArrayList<>());
    }

    public Pop3Response(boolean ok, String message, List<String> dataLines) {
        this.ok = ok;
        this.message = message != null ? message : "";
        this.dataLines = dataLines != null ? new ArrayList<>(dataLines) : new ArrayList<>();
    }

    public boolean isOk() {
        return ok;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getDataLines() {
        return dataLines;
    }

    @Override
    public String toString() {
        return (ok ? "+OK " : "-ERR ") + message;
    }
}
