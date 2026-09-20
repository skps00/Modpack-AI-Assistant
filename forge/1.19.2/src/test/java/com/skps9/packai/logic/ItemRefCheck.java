package com.skps9.packai.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Runnable check: ItemRef sample keeps NBT for tooltip rebuild. */
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
        // evidence: damageable ItemStack ctor writes {Damage:0} → hasTag() true → preferFocusNbt keeps built
        System.out.println("ItemRefCheck debug bareSword.hasTag=" + bareSword.hasTag()
                + " bareTag=" + bareSword.getTag()
                + " mergedTag=" + merged.getTag()
                + " sword/blade='" + (merged.getTag() == null ? "null" : merged.getTag().getString("sword/blade")) + "'");
        assert merged.hasTag() : "bare rebuild must copy focus NBT";
        // CASE B (prod bug): hasVariantData treats Damage:0 as variant → focus NBT not copied.
        // Keep failing until ItemResolver.hasVariantData ignores durability-only tags.
        assert "sword/basic_blade".equals(merged.getTag().getString("sword/blade"));
        ItemStack already = richSword.copy();
        already.getTag().putString("keep", "yes");
        ItemStack kept = ItemResolver.preferFocusNbt(already, richSword);
        assert "yes".equals(kept.getTag().getString("keep")) : "built NBT wins over focus";
        ItemStack other = ItemResolver.preferFocusNbt(new ItemStack(Items.STICK), richSword);
        assert other.getItem() == Items.STICK : "different item stays built";

        // regression: built with only {Damage:0} must copy focus variant data
        ItemStack dmgOnly = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag wear = new CompoundTag();
        wear.putInt("Damage", 0);
        dmgOnly.setTag(wear);
        ItemStack focusVariant = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag focusTag = new CompoundTag();
        focusTag.putString("sword/blade", "sword/basic_blade");
        focusVariant.setTag(focusTag);
        ItemStack fromWear = ItemResolver.preferFocusNbt(dmgOnly, focusVariant);
        assert fromWear.hasTag() : "Damage-only built must copy focus NBT";
        assert "sword/basic_blade".equals(fromWear.getTag().getString("sword/blade"))
                : "Damage-only built must copy focus variant";

        // regression: built with real variant (sword/blade=sword/x) wins — no overwrite
        ItemStack builtVariant = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag builtTag = new CompoundTag();
        builtTag.putString("sword/blade", "sword/x");
        builtVariant.setTag(builtTag);
        ItemStack builtWins = ItemResolver.preferFocusNbt(builtVariant, focusVariant);
        assert "sword/x".equals(builtWins.getTag().getString("sword/blade"))
                : "built real variant must not be overwritten";

        System.out.println("ItemRefCheck OK");
    }
}
