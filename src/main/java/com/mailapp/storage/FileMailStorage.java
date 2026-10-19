package com.mailapp.storage;

import com.mailapp.mime.MimeParser;
import com.mailapp.model.EmailMessage;
import com.mailapp.model.Pop3MessageInfo;
import com.mailapp.util.EmailValidator;
import com.mailapp.util.ProtocolLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * File-based implementation of MailStorage storing emails as .eml files.
 */
public class FileMailStorage implements MailStorage {

    private static final java.util.concurrent.atomic.AtomicLong SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong(1);

    private final Path mailboxesRoot;
    private final ConcurrentHashMap<String, Boolean> mailboxLocks = new ConcurrentHashMap<>();

    public FileMailStorage(Path mailboxesRoot) {
        this.mailboxesRoot = mailboxesRoot.toAbsolutePath().normalize();
    }

    @Override
    public void init() throws IOException {
        Files.createDirectories(mailboxesRoot);
    }

    private Path resolveSafeMailboxPath(String username) {
        String cleanUser = EmailValidator.extractUsername(username);
        if (!EmailValidator.isValidUsername(cleanUser)) {
            throw new SecurityException("Invalid username or path traversal attempt: " + username);
        }

        Path userDir = mailboxesRoot.resolve(cleanUser).normalize();
        if (!userDir.startsWith(mailboxesRoot)) {
            throw new SecurityException("Mailbox path traversal attempt detected: " + username);
        }
        return userDir;
    }

    @Override
    public String saveEmail(String recipientUsername, String rawEmlContent) throws IOException {
        Path userDir = resolveSafeMailboxPath(recipientUsername);
        Files.createDirectories(userDir);

        // Generate unique message ID with monotonic counter for stable sorting
        long seq = SEQUENCE.incrementAndGet();
        String messageUid = System.currentTimeMillis() + "_" + String.format("%010d", seq) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Path targetFile = userDir.resolve(messageUid + ".eml");

        // Write content durably
        Files.writeString(targetFile, rawEmlContent, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);

        ProtocolLogger.log(ProtocolLogger.LogType.SYSTEM,
                "Saved email for '" + recipientUsername + "' to " + targetFile.getFileName());

        return messageUid;
    }

    @Override
    public List<Pop3MessageInfo> getMailboxMessages(String username) throws IOException {
        Path userDir = resolveSafeMailboxPath(username);
        if (!Files.exists(userDir)) {
            return Collections.emptyList();
        }

        List<Pop3MessageInfo> messages = new ArrayList<>();
        try (Stream<Path> stream = Files.list(userDir)) {
            List<Path> emlFiles = stream
                    .filter(p -> p.toString().endsWith(".eml"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();

            int msgNum = 1;
            for (Path path : emlFiles) {
                String fileName = path.getFileName().toString();
                String uid = fileName.substring(0, fileName.lastIndexOf('.'));
                long size = Files.size(path);
                Pop3MessageInfo info = new Pop3MessageInfo(msgNum++, uid, size, path);

                // Quick parse header snippet for dashboard/listing
                try {
                    String raw = Files.readString(path, StandardCharsets.UTF_8);
                    EmailMessage msg = MimeParser.parseEml(raw);
                    info.setSubject(msg.getSubject());
                    info.setFrom(msg.getFrom());
                    info.setDate(msg.getDate());
                } catch (Exception ignored) {}

                messages.add(info);
            }
        }

        return messages;
    }

    @Override
    public String readEmailRaw(String username, String messageUid) throws IOException {
        Path userDir = resolveSafeMailboxPath(username);
        if (messageUid.contains("..") || messageUid.contains("/") || messageUid.contains("\\")) {
            throw new SecurityException("Invalid message UID: " + messageUid);
        }

        Path targetFile = userDir.resolve(messageUid + ".eml").normalize();
        if (!targetFile.startsWith(userDir) || !Files.exists(targetFile)) {
            throw new NoSuchFileException("Email not found: " + messageUid);
        }

        return Files.readString(targetFile, StandardCharsets.UTF_8);
    }

    @Override
    public EmailMessage readEmail(String username, String messageUid) throws IOException {
        String raw = readEmailRaw(username, messageUid);
        return MimeParser.parseEml(raw);
    }

    @Override
    public void deleteEmails(String username, List<String> messageUids) throws IOException {
        Path userDir = resolveSafeMailboxPath(username);
        if (!Files.exists(userDir) || messageUids == null) {
            return;
        }

        for (String uid : messageUids) {
            if (uid.contains("..") || uid.contains("/") || uid.contains("\\")) {
                continue;
            }
            Path target = userDir.resolve(uid + ".eml").normalize();
            if (target.startsWith(userDir) && Files.exists(target)) {
                Files.delete(target);
                ProtocolLogger.log(ProtocolLogger.LogType.SYSTEM,
                        "Deleted email '" + uid + "' from mailbox '" + username + "'");
            }
        }
    }

    @Override
    public boolean acquireMailboxLock(String username) {
        String cleanUser = EmailValidator.extractUsername(username);
        return mailboxLocks.putIfAbsent(cleanUser, Boolean.TRUE) == null;
    }

    @Override
    public void releaseMailboxLock(String username) {
        String cleanUser = EmailValidator.extractUsername(username);
        mailboxLocks.remove(cleanUser);
    }

    @Override
    public Path getMailboxPath(String username) {
        return resolveSafeMailboxPath(username);
    }

    @Override
    public List<String> listAllMailboxes() {
        if (!Files.exists(mailboxesRoot)) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        File[] dirs = mailboxesRoot.toFile().listFiles(File::isDirectory);
        if (dirs != null) {
            for (File d : dirs) {
                if (EmailValidator.isValidUsername(d.getName())) {
                    result.add(d.getName());
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    @Override
    public int getMessageCount(String username) {
        try {
            Path userDir = resolveSafeMailboxPath(username);
            if (!Files.exists(userDir)) return 0;
            try (Stream<Path> stream = Files.list(userDir)) {
                return (int) stream.filter(p -> p.toString().endsWith(".eml")).count();
            }
        } catch (Exception e) {
            return 0;
        }
    }
}
