package com.skps9.packai.logic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/**
 * D6=C — standard unlock protocols (#1B) + KubeJS advancement cancel/ritual heuristic (#1C).
 *
 * <p>#1B sources (accuracy &gt; completeness; missing soft-dep → silent empty):
 * <ul>
 *   <li>RecipeStages {@code IStagedRecipe#getStage()} (GameStages progression via recipe wrap)</li>
 *   <li>Vanilla / datapack advancements that <em>reward</em> the recipe id and have a display title
 *       (skips hidden recipe-book unlockers with no display — those would spam every craft)</li>
 * </ul>
 *
 * <p>#1C: generic KubeJS shape {@code isAdvancementDone} near {@code event.cancel()} in a
 * recipe-id handler ({@code 'mod:id': function (event) {…}}). Literal advancement ids →
 * {@link Kind#ADVANCEMENT}; table/variable-only checks → {@link Kind#UNKNOWN}. No pack-specific
 * table-name allowlist (ignore D / mrqxAdvancementsCheck).
 */
public final class RecipeUnlockGates {
    private static final int MAX_GATES = 4;
    private static final String RECIPE_STAGES_MOD = "recipestages";
    /** Stable sentinel; {@link #formatGateLabel} localizes via ReplyLang. */
    static final String UNKNOWN_ADV_SENTINEL = "unknown_advancement_gate";

    /**
     * {@code 'mod:recipe_id': function (} — ritual start / complete strategy maps, etc.
     * Does not hardcode pack table names.
     */
    private static final Pattern KUBE_HANDLER_KEY = Pattern.compile(
            "['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]\\s*:\\s*function\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IS_ADVANCEMENT_DONE = Pattern.compile(
            "(?i)\\.isAdvancementDone\\s*\\(");
    private static final Pattern EVENT_CANCEL = Pattern.compile(
            "(?i)\\bevent\\.cancel\\s*\\(\\s*\\)");
    /** Literal id only — skip table[key] / variable refs (those → UNKNOWN). */
    private static final Pattern ADV_LITERAL = Pattern.compile(
            "(?i)\\.isAdvancementDone\\s*\\(\\s*['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]\\s*\\)");

    private static volatile Object advCacheLevelKey;
    private static volatile Map<String, List<String>> recipeToAdvTitles = Map.of();
    /** recipe id (lower) → gates from #1C script heuristic. */
    private static final ConcurrentHashMap<String, List<Gate>> kubeJsGatesByRecipe =
            new ConcurrentHashMap<>();

    private RecipeUnlockGates() {}

    public enum Kind {
        STAGE,
        ADVANCEMENT,
        /** #1C: pattern hit but no reliable advancement literal. */
        UNKNOWN
    }

    public record Gate(Kind kind, String label) {
        public Gate {
            kind = kind == null ? Kind.UNKNOWN : kind;
            label = label == null ? "" : label.trim();
        }

        public boolean isEmpty() {
            return label.isEmpty();
        }
    }

