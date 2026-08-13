package com.skps9.packai.client.jei;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.skps9.packai.logic.AskToolContext;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * JEI summarize on the client thread only. Worker may {@code future.get()} a client-completed
 * future; the client thread must never {@code get()} (deadlock with the game loop).
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
