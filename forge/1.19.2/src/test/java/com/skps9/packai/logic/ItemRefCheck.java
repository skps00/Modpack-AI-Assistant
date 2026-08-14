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

        ItemStack bareSword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack richSword = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag tetra = new CompoundTag();
        tetra.putString("sword/blade", "sword/basic_blade");
        tetra.putString("displayName", "悟");
        richSword.setTag(tetra);
        ItemStack merged = ItemResolver.preferFocusNbt(bareSword, richSword);
        assert merged.hasTag() : "bare rebuild must copy focus NBT";
        assert "sword/basic_blade".equals(merged.getTag().getString("sword/blade"));
        ItemStack already = richSword.copy();
        already.getTag().putString("keep", "yes");
        ItemStack kept = ItemResolver.preferFocusNbt(already, richSword);
        assert "yes".equals(kept.getTag().getString("keep")) : "built NBT wins over focus";
        ItemStack other = ItemResolver.preferFocusNbt(new ItemStack(Items.STICK), richSword);
        assert other.getItem() == Items.STICK : "different item stays built";

        System.out.println("ItemRefCheck OK");
    }
}
