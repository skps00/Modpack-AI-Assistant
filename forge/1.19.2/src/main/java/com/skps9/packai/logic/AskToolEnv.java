package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Registry;
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
     * Same RecipeCard catalog that produced {@link #recipeCardLines} / prompt {@code [RECIPE_CARDS]}.
     * Bound from {@link AskLoopState#catalogCards()} so {@code render_recipe_cards(role=uses)}
     * can prefer catalog input-use cards over JEI diversity orphans.
     */
    public List<RecipeCard> catalogCards = List.of();
    /**
     * Pending card-strip emissions for this env bind. Flushed into {@link AskLoopState}
     * before {@link AskToolLoop#clearEnv()} (R2 pin — do not change AskTool return type).
     */
    public final ArrayList<CardEmission> pendingEmissions = new ArrayList<>();
    /**
     * Focus id for {@link ModularFrameCards#shouldDropFrameCard} (B11). Set at bind from
     * {@link AskLoopState#modularFrameDropId()}; blank = no filter. Not {@link #stack}.
     */
    public String modularFrameDropId = "";
    /** STANDARD recipe output to keep; blank = no allow-list. Copied from {@link AskLoopState}. */
    public String frameStandardKeepOutputId = "";
    public java.util.List<String> frameStandardKeepInputIds = java.util.List.of();
    /** Bind-local count of frame cards rejected by {@link #offerEmission} (B11 LD5). */
    public int suppressedFrameOffers;
    /** Ask bag for ask-wide suppress counter; set at bind. May be null in tests. */
    AskLoopState loop;

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
     * Assigns ask-scope {@code [card:N]} ref id (1-based, dense in pending order).
     *
     * @return assigned ref id, or {@code 0} when cap hit / duplicate / invalid
     */
    public int offerEmission(CardEmission emission) {
        if (emission == null || emission.card() == null || emission.card().isEmpty()) {
            return 0;
        }
        RecipeCard card = emission.card();
        // B11: filter before refId assign so digest refs stay dense 1..N per bind.
        if (rejectFrameCard(card)) {
            return 0;
        }
        if (pendingEmissions.size() >= AskLoopState.MAX_CARD_EMISSIONS) {
            return 0;
        }
        String key = emission.dedupeKey();
        for (CardEmission existing : pendingEmissions) {
            if (existing != null && key.equals(existing.dedupeKey())) {
                return 0;
            }
        }
        int refId = pendingEmissions.size() + 1;
        pendingEmissions.add(new CardEmission(emission.itemId(), emission.role(), emission.card(), refId));
        return refId;
    }

    /**
     * Frame-drop gate shared with display suppress (pure ids).
     *
     * @return true when rejected (counters already incremented)
     */
    private boolean rejectFrameCard(RecipeCard card) {
        if (ModularFrameCards.isStandardKeepCard(
                card.primaryOutputId(), card.layoutInputIds(),
                frameStandardKeepOutputId, frameStandardKeepInputIds)) {
            return false;
        }
        if (!ModularFrameCards.shouldDropFrameCard(
                modularFrameDropId, card.primaryOutputId(), card.isInputUse(), card.isTrailingOptional())) {
            return false;
        }
        suppressedFrameOffers++;
        if (loop != null) {
            loop.noteSuppressedFrameOffer();
        }
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
