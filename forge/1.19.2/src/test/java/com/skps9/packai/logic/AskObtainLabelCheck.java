package com.skps9.packai.logic;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** NBT type qualifier on obtain / card labels — byte-identical when absent. */
public final class AskObtainLabelCheck {
    private AskObtainLabelCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        withQualifier();
        withoutQualifier();
        NbtQualifier.resetTooltipLinesSourceForTest();
        System.out.println("AskObtainLabelCheck OK");
    }

    private static void withQualifier() {
        String lang = "en_us";
        ItemStack stack = new ItemStack(Items.HONEYCOMB);
        CompoundTag tag = new CompoundTag();
        tag.putByte("productivebees_gene_stack", (byte) 1);
        stack.setTag(tag);
        NbtQualifier.setTooltipLinesSourceForTest(
                s -> List.of("Honey Treat", "Type: minecraft:bee (100%)"));
        try {
            OfficialDisplay.lookup = id -> "Honey Treat";
            String label = ObtainPathText.itemLabelForStack(stack, lang);
            assert label.contains("(Bee)") : label;
            String card = CardSlotLabels.labelForStack("Honey Treat", null, stack, lang);
            assert card.contains("(Bee)") : card;
        } finally {
            OfficialDisplay.resetLookup();
            NbtQualifier.resetTooltipLinesSourceForTest();
        }
        System.out.println("withQualifier OK");
    }

    private static void withoutQualifier() {
        String lang = "en_us";
        OfficialDisplay.lookup = id -> "Iron Ingot";
        try {
            String base = ObtainPathText.itemLabelForItem(Items.IRON_INGOT, lang);
            ItemStack stack = new ItemStack(Items.IRON_INGOT);
            NbtQualifier.setTooltipLinesSourceForTest(s -> List.of("Iron Ingot"));
            String withStack = ObtainPathText.itemLabelForStack(stack, lang);
            assert base.equals(withStack) : base + " vs " + withStack;
            String cardBase = CardSlotLabels.labelFor("Iron Ingot", null, lang);
            String cardStack = CardSlotLabels.labelForStack("Iron Ingot", null, stack, lang);
            assert cardBase.equals(cardStack) : cardBase + " vs " + cardStack;
        } finally {
            OfficialDisplay.resetLookup();
            NbtQualifier.resetTooltipLinesSourceForTest();
        }
        System.out.println("withoutQualifier OK");
    }
}
