package com.skps9.packai.client.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.client.context.GameContextCollector;
import com.skps9.packai.client.context.SeasonContext;
import com.skps9.packai.client.jei.JeiLookup;
import com.skps9.packai.client.jei.JeiRecipeCards;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.logic.AskEngine;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ItemRef;
import com.skps9.packai.logic.PsiHelper;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.client.Minecraft;
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
        askAsync(question, false, false, onResult);
    }

    public void askAsync(String question, boolean includeHotbar, Consumer<AskResult> onResult) {
        askAsync(question, includeHotbar, false, onResult);
    }

    public void askAsync(String question, boolean includeHotbar, boolean questOverride, Consumer<AskResult> onResult) {
        askAsync(question, includeHotbar, questOverride, List.of(), onResult);
    }

    public void askAsync(
            String question,
            boolean includeHotbar,
            boolean questOverride,
            List<ChatMessage> history,
            Consumer<AskResult> onResult
    ) {
        runAsk(question, includeHotbar, questOverride, history, onResult);
    }

    private void runAsk(
            String question,
            boolean includeHotbar,
            boolean questOverride,
            List<ChatMessage> history,
            Consumer<AskResult> onResult
    ) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        List<String> modIds = loadedModIds();
        Map<String, Object> ctx = GameContextCollector.collect(includeHotbar);
        ItemRef held = itemRef(ctx.get("heldItem"));
        List<ItemRef> hotbar = includeHotbar ? itemRefs(ctx.get("hotbar")) : List.of();

        ItemStack jeiTarget = JeiTargetResolver.resolve(mc, question);
        JeiTargetResolver.clearPin();
        // Identity for ask/LLM must match the JEI lookup target (id-in-question / pin / hand).
        final ItemRef focusItem;
        if (!jeiTarget.isEmpty()) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(jeiTarget.getItem());
            if (key != null) {
                focusItem = new ItemRef(key.toString(), jeiTarget.getHoverName().getString());
            } else {
                focusItem = held;
            }
        } else {
            focusItem = held;
        }
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
        String jeiSummary = JeiLookup.summarize(jeiTarget);
        if (jeiSummary != null && !jeiSummary.isBlank()) {
            if (!jeiBlock.isEmpty()) {
                jeiBlock.append('\n');
            }
            jeiBlock.append(jeiSummary);
        } else if (focusItem.isPresent() && jeiTarget.isEmpty()) {
            jeiBlock.append(ReplyLang.jeiNoRecipes(replyLang));
        } else if (!focusItem.isPresent() && jeiTarget.isEmpty()) {
            jeiBlock.append(ReplyLang.jeiHintEmpty(replyLang));
        }
        final String jei = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        final List<ChatMessage> prior = history == null ? List.of() : List.copyOf(history);
        // Capture cards on client thread (JEI); attach after AskEngine returns.
        final List<RecipeCard> recipeCards = JeiRecipeCards.forItem(jeiTarget, 3);

        CompletableFuture.supplyAsync(() -> {
                    try {
                        return AskEngine.INSTANCE.ask(
                                question, gameDir, modIds, focusItem, hotbar, questOverride, jei, prior, replyLang);
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

    public void warmupAsync() {
        CompletableFuture.runAsync(this::warmupBlocking);
    }

    public AskResult askBlocking(
            String question,
            boolean includeHotbar,
            boolean questOverride,
            List<ChatMessage> history
    ) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        List<String> modIds = loadedModIds();
        Map<String, Object> ctx = GameContextCollector.collect(includeHotbar);
        ItemRef held = itemRef(ctx.get("heldItem"));
        List<ItemRef> hotbar = includeHotbar ? itemRefs(ctx.get("hotbar")) : List.of();
        ItemStack jeiTarget = JeiTargetResolver.resolve(mc, question);
        JeiTargetResolver.clearPin();
        ItemRef focusItem = held;
        if (!jeiTarget.isEmpty()) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(jeiTarget.getItem());
            if (key != null) {
                focusItem = new ItemRef(key.toString(), jeiTarget.getHoverName().getString());
            }
        }
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
        String jeiSummary = JeiLookup.summarize(jeiTarget);
        if (jeiSummary != null && !jeiSummary.isBlank()) {
            if (!jeiBlock.isEmpty()) {
                jeiBlock.append('\n');
            }
            jeiBlock.append(jeiSummary);
        }
        final String jei = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        List<RecipeCard> recipeCards = JeiRecipeCards.forItem(jeiTarget, 3);
        try {
            AskResult result = AskEngine.INSTANCE.ask(
                    question, gameDir, modIds, focusItem, hotbar, questOverride, jei,
                    history == null ? List.of() : history,
                    replyLang);
            return result.withRecipeCards(recipeCards);
        } catch (Exception e) {
            PackAiMod.LOGGER.error("AskEngine failed", e);
            return AskResult.text(ReplyLang.queryFailed(replyLang, e.getMessage()));
        }
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

    private static ItemRef itemRef(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return ItemRef.NONE;
        }
        if (Boolean.TRUE.equals(m.get("empty"))) {
            return ItemRef.NONE;
        }
        Object id = m.get("id");
        if (id == null || id.toString().isBlank()) {
            return ItemRef.NONE;
        }
        // Prefer short hover name; full tooltip is too noisy for matching / identity.
        Object name = m.get("name");
        if (name == null || name.toString().isBlank()) {
            name = m.get("displayName");
        }
        String label = name == null ? null : firstLine(name.toString());
        return new ItemRef(id.toString(), label);
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        int nl = text.indexOf('\n');
        String line = nl < 0 ? text.trim() : text.substring(0, nl).trim();
        return line.length() > 120 ? line.substring(0, 120) : line;
    }

    private static List<ItemRef> itemRefs(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ItemRef> out = new ArrayList<>();
        for (Object o : list) {
            ItemRef ref = itemRef(o);
            if (ref.isPresent()) {
                out.add(ref);
            }
        }
        return out;
    }
}
