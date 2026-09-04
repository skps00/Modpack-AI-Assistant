package com.skps9.packai.api;

/**
 * Public Ask tool contract for built-in and third-party registrations.
 * Name is the registry / JSON / native-tools id.
 */
public interface AskTool {
    String name();

    /** Empty string on miss / error. Never throw into the loop. */
    String run(AskToolArgs args);

    /**
     * Human-readable purpose for LLM-facing schema; third-party MUST provide non-empty.
     */
    String description();

    /**
     * JSON-object property schema for args; must parse as JSON object.
     */
    String argsSchemaJson();
}
