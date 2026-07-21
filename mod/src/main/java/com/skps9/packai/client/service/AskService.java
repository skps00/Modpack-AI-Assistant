package com.skps9.packai.client.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.context.GameContextCollector;
import com.skps9.packai.client.jei.JeiLookup;
import com.skps9.packai.logic.AskEngine;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ItemRef;

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
        Minecraft mc = Minecraft.getInstance();
        // Tooltip + JEI capture must run on the client thread.
        Path gameDir = mc.gameDirectory.toPath();
        List<String> modIds = loadedModIds();
        Map<String, Object> ctx = GameContextCollector.collect(includeHotbar);
        ItemRef held = itemRef(ctx.get("heldItem"));
        List<ItemRef> hotbar = includeHotbar ? itemRefs(ctx.get("hotbar")) : List.of();
        String jeiSummary = null;
        if (mc.player != null) {
            ItemStack main = mc.player.getMainHandItem();
            jeiSummary = JeiLookup.summarize(main);
        }
        final String jei = jeiSummary;

        CompletableFuture.supplyAsync(() -> {
                    try {
                        return AskEngine.INSTANCE.ask(question, gameDir, modIds, held, hotbar, questOverride, jei);
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

    public AskResult askBlocking(String question, boolean includeHotbar, boolean questOverride) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        List<String> modIds = loadedModIds();
        Map<String, Object> ctx = GameContextCollector.collect(includeHotbar);
        ItemRef held = itemRef(ctx.get("heldItem"));
        List<ItemRef> hotbar = includeHotbar ? itemRefs(ctx.get("hotbar")) : List.of();
        String jeiSummary = null;
        if (mc.player != null) {
            jeiSummary = JeiLookup.summarize(mc.player.getMainHandItem());
        }
        try {
            return AskEngine.INSTANCE.ask(question, gameDir, modIds, held, hotbar, questOverride, jeiSummary);
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
