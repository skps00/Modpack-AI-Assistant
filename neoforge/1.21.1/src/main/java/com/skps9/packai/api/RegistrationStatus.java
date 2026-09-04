package com.skps9.packai.api;

/**
 * Third-party AskTool registration outcome (Scope Y).
 * Stored tools are NOT schema/exec-visible until Scope X.
 */
public enum RegistrationStatus {
    OK_STORED_NOT_ALLOWLISTED,
    REJECT_DUP,
    REJECT_RESERVED,
    REJECT_BAD_SCHEMA
}
