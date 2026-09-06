package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

/** Live Minecraft context for {@link com.skps9.packai.api.AskTool} adapters. Bound via {@link AskToolLoop#bindEnv}. */
public final class AskToolEnv {
    public final ItemStack stack;
    public final PackIndex index;
    public final Path gameDir;
    public final List<String> scanners;
    public final ItemRef held;
    /** Set by JeiLookup when a Pass 2 station template was used. */
    public boolean jeiStationTemplate;
    public String purposeTooltip = "";
    public List<String> recipeCardLines = List.of();
    /**
     * Pending card-strip emissions for this env bind. Flushed into {@link AskLoopState}
     * before {@link AskToolLoop#clearEnv()} (R2 pin — do not change AskTool return type).
     */
    public final ArrayList<CardEmission> pendingEmissions = new ArrayList<>();

    public AskToolEnv(ItemStack stack, PackIndex index, Path gameDir, List<String> scanners, ItemRef held) {
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        this.index = index;
        this.gameDir = gameDir;
        this.scanners = scanners == null ? List.of() : scanners;
        this.held = held == null ? ItemRef.NONE : held;
    }

    public static AskToolEnv current() {
        Object env = AskToolLoop.env();
        return env instanceof AskToolEnv e ? e : null;
    }

    /**
     * Queue a card for the strip. Enforces ask-wide cap/dedupe against already-queued
     * emissions in this env (state flush merges with the same rules).
     *
     * @return false when cap hit or duplicate
     */
    public boolean offerEmission(CardEmission emission) {
        if (emission == null || emission.card() == null || emission.card().isEmpty()) {
            return false;
        }
        if (pendingEmissions.size() >= AskLoopState.MAX_CARD_EMISSIONS) {
            return false;
        }
        String key = emission.dedupeKey();
        for (CardEmission existing : pendingEmissions) {
            if (existing != null && key.equals(existing.dedupeKey())) {
                return false;
            }
        }
        pendingEmissions.add(emission);
        return true;
    }

    /** Copy pending emissions into loop state (call before clearEnv). */
    public void flushEmissionsTo(AskLoopState state) {
        if (state == null || pendingEmissions.isEmpty()) {
            return;
        }
        for (CardEmission em : pendingEmissions) {
            state.offerCardEmission(em);
        }
        pendingEmissions.clear();
    }
}
