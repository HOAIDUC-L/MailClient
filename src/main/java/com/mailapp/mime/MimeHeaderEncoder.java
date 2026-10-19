package com.mailapp.mime;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encodes and decodes MIME header fields (such as Subject) according to RFC 2047.
 */
public class MimeHeaderEncoder {

    private static final Pattern ENCODED_WORD_PATTERN = Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]+)\\?=");

    private MimeHeaderEncoder() {}

    /**
     * Encodes a string for email header if it contains non-ASCII characters.
     * Uses =?UTF-8?B?...?= format.
     */
    public static String encodeHeader(String text) {
        if (text == null) return "";
        // Check if string contains non-ASCII
        boolean hasNonAscii = false;
        for (char c : text.toCharArray()) {
            if (c > 127) {
                hasNonAscii = true;
                break;
            }
        }
        if (!hasNonAscii) {
            return text;
        }

        String b64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        return "=?UTF-8?B?" + b64 + "?=";
    }

    /**
     * Decodes an RFC 2047 encoded header string (or returns original text if not encoded).
     */
    public static String decodeHeader(String header) {
        if (header == null) return "";
        Matcher matcher = ENCODED_WORD_PATTERN.matcher(header);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String charsetName = matcher.group(1);
            String encoding = matcher.group(2).toUpperCase();
            String encodedText = matcher.group(3);

            try {
                Charset charset = Charset.forName(charsetName);
                String decodedWord;
                if ("B".equals(encoding)) {
                    byte[] bytes = Base64.getDecoder().decode(encodedText.replaceAll("\\s+", ""));
                    decodedWord = new String(bytes, charset);
                } else if ("Q".equals(encoding)) {
                    decodedWord = decodeQuotedPrintableWord(encodedText, charset);
                } else {
                    decodedWord = matcher.group(0);
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(decodedWord));
            } catch (Exception e) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String decodeQuotedPrintableWord(String word, Charset charset) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '_') {
                baos.write(' ');
            } else if (c == '=' && i + 2 < word.length()) {
                String hex = word.substring(i + 1, i + 3);
                try {
                    int b = Integer.parseInt(hex, 16);
                    baos.write(b);
                    i += 2;
                } catch (NumberFormatException e) {
                    baos.write(c);
                }
            } else {
                baos.write((byte) c);
            }
        }
        return new String(baos.toByteArray(), charset);
    }
}
