package com.skps9.packai.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Runnable check: ItemRef sample keeps NBT for tooltip rebuild. */
public final class ItemRefCheck {
    private ItemRefCheck() {}

    public static void main(String[] args) {
        ItemRef a = new ItemRef("the_bumblezone:honey_compass", "Honey Compass (Throne)");
        assert a.label().equals("Honey Compass (Throne)") : "hover text must be sent as-is";
        assert a.hintTokens().contains("throne");
        assert !a.hasSample() : "2-arg ctor has no sample";
        ItemRef bare = new ItemRef("minecraft:stick", null);
        assert "stick".equals(bare.label());

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag tag = new CompoundTag();
        tag.putString("Ability", "endure");
        sword.setTag(tag);
        ItemRef rich = new ItemRef("minecraft:diamond_sword", "Sword", sword);
        assert rich.hasSample();
        ItemStack rebuilt = ItemResolver.stackFromRef(rich);
        assert rebuilt.hasTag() : "stackFromRef must keep NBT";
        assert "endure".equals(rebuilt.getTag().getString("Ability"));

        ItemRef idOnly = new ItemRef("minecraft:stick", "Stick");
        assert ItemResolver.stackFromRef(idOnly).getItem() == Items.STICK;

        System.out.println("ItemRefCheck OK");
    }
}
