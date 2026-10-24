package com.skps9.packai.logic;

public final class CardSlotLabels {
    private CardSlotLabels() {}

    public static String labelFor(String itemName, SlotOptions opt, String lang) {
        String name = itemName == null ? "" : itemName;
        if (opt == null) {
            return name;
        }
        String code = lang == null || lang.isBlank() ? ReplyLang.current() : lang;
        if (opt.count() == 0) {
            return "?" + ReplyLang.tr(code, "packai.reply.unknown_slot");
        }
        if (opt.count() > 1) {
            return name + " (" + ReplyLang.tr(code, "packai.reply.any_of_n", String.valueOf(opt.count())) + ")";
        }
        return name;
    }
}
