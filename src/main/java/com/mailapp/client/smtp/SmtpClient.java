package com.mailapp.client.smtp;

import com.mailapp.mime.MimeWriter;
import com.mailapp.model.EmailMessage;
import com.mailapp.util.EmailValidator;
import com.mailapp.util.ProtocolLogger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Low-level SMTP Client implementation using Java TCP Sockets.
 */
public class SmtpClient implements AutoCloseable {

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean connected = false;

    public SmtpClient() {}

    /**
     * Connects to the SMTP server and validates the 220 greeting.
     */
    public SmtpResponse connect(String host, int port, int timeoutMs, boolean useSsl) throws IOException {
        if (useSsl) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket();
            sslSocket.connect(new InetSocketAddress(host, port), timeoutMs);
            sslSocket.setSoTimeout(timeoutMs);
            sslSocket.startHandshake();
            this.socket = sslSocket;
        } else {
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(host, port), timeoutMs);
            this.socket.setSoTimeout(timeoutMs);
        }

        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.connected = true;

        ProtocolLogger.log(ProtocolLogger.LogType.SMTP_CLIENT, "Connected to SMTP server " + host + ":" + port);

        // Read 220 greeting
        SmtpResponse greeting = readResponse();
        if (greeting.getCode() != 220) {
            close();
            throw new IOException("Server rejected greeting with: " + greeting);
        }
        return greeting;
    }

    /**
     * Sends EHLO and falls back to HELO if EHLO fails.
     */
    public SmtpResponse sendEhlo(String clientDomain) throws IOException {
        SmtpResponse response = sendCommand("EHLO " + (clientDomain != null ? clientDomain : "localhost"));
        if (!response.isSuccess()) {
            ProtocolLogger.log(ProtocolLogger.LogType.SMTP_CLIENT, "EHLO rejected, falling back to HELO");
            response = sendCommand("HELO " + (clientDomain != null ? clientDomain : "localhost"));
        }
        return response;
    }

    /**
     * Performs SMTP AUTH LOGIN.
     */
    public SmtpResponse authenticateLogin(String username, String password) throws IOException {
        SmtpResponse authResp = sendCommand("AUTH LOGIN");
        if (authResp.getCode() != 334) {
            return authResp;
        }

        // Send base64 username
        String userB64 = Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8));
        SmtpResponse userResp = sendCommand(userB64);
        if (userResp.getCode() != 334) {
            return userResp;
        }

        // Send base64 password (masked in logs)
        String passB64 = Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
        ProtocolLogger.logClientSend(ProtocolLogger.LogType.SMTP_CLIENT, "[PASSWORD_DATA_MASKED]");
        writer.write(passB64 + "\r\n");
        writer.flush();

        return readResponse();
    }

    /**
     * Performs SMTP AUTH PLAIN.
     */
    public SmtpResponse authenticatePlain(String username, String password) throws IOException {
        // Format: \0username\0password
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0);
        baos.write(username.getBytes(StandardCharsets.UTF_8));
        baos.write(0);
        baos.write(password.getBytes(StandardCharsets.UTF_8));
        String plainB64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        ProtocolLogger.logClientSend(ProtocolLogger.LogType.SMTP_CLIENT, "AUTH PLAIN [DATA_MASKED]");
        writer.write("AUTH PLAIN " + plainB64 + "\r\n");
        writer.flush();
        return readResponse();
    }

    /**
     * Sends MAIL FROM:<address>.
     */
    public SmtpResponse sendMailFrom(String sender) throws IOException {
        String clean = EmailValidator.extractCleanAddress(sender);
        EmailValidator.validateHeaderNoCrlf("MAIL FROM", clean);
        return sendCommand("MAIL FROM:<" + clean + ">");
    }

    /**
     * Sends RCPT TO:<address>.
     */
    public SmtpResponse sendRcptTo(String recipient) throws IOException {
        String clean = EmailValidator.extractCleanAddress(recipient);
        EmailValidator.validateHeaderNoCrlf("RCPT TO", clean);
        return sendCommand("RCPT TO:<" + clean + ">");
    }

    /**
     * Sends DATA and message content with dot-stuffing.
     */
    public SmtpResponse sendData(String emlContent) throws IOException {
        SmtpResponse dataResp = sendCommand("DATA");
        if (dataResp.getCode() != 354) {
            return dataResp;
        }

        ProtocolLogger.log(ProtocolLogger.LogType.SMTP_CLIENT, "Streaming message body with dot-stuffing...");

        // Stream lines with dot-stuffing (RFC 5321 section 4.5.2)
        try (BufferedReader lineReader = new BufferedReader(new StringReader(emlContent))) {
            String line;
            while ((line = lineReader.readLine()) != null) {
                if (line.startsWith(".")) {
                    // Dot-stuffing: prepend an extra dot
                    line = "." + line;
                }
                writer.write(line);
                writer.write("\r\n");
            }
        }

        // End of data terminator: <CRLF>.<CRLF>
        writer.write(".\r\n");
        writer.flush();
        ProtocolLogger.logClientSend(ProtocolLogger.LogType.SMTP_CLIENT, ".");

        return readResponse();
    }

    /**
     * Sends RSET.
     */
    public SmtpResponse sendRset() throws IOException {
        return sendCommand("RSET");
    }

    /**
     * Sends NOOP.
     */
    public SmtpResponse sendNoop() throws IOException {
        return sendCommand("NOOP");
    }

    /**
     * Sends QUIT and closes connection.
     */
    public SmtpResponse sendQuit() throws IOException {
        try {
            return sendCommand("QUIT");
        } finally {
            close();
        }
    }

    /**
     * High-level method to send an email end-to-end.
     */
    public SmtpResponse sendEmail(
            String host,
            int port,
            String username,
            String password,
            boolean useAuth,
            EmailMessage message,
            boolean useSsl) throws IOException {

        // 1. Connect
        connect(host, port, 30000, useSsl);

        // 2. EHLO
        SmtpResponse ehloResp = sendEhlo("localhost");
        if (!ehloResp.isSuccess()) {
            throw new IOException("EHLO/HELO failed: " + ehloResp);
        }

        // 3. AUTH if required
        if (useAuth && username != null && !username.isBlank()) {
            SmtpResponse authResp = authenticateLogin(username, password);
            if (authResp.getCode() != 235) {
                throw new IOException("Authentication failed: " + authResp);
            }
        }

        // 4. MAIL FROM
        SmtpResponse fromResp = sendMailFrom(message.getFrom());
        if (!fromResp.isSuccess()) {
            throw new IOException("MAIL FROM rejected: " + fromResp);
        }

        // 5. RCPT TO for each recipient (To and Cc)
        List<String> allRecipients = message.getAllRecipients();
        if (allRecipients.isEmpty()) {
            throw new IOException("No recipients specified in email");
        }

        for (String rcpt : allRecipients) {
            SmtpResponse rcptResp = sendRcptTo(rcpt);
            if (!rcptResp.isSuccess()) {
                throw new IOException("Recipient '" + rcpt + "' rejected: " + rcptResp);
            }
        }

        // 6. Write EML and send DATA
        String rawEml = MimeWriter.writeToEml(message);
        SmtpResponse dataResp = sendData(rawEml);
        if (!dataResp.isSuccess()) {
            throw new IOException("DATA rejected by server: " + dataResp);
        }

        // 7. QUIT
        try {
            sendQuit();
        } catch (Exception ignored) {}

        return dataResp;
    }

    public SmtpResponse sendCommand(String command) throws IOException {
        if (!connected || socket == null || socket.isClosed()) {
            throw new IOException("SMTP Client is not connected");
        }

        ProtocolLogger.logClientSend(ProtocolLogger.LogType.SMTP_CLIENT, command);
        writer.write(command + "\r\n");
        writer.flush();

        return readResponse();
    }

    /**
     * Reads a full single-line or multi-line response from the SMTP server.
     */
    public SmtpResponse readResponse() throws IOException {
        List<String> lines = new ArrayList<>();
        int code = -1;
        StringBuilder messageBuilder = new StringBuilder();

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                throw new EOFException("Connection closed prematurely by SMTP server");
            }
            ProtocolLogger.logClientReceive(ProtocolLogger.LogType.SMTP_CLIENT, line);
            lines.add(line);

            if (line.length() >= 3) {
                try {
                    int lineCode = Integer.parseInt(line.substring(0, 3));
                    if (code == -1) {
                        code = lineCode;
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Check RFC 5321 multi-line continuation: <code>- means more lines, <code><space> or just <code> means end
            if (line.length() >= 4 && line.charAt(3) == '-') {
                // Multi-line continuation
                if (line.length() > 4) {
                    messageBuilder.append(line.substring(4)).append("\n");
                }
            } else {
                // Last line of response
                if (line.length() > 4) {
                    messageBuilder.append(line.substring(4));
                } else if (line.length() == 3) {
                    messageBuilder.append(line);
                }
                break;
            }
        }

        return new SmtpResponse(code, messageBuilder.toString().trim(), lines);
    }

    @Override
    public void close() {
        connected = false;
        try {
            if (writer != null) writer.close();
        } catch (Exception ignored) {}
        try {
            if (reader != null) reader.close();
        } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}
