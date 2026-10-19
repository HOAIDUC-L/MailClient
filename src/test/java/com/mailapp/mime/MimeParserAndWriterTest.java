package com.mailapp.mime;

import com.mailapp.model.EmailMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MimeParserAndWriterTest {

    @Test
    public void testRfc2047HeaderEncodingAndDecoding() {
        String plainSubject = "Hello World";
        String encodedPlain = MimeHeaderEncoder.encodeHeader(plainSubject);
        assertEquals("Hello World", encodedPlain); // ASCII remains untouched

        String unicodeSubject = "Tiêu đề thư Tiếng Việt: Chúc mừng năm mới!";
        String encodedUnicode = MimeHeaderEncoder.encodeHeader(unicodeSubject);
        assertTrue(encodedUnicode.startsWith("=?UTF-8?B?"));
        assertTrue(encodedUnicode.endsWith("?="));

        String decodedUnicode = MimeHeaderEncoder.decodeHeader(encodedUnicode);
        assertEquals(unicodeSubject, decodedUnicode);
    }

    @Test
    public void testEmlWriteAndParseRoundTrip() {
        EmailMessage original = new EmailMessage(
                "duc@localhost",
                List.of("lan@localhost", "admin@localhost"),
                List.of("cc@localhost"),
                "Kiểm thử hệ thống email SMTP và POP3",
                "Nội dung email kiểm thử với ký tự Tiếng Việt có dấu.\n.Dòng có dấu chấm\nTrân trọng."
        );

        String rawEml = MimeWriter.writeToEml(original);
        assertNotNull(rawEml);
        assertTrue(rawEml.contains("From: duc@localhost"));
        assertTrue(rawEml.contains("To: lan@localhost, admin@localhost"));
        assertTrue(rawEml.contains("Cc: cc@localhost"));

        EmailMessage parsed = MimeParser.parseEml(rawEml);
        assertEquals("duc@localhost", parsed.getFrom());
        assertEquals(2, parsed.getTo().size());
        assertTrue(parsed.getTo().contains("lan@localhost"));
        assertTrue(parsed.getTo().contains("admin@localhost"));
        assertEquals("cc@localhost", parsed.getCc().get(0));
        assertEquals("Kiểm thử hệ thống email SMTP và POP3", parsed.getSubject());
        assertTrue(parsed.getBody().contains("Nội dung email kiểm thử với ký tự Tiếng Việt có dấu."));
    }

    @Test
    public void testFoldedHeaderParsing() {
        String emlWithFoldedHeader = "From: duc@localhost\r\n" +
                "Subject: Day la tieu de\r\n" +
                " rat dai duoc chia lam\r\n" +
                " nhieu dong tiep theo\r\n" +
                "To: lan@localhost\r\n" +
                "\r\n" +
                "Noi dung email.";

        EmailMessage parsed = MimeParser.parseEml(emlWithFoldedHeader);
        assertEquals("Day la tieu de rat dai duoc chia lam nhieu dong tiep theo", parsed.getSubject());
        assertEquals("duc@localhost", parsed.getFrom());
        assertEquals("Noi dung email.", parsed.getBody());
    }

    @Test
    public void testMultipartHandling() {
        String multipartEml = "From: sender@example.com\r\n" +
                "To: recipient@example.com\r\n" +
                "Subject: Multipart Test\r\n" +
                "Content-Type: multipart/alternative; boundary=\"boundary42\"\r\n" +
                "\r\n" +
                "--boundary42\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "This is the plain text part of multipart email.\r\n" +
                "--boundary42\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "\r\n" +
                "<b>This is HTML</b>\r\n" +
                "--boundary42--";

        EmailMessage parsed = MimeParser.parseEml(multipartEml);
        assertEquals("Multipart Test", parsed.getSubject());
        assertEquals("This is the plain text part of multipart email.", parsed.getBody());
    }
}
