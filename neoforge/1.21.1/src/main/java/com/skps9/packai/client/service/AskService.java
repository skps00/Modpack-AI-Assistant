package com.skps9.packai.client.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.context.GameContextCollector;
import com.skps9.packai.client.context.SeasonContext;
import com.skps9.packai.client.context.TooltipCapture;
import com.skps9.packai.client.jei.JeiLookup;
import com.skps9.packai.client.jei.JeiRecipeCards;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.client.patchouli.PatchouliGuideLookup;
import com.skps9.packai.logic.AskEngine;
import com.skps9.packai.logic.AskJeiHints;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ItemRef;
import com.skps9.packai.logic.PsiHelper;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Client ask entry — capture item text on the game thread, then run AskEngine off-thread.
 */
public final class AskService {
    public static final AskService INSTANCE = new AskService();

    private AskService() {}

    public void askAsync(String question, Consumer<AskResult> onResult) {
        askAsync(question, List.of(), false, List.of(), ItemStack.EMPTY, onResult);
    }

    public void askAsync(
            String question,
            List<ItemRef> selectedItems,
            boolean questOverride,
            List<ChatMessage> history,
            Consumer<AskResult> onResult
    ) {
        askAsync(question, selectedItems, questOverride, history, ItemStack.EMPTY, onResult);
    }

    /**
     * @param stripFocus exact stack the assistant strip shows ({@code contextStack}); when non-empty,
     *                   do not re-resolve from the full question (id-in-question can diverge from draft/hover).
     */
    public void askAsync(
            String question,
            List<ItemRef> selectedItems,
            boolean questOverride,
            List<ChatMessage> history,
            ItemStack stripFocus,
            Consumer<AskResult> onResult
    ) {
        runAsk(question, selectedItems, questOverride, history, stripFocus, onResult);
    }

    private void runAsk(
            String question,
            List<ItemRef> selectedItems,
            boolean questOverride,
            List<ChatMessage> history,
            ItemStack stripFocus,
            Consumer<AskResult> onResult
    ) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        List<String> modIds = loadedModIds();
        GameContextCollector.collect(false); // fingerprint / dim side effects only
        List<ItemRef> selected = normalizeSelected(selectedItems);

        ItemStack jeiTarget = resolveAskTarget(mc, question, stripFocus);
        JeiTargetResolver.clearPin();
        final ItemRef focusItem = resolveFocus(jeiTarget, selected);
        final List<ItemRef> extras = extrasFor(focusItem, selected);

        StringBuilder jeiBlock = new StringBuilder();
        final String replyLang = clientLanguageCode(mc);
        String season = mc.player == null
                ? ""
                : SeasonContext.summary(mc.player, modIds, question, focusItem.id(), replyLang);
        if (season != null && !season.isBlank()) {
            jeiBlock.append(season).append('\n');
        }
        String psi = PsiHelper.promptAddon(question, modIds, replyLang);
        if (!psi.isBlank()) {
            jeiBlock.append(psi).append('\n');
        }
        final List<RecipeCard> recipeCards = JeiRecipeCards.forItem(jeiTarget, 3);
        boolean hasCards = recipeCards != null && !recipeCards.isEmpty();
        String jeiSummary = JeiLookup.summarize(jeiTarget);
        String firstTitle = hasCards ? recipeCards.get(0).categoryTitle() : "";
        String chosen = AskJeiHints.chooseJeiSummaryText(replyLang, jeiSummary, hasCards, firstTitle);
        if (chosen != null && !chosen.isBlank()) {
            if (!jeiBlock.isEmpty()) {
                jeiBlock.append('\n');
            }
            jeiBlock.append(chosen);
        } else if (AskJeiHints.shouldAppendNoJeiRecipes(hasCards, focusItem.isPresent(), jeiTarget.isEmpty())) {
            jeiBlock.append(ReplyLang.jeiNoRecipes(replyLang));
        } else if (AskJeiHints.shouldAppendJeiHintEmpty(hasCards, focusItem.isPresent(), jeiTarget.isEmpty())) {
            jeiBlock.append(ReplyLang.jeiHintEmpty(replyLang));
        }
        final String jei = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        final String purposeTooltip = (jeiTarget != null && !jeiTarget.isEmpty())
                ? TooltipCapture.capture(jeiTarget, mc.player)
                : "";
        final String purposeGuide = (jeiTarget != null && !jeiTarget.isEmpty())
                ? PatchouliGuideLookup.lookup(jeiTarget)
                : "";
        final List<ChatMessage> prior = history == null ? List.of() : List.copyOf(history);

