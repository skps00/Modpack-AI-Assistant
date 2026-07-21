package com.skps9.packai.logic;

import net.neoforged.fml.ModList;

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

    public static boolean modLoaded() {
        return ModList.get().isLoaded("psi");
    }

    /** Extra system hint when player asks about Psi spells. */
    public static String promptAddon(String question) {
        if (!modLoaded() || !isPsiQuestion(question)) {
            return "";
        }
        return "【Psi】玩家想設計 Psi 術式：用繁中說明 trick 組合思路（向量、實體、運動、偵測等），"
                + "列出建議的 trick 名稱與順序；提醒在 CAD 中組裝與測試 PSI 消耗。"
                + "勿捏造不存在的 trick 名稱；不確定時建議查 JEI 的 Psi 分類。";
    }
}
