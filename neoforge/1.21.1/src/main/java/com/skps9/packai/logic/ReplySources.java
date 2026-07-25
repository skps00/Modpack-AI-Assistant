package com.skps9.packai.logic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Ensure every player-facing answer ends with a sources line. */
public final class ReplySources {
    private static final Pattern MARKER = Pattern.compile("(?m)(【來源】|\\[Sources\\])");

    private ReplySources() {}

    public static List<String> build(
            boolean jei,
            boolean questBook,
            boolean localScripts,
            boolean acquireTables,
            boolean webSearch
    ) {
        return build(jei, questBook, localScripts, acquireTables, webSearch, ReplyLang.current());
    }

    public static List<String> build(
            boolean jei,
            boolean questBook,
            boolean localScripts,
            boolean acquireTables,
            boolean webSearch,
            String replyLang
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (jei) {
            out.add("JEI");
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
        if (webSearch) {
            out.add(ReplyLang.labelWeb(replyLang));
        }
        if (out.isEmpty()) {
            out.add(ReplyLang.labelAiOnly(replyLang));
        }
        return List.copyOf(out);
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
