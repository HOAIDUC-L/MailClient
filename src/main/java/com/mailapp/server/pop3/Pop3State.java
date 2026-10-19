package com.mailapp.server.pop3;

/**
 * Lifecycle states of a POP3 session as specified in RFC 1939.
 */
public enum Pop3State {
    AUTHORIZATION,
    TRANSACTION,
    UPDATE
}
