package com.mailapp.storage;

import com.mailapp.model.EmailMessage;
import com.mailapp.model.Pop3MessageInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for mail storage operations.
 */
public interface MailStorage {

    /**
     * Initializes storage directories.
     */
    void init() throws IOException;

    /**
     * Saves raw .eml email content into a recipient's mailbox.
     * @param recipientUsername clean recipient username
     * @param rawEmlContent complete RFC 5322 .eml string
     * @return unique message identifier (filename without extension)
     */
    String saveEmail(String recipientUsername, String rawEmlContent) throws IOException;

    /**
     * Retrieves all messages in a mailbox as Pop3MessageInfo.
     */
    List<Pop3MessageInfo> getMailboxMessages(String username) throws IOException;

    /**
     * Reads raw .eml string of a specific message.
     */
    String readEmailRaw(String username, String messageUid) throws IOException;

    /**
     * Reads and parses a specific email into an EmailMessage object.
     */
    EmailMessage readEmail(String username, String messageUid) throws IOException;

    /**
     * Durably deletes emails with given UIDs from a mailbox.
     */
    void deleteEmails(String username, List<String> messageUids) throws IOException;

    /**
     * Attempts to acquire an exclusive lock on a user's mailbox (POP3 session locking).
     * @return true if acquired, false if already locked by another session.
     */
    boolean acquireMailboxLock(String username);

    /**
     * Releases the lock on a user's mailbox.
     */
    void releaseMailboxLock(String username);

    /**
     * Returns the mailbox directory path for a user.
     */
    Path getMailboxPath(String username);

    /**
     * Lists all existing mailbox usernames.
     */
    List<String> listAllMailboxes();

    /**
     * Returns the total count of messages in a mailbox.
     */
    int getMessageCount(String username);
}
