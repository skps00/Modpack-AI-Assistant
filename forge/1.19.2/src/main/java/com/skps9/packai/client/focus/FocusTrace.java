package com.skps9.packai.client.focus;

import com.skps9.packai.logic.AskTrace;

import net.minecraft.client.Minecraft;

/**
 * {@code check.focus} emit helpers. Rate-limit state-change lines to ≤1/sec.
 */
public final class FocusTrace {
    private static volatile long lastStateEmitMs;
    private static volatile String lastStateSig = "";

    private FocusTrace() {}

    /**
     * Emit one {@code check.focus} at successful resolve (world Y / ask).
     *
     * @param only {@code auto} | {@code block} | {@code entity}
     * @param way  {@code world} | {@code panel}
     * @param key  {@code focus} | {@code think}
     */
    public static void emitResolved(
            FocusTarget target,
            double rangeUsed,
            String only,
            String way,
            String key
    ) {
        write(target, rangeUsed, only, way, key, true);
    }

    /**
     * Emit on kind/id/state change (GONE / TOO_FAR). At most one line per second.
     */
    public static void emitStateChange(
            FocusTarget target,
            double rangeUsed,
            String only,
            String way,
            String key
    ) {
        if (target == null) {
            return;
        }
        String sig = target.kind() + "|" + target.id() + "|" + target.state();
        long now = System.currentTimeMillis();
        if (sig.equals(lastStateSig) && (now - lastStateEmitMs) < 1000L) {
            return;
        }
        if ((now - lastStateEmitMs) < 1000L && !sig.equals(lastStateSig)) {
            // still throttle: ≤1/sec even across different states
            return;
        }
        lastStateSig = sig;
        lastStateEmitMs = now;
        write(target, rangeUsed, only, way, key, false);
    }

    private static void write(
            FocusTarget target,
            double rangeUsed,
            String only,
            String way,
            String key,
            boolean forceBegin
    ) {
        if (target == null) {
            return;
        }
        FocusTarget.Kind kind = target.kind();
        String winner = kind == FocusTarget.Kind.NONE ? "NONE" : kind.name();
        Runnable emit = () -> AskTrace.event("check.focus", o -> {
            o.addProperty("kind", kind.name());
            o.addProperty("winner", winner);
            o.addProperty("id", target.id());
            o.addProperty("range_used", rangeUsed);
            o.addProperty("distance", target.distance());
            o.addProperty("only", only == null ? "auto" : only);
            o.addProperty("way", way == null ? "world" : way);
            o.addProperty("key", key == null ? "focus" : key);
            o.addProperty("state", target.state().name());
        });
        if (AskTrace.active()) {
            emit.run();
            return;
        }
        if (!forceBegin) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) {
            return;
        }
        AskTrace.begin(mc.gameDirectory.toPath(), "focus", target.id());
        try {
            emit.run();
        } finally {
            AskTrace.close("ok");
        }
    }
}
