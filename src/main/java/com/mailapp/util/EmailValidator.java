package com.mailapp.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for validating and sanitizing email addresses, headers, and usernames.
 */
public class EmailValidator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern ANGLE_ADDR_PATTERN = Pattern.compile("<([^>]+)>");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+(@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}|@[a-zA-Z0-9.-]+)?$");

    private EmailValidator() {}

    /**
     * Checks if a string contains CRLF characters (Header Injection protection).
     */
    public static boolean containsCrlf(String text) {
        if (text == null) return false;
        return text.contains("\r") || text.contains("\n");
    }

    /**
     * Validates that a header string has no CRLF injection attempts.
     * @throws IllegalArgumentException if CRLF is detected.
     */
    public static void validateHeaderNoCrlf(String headerName, String headerValue) {
        if (containsCrlf(headerValue)) {
            throw new IllegalArgumentException("CRLF injection detected in header '" + headerName + "': " + headerValue);
        }
    }

    /**
     * Extracts email address from string, e.g., "Duc <duc@localhost>" -> "duc@localhost"
     * or "<duc@localhost>" -> "duc@localhost" or "duc" -> "duc".
     */
    public static String extractCleanAddress(String address) {
        if (address == null) return "";
        String trimmed = address.trim();
        Matcher matcher = ANGLE_ADDR_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return trimmed;
    }

    /**
     * Extracts local username from an email address (e.g. "duc@localhost" -> "duc", "lan" -> "lan").
     */
    public static String extractUsername(String address) {
        String clean = extractCleanAddress(address);
        int atIndex = clean.indexOf('@');
        if (atIndex >= 0) {
            return clean.substring(0, atIndex).toLowerCase();
        }
        return clean.toLowerCase();
    }

    /**
     * Validates that username is safe for filesystem path usage (prevents Path Traversal).
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        if (username.contains("..") || username.contains("/") || username.contains("\\")) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * Validates an email format or local username.
     */
    public static boolean isValidEmailOrUsername(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String clean = extractCleanAddress(email);
        if (clean.isBlank()) return false;
        return EMAIL_PATTERN.matcher(clean).matches();
    }
}
