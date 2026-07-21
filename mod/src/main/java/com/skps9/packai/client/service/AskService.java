package com.skps9.packai.client.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.context.GameContextCollector;
import com.skps9.packai.logic.AskEngine;
import com.skps9.packai.logic.AskResult;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Client ask entry — runs AskEngine off-thread (no Python Bridge).
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
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> askBlocking(question, includeHotbar, questOverride))
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

    public AskResult askBlocking(String question, boolean includeHotbar, boolean questOverride) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        List<String> modIds = loadedModIds();
        Map<String, Object> ctx = GameContextCollector.collect(includeHotbar);
        String held = heldItemId(ctx);
        try {
            return AskEngine.INSTANCE.ask(question, gameDir, modIds, held, questOverride);
        } catch (Exception e) {
            PackAiMod.LOGGER.error("AskEngine failed", e);
            return AskResult.text("查詢失敗：" + e.getMessage());
        }
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

    private static String heldItemId(Map<String, Object> ctx) {
        Object held = ctx.get("heldItem");
        if (!(held instanceof Map<?, ?> m)) {
            return null;
        }
        Object empty = m.get("empty");
        if (Boolean.TRUE.equals(empty)) {
            return null;
        }
        Object id = m.get("id");
        return id == null ? null : id.toString();
    }
}
