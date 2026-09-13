package com.skps9.packai.logic;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Per-ask JSONL audit under {@code packai/trace/}. IO failure never throws into Ask.
 */
public final class AskTrace {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_KEEP_FILES = 50;
    public static final int KEEP_MIN = 1;
    public static final int KEEP_MAX = 500;
    public static final int TOOL_RESULT_FULL_LIMIT = 8_000;
    public static final int TOOL_RESULT_HEAD_TAIL = 2_000;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter TS_ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Pattern UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SK_KEY = Pattern.compile("(?<![A-Za-z0-9_\\-])sk-[A-Za-z0-9_\\-]{8,}");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Object INDEX_LOCK = new Object();
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final AtomicInteger WARN_COUNT = new AtomicInteger();
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    static Supplier<LocalDateTime> clock = LocalDateTime::now;

    private AskTrace() {}

    /** One Ask's open JSONL + index row. */
    public static final class Session {
        final Path gameDir;
        final Path file;
        final String relativeName;
        final String question;
        final String focusId;
        final String openedTs;
        final int keepFiles;
        boolean enabled;
        final boolean owner;
        BufferedWriter writer;
        int rounds;
        int cardsOut;
        String status = "ok";
        boolean closed;

        Session(
                Path gameDir,
                Path file,
                String relativeName,
                String question,
                String focusId,
                String openedTs,
                int keepFiles,
                boolean enabled,
                boolean owner
        ) {
            this.gameDir = gameDir;
            this.file = file;
            this.relativeName = relativeName;
            this.question = question;
            this.focusId = focusId;
            this.openedTs = openedTs;
            this.keepFiles = keepFiles;
            this.enabled = enabled;
            this.owner = owner;
        }
    }

    public static Session begin(Path gameDir, String question, String focusId) {
        return begin(gameDir, question, focusId, configEnabled(), configKeepFiles());
    }

    public static Session begin(
            Path gameDir, String question, String focusId, boolean enabled, int keepFiles
    ) {
        Session s = open(gameDir, question, focusId, enabled, keepFiles, true);
        CURRENT.set(s);
        return s;
    }

    /** Attach a session opened on another thread (FJP worker / client callback). */
    public static void attach(Session session) {
        CURRENT.set(session);
    }

    public static void detach() {
        CURRENT.remove();
    }

    public static Session current() {
        return CURRENT.get();
    }

    public static boolean active() {
        Session s = CURRENT.get();
        return s != null && s.enabled && !s.closed;
    }

    public static void event(String type) {
        event(type, null);
    }

    public static void event(String type, Consumer<JsonObject> extra) {
        try {
            Session s = CURRENT.get();
            if (s == null || !s.enabled || s.closed) {
                return;
            }
            JsonObject o = new JsonObject();
            o.addProperty("event", type == null ? "" : type);
            o.addProperty("ts", TS_ISO.format(clock.get()));
            if (extra != null) {
                extra.accept(o);
            }
            writeLine(s, maskSecrets(GSON.toJson(o)));
        } catch (Throwable t) {
            warnOnce("event write failed", t);
        }
    }

    public static void toolCall(String name, String args, int round) {
        event("tool.call", o -> {
            o.addProperty("name", nz(name));
            o.addProperty("args", maskSecrets(nz(args)));
            o.addProperty("round", round);
        });
    }

    public static void toolResult(String name, String result, int round) {
        event("tool.result", o -> {
            o.addProperty("name", nz(name));
            o.addProperty("round", round);
            putBodyOrHash(o, result);
        });
    }

    public static void card(
            String event,
            String category,
            String primaryOutputId,
            int outputsSize,
            boolean hasVariant,
            String sourceItemId,
            String placement,
            String reason
    ) {
        event(event, o -> {
            o.addProperty("category", nz(category));
            o.addProperty("primaryOutputId", nz(primaryOutputId));
            o.addProperty("outputsSize", outputsSize);
            o.addProperty("hasVariant", hasVariant);
            o.addProperty("sourceItemId", nz(sourceItemId));
            o.addProperty("placement", nz(placement));
            o.addProperty("reason", nz(reason));
        });
    }

    public static void renderCards(
            String item,
            String role,
            int scannedCats,
            int foundOutput,
            int afterFilter,
            int outputsSize,
            String primaryOutputId,
            boolean hasVariant
    ) {
        event("render.cards", o -> {
            o.addProperty("item", nz(item));
            o.addProperty("role", nz(role));
            o.addProperty("scannedCats", scannedCats);
            o.addProperty("foundOutput", foundOutput);
            o.addProperty("afterFilter", afterFilter);
            o.addProperty("outputsSize", outputsSize);
            o.addProperty("primaryOutputId", nz(primaryOutputId));
            o.addProperty("hasVariant", hasVariant);
        });
    }

