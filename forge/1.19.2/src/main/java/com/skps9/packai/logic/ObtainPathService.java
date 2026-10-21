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
            String itemId,
            List<ObtainPaths.Path> paths,
            int fluidLines,
            int fluidUnresolved,
            int reqEnergy,
            int reqExtra,
            int reqIncomplete) {

        /** Legacy arity for early exits before requirement census. */
        public Result(
                List<ObtainPathText.Line> lines,
                boolean hasPath,
                boolean ready,
                String source,
                String itemId,
                List<ObtainPaths.Path> paths,
                int fluidLines,
                int fluidUnresolved) {
            this(
                    lines,
                    hasPath,
                    ready,
                    source,
                    itemId,
                    paths,
                    fluidLines,
                    fluidUnresolved,
                    0,
                    0,
                    0);
        }
    }

    private ObtainPathService() {}

    public static Result compute(String itemId, String lang, int budget) {
        // Mid-session enable: kick one client-thread rebuild when graph not ready.
        if (PackAiConfig.obtainPaths()) {
            ObtainGraphRuntime.INSTANCE.requestClientRebuildIfNeeded();
        }
        String id = itemId == null ? "" : itemId.trim();
        if (!ObtainGraphRuntime.INSTANCE.ready() || id.isEmpty()) {
            return new Result(List.of(), false, false, "unknown", id, List.of(), 0, 0);
        }
        ObtainGraph graph = ObtainGraphRuntime.INSTANCE.graph();
        if (graph == null) {
            return new Result(List.of(), false, false, "unknown", id, List.of(), 0, 0);
        }
        Item item;
        try {
            // 1.19.2: Registry.ITEM (BuiltInRegistries arrives later).
            item = Registry.ITEM.get(new ResourceLocation(id));
        } catch (Exception e) {
            return new Result(List.of(), false, true, "unknown", id, List.of(), 0, 0);
        }
        if (item == null || item == Items.AIR) {
            return new Result(List.of(), false, true, "unknown", id, List.of(), 0, 0);
        }
        List<ObtainPaths.Path> paths = ObtainPaths.find(graph, item);
        if (paths == null || paths.isEmpty()) {
            return new Result(List.of(), false, true, "unknown", id, List.of(), 0, 0);
        }
        List<ObtainPathText.Line> lines = ObtainPathText.render(paths, lang, budget);
        if (lines == null) {
            lines = List.of();
        }
        int fluidLines = ObtainPaths.countFluidLines(paths);
        int fluidUnresolved = ObtainPaths.countFluidUnresolved(paths);
        int reqEnergy = ObtainPaths.countReqEnergy(paths);
        int reqExtra = ObtainPaths.countReqExtra(paths);
        int reqIncomplete = ObtainPaths.countReqIncomplete(paths);
        return new Result(
                List.copyOf(lines),
                !lines.isEmpty(),
                true,
                "graph",
                id,
                List.copyOf(paths),
                fluidLines,
                fluidUnresolved,
                reqEnergy,
                reqExtra,
                reqIncomplete);
    }

    public static void traceEvent(String name, Result r) {
        if (!PackAiConfig.obtainPaths()) {
            return;
        }
        Result x = r == null ? new Result(List.of(), false, false, "unknown", "", List.of(), 0, 0) : r;
        int pathLines = x.lines() == null ? 0 : x.lines().size();
        boolean fluidFlip = false;
        ObtainGraph traceGraph = ObtainGraphRuntime.INSTANCE.graph();
        if (traceGraph != null) {
            fluidFlip = traceGraph.completeFlipEnabled();
        }
        final boolean fluidFlipFinal = fluidFlip;
        AskTrace.event(name == null || name.isBlank() ? "obtain.path" : name, o -> {
            o.addProperty("obtain.source", x.source() == null ? "" : x.source());
            o.addProperty("obtain.ready", x.ready());
            o.addProperty("obtain.paths", x.hasPath() ? 1 : 0);
            o.addProperty("obtain.path_lines", pathLines);
            o.addProperty("obtain.fluid_lines", x.fluidLines());
            o.addProperty("obtain.fluid_unresolved", x.fluidUnresolved());
            o.addProperty("obtain.req_energy", x.reqEnergy());
            o.addProperty("obtain.req_extra", x.reqExtra());
            o.addProperty("obtain.req_incomplete", x.reqIncomplete());
            o.addProperty("obtain.fluid_flip", fluidFlipFinal);
            o.addProperty("obtain.item", x.itemId() == null ? "" : x.itemId());
        });
    }
}
