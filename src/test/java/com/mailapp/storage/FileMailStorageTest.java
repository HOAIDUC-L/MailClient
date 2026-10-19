package com.mailapp.storage;

import com.mailapp.model.EmailMessage;
import com.mailapp.model.Pop3MessageInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileMailStorageTest {

    @Test
    public void testSaveReadAndListEmails(@TempDir Path tempDir) throws IOException {
        Path mailboxesDir = tempDir.resolve("mailboxes");
        FileMailStorage storage = new FileMailStorage(mailboxesDir);
        storage.init();

        String rawEml1 = "From: duc@localhost\r\nSubject: Mail 1\r\n\r\nContent 1";
        String rawEml2 = "From: duc@localhost\r\nSubject: Mail 2\r\n\r\nContent 2";

        String uid1 = storage.saveEmail("lan", rawEml1);
        String uid2 = storage.saveEmail("lan", rawEml2);

        assertNotNull(uid1);
        assertNotNull(uid2);
        assertNotEquals(uid1, uid2, "Message UIDs must be distinct to prevent overwriting");

        assertEquals(2, storage.getMessageCount("lan"));

        List<Pop3MessageInfo> list = storage.getMailboxMessages("lan");
        assertEquals(2, list.size());

        String read1 = storage.readEmailRaw("lan", uid1);
        assertEquals(rawEml1, read1);

        EmailMessage parsed2 = storage.readEmail("lan", uid2);
        assertEquals("Mail 2", parsed2.getSubject());
        assertEquals("Content 2", parsed2.getBody());
    }

    @Test
    public void testDeleteEmails(@TempDir Path tempDir) throws IOException {
        Path mailboxesDir = tempDir.resolve("mailboxes");
        FileMailStorage storage = new FileMailStorage(mailboxesDir);
        storage.init();

        String uid1 = storage.saveEmail("duc", "From: lan\r\n\r\nBody 1");
        String uid2 = storage.saveEmail("duc", "From: lan\r\n\r\nBody 2");
        assertEquals(2, storage.getMessageCount("duc"));

        storage.deleteEmails("duc", List.of(uid1));
        assertEquals(1, storage.getMessageCount("duc"));

        List<Pop3MessageInfo> list = storage.getMailboxMessages("duc");
        assertEquals(1, list.size());
        assertEquals(uid2, list.get(0).getUidl());
    }

    @Test
    public void testPersistenceAcrossStorageRestart(@TempDir Path tempDir) throws IOException {
        Path mailboxesDir = tempDir.resolve("mailboxes");
        FileMailStorage storage1 = new FileMailStorage(mailboxesDir);
        storage1.init();

        String uid = storage1.saveEmail("lan", "From: duc\r\nSubject: Persistent\r\n\r\nWill survive restart");
        assertEquals(1, storage1.getMessageCount("lan"));

        // Instantiate new storage pointing to same directory
        FileMailStorage storage2 = new FileMailStorage(mailboxesDir);
        storage2.init();
        assertEquals(1, storage2.getMessageCount("lan"));
        String content = storage2.readEmailRaw("lan", uid);
        assertTrue(content.contains("Persistent"));
    }

    @Test
    public void testPathTraversalRejection(@TempDir Path tempDir) throws IOException {
        Path mailboxesDir = tempDir.resolve("mailboxes");
        FileMailStorage storage = new FileMailStorage(mailboxesDir);
        storage.init();

        assertThrows(SecurityException.class, () -> storage.saveEmail("../etc", "content"));
        assertThrows(SecurityException.class, () -> storage.getMailboxMessages("../"));
        assertThrows(SecurityException.class, () -> storage.readEmailRaw("lan", "../other"));
    }

    @Test
    public void testMailboxLocking() {
        FileMailStorage storage = new FileMailStorage(Path.of("temp-mailboxes"));

        assertTrue(storage.acquireMailboxLock("lan"), "First session must acquire lock");
        assertFalse(storage.acquireMailboxLock("lan"), "Second session must be rejected");

        storage.releaseMailboxLock("lan");
        assertTrue(storage.acquireMailboxLock("lan"), "Should acquire lock after release");
        storage.releaseMailboxLock("lan");
    }
}
