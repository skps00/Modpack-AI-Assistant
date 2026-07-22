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

    private static final Set<String> MODES = Set.of("auto", "cloud", "ollama", "offline");
    private static final Set<String> SIDEBARS = Set.of("left", "right");
    private static final Set<String> PREFER_OBTAINS = Set.of("craft", "quest", "loot", "balanced");

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
