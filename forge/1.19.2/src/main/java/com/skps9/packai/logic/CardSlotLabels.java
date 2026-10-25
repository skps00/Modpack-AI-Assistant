package com.skps9.packai.logic;

import net.minecraft.world.item.ItemStack;

public final class CardSlotLabels {
    private CardSlotLabels() {}

    public static String labelFor(String itemName, SlotOptions opt, String lang) {
        return labelForStack(itemName, opt, null, lang);
    }

    public static String labelForStack(String itemName, SlotOptions opt, ItemStack stack, String lang) {
        String name = itemName == null ? "" : itemName;
        String code = lang == null || lang.isBlank() ? ReplyLang.current() : lang;
        if (opt != null) {
            if (opt.count() == 0) {
                name = "?" + ReplyLang.tr(code, "packai.reply.unknown_slot");
            } else if (opt.count() > 1) {
                name = name + " (" + ReplyLang.tr(code, "packai.reply.any_of_n", String.valueOf(opt.count())) + ")";
            }
        }
        if (stack == null || stack.isEmpty()) {
            return name;
        }
        String qualifier = NbtQualifier.qualifier(stack);
        if (qualifier.isEmpty()) {
            return name;
        }
        return ReplyLang.tr(code, "packai.reply.nbt_qualifier", name, qualifier);
    }
}
