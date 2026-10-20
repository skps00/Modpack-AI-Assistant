package com.skps9.packai.logic;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Client-thread driver for incremental {@link ObtainGraph} builds.
 * Snapshot + feed only on client thread; workers later read published immutable graph when {@link #ready()}.
 */
public final class ObtainGraphRuntime {
    public static final ObtainGraphRuntime INSTANCE = new ObtainGraphRuntime();

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int BATCH_SIZE = 2000;
    private static final long TICK_BUDGET_NS = 2_000_000L;

    private volatile ObtainGraph graph;
    private volatile boolean ready;
    private volatile long generation;
    private volatile boolean building;

    private final Queue<List<Recipe<?>>> pending = new ArrayDeque<>();
    private ObtainGraph.Builder builder;

    private ObtainGraphRuntime() {}

    public ObtainGraph graph() {
        return graph;
    }

    public boolean ready() {
        return ready;
    }

    public long generation() {
        return generation;
    }

    /**
     * Called on the client thread from {@code RecipesUpdatedEvent}.
     * Snapshots recipes immediately; never keep the live collection.
     */
    public void onRecipesUpdated(RecipeManager rm) {
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
     * Does not touch {@link RecipeManager}.
     */
    public void tick() {
        if (!building || builder == null) {
            return;
        }
        long gen = generation;
        long t0 = System.nanoTime();
        while (!pending.isEmpty() && (System.nanoTime() - t0) < TICK_BUDGET_NS) {
            List<Recipe<?>> batch = pending.poll();
            if (batch != null) {
                builder.feed(batch, gen);
            }
        }
        if (!pending.isEmpty()) {
            return;
        }
        try {
            long f0 = System.nanoTime();
            ObtainGraph finished = builder.finish();
            long finishMs = (System.nanoTime() - f0) / 1_000_000L;
            graph = finished;
            ready = true;
            building = false;
            builder = null;
            LOGGER.info(
                    "obtain.graph.ready gen={} edges={} finish_ms={}",
                    gen,
                    finished.edgeCount(),
                    finishMs);
        } catch (Throwable t) {
            building = false;
            builder = null;
            ready = false;
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
        LOGGER.info("obtain.graph.clear gen={}", gen);
    }
}
