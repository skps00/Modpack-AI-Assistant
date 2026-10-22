package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Single daemon thread for full-speed {@link ObtainGraph} builds. Client thread submits a recipe
 * snapshot + frozen {@link BuildContext}; results are delivered on the worker thread via {@link Sink}.
 */
public final class ObtainGraphWorker {
    private static final Logger LOGGER = LogManager.getLogger();

    static final long DEFAULT_TIMEOUT_MS = 180_000L;
    private static final int BATCH_SIZE = 256;

    private static final String PROP_FAIL_ONCE = "packai.workerFailOnce";
    private static final String PROP_HANG_ONCE = "packai.workerHangOnce";
    private static final String PROP_TIMEOUT_MS = "packai.workerTimeoutMs";

    static final Set<ResourceLocation> PROBE = Set.of(
            new ResourceLocation("thermal", "apatite"),
            new ResourceLocation("electrodynamics", "crystaliron"),
            new ResourceLocation("minecraft", "clay_ball"),
            new ResourceLocation("create", "bar_of_chocolate"));

    public interface Sink {
        void onSuccess(long gen, ObtainGraph graph, String sha, long workerMs);

        void onFailure(long gen, Throwable error);

        void onTimeout(long gen);
    }

    private static final Object LOCK = new Object();
    private static volatile Thread daemon;
    private static volatile boolean busy;
    private static volatile long latestGen;
    private static volatile List<Recipe<?>> activeRecipes;
    private static volatile Runnable pendingTask;
    private static volatile long submittedTimeoutMs = DEFAULT_TIMEOUT_MS;

    private static volatile boolean injectFailOnce;
    private static volatile boolean injectHangOnce;

    private ObtainGraphWorker() {}

    /** Timeout (ms) captured on the last successful {@link #submit}; used by runtime watchdog. */
    static long submittedTimeoutMs() {
        return submittedTimeoutMs;
    }

    public static boolean submit(long gen, List<Recipe<?>> recipes, BuildContext ctx, Sink sink) {
        if (recipes == null || ctx == null || sink == null) {
            return false;
        }
        submittedTimeoutMs = readTimeoutMs();
        injectFailOnce = propTruthy(PROP_FAIL_ONCE);
        injectHangOnce = propTruthy(PROP_HANG_ONCE);

        Runnable task = () -> runBuild(gen, recipes, ctx, sink);
        synchronized (LOCK) {
            if (busy) {
                LOGGER.debug("obtain.graph.worker submit rejected busy gen={}", gen);
                return false;
            }
            busy = true;
            latestGen = gen;
            activeRecipes = recipes;
            pendingTask = task;
            ensureDaemon();
            LOCK.notifyAll();
        }
        return true;
    }

    public static void cancel(long gen) {
        latestGen = gen;
        activeRecipes = null;
    }

    public static boolean isBusy() {
        return busy;
    }

    static void resetForTests() {
        cancel(0L);
        synchronized (LOCK) {
            busy = false;
            pendingTask = null;
            injectFailOnce = false;
            injectHangOnce = false;
            submittedTimeoutMs = DEFAULT_TIMEOUT_MS;
        }
    }

    private static void ensureDaemon() {
        Thread t = daemon;
        if (t != null && t.isAlive()) {
            return;
        }
        synchronized (LOCK) {
            t = daemon;
            if (t != null && t.isAlive()) {
                return;
            }
            t = new Thread(ObtainGraphWorker::daemonLoop, "packai-obtain-graph");
            t.setDaemon(true);
            t.start();
            daemon = t;
        }
    }

    private static void daemonLoop() {
        while (true) {
            Runnable task;
            synchronized (LOCK) {
                while (pendingTask == null) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                task = pendingTask;
                pendingTask = null;
            }
            try {
                task.run();
            } finally {
                synchronized (LOCK) {
                    busy = false;
                    activeRecipes = null;
                }
            }
        }
    }

    private static void runBuild(long gen, List<Recipe<?>> recipes, BuildContext ctx, Sink sink) {
        long startMs = System.currentTimeMillis();
        try {
            ObtainGraph.Builder builder = new ObtainGraph.Builder(gen, ctx);
            builder.probeItems(PROBE);

            int total = recipes.size();
            int batchIndex = 0;
            for (int offset = 0; offset < total; offset += BATCH_SIZE) {
                if (gen != latestGen) {
                    LOGGER.debug("obtain.graph.worker abort gen={}", gen);
                    activeRecipes = null;
                    return;
                }
                if (activeRecipes == null) {
                    LOGGER.debug("obtain.graph.worker abort gen={}", gen);
                    return;
                }
                if (batchIndex == 1 && injectHangOnce) {
                    injectHangOnce = false;
                    Thread.sleep(400_000L);
                }
                int end = Math.min(offset + BATCH_SIZE, total);
                List<Recipe<?>> batch = new ArrayList<>(recipes.subList(offset, end));
                builder.feed(batch, builder.generation());
                batchIndex++;
            }

            if (gen != latestGen) {
                LOGGER.debug("obtain.graph.worker abort gen={}", gen);
                activeRecipes = null;
                return;
            }

            if (injectFailOnce) {
                injectFailOnce = false;
                throw new RuntimeException("injected");
            }

            ObtainGraph finished = builder.finish();
            String sha = finished.canonicalContentFingerprint();
            long workerMs = System.currentTimeMillis() - startMs;
            sink.onSuccess(gen, finished, sha, workerMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sink.onFailure(gen, e);
        } catch (Throwable t) {
            sink.onFailure(gen, t);
        }
    }

    private static long readTimeoutMs() {
        String raw = System.getProperty(PROP_TIMEOUT_MS);
        if (raw == null || raw.isEmpty()) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    private static boolean propTruthy(String key) {
        String v = System.getProperty(key);
        return v != null && !v.isEmpty() && !"false".equalsIgnoreCase(v) && !"0".equals(v);
    }
}
