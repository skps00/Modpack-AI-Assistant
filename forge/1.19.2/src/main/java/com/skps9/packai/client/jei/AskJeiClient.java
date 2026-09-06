package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.skps9.packai.logic.AskToolContext;
import com.skps9.packai.logic.RecipeCard;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * JEI summarize / recipe-card collect on the client thread only. Worker may
 * {@code future.get()} a client-completed future; the client thread must never
 * {@code get()} (deadlock with the game loop).
 */
public final class AskJeiClient {
    private AskJeiClient() {}

    public static String summarize(ItemStack stack, AskToolContext.JeiDumpLevel level, long deadlineMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return "";
        }
        if (mc.isSameThread()) {
            return nz(JeiLookup.summarize(stack, level));
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(nz(JeiLookup.summarize(stack, level)));
            } catch (Throwable t) {
                future.complete("");
            }
        });
        long wait = Math.max(1L, deadlineMs - System.currentTimeMillis());
        try {
            return nz(future.get(wait, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            // ponytail: empty rather than crash the Ask worker. Upgrade: retry budget.
            return "";
        }
    }

    /**
     * Collect OUTPUT+INPUT+maintenance/upgrade cards for one item (client thread).
     * Used by {@code render_recipe_cards} tool emission.
     */
    public static List<RecipeCard> recipeCardsForItem(
            ItemStack stack, int maxOutput, int maxInput, long deadlineMs
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || stack == null || stack.isEmpty()) {
            return List.of();
        }
        if (mc.isSameThread()) {
            return collectCards(stack, maxOutput, maxInput);
        }
        CompletableFuture<List<RecipeCard>> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(collectCards(stack, maxOutput, maxInput));
            } catch (Throwable t) {
                future.complete(List.of());
            }
        });
        long wait = Math.max(1L, deadlineMs - System.currentTimeMillis());
        try {
            List<RecipeCard> got = future.get(wait, TimeUnit.MILLISECONDS);
            return got == null ? List.of() : got;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<RecipeCard> collectCards(ItemStack stack, int maxOutput, int maxInput) {
        JeiRecipeCards.ItemParts parts = JeiRecipeCards.forItemParts(stack, maxOutput, maxInput);
        List<RecipeCard> out = new ArrayList<>();
        if (parts.normal() != null) {
            out.addAll(parts.normal());
        }
        if (parts.maintenance() != null) {
            out.addAll(parts.maintenance());
        }
        return List.copyOf(out);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
