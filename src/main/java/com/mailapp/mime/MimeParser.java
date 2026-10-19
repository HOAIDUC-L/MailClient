package com.mailapp.mime;

import com.mailapp.model.EmailMessage;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw .eml (RFC 5322 / MIME) content into an EmailMessage object.
 */
public class MimeParser {

    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("boundary=[\"']?([^\"';\\s]+)[\"']?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=[\"']?([^\"';\\s]+)[\"']?", Pattern.CASE_INSENSITIVE);

    private MimeParser() {}

    /**
     * Parses raw RFC 5322 email string into EmailMessage.
     */
    public static EmailMessage parseEml(String rawEml) {
        EmailMessage msg = new EmailMessage();
        msg.setRawContent(rawEml);

        if (rawEml == null || rawEml.isBlank()) {
            return msg;
        }

        // Split headers and body at the first empty line
        int headerEndIndex = -1;
        int bodyStartIndex = -1;

        if (rawEml.contains("\r\n\r\n")) {
            headerEndIndex = rawEml.indexOf("\r\n\r\n");
            bodyStartIndex = headerEndIndex + 4;
        } else if (rawEml.contains("\n\n")) {
            headerEndIndex = rawEml.indexOf("\n\n");
            bodyStartIndex = headerEndIndex + 2;
        }

        String headerBlock;
        String rawBody;
        if (headerEndIndex >= 0) {
            headerBlock = rawEml.substring(0, headerEndIndex);
            rawBody = rawEml.substring(bodyStartIndex);
        } else {
            headerBlock = rawEml;
            rawBody = "";
        }

        // Parse and unfold headers
        Map<String, String> headers = parseHeaders(headerBlock);

        // Populate fields
        msg.setMessageId(headers.getOrDefault("message-id", ""));
        msg.setFrom(headers.getOrDefault("from", ""));
        msg.setDate(headers.getOrDefault("date", ""));
        msg.setContentType(headers.getOrDefault("content-type", "text/plain; charset=UTF-8"));

        String subjectRaw = headers.getOrDefault("subject", "");
        msg.setSubject(MimeHeaderEncoder.decodeHeader(subjectRaw));

        String toHeader = headers.get("to");
        if (toHeader != null && !toHeader.isBlank()) {
            msg.setTo(splitAddresses(toHeader));
        }

        String ccHeader = headers.get("cc");
        if (ccHeader != null && !ccHeader.isBlank()) {
            msg.setCc(splitAddresses(ccHeader));
        }

        // Parse Body
        String contentType = msg.getContentType().toLowerCase();
        String transferEncoding = headers.getOrDefault("content-transfer-encoding", "").toLowerCase();
        Charset charset = extractCharset(contentType);

        if (contentType.contains("multipart/")) {
            String parsedBody = parseMultipart(rawBody, msg.getContentType());
            msg.setBody(parsedBody);
        } else {
            String decodedBody = decodeBody(rawBody, transferEncoding, charset);
            msg.setBody(decodedBody);
        }

        return msg;
    }

    /**
     * Unfolds and parses RFC 5322 header lines into a case-insensitive map.
     */
    public static Map<String, String> parseHeaders(String headerBlock) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (headerBlock == null || headerBlock.isBlank()) {
            return headers;
        }

        List<String> unfoldedLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(headerBlock))) {
            String line;
            StringBuilder currentLine = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    // Header continuation (folding)
                    currentLine.append(" ").append(line.trim());
                } else {
                    if (currentLine.length() > 0) {
                        unfoldedLines.add(currentLine.toString());
                    }
                    currentLine = new StringBuilder(line);
                }
            }
            if (currentLine.length() > 0) {
                unfoldedLines.add(currentLine.toString());
            }
        } catch (Exception e) {
            // Should not happen with StringReader
        }

        for (String headerLine : unfoldedLines) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex > 0) {
                String key = headerLine.substring(0, colonIndex).trim().toLowerCase();
                String value = headerLine.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }

        return headers;
    }

    private static List<String> splitAddresses(String addrList) {
        List<String> result = new ArrayList<>();
        String[] parts = addrList.split(",");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static Charset extractCharset(String contentType) {
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String parseMultipart(String rawBody, String contentType) {
        Matcher matcher = BOUNDARY_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return "[Thông báo: Định dạng multipart không xác định được boundary]\n\n" + rawBody;
        }
        String boundary = matcher.group(1);
        String delimiter = "--" + boundary;

        String[] parts = rawBody.split(Pattern.quote(delimiter));
        StringBuilder extractedText = new StringBuilder();
        boolean foundTextPart = false;

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty() || trimmed.equals("--")) {
                continue; // Epilogue or closing boundary
            }

            int splitIdx = trimmed.indexOf("\r\n\r\n");
            int offset = 4;
            if (splitIdx < 0) {
                splitIdx = trimmed.indexOf("\n\n");
                offset = 2;
            }

            if (splitIdx >= 0) {
                String partHeaders = trimmed.substring(0, splitIdx);
                String partBody = trimmed.substring(splitIdx + offset);
                Map<String, String> pHeaders = parseHeaders(partHeaders);
                String pType = pHeaders.getOrDefault("content-type", "text/plain").toLowerCase();
                String pEncoding = pHeaders.getOrDefault("content-transfer-encoding", "").toLowerCase();
                Charset pCharset = extractCharset(pType);

                if (pType.startsWith("text/plain")) {
                    String decoded = decodeBody(partBody, pEncoding, pCharset);
                    extractedText.append(decoded).append("\n");
                    foundTextPart = true;
                    break;
                } else if (pType.startsWith("text/html") && !foundTextPart) {
                    String decoded = decodeBody(partBody, pEncoding, pCharset);
                    extractedText.append(decoded).append("\n");
                }
            }
        }

        if (extractedText.length() > 0) {
            return extractedText.toString().trim();
        }

        return "[Thông báo: Email multipart chứa nội dung phức tạp hoặc đính kèm. Xem thô bên dưới:]\n\n" + rawBody;
    }

    private static String decodeBody(String rawBody, String transferEncoding, Charset charset) {
        if (rawBody == null) return "";
        try {
            if ("base64".equals(transferEncoding)) {
                String cleanB64 = rawBody.replaceAll("\\s+", "");
                byte[] decoded = Base64.getDecoder().decode(cleanB64);
                return new String(decoded, charset != null ? charset : StandardCharsets.UTF_8);
            } else if ("quoted-printable".equals(transferEncoding)) {
                return decodeQuotedPrintable(rawBody, charset != null ? charset : StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // Fallback to raw if decoding fails
            return rawBody;
        }
        return rawBody;
    }

    private static String decodeQuotedPrintable(String text, Charset charset) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '=') {
                if (i + 1 < len && (text.charAt(i + 1) == '\r' || text.charAt(i + 1) == '\n')) {
                    // Soft line break
                    i++;
                    if (i < len && text.charAt(i) == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                } else if (i + 2 < len) {
                    String hex = text.substring(i + 1, i + 3);
                    try {
                        baos.write(Integer.parseInt(hex, 16));
                        i += 2;
                    } catch (NumberFormatException e) {
                        baos.write('=');
                    }
                } else {
                    baos.write('=');
                }
            } else {
                baos.write((byte) c);
            }
        }
        return new String(baos.toByteArray(), charset);
    }
}
