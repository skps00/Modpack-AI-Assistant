package com.skps9.packai.logic;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.skps9.packai.client.routes.RouteStore;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.resources.ResourceLocation;
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
    static final long TICK_BUDGET_IDLE_NS = 8_000_000L;
    static final long TICK_BUDGET_ACTIVE_NS = 2_000_000L;
    static final long ASK_QUIET_NS = 10_000_000_000L;

    private static volatile long lastAskNanos;
    private static volatile boolean budgetConfigLogged;
    private static final Set<ResourceLocation> PROBE_ITEMS = Set.of(
            new ResourceLocation("thermal", "apatite"),
            new ResourceLocation("electrodynamics", "crystaliron"),
            new ResourceLocation("minecraft", "clay_ball"),
            new ResourceLocation("create", "bar_of_chocolate"));

    private volatile ObtainGraph graph;
    private volatile boolean ready;
    private volatile long generation;
    private volatile boolean building;

    /** Idempotent gate: one client-thread rebuild request until build starts/finishes/clears. */
    private final AtomicBoolean clientRebuildQueued = new AtomicBoolean(false);

    private final ArrayDeque<List<Recipe<?>>> pending = new ArrayDeque<>();
    private ObtainGraph.Builder builder;
    private long buildStartMs;
    private int buildRecipeCount;

    private ObtainGraphRuntime() {}

    /** Called before each obtain-path query so graph build throttles while the player is asking. */
    public static void noteAskActivity() {
        lastAskNanos = System.nanoTime();
    }

    /** Active (low) per-tick build budget in nanoseconds (config or default). */
    public static long activeBudgetNanos() {
        return resolveActiveBudgetNs();
    }

    /** Idle (high) per-tick build budget in nanoseconds (config or default). */
    public static long idleBudgetNanos() {
        return resolveIdleBudgetNs();
    }

    public static long tickBudgetNanos() {
        return tickBudgetNanos(System.nanoTime());
    }

    static long tickBudgetNanos(long nowNanos) {
        if (isIdleBuildBudget(nowNanos, lastAskNanos)) {
            return resolveIdleBudgetNs();
        }
        return resolveActiveBudgetNs();
    }

    static boolean isIdleBuildBudget(long nowNanos, long lastAsk) {
        if (lastAsk == 0L) {
            return true;
        }
        return (nowNanos - lastAsk) > ASK_QUIET_NS;
    }

    static void harnessSetLastAskNanos(long nanos) {
        lastAskNanos = nanos;
    }

    private static long resolveIdleBudgetNs() {
        try {
            return PackAiConfig.obtainBuildBudgetIdleMs() * 1_000_000L;
        } catch (IllegalStateException e) {
            return TICK_BUDGET_IDLE_NS;
        } catch (NullPointerException e) {
            return TICK_BUDGET_IDLE_NS;
        }
    }

    private static long resolveActiveBudgetNs() {
        try {
            return PackAiConfig.obtainBuildBudgetActiveMs() * 1_000_000L;
        } catch (IllegalStateException e) {
            return TICK_BUDGET_ACTIVE_NS;
        } catch (NullPointerException e) {
            return TICK_BUDGET_ACTIVE_NS;
        }
    }

    private static void logBudgetConfigOnce() {
        if (budgetConfigLogged) {
            return;
        }
        budgetConfigLogged = true;
        LOGGER.info("obtain.graph.budget idle_ms=8 active_ms=2 quiet_s=10");
    }

    /** Mod jar fingerprint for obtain-graph cache (same semantics as route library). */
    static String modsStamp() {
        return RouteStore.modsStamp();
    }

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
        int recipeCount = all.size();
        boolean keepServing = graph != null && ready;

        if (PackAiConfig.obtainGraphCache()) {
            String fingerprint = ObtainGraphCache.computeFingerprint(FMLPaths.GAMEDIR.get());
            ObtainGraphCache.LoadResult cached = ObtainGraphCache.tryLoad(fingerprint, recipeCount);
            if (cached.kind() == ObtainGraphCache.LoadKind.HIT && cached.graph() != null) {
                long gen = ++generation;
                graph = cached.graph();
                ready = true;
                building = false;
                pending.clear();
                builder = null;
                clientRebuildQueued.set(false);
                LOGGER.info(
                        "obtain.graph.ready gen={} edges={} finish_ms=0",
                        gen,
                        graph.edgeCount());
                return;
            }
        }

        long gen = ++generation;
        if (!keepServing) {
            ready = false;
        }
        building = true;
        pending.clear();
        builder = new ObtainGraph.Builder(gen);
        builder.probeItems(PROBE_ITEMS);
        buildStartMs = System.currentTimeMillis();
        buildRecipeCount = recipeCount;
        for (int i = 0; i < all.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, all.size());
            pending.add(List.copyOf(all.subList(i, end)));
        }
        LOGGER.info("obtain.graph.snapshot gen={} recipes={}", gen, recipeCount);
    }

    /**
     * Called every client tick END. Feeds queued batches within a dynamic per-tick budget.
     * Budget is checked inside each batch slice (per recipe), not only between batches.
     * Does not touch {@link RecipeManager}.
     */
    public void tick() {
        if (!building || builder == null) {
            return;
        }
        logBudgetConfigOnce();
        long tickBudgetNs = tickBudgetNanos();
        long gen = generation;
        long t0 = System.nanoTime();
        while (!pending.isEmpty()) {
            if ((System.nanoTime() - t0) >= tickBudgetNs) {
                return;
            }
            List<Recipe<?>> batch = pending.poll();
            if (batch == null || batch.isEmpty()) {
                continue;
            }
            int start = 0;
            while (start < batch.size()) {
                if ((System.nanoTime() - t0) >= tickBudgetNs) {
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
            long elapsedMs = System.currentTimeMillis() - buildStartMs;
            LOGGER.info(
                    "obtain.graph.summary recipes={} edges={} items={} fluids={} elapsedMs={}",
                    buildRecipeCount,
                    finished.edgeCount(),
                    finished.byOutput().size(),
                    finished.distinctFluids(),
                    elapsedMs);
            LOGGER.info(
                    "obtain.graph.summary.fluid edgesWithFluids={} fluidOnlyEdges={} containerSuspects={}",
                    finished.edgesWithFluids(),
                    finished.fluidOnlyEdges(),
                    finished.containerSuspects());
            if (PackAiConfig.obtainGraphCache()) {
                String fingerprint = ObtainGraphCache.computeFingerprint(FMLPaths.GAMEDIR.get());
                ObtainGraphCache.tryWrite(finished, fingerprint, buildRecipeCount);
            }
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
