package com.skps9.packai.logic;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Runnable check: ItemRef sample + preferFocusNbt variant data.
 */
public final class ItemRefCheck {
    private ItemRefCheck() {}

    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ItemRef a = new ItemRef("the_bumblezone:honey_compass", "Honey Compass (Throne)");
        assert a.label().equals("Honey Compass (Throne)") : "hover text must be sent as-is";
        assert a.hintTokens().contains("throne");
        assert !a.hasSample() : "2-arg ctor has no sample";
        ItemRef bare = new ItemRef("minecraft:stick", null);
        assert "stick".equals(bare.label());

        // regression: built with only {Damage:0} must copy focus variant data
        ItemStack dmgOnly = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag wear = new CompoundTag();
        wear.putInt("Damage", 0);
        dmgOnly.set(DataComponents.CUSTOM_DATA, CustomData.of(wear));
        ItemStack focusVariant = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag focusTag = new CompoundTag();
        focusTag.putString("sword/blade", "sword/basic_blade");
        focusVariant.set(DataComponents.CUSTOM_DATA, CustomData.of(focusTag));
        ItemStack fromWear = ItemResolver.preferFocusNbt(dmgOnly, focusVariant);
        CustomData fromWearData = fromWear.get(DataComponents.CUSTOM_DATA);
        assert fromWearData != null : "Damage-only built must copy focus data";
        assert "sword/basic_blade".equals(fromWearData.copyTag().getString("sword/blade"))
                : "Damage-only built must copy focus variant";

        // regression: built with real variant (sword/blade=sword/x) wins — no overwrite
        ItemStack builtVariant = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag builtTag = new CompoundTag();
        builtTag.putString("sword/blade", "sword/x");
        builtVariant.set(DataComponents.CUSTOM_DATA, CustomData.of(builtTag));
        ItemStack builtWins = ItemResolver.preferFocusNbt(builtVariant, focusVariant);
        CustomData builtWinsData = builtWins.get(DataComponents.CUSTOM_DATA);
        assert builtWinsData != null;
        assert "sword/x".equals(builtWinsData.copyTag().getString("sword/blade"))
                : "built real variant must not be overwritten";

        System.out.println("ItemRefCheck OK");
    }
}
