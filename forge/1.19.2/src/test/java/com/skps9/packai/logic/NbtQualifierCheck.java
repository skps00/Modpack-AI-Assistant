package com.skps9.packai.logic;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NbtQualifierCheck {
    private NbtQualifierCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        damageOnlyTag();
        typeLineFromTooltip();
        emptyStack();
        negativeControlWithoutConsumableFilter();
        NbtQualifier.resetTooltipLinesSourceForTest();
        System.out.println("NbtQualifierCheck OK");
    }

    private static void damageOnlyTag() {
        ItemStack stack = new ItemStack(Items.FISHING_ROD);
        stack.setDamageValue(3);
        NbtQualifier.resetTooltipLinesSourceForTest();
        assert "".equals(NbtQualifier.qualifier(stack)) : NbtQualifier.qualifier(stack);
        System.out.println("damageOnlyTag OK");
    }

    private static void typeLineFromTooltip() {
        ItemStack stack = new ItemStack(Items.HONEYCOMB);
        CompoundTag tag = new CompoundTag();
        tag.putByte("productivebees_gene_stack", (byte) 1);
        stack.setTag(tag);
        NbtQualifier.setTooltipLinesSourceForTest(
                s -> List.of("Honey Treat", "Type: minecraft:bee (100%)"));
        try {
            assert "Bee".equals(NbtQualifier.qualifier(stack)) : NbtQualifier.qualifier(stack);
        } finally {
            NbtQualifier.resetTooltipLinesSourceForTest();
        }
        System.out.println("typeLineFromTooltip OK");
    }

    private static void emptyStack() {
        assert "".equals(NbtQualifier.qualifier(ItemStack.EMPTY));
        ItemStack plain = new ItemStack(Items.STICK);
        NbtQualifier.setTooltipLinesSourceForTest(s -> List.of("Stick"));
        try {
            assert "".equals(NbtQualifier.qualifier(plain));
        } finally {
            NbtQualifier.resetTooltipLinesSourceForTest();
        }
        System.out.println("emptyStack OK");
    }

    /** Negative control: without consumable-only filter, Damage-only tag would still scan tooltip. */
    private static void negativeControlWithoutConsumableFilter() {
        ItemStack stack = new ItemStack(Items.FISHING_ROD);
        stack.setDamageValue(1);
        NbtQualifier.setTooltipLinesSourceForTest(
                s -> List.of("Fishing Rod", "Type: minecraft:bee (100%)"));
        try {
            String q = NbtQualifier.qualifier(stack);
            assert "".equals(q) : "expected consumable-only tag to skip tooltip scan, got " + q;
        } finally {
            NbtQualifier.resetTooltipLinesSourceForTest();
        }
        System.out.println("negativeControlWithoutConsumableFilter OK");
    }
}
