package com.skps9.packai.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.skps9.packai.config.PackAiConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KubeJS script obtain-channel parser (plan v6.14 單 1).
 * Pure text scan — no JEI / recipe facts. {@code rel}/{@code line} stay out of player text.
 */
public final class JsObtainSites {
    /** Plan §0 / §2.7 / fixture / harness — same literal. */
    public static final String PLAYER_VISIBLE_FORBIDDEN =
            "(src:)|(\\.js\\b)|(\\.json\\b)|(kubejs[/\\\\])";
    public static final Pattern PLAYER_VISIBLE_FORBIDDEN_RE =
            Pattern.compile(PLAYER_VISIBLE_FORBIDDEN);

    private static final int BODY_CHARS = 6000;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final Pattern ID = Pattern.compile(
            "[a-z0-9_]+:[a-z0-9_./-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIVE = Pattern.compile(
            "\\.(?:give|giveInHand|addItem|insertItem|addToInventory)\\b\\s*\\(\\s*"
                    + "(?:Item\\.of\\s*\\(\\s*)?['\"](" + ID.pattern() + ")['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOT = Pattern.compile(
            "(?:addLoot|LootEntry|dropItem|spawnItem)\\b[^\\n]{0,80}?['\"]("
                    + ID.pattern() + ")['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SETSLOT = Pattern.compile(
            "\\.(?:setStackInSlot|setItemSlot)\\s*\\([^,)]*,\\s*(?:Item\\.of\\s*\\(\\s*)?['\"]("
                    + ID.pattern() + ")['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE = Pattern.compile(
            "['\"](" + ID.pattern() + ")['\"]\\s*:\\s*(?:function|\\()",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIGN = Pattern.compile(
            "\\w+\\s*\\[\\s*['\"](" + ID.pattern() + ")['\"]\\s*\\]\\s*=\\s*function",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_RC = Pattern.compile(
            "BlockEvents\\.rightClicked\\s*\\(\\s*['\"](" + ID.pattern() + ")['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_RC = Pattern.compile(
            "ItemEvents\\.(?:rightClicked|firstRightClicked)\\s*\\(\\s*['\"]("
                    + ID.pattern() + ")['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENT_DEATH = Pattern.compile(
            "EntityEvents\\.death\\s*\\(\\s*['\"](" + ID.pattern() + ")['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ORGAN_PARAM = Pattern.compile(
            "function\\s*\\([^)]*\\borgan\\b|\\(\\s*\\w+\\s*,\\s*organ\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_GATE_ITEM_MAP = Pattern.compile(
            "getPlayerChestCavityItemMap\\s*\\([^)]*\\)\\s*\\.has\\s*\\(\\s*['\"]("
                    + ID.pattern() + ")['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_GATE_ITEMMAP = Pattern.compile(
            "\\bitemMap\\.has\\s*\\(\\s*['\"](" + ID.pattern() + ")['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_GATE_SCORE = Pattern.compile(
            "organScores\\.get\\s*\\(\\s*new\\s+ResourceLocation\\s*\\(\\s*['\"]chestcavity['\"]\\s*,\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IF_OPEN = Pattern.compile("\\bif\\s*\\(");
    private static final Pattern COND_HINT = Pattern.compile(
            "%|概率|機率|几率|Math\\.random\\s*\\(|random\\s*[<>]=?\\s*[0-9.]+"
                    + "|\\.age\\s*%\\s*[0-9]+|hasEffect\\(|hasNBT\\(|isUnderWater\\("
                    + "|getDifficulty\\(|persistentData",
            Pattern.CASE_INSENSITIVE);

    private static final HeldForm[] HELD_FORMS = {
            new HeldForm(Pattern.compile("\\bitem\\s*==\\s*['\"](" + ID.pattern() + ")['\"]",
                    Pattern.CASE_INSENSITIVE), "mainhand"),
            new HeldForm(Pattern.compile(
                    "getMainHandItem\\s*\\(\\s*\\)\\s*==\\s*(?:Item\\.of\\s*\\(\\s*)?['\"]("
                            + ID.pattern() + ")['\"]",
                    Pattern.CASE_INSENSITIVE), "mainhand"),
            new HeldForm(Pattern.compile(
                    "getOffHandItem\\s*\\(\\s*\\)\\s*==\\s*(?:Item\\.of\\s*\\(\\s*)?['\"]("
                            + ID.pattern() + ")['\"]",
                    Pattern.CASE_INSENSITIVE), "offhand"),
            new HeldForm(Pattern.compile(
                    "mainitem\\s*\\?\\.\\s*id\\s*==\\s*['\"](" + ID.pattern() + ")['\"]",
                    Pattern.CASE_INSENSITIVE), "mainhand"),
            new HeldForm(Pattern.compile(
                    "mainHandItem\\s*==\\s*(?:Item\\.of\\s*\\(\\s*)?['\"]("
                            + ID.pattern() + ")['\"]",
                    Pattern.CASE_INSENSITIVE), "mainhand"),
    };

    /** Test hook — entity display name (default I18n / fallback). */
    static volatile Function<String, String> entityLabelFn = JsObtainSites::defaultEntityLabel;
    /** Test hook — block display name (default OfficialDisplay → I18n → id). */
    static volatile Function<String, String> blockLabelFn = JsObtainSites::defaultBlockLabel;

    private JsObtainSites() {}

    public enum Kind { PRODUCE, SWAP, TRANSFORM }

    public enum GateKind { TABLE_KEY, BODY_CHECK, NONE }

    public enum TriggerKind { RIGHT_CLICK, HURT_BY_PLAYER, ENTITY_DEATH, BLOCK_RIGHT_CLICK }

    public record Trigger(
            TriggerKind kind,
            String event,
            String dispatcherRel,
            int dispatcherLine,
            String organTag,
            String targetId
    ) {}

    public record Site(
            String outId,
            Trigger trigger,
            String rel,
            int line,
            int entryLine,
            String cond,
            Kind kind,
            String organGate,
            GateKind gateKind,
            String heldItem,
            String heldSlot
    ) {}

    private record HeldForm(Pattern pattern, String slot) {}

    private record HandlerHit(int start, int end, String id, String form) {}

    private record SinkHit(int relPos, String outId, Kind kind) {}

    static void resetLabelFns() {
        entityLabelFn = JsObtainSites::defaultEntityLabel;
        blockLabelFn = JsObtainSites::defaultBlockLabel;
    }

    /** True for kubejs server/startup scripts (not client / assets / data). */
    public static boolean isObtainScanRel(String rel) {
        if (rel == null || rel.isBlank()) {
            return false;
        }
        String pl = rel.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (!pl.endsWith(".js")) {
            return false;
        }
        if (pl.contains("/client_scripts/") || pl.contains("/assets/") || pl.contains("/data/")) {
            return false;
        }
        return pl.contains("kubejs/server_scripts/") || pl.contains("kubejs/startup_scripts/")
                || pl.startsWith("server_scripts/") || pl.startsWith("startup_scripts/");
    }

    /** Normalize to instance-relative path with {@code kubejs/} prefix. */
    public static String normalizeRel(String rel) {
        if (rel == null || rel.isBlank()) {
            return "";
        }
        String r = rel.replace('\\', '/');
        if (r.startsWith("kubejs/")) {
            return r;
        }
        if (r.startsWith("server_scripts/") || r.startsWith("startup_scripts/")) {
            return "kubejs/" + r;
        }
        return r;
    }

    public static String stripKubejsPrefix(String rel) {
        String r = normalizeRel(rel);
        return r.startsWith("kubejs/") ? r.substring("kubejs/".length()) : r;
    }

    public static List<Site> parse(String rel, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normRel = normalizeRel(rel);
        List<HandlerHit> handlers = new ArrayList<>();
        collect(handlers, TABLE, text, "table");
        collect(handlers, ASSIGN, text, "assign");
        collect(handlers, BLOCK_RC, text, "block_rc");
        collect(handlers, ITEM_RC, text, "item_rc");
        collect(handlers, ENT_DEATH, text, "ent_death");
        handlers.sort(Comparator.comparingInt(h -> h.start));

        List<Site> out = new ArrayList<>();
        for (HandlerHit h : handlers) {
            int searchFrom = ("table".equals(h.form) || "assign".equals(h.form)) ? h.end - 1 : h.end;
            BraceBody body = extractBraceBody(text, searchFrom);
            if (body == null || body.text.isEmpty()) {
                continue;
            }
            int entryLine = lineOf(text, h.start);
            String header = text.substring(h.start, Math.min(text.length(), body.bracePos + 80));
            boolean organParam = ORGAN_PARAM.matcher(header).find();

            List<SinkHit> sinks = new ArrayList<>();
            Matcher gm = GIVE.matcher(body.text);
            while (gm.find()) {
                sinks.add(new SinkHit(gm.start(), gm.group(1).toLowerCase(Locale.ROOT), Kind.PRODUCE));
            }
            Matcher lm = LOOT.matcher(body.text);
            while (lm.find()) {
                sinks.add(new SinkHit(lm.start(), lm.group(1).toLowerCase(Locale.ROOT), Kind.PRODUCE));
            }
            Matcher sm = SETSLOT.matcher(body.text);
            while (sm.find()) {
                sinks.add(new SinkHit(sm.start(), sm.group(1).toLowerCase(Locale.ROOT), Kind.TRANSFORM));
            }

            for (SinkHit sink : sinks) {
                if ("minecraft:air".equals(sink.outId)) {
                    continue;
                }
                int abs = body.bracePos + sink.relPos;
                int line = lineOf(text, abs);
                List<String> conds = enclosingIfConds(body.text, sink.relPos);
                String heldItem = null;
                String heldSlot = null;
                String cond = "";
                for (String c : conds) {
                    HeldMatch hm = matchHeld(c);
                    if (hm != null) {
                        heldItem = hm.id;
                        heldSlot = hm.slot;
                        cond = trimCond(c);
                        break;
                    }
                }
                if (heldItem == null && "block_rc".equals(h.form)) {
                    heldItem = h.id;
                    heldSlot = "trigger_block";
                }
                if (cond.isEmpty()) {
                    cond = pickCond(conds);
                }

                String organGate = null;
                GateKind gateKind = GateKind.NONE;
                if (("table".equals(h.form) || "assign".equals(h.form)) && organParam) {
                    organGate = h.id;
                    gateKind = GateKind.TABLE_KEY;
                } else {
                    String bodyGate = bodyGateId(body.text);
                    if (bodyGate != null) {
                        organGate = bodyGate;
                        gateKind = GateKind.BODY_CHECK;
                    }
                }

                Trigger trigger = buildTrigger(h, normRel, entryLine);
                out.add(new Site(
                        sink.outId,
                        trigger,
                        normRel,
                        line,
                        entryLine,
                        cond,
                        sink.kind,
                        organGate,
                        gateKind,
                        heldItem,
                        heldSlot));
            }
        }
        return out;
    }

    private static void collect(List<HandlerHit> out, Pattern p, String text, String form) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.add(new HandlerHit(m.start(), m.end(), m.group(1).toLowerCase(Locale.ROOT), form));
        }
    }

    private static Trigger buildTrigger(HandlerHit h, String normRel, int entryLine) {
        String dispRel = stripKubejsPrefix(normRel);
        String low = normRel.toLowerCase(Locale.ROOT);
        return switch (h.form) {
            case "ent_death" -> new Trigger(
                    TriggerKind.ENTITY_DEATH,
                    "EntityEvents.death('" + h.id + "')",
                    dispRel,
                    entryLine,
                    null,
                    h.id);
            case "block_rc" -> new Trigger(
                    TriggerKind.BLOCK_RIGHT_CLICK,
                    "BlockEvents.rightClicked('" + h.id + "')",
                    dispRel,
                    entryLine,
                    null,
                    h.id);
            case "item_rc" -> new Trigger(
                    TriggerKind.RIGHT_CLICK,
                    "ItemEvents.rightClicked('" + h.id + "')",
                    dispRel,
                    entryLine,
                    null,
                    null);
            case "table", "assign" -> {
                boolean hurt = low.contains("damage") || low.contains("hurt")
                        || low.contains("player_damage");
                if (hurt) {
                    yield new Trigger(
                            TriggerKind.HURT_BY_PLAYER,
                            "ForgeEvents.onEvent(LivingHurtEvent)",
                            "startup_scripts/entity_hurt.js",
                            6,
                            "kubejs:damage_only",
                            null);
                }
                yield new Trigger(
                        TriggerKind.RIGHT_CLICK,
                        "ItemEvents.rightClicked",
                        "server_scripts/organ/item_right.js",
                        2,
                        "kubejs:rclick_only",
                        null);
            }
            default -> new Trigger(TriggerKind.RIGHT_CLICK, "?", dispRel, entryLine, null, null);
        };
    }

    private static String bodyGateId(String body) {
        Matcher m = BODY_GATE_ITEM_MAP.matcher(body);
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }
        m = BODY_GATE_ITEMMAP.matcher(body);
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }
        m = BODY_GATE_SCORE.matcher(body);
        if (m.find()) {
            return "chestcavity:" + m.group(1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static HeldMatch matchHeld(String cond) {
        for (HeldForm f : HELD_FORMS) {
            Matcher m = f.pattern.matcher(cond);
            if (m.find()) {
                return new HeldMatch(m.group(1).toLowerCase(Locale.ROOT), f.slot);
            }
        }
        return null;
    }

    private record HeldMatch(String id, String slot) {}

    private static String pickCond(List<String> conds) {
        for (String c : conds) {
            if (COND_HINT.matcher(c).find()) {
                return trimCond(c);
            }
        }
        return conds.isEmpty() ? "" : trimCond(conds.get(0));
    }

    private static String trimCond(String c) {
        if (c == null) {
            return "";
        }
        String t = c.replaceAll("\\s+", " ").trim();
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    private static List<String> enclosingIfConds(String body, int sinkRelPos) {
        List<SpanCond> spans = new ArrayList<>();
        Matcher m = IF_OPEN.matcher(body);
        while (m.find()) {
            int condEnd = matchingParen(body, m.end() - 1);
            if (condEnd < 0) {
                continue;
            }
            int k = condEnd + 1;
            while (k < body.length() && Character.isWhitespace(body.charAt(k))) {
                k++;
            }
            if (k >= body.length() || body.charAt(k) != '{') {
                continue;
            }
            int end = braceEnd(body, k);
            if (m.start() <= sinkRelPos && sinkRelPos <= end) {
                spans.add(new SpanCond(end - m.start(), body.substring(m.start(), condEnd + 1)));
            }
        }
        spans.sort(Comparator.comparingInt(s -> s.span));
        List<String> out = new ArrayList<>(spans.size());
        for (SpanCond s : spans) {
            out.add(s.cond);
        }
        return out;
    }

    private record SpanCond(int span, String cond) {}

    private static int matchingParen(String text, int openPos) {
        int depth = 0;
        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private record BraceBody(String text, int bracePos) {}

    private static BraceBody extractBraceBody(String text, int from) {
        if (text == null || from < 0 || from >= text.length()) {
            return null;
        }
        int brace = text.indexOf('{', from);
        if (brace < 0 || brace - from > 120) {
            return null;
        }
        int end = braceEnd(text, brace);
        int limit = Math.min(text.length(), brace + BODY_CHARS);
        if (end >= limit) {
            return new BraceBody(text.substring(brace, limit), brace);
        }
        return new BraceBody(text.substring(brace, end + 1), brace);
    }

    /** Quote / line-comment / block-comment aware brace match. */
    static int braceEnd(String text, int brace) {
        int depth = 0;
        int i = brace;
        Character inS = null;
        boolean esc = false;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (inS != null) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == inS) {
                    inS = null;
                }
                i++;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                inS = c;
                i++;
                continue;
            }
            if (c == '/' && i + 1 < text.length()) {
                char n = text.charAt(i + 1);
                if (n == '/') {
                    int nl = text.indexOf('\n', i);
                    i = nl < 0 ? text.length() : nl;
                    continue;
                }
                if (n == '*') {
                    int close = text.indexOf("*/", i + 2);
                    i = close < 0 ? text.length() : close + 2;
                    continue;
                }
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return text.length() - 1;
    }

    static int lineOf(String text, int pos) {
        int line = 1;
        int lim = Math.min(pos, text.length());
        for (int i = 0; i < lim; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Shared runtime + harness label rule (plan §2.7). */
    public static String triggerLabel(Trigger trigger) {
        if (trigger == null || trigger.kind() == null) {
            return "右鍵";
        }
        return switch (trigger.kind()) {
            case RIGHT_CLICK -> "右鍵";
            case HURT_BY_PLAYER -> "玩家攻擊命中";
            case ENTITY_DEATH -> entityLabelFn.apply(
                    trigger.targetId() == null ? "" : trigger.targetId()) + "死亡";
            case BLOCK_RIGHT_CLICK -> blockLabelFn.apply(
                    trigger.targetId() == null ? "" : trigger.targetId()) + "右鍵";
        };
    }

    private static String defaultEntityLabel(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        try {
            // Render-thread I18n; headless / missing → fallback id.
            // Forbidden: OfficialDisplay / Registry.ITEM (R10 — entity ids do not resolve).
            net.minecraft.resources.ResourceLocation loc =
                    new net.minecraft.resources.ResourceLocation(id);
            net.minecraft.world.entity.EntityType<?> type =
                    net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(loc);
            if (type != null) {
                String desc = type.getDescriptionId();
                String name = net.minecraft.client.resources.language.I18n.get(desc);
                if (name != null && !name.isBlank() && !name.equals(desc)) {
                    return name.trim();
                }
            }
            String key = "entity." + id.replace(':', '.');
            String viaKey = net.minecraft.client.resources.language.I18n.get(key);
            if (viaKey != null && !viaKey.isBlank() && !viaKey.equals(key)) {
                return viaKey.trim();
            }
        } catch (Throwable ignored) {
            // headless
        }
        return id;
    }

    private static String defaultBlockLabel(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String official = OfficialDisplay.officialName(id);
        if (official != null && !official.isBlank()) {
            return official;
        }
        try {
            String key = "block." + id.replace(':', '.');
            String viaKey = net.minecraft.client.resources.language.I18n.get(key);
            if (viaKey != null && !viaKey.isBlank() && !viaKey.equals(key)) {
                return viaKey.trim();
            }
        } catch (Throwable ignored) {
            // headless
        }
        return id;
    }

    /** Test hook — null = PackAiConfig; non-null forces diag on/off without Forge SPEC. */
    static volatile Boolean diagLogOverride = null;

    public static Path diagFile(Path gameDir) {
        if (gameDir == null) {
            return null;
        }
        return gameDir.resolve("packai").resolve("js-obtain-diag.jsonl");
    }

    static boolean diagEnabled() {
        if (diagLogOverride != null) {
            return diagLogOverride;
        }
        return PackAiConfig.jsObtainDiagLog();
    }

    public static void appendDiag(Path gameDir, Site site) {
        if (!diagEnabled() || gameDir == null || site == null) {
            return;
        }
        Path file = diagFile(gameDir);
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("outId", site.outId());
            row.put("kind", site.kind() == null ? "" : site.kind().name());
            row.put("triggerKind", site.trigger() == null || site.trigger().kind() == null
                    ? "" : site.trigger().kind().name());
            row.put("rel", site.rel());
            row.put("line", site.line());
            row.put("entryLine", site.entryLine());
            row.put("gateKind", site.gateKind() == null ? "" : site.gateKind().name());
            row.put("organGate", site.organGate() == null ? "" : site.organGate());
            row.put("heldItem", site.heldItem() == null ? "" : site.heldItem());
            Files.writeString(
                    file,
                    GSON.toJson(row) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // never throw into Ask
        }
    }

    public static boolean playerVisibleForbidden(String text) {
        return text != null && PLAYER_VISIBLE_FORBIDDEN_RE.matcher(text).find();
    }
}
