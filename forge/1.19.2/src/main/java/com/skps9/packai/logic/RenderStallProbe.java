package com.skps9.packai.logic;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Client-thread render stall diagnostics only; does not affect game logic. */
public final class RenderStallProbe {
    private static final ThreadLocal<Map<String, Double>> PHASE_MS = ThreadLocal.withInitial(LinkedHashMap::new);
    private static final ThreadLocal<Map<String, Long>> PHASE_START = ThreadLocal.withInitial(LinkedHashMap::new);
    private static final ThreadLocal<Long> GC_START_COUNT = new ThreadLocal<>();
    private static final ThreadLocal<Long> GC_START_MS = new ThreadLocal<>();
    private static final ThreadLocal<String> STALL_PHASES = new ThreadLocal<>();
    private static final ThreadLocal<String> STALL_GC = new ThreadLocal<>();

    private static volatile Supplier<List<GarbageCollectorMXBean>> gcBeansSupplier =
            ManagementFactory::getGarbageCollectorMXBeans;

    private static final int MAX_STALL_LOGS = 400;
    private static int stallLogCount;

    private RenderStallProbe() {}

    /** Stall log lines emitted this client session (capped at {@link #MAX_STALL_LOGS}). */
    public static int logged() {
        return stallLogCount;
    }

    /** Records one stall log slot; returns true if the caller should emit the log line. */
    public static boolean recordStallLog() {
        if (stallLogCount >= MAX_STALL_LOGS) {
            return false;
        }
        stallLogCount++;
        return true;
    }

    /** Test hook: pass {@code () -> null} to simulate missing GC beans. */
    static void setGcBeansSupplierForTest(Supplier<List<GarbageCollectorMXBean>> supplier) {
        gcBeansSupplier = supplier != null ? supplier : ManagementFactory::getGarbageCollectorMXBeans;
    }

    static void clearGcBeansSupplierForTest() {
        gcBeansSupplier = ManagementFactory::getGarbageCollectorMXBeans;
    }

    public static void reset() {
        STALL_PHASES.set(phases());
        STALL_GC.set(gc());
        PHASE_MS.get().clear();
        PHASE_START.get().clear();
    }

    /** Phases accumulated since the previous {@link #reset()} (for inter-tick stall logging). */
    public static String phasesAtStall() {
        String snap = STALL_PHASES.get();
        return snap != null ? snap : "";
    }

    /** GC delta since the previous {@link #beginGc()} at the time of the last {@link #reset()}. */
    public static String gcAtStall() {
        String snap = STALL_GC.get();
        return snap != null ? snap : "count=-1,ms=-1";
    }

    public static void beginGc() {
        GcSnapshot snap = readGc();
        if (snap == null) {
            GC_START_COUNT.set(-1L);
            GC_START_MS.set(-1L);
        } else {
            GC_START_COUNT.set(snap.count);
            GC_START_MS.set(snap.ms);
        }
    }

    public static long t0(String phase) {
        PHASE_START.get().put(phase, System.nanoTime());
        return 0L;
    }

    public static long end(String phase) {
        Long start = PHASE_START.get().remove(phase);
        if (start == null) {
            return 0L;
        }
        double addMs = (System.nanoTime() - start) / 1_000_000.0;
        Map<String, Double> acc = PHASE_MS.get();
        acc.put(phase, acc.getOrDefault(phase, 0.0) + addMs);
        return 0L;
    }

    public static String phases() {
        Map<String, Double> acc = PHASE_MS.get();
        if (acc.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : acc.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey())
                    .append('=')
                    .append(String.format(Locale.ROOT, "%.1f", e.getValue()));
        }
        return sb.toString();
    }

    public static String gc() {
        Long startCount = GC_START_COUNT.get();
        Long startMs = GC_START_MS.get();
        if (startCount == null || startMs == null) {
            return "count=-1,ms=-1";
        }
        if (startCount < 0L || startMs < 0L) {
            return "count=-1,ms=-1";
        }
        try {
            GcSnapshot now = readGc();
            if (now == null) {
                return "count=-1,ms=-1";
            }
            long deltaCount = now.count - startCount;
            long deltaMs = now.ms - startMs;
            if (deltaCount < 0L || deltaMs < 0L) {
                return "count=-1,ms=-1";
            }
            return "count=" + deltaCount + ",ms=" + deltaMs;
        } catch (Throwable t) {
            return "count=-1,ms=-1";
        }
    }

    private static GcSnapshot readGc() {
        try {
            List<GarbageCollectorMXBean> beans = gcBeansSupplier.get();
            if (beans == null) {
                return null;
            }
            long count = 0L;
            long ms = 0L;
            for (GarbageCollectorMXBean bean : beans) {
                if (bean == null) {
                    continue;
                }
                long c = bean.getCollectionCount();
                if (c >= 0L) {
                    count += c;
                }
                long m = bean.getCollectionTime();
                if (m >= 0L) {
                    ms += m;
                }
            }
            return new GcSnapshot(count, ms);
        } catch (Throwable t) {
            return null;
        }
    }

    private record GcSnapshot(long count, long ms) {}
}
