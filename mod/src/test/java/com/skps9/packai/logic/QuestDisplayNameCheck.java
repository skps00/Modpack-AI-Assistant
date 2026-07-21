package com.skps9.packai.logic;

import java.util.List;

/** Runnable check: quest UI/LLM text uses names, never hex ids. */
public final class QuestDisplayNameCheck {
    private QuestDisplayNameCheck() {}

    public static void main(String[] args) {
        assert QuestGuide.looksLikeQuestId("0F16498769DFB3B0");
        assert !QuestGuide.looksLikeQuestId("Andesite Alloys");

        QuestGuide.Hit byId = new QuestGuide.Hit(
                "create", "0F16498769DFB3B0", "", "x", List.of("create:andesite_alloy"),
                0, false, "0F16498769DFB3B0", "ftbquests");
        String t = QuestGuide.displayTitle(byId, "zh_tw");
        assert !QuestGuide.looksLikeQuestId(t) : t;
        assert t.contains("andesite") || t.contains("合金") || t.contains("相關") : t;

        String te = QuestGuide.displayTitle(byId, "en_us");
        assert te.contains("related quest") || te.toLowerCase().contains("andesite") : te;

        QuestGuide.Hit keyed = new QuestGuide.Hit(
                "alchem", "{atm9.quest.alchem.dissolving}", "", "x", List.of(),
                0, false, "71815B287D0F162A", "ftbquests");
        String kt = QuestGuide.displayTitle(keyed, "zh_tw");
        assert !kt.contains("{") : kt;
        assert !QuestGuide.looksLikeQuestId(kt) : kt;
        assert kt.toLowerCase().contains("dissolv") || kt.contains("任務") : kt;

        String guide = QuestGuide.formatGuide(List.of(byId), false, null, 1, false);
        assert !guide.contains("0F16498769DFB3B0") : guide;

        System.out.println("QuestDisplayNameCheck OK");
    }
}
