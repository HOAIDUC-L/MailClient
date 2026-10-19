package com.mailapp.account;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mailapp.util.EmailValidator;
import com.mailapp.util.ProtocolLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages user accounts, authentication, and persistence in accounts.json.
 */
public class AccountManager {

    private final Path accountsFile;
    private final ObjectMapper objectMapper;
    private final Map<String, Account> accountMap = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public AccountManager(Path accountsFile) {
        this.accountsFile = accountsFile;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        init();
    }

    /**
     * Initializes accounts directory and file. If accounts.json does not exist,
     * creates default demo accounts ('duc' and 'lan') without overwriting existing data.
     */
    public void init() {
        lock.writeLock().lock();
        try {
            if (accountsFile.getParent() != null) {
                Files.createDirectories(accountsFile.getParent());
            }

            File file = accountsFile.toFile();
            if (file.exists() && file.length() > 0) {
                // Load existing accounts
                try {
                    List<Account> accounts = objectMapper.readValue(file, new TypeReference<List<Account>>() {});
                    accountMap.clear();
                    for (Account acc : accounts) {
                        if (acc.getUsername() != null) {
                            accountMap.put(acc.getUsername().toLowerCase(), acc);
                        }
                    }
                    ProtocolLogger.log(ProtocolLogger.LogType.SYSTEM,
                            "Loaded " + accountMap.size() + " accounts from " + accountsFile);
                    return;
                } catch (IOException e) {
                    ProtocolLogger.log(ProtocolLogger.LogType.SYSTEM,
                            "Error reading accounts.json, initializing demo accounts: " + e.getMessage());
                }
            }

            // Create default demo accounts
            accountMap.clear();
            Account duc = new Account("duc", "duc123");
            Account lan = new Account("lan", "lan123");
            accountMap.put("duc", duc);
            accountMap.put("lan", lan);

            saveToFile();
            ProtocolLogger.log(ProtocolLogger.LogType.SYSTEM,
                    "Initialized default demo accounts ('duc', 'lan') in " + accountsFile);

        } catch (IOException e) {
            ProtocolLogger.log(ProtocolLogger.LogType.SYSTEM, "Failed to initialize AccountManager: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Authenticates a user against hashed credentials.
     */
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        String cleanUser = EmailValidator.extractUsername(username);

        lock.readLock().lock();
        try {
            Account account = accountMap.get(cleanUser);
            if (account == null) {
                return false;
            }
            return PasswordHasher.verifyPassword(password, account.getSalt(), account.getPasswordHash());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if an account exists.
     */
    public boolean accountExists(String username) {
        if (username == null) return false;
        String cleanUser = EmailValidator.extractUsername(username);

        lock.readLock().lock();
        try {
            return accountMap.containsKey(cleanUser);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Creates a new account if username is valid and not taken.
     */
    public boolean createAccount(String username, String password) {
        if (username == null || password == null || password.isBlank()) {
            return false;
        }
        String cleanUser = EmailValidator.extractUsername(username);
        if (!EmailValidator.isValidUsername(cleanUser)) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (accountMap.containsKey(cleanUser)) {
                return false;
            }
            Account newAccount = new Account(cleanUser, password);
            accountMap.put(cleanUser, newAccount);
            saveToFile();
            return true;
        } catch (IOException e) {
            ProtocolLogger.log(ProtocolLogger.LogType.SYSTEM, "Failed to persist new account: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Account getAccount(String username) {
        if (username == null) return null;
        String cleanUser = EmailValidator.extractUsername(username);
        lock.readLock().lock();
        try {
            return accountMap.get(cleanUser);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Account> getAllAccounts() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(accountMap.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    private void saveToFile() throws IOException {
        objectMapper.writeValue(accountsFile.toFile(), new ArrayList<>(accountMap.values()));
    }
}
