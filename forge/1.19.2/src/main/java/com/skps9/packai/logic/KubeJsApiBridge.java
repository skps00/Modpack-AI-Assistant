package com.skps9.packai.logic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Soft-dep KubeJS runtime map: item id → (event, source, line).
 * Prefer public {@code findUniqueExtraIds}/{@code forEachListener}; private
 * {@code extraEventContainers}/{@code eventContainers} as fallback. No kubejs import.
 */
public final class KubeJsApiBridge {
    static final String GROUP_CLASS = "dev.latvian.mods.kubejs.event.EventGroup";
    static final String HANDLER_CLASS = "dev.latvian.mods.kubejs.event.EventHandler";
    static final String CONTAINER_CLASS = "dev.latvian.mods.kubejs.event.EventHandlerContainer";
    static final String SCRIPT_TYPE_CLASS = "dev.latvian.mods.kubejs.script.ScriptType";
    static final String SCRIPTS_LOADED = "dev.latvian.mods.kubejs.script.ScriptsLoadedEvent";
    static final String[] SCRIPT_KINDS = {"startup", "server", "client"};
    private static final long MTIME_INTERVAL_MS = 60_000L;
    private static final int CHILD_CAP = 64;
    private static final java.util.regex.Pattern ID_IN_TEXT =
            java.util.regex.Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)");
    private static final String[] ID_GETTERS = {
            "kjs$getId", "getId", "getRegistryName", "location", "id", "getItemId"
    };

    public static final class Hit {
        public final String event;
        public final String source;
        public final int line;
        public final String kind;

        public Hit(String event, String source, int line, String kind) {
            this.event = event == null ? "" : event;
            this.source = source == null ? "" : source;
            this.line = line;
            this.kind = kind == null || kind.isBlank() ? "extra" : kind;
        }

        /** Same item + source + line → one hit (event ignored). */
        String dedupeKey(String itemId) {
            String id = itemId == null || itemId.isBlank() ? "_" : itemId;
            return id + "|" + source + "|" + line;
        }
    }

    private static volatile Boolean AVAILABLE;
    private static volatile Map<String, List<Hit>> BY_ITEM = Map.of();
    private static volatile List<Hit> UNBOUND = List.of();
    private static volatile String LAST_LOG = "";
    private static volatile String EXTRA_FIELD = "extraEventContainers";
    private static volatile String CONTAINERS_FIELD = "eventContainers";
    private static volatile Path LAST_DIR;
    private static volatile long LAST_MTIME_CHECK_MS;
    private static volatile long LAST_SEEN_MTIME;
    private static volatile boolean HOOKED;
    private static volatile int LAST_HITS;
    private static volatile String LAST_MODE = "";
    private static volatile int LAST_GROUPS;
    private static volatile int LAST_HANDLERS;
    private static volatile int LAST_EXTRA_IDS;
    private static volatile int LAST_ENTRIES;
    private static volatile String LAST_COLLECT_MODE = "";

    private static final AtomicBoolean UNAVAIL_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean HOOK_TRIED = new AtomicBoolean();
    private static final AtomicBoolean SNAPSHOT_RUNNING = new AtomicBoolean();
    private static final AtomicBoolean PROBE_ASK_LOGGED = new AtomicBoolean();
    /** One diag line per snapshot cycle; miss-only (not public-hit). */
    private static final AtomicBoolean DIAG_ASK_LOGGED = new AtomicBoolean();
    private static final Object DIAG_LOCK = new Object();
    private static final ArrayList<String> DIAG_EXTRA_SAMPLES = new ArrayList<>(5);
    private static final ArrayList<String> DIAG_LOOKUP_SAMPLES = new ArrayList<>(5);
    private static volatile String DIAG_BY_ITEM_KEYS = "";
    private static final int DIAG_LINE_MAX = 400;
    private static final int DIAG_SAMPLE_CAP = 5;
    private static volatile boolean SNAPSHOT_READY;

    private KubeJsApiBridge() {}

    public static boolean available() {
        Boolean cached = AVAILABLE;
        if (cached != null) {
            return cached;
        }
        synchronized (KubeJsApiBridge.class) {
            if (AVAILABLE != null) {
                return AVAILABLE;
            }
            AVAILABLE = probe();
            return AVAILABLE;
        }
    }

    /** In-memory item → hits. Never walks disk. Empty if not snapshotted / unavailable. */
    public static Map<String, List<Hit>> snapshot() {
        Map<String, List<Hit>> snap = BY_ITEM;
        return snap == null ? Map.of() : snap;
    }

    public static List<Hit> hitsFor(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return List.of();
        }
        String key = itemId.toLowerCase(Locale.ROOT).trim();
        sampleLookupKey(key, "hitsFor");
        List<Hit> hits = BY_ITEM.get(key);
        return hits == null ? List.of() : hits;
    }

    static List<Hit> unboundHits() {
        return UNBOUND;
    }

    static String lastLog() {
        return LAST_LOG == null ? "" : LAST_LOG;
    }

    static int lastHits() {
        return LAST_HITS;
    }

    static String lastMode() {
        return LAST_MODE == null ? "" : LAST_MODE;
    }

    /** Ask-path log: probe once, then {@code Pack AI kubejs bridge hits=<n> mode=api|scan}. */
    public static void noteAsk(int hits, String mode) {
        LAST_HITS = hits;
        LAST_MODE = mode == null ? "" : mode;
        if (PROBE_ASK_LOGGED.compareAndSet(false, true)) {
            logInfo("Pack AI kubejs bridge probe groups=" + LAST_GROUPS
                    + " handlers=" + LAST_HANDLERS
                    + " extraIds=" + LAST_EXTRA_IDS
                    + " entries=" + LAST_ENTRIES
                    + " matched=" + hits
                    + (LAST_COLLECT_MODE.isBlank() ? "" : " via=" + LAST_COLLECT_MODE));
        }
        maybeLogMissDiag(hits, LAST_MODE);
        logInfo("Pack AI kubejs bridge hits=" + hits + " mode=" + LAST_MODE);
    }

    static boolean hooked() {
        return HOOKED;
    }

    static String reloadMode() {
        if (!enabled()) {
            return "off";
        }
        return HOOKED ? "event" : "mtime";
    }

    public static boolean enabled() {
        try {
            Class<?> c = Class.forName("com.skps9.packai.config.PackAiConfig");
            Object v = c.getMethod("kubejsApiBridge").invoke(null);
            return !Boolean.FALSE.equals(v);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void ensureStart(Path gameDir) {
        if (gameDir != null) {
            LAST_DIR = gameDir;
        }
        if (!enabled()) {
            return;
        }
        tryHookOnce();
        scheduleSnapshot(false);
    }

    /** Render-thread safe: only flags. IO / snapshot on a daemon. */
    public static void onAskScreenOpen(Path gameDir) {
        if (gameDir != null) {
            LAST_DIR = gameDir;
        }
        if (!enabled() || HOOKED) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - LAST_MTIME_CHECK_MS < MTIME_INTERVAL_MS) {
            return;
        }
        LAST_MTIME_CHECK_MS = now;
        Path dir = LAST_DIR;
        Thread t = new Thread(() -> mtimeRebuild(dir), "packai-kubejs-mtime");
        t.setDaemon(true);
        t.start();
    }

    static void resetForTest() {
        AVAILABLE = null;
        BY_ITEM = Map.of();
        UNBOUND = List.of();
        LAST_LOG = "";
        EXTRA_FIELD = "extraEventContainers";
        CONTAINERS_FIELD = "eventContainers";
        LAST_DIR = null;
        LAST_MTIME_CHECK_MS = 0L;
        LAST_SEEN_MTIME = 0L;
        HOOKED = false;
        SNAPSHOT_READY = false;
        LAST_HITS = 0;
        LAST_MODE = "";
        LAST_GROUPS = 0;
        LAST_HANDLERS = 0;
        LAST_EXTRA_IDS = 0;
        LAST_ENTRIES = 0;
        LAST_COLLECT_MODE = "";
        UNAVAIL_LOGGED.set(false);
        HOOK_TRIED.set(false);
        SNAPSHOT_RUNNING.set(false);
        PROBE_ASK_LOGGED.set(false);
        DIAG_ASK_LOGGED.set(false);
        DIAG_BY_ITEM_KEYS = "";
        synchronized (DIAG_LOCK) {
            DIAG_EXTRA_SAMPLES.clear();
            DIAG_LOOKUP_SAMPLES.clear();
        }
    }

    static void setProbeFieldsForTest(String extra, String containers) {
        EXTRA_FIELD = extra == null ? "extraEventContainers" : extra;
        CONTAINERS_FIELD = containers == null ? "eventContainers" : containers;
        AVAILABLE = null;
        BY_ITEM = Map.of();
        SNAPSHOT_READY = false;
    }

    static void installHitsForTest(String itemId, List<Hit> hits) {
        LinkedHashMap<String, List<Hit>> m = new LinkedHashMap<>(BY_ITEM);
        String id = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT).trim();
        m.put(id, List.copyOf(hits == null ? List.of() : hits));
        BY_ITEM = freeze(m);
        SNAPSHOT_READY = true;
        AVAILABLE = true;
    }

    static Map<String, List<Hit>> hitsFromHandler(
            String groupName, Object handler, String extraField, String contField
    ) {
        LinkedHashMap<String, List<Hit>> byItem = new LinkedHashMap<>();
        List<Hit> unbound = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        try {
            ingestHandler(groupName, handler, byItem, unbound, seen, extraField, contField);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logUnavail(e.getClass().getSimpleName() + ": " + e.getMessage());
            return Map.of();
        } catch (RuntimeException e) {
            logUnavail(e.getClass().getSimpleName() + ": " + e.getMessage());
            return Map.of();
        }
        UNBOUND = List.copyOf(unbound);
        return freeze(byItem);
    }

    static Map<String, List<Hit>> hitsFromExtraMap(String event, Map<?, ?> extraMap) {
        LinkedHashMap<String, List<Hit>> byItem = new LinkedHashMap<>();
        List<Hit> unbound = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (extraMap != null) {
            for (Map.Entry<?, ?> e : extraMap.entrySet()) {
                ingestValue(event, e.getKey(), e.getValue(), "extra", byItem, unbound, seen);
            }
        }
        UNBOUND = List.copyOf(unbound);
        return freeze(byItem);
    }

    static List<Hit> hitsFromEventContainers(String event, Object[] containers) {
        LinkedHashMap<String, List<Hit>> byItem = new LinkedHashMap<>();
        List<Hit> unbound = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (containers != null) {
            for (int i = 0; i < containers.length; i++) {
                ingestValue(event, null, containers[i], kindAt(i), byItem, unbound, seen);
            }
        }
        UNBOUND = List.copyOf(unbound);
        List<Hit> all = new ArrayList<>(unbound);
        for (List<Hit> hits : byItem.values()) {
            all.addAll(hits);
        }
        return List.copyOf(all);
    }

    static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String s = source.replace('\\', '/').trim();
        int kube = s.toLowerCase(Locale.ROOT).lastIndexOf("kubejs/");
        if (kube >= 0) {
            s = s.substring(kube + "kubejs/".length());
        }
        int colon = s.indexOf(':');
        if (colon > 0 && colon < 32 && s.indexOf('/') < 0) {
            String left = s.substring(0, colon);
            if (left.endsWith("_scripts") || "startup_scripts".equals(left)
                    || "server_scripts".equals(left) || "client_scripts".equals(left)) {
                s = left + "/" + s.substring(colon + 1);
            }
        }
        while (s.startsWith("./")) {
            s = s.substring(2);
        }
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s;
    }

    private static boolean probe() {
        try {
            Class.forName(GROUP_CLASS);
        } catch (ClassNotFoundException e) {
            logUnavail("class not found");
            return false;
        } catch (Throwable t) {
            logUnavail(t.getClass().getSimpleName());
            return false;
        }
        try {
            Class<?> group = Class.forName(GROUP_CLASS);
            group.getMethod("getGroups");
            group.getMethod("getHandlers");
            group.getField("name");
            Class<?> handler = Class.forName(HANDLER_CLASS);
            handler.getField("name");
            Class<?> box = Class.forName(CONTAINER_CLASS);
            box.getField("extraId");
            box.getField("source");
            box.getField("line");
            boolean pub = hasPublicListenerApi(handler);
            boolean priv = hasPrivateContainerFields(handler);
            if (!pub && !priv) {
                logUnavail("no public listener API and no private container fields");
                return false;
            }
            return true;
        } catch (NoSuchFieldException e) {
            logUnavail("NoSuchFieldException: " + e.getMessage());
            return false;
        } catch (NoSuchMethodException e) {
            logUnavail("NoSuchMethodException: " + e.getMessage());
            return false;
        } catch (ClassNotFoundException e) {
            logUnavail("class not found");
            return false;
        } catch (Throwable t) {
            logUnavail(t.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean hasPublicListenerApi(Class<?> handler) {
        try {
            Class<?> st = Class.forName(SCRIPT_TYPE_CLASS);
            handler.getMethod("forEachListener", st, java.util.function.Consumer.class);
            handler.getMethod("findUniqueExtraIds", st);
            st.getField("VALUES");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasPrivateContainerFields(Class<?> handler) {
        try {
            Field extra = findDeclared(handler, EXTRA_FIELD);
            Field cont = findDeclared(handler, CONTAINERS_FIELD);
            return tryAccess(extra) && tryAccess(cont);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void tryHookOnce() {
        if (!HOOK_TRIED.compareAndSet(false, true)) {
            return;
        }
        if (!available()) {
            HOOKED = false;
            return;
        }
        HOOKED = registerScriptsLoaded();
        logInfo("Pack AI kubejs bridge reload=" + (HOOKED ? "event" : "mtime"));
    }

    private static boolean registerScriptsLoaded() {
        try {
            Class<?> c = Class.forName(SCRIPTS_LOADED);
            Object event = c.getField("EVENT").get(null);
            if (event == null) {
                return false;
            }
            Runnable cb = KubeJsApiBridge::onScriptsLoaded;
            for (Method m : event.getClass().getMethods()) {
                if (!"register".equals(m.getName()) || m.getParameterCount() != 1) {
                    continue;
                }
                Class<?> p = m.getParameterTypes()[0];
                if (p.isInstance(cb) || p == Runnable.class || p == Object.class) {
                    m.invoke(event, cb);
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void onScriptsLoaded() {
        logInfo("Pack AI kubejs bridge reload=event");
        scheduleSnapshot(true);
    }

    private static void scheduleSnapshot(boolean force) {
        if (!available()) {
            BY_ITEM = Map.of();
            UNBOUND = List.of();
            SNAPSHOT_READY = true;
            return;
        }
        // M1d: refresh bridge map + invalidate mechanic scan index (next ask rebuilds off render thread).
        if (!force) {
            if (SNAPSHOT_READY && !BY_ITEM.isEmpty()) {
                return;
            }
            if (!SNAPSHOT_RUNNING.compareAndSet(false, true)) {
                return;
            }
            Thread t = new Thread(() -> {
                try {
                    rebuildSnapshotNow();
                } finally {
                    SNAPSHOT_RUNNING.set(false);
                }
            }, "packai-kubejs-bridge");
            t.setDaemon(true);
            t.start();
            return;
        }
        KubeJsMechanicScan.invalidateOnReload(LAST_DIR);
        Thread t = new Thread(KubeJsApiBridge::rebuildSnapshotNow, "packai-kubejs-bridge-reload");
        t.setDaemon(true);
        t.start();
    }

    private static void mtimeRebuild(Path dir) {
        if (dir == null) {
            return;
        }
        long mt = maxScriptMtime(dir);
        boolean empty = BY_ITEM.isEmpty();
        if (!empty && mt <= LAST_SEEN_MTIME) {
            return;
        }
        // First fill (empty) refreshes bridge only; invalidate only when an existing map is stale.
        boolean reload = !empty;
        LAST_SEEN_MTIME = mt;
        logInfo("Pack AI kubejs bridge reload=mtime");
        if (reload) {
            KubeJsMechanicScan.invalidateOnReload(dir);
        }
        rebuildSnapshotNow();
    }

    static void rebuildSnapshotNow() {
        if (!available()) {
            BY_ITEM = Map.of();
            UNBOUND = List.of();
            SNAPSHOT_READY = true;
            return;
        }
        try {
            clearDiagExtraSamples();
            Collected c = collectLive();
            BY_ITEM = freeze(c.byItem);
            UNBOUND = List.copyOf(c.unbound);
            LAST_GROUPS = c.groups;
            LAST_HANDLERS = c.handlers;
            LAST_EXTRA_IDS = c.extraIds;
            LAST_ENTRIES = c.entries;
            LAST_COLLECT_MODE = c.mode == null ? "" : c.mode;
            captureByItemDiagKeys(BY_ITEM);
            PROBE_ASK_LOGGED.set(false);
            DIAG_ASK_LOGGED.set(false);
            synchronized (DIAG_LOCK) {
                DIAG_LOOKUP_SAMPLES.clear();
            }
            SNAPSHOT_READY = true;
            if (LAST_DIR != null && LAST_SEEN_MTIME == 0L) {
                LAST_SEEN_MTIME = maxScriptMtime(LAST_DIR);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logUnavail(e.getClass().getSimpleName() + ": " + e.getMessage());
            BY_ITEM = Map.of();
            UNBOUND = List.of();
            SNAPSHOT_READY = true;
            AVAILABLE = false;
        } catch (Throwable t) {
            logUnavail(t.getClass().getSimpleName());
            BY_ITEM = Map.of();
            UNBOUND = List.of();
            SNAPSHOT_READY = true;
        }
    }

    private static Collected collectLive() throws Exception {
        Class<?> groupClz = Class.forName(GROUP_CLASS);
        Method getGroups = groupClz.getMethod("getGroups");
        Method getHandlers = groupClz.getMethod("getHandlers");
        Field groupName = groupClz.getField("name");
        Object raw = getGroups.invoke(null);
        LinkedHashMap<String, List<Hit>> byItem = new LinkedHashMap<>();
        List<Hit> unbound = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int groups = 0;
        int handlers = 0;
        int extraIds = 0;
        int entries = 0;
        if (!(raw instanceof Map<?, ?> groupMap)) {
            return new Collected(byItem, unbound, 0, 0, 0, 0, "empty");
        }
        groups = groupMap.size();
        Class<?> handlerClz = Class.forName(HANDLER_CLASS);
        boolean usePublic = hasPublicListenerApi(handlerClz);
        Method forEach = null;
        Method findIds = null;
        Object[] scriptTypes = null;
        if (usePublic) {
            Class<?> st = Class.forName(SCRIPT_TYPE_CLASS);
            forEach = handlerClz.getMethod("forEachListener", st, java.util.function.Consumer.class);
            findIds = handlerClz.getMethod("findUniqueExtraIds", st);
            Object vals = st.getField("VALUES").get(null);
            if (vals instanceof Object[] arr) {
                scriptTypes = arr;
            }
        }
        String mode = usePublic && scriptTypes != null ? "public" : "reflect";
        for (Object group : groupMap.values()) {
            if (group == null) {
                continue;
            }
            String gName = String.valueOf(groupName.get(group));
            Object hRaw = getHandlers.invoke(group);
            if (!(hRaw instanceof Map<?, ?> handlerMap)) {
                continue;
            }
            for (Object handler : handlerMap.values()) {
                if (handler == null) {
                    continue;
                }
                handlers++;
                if ("public".equals(mode)) {
                    extraIds += ingestHandlerPublic(
                            gName, handler, scriptTypes, forEach, findIds, byItem, unbound, seen);
                } else {
                    ingestHandler(gName, handler, byItem, unbound, seen, EXTRA_FIELD, CONTAINERS_FIELD);
                }
            }
        }
        if ("public".equals(mode) && byItem.isEmpty() && hasPrivateContainerFields(handlerClz)) {
            // Public walk empty — retry private maps (version / timing quirks).
            byItem = new LinkedHashMap<>();
            unbound = new ArrayList<>();
            seen = new LinkedHashSet<>();
            handlers = 0;
            for (Object group : groupMap.values()) {
                if (group == null) {
                    continue;
                }
                String gName = String.valueOf(groupName.get(group));
                Object hRaw = getHandlers.invoke(group);
                if (!(hRaw instanceof Map<?, ?> handlerMap)) {
                    continue;
                }
                for (Object handler : handlerMap.values()) {
                    if (handler == null) {
                        continue;
                    }
                    handlers++;
                    ingestHandler(gName, handler, byItem, unbound, seen, EXTRA_FIELD, CONTAINERS_FIELD);
                }
            }
            mode = byItem.isEmpty() ? "public" : "reflect";
            extraIds = byItem.size();
        } else if (!"public".equals(mode)) {
            extraIds = byItem.size();
        }
        entries = countEntries(byItem);
        return new Collected(byItem, unbound, groups, handlers, extraIds, entries, mode);
    }

    /** @return unique-extra-id count seen on this handler (best-effort) */
    private static int ingestHandlerPublic(
            String groupName,
            Object handler,
            Object[] scriptTypes,
            Method forEach,
            Method findIds,
            Map<String, List<Hit>> byItem,
            List<Hit> unbound,
            Set<String> seen
    ) {
        int extraIds = 0;
        Object nameObj = quietField(handler, "name");
        String event = groupName + "." + (nameObj == null ? "" : nameObj);
        if (scriptTypes == null) {
            return 0;
        }
        for (Object scriptType : scriptTypes) {
            if (scriptType == null) {
                continue;
            }
            String kind = scriptKindName(scriptType);
            try {
                Object idSet = findIds.invoke(handler, scriptType);
                if (idSet instanceof Set<?> set) {
                    extraIds += set.size();
                    for (Object rawId : set) {
                        sampleExtraId(rawId);
                    }
                }
            } catch (Throwable ignored) {
                // count best-effort
            }
            try {
                java.util.function.Consumer<Object> consumer = container -> {
                    if (container == null) {
                        return;
                    }
                    Object xid = quietField(container, "extraId");
                    sampleExtraId(xid);
                    String source = normalizeSource(strField(container, "source"));
                    int line = intField(container, "line");
                    String id = normalizeId(xid);
                    Hit hit = new Hit(event, source, line, kind);
                    if (seen.add(hit.dedupeKey(id))) {
                        if (id.isEmpty()) {
                            unbound.add(hit);
                        } else {
                            byItem.computeIfAbsent(id, k -> new ArrayList<>()).add(hit);
                        }
                    }
                };
                forEach.invoke(handler, scriptType, consumer);
            } catch (Throwable ignored) {
                // soft-fail per handler
            }
        }
        return extraIds;
    }

    private static String scriptKindName(Object scriptType) {
        try {
            Field f = scriptType.getClass().getField("name");
            Object n = f.get(scriptType);
            return n == null ? "extra" : n.toString();
        } catch (Throwable t) {
            return "extra";
        }
    }

    private static int countEntries(Map<String, List<Hit>> byItem) {
        int n = 0;
        for (List<Hit> hits : byItem.values()) {
            n += hits == null ? 0 : hits.size();
        }
        return n;
    }

    private static void ingestHandler(
            String groupName,
            Object handler,
            Map<String, List<Hit>> byItem,
            List<Hit> unbound,
            Set<String> seen,
            String extraField,
            String contField
    ) throws NoSuchFieldException, IllegalAccessException {
        Object nameObj = quietField(handler, "name");
        String event = groupName + "." + (nameObj == null ? "" : nameObj);
        Object extra = declaredGet(handler, extraField);
        if (extra instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                ingestValue(event, e.getKey(), e.getValue(), "extra", byItem, unbound, seen);
            }
        }
        Object cont = declaredGet(handler, contField);
        if (cont instanceof Object[] arr) {
            for (int i = 0; i < arr.length; i++) {
                ingestValue(event, null, arr[i], kindAt(i), byItem, unbound, seen);
            }
        }
    }

    private static void ingestValue(
            String event,
            Object extraKey,
            Object value,
            String kind,
            Map<String, List<Hit>> byItem,
            List<Hit> unbound,
            Set<String> seen
    ) {
        if (value == null) {
            return;
        }
        if (value instanceof Object[] arr) {
            for (Object one : arr) {
                walkContainer(event, extraKey, one, kind, byItem, unbound, seen);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (Object one : list) {
                walkContainer(event, extraKey, one, kind, byItem, unbound, seen);
            }
            return;
        }
        walkContainer(event, extraKey, value, kind, byItem, unbound, seen);
    }

    private static void walkContainer(
            String event,
            Object extraKey,
            Object container,
            String kind,
            Map<String, List<Hit>> byItem,
            List<Hit> unbound,
            Set<String> seen
    ) {
        Object cur = container;
        int guard = 0;
        while (cur != null && guard++ < CHILD_CAP) {
            String source = normalizeSource(strField(cur, "source"));
            int line = intField(cur, "line");
            Object xid = extraKey != null ? extraKey : quietField(cur, "extraId");
            sampleExtraId(xid);
            String id = normalizeId(xid);
            Hit hit = new Hit(event, source, line, kind);
            if (seen.add(hit.dedupeKey(id))) {
                if (id.isEmpty()) {
                    unbound.add(hit);
                } else {
                    byItem.computeIfAbsent(id, k -> new ArrayList<>()).add(hit);
                }
            }
            cur = quietField(cur, "child");
        }
    }

    static String normalizeId(Object extraId) {
        if (extraId == null) {
            return "";
        }
        String fromGetter = idViaGetter(extraId);
        if (!fromGetter.isEmpty()) {
            return fromGetter;
        }
        if (extraId instanceof Iterable<?> it) {
            for (Object one : it) {
                String id = normalizeId(one);
                if (!id.isEmpty()) {
                    return id;
                }
            }
        }
        if (extraId.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(extraId);
            for (int i = 0; i < len; i++) {
                String id = normalizeId(java.lang.reflect.Array.get(extraId, i));
                if (!id.isEmpty()) {
                    return id;
                }
            }
        }
        String s = extraId.toString().trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("resourcekey[") || s.startsWith("optional[")) {
            return extractIdToken(s);
        }
        if (KubeJsMechanicScan.looksLikeItem(s)) {
            return s;
        }
        return extractIdToken(s);
    }

    /**
     * Miss-path diag only (not public-hit). One line per snapshot cycle, ≤400 chars.
     * public-hit = mode=api with hits>0.
     */
    private static void maybeLogMissDiag(int hits, String mode) {
        if (hits > 0 && "api".equals(mode)) {
            return;
        }
        if (!DIAG_ASK_LOGGED.compareAndSet(false, true)) {
            return;
        }
        logInfo(buildDiagLine());
    }

    static String buildDiagLine() {
        String extra;
        String lookup;
        synchronized (DIAG_LOCK) {
            extra = String.join(",", DIAG_EXTRA_SAMPLES);
            lookup = String.join(",", DIAG_LOOKUP_SAMPLES);
        }
        String by = DIAG_BY_ITEM_KEYS == null ? "" : DIAG_BY_ITEM_KEYS;
        String line = "Pack AI kubejs bridge diag extra=[" + extra + "] lookup=[" + lookup
                + "] byKeys=[" + by + "]";
        if (line.length() <= DIAG_LINE_MAX) {
            return line;
        }
        return line.substring(0, DIAG_LINE_MAX - 3) + "...";
    }

    private static void clearDiagExtraSamples() {
        synchronized (DIAG_LOCK) {
            DIAG_EXTRA_SAMPLES.clear();
        }
    }

    private static void captureByItemDiagKeys(Map<String, List<Hit>> byItem) {
        if (byItem == null || byItem.isEmpty()) {
            DIAG_BY_ITEM_KEYS = "";
            return;
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String k : byItem.keySet()) {
            if (n >= DIAG_SAMPLE_CAP) {
                break;
            }
            if (n > 0) {
                sb.append(',');
            }
            sb.append(maskDiagText(k));
            n++;
        }
        DIAG_BY_ITEM_KEYS = sb.toString();
    }

    private static void sampleExtraId(Object xid) {
        synchronized (DIAG_LOCK) {
            if (DIAG_EXTRA_SAMPLES.size() >= DIAG_SAMPLE_CAP) {
                return;
            }
            String cls = xid == null ? "null" : xid.getClass().getSimpleName();
            String raw = xid == null ? "null" : maskDiagText(String.valueOf(xid));
            String norm = normalizeId(xid);
            DIAG_EXTRA_SAMPLES.add(cls + ":" + raw + "→" + (norm.isEmpty() ? "∅" : norm));
        }
    }

    private static void sampleLookupKey(String key, String from) {
        synchronized (DIAG_LOCK) {
            if (DIAG_LOOKUP_SAMPLES.size() >= DIAG_SAMPLE_CAP) {
                return;
            }
            String src = from == null || from.isBlank() ? "?" : from;
            DIAG_LOOKUP_SAMPLES.add(maskDiagText(key) + "@" + src);
        }
    }

    /** Mask paths / UUIDs; keep short class-safe text + ns:path fragments. */
    static String maskDiagText(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String t = s.replace('\n', ' ').replace('\r', ' ');
        t = t.replaceAll(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                "?");
        t = t.replaceAll("[A-Za-z]:\\\\[^\\\\\\s,→@]+", "?");
        t = t.replaceAll("(?<![\\w:])/(?:Users|home|var|tmp|opt)/[^\\s,→@]+", "?");
        if (t.length() > 32) {
            t = t.substring(0, 32) + "…";
        }
        return t;
    }

    private static String idViaGetter(Object obj) {
        for (String name : ID_GETTERS) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object v = m.invoke(obj);
                if (v == null || v == obj) {
                    continue;
                }
                String s = v.toString().trim().toLowerCase(Locale.ROOT);
                if (KubeJsMechanicScan.looksLikeItem(s)) {
                    return s;
                }
                String extracted = extractIdToken(s);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            } catch (Throwable ignored) {
                // try next
            }
        }
        return "";
    }

    private static String extractIdToken(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        java.util.regex.Matcher m = ID_IN_TEXT.matcher(s);
        while (m.find()) {
            String id = m.group(1);
            if (KubeJsMechanicScan.looksLikeItem(id)) {
                return id;
            }
        }
        return "";
    }

    private static String kindAt(int index) {
        if (index >= 0 && index < SCRIPT_KINDS.length) {
            return SCRIPT_KINDS[index];
        }
        return "script" + index;
    }

    private static long maxScriptMtime(Path gameDir) {
        if (gameDir == null) {
            return 0L;
        }
        long max = 0L;
        for (Path p : KubeJsMechanicScan.listScriptFiles(gameDir.resolve("kubejs"))) {
            try {
                max = Math.max(max, java.nio.file.Files.getLastModifiedTime(p).toMillis());
            } catch (Exception ignored) {
                // skip
            }
        }
        return max;
    }

    private static boolean tryAccess(Field f) {
        if (f == null) {
            return false;
        }
        try {
            return f.trySetAccessible();
        } catch (Throwable t) {
            return false;
        }
    }

    private static Field findDeclared(Class<?> start, String name) throws NoSuchFieldException {
        Class<?> c = start;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object declaredGet(Object obj, String name)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = findDeclared(obj.getClass(), name);
        if (!f.canAccess(obj) && !f.trySetAccessible()) {
            throw new IllegalAccessException("trySetAccessible " + name);
        }
        return f.get(obj);
    }

    private static Object quietField(Object obj, String name) {
        try {
            Field f = findDeclared(obj.getClass(), name);
            if (!f.canAccess(obj) && !f.trySetAccessible()) {
                return null;
            }
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static String strField(Object obj, String name) {
        Object v = quietField(obj, name);
        return v == null ? "" : v.toString();
    }

    private static int intField(Object obj, String name) {
        Object v = quietField(obj, name);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(v.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static Map<String, List<Hit>> freeze(Map<String, List<Hit>> m) {
        LinkedHashMap<String, List<Hit>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Hit>> e : m.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static void logUnavail(String reason) {
        LAST_LOG = "Pack AI kubejs bridge unavailable (" + reason + ")";
        if (UNAVAIL_LOGGED.compareAndSet(false, true)) {
            logInfo(LAST_LOG);
        }
    }

    private static void logInfo(String msg) {
        LAST_LOG = msg;
        try {
            Class<?> c = Class.forName("com.skps9.packai.PackAiMod");
            Object logger = c.getField("LOGGER").get(null);
            logger.getClass().getMethod("info", String.class).invoke(logger, msg);
        } catch (Throwable ignored) {
            // tests / early
        }
    }

    private static final class Collected {
        final Map<String, List<Hit>> byItem;
        final List<Hit> unbound;
        final int groups;
        final int handlers;
        final int extraIds;
        final int entries;
        final String mode;

        Collected(Map<String, List<Hit>> byItem, List<Hit> unbound) {
            this(byItem, unbound, 0, 0, 0, 0, "");
        }

        Collected(
                Map<String, List<Hit>> byItem,
                List<Hit> unbound,
                int groups,
                int handlers,
                int extraIds,
                int entries,
                String mode
        ) {
            this.byItem = byItem;
            this.unbound = unbound;
            this.groups = groups;
            this.handlers = handlers;
            this.extraIds = extraIds;
            this.entries = entries;
            this.mode = mode == null ? "" : mode;
        }
    }
}
