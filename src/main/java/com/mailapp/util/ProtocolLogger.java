package com.mailapp.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Protocol and event logger that masks sensitive information (passwords, auth tokens)
 * and dispatches log messages to registered listeners (e.g. Swing UI).
 */
public class ProtocolLogger {

    public enum LogType {
        SMTP_CLIENT,
        SMTP_SERVER,
        POP3_CLIENT,
        POP3_SERVER,
        SYSTEM
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final List<Consumer<String>> GLOBAL_LISTENERS = new CopyOnWriteArrayList<>();

    private ProtocolLogger() {}

    public static void addGlobalListener(Consumer<String> listener) {
        GLOBAL_LISTENERS.add(listener);
    }

    public static void removeGlobalListener(Consumer<String> listener) {
        GLOBAL_LISTENERS.remove(listener);
    }

    public static void log(LogType type, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String masked = maskSensitive(message);
        String formatted = String.format("[%s] [%s] %s", timestamp, type.name(), masked);

        System.out.println(formatted);
        for (Consumer<String> listener : GLOBAL_LISTENERS) {
            try {
                listener.accept(formatted);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }

    public static void logClientSend(LogType type, String rawLine) {
        log(type, "C -> S: " + rawLine.trim());
    }

    public static void logClientReceive(LogType type, String rawLine) {
        log(type, "S -> C: " + rawLine.trim());
    }

    public static void logServerReceive(LogType type, String rawLine) {
        log(type, "RECV <- " + rawLine.trim());
    }

    public static void logServerSend(LogType type, String rawLine) {
        log(type, "SEND -> " + rawLine.trim());
    }

    /**
     * Masks passwords and sensitive authentication parameters.
     */
    public static String maskSensitive(String input) {
        if (input == null) return null;

        // POP3 PASS command
        if (input.matches("(?i).*\\bPASS\\s+.*")) {
            return input.replaceAll("(?i)(\\bPASS\\s+)\\S+", "$1********");
        }

        // SMTP AUTH PLAIN
        if (input.matches("(?i).*\\bAUTH\\s+PLAIN\\s+.*")) {
            return input.replaceAll("(?i)(\\bAUTH\\s+PLAIN\\s+)\\S+", "$1[AUTH_DATA_MASKED]");
        }

        // Generic password parameter
        if (input.matches("(?i).*password\\s*=\\s*\\S+.*")) {
            return input.replaceAll("(?i)(password\\s*=\\s*)\\S+", "$1********");
        }

        return input;
    }
}
