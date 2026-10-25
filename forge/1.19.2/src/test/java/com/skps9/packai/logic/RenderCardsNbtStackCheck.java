package com.skps9.packai.logic;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** JEI render focus prefers env NBT stack for same registry id. Run with -ea. */
public final class RenderCardsNbtStackCheck {
    private RenderCardsNbtStackCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        usesTaggedEnvStack();
        bareIdWhenEnvUntagged();
        differentIdDoesNotBorrow();
        negativeControlAlwaysEnvFailsDifferentId();
        System.out.println("RenderCardsNbtStackCheck OK");
    }

    private static void usesTaggedEnvStack() {
        String itemId = "minecraft:iron_sword";
        ItemStack tagged = new ItemStack(Items.IRON_SWORD);
        CompoundTag tag = new CompoundTag();
        tag.putString("packai_test", "variant");
        tagged.setTag(tag);
        AskToolEnv env = new AskToolEnv(
                ItemStack.EMPTY, null, null, List.of(),
                new ItemRef(itemId, "Sword", tagged));

        ItemStack resolved = RenderRecipeCardsAskTool.resolveRenderItemStack(itemId, env);
        assert resolved.hasTag() : resolved;
        assert "variant".equals(resolved.getTag().getString("packai_test")) : resolved.getTag();
        System.out.println("usesTaggedEnvStack OK");
    }

    private static void bareIdWhenEnvUntagged() {
        String itemId = "minecraft:stick";
        ItemStack bareStick = new ItemStack(Items.STICK);
        AskToolEnv env = new AskToolEnv(
                bareStick, null, null, List.of(), new ItemRef(itemId, "Stick"));

        ItemStack resolved = RenderRecipeCardsAskTool.resolveRenderItemStack(itemId, env);
        assert Items.STICK == resolved.getItem() : resolved;
        assert !resolved.hasTag() : resolved;
        System.out.println("bareIdWhenEnvUntagged OK");
    }

    private static void differentIdDoesNotBorrow() {
        ItemStack apple = new ItemStack(Items.APPLE);
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", 1);
        apple.setTag(tag);
        AskToolEnv env = new AskToolEnv(apple, null, null, List.of(), ItemRef.NONE);

        ItemStack resolved = RenderRecipeCardsAskTool.resolveRenderItemStack("minecraft:stick", env);
        assert Items.STICK == resolved.getItem() : resolved;
        assert !resolved.hasTag() : resolved;
        System.out.println("differentIdDoesNotBorrow OK");
    }

    private static void negativeControlAlwaysEnvFailsDifferentId() {
        ItemStack apple = new ItemStack(Items.APPLE);
        AskToolEnv env = new AskToolEnv(apple, null, null, List.of(), ItemRef.NONE);
        ItemStack wrong = RenderRecipeCardsAskTool.harnessAlwaysEnvStack("minecraft:stick", env);
        if (wrong.getItem() != Items.APPLE) {
            throw new AssertionError(
                    "negative control FAILED: without id guard, render would not borrow env stack");
        }
        System.out.println("negativeControlAlwaysEnvFailsDifferentId OK");
    }
}
