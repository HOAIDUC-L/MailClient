package com.mailapp.client.pop3;

import com.mailapp.mime.MimeParser;
import com.mailapp.model.EmailMessage;
import com.mailapp.model.Pop3MessageInfo;
import com.mailapp.util.ProtocolLogger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Low-level POP3 Client implementation using Java TCP Sockets.
 */
public class Pop3Client implements AutoCloseable {

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean connected = false;
    private boolean authenticated = false;

    public Pop3Client() {}

    /**
     * Connects to POP3 server and reads greeting +OK.
     */
    public Pop3Response connect(String host, int port, int timeoutMs, boolean useSsl) throws IOException {
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

        ProtocolLogger.log(ProtocolLogger.LogType.POP3_CLIENT, "Connected to POP3 server " + host + ":" + port);

        Pop3Response greeting = readStatusLine();
        if (!greeting.isOk()) {
            close();
            throw new IOException("Server rejected POP3 greeting: " + greeting);
        }
        return greeting;
    }

    /**
     * Performs authentication using USER and PASS commands.
     */
    public Pop3Response login(String username, String password) throws IOException {
        Pop3Response userResp = sendCommand("USER " + username);
        if (!userResp.isOk()) {
            return userResp;
        }

        // Send PASS (masked in protocol log)
        ProtocolLogger.logClientSend(ProtocolLogger.LogType.POP3_CLIENT, "PASS ********");
        writer.write("PASS " + password + "\r\n");
        writer.flush();

        Pop3Response passResp = readStatusLine();
        if (passResp.isOk()) {
            this.authenticated = true;
        }
        return passResp;
    }

    /**
     * Executes STAT command. Returns array [messageCount, totalSizeBytes].
     */
    public long[] stat() throws IOException {
        Pop3Response resp = sendCommand("STAT");
        if (!resp.isOk()) {
            throw new IOException("STAT failed: " + resp.getMessage());
        }

        // STAT response format: +OK <count> <totalBytes>
        String[] tokens = resp.getMessage().trim().split("\\s+");
        if (tokens.length >= 2) {
            long count = Long.parseLong(tokens[0]);
            long bytes = Long.parseLong(tokens[1]);
            return new long[]{count, bytes};
        }
        return new long[]{0, 0};
    }

    /**
     * Executes LIST command and returns list of Pop3MessageInfo.
     */
    public List<Pop3MessageInfo> list() throws IOException {
        Pop3Response resp = sendCommand("LIST");
        if (!resp.isOk()) {
            throw new IOException("LIST failed: " + resp.getMessage());
        }

        List<String> lines = readMultiLineData();
        List<Pop3MessageInfo> list = new ArrayList<>();

        for (String line : lines) {
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length >= 2) {
                try {
                    int num = Integer.parseInt(tokens[0]);
                    long size = Long.parseLong(tokens[1]);
                    list.add(new Pop3MessageInfo(num, String.valueOf(num), size, null));
                } catch (NumberFormatException ignored) {}
            }
        }
        return list;
    }

    /**
     * Executes UIDL command and returns map of message number to unique ID.
     */
    public Map<Integer, String> uidl() throws IOException {
        Pop3Response resp = sendCommand("UIDL");
        if (!resp.isOk()) {
            throw new IOException("UIDL failed: " + resp.getMessage());
        }

        List<String> lines = readMultiLineData();
        Map<Integer, String> map = new HashMap<>();

        for (String line : lines) {
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length >= 2) {
                try {
                    int num = Integer.parseInt(tokens[0]);
                    map.put(num, tokens[1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    /**
     * Retrieves full raw .eml message using RETR command.
     */
    public String retr(int messageNumber) throws IOException {
        Pop3Response resp = sendCommand("RETR " + messageNumber);
        if (!resp.isOk()) {
            throw new IOException("RETR " + messageNumber + " failed: " + resp.getMessage());
        }

        List<String> lines = readMultiLineData();
        return String.join("\r\n", lines);
    }

    /**
     * Retrieves and parses email into EmailMessage.
     */
    public EmailMessage retrieveMessage(int messageNumber) throws IOException {
        String raw = retr(messageNumber);
        return MimeParser.parseEml(raw);
    }

    /**
     * Marks message for deletion using DELE command.
     */
    public Pop3Response dele(int messageNumber) throws IOException {
        return sendCommand("DELE " + messageNumber);
    }

    /**
     * Resets deletion marks using RSET command.
     */
    public Pop3Response rset() throws IOException {
        return sendCommand("RSET");
    }

    /**
     * Sends NOOP command to keep connection alive.
     */
    public Pop3Response noop() throws IOException {
        return sendCommand("NOOP");
    }

    /**
     * Commits deletions and signs off using QUIT command.
     */
    public Pop3Response quit() throws IOException {
        try {
            return sendCommand("QUIT");
        } finally {
            close();
        }
    }

    public Pop3Response sendCommand(String command) throws IOException {
        if (!connected || socket == null || socket.isClosed()) {
            throw new IOException("POP3 client is not connected");
        }

        ProtocolLogger.logClientSend(ProtocolLogger.LogType.POP3_CLIENT, command);
        writer.write(command + "\r\n");
        writer.flush();

        return readStatusLine();
    }

    /**
     * Reads a single status line (+OK or -ERR) from the server.
     */
    public Pop3Response readStatusLine() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new EOFException("Connection closed by POP3 server");
        }
        ProtocolLogger.logClientReceive(ProtocolLogger.LogType.POP3_CLIENT, line);

        String trimmed = line.trim();
        if (trimmed.startsWith("+OK")) {
            String msg = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
            return new Pop3Response(true, msg);
        } else if (trimmed.startsWith("-ERR")) {
            String msg = trimmed.length() > 4 ? trimmed.substring(4).trim() : "";
            return new Pop3Response(false, msg);
        } else {
            return new Pop3Response(false, line);
        }
    }

    /**
     * Reads multi-line data until '.' line, performing RFC 1939 dot-unstuffing.
     */
    public List<String> readMultiLineData() throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            // Check terminator
            if (line.equals(".")) {
                ProtocolLogger.logClientReceive(ProtocolLogger.LogType.POP3_CLIENT, ".");
                break;
            }

            // Dot-unstuffing: RFC 1939 section 3
            if (line.startsWith("..")) {
                line = line.substring(1);
            }

            lines.add(line);
        }
        return lines;
    }

    @Override
    public void close() {
        connected = false;
        authenticated = false;
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

    public boolean isAuthenticated() {
        return authenticated;
    }
}
