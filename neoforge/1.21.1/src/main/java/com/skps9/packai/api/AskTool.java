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
     * Full OpenAI-style parameters object (type/properties/required/additionalProperties).
     */
    String argsSchemaJson();

    /** LLM-facing purpose line (schema description). Default = description(). */
    default String llmDescription() {
        return description();
    }

    /** Teaching line when the tool returns empty for a given item. */
    default String toolMissNote(String item) {
        return "[TOOL_MISS] " + name() + " empty — do not invent";
    }
}
