package com.skps9.packai.logic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Ensure every player-facing answer ends with a sources line. */
public final class ReplySources {
    /** zh_tw / zh_cn / en — must stay in sync with {@link RecipeEmbed} sources split. */
    public static final Pattern HEADER = Pattern.compile("(?m)(【來源】|【来源】|\\[Sources\\])");
    private static final Pattern MARKER = HEADER;
    /** Softer JEI label when focus has NBT/schematic variants that JEI may conflate. */
    public static final String JEI_VARIANT_SOFT = "JEI (NBT variants may mix)";

    private ReplySources() {}

    public static List<String> build(
            boolean jei,
            boolean questBook,
            boolean localScripts,
            boolean acquireTables,
            boolean webSearch
    ) {
        return build(jei, false, false, false, questBook, localScripts, acquireTables, webSearch, false, ReplyLang.current());
    }

    public static List<String> build(
            boolean jei,
            boolean questBook,
            boolean localScripts,
            boolean acquireTables,
            boolean webSearch,
            String replyLang
    ) {
        return build(jei, false, false, false, questBook, localScripts, acquireTables, webSearch, false, replyLang);
    }

    public static List<String> build(
            boolean jei,
            boolean questBook,
            boolean localScripts,
            boolean acquireTables,
            boolean webSearch,
            boolean jarMods,
            String replyLang
    ) {
        return build(jei, false, false, false, questBook, localScripts, acquireTables, webSearch, jarMods, replyLang);
    }

    public static List<String> build(
            boolean jei,
            boolean emi,
            boolean purpose,
            boolean guide,
            boolean questBook,
            boolean localScripts,
            boolean acquireTables,
            boolean webSearch,
            boolean jarMods,
            String replyLang
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (jei) {
            out.add("JEI");
        }
        if (emi) {
            out.add("EMI");
        }
        if (purpose) {
            out.add(ReplyLang.labelPurpose(replyLang));
        }
        if (guide) {
            out.add(ReplyLang.labelGuide(replyLang));
        }
        if (questBook) {
            out.add(ReplyLang.labelQuestBook(replyLang));
        }
        if (localScripts) {
            out.add(ReplyLang.labelLocalRecipes(replyLang));
        }
        if (acquireTables) {
            out.add(ReplyLang.labelAcquire(replyLang));
        }
        if (jarMods) {
            out.add(ReplyLang.labelJarIndex(replyLang));
        }
        if (webSearch) {
            out.add(ReplyLang.labelWeb(replyLang));
        }
        if (out.isEmpty()) {
            out.add(ReplyLang.labelAiOnly(replyLang));
        }
        return List.copyOf(out);
    }

    /**
     * Keep JEI listed but mark NBT-variant ambiguity (Tetra scrolls etc.).
     * No-op when JEI absent.
     */
    public static List<String> softenJeiForVariant(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return labels == null ? List.of() : labels;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        boolean changed = false;
        for (String s : labels) {
            if ("JEI".equals(s)) {
                out.add(JEI_VARIANT_SOFT);
                changed = true;
            } else {
                out.add(s);
            }
        }
        return changed ? List.copyOf(out) : labels;
    }

    /** Append sources footer when the answer does not already include one. */
    public static String ensure(String answer, List<String> labels) {
        return ensure(answer, labels, ReplyLang.current());
    }

    public static String ensure(String answer, List<String> labels, String replyLang) {
        if (answer == null || answer.isBlank()) {
            return format(labels, replyLang);
        }
        if (MARKER.matcher(answer).find()) {
            return answer;
        }
        return answer.trim() + "\n\n" + format(labels, replyLang);
    }

    private static String format(List<String> labels, String replyLang) {
        List<String> use = labels == null || labels.isEmpty()
                ? List.of(ReplyLang.labelAiModel(replyLang))
                : labels;
        return ReplyLang.sourceHeader(replyLang) + String.join(ReplyLang.sourceJoin(replyLang), use);
    }
}
