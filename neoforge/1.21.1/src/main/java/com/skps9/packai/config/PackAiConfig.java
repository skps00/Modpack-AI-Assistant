package com.skps9.packai.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.skps9.packai.logic.LlmClient;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config for in-mod AI (no external Bridge required).
 */
public final class PackAiConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> MODE;
    public static final ModConfigSpec.ConfigValue<String> API_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;
    public static final ModConfigSpec.ConfigValue<String> MODEL;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_MODEL;
    public static final ModConfigSpec.IntValue MAX_JEI_CHARS;
    public static final ModConfigSpec.IntValue HISTORY_TURNS;
    public static final ModConfigSpec.IntValue MAX_FACTS;
    public static final ModConfigSpec.BooleanValue ALLOW_WEB_SEARCH;
    public static final ModConfigSpec.ConfigValue<String> TAVILY_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> SERPER_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> SIDEBAR_SIDE;
    /** Which obtain pathway to emphasize: craft | quest | loot | balanced. */
    public static final ModConfigSpec.ConfigValue<String> PREFER_OBTAIN;
    /**
     * Semicolon-separated JEI RecipeType UIDs in display priority (first = highest).
     * Empty = use preferObtain heuristic order.
     */
    public static final ModConfigSpec.ConfigValue<String> RECIPE_CATEGORY_ORDER;
    /** Semicolon-separated JEI RecipeType UIDs hidden from JEI summary / recipe cards. */
    public static final ModConfigSpec.ConfigValue<String> RECIPE_CATEGORY_HIDDEN;
    /**
     * How to attach NBT/component extras on JEI ingredient labels for the LLM:
     * auto (default) | always | never.
     */
    public static final ModConfigSpec.ConfigValue<String> INGREDIENT_NBT_POLICY;
    /**
     * Semicolon-separated substrings; NBT keys / tooltip lines containing these
     * (case-insensitive) are treated as display noise, not craft requirements.
     */
    public static final ModConfigSpec.ConfigValue<String> INGREDIENT_NBT_SKIP_PATTERNS;
    /**
     * Semicolon-separated substrings; under {@code auto} when a bare stack is accepted
     * (sample ≠ full gate), still attach matching keys/lines as likely craft gates.
     */
    public static final ModConfigSpec.ConfigValue<String> INGREDIENT_NBT_KEEP_PATTERNS;
    /** When true, digit-bearing tooltip lines may be treated as requirements (usually noisy). */
    public static final ModConfigSpec.BooleanValue INGREDIENT_TOOLTIP_AS_REQ;
    /**
     * When true, Pack AI may surface FTB/Heracles quests marked hide/invisible/deps-gated.
     * Default false (anti-spoiler).
     */
    public static final ModConfigSpec.BooleanValue SHOW_HIDDEN_QUESTS;
    /** When true, attach “related missions” / quest facts under answers. */
    public static final ModConfigSpec.BooleanValue ATTACH_RELATED_QUESTS;
    /**
     * When true, multi-selected inventory extras may score quest matches.
     * Default false — only focus item + question tokens score quests.
     */
    public static final ModConfigSpec.BooleanValue QUEST_MATCH_HOTBAR;
    /**
     * When true, hide JEI recipes where the focus item registry id appears as both
     * INPUT and OUTPUT (upgrade / anvil-style). Default true.
     */
    public static final ModConfigSpec.BooleanValue HIDE_UPGRADE_RECIPES;
    /**
     * When true, background-scan {@code mods/*.jar} zip entries (recipes / loot_tables)
     * into {@code config/packai/jar-cache/}. Default false — safer for huge packs (NFWC).
     */
    public static final ModConfigSpec.BooleanValue SCAN_MOD_JARS;

    private static final Set<String> MODES = Set.of("auto", "cloud", "ollama", "offline");
    private static final Set<String> SIDEBARS = Set.of("left", "right");
    private static final Set<String> PREFER_OBTAINS = Set.of("craft", "quest", "loot", "balanced");
    private static final Set<String> INGREDIENT_NBT_POLICIES = Set.of("auto", "always", "never");
    /** Default skip list — generic storage/display noise, not mod brand names. */
    public static final String DEFAULT_INGREDIENT_NBT_SKIP =
            "energy;eu;fe;rf;mana;stored;capacity;eterna;durability;maxdamage;"
                    + "uuid;uid;color;texture;model;timestamp;hash;seed;damage";
    /**
     * Default keep list — semantic “gate-shaped” substrings only (not mod/pack brands).
     * Example hits: killCount, ProudSoul, refine, organData, level/tier/stage… Pack-specific
     * ids like slashblade / chestcavity are covered by {@link IngredientReqHints} namespaced-key heuristic.
     */
    public static final String DEFAULT_INGREDIENT_NBT_KEEP =
            "kill;soul;refine;level;rank;tier;stage;progress;score;grade;quality;purity;"
                    + "upgrade;forge;blood;organ;times;combo;special;"
                    + "擊殺;等級;階段;進度;洗練;品質;器官";

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("llm");
        MODE = b.comment(
                        "LLM backend: auto | cloud | ollama | offline.",
                        "auto = key→cloud else Ollama else local; cloud/ollama force that backend; offline = no LLM.")
                .define("mode", "auto");
        API_BASE_URL = b.comment("OpenAI-compatible API base (used in cloud / auto with key).")
                .define("apiBaseUrl", "https://api.openai.com/v1");
        API_KEY = b.comment(
                        "Cloud API key (sk-...). Prefer Mods → Packai settings screen (full paste);",
                        "or edit packai-client.toml / env PACKAI_API_KEY. Avoid NeoForge default string box.")
                .define("apiKey", "");
        MODEL = b.comment("Model id for cloud API.")
                .define("model", "gpt-4o-mini");
        OLLAMA_BASE_URL = b.comment("Local Ollama OpenAI-compatible base.")
                .define("ollamaBaseUrl", "http://127.0.0.1:11434/v1");
        OLLAMA_MODEL = b.comment("Ollama model name.")
                .define("ollamaModel", "llama3.2");
        b.pop();
        b.push("token");
        MAX_JEI_CHARS = b.comment(
                        "Max characters of JEI text sent to the LLM (largest token cost).",
                        "Lower = cheaper; 2000–4000 is usually enough.")
                .defineInRange("maxJeiChars", 12000, 1000, 12000);
        HISTORY_TURNS = b.comment(
                        "How many recent chat messages to send as LLM history (0 = question only).")
                .defineInRange("historyTurns", 8, 0, 16);
        MAX_FACTS = b.comment(
                        "Max local/quest/web fact lines packed into the LLM prompt.")
                .defineInRange("maxFacts", 24, 4, 32);
        b.pop();
        b.push("web");
        ALLOW_WEB_SEARCH = b.comment(
                        "Allow Minecraft-mod web search (Modrinth + Minecraft Wiki by default, no key).",
                        "Optional Tavily / Serper keys override for broader web results.",
                        "Still runs when the item has local script/loot overrides; LLM must prefer local on conflict.")
                .define("allowWebSearch", true);
        TAVILY_API_KEY = b.comment("Optional Tavily API key (preferred paid search). Or env TAVILY_API_KEY / PACKAI_TAVILY_API_KEY.")
                .define("tavilyApiKey", "");
        SERPER_API_KEY = b.comment("Optional Serper API key (fallback paid search). Or env SERPER_API_KEY / PACKAI_SERPER_API_KEY.")
                .define("serperApiKey", "");
        b.pop();
        b.push("ui");
        SIDEBAR_SIDE = b.comment(
                        "Assistant action buttons side: left | right (default right).",
                        "Keeps the chat column tall for long answers.")
                .define("sidebarSide", "right");
        PREFER_OBTAIN = b.comment(
                        "Which obtain pathway to emphasize when recommending how to get an item:",
                        "craft (JEI/recipes, default) | quest | loot (drops/fish/trade) | balanced.",
                        "Legacy aliases: last→craft, first→quest, normal→balanced.")
                .define("preferObtain", "craft");
        RECIPE_CATEGORY_ORDER = b.comment(
                        "JEI recipe category UIDs in priority order (semicolon-separated).",
                        "Empty = default heuristic from preferObtain. Edit via Mods → Pack AI → Recipe categories.")
                .define("recipeCategoryOrder", "");
        RECIPE_CATEGORY_HIDDEN = b.comment(
                        "JEI recipe category UIDs to hide from summaries/cards (semicolon-separated).",
                        "Edit via Mods → Pack AI → Recipe categories.")
                .define("recipeCategoryHidden", "");
        INGREDIENT_NBT_POLICY = b.comment(
                        "JEI ingredient NBT labels for the LLM: auto | always | never.",
                        "auto = bare Ingredient accepted → only keep-pattern extras (sample may still be a gate);",
                        "        else → all extras minus skip (NBT is a hard match).",
                        "always = always attach filtered extras; never = names only.",
                        "Same pack can mix sample≠gate (Apotheosis stats) and sample=gate (kill/proud).")
                .define("ingredientNbtPolicy", "auto");
        INGREDIENT_NBT_SKIP_PATTERNS = b.comment(
                        "Semicolon-separated substrings; matching NBT keys/tooltip lines are skipped",
                        "(case-insensitive). Edit to tune false positives; empty uses the built-in default.")
                .define("ingredientNbtSkipPatterns", DEFAULT_INGREDIENT_NBT_SKIP);
        INGREDIENT_NBT_KEEP_PATTERNS = b.comment(
                        "Semicolon-separated substrings; under auto when bare stack is accepted,",
                        "still attach matching keys/tooltip lines as likely craft gates",
                        "(semantic roles: kill/soul/level/score/organ/… — not mod brand names).",
                        "Namespaced keys like modid:attr are also kept by heuristic. Skip wins if both match.",
                        "Empty uses the built-in default; built-in defaults are always unioned in.")
                .define("ingredientNbtKeepPatterns", DEFAULT_INGREDIENT_NBT_KEEP);
        INGREDIENT_TOOLTIP_AS_REQ = b.comment(
                        "If true, digit-bearing tooltip lines may be treated as craft requirements.",
                        "Default false — JEI sample tooltips (energy, machine stats) are usually not ingredients.",
                        "Under auto keep-only, keep-pattern tooltip lines are still attached without this flag.")
                .define("ingredientTooltipAsReq", false);
        SHOW_HIDDEN_QUESTS = b.comment(
                        "If true, allow Pack AI to mention FTB Quests / Heracles marked hide, invisible,",
                        "or dependency-gated (hide_quest_until_deps_visible / invisible_until_tasks).",
                        "Default false — anti-spoiler: match quest book visibility for guessing packs.")
                .define("showHiddenQuests", false);
        ATTACH_RELATED_QUESTS = b.comment(
                        "If true, attach related quest-book missions / quest fact lines under answers.",
                        "Default true. Off = no quest matching for side panels or prompt facts.")
                .define("attachRelatedQuests", true);
        QUEST_MATCH_HOTBAR = b.comment(
                        "If true, multi-selected inventory extras may score quest matches (often noisy).",
                        "Default false — only focus item + question tokens score quests.",
                        "Legacy key name questMatchHotbar.")
                .define("questMatchHotbar", false);
        HIDE_UPGRADE_RECIPES = b.comment(
                        "If true, hide JEI recipes where the focus item (same registry id) is both",
                        "an INPUT and an OUTPUT — typical upgrade / arcane-anvil style recipes.",
                        "Default true. Set false to show those recipes in Ask cards / JEI summary.")
                .define("hideUpgradeRecipes", true);
        SCAN_MOD_JARS = b.comment(
                        "If true, background-scan mods/*.jar zip entries (data/**/recipes|loot_tables)",
                        "into config/packai/jar-cache/ and inject short [JAR] hints into Ask.",
                        "Default false — safer for huge packs; enable in Mods → Pack AI → Ask.",
                        "No decompile; unchanged jars skipped via zip central-dir SHA-256 fingerprint.")
                .define("scanModJars", false);
        b.pop();
        SPEC = b.build();
    }

    /** Master switch for web search (free Modrinth/Wiki and/or keyed providers). */
    public static boolean webSearchEnabled() {
        return Boolean.TRUE.equals(ALLOW_WEB_SEARCH.get());
    }

    public static void setWebSearchEnabled(boolean enabled) {
        ALLOW_WEB_SEARCH.set(enabled);
        SPEC.save();
    }

    public static void setTavilyApiKey(String key) {
        TAVILY_API_KEY.set(LlmClient.sanitizeApiKey(key));
        SPEC.save();
    }

    public static void setSerperApiKey(String key) {
        SERPER_API_KEY.set(LlmClient.sanitizeApiKey(key));
        SPEC.save();
    }

    public static int maxJeiChars() {
        Integer v = MAX_JEI_CHARS.get();
        return v == null ? 12000 : Math.max(1000, Math.min(12000, v));
    }

    public static int historyTurns() {
        Integer v = HISTORY_TURNS.get();
        return v == null ? 8 : Math.max(0, Math.min(16, v));
    }

    public static int maxFacts() {
        Integer v = MAX_FACTS.get();
        return v == null ? 24 : Math.max(4, Math.min(32, v));
    }

    public static void setMaxJeiChars(int chars) {
        MAX_JEI_CHARS.set(Math.max(1000, Math.min(12000, chars)));
        SPEC.save();
    }

    public static void setHistoryTurns(int turns) {
        HISTORY_TURNS.set(Math.max(0, Math.min(16, turns)));
        SPEC.save();
    }

    public static void setMaxFacts(int facts) {
        MAX_FACTS.set(Math.max(4, Math.min(32, facts)));
        SPEC.save();
    }

    /** Normalized UI sidebar: {@code left} or {@code right}. */
    public static String sidebarSide() {
        String raw = SIDEBAR_SIDE.get();
        if (raw == null || raw.isBlank()) {
            return "right";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return SIDEBARS.contains(s) ? s : "right";
    }

    public static boolean sidebarOnRight() {
        return "right".equals(sidebarSide());
    }

    public static void setSidebarSide(String side) {
        String s = side == null ? "right" : side.trim().toLowerCase(Locale.ROOT);
        SIDEBAR_SIDE.set(SIDEBARS.contains(s) ? s : "right");
        SPEC.save();
    }

    /**
     * Preferred obtain pathway for recommendations:
     * {@code craft} (default), {@code quest}, {@code loot}, or {@code balanced}.
     */
    public static String preferObtain() {
        String raw = PREFER_OBTAIN.get();
        if (raw == null || raw.isBlank()) {
            return "craft";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        // Legacy questObtainPriority values
        if ("last".equals(s)) {
            return "craft";
        }
        if ("first".equals(s)) {
            return "quest";
        }
        if ("normal".equals(s)) {
            return "balanced";
        }
        return PREFER_OBTAINS.contains(s) ? s : "craft";
    }

    public static void setPreferObtain(String path) {
        String s = path == null ? "craft" : path.trim().toLowerCase(Locale.ROOT);
        if ("last".equals(s)) {
            s = "craft";
        } else if ("first".equals(s)) {
            s = "quest";
        } else if ("normal".equals(s)) {
            s = "balanced";
        }
        PREFER_OBTAIN.set(PREFER_OBTAINS.contains(s) ? s : "craft");
        SPEC.save();
    }

    /** Ordered JEI RecipeType UIDs; empty means no custom order. */
    public static List<String> recipeCategoryOrder() {
        return splitUidList(RECIPE_CATEGORY_ORDER.get());
    }

    /** Hidden JEI RecipeType UIDs. */
    public static Set<String> recipeCategoryHidden() {
        return new LinkedHashSet<>(splitUidList(RECIPE_CATEGORY_HIDDEN.get()));
    }

    public static boolean hasRecipeCategoryPrefs() {
        return !recipeCategoryOrder().isEmpty() || !recipeCategoryHidden().isEmpty();
    }

    /**
     * Persist category drag-order and visibility.
     *
     * @param order  full UID order (enabled + hidden keep their slots)
     * @param hidden UIDs that should not appear in JEI summaries / cards
     */
    public static void setRecipeCategoryPrefs(List<String> order, Set<String> hidden) {
        List<String> cleanOrder = sanitizeUidList(order);
        Set<String> cleanHidden = new LinkedHashSet<>();
        if (hidden != null) {
            for (String h : hidden) {
                String s = sanitizeUid(h);
                if (!s.isEmpty()) {
                    cleanHidden.add(s);
                }
            }
        }
        RECIPE_CATEGORY_ORDER.set(String.join(";", cleanOrder));
        RECIPE_CATEGORY_HIDDEN.set(String.join(";", cleanHidden));
        SPEC.save();
    }

    /** Clear custom order + hidden (back to preferObtain heuristic, all visible). */
    public static void resetRecipeCategoryPrefs() {
        RECIPE_CATEGORY_ORDER.set("");
        RECIPE_CATEGORY_HIDDEN.set("");
        SPEC.save();
    }

    /** {@code auto} (default), {@code always}, or {@code never}. */
    public static String ingredientNbtPolicy() {
        String raw = INGREDIENT_NBT_POLICY.get();
        if (raw == null || raw.isBlank()) {
            return "auto";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return INGREDIENT_NBT_POLICIES.contains(s) ? s : "auto";
    }

    public static void setIngredientNbtPolicy(String policy) {
        String s = policy == null ? "auto" : policy.trim().toLowerCase(Locale.ROOT);
        INGREDIENT_NBT_POLICY.set(INGREDIENT_NBT_POLICIES.contains(s) ? s : "auto");
        SPEC.save();
    }

    /** Skip substrings for NBT keys / tooltip lines (lowercased, non-empty). */
    public static List<String> ingredientNbtSkipPatterns() {
        return splitPatternList(INGREDIENT_NBT_SKIP_PATTERNS.get(), DEFAULT_INGREDIENT_NBT_SKIP);
    }

    /** Keep substrings for likely craft gates under auto+bare (lowercased, non-empty). */
    public static List<String> ingredientNbtKeepPatterns() {
        // Union config with built-in defaults so upgrades pick up new gate families (organs, etc.).
        LinkedHashSet<String> out = new LinkedHashSet<>(
                splitPatternList(INGREDIENT_NBT_KEEP_PATTERNS.get(), DEFAULT_INGREDIENT_NBT_KEEP));
        for (String part : DEFAULT_INGREDIENT_NBT_KEEP.split(";")) {
            String s = part.trim().toLowerCase(Locale.ROOT);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    private static List<String> splitPatternList(String raw, String defaultList) {
        if (raw == null || raw.isBlank()) {
            raw = defaultList;
        }
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String part : raw.split(";")) {
            String s = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (!s.isEmpty() && seen.add(s)) {
                out.add(s);
            }
        }
        if (out.isEmpty()) {
            for (String part : defaultList.split(";")) {
                out.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    public static boolean ingredientTooltipAsReq() {
        return Boolean.TRUE.equals(INGREDIENT_TOOLTIP_AS_REQ.get());
    }

    public static void setIngredientTooltipAsReq(boolean enabled) {
        INGREDIENT_TOOLTIP_AS_REQ.set(enabled);
        SPEC.save();
    }

    /** Default false: do not surface hidden/secret FTB/Heracles quests. */
    public static boolean showHiddenQuests() {
        return Boolean.TRUE.equals(SHOW_HIDDEN_QUESTS.get());
    }

    public static void setShowHiddenQuests(boolean enabled) {
        SHOW_HIDDEN_QUESTS.set(enabled);
        SPEC.save();
    }

    /** Default true: related quests may be attached / used as facts. */
    public static boolean attachRelatedQuests() {
        return Boolean.TRUE.equals(ATTACH_RELATED_QUESTS.get());
    }

    public static void setAttachRelatedQuests(boolean enabled) {
        ATTACH_RELATED_QUESTS.set(enabled);
        SPEC.save();
    }

    /** Default false: selected extras do not score quest matches. */
    public static boolean questMatchHotbar() {
        return Boolean.TRUE.equals(QUEST_MATCH_HOTBAR.get());
    }

    public static void setQuestMatchHotbar(boolean enabled) {
        QUEST_MATCH_HOTBAR.set(enabled);
        SPEC.save();
    }

    /** Default true: skip upgrade-style JEI recipes (focus id in both INPUT and OUTPUT). */
    public static boolean hideUpgradeRecipes() {
        return Boolean.TRUE.equals(HIDE_UPGRADE_RECIPES.get());
    }

    public static void setHideUpgradeRecipes(boolean enabled) {
        HIDE_UPGRADE_RECIPES.set(enabled);
        SPEC.save();
    }

    /** Default false: skip heavy mods/*.jar light index (safer for huge packs). */
    public static boolean scanModJars() {
        return Boolean.TRUE.equals(SCAN_MOD_JARS.get());
    }

    public static void setScanModJars(boolean enabled) {
        SCAN_MOD_JARS.set(enabled);
        SPEC.save();
    }

    private static List<String> splitUidList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String part : raw.split(";")) {
            String s = sanitizeUid(part);
            if (!s.isEmpty() && seen.add(s)) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    private static List<String> sanitizeUidList(List<String> order) {
        if (order == null || order.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String id : order) {
            String s = sanitizeUid(id);
            if (!s.isEmpty() && seen.add(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private static String sanitizeUid(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    /** Normalized mode: auto, cloud, ollama, or offline. */
    public static String resolvedMode() {
        String raw = MODE.get();
        if (raw == null || raw.isBlank()) {
            return "auto";
        }
        String m = raw.trim().toLowerCase(Locale.ROOT);
        return MODES.contains(m) ? m : "auto";
    }

    /** Persist mode from GUI; invalid values become auto. */
    public static void setMode(String mode) {
        String m = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        MODE.set(MODES.contains(m) ? m : "auto");
        SPEC.save();
    }

    /** Persist cloud model id from GUI. */
    public static void setCloudModel(String model) {
        String m = model == null ? "" : model.trim();
        if (!m.isEmpty()) {
            MODEL.set(m);
            SPEC.save();
        }
    }

    /** Persist Ollama model name from GUI. */
    public static void setOllamaModel(String model) {
        String m = model == null ? "" : model.trim();
        if (!m.isEmpty()) {
            OLLAMA_MODEL.set(m);
            SPEC.save();
        }
    }

    /**
     * auto：無 key 視為會走 Ollama，模型 UI 應改 ollamaModel；否則改 cloud model。
     */
    public static boolean uiUsesOllamaModel() {
        String mode = resolvedMode();
        if ("ollama".equals(mode)) {
            return true;
        }
        if ("cloud".equals(mode) || "offline".equals(mode)) {
            return false;
        }
        return LlmClient.resolveApiKey().isEmpty();
    }

    public static String uiModel() {
        if (uiUsesOllamaModel()) {
            String m = OLLAMA_MODEL.get();
            return m == null || m.isBlank() ? "llama3.2" : m.trim();
        }
        String m = MODEL.get();
        return m == null || m.isBlank() ? "gpt-4o-mini" : m.trim();
    }

    public static void setUiModel(String model) {
        if (uiUsesOllamaModel()) {
            setOllamaModel(model);
        } else {
            setCloudModel(model);
        }
    }

    /**
     * Persist API key from in-game assistant box (max length friendly).
     * Empty string clears the config key.
     */
    public static void setApiKey(String key) {
        String cleaned = LlmClient.sanitizeApiKey(key);
        API_KEY.set(cleaned);
        SPEC.save();
    }

    private PackAiConfig() {}
}
