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
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.logic.AskEngine;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ItemRef;
import com.skps9.packai.logic.PsiHelper;

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
        StringBuilder jeiBlock = new StringBuilder();
        String season = mc.player == null ? "" : SeasonContext.summary(mc.player);
        if (season != null && !season.isBlank()) {
            jeiBlock.append(season).append('\n');
        }
        String psi = PsiHelper.promptAddon(question);
        if (!psi.isBlank()) {
            jeiBlock.append(psi).append('\n');
        }
        String jeiSummary = JeiLookup.summarize(jeiTarget);
        if (jeiSummary != null && !jeiSummary.isBlank()) {
            if (!jeiBlock.isEmpty()) {
                jeiBlock.append('\n');
            }
            jeiBlock.append(jeiSummary);
        } else if (held.isPresent() && jeiTarget.isEmpty()) {
            jeiBlock.append("【JEI】手上物品無 JEI 配方資料。\n");
        } else if (!held.isPresent() && jeiTarget.isEmpty()) {
            jeiBlock.append("【JEI 提示】未持物品：在問題中寫 mod:id，或開 JEI 把游標停在物品上再提問。\n");
        }
        final String jei = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        final List<ChatMessage> prior = history == null ? List.of() : List.copyOf(history);
        final String replyLang = clientLanguageCode(mc);

        CompletableFuture.supplyAsync(() -> {
                    try {
                        return AskEngine.INSTANCE.ask(
                                question, gameDir, modIds, held, hotbar, questOverride, jei, prior, replyLang);
                    } catch (Exception e) {
                        PackAiMod.LOGGER.error("AskEngine failed", e);
                        return AskResult.text("查詢失敗：" + e.getMessage());
                    }
                })
                .whenComplete((result, err) -> mc.execute(() -> {
                    if (err != null) {
                        PackAiMod.LOGGER.error("Ask failed", err);
                        onResult.accept(AskResult.text("Error: " + err.getMessage()));
                    } else {
                        onResult.accept(result);
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
        String jeiSummary = JeiLookup.summarize(jeiTarget);
        try {
            return AskEngine.INSTANCE.ask(
                    question, gameDir, modIds, held, hotbar, questOverride, jeiSummary,
                    history == null ? List.of() : history,
                    clientLanguageCode(mc));
        } catch (Exception e) {
            PackAiMod.LOGGER.error("AskEngine failed", e);
            return AskResult.text("查詢失敗：" + e.getMessage());
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
        String name = m.get("displayName") == null ? null : m.get("displayName").toString();
        return new ItemRef(id.toString(), name);
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