    public static void modelRound(int n, String content, String toolCallsJson) {
        event("model.reply.round" + n, o -> {
            o.addProperty("content", maskSecrets(nz(content)));
            o.addProperty("tool_calls", maskSecrets(nz(toolCallsJson)));
        });
        Session s = CURRENT.get();
        if (s != null && n > s.rounds) {
            s.rounds = n;
        }
    }

    public static int nextRound() {
        Session s = CURRENT.get();
        if (s == null) {
            return 1;
        }
        s.rounds++;
        return s.rounds;
    }

    public static int rounds() {
        Session s = CURRENT.get();
        return s == null ? 0 : s.rounds;
    }

    public static void setCardsOut(int n) {
        Session s = CURRENT.get();
        if (s != null) {
            s.cardsOut = Math.max(0, n);
        }
    }

    public static void markError() {
        Session s = CURRENT.get();
        if (s != null) {
            s.status = "error";
        }
    }

    public static void close(String status) {
        close(status, -1, -1);
    }

    public static void close(String status, int rounds, int cardsOut) {
        Session s = CURRENT.get();
        if (s == null) {
            return;
        }
        try {
            finish(s, status, rounds, cardsOut);
        } catch (Throwable t) {
            warnOnce("close failed", t);
        } finally {
            CURRENT.remove();
        }
    }

    public static String askFileName(String stamp, String focusId) {
        String id = sanitizeFocus(focusId);
        String st = stamp == null || stamp.isBlank() ? STAMP.format(clock.get()) : stamp;
        return "ask-" + st + "-" + id + ".jsonl";
    }

    public static String maskSecrets(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = SK_KEY.matcher(raw).replaceAll("***");
        s = BEARER.matcher(s).replaceAll("Bearer ***");
        s = UUID.matcher(s).replaceAll("***");
        for (String secret : configuredSecrets()) {
            if (secret.length() >= 8) {
                s = s.replace(secret, "***");
            }
        }
        return s;
    }

    public static int warnCount() {
        return WARN_COUNT.get();
    }

    /** Test hook — not used in production Ask. */
    public static void resetForTest() {
        CURRENT.remove();
        WARNED.set(false);
        WARN_COUNT.set(0);
        clock = LocalDateTime::now;
    }

    public static Path traceDir(Path gameDir) {
        return gameDir == null ? null : gameDir.resolve("packai").resolve("trace");
    }

    static Session open(
            Path gameDir,
            String question,
            String focusId,
            boolean enabled,
            int keepFiles,
            boolean owner
    ) {
        int keep = Math.max(KEEP_MIN, Math.min(KEEP_MAX, keepFiles));
        String q = question == null ? "" : question;
        String focus = focusId == null ? "" : focusId;
        LocalDateTime now = clock.get();
        String stamp = STAMP.format(now);
        String openedTs = TS_ISO.format(now);
        if (!enabled || gameDir == null) {
            return new Session(gameDir, null, "", q, focus, openedTs, keep, false, owner);
        }
        Path dir = traceDir(gameDir);
        Path file;
        String relative;
        BufferedWriter w;
        try {
            Files.createDirectories(dir);
            relative = uniqueName(dir, stamp, focus);
            file = dir.resolve(relative);
            w = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Throwable t) {
            warnOnce("cannot open packai/trace", t);
            return new Session(gameDir, null, "", q, focus, openedTs, keep, false, owner);
        }
        Session s = new Session(gameDir, file, relative, q, focus, openedTs, keep, true, owner);
        s.writer = w;
        return s;
    }

    private static String uniqueName(Path dir, String stamp, String focusId) {
        String base = askFileName(stamp, focusId);
        Path p = dir.resolve(base);
        if (!Files.exists(p)) {
            return base;
        }
        String id = sanitizeFocus(focusId);
        for (int i = 2; i < 100; i++) {
            String n = "ask-" + stamp + "-" + id + "-" + i + ".jsonl";
            if (!Files.exists(dir.resolve(n))) {
                return n;
            }
        }
        return "ask-" + stamp + "-" + id + "-" + System.nanoTime() + ".jsonl";
    }

    static String sanitizeFocus(String focusId) {
        if (focusId == null || focusId.isBlank()) {
            return "none";
        }
        StringBuilder b = new StringBuilder(focusId.length());
        for (int i = 0; i < focusId.length(); i++) {
            char c = focusId.charAt(i);
            if (c == ':') {
                b.append('_');
            } else if (c < 32 || c == '/' || c == '\\' || c == '<' || c == '>'
                    || c == '"' || c == '|' || c == '?' || c == '*') {
                b.append('_');
            } else {
                b.append(c);
            }
        }
        String s = b.toString().trim();
        return s.isEmpty() ? "none" : s;
    }

