package com.mailapp.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents a user account on the local mail server.
 */
public class Account {
    private String username;
    private String passwordHash;
    private String salt;
    private String createdAt;

    public Account() {}

    @JsonCreator
    public Account(
            @JsonProperty("username") String username,
            @JsonProperty("passwordHash") String passwordHash,
            @JsonProperty("salt") String salt,
            @JsonProperty("createdAt") String createdAt) {
        this.username = username != null ? username.toLowerCase() : null;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.createdAt = createdAt != null ? createdAt : Instant.now().toString();
    }

    public Account(String username, String rawPassword) {
        this.username = username != null ? username.toLowerCase() : null;
        this.salt = PasswordHasher.generateSalt();
        this.passwordHash = PasswordHasher.hashPassword(rawPassword, this.salt);
        this.createdAt = Instant.now().toString();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username.toLowerCase() : null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Account{" +
                "username='" + username + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
