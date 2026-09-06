package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.api.AskToolCall;
import com.skps9.packai.api.RegistrationStatus;

/**
 * Hybrid Ask tool-loop. Capable path may send native {@code tools} on the first
 * craft/obtain LLM round; fallback / PURPOSE / {@code off} stay no-schema.
 * Drain is intent-scoped; JSON hop is {@code [[tools]]} + {@code {"calls":[...]}} only.
 */
public final class AskToolLoop {
    public static final AskToolLoop INSTANCE = new AskToolLoop();

    public static final int MAX_LLM_ROUNDS = 3;
    public static final int MAX_LOCAL_TOOLS = 8;
    public static final long WALL_MS = 90_000L;
    public static final String JSON_MARKER = "[[tools]]";

    public static final List<String> FIRST_ROUND_TOOLS = List.of(
            "jei_lookup", "acquire", "guide_fetch", "quest_fetch", "consume_use");

    public static final List<String> CAPABLE_TOOLS = List.of(
            "jei_lookup", "acquire", "guide_fetch", "quest_fetch", "consume_use",
            "item_search", "render_recipe_cards", "purpose_lookup", "enchant_lookup", "repair_lookup",
            "tool_build", "tetra_use", "worldgen_lookup");

    public static final Set<String> ALLOWLIST = Set.copyOf(CAPABLE_TOOLS);