    /** Labels for {@link FormatRequirements} unlock section (no prefix — formatter adds it). */
    public static List<String> labels(List<Gate> gates) {
        if (gates == null || gates.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (Gate g : gates) {
            if (g == null || g.isEmpty()) {
                continue;
            }
            String line = formatGateLabel(g);
            if (line.isEmpty() || !seen.add(line.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(line);
            if (out.size() >= MAX_GATES) {
                break;
            }
        }
        return List.copyOf(out);
    }

    /**
     * Collect unlock gates for a JEI / vanilla recipe object (or Neo {@code RecipeHolder}).
     * Never throws; returns empty when nothing reliable is found.
     */
    public static List<Gate> forRecipe(Object recipe) {
        if (recipe == null) {
            return List.of();
        }
        try {
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            List<Gate> out = new ArrayList<>();

            Object target = unwrapRecipe(recipe);
            addStageGates(target, seen, out);
            if (target != recipe) {
                addStageGates(recipe, seen, out);
            }

            String recipeId = recipeIdOf(recipe);
            if (recipeId.isEmpty()) {
                recipeId = recipeIdOf(target);
            }
            if (!recipeId.isEmpty()) {
                for (String title : advancementTitlesForRecipe(recipeId)) {
                    addGate(out, seen, new Gate(Kind.ADVANCEMENT, title));
                    if (out.size() >= MAX_GATES) {
                        break;
                    }
                }
                for (Gate g : kubeJsGatesForRecipe(recipeId)) {
                    addGate(out, seen, g);
                    if (out.size() >= MAX_GATES) {
                        break;
                    }
                }
            }
            return List.copyOf(out);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    /** Convenience: labels only. */
    public static List<String> labelsForRecipe(Object recipe) {
        return labels(forRecipe(recipe));
    }

    /** Clear #1C cache (PackIndex rebuild). */
    public static void clearKubeJsGates() {
        kubeJsGatesByRecipe.clear();
    }

    /**
     * Merge #1C gates from one KubeJS / script file into the recipe→gate index.
     * Safe to call repeatedly; prefers miss over inventing table contents.
     */
    public static void ingestKubeJs(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            Map<String, List<Gate>> parsed = parseKubeJsAdvancementGates(text);
            for (Map.Entry<String, List<Gate>> e : parsed.entrySet()) {
                mergeKubeGates(e.getKey(), e.getValue());
            }
        } catch (Throwable ignored) {
            // accuracy > completeness
        }
    }

    /** Package/tests: recipe id → gates from last ingest(s). */
    static List<Gate> kubeJsGatesForRecipe(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return List.of();
        }
        List<Gate> g = kubeJsGatesByRecipe.get(recipeId.toLowerCase(Locale.ROOT));
        return g == null ? List.of() : g;
    }

    /**
     * Pure #1C heuristic (no I/O). Keys are recipe ids from {@code 'id': function (} handlers
     * whose body has both {@code isAdvancementDone} and {@code event.cancel()}.
     */
    static Map<String, List<Gate>> parseKubeJsAdvancementGates(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        LinkedHashMap<String, List<Gate>> out = new LinkedHashMap<>();
        Matcher keyM = KUBE_HANDLER_KEY.matcher(text);
        int guard = 0;
        while (keyM.find() && guard++ < 200) {
            String recipeId = keyM.group(1).toLowerCase(Locale.ROOT);
            String body = extractBalancedBody(text, keyM.end());
            if (body == null || body.length() < 12) {
                continue;
            }
            if (!IS_ADVANCEMENT_DONE.matcher(body).find()) {
                continue;
            }
            if (!EVENT_CANCEL.matcher(body).find()) {
                continue;
            }
            List<Gate> gates = gatesFromAdvancementBody(body);
            if (gates.isEmpty()) {
                continue;
            }
            out.put(recipeId, gates);
        }
        return Collections.unmodifiableMap(out);
    }

    /** Literal advancement ids → ADVANCEMENT; else one UNKNOWN sentinel. */
    static List<Gate> gatesFromAdvancementBody(String body) {
        LinkedHashSet<String> literals = new LinkedHashSet<>();
        Matcher m = ADV_LITERAL.matcher(body);
        int n = 0;
        while (m.find() && n++ < MAX_GATES) {
            String id = m.group(1).toLowerCase(Locale.ROOT).trim();
            if (!id.isEmpty()) {
                literals.add(id);
            }
        }
        if (!literals.isEmpty()) {
            List<Gate> out = new ArrayList<>();
            for (String id : literals) {
                out.add(new Gate(Kind.ADVANCEMENT, id));
                if (out.size() >= MAX_GATES) {
                    break;
                }
            }
            return List.copyOf(out);
        }
        // Pattern hit (isAdvancementDone + cancel) but only table/var refs — honest unknown.
        return List.of(new Gate(Kind.UNKNOWN, UNKNOWN_ADV_SENTINEL));
    }

    private static void mergeKubeGates(String recipeId, List<Gate> gates) {
        if (recipeId == null || recipeId.isBlank() || gates == null || gates.isEmpty()) {
            return;
        }
        String key = recipeId.toLowerCase(Locale.ROOT);
        kubeJsGatesByRecipe.compute(key, (k, prev) -> {
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            List<Gate> merged = new ArrayList<>();
            if (prev != null) {
                for (Gate g : prev) {
                    addGate(merged, seen, g);
                }
            }
            for (Gate g : gates) {
                addGate(merged, seen, g);
            }
            return List.copyOf(merged);
        });
    }

    /**
     * Body of {@code function (…) { … }} starting after the {@code (} that ends the matcher.
     * Caps scan length to avoid runaway on huge files.
     */
    static String extractBalancedBody(String text, int afterOpenParen) {
        if (text == null || afterOpenParen < 0 || afterOpenParen >= text.length()) {
            return null;
        }
        int brace = text.indexOf('{', afterOpenParen);
        if (brace < 0 || brace - afterOpenParen > 80) {
            return null;
        }
        int depth = 0;
        int max = Math.min(text.length(), brace + 12_000);
        for (int i = brace; i < max; i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(brace + 1, i);
                }
            }
        }
        return null;
    }

    static String formatGateLabel(Gate gate) {
        if (gate == null || gate.isEmpty()) {
            return "";
        }
        String label = Plainify.stripMcFormat(gate.label()).trim();
        if (label.isEmpty() || label.length() > 96) {
            return "";
        }
        return switch (gate.kind()) {
            case STAGE -> label;
            case ADVANCEMENT -> label;
            case UNKNOWN -> {
                if (UNKNOWN_ADV_SENTINEL.equalsIgnoreCase(label)) {
                    yield ReplyLang.unknownAdvancementGate(ReplyLang.current());
                }
                yield label;
            }
        };
    }

    private static void addStageGates(Object recipe, LinkedHashSet<String> seen, List<Gate> out) {
        if (recipe == null || out.size() >= MAX_GATES) {
            return;
        }
        // Soft-dep: only probe getStage when RecipeStages present (or method exists on wrapper).
        if (!recipeStagesPresent() && !hasGetStage(recipe)) {
            return;
        }
        String stage = readStage(recipe);
        if (stage.isEmpty()) {
            return;
        }
        addGate(out, seen, new Gate(Kind.STAGE, stage));
    }

    private static boolean recipeStagesPresent() {
        try {
            return ModList.get().isLoaded(RECIPE_STAGES_MOD);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasGetStage(Object recipe) {
        try {
            recipe.getClass().getMethod("getStage");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String readStage(Object recipe) {
        Object stage = invokeNoArg(recipe, "getStage");
        if (stage instanceof String s) {
            return s.trim();
        }
        return "";
    }

    private static Object unwrapRecipe(Object recipe) {
        if (recipe == null) {
            return null;
        }
        // Neo RecipeHolder#value / RecipeStages IStagedRecipe#getRecipe
        Object inner = invokeNoArg(recipe, "value", "getRecipe");
        return inner != null ? inner : recipe;
    }

    static String recipeIdOf(Object recipe) {
        if (recipe == null) {
            return "";
        }
        // Neo 1.21: RecipeHolder#id / getId — Recipe itself has no getId().
        Object id = invokeNoArg(recipe, "id", "getId");
        if (id instanceof ResourceLocation rl) {
            return rl.toString().toLowerCase(Locale.ROOT);
        }
        if (id != null) {
            return id.toString().trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static List<String> advancementTitlesForRecipe(String recipeId) {
        if (recipeId == null || recipeId.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> map = advancementRecipeIndex();
        List<String> titles = map.get(recipeId.toLowerCase(Locale.ROOT));
        return titles == null ? List.of() : titles;
    }

    private static Map<String, List<String>> advancementRecipeIndex() {
        Minecraft mc = Minecraft.getInstance();
        Object key = mc.level;
        if (key != null && key == advCacheLevelKey && !recipeToAdvTitles.isEmpty()) {
            return recipeToAdvTitles;
        }
        Map<String, List<String>> built = buildAdvancementRecipeIndex(mc);
        advCacheLevelKey = key;
        recipeToAdvTitles = built;
        return built;
    }

    /** Visible for tests — only keep advancements that expose a display title. */
    static boolean shouldEmitAdvancementGate(boolean hasDisplay, String title) {
        if (!hasDisplay) {
            return false;
        }
        return title != null && !title.isBlank();
    }

    private static Map<String, List<String>> buildAdvancementRecipeIndex(Minecraft mc) {
        LinkedHashMap<String, List<String>> out = new LinkedHashMap<>();
        Collection<?> advancements = loadAdvancements(mc);
        if (advancements == null || advancements.isEmpty()) {
            return Map.of();
        }
        for (Object raw : advancements) {
            try {
                indexOneAdvancement(raw, out);
            } catch (Throwable ignored) {
                // keep scanning
            }
        }
        if (out.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : out.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static void indexOneAdvancement(Object raw, Map<String, List<String>> out) {
        Object adv = raw;
        // Neo AdvancementHolder → value()
        Object value = invokeNoArg(raw, "value");
        if (value != null) {
            adv = value;
        }

        DisplayInfo display = readDisplay(adv);
        if (display == null) {
            return;
        }
        String title = Plainify.stripMcFormat(display.getTitle().getString()).trim();
        if (!shouldEmitAdvancementGate(true, title)) {
            return;
        }

        for (String recipeId : readRewardRecipeIds(adv)) {
            if (recipeId.isEmpty()) {
                continue;
            }
            out.computeIfAbsent(recipeId, k -> new ArrayList<>());
            List<String> titles = out.get(recipeId);
            String key = title.toLowerCase(Locale.ROOT);
            boolean dup = false;
            for (String t : titles) {
                if (t.toLowerCase(Locale.ROOT).equals(key)) {
                    dup = true;
                    break;
                }
            }
            if (!dup && titles.size() < MAX_GATES) {
                titles.add(title);
            }
        }
    }

    private static DisplayInfo readDisplay(Object adv) {
        // Neo 1.21 Advancement is a record — use reflection / Optional display().
        Object display = invokeNoArg(adv, "display", "getDisplay");
        if (display instanceof DisplayInfo info) {
            return info;
        }
        if (display instanceof java.util.Optional<?> opt) {
            Object v = opt.orElse(null);
            return v instanceof DisplayInfo info2 ? info2 : null;
        }
        return null;
    }

    private static List<String> readRewardRecipeIds(Object adv) {
        Object rewards = invokeNoArg(adv, "getRewards", "rewards");
        if (rewards == null) {
            return List.of();
        }
        // Public getter if present
        Object recipes = invokeNoArg(rewards, "getRecipes", "recipes");
        List<String> ids = resourceIds(recipes);
        if (!ids.isEmpty()) {
            return ids;
        }
        // 1.19 AdvancementRewards.recipes is private ResourceLocation[]
        Object field = readField(rewards, "recipes");
        return resourceIds(field);
    }

    private static List<String> resourceIds(Object recipes) {
        if (recipes == null) {
            return List.of();
        }
        if (recipes instanceof ResourceLocation[] arr) {
            List<String> out = new ArrayList<>(arr.length);
            for (ResourceLocation rl : arr) {
                if (rl != null) {
                    out.add(rl.toString().toLowerCase(Locale.ROOT));
                }
            }
            return out;
        }
        if (recipes instanceof Collection<?> col) {
            List<String> out = new ArrayList<>();
            for (Object o : col) {
                if (o instanceof ResourceLocation rl) {
                    out.add(rl.toString().toLowerCase(Locale.ROOT));
                } else if (o != null) {
                    String s = o.toString().trim().toLowerCase(Locale.ROOT);
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
            return out;
        }
        if (recipes instanceof java.util.Optional<?> opt) {
            return resourceIds(opt.orElse(null));
        }
        return List.of();
    }

    private static Collection<?> loadAdvancements(Minecraft mc) {
        if (mc == null) {
            return List.of();
        }
        try {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                Object mgr = server.getAdvancements();
                Object all = invokeNoArg(mgr, "getAllAdvancements");
                if (all instanceof Collection<?> c && !c.isEmpty()) {
                    return c;
                }
            }
        } catch (Throwable ignored) {
            // fall through to client list
        }
        try {
            if (mc.getConnection() == null) {
                return List.of();
            }
            Object clientAdvs = mc.getConnection().getAdvancements();
            Object list = readField(clientAdvs, "advancements");
            if (list == null) {
                Object all = invokeNoArg(clientAdvs, "getAllAdvancements");
                return all instanceof Collection<?> c ? c : List.of();
            }
            Object all = invokeNoArg(list, "getAllAdvancements");
            return all instanceof Collection<?> c ? c : List.of();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static void addGate(List<Gate> out, LinkedHashSet<String> seen, Gate gate) {
        if (gate == null || gate.isEmpty() || out.size() >= MAX_GATES) {
            return;
        }
        String line = formatGateLabel(gate);
        if (line.isEmpty() || !seen.add(line.toLowerCase(Locale.ROOT))) {
            return;
        }
        out.add(new Gate(gate.kind(), line));
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
