package com.skps9.packai.logic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * WP4 — runtime player advancement checklist for literal {@link RecipeUnlockGates.Kind#ADVANCEMENT}
 * gates only. UNKNOWN / non-literal labels get no fake checkbox. Soft-reads client (or
 * integrated-server) progress; never throws — unreadable on miss / dedicated limits.
 *
 * <p>No GameStages player API (YAGNI). No pack id hardcodes.
 */
public final class PlayerUnlockStatus {
    /** {@code ns:path} — same shape as KubeJS advancement literals. */
    private static final Pattern LITERAL_ID = Pattern.compile(
            "^[a-z0-9_]+:[a-z0-9_./-]+$");

    /**
     * Test hook: when non-null, {@link #progressFor} uses this instead of live client.
     * Clear after -ea checks.
     */
    static volatile Function<String, Progress> progressOverride;

    private PlayerUnlockStatus() {}

    public enum Progress {
        DONE,
        NOT_DONE,
        UNREADABLE
    }

    /** True when label is a registry-shaped advancement id (not a display title). */
    public static boolean isLiteralAdvancementId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String s = id.trim().toLowerCase(Locale.ROOT);
        if (RecipeUnlockGates.UNKNOWN_ADV_SENTINEL.equalsIgnoreCase(s)) {
            return false;
        }
        return LITERAL_ID.matcher(s).matches();
    }

    /**
     * Player completion for a literal advancement id. Non-literal → {@link Progress#UNREADABLE}
     * (caller should omit checklist, not invent).
     */
    public static Progress progressFor(String advancementId) {
        if (!isLiteralAdvancementId(advancementId)) {
            return Progress.UNREADABLE;
        }
        String key = advancementId.trim().toLowerCase(Locale.ROOT);
        Function<String, Progress> ov = progressOverride;
        if (ov != null) {
            try {
                Progress p = ov.apply(key);
                return p == null ? Progress.UNREADABLE : p;
            } catch (Throwable ignored) {
                return Progress.UNREADABLE;
            }
        }
        return readClientProgress(key);
    }

    /** Localized suffix including leading space, e.g. {@code " [done]"}. */
    public static String statusSuffix(Progress progress, String lang) {
        Progress p = progress == null ? Progress.UNREADABLE : progress;
        String code = lang == null || lang.isBlank() ? ReplyLang.current() : lang.trim();
        return switch (p) {
            case DONE -> ReplyLang.unlockDone(code);
            case NOT_DONE -> ReplyLang.unlockNotDone(code);
            case UNREADABLE -> ReplyLang.unlockUnreadable(code);
        };
    }

    /**
     * Append checklist only for {@link RecipeUnlockGates.Kind#ADVANCEMENT} + literal id.
     * UNKNOWN / STAGE / title-only → {@code displayBase} unchanged (no fake checkbox).
     */
    public static String withProgress(
            RecipeUnlockGates.Kind kind,
            String rawLabel,
            String displayBase,
            String lang
    ) {
        String base = displayBase == null ? "" : displayBase;
        if (kind != RecipeUnlockGates.Kind.ADVANCEMENT) {
            return base;
        }
        if (!isLiteralAdvancementId(rawLabel)) {
            return base;
        }
        return base + statusSuffix(progressFor(rawLabel), lang);
    }

    private static Progress readClientProgress(String advancementId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return Progress.UNREADABLE;
            }
            ResourceLocation rl = parseId(advancementId);
            if (rl == null) {
                return Progress.UNREADABLE;
            }

            Progress fromSp = readIntegratedServerProgress(mc, rl);
            if (fromSp != null) {
                return fromSp;
            }

            if (mc.getConnection() == null) {
                return Progress.UNREADABLE;
            }
            Object clientAdvs = mc.getConnection().getAdvancements();
            if (clientAdvs == null) {
                return Progress.UNREADABLE;
            }
            Object adv = findAdvancement(clientAdvs, rl);
            if (adv == null) {
                return Progress.UNREADABLE;
            }
            Object progress = progressOf(clientAdvs, adv, rl);
            return doneFlag(progress);
        } catch (Throwable ignored) {
            return Progress.UNREADABLE;
        }
    }

    /** {@code null} = could not read via integrated server (try client next). */
    private static Progress readIntegratedServerProgress(Minecraft mc, ResourceLocation rl) {
        try {
            var server = mc.getSingleplayerServer();
            if (server == null || mc.player == null) {
                return null;
            }
            Object playerList = invokeNoArg(server, "getPlayerList");
            if (playerList == null) {
                return null;
            }
            Method getPlayer = null;
            for (String name : new String[] {"getPlayer", "getPlayerByUUID"}) {
                try {
                    getPlayer = playerList.getClass().getMethod(name, java.util.UUID.class);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
            if (getPlayer == null) {
                return null;
            }
            Object sp = getPlayer.invoke(playerList, mc.player.getUUID());
            if (sp == null) {
                return null;
            }
            Object playerAdvs = invokeNoArg(sp, "getAdvancements");
            if (playerAdvs == null) {
                return null;
            }
            Object advMgr = invokeNoArg(server, "getAdvancements");
            Object adv = findAdvancement(advMgr != null ? advMgr : playerAdvs, rl);
            if (adv == null) {
                return Progress.UNREADABLE;
            }
            Object progress = progressOf(playerAdvs, adv, rl);
            return doneFlag(progress);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findAdvancement(Object holder, ResourceLocation rl) {
        if (holder == null || rl == null) {
            return null;
        }
        Object direct = invokeOneArg(holder, rl, "get", "getAdvancement");
        if (direct != null) {
            return unwrapHolder(direct);
        }
        // Scan collections / maps used by ClientAdvancements
        Object all = invokeNoArg(holder, "getAllAdvancements");
        Object match = matchInCollection(all, rl);
        if (match != null) {
            return match;
        }
        Object list = readField(holder, "advancements");
        if (list != null && list != holder) {
            match = findAdvancement(list, rl);
            if (match != null) {
                return match;
            }
        }
        Object map = readField(holder, "advancements", "progress", "roots");
        if (map instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (idEquals(e.getKey(), rl)) {
                    return unwrapHolder(e.getKey() instanceof ResourceLocation ? e.getValue() : e.getKey());
                }
                if (idEquals(e.getValue(), rl)) {
                    return unwrapHolder(e.getValue());
                }
            }
        }
        return null;
    }

    private static Object matchInCollection(Object all, ResourceLocation rl) {
        if (!(all instanceof Collection<?> c)) {
            return null;
        }
        for (Object raw : c) {
            if (raw == null) {
                continue;
            }
            if (idEquals(raw, rl)) {
                return unwrapHolder(raw);
            }
            Object id = invokeNoArg(raw, "id", "getId");
            if (idEquals(id, rl)) {
                return unwrapHolder(raw);
            }
            Object value = invokeNoArg(raw, "value");
            if (value != null && idEquals(invokeNoArg(raw, "id"), rl)) {
                return value;
            }
        }
        return null;
    }

    private static Object unwrapHolder(Object raw) {
        if (raw == null) {
            return null;
        }
        Object value = invokeNoArg(raw, "value");
        return value != null ? value : raw;
    }

    private static Object progressOf(Object advs, Object adv, ResourceLocation rl) {
        Object p = invokeOneArg(advs, adv, "getOrStartProgress", "get");
        if (p != null) {
            return p;
        }
        Object map = readField(advs, "progress");
        if (map instanceof Map<?, ?> m) {
            Object byAdv = m.get(adv);
            if (byAdv != null) {
                return byAdv;
            }
            Object byId = m.get(rl);
            if (byId != null) {
                return byId;
            }
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (idEquals(e.getKey(), rl) || e.getKey() == adv) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static Progress doneFlag(Object progress) {
        if (progress == null) {
            return Progress.UNREADABLE;
        }
        Object done = invokeNoArg(progress, "isDone");
        if (done instanceof Boolean b) {
            return b ? Progress.DONE : Progress.NOT_DONE;
        }
        return Progress.UNREADABLE;
    }

    private static boolean idEquals(Object obj, ResourceLocation rl) {
        if (obj == null || rl == null) {
            return false;
        }
        if (obj instanceof ResourceLocation other) {
            return other.equals(rl);
        }
        String s = obj.toString().trim().toLowerCase(Locale.ROOT);
        return s.equals(rl.toString().toLowerCase(Locale.ROOT));
    }

    private static ResourceLocation parseId(String id) {
        try {
            return ResourceLocation.tryParse(id);
        } catch (Throwable ignored) {
            try {
                Method m = ResourceLocation.class.getMethod("tryParse", String.class);
                Object v = m.invoke(null, id);
                return v instanceof ResourceLocation rl ? rl : null;
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null || names == null) {
            return null;
        }
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (Throwable ignored) {
                // try next
            }
        }
        return null;
    }

    private static Object invokeOneArg(Object target, Object arg, String... names) {
        if (target == null || names == null) {
            return null;
        }
        for (String name : names) {
            try {
                for (Method m : target.getClass().getMethods()) {
                    if (!m.getName().equals(name) || m.getParameterCount() != 1) {
                        continue;
                    }
                    Class<?> p0 = m.getParameterTypes()[0];
                    if (arg != null && !p0.isInstance(arg) && !p0.isAssignableFrom(arg.getClass())) {
                        // still try — Advancement vs AdvancementHolder
                    }
                    m.setAccessible(true);
                    try {
                        return m.invoke(target, arg);
                    } catch (Throwable ignored) {
                        // try next overload
                    }
                }
            } catch (Throwable ignored) {
                // try next name
            }
        }
        return null;
    }

    private static Object readField(Object target, String... names) {
        if (target == null || names == null) {
            return null;
        }
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (Throwable ignored) {
                    // try next
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