    private static final Set<String> QUERY_TOOLS = Set.of(
            "item_search", "render_recipe_cards", "worldgen_lookup", "purpose_lookup", "enchant_lookup",
            "repair_lookup", "tool_build", "tetra_use", "jei_lookup");

    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([a-z0-9_]+)\"");
    private static final Pattern ITEM = Pattern.compile("\"item\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern DUMP = Pattern.compile("\"dump_level\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern QUERY = Pattern.compile("\"query\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern KEYS = Pattern.compile("\"variant_keys\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern KEY_STR = Pattern.compile("\"([^\"]+)\"");

    private static final Pattern DSML_INVOKE = Pattern.compile(
            "(?is)<\\s*\\|\\s*DSML\\s*\\|\\s*(?:>\\s*)?(?:\\|\\s*)?invoke\\s+name\\s*=\\s*\"([^\"]+)\"\\s*>"
                    + "(.*?)"
                    + "</\\s*\\|\\s*DSML\\s*\\|\\s*(?:>\\s*)?(?:\\|\\s*)?invoke\\s*>");
    private static final Pattern DSML_PARAM = Pattern.compile(
            "(?is)<\\s*\\|\\s*DSML\\s*\\|\\s*(?:>\\s*)?(?:\\|\\s*)?parameter\\s+name\\s*=\\s*\"([^\"]+)\"[^>]*>"
                    + "(.*?)"
                    + "</\\s*\\|\\s*DSML\\s*\\|\\s*(?:>\\s*)?(?:\\|\\s*)?parameter\\s*>");
    private static final Pattern TOOL_CALL_XML = Pattern.compile(
            "(?is)<\\s*tool_call\\s*>(.*?)</\\s*tool_call\\s*>");
    private static final Pattern DSML_TOKEN = Pattern.compile("(?i)<\\s*\\|\\s*DSML\\s*\\|");
    private static final Pattern EXTERNAL_NAME = Pattern.compile("^[a-z][a-z0-9_]*$");

    private static final ThreadLocal<Object> ENV = new ThreadLocal<>();

    private final Map<String, AskTool> registry = new LinkedHashMap<>();

    public interface LlmBridge {
        String askNoTools();

        LlmRound completeWithTools(List<String> toolNames);

        void rememberNoNativeTools();

        boolean noNativeTools();

        /** auto | force | off. Default auto so headless fakes stay capable. */
        default String nativeToolsMode() {
            return "auto";
        }
    }

    private AskToolLoop() {}

    public static Object env() {
        return ENV.get();
    }

    public static void bindEnv(Object env) {
        ENV.set(env);
    }

    public static void clearEnv() {
        ENV.remove();
    }

    /** Flush pending card emissions into {@code state}, then clear the thread-local env. */
    public static void clearEnv(AskLoopState state) {
        AskToolEnv env = AskToolEnv.current();
        if (env != null) {
            env.flushEmissionsTo(state);
        }
        clearEnv();
    }

    /** Loader-neutral args factory (AskToolArgs itself is api/-pure and cannot depend on AskLoopState). */
    public static AskToolArgs argsFrom(AskLoopState state) {
        return argsFrom(state, state.dumpLevel(), state.variantKeys());
    }

    public static AskToolArgs argsFrom(AskLoopState state, String dumpLevel, List<String> variantKeys) {
        return new AskToolArgs(
                state.itemId(),
                dumpLevel,
                variantKeys,
                state.question(),
                state.lang(),
                state.gameDir(),
                state.scanners(),
                state.deadlineMs());
    }

    public void register(AskTool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            return;
        }
        if (!ALLOWLIST.contains(tool.name())) {
            return;
        }
        registry.put(tool.name(), tool);
    }

    /** Third-party registration: dup/reserved/structural-validated store, bypassing the ALLOWLIST gate.
     *  Registered tools are stored but NOT schema/exec-visible until Scope X (v1 keeps every gate closed). */
    public RegistrationStatus registerExternal(AskTool tool) {
        try {
            if (tool == null || tool.name() == null || !EXTERNAL_NAME.matcher(tool.name()).matches()) {
                return RegistrationStatus.REJECT_BAD_SCHEMA;
            }
            String name = tool.name();
            if (ALLOWLIST.contains(name)) {
                return RegistrationStatus.REJECT_RESERVED;
            }
            if (registry.containsKey(name)) {
                return RegistrationStatus.REJECT_DUP;
            }
            String desc = tool.description();
            if (desc == null || desc.isBlank()) {
                return RegistrationStatus.REJECT_BAD_SCHEMA;
            }
            String schemaJson = tool.argsSchemaJson();
            try {
                if (schemaJson == null || !JsonParser.parseString(schemaJson).isJsonObject()) {
                    return RegistrationStatus.REJECT_BAD_SCHEMA;
                }
            } catch (JsonSyntaxException e) {
                return RegistrationStatus.REJECT_BAD_SCHEMA;
            }
            registry.put(name, tool);
            return RegistrationStatus.OK_STORED_NOT_ALLOWLISTED;
        } catch (RuntimeException | LinkageError e) {
            return RegistrationStatus.REJECT_BAD_SCHEMA;
        }
    }

    /** Tests replace the live registry with fakes. */
    public void replaceAll(List<AskTool> tools) {
        registry.clear();
        if (tools == null) {
            return;
        }
        for (AskTool t : tools) {
            register(t);
        }
    }

    public static String fingerprint(String tool, String itemId, String dumpLevel, List<String> variantKeys) {
        String t = tool == null ? "" : tool;
        String id = itemId == null ? "" : itemId;
        String lvl = dumpLevel == null ? "" : dumpLevel;
        List<String> keys = new ArrayList<>();
        if (variantKeys != null) {
            for (String k : variantKeys) {
                if (k != null && !k.isBlank()) {
                    keys.add(k);
                }
            }
        }
        Collections.sort(keys);
        return t + "\0" + id + "\0" + lvl + "\0" + String.join(",", keys);
    }

    public String run(AskLoopState state, String name, AskToolArgs args) {
        if (state == null || name == null || !ALLOWLIST.contains(name)) {
            return "";
        }
        if (args == null) {
            args = argsFrom(state);
        }
        String fp = fingerprint(name, args.itemId, args.dumpLevel, args.variantKeys);
        if (state.alreadyRan(fp)) {
            return state.result(fp);
        }
        if (state.localTools() >= MAX_LOCAL_TOOLS || state.wallExpired()) {
            return "";
        }
        AskTool tool = registry.get(name);
        if (tool == null) {
            return "";
        }
        String out;
        try {
            out = tool.run(args);
        } catch (Throwable t) {
            out = "";
        }
        if (out == null) {
            out = "";
        }
        state.record(name, args.itemId, args.dumpLevel, args.variantKeys, out, true);
        if ("jei_lookup".equals(name)) {
            copyStationTemplateFlag(state);
        }
        return out;
    }

    /**
     * Craft/obtain only. Purpose/idle: no extra tools.
     * Variant JEI prefetch always (if keys unrun). Empty-gate then drains other unrun tools.
     */
    public void drainBeforeFirstLlm(AskLoopState state) {
        if (state == null || state.intent() == AskLoopState.Intent.PURPOSE) {
            return;
        }
        if (state.hasVariantKeys()) {
            AskToolArgs jeiArgs = argsFrom(state, state.dumpLevel(), state.variantKeys());
            run(state, "jei_lookup", jeiArgs);
        }
        if (state.intent() == AskLoopState.Intent.CRAFT) {
            if (state.craftEmpty()) {
                run(state, "guide_fetch", argsFrom(state, "", List.of()));
                if (state.craftEmpty() && AskLoopState.isEmptyOrMiss(state.guideText())) {
                    run(state, "quest_fetch", argsFrom(state, "", List.of()));
                }
            }
        } else if (state.intent() == AskLoopState.Intent.OBTAIN) {
            if (state.obtainEmpty()) {
                run(state, "acquire", argsFrom(state, "FULL", state.variantKeys()));
                run(state, "guide_fetch", argsFrom(state, "", List.of()));
                run(state, "quest_fetch", argsFrom(state, "", List.of()));
                run(state, "consume_use", argsFrom(state, "", List.of()));
            }
        }
        if (state.intentRelevantEmpty()) {
            state.setSkipLlm(true);
        } else {
            state.dropMissPin();
            state.setSkipLlm(false);
        }
    }

    /**
     * PURPOSE / {@code off} / auto+remembered URL → no schema.
     * {@code force} → offer even if URL was remembered.
     */
    public static boolean shouldOfferFirstRoundTools(
            AskLoopState.Intent intent, String mode, boolean urlLacksNative) {
        if (intent == null) {
            return false;
        }
        String m = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        if ("off".equals(m)) {
            return false;
        }
        if ("force".equals(m)) {
            return true;
        }
        return !urlLacksNative;
    }

    /**
     * First LLM round. Capable craft/obtain sends {@link #FIRST_ROUND_TOOLS}.
     * HTTP 400+tools → remember URL, same Ask {@link LlmBridge#askNoTools()}.
     * Empty {@code tool_calls} + text is a valid final answer (caller counts).
     */
    public String firstAsk(AskLoopState state, LlmBridge llm) {
        if (state == null || llm == null) {
            return "";
        }
        boolean offer = shouldOfferFirstRoundTools(
                state.intent(), llm.nativeToolsMode(), llm.noNativeTools());
        if (!offer) {
            return nz(llm.askNoTools());
        }
        return capableLoop(state, llm, CAPABLE_TOOLS);
    }

    /**
     * Every capable turn sends {@code tools}. {@code tool_calls} → run → {@code role:tool}
     * turns on state → another completeWithTools until text / wall / 400 fallback.
     */
    String capableLoop(AskLoopState state, LlmBridge llm, List<String> tools) {
        LlmRound round = llm.completeWithTools(tools);
        if (round == null) {
            return "";
        }
        if (round.httpStatus() == 400 && round.protocolProbe()) {
            llm.rememberNoNativeTools();
            return nz(llm.askNoTools());
        }
        if (round.httpStatus() == 401 || round.httpStatus() == 429) {
            return round.content();
        }
        if (round.httpStatus() >= 400) {
            return round.content();
        }
        int hops = 0;
        while (round != null && round.ok()) {
            if (round.hasToolCalls()) {
                applyNativeCalls(state, round);
                hops++;
                if (!state.canLlm() || hops >= MAX_LLM_ROUNDS) {
                    if (state.canLlm()) {
                        return withCardMarkers(state, nz(llm.askNoTools()));
                    }
                    return withCardMarkers(state, round.content());
                }
                round = llm.completeWithTools(tools);
                continue;
            }
            if (hasEmbeddedToolDump(round.content())) {
                for (AskToolCall call : parseEmbeddedToolCalls(round.content())) {
                    runCall(state, call);
                }
                if (state.canLlm()) {
                    if ("force".equals(nz(llm.nativeToolsMode()).toLowerCase(Locale.ROOT))
                            || !llm.noNativeTools()) {
                        hops++;
                        if (hops < MAX_LLM_ROUNDS && state.canLlm()) {
                            round = llm.completeWithTools(tools);
                            continue;
                        }
                    }
                    return withCardMarkers(state, nz(llm.askNoTools()));
                }
            }
            return withCardMarkers(state, round.content());
        }
        return withCardMarkers(state, round == null ? "" : round.content());
    }

    private static String withCardMarkers(AskLoopState state, String content) {
        String marks = state == null ? "" : state.drainCardMarkers();
        String body = content == null ? "" : content;
        if (marks.isBlank()) {
            return body;
        }
        if (body.contains("[[recipe_card:")) {
            return body;
        }
        return marks + "\n" + body;
    }

    private void applyNativeCalls(AskLoopState state, LlmRound round) {
        state.addToolTurn(ToolChatTurn.assistant(round.content(), round.toolCalls(), round.reasoningContent()));
        int i = 0;
        for (AskToolCall call : round.toolCalls()) {
            String out = runCall(state, call);
            String id = call.toolCallId().isBlank() ? ("call_" + call.name() + "_" + i) : call.toolCallId();
            String resolvedItem = call.itemId().isBlank() ? state.itemId() : call.itemId();
            String body = out == null || out.isBlank()
                    ? LlmClient.toolMissNote(call.name(), resolvedItem)
                    : out;
            state.addToolTurn(ToolChatTurn.tool(id, body));
            i++;
        }
    }

    public String continueAfterAsk(AskLoopState state, String first, LlmBridge llm) {
        if (state == null || llm == null || state.intent() == AskLoopState.Intent.PURPOSE) {
            return first;
        }
        if (state.skipLlm()) {
            return first;
        }
        String answer = first == null ? "" : first;
        AskGrounding.Result g = AskGrounding.check(answer, state);
        if (g.needsLookup() && state.groundingLookups() < 1 && !state.wallExpired()) {
            run(state, g.lookupTool(), g.lookupArgs());
            state.incGroundingLookups();
            if (state.canLlm()) {
                answer = nz(llm.askNoTools());
                state.countSuccessfulLlm();
                g = AskGrounding.check(answer, state);
            }
        }
        if (g.grounded()) {
            return answer;
        }
        List<String> unrun = state.unrunRelated();
        if (unrun.isEmpty() || !state.canLlm()) {
            return answer;
        }
        state.setEscalate(true);
        if (llm.noNativeTools()) {
            return jsonHop(state, llm);
        }
        LlmRound round = llm.completeWithTools(unrun);
        if (round == null) {
            return answer;
        }
        if (round.httpStatus() == 400 && round.protocolProbe()) {
            llm.rememberNoNativeTools();
            if (state.canLlm()) {
                String fallback = nz(llm.askNoTools());
                state.countSuccessfulLlm();
                return fallback;
            }
            return answer;
        }
        if (round.httpStatus() == 401 || round.httpStatus() == 429) {
            return round.content();
        }
        if (round.httpStatus() >= 400) {
            return round.content().isBlank() ? answer : round.content();
        }
        state.countSuccessfulLlm();
        if (round.hasToolCalls()) {
            applyNativeCalls(state, round);
            if (state.canLlm()) {
                LlmRound nextRound = llm.completeWithTools(CAPABLE_TOOLS);
                state.countSuccessfulLlm();
                if (nextRound != null && nextRound.hasToolCalls()) {
                    applyNativeCalls(state, nextRound);
                    if (state.canLlm()) {
                        String next = nz(llm.askNoTools());
                        state.countSuccessfulLlm();
                        return withCardMarkers(state, next);
                    }
                }
                String next = nextRound == null ? "" : nextRound.content();
                return withCardMarkers(state, next.isBlank() ? answer : next);
            }
        }
        if (hasEmbeddedToolDump(round.content())) {
            for (AskToolCall call : parseEmbeddedToolCalls(round.content())) {
                runCall(state, call);
            }
            if (state.canLlm()) {
                String next = nz(llm.askNoTools());
                state.countSuccessfulLlm();
                return withCardMarkers(state, next);
            }
        }
        return withCardMarkers(state, round.content().isBlank() ? answer : round.content());
    }

    private String jsonHop(AskLoopState state, LlmBridge llm) {
        if (!state.canLlm()) {
            return "";
        }
        String hop = nz(llm.askNoTools());
        state.countSuccessfulLlm();
        List<AskToolCall> calls = parseEmbeddedToolCalls(hop);
        if (calls.isEmpty()) {
            return hop;
        }
        for (AskToolCall call : calls) {
            runCall(state, call);
        }
        if (state.canLlm()) {
            String next = nz(llm.askNoTools());
            state.countSuccessfulLlm();
            return next;
        }
        return hop;
    }

    private String runCall(AskLoopState state, AskToolCall call) {
        if (call == null || !ALLOWLIST.contains(call.name())) {
            return "";
        }
        String item = call.itemId().isBlank() ? state.itemId() : call.itemId();
        if (!QUERY_TOOLS.contains(call.name()) && !item.equals(state.itemId())) {
            return "";
        }
        String level = call.dumpLevel().isBlank()
                ? ("jei_lookup".equals(call.name()) ? state.dumpLevel()
                        : "acquire".equals(call.name()) ? "FULL" : "")
                : call.dumpLevel();
        List<String> keys = call.variantKeys().isEmpty()
                ? ("jei_lookup".equals(call.name()) || "acquire".equals(call.name())
                        ? state.variantKeys() : List.of())
                : call.variantKeys();
        AskToolArgs args = new AskToolArgs(
                item, level, keys, state.question(), state.lang(),
                state.gameDir(), state.scanners(), state.deadlineMs(), call.argumentsJson());
        String out = run(state, call.name(), args);
        if (out.isBlank()) {
            state.addModelNote(LlmClient.toolMissNote(call.name(), item));
            return "";
        }
        return out;
    }

    public static boolean hasJsonMarker(String text) {
        return text != null && text.contains(JSON_MARKER);
    }

    public static boolean hasLeakedToolXml(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return DSML_TOKEN.matcher(text).find()
                || TOOL_CALL_XML.matcher(text).find()
                || text.contains("<|tool_call");
    }

    static boolean hasEmbeddedToolDump(String text) {
        return hasJsonMarker(text) || hasLeakedToolXml(text);
    }

    static List<AskToolCall> parseEmbeddedToolCalls(String text) {
        List<AskToolCall> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (AskToolCall c : parseJsonTools(text)) {
            if (seen.add(c.name() + "\0" + c.itemId() + "\0" + c.dumpLevel())) {
                out.add(c);
            }
        }
        for (AskToolCall c : parseLeakedToolXml(text)) {
            if (seen.add(c.name() + "\0" + c.itemId() + "\0" + c.dumpLevel())) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Map hallucinated names onto allowlist. {@code recipe_lookup} + dump-like query
     * → {@code jei_lookup}; otherwise {@code render_recipe_cards}.
     */
    public static AskToolCall canonicalizeCall(
            String name, String item, String dump, String query, List<String> keys,
            String callId, String argsJson) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        String it = item == null ? "" : item.trim();
        String d = dump == null ? "" : dump.trim();
        String q = query == null ? "" : query.trim();
        if (d.isBlank() && isDumpLevel(q)) {
            d = q;
            q = "";
        }
        if ("show_recipe_card".equals(n)) {
            // Retired — map leftover model calls onto render_recipe_cards.
            n = "render_recipe_cards";
            if (d.isBlank()) {
                d = "output";
            }
        }
        if ("recipe_lookup".equals(n) || "lookup_recipe".equals(n) || "get_recipe".equals(n)) {
            if (!q.isBlank() && !isDumpLevel(d)) {
                n = "render_recipe_cards";
                if (d.isBlank()) {
                    d = "output";
                }
                if (argsJson == null || argsJson.isBlank()) {
                    argsJson = "{\"item\":\"" + it + "\",\"role\":\"" + d
                            + "\",\"machine\":\"" + q.replace("\"", "") + "\"}";
                }
            } else {
                n = "jei_lookup";
                if (d.isBlank()) {
                    d = "FULL";
                }
            }
        }
        if (!ALLOWLIST.contains(n)) {
            return null;
        }
        if (!d.isBlank()) {
            String upper = d.toUpperCase(Locale.ROOT);
            if (isDumpLevel(upper)) {
                d = "INFORMATION".equals(upper) ? "INFO" : upper;
            }
        }
        return new AskToolCall(n, it, d, keys == null ? List.of() : keys, callId, argsJson);
    }

    static boolean isDumpLevel(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String u = s.trim().toUpperCase(Locale.ROOT);
        return "FULL".equals(u) || "SLIM".equals(u) || "OUTPUT".equals(u) || "INPUT".equals(u)
                || "INFO".equals(u) || "INFORMATION".equals(u);
    }

    /**
     * DeepSeek DSML / {@code <tool_call>} XML dumped into assistant content.
     * Unknown names dropped after {@link #canonicalizeCall}.
     */
    public static List<AskToolCall> parseLeakedToolXml(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<AskToolCall> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Matcher inv = DSML_INVOKE.matcher(text);
        while (inv.find()) {
            AskToolCall c = callFromDsmlParams(inv.group(1), inv.group(2));
            if (c != null && seen.add(c.name() + "\0" + c.itemId() + "\0" + c.dumpLevel())) {
                out.add(c);
            }
        }
        Matcher tc = TOOL_CALL_XML.matcher(text);
        while (tc.find()) {
            AskToolCall c = callFromJsonChunk(tc.group(1));
            if (c != null && seen.add(c.name() + "\0" + c.itemId() + "\0" + c.dumpLevel())) {
                out.add(c);
            }
        }
        return out;
    }

    private static AskToolCall callFromDsmlParams(String name, String inner) {
        String item = "";
        String dump = "";
        String query = "";
        Matcher p = DSML_PARAM.matcher(inner == null ? "" : inner);
        while (p.find()) {
            String k = p.group(1).trim().toLowerCase(Locale.ROOT);
            String v = p.group(2) == null ? "" : p.group(2).trim();
            switch (k) {
                case "item" -> item = v;
                case "dump_level" -> dump = v;
                case "query" -> query = v;
                case "card_index" -> {
                    if (dump.isBlank()) {
                        dump = v;
                    }
                }
                default -> { }
            }
        }
        return canonicalizeCall(name, item, dump, query, List.of(), "", "");
    }

    private static AskToolCall callFromJsonChunk(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return null;
        }
        Matcher nm = NAME.matcher(chunk);
        String name = nm.find() ? nm.group(1) : "";
        if (name.isBlank()) {
            return null;
        }
        List<String> keys = List.of();
        Matcher km = KEYS.matcher(chunk);
        if (km.find()) {
            List<String> ks = new ArrayList<>();
            Matcher sm = KEY_STR.matcher(km.group(1));
            while (sm.find()) {
                ks.add(sm.group(1));
            }
            keys = ks;
        }
        return canonicalizeCall(name, find(ITEM, chunk), find(DUMP, chunk), find(QUERY, chunk), keys, "", "");
    }

    /**
     * Marker-only JSON. Bare {@code {} } without {@code [[tools]]} is ignored.
     * Unknown names dropped. Only allowlisted tools (after alias map).
     */
    public static List<AskToolCall> parseJsonTools(String text) {
        if (text == null) {
            return List.of();
        }
        int mark = text.indexOf(JSON_MARKER);
        if (mark < 0) {
            return List.of();
        }
        String rest = text.substring(mark + JSON_MARKER.length());
        String obj = extractJsonObject(rest);
        if (obj == null) {
            return List.of();
        }
        int callsAt = obj.indexOf("\"calls\"");
        if (callsAt < 0) {
            return List.of();
        }
        int arr = obj.indexOf('[', callsAt);
        if (arr < 0) {
            return List.of();
        }
        String arrBody = extractJsonArray(obj.substring(arr));
        if (arrBody == null) {
            return List.of();
        }
        List<AskToolCall> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String chunk : splitTopObjects(arrBody)) {
            AskToolCall mapped = callFromJsonChunk(chunk);
            if (mapped == null || !seen.add(mapped.name() + chunk)) {
                continue;
            }
            out.add(mapped);
        }
        return out;
    }

    private static String find(Pattern p, String chunk) {
        Matcher m = p.matcher(chunk);
        return m.find() ? m.group(1) : "";
    }

    static String extractJsonObject(String s) {
        int i = s.indexOf('{');
        if (i < 0) {
            return null;
        }
        return sliceBalanced(s, i, '{', '}');
    }

    static String extractJsonArray(String s) {
        int i = s.indexOf('[');
        if (i < 0) {
            return null;
        }
        return sliceBalanced(s, i, '[', ']');
    }

    private static String sliceBalanced(String s, int from, char open, char close) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int j = from; j < s.length(); j++) {
            char c = s.charAt(j);
            if (inStr) {
                if (esc) {
                    esc = false;
                    continue;
                }
                if (c == '\\') {
                    esc = true;
                    continue;
                }
                if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return s.substring(from, j + 1);
                }
            }
        }
        return null;
    }

    static List<String> splitTopObjects(String arrayInclBrackets) {
        List<String> out = new ArrayList<>();
        if (arrayInclBrackets == null || arrayInclBrackets.length() < 2) {
            return out;
        }
        String inner = arrayInclBrackets.substring(1, arrayInclBrackets.length() - 1);
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        int start = -1;
        for (int j = 0; j < inner.length(); j++) {
            char c = inner.charAt(j);
            if (inStr) {
                if (esc) {
                    esc = false;
                    continue;
                }
                if (c == '\\') {
                    esc = true;
                    continue;
                }
                if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = j;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(inner.substring(start, j + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    /** AskToolEnv is Minecraft-typed; keep loop -ea headless. */
    private static void copyStationTemplateFlag(AskLoopState state) {
        Object env = env();
        if (env == null || state == null) {
            return;
        }
        try {
            var field = env.getClass().getField("jeiStationTemplate");
            state.setJeiStationTemplate(field.getBoolean(env));
        } catch (ReflectiveOperationException ignored) {
            // headless checks have no env
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
