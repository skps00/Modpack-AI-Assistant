package com.skps9.packai.logic;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.skps9.packai.config.PackAiConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Client-thread driver for incremental {@link ObtainGraph} builds.
 * Snapshot + feed only on client thread; workers later read published immutable graph when {@link #ready()}.
 */
public final class ObtainGraphRuntime {
    public static final ObtainGraphRuntime INSTANCE = new ObtainGraphRuntime();

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int BATCH_SIZE = 256;
    private static final long TICK_BUDGET_NS = 2_000_000L;

    private volatile ObtainGraph graph;
    private volatile boolean ready;
    private volatile long generation;
    private volatile boolean building;

    /** Idempotent gate: one client-thread rebuild request until build starts/finishes/clears. */
    private final AtomicBoolean clientRebuildQueued = new AtomicBoolean(false);

    private final ArrayDeque<List<Recipe<?>>> pending = new ArrayDeque<>();
    private ObtainGraph.Builder builder;

    private ObtainGraphRuntime() {}

    public ObtainGraph graph() {
        return graph;
    }

    public boolean ready() {
        return ready;
    }

    public boolean building() {
        return building;
    }

    public long generation() {
        return generation;
    }

    /**
     * When obtainPaths is on but the graph is not ready and no build is pending, queue one
     * {@link #onRecipesUpdated} on the client thread (mid-session enable). Worker threads must
     * only touch MC via {@link Minecraft#execute}.
     */
    public void requestClientRebuildIfNeeded() {
        if (!PackAiConfig.obtainPaths()) {
            return;
        }
        if (ready || building) {
            return;
        }
        if (!clientRebuildQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                clientRebuildQueued.set(false);
                return;
            }
            mc.execute(() -> {
                try {
                    if (!PackAiConfig.obtainPaths()) {
                        return;
                    }
                    if (ready || building) {
                        return;
                    }
                    Minecraft m = Minecraft.getInstance();
                    if (m == null) {
                        return;
                    }
                    RecipeManager rm = m.level == null ? null : m.level.getRecipeManager();
                    if (rm != null) {
                        onRecipesUpdated(rm);
                    }
                } finally {
                    if (!building) {
                        clientRebuildQueued.set(false);
                    }
                }
            });
        } catch (Throwable t) {
            clientRebuildQueued.set(false);
        }
    }

    /**
     * Called on the client thread from {@code RecipesUpdatedEvent}.
     * Snapshots recipes immediately; never keep the live collection.
     * No-op (and clears) when {@link PackAiConfig#obtainPaths()} is off.
     */
    public void onRecipesUpdated(RecipeManager rm) {
        if (!PackAiConfig.obtainPaths()) {
            clear();
            return;
        }
        if (rm == null) {
            clear();
            return;
        }
        List<Recipe<?>> all = List.copyOf(rm.getRecipes());
        long gen = ++generation;
        ready = false;
        building = true;
        pending.clear();
        builder = new ObtainGraph.Builder(gen);
        for (int i = 0; i < all.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, all.size());
            pending.add(List.copyOf(all.subList(i, end)));
        }
        LOGGER.info("obtain.graph.snapshot gen={} recipes={}", gen, all.size());
    }

    /**
     * Called every client tick END. Feeds queued batches within a 2 ms budget.
     * Budget is checked inside each batch slice (per recipe), not only between batches.
     * Does not touch {@link RecipeManager}.
     */
    public void tick() {
        if (!building || builder == null) {
            return;
        }
        long gen = generation;
        long t0 = System.nanoTime();
        while (!pending.isEmpty()) {
            if ((System.nanoTime() - t0) >= TICK_BUDGET_NS) {
                return;
            }
            List<Recipe<?>> batch = pending.poll();
            if (batch == null || batch.isEmpty()) {
                continue;
            }
            int start = 0;
            while (start < batch.size()) {
                if ((System.nanoTime() - t0) >= TICK_BUDGET_NS) {
                    pending.addFirst(List.copyOf(batch.subList(start, batch.size())));
                    return;
                }
                builder.feed(List.of(batch.get(start)), gen);
                start++;
            }
        }
        try {
            long f0 = System.nanoTime();
            ObtainGraph finished = builder.finish();
            long finishMs = (System.nanoTime() - f0) / 1_000_000L;
            graph = finished;
            ready = true;
            building = false;
            builder = null;
            clientRebuildQueued.set(false);
            LOGGER.info(
                    "obtain.graph.ready gen={} edges={} finish_ms={}",
                    gen,
                    finished.edgeCount(),
                    finishMs);
        } catch (Throwable t) {
            building = false;
            builder = null;
            ready = false;
            clientRebuildQueued.set(false);
            LOGGER.info("obtain.graph.finish_failed", t);
        }
    }

    public void clear() {
        long gen = ++generation;
        graph = null;
        ready = false;
        building = false;
        builder = null;
        pending.clear();
        clientRebuildQueued.set(false);
        LOGGER.info("obtain.graph.clear gen={}", gen);
    }
}
