package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits item <em>purpose</em> (what it does / how you use it) from JEI-U
 * <em>as ingredient</em> (recipes that consume it). Guide-book facts use {@link #GUIDE_HEADER}.
 */
public final class AskPurposeContext {
    public static final String PURPOSE_HEADER = "[PURPOSE]";
    public static final String GUIDE_HEADER = "[GUIDE]";
    public static final String AS_INGREDIENT_HEADER = "[AS_INGREDIENT]";

    private AskPurposeContext() {}

    /** Graph edges that describe function / interaction — not craft-input lists. */
    public static boolean isPurposeGraphFact(String gf) {
        if (gf == null || gf.isBlank()) {
            return false;
        }
        return gf.contains("-[desc]->")
                || gf.contains("-[score]->")
                || gf.contains("-[triggers]->")
                || gf.contains("-[on:")
                || gf.contains("-[right_click]->")
                || gf.contains("-[right_click_use]->")
                || gf.contains("-[right_click_as_block]->");
    }

    /**
     * Build LLM purpose block. Empty string when nothing useful.
     *
     * @param tooltip      full focus-item tooltip (may be multi-line)
     * @param purposeLines already-humanized interact / desc lines
     */
    public static String buildPurposeBlock(String tooltip, List<String> purposeLines) {
        return buildPurposeBlock(tooltip, purposeLines, null);
    }

    /**
     * @param guideFacts bare Patchouli／guide text (no header), or already starts with {@link #GUIDE_HEADER}
     */
    public static String buildPurposeBlock(String tooltip, List<String> purposeLines, String guideFacts) {
        List<String> body = new ArrayList<>();
        if (tooltip != null && !tooltip.isBlank()) {
            body.add(tooltip.trim());
        }
        if (purposeLines != null) {
            for (String line : purposeLines) {
                if (line != null && !line.isBlank()) {
                    body.add(line.trim());
                }
            }
        }
        StringBuilder out = new StringBuilder();
        if (!body.isEmpty()) {
            out.append(PURPOSE_HEADER).append('\n').append(String.join("\n", body));
        }
        String guide = normalizeGuide(guideFacts);
        if (!guide.isEmpty()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(guide);
        }
        return out.toString();
    }

    private static String normalizeGuide(String guideFacts) {
        if (guideFacts == null || guideFacts.isBlank()) {
            return "";
        }
        String g = guideFacts.trim();
        if (g.startsWith(GUIDE_HEADER)) {
            return g;
        }
        return GUIDE_HEADER + "\n" + g;
    }
}
