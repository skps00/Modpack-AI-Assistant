package com.skps9.packai.api;

/**
 * Loader-neutral third-party AskTool registration request (Scope Y).
 * Carries the tool instance; the per-loader ClientSetup adapter posts this over the
 * shared game bus and calls {@link com.skps9.packai.logic.AskToolLoop#registerExternal}.
 */
public record AskToolRegistration(AskTool tool) {
    public AskToolRegistration {
        if (tool == null) {
            throw new IllegalArgumentException("tool must not be null");
        }
    }
}
