package com.skps9.packai.logic;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CardSlotPickCheck {
    private CardSlotPickCheck() {}

    public static void main(String[] args) {
        assert CardSlotPick.pick(null, null, 0, 0) == null;

        List<SlotOptions> twoSlots = List.of(
                new SlotOptions("", List.of(), 1),
                new SlotOptions("", List.of(), 1));
        assert CardSlotPick.pick(null, twoSlots, -1, 0) == null;
        assert CardSlotPick.pick(null, twoSlots, 5, 0) == null;

        List<SlotOptions> singleShown = List.of(new SlotOptions("", List.of(), 1));
        assert CardSlotPick.pick(null, singleShown, 0, 0) == null;

        assert SlotCycler.index(0, 3) != SlotCycler.index(20, 3);

        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ItemStack fallback = ItemStack.EMPTY;
        ItemStack a = new ItemStack(Items.APPLE);
        ItemStack b = new ItemStack(Items.BREAD);
        ItemStack c = new ItemStack(Items.CARROT);
        List<SlotOptions> multi = List.of(new SlotOptions("", List.of(a, b, c), 3));
        ItemStack at0 = CardSlotPick.pick(fallback, multi, 0, 0);
        ItemStack at20 = CardSlotPick.pick(fallback, multi, 0, 20);
        assert !ItemStack.matches(at0, at20);

        System.out.println("check CardSlotPickCheck OK");
    }
}
