package com.mailapp.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AccountManagerTest {

    @Test
    public void testDemoAccountInitializationAndAuthentication(@TempDir Path tempDir) {
        Path accountsFile = tempDir.resolve("accounts").resolve("accounts.json");
        AccountManager manager = new AccountManager(accountsFile);

        assertTrue(manager.accountExists("duc"));
        assertTrue(manager.accountExists("lan"));

        assertTrue(manager.authenticate("duc", "duc123"));
        assertTrue(manager.authenticate("lan", "lan123"));
        assertFalse(manager.authenticate("duc", "wrongPassword"));
        assertFalse(manager.authenticate("unknown", "any"));
    }

    @Test
    public void testCreateAccountAndPersistenceWithoutOverwriting(@TempDir Path tempDir) {
        Path accountsFile = tempDir.resolve("accounts").resolve("accounts.json");
        AccountManager manager1 = new AccountManager(accountsFile);

        assertTrue(manager1.createAccount("customUser", "pass456"));
        assertTrue(manager1.accountExists("customUser"));
        assertTrue(manager1.authenticate("customUser", "pass456"));

        // Re-initialize a second AccountManager with the same file
        AccountManager manager2 = new AccountManager(accountsFile);
        assertTrue(manager2.accountExists("customUser"), "Custom user must persist across restarts");
        assertTrue(manager2.authenticate("customUser", "pass456"));
        assertTrue(manager2.accountExists("duc"));
    }

    @Test
    public void testRejectInvalidUsernames(@TempDir Path tempDir) {
        Path accountsFile = tempDir.resolve("accounts").resolve("accounts.json");
        AccountManager manager = new AccountManager(accountsFile);

        assertFalse(manager.createAccount("../hacker", "pass"));
        assertFalse(manager.createAccount("bad/user", "pass"));
        assertFalse(manager.createAccount("", "pass"));
        assertFalse(manager.createAccount("duc", "pass")); // Duplicate username
    }
}
