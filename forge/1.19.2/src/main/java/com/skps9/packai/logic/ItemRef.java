package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.world.item.ItemStack;

/**
 * In-game item for ask/LLM: registry id for matching;
 * displayName is short hover name (chat strip labels);
 * sample keeps NBT/components so UI/tooltip/Ask can rebuild the real stack.
 */
public record ItemRef(String id, String displayName, ItemStack sample) {
    public static final ItemRef NONE = new ItemRef(null, null, ItemStack.EMPTY);

    public ItemRef {
        if (sample == null || sample.isEmpty()) {
            sample = ItemStack.EMPTY;
        } else {
            sample = sample.copy();
        }
    }

    /** Id + label only (JEI suggestion / text resolve — no NBT). */
    public ItemRef(String id, String displayName) {
        this(id, displayName, ItemStack.EMPTY);
    }

    public boolean isPresent() {
        return id != null && !id.isBlank();
    }

    /** True when InvPick / pin kept a full stack copy. */
    public boolean hasSample() {
        return sample != null && !sample.isEmpty();
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