    private static synchronized void writeLine(Session s, String line) {
        if (s == null || !s.enabled || s.writer == null || s.closed) {
            return;
        }
        try {
            s.writer.write(line);
            s.writer.write('\n');
            s.writer.flush();
        } catch (Throwable t) {
            warnOnce("append jsonl failed", t);
            try {
                if (s.writer != null) {
                    s.writer.close();
                }
            } catch (Throwable ignored) {
                // already failing
            }
            s.writer = null;
            s.enabled = false;
        }
    }

    private static void finish(Session s, String status, int rounds, int cardsOut) {
        if (s == null || s.closed) {
            return;
        }
        s.closed = true;
        if (rounds >= 0) {
            s.rounds = rounds;
        }
        if (cardsOut >= 0) {
            s.cardsOut = cardsOut;
        }
        if (status != null && !status.isBlank()) {
            s.status = status;
        }
        try {
            if (s.writer != null) {
                s.writer.close();
            }
        } catch (Throwable t) {
            warnOnce("close writer failed", t);
        }
        s.writer = null;
        if (s.file == null || s.gameDir == null) {
            return;
        }
        writeIndex(s);
        rotate(s.gameDir, s.keepFiles);
    }

    private static void writeIndex(Session s) {
        Path index = traceDir(s.gameDir).resolve("index.jsonl");
        JsonObject o = new JsonObject();
        o.addProperty("ts", s.openedTs);
        o.addProperty("question", maskSecrets(s.question));
        o.addProperty("focusId", s.focusId);
        o.addProperty("file", s.relativeName);
        o.addProperty("rounds", s.rounds);
        o.addProperty("cardsOut", s.cardsOut);
        o.addProperty("status", s.status == null ? "ok" : s.status);
        String line = GSON.toJson(o) + "\n";
        synchronized (INDEX_LOCK) {
            try {
                Files.createDirectories(index.getParent());
                Files.writeString(
                        index,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (Throwable t) {
                warnOnce("index.jsonl write failed", t);
            }
        }
    }

    static void rotate(Path gameDir, int keepFiles) {
        int keep = Math.max(KEEP_MIN, Math.min(KEEP_MAX, keepFiles));
        Path dir = traceDir(gameDir);
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        List<Path> asks = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("ask-") && n.endsWith(".jsonl");
            }).forEach(asks::add);
        } catch (Throwable t) {
            warnOnce("rotate list failed", t);
            return;
        }
        if (asks.size() <= keep) {
            return;
        }
        asks.sort(Comparator
                .comparingLong(AskTrace::mtime)
                .thenComparing(p -> p.getFileName().toString()));
        int drop = asks.size() - keep;
        for (int i = 0; i < drop; i++) {
            try {
                Files.deleteIfExists(asks.get(i));
            } catch (Throwable t) {
                warnOnce("rotate delete failed", t);
            }
        }
    }

    private static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    static void putBodyOrHash(JsonObject o, String result) {
        String text = maskSecrets(result == null ? "" : result);
        if (text.length() <= TOOL_RESULT_FULL_LIMIT) {
            o.addProperty("result", text);
            return;
        }
        o.addProperty("truncated", true);
        o.addProperty("chars", text.length());
        o.addProperty("sha256", sha256(text));
        o.addProperty("head", text.substring(0, TOOL_RESULT_HEAD_TAIL));
        o.addProperty("tail", text.substring(text.length() - TOOL_RESULT_HEAD_TAIL));
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return "";
        }
    }

    static void warnOnce(String msg, Throwable t) {
        WARN_COUNT.incrementAndGet();
        if (!WARNED.compareAndSet(false, true)) {
            return;
        }
        String m = msg == null ? "trace io failed" : msg;
        try {
            Class<?> c = Class.forName("com.skps9.packai.PackAiMod");
            Object logger = c.getField("LOGGER").get(null);
            logger.getClass()
                    .getMethod("warn", String.class, Object.class, Object.class)
                    .invoke(logger, "Pack AI ask trace: {} {}", m, t == null ? "" : t.toString());
        } catch (Throwable ignored) {
            System.err.println("Pack AI ask trace: " + m + (t == null ? "" : " " + t));
        }
    }

    private static boolean configEnabled() {
        try {
            Class<?> c = Class.forName("com.skps9.packai.config.PackAiConfig");
            Object v = c.getMethod("askTraceJsonl").invoke(null);
            return v instanceof Boolean b ? b : DEFAULT_ENABLED;
        } catch (Throwable t) {
            return DEFAULT_ENABLED;
        }
    }

    private static int configKeepFiles() {
        try {
            Class<?> c = Class.forName("com.skps9.packai.config.PackAiConfig");
            Object v = c.getMethod("askTraceKeepFiles").invoke(null);
            if (v instanceof Number n) {
                return Math.max(KEEP_MIN, Math.min(KEEP_MAX, n.intValue()));
            }
        } catch (Throwable ignored) {
            // default
        }
        return DEFAULT_KEEP_FILES;
    }

    private static List<String> configuredSecrets() {
        List<String> out = new ArrayList<>();
        addSecret(out, "com.skps9.packai.config.PackAiConfig", "API_KEY");
        addSecret(out, "com.skps9.packai.config.PackAiConfig", "TAVILY_API_KEY");
        addSecret(out, "com.skps9.packai.config.PackAiConfig", "SERPER_API_KEY");
        addEnv(out, "PACKAI_API_KEY");
        addEnv(out, "OPENAI_API_KEY");
        addEnv(out, "DEEPSEEK_API_KEY");
        addEnv(out, "TAVILY_API_KEY");
        addEnv(out, "SERPER_API_KEY");
        return out;
    }

    private static void addEnv(List<String> out, String name) {
        try {
            String v = System.getenv(name);
            if (v != null && v.length() >= 8) {
                out.add(v);
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static void addSecret(List<String> out, String className, String field) {
        try {
            Class<?> c = Class.forName(className);
            Object cfg = c.getField(field).get(null);
            Object v = cfg.getClass().getMethod("get").invoke(cfg);
            if (v instanceof String s && s.length() >= 8) {
                out.add(s);
            }
        } catch (Throwable ignored) {
            // config not loaded
        }
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static String toolsSummary(JsonArray tools) {
        if (tools == null || tools.isEmpty()) {
            return "[]";
        }
        JsonArray out = new JsonArray();
        for (JsonElement el : tools) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject t = el.getAsJsonObject();
            JsonObject row = new JsonObject();
            String name = "";
            if (t.has("function") && t.get("function").isJsonObject()) {
                JsonObject fn = t.getAsJsonObject("function");
                if (fn.has("name")) {
                    name = fn.get("name").getAsString();
                }
                if (fn.has("parameters")) {
                    row.add("schema", fn.get("parameters"));
                }
            }
            row.addProperty("name", name);
            out.add(row);
        }
        return GSON.toJson(out);
    }

    public static void sendMessages(JsonArray messages, JsonArray tools, String facts) {
        try {
            if (!active() || messages == null) {
                return;
            }
            JsonArray history = new JsonArray();
            String system = "";
            String user = "";
            for (JsonElement el : messages) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject m = el.getAsJsonObject();
                String role = m.has("role") ? m.get("role").getAsString() : "";
                String content = "";
                if (m.has("content") && !m.get("content").isJsonNull()) {
                    JsonElement c = m.get("content");
                    content = c.isJsonPrimitive() ? c.getAsString() : c.toString();
                }
                if ("system".equals(role) && system.isEmpty()) {
                    system = content;
                } else if ("user".equals(role)) {
                    user = content;
                } else if ("assistant".equals(role) || "tool".equals(role)) {
                    history.add(m);
                } else if (!"system".equals(role)) {
                    history.add(m);
                }
            }
            final String sys = system;
            final String usr = user;
            event("send.system", o -> o.addProperty("content", maskSecrets(sys)));
            event("send.history", o -> o.add("turns", history));
            event("send.user", o -> o.addProperty("content", maskSecrets(usr)));
            event("send.tools", o -> o.addProperty("tools", toolsSummary(tools)));
            event("send.facts", o -> o.addProperty("content", maskSecrets(nz(facts))));
        } catch (Throwable t) {
            warnOnce("send.* failed", t);
        }
    }

    public static void markers(String body, JsonArray emissionRefs) {
        event("render.markers", o -> {
            JsonArray marks = new JsonArray();
            String text = body == null ? "" : body;
            String needle = "[[recipe_card:";
            int from = 0;
            while (from < text.length()) {
                int i = text.indexOf(needle, from);
                if (i < 0) {
                    break;
                }
                int end = text.indexOf("]]", i);
                JsonObject m = new JsonObject();
                m.addProperty("index", i);
                m.addProperty("marker", end > i ? text.substring(i, Math.min(end + 2, text.length())) : needle);
                marks.add(m);
                from = i + needle.length();
            }
            o.add("recipe_card_markers", marks);
            o.add("emissionRefs", emissionRefs == null ? new JsonArray() : emissionRefs);
        });
    }
}
