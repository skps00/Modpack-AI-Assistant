package com.skps9.packai.logic;

import java.util.List;

/**
 * Psi mod hint for spell-generation questions (no in-game CAD write — LLM describes spell).
 */
public final class PsiHelper {
    private PsiHelper() {}

    public static boolean isPsiQuestion(String question) {
        if (question == null) {
            return false;
        }
        String q = question.toLowerCase();
        return q.contains("psi")
                || q.contains("術式")
                || q.contains("cad")
                || q.contains("spell")
                || q.contains("trick");
    }

    /** Extra system hint when this pack has Psi and the player asks about spells. */
    public static String promptAddon(String question, List<String> modIds) {
        return promptAddon(question, modIds, ReplyLang.current());
    }

    /** Extra system hint when this pack has Psi and the player asks about spells. */
    public static String promptAddon(String question, List<String> modIds, String replyLang) {
        if (!ModScanners.hasMod(modIds, "psi") || !isPsiQuestion(question)) {
            return "";
        }
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        return ReplyLang.psiPromptAddon(lang);
    }
}
