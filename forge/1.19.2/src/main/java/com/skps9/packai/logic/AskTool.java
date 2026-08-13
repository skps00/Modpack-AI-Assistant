package com.skps9.packai.logic;

/** Allowlisted local Ask lookup. Name is the registry / JSON / native-tools id. */
public interface AskTool {
    String name();

    /** Empty string on miss / error. Never throw into the loop. */
    String run(AskToolArgs args);
}
