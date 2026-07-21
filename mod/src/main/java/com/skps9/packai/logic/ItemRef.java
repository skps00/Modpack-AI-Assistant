package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game item for ask/LLM: registry id for matching;
 * displayName is expanded tooltip text (what the player would see, including Shift details).
 */
public record ItemRef(String id, String displayName) {
    public static final ItemRef NONE = new ItemRef(null, null);

    public boolean isPresent() {
        return id != null && !id.isBlank();
    }

    /** On-screen hover name (fallback: readable id). */
    public String label() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return Plainify.displayName(id);
    }

    /** Search tokens from the on-screen name. */
    public List<String> hintTokens() {
        List<String> out = new ArrayList<>();
        String label = label();
        if (label == null || label.isBlank()) {
            return out;
        }
        for (String p : label.toLowerCase(Locale.ROOT).split("[\\s|/,_\\-()\\[\\]]+")) {
            if (p.length() >= 2) {
                out.add(p);
            }
        }
        return out;
    }
}
