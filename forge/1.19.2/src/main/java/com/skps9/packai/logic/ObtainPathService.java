package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.config.PackAiConfig;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Sole production exit that turns {@link ObtainGraph} + item id into path lines.
 * AskEngine / tools only call {@link #compute}; no second graph walk elsewhere.
 */
public final class ObtainPathService {
    public record Result(
            List<ObtainPathText.Line> lines,
            boolean hasPath,
            boolean ready,
            String source,
            String itemId) {}

    private ObtainPathService() {}

    public static Result compute(String itemId, String lang, int budget) {
        // Mid-session enable: kick one client-thread rebuild when graph not ready.
        if (PackAiConfig.obtainPaths()) {
            ObtainGraphRuntime.INSTANCE.requestClientRebuildIfNeeded();
        }
        String id = itemId == null ? "" : itemId.trim();
        if (!ObtainGraphRuntime.INSTANCE.ready() || id.isEmpty()) {
            return new Result(List.of(), false, false, "unknown", id);
        }
        ObtainGraph graph = ObtainGraphRuntime.INSTANCE.graph();
        if (graph == null) {
            return new Result(List.of(), false, false, "unknown", id);
        }
        Item item;
        try {
            // 1.19.2: Registry.ITEM (BuiltInRegistries arrives later).
            item = Registry.ITEM.get(new ResourceLocation(id));
        } catch (Exception e) {
            return new Result(List.of(), false, true, "unknown", id);
        }
        if (item == null || item == Items.AIR) {
            return new Result(List.of(), false, true, "unknown", id);
        }
        List<ObtainPaths.Path> paths = ObtainPaths.find(graph, item);
        if (paths == null || paths.isEmpty()) {
            return new Result(List.of(), false, true, "unknown", id);
        }
        List<ObtainPathText.Line> lines = ObtainPathText.render(paths, lang, budget);
        if (lines == null) {
            lines = List.of();
        }
        return new Result(List.copyOf(lines), !lines.isEmpty(), true, "graph", id);
    }

    public static void traceEvent(String name, Result r) {
        if (!PackAiConfig.obtainPaths()) {
            return;
        }
        Result x = r == null ? new Result(List.of(), false, false, "unknown", "") : r;
        int pathLines = x.lines() == null ? 0 : x.lines().size();
        AskTrace.event(name == null || name.isBlank() ? "obtain.path" : name, o -> {
            o.addProperty("obtain.source", x.source() == null ? "" : x.source());
            o.addProperty("obtain.ready", x.ready());
            o.addProperty("obtain.paths", x.hasPath() ? 1 : 0);
            o.addProperty("obtain.path_lines", pathLines);
            o.addProperty("obtain.item", x.itemId() == null ? "" : x.itemId());
        });
    }
}
