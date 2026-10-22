package com.skps9.packai.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.skps9.packai.client.routes.RouteStore;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TagsUpdatedEvent;
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
    private static final long TAGS_GATE_BYPASS_MS = 60_000L;

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

    private volatile boolean tagsApplied;
    private volatile boolean tagsBypass;
    private volatile boolean tagsGateLogged;
    private volatile long firstRecipesUpdatedMs;
    private RecipeManager deferredRecipeManager;

    /** Idempotent gate: one client-thread rebuild request until build starts/finishes/clears. */
    private final AtomicBoolean clientRebuildQueued = new AtomicBoolean(false);

    private final ArrayDeque<List<Recipe<?>>> pending = new ArrayDeque<>();
    private ObtainGraph.Builder builder;
    private long buildStartMs;
    private int buildRecipeCount;

    private volatile boolean workerActive;
    private long workerStartMs;
    private boolean workerFallbackForced;
    private boolean workerRetryPending;
    private long workerRetryUntilMs;

    private static final ObtainGraphWorker.Sink SINK = new ObtainGraphWorker.Sink() {
        @Override
        public void onSuccess(long gen, ObtainGraph g, String sha, long ms) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> INSTANCE.applyResult(gen, g, sha, "worker", 0L, ms));
            }
        }

        @Override
        public void onFailure(long gen, Throwable t) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> INSTANCE.workerFailed(gen, t));
            }
        }

        @Override
        public void onTimeout(long gen) {
            // Runtime tick watchdog handles timeout.
        }
    };

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

    public record BenchResult(int runs, List<String> shas, List<Integer> edges, boolean allEqual) {}

    /**
     * Synchronous full-speed graph builds for determinism benching. Does not touch live graph, cache, or tick budget.
     */
    public static BenchResult benchBuild(RecipeManager rm, int runs) {
        int n = Math.max(1, runs);
        if (rm == null) {
            return new BenchResult(0, List.of(), List.of(), true);
        }
        List<Recipe<?>> all = List.copyOf(rm.getRecipes());
        int recipeCount = all.size();
        List<String> shas = new ArrayList<>();
        List<Integer> edgeCounts = new ArrayList<>();
        boolean tagsReady = ObtainGraphCache.tagsLookReady();
        int sentinel = ObtainGraphCache.sentinelIronIngotTagSize();
        BuildContext benchCtx = BuildContext.clientDefaults();
        for (int run = 1; run <= n; run++) {
            long t0 = System.currentTimeMillis();
            ObtainGraph.Builder builder = new ObtainGraph.Builder(run, benchCtx);
            builder.feed(all);
            long bailEmpty = builder.bailEmptyResult();
            long bailNoInputs = builder.bailNoInputs();
            ObtainGraph finished = builder.finish();
            long elapsedMs = System.currentTimeMillis() - t0;
            String sha = finished.canonicalContentFingerprint();
            int edges = finished.edgeCount();
            int items = finished.byOutput().size();
            int fluids = finished.distinctFluids();
            shas.add(sha);
            edgeCounts.add(edges);
            LOGGER.info(
                    "obtain.bench run={} recipes={} edges={} items={} fluids={} elapsedMs={} tags={} sentinel={} sha={}",
                    run,
                    recipeCount,
                    edges,
                    items,
                    fluids,
                    elapsedMs,
                    tagsReady,
                    sentinel,
                    sha);
            LOGGER.info(
                    "obtain.bench bail emptyResult={} noInputs={}",
                    bailEmpty,
                    bailNoInputs);
        }
        boolean allEqual = true;
        if (!shas.isEmpty()) {
            String firstSha = shas.get(0);
            for (String sha : shas) {
                if (!firstSha.equals(sha)) {
                    allEqual = false;
                    break;
                }
            }
        }
        if (allEqual && !edgeCounts.isEmpty()) {
            int firstEdges = edgeCounts.get(0);
            for (int edges : edgeCounts) {
                if (edges != firstEdges) {
                    allEqual = false;
                    break;
                }
            }
        }
        return new BenchResult(n, List.copyOf(shas), List.copyOf(edgeCounts), allEqual);
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
    /**
     * Client thread: tags have been rebound (see {@link TagsUpdatedEvent}).
     */
    public void onTagsUpdated(TagsUpdatedEvent event) {
        if (!PackAiConfig.obtainPaths()) {
            return;
        }
        boolean hadReadyOrBuilding = ready || building;
        markTagsApplied();
        logTagsGateOnce(true, "tags_event");
        if (hadReadyOrBuilding) {
            building = false;
            builder = null;
            pending.clear();
            ready = false;
            clientRebuildQueued.set(false);
        }
        RecipeManager rm = deferredRecipeManager;
        if (rm == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                rm = mc.level.getRecipeManager();
            }
        }
        if (rm != null) {
            onRecipesUpdated(rm);
        }
    }

    void markTagsApplied() {
        tagsApplied = true;
    }

    boolean tagsAppliedForCache() {
        return tagsApplied && !tagsBypass;
    }

    private boolean mayStartBuild() {
        return tagsApplied || tagsBypass;
    }

    private void maybeBypassTagsGate(long nowMs) {
        if (tagsApplied || tagsBypass) {
            return;
        }
        if (firstRecipesUpdatedMs <= 0L) {
            return;
        }
        if (nowMs - firstRecipesUpdatedMs < TAGS_GATE_BYPASS_MS) {
            return;
        }
        tagsBypass = true;
        logTagsGateOnce(false, "timeout_60s");
        LOGGER.info("obtain.graph.tags_gate=bypass");
    }

    private void logTagsGateOnce(boolean applied, String reason) {
        if (tagsGateLogged) {
            return;
        }
        tagsGateLogged = true;
        LOGGER.info("obtain.graph.tags_gate applied={} reason={}", applied ? "true" : "bypass", reason);
    }

    public void onRecipesUpdated(RecipeManager rm) {
        if (!PackAiConfig.obtainPaths()) {
            clear();
            return;
        }
        if (rm == null) {
            clear();
            return;
        }
        deferredRecipeManager = rm;
        if (firstRecipesUpdatedMs == 0L) {
            firstRecipesUpdatedMs = System.currentTimeMillis();
        }
        maybeBypassTagsGate(System.currentTimeMillis());
        if (!mayStartBuild()) {
            building = false;
            clientRebuildQueued.set(false);
            return;
        }
        List<Recipe<?>> all = List.copyOf(rm.getRecipes());
        int recipeCount = all.size();
        boolean keepServing = graph != null && ready;

        if (PackAiConfig.obtainGraphCache()) {
            String fingerprint = ObtainGraphCache.computeFingerprint(FMLPaths.GAMEDIR.get(), recipeCount);
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
                        "obtain.graph.ready gen={} edges={} finish_ms=0 path=cache",
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
        buildStartMs = System.currentTimeMillis();
        buildRecipeCount = recipeCount;
        builder = null;
        pending.clear();
        boolean useWorker = PackAiConfig.obtainGraphWorker() && !workerFallbackForced;
        if (useWorker) {
            ObtainGraphWorker.cancel(gen);
            workerActive = true;
            workerStartMs = System.currentTimeMillis();
            boolean ok = ObtainGraphWorker.submit(gen, all, BuildContext.clientDefaults(), SINK);
            if (ok) {
                workerRetryPending = false;
                LOGGER.info("obtain.graph.worker start gen={} recipes={}", gen, recipeCount);
                return;
            }
            workerActive = false;
            workerRetryPending = true;
            workerRetryUntilMs = System.currentTimeMillis() + 5000;
            LOGGER.info("obtain.graph.worker retry gen={}", gen);
            return;
        }
        builder = new ObtainGraph.Builder(gen, BuildContext.clientDefaults());
        builder.probeItems(PROBE_ITEMS);
        for (int i = 0; i < all.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, all.size());
            pending.add(List.copyOf(all.subList(i, end)));
        }
        LOGGER.info("obtain.graph.snapshot gen={} recipes={}", gen, recipeCount);
    }

    private void applyResult(long gen, ObtainGraph finished, String sha, String path, long finishMs, long workerMs) {
        if (gen != generation) {
            workerActive = false;
            return;
        }
        if (finished == null || finished.edgeCount() == 0) {
            LOGGER.info("obtain.graph.publish_rejected edges=0 path={}", path);
            workerActive = false;
            building = false;
            return;
        }
        graph = finished;
        ready = true;
        building = false;
        workerActive = false;
        pending.clear();
        builder = null;
        clientRebuildQueued.set(false);
        LOGGER.info(
                "obtain.graph.ready gen={} edges={} finish_ms={} path={} worker_ms={} sha={}",
                gen,
                finished.edgeCount(),
                finishMs,
                path,
                workerMs,
                sha);
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
        if (PackAiConfig.obtainGraphCache() && tagsAppliedForCache()) {
            String fingerprint = ObtainGraphCache.computeFingerprint(FMLPaths.GAMEDIR.get(), buildRecipeCount);
            long cacheT0 = System.currentTimeMillis();
            long cacheBytes = ObtainGraphCache.tryWrite(finished, fingerprint, buildRecipeCount);
            long cacheMs = System.currentTimeMillis() - cacheT0;
            if (cacheBytes > 0L) {
                LOGGER.info("obtain.cache_write=ok bytes={} ms={}", cacheBytes, cacheMs);
            } else {
                LOGGER.info("obtain.cache_write=skipped");
            }
        }
    }

    private void workerFailed(long gen, Throwable t) {
        if (gen != generation) {
            workerActive = false;
            return;
        }
        LOGGER.info("obtain.graph.worker failed", t);
        workerActive = false;
        if (!workerFallbackForced) {
            workerFallbackForced = true;
            workerRetryPending = false;
            onRecipesUpdated(deferredRecipeManager);
        } else {
            building = false;
            LOGGER.error("obtain.graph.worker failed twice; obtain graph unavailable");
        }
    }

    /**
     * Called every client tick END. Feeds queued batches within a dynamic per-tick budget.
     * Budget is checked inside each batch slice (per recipe), not only between batches.
     * Does not touch {@link RecipeManager}.
     */
    public void tick() {
        if (!building && deferredRecipeManager != null && !mayStartBuild()) {
            maybeBypassTagsGate(System.currentTimeMillis());
            if (tagsBypass && deferredRecipeManager != null) {
                onRecipesUpdated(deferredRecipeManager);
            }
        }
        if (workerRetryPending) {
            if (System.currentTimeMillis() > workerRetryUntilMs) {
                workerRetryPending = false;
                workerFallbackForced = true;
                LOGGER.info("obtain.graph.worker retry_expired gen={}", generation);
            }
            if (deferredRecipeManager != null) {
                onRecipesUpdated(deferredRecipeManager);
            }
            return;
        }
        if (workerActive) {
            long timeout = ObtainGraphWorker.submittedTimeoutMs();
            if (System.currentTimeMillis() - workerStartMs > timeout) {
                long gen = generation;
                ObtainGraphWorker.cancel(gen);
                workerActive = false;
                LOGGER.info("obtain.graph.worker timeout gen={} ms={}", gen, timeout);
                workerFailed(gen, new IllegalStateException("worker timeout"));
            }
            return;
        }
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
            applyResult(gen, finished, finished.canonicalContentFingerprint(), "tick", finishMs, 0L);
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
        ObtainGraphWorker.cancel(gen);
        workerActive = false;
        workerRetryPending = false;
        graph = null;
        ready = false;
        building = false;
        builder = null;
        pending.clear();
        clientRebuildQueued.set(false);
        tagsApplied = false;
        tagsBypass = false;
        tagsGateLogged = false;
        firstRecipesUpdatedMs = 0L;
        deferredRecipeManager = null;
        LOGGER.info("obtain.graph.clear gen={}", gen);
    }
}