        CompletableFuture.supplyAsync(() -> {
                    try {
                        return AskEngine.INSTANCE.ask(
                                question, gameDir, modIds, focusItem, extras, questOverride, jei, prior,
                                replyLang, purposeTooltip, purposeGuide);
                    } catch (Exception e) {
                        PackAiMod.LOGGER.error("AskEngine failed", e);
                        return AskResult.text(ReplyLang.queryFailed(replyLang, e.getMessage()));
                    }
                })
                .whenComplete((result, err) -> mc.execute(() -> {
                    if (err != null) {
                        PackAiMod.LOGGER.error("Ask failed", err);
                        onResult.accept(AskResult.text("Error: " + err.getMessage()));
                    } else if (result == null) {
                        onResult.accept(AskResult.text(""));
                    } else {
                        onResult.accept(result.withRecipeCards(recipeCards));
                    }
                }));
    }

    /** Prefer strip focus; else resolveStable(question) — no live JEI hover. */
    static ItemStack resolveAskTarget(Minecraft mc, String question, ItemStack stripFocus) {
        if (stripFocus != null && !stripFocus.isEmpty()) {
            return stripFocus.copy();
        }
        return JeiTargetResolver.resolveStable(mc, question);
    }

    public void warmupAsync() {
        CompletableFuture.runAsync(this::warmupBlocking);
    }

    public AskResult askBlocking(
            String question,
            List<ItemRef> selectedItems,
            boolean questOverride,
            List<ChatMessage> history
    ) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        List<String> modIds = loadedModIds();
        GameContextCollector.collect(false);
        List<ItemRef> selected = normalizeSelected(selectedItems);
        ItemStack jeiTarget = resolveAskTarget(mc, question, ItemStack.EMPTY);
        JeiTargetResolver.clearPin();
        ItemRef focusItem = resolveFocus(jeiTarget, selected);
        List<ItemRef> extras = extrasFor(focusItem, selected);
        final String replyLang = clientLanguageCode(mc);
        StringBuilder jeiBlock = new StringBuilder();
        String season = mc.player == null
                ? ""
                : SeasonContext.summary(mc.player, modIds, question, focusItem.id(), replyLang);
        if (season != null && !season.isBlank()) {
            jeiBlock.append(season).append('\n');
        }
        String psi = PsiHelper.promptAddon(question, modIds, replyLang);
        if (!psi.isBlank()) {
            jeiBlock.append(psi).append('\n');
        }
        List<RecipeCard> recipeCards = JeiRecipeCards.forItem(jeiTarget, 3);
        boolean hasCards = recipeCards != null && !recipeCards.isEmpty();
        String jeiSummary = JeiLookup.summarize(jeiTarget);
        String firstTitle = hasCards ? recipeCards.get(0).categoryTitle() : "";
        String chosen = AskJeiHints.chooseJeiSummaryText(replyLang, jeiSummary, hasCards, firstTitle);
        if (chosen != null && !chosen.isBlank()) {
            if (!jeiBlock.isEmpty()) {
                jeiBlock.append('\n');
            }
            jeiBlock.append(chosen);
        }
        final String jei = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        final String purposeTooltip = (jeiTarget != null && !jeiTarget.isEmpty())
                ? TooltipCapture.capture(jeiTarget, mc.player)
                : "";
        final String purposeGuide = (jeiTarget != null && !jeiTarget.isEmpty())
                ? PatchouliGuideLookup.lookup(jeiTarget)
                : "";
        try {
            AskResult result = AskEngine.INSTANCE.ask(
                    question, gameDir, modIds, focusItem, extras, questOverride, jei,
                    history == null ? List.of() : history,
                    replyLang, purposeTooltip, purposeGuide);
            return result.withRecipeCards(recipeCards);
        } catch (Exception e) {
            PackAiMod.LOGGER.error("AskEngine failed", e);
            return AskResult.text(ReplyLang.queryFailed(replyLang, e.getMessage()));
        }
    }

    /** JEI pin / id-in-question wins; else first selected; else none (no auto-held). */
    static ItemRef resolveFocus(ItemStack jeiTarget, List<ItemRef> selected) {
        if (jeiTarget != null && !jeiTarget.isEmpty()) {
            var key = BuiltInRegistries.ITEM.getKey(jeiTarget.getItem());
            if (key != null) {
                return new ItemRef(key.toString(), jeiTarget.getHoverName().getString());
            }
        }
        if (selected != null) {
            for (ItemRef ref : selected) {
                if (ref != null && ref.isPresent()) {
                    return ref;
                }
            }
        }
        return ItemRef.NONE;
    }

    static List<ItemRef> extrasFor(ItemRef focus, List<ItemRef> selected) {
        if (selected == null || selected.isEmpty()) {
            return List.of();
        }
        String focusId = focus != null && focus.isPresent()
                ? focus.id().toLowerCase(Locale.ROOT)
                : "";
        List<ItemRef> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ItemRef ref : selected) {
            if (ref == null || !ref.isPresent()) {
                continue;
            }
            String id = ref.id().toLowerCase(Locale.ROOT);
            if (id.equals(focusId) || !seen.add(id)) {
                continue;
            }
            out.add(ref);
        }
        return out;
    }

    static List<ItemRef> normalizeSelected(List<ItemRef> selectedItems) {
        List<ItemRef> raw = selectedItems == null ? ChatSession.pendingItems() : selectedItems;
        List<ItemRef> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ItemRef ref : raw) {
            if (ref == null || !ref.isPresent()) {
                continue;
            }
            if (!seen.add(ref.id().toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(ref);
            if (out.size() >= ChatSession.MAX_PENDING_ITEMS) {
                break;
            }
        }
        return out;
    }

    public static ItemRef fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemRef.NONE;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            return ItemRef.NONE;
        }
        return new ItemRef(key.toString(), stack.getHoverName().getString());
    }

    static String clientLanguageCode(Minecraft mc) {
        if (mc == null || mc.getLanguageManager() == null) {
            return "zh_tw";
        }
        String code = mc.getLanguageManager().getSelected();
        return code == null || code.isBlank() ? "zh_tw" : code.trim();
    }

    private void warmupBlocking() {
        try {
            Minecraft mc = Minecraft.getInstance();
            GameContextCollector.resetFingerprintCache();
            AskEngine.INSTANCE.warmup(mc.gameDirectory.toPath(), loadedModIds());
            PackAiMod.LOGGER.info("Pack AI index warmup done");
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("Pack AI warmup skipped: {}", e.toString());
        }
    }

    private static List<String> loadedModIds() {
        List<String> modIds = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            modIds.add(info.getModId());
        }
        modIds.sort(String::compareTo);
        return modIds;
    }
}
