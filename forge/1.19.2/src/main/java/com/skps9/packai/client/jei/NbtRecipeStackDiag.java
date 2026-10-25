package com.skps9.packai.client.jei;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.skps9.packai.PackAiMod;

import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Session-capped NBT key diagnostics for JEI recipe stacks (no NBT values). */
final class NbtRecipeStackDiag {
    private static final int CAP = 40;
    private static final AtomicInteger NBT_DIAG_LINES = new AtomicInteger();

    private NbtRecipeStackDiag() {}

    static void maybeLogSlotStack(RecipeIngredientRole role, ItemStack stack) {
        if (role == RecipeIngredientRole.OUTPUT) {
            maybeLogOutput(stack);
        } else {
            maybeLogIngredient(stack);
        }
    }

    static void maybeLogIngredient(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag() || NBT_DIAG_LINES.get() >= CAP) {
            return;
        }
        try {
            synchronized (NbtRecipeStackDiag.class) {
                if (NBT_DIAG_LINES.get() >= CAP) {
                    return;
                }
                NBT_DIAG_LINES.incrementAndGet();
                String id = String.valueOf(Registry.ITEM.getKey(stack.getItem()));
                Set<String> keys = stack.getTag().getAllKeys();
                String hover = stack.getHoverName().getString();
                List<String> tip = stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL)
                        .stream()
                        .map(c -> c.getString())
                        .limit(3)
                        .toList();
                PackAiMod.LOGGER.info("Pack AI nbt ingredient id={} keys={} hover={} tip={}", id, keys, hover, tip);
            }
        } catch (Throwable ignored) {
            // diagnostic only
        }
    }

    static void maybeLogOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag() || NBT_DIAG_LINES.get() >= CAP) {
            return;
        }
        try {
            synchronized (NbtRecipeStackDiag.class) {
                if (NBT_DIAG_LINES.get() >= CAP) {
                    return;
                }
                NBT_DIAG_LINES.incrementAndGet();
                String id = String.valueOf(Registry.ITEM.getKey(stack.getItem()));
                Set<String> keys = stack.getTag().getAllKeys();
                String hover = stack.getHoverName().getString();
                PackAiMod.LOGGER.info("Pack AI nbt output id={} keys={} hover={}", id, keys, hover);
            }
        } catch (Throwable ignored) {
            // diagnostic only
        }
    }
}
