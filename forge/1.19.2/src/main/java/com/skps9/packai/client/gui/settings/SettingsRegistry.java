package com.skps9.packai.client.gui.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.AskTrace;

/**
 * Single source of truth for Settings V2 entries (Batch B1).
 *
 * <pre>
 * TOML section → UI category map (explicit; registry follows — never match by leaf name alone):
 *
 * [llm]
 *   → CONNECTION: mode, apiKey, apiBaseUrl, model, ollamaBaseUrl, ollamaModel, dailyTokenLimit
 *   → ANSWER:     askNativeTools, askMaxToolRounds
 *   → INTERFACE:  showTokenUsage
 *   → DEBUG:      logFullPrompt, askTraceJsonl, askTraceKeepFiles, traceKeepDays
 *
 * [token]
 *   → ANSWER:     maxJeiChars, historyTurns, maxFacts
 *
 * [web]
 *   → CONNECTION: allowWebSearch, tavilyApiKey, serperApiKey
 *
 * [ui]
 *   → INTERFACE:  sidebarSide, ingredientNbtPolicy, ingredientTooltipAsReq
 *   → ANSWER:     askPurposeOrder, guidebookScope, guidebookRelatedHop
 *   → RECIPE:     preferObtain, recipeCategoryOrder (+hidden via same prefs setter in B2),
 *                 recipeCardsPerItem, recipeCardsPerItemUse, recipeCardsMode,
 *                 recipeCardMirrorCategories, modularToolSingleItem, recipeBackend
 *   → QUEST:      showHiddenQuests, attachRelatedQuests, questMatchHotbar
 *   → ADVANCED:   knowledgeEnabled, knowledgeRemote, knowledgeCacheMaxMb, knowledgeUrl,
 *                 ingredientNbtSkipPatterns, ingredientNbtKeepPatterns
 *   → DEBUG:      scanModJars, unpackStoredItems, packIndexClipRadius
 *
 * Fail-closed: only keys with a set* accessor are listed (EXCLUDED empty unless justified).
 * Compare by full path (llm.mode ≠ other mode keys; b.push has four sections).
 * </pre>
 */
public final class SettingsRegistry {
    /** Fail-closed allowlist: empty unless a setter must not appear (file+line evidence). */
    public static final java.util.Set<String> EXCLUDED = java.util.Set.of();

    public enum UiCategory {
        CONNECTION("packai.settings.cat.connection"),
        ANSWER("packai.settings.cat.answer"),
        RECIPE("packai.settings.cat.recipe"),
        QUEST("packai.settings.cat.quest"),
        INTERFACE("packai.settings.cat.interface"),
        ADVANCED("packai.settings.cat.advanced"),
        DEBUG("packai.settings.cat.debug");

        public final String labelKey;

        UiCategory(String labelKey) {
            this.labelKey = labelKey;
        }
    }

    public enum ControlType {
        TOGGLE,
        NUMBER,
        TEXT,
        LIST
    }

    public static final class Entry {
        public final String path;
        public final UiCategory category;
        public final String labelKey;
        public final String tooltipKey;
        public final ControlType type;
        /** PackAiConfig setter simple name (guard / fail-closed). */
        public final String setterName;
        public final Supplier<String> getter;
        public final Consumer<String> setter;
        /**
         * When true, SettingsScreenV2 skips render／search／scroll for this row, but
         * resetPage／resetAll still apply (D-batch: llm.ollamaModel hidden behind unified picker).
         */
        public final boolean hiddenInUi;

        Entry(
                String path,
                UiCategory category,
                String labelKey,
                String tooltipKey,
                ControlType type,
                String setterName,
                Supplier<String> getter,
                Consumer<String> setter) {
            this(path, category, labelKey, tooltipKey, type, setterName, getter, setter, false);
        }

        Entry(
                String path,
                UiCategory category,
                String labelKey,
                String tooltipKey,
                ControlType type,
                String setterName,
                Supplier<String> getter,
                Consumer<String> setter,
                boolean hiddenInUi) {
            this.path = path;
            this.category = category;
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
            this.type = type;
            this.setterName = setterName;
            this.getter = getter;
            this.setter = setter;
            this.hiddenInUi = hiddenInUi;
        }
    }

    private static final List<Entry> ALL;
    private static final Map<String, Entry> BY_PATH;

    static {
        List<Entry> built = new ArrayList<>();
        // --- [llm] → CONNECTION ---
        built.add(e(
                "llm.mode",
                UiCategory.CONNECTION,
                "packai.screen.mode",
                "packai.settings.tooltip.mode",
                ControlType.LIST,
                "setMode",
                PackAiConfig::resolvedMode,
                PackAiConfig::setMode));
        built.add(e(
                "llm.apiKey",
                UiCategory.CONNECTION,
                "packai.screen.api_key",
                "packai.settings.tooltip.api_key",
                ControlType.TEXT,
                "setApiKey",
                () -> PackAiConfig.API_KEY.get(),
                PackAiConfig::setApiKey));
        built.add(e(
                "llm.apiBaseUrl",
                UiCategory.CONNECTION,
                "packai.settings.api_base",
                "packai.settings.tooltip.api_base",
                ControlType.TEXT,
                "setApiBaseUrl",
                () -> PackAiConfig.API_BASE_URL.get(),
                PackAiConfig::setApiBaseUrl));
        built.add(e(
                "llm.model",
                UiCategory.CONNECTION,
                "packai.screen.model",
                "packai.settings.tooltip.model",
                ControlType.TEXT,
                "setCloudModel",
                () -> {
                    String m = PackAiConfig.MODEL.get();
                    return m == null ? "" : m;
                },
                PackAiConfig::setCloudModel));
        built.add(e(
                "llm.ollamaBaseUrl",
                UiCategory.CONNECTION,
                "packai.settings.ollama_base",
                "packai.settings.tooltip.ollama_base",
                ControlType.TEXT,
                "setOllamaBaseUrl",
                () -> PackAiConfig.OLLAMA_BASE_URL.get(),
                PackAiConfig::setOllamaBaseUrl));
        built.add(eHidden(
                "llm.ollamaModel",
                UiCategory.CONNECTION,
                "packai.settings.ollama_model",
                "packai.settings.tooltip.ollama_model",
                ControlType.TEXT,
                "setOllamaModel",
                () -> PackAiConfig.OLLAMA_MODEL.get(),
                PackAiConfig::setOllamaModel));
        // --- [web] → CONNECTION ---
        built.add(e(
                "web.allowWebSearch",
                UiCategory.CONNECTION,
                "packai.web_settings.enable",
                "packai.web_settings.tooltip.enable",
                ControlType.TOGGLE,
                "setWebSearchEnabled",
                () -> Boolean.toString(PackAiConfig.webSearchEnabled()),
                v -> PackAiConfig.setWebSearchEnabled(parseBool(v))));
        built.add(e(
                "web.tavilyApiKey",
                UiCategory.CONNECTION,
                "packai.web_settings.tavily",
                "packai.web_settings.tooltip.tavily",
                ControlType.TEXT,
                "setTavilyApiKey",
                () -> PackAiConfig.TAVILY_API_KEY.get(),
                PackAiConfig::setTavilyApiKey));
        built.add(e(
                "web.serperApiKey",
                UiCategory.CONNECTION,
                "packai.web_settings.serper",
                "packai.web_settings.tooltip.serper",
                ControlType.TEXT,
                "setSerperApiKey",
                () -> PackAiConfig.SERPER_API_KEY.get(),
                PackAiConfig::setSerperApiKey));
        built.add(e(
                "llm.dailyTokenLimit",
                UiCategory.CONNECTION,
                "packai.settings.daily_token_limit",
                "packai.settings.tooltip.daily_token_limit",
                ControlType.NUMBER,
                "setDailyTokenLimit",
                () -> Integer.toString(PackAiConfig.dailyTokenLimit()),
                v -> PackAiConfig.setDailyTokenLimit(
                        parseNumberInput(v, PackAiConfig.dailyTokenLimit(), 0, 100_000_000))));
        // --- [token] + [llm] answer ---
        built.add(e(
                "token.maxJeiChars",
                UiCategory.ANSWER,
                "packai.settings.max_jei_chars",
                "packai.settings.tooltip.max_jei_chars",
                ControlType.NUMBER,
                "setMaxJeiChars",
                () -> Integer.toString(PackAiConfig.maxJeiChars()),
                v -> PackAiConfig.setMaxJeiChars(
                        parseNumberInput(v, PackAiConfig.maxJeiChars(), 1000, 12000))));
        built.add(e(
                "token.historyTurns",
                UiCategory.ANSWER,
                "packai.settings.history_turns",
                "packai.settings.tooltip.history_turns",
                ControlType.NUMBER,
                "setHistoryTurns",
                () -> Integer.toString(PackAiConfig.historyTurns()),
                v -> PackAiConfig.setHistoryTurns(
                        parseNumberInput(v, PackAiConfig.historyTurns(), 0, 16))));
        built.add(e(
                "token.maxFacts",
                UiCategory.ANSWER,
                "packai.settings.max_facts",
                "packai.settings.tooltip.max_facts",
                ControlType.NUMBER,
                "setMaxFacts",
                () -> Integer.toString(PackAiConfig.maxFacts()),
                v -> PackAiConfig.setMaxFacts(
                        parseNumberInput(v, PackAiConfig.maxFacts(), 4, 32))));
        built.add(e(
                "llm.askNativeTools",
                UiCategory.ANSWER,
                "packai.settings.ask_native_tools",
                "packai.settings.tooltip.ask_native_tools",
                ControlType.LIST,
                "setAskNativeToolsMode",
                PackAiConfig::askNativeToolsMode,
                PackAiConfig::setAskNativeToolsMode));
        built.add(e(
                "llm.askMaxToolRounds",
                UiCategory.ANSWER,
                "packai.settings.ask_max_tool_rounds",
                "packai.settings.tooltip.ask_max_tool_rounds",
                ControlType.NUMBER,
                "setAskMaxToolRounds",
                () -> Integer.toString(PackAiConfig.askMaxToolRounds()),
                v -> PackAiConfig.setAskMaxToolRounds(
                        parseNumberInput(v, PackAiConfig.askMaxToolRounds(), 1, 8))));
        built.add(e(
                "ui.askPurposeOrder",
                UiCategory.ANSWER,
                "packai.settings.ask_purpose_order",
                "packai.settings.tooltip.ask_purpose_order",
                ControlType.LIST,
                "setAskPurposeOrder",
                PackAiConfig::askPurposeOrder,
                PackAiConfig::setAskPurposeOrder));
        built.add(e(
                "ui.guidebookScope",
                UiCategory.ANSWER,
                "packai.settings.guidebook_scope",
                "packai.settings.tooltip.guidebook_scope",
                ControlType.LIST,
                "setGuidebookScope",
                PackAiConfig::guidebookScope,
                PackAiConfig::setGuidebookScope));
        built.add(e(
                "ui.guidebookRelatedHop",
                UiCategory.ANSWER,
                "packai.settings.guidebook_related",
                "packai.settings.tooltip.guidebook_related",
                ControlType.TOGGLE,
                "setGuidebookRelatedHop",
                () -> Boolean.toString(PackAiConfig.guidebookRelatedHop()),
                v -> PackAiConfig.setGuidebookRelatedHop(parseBool(v))));
        // --- [ui] RECIPE ---
        built.add(e(
                "ui.preferObtain",
                UiCategory.RECIPE,
                "packai.settings.prefer_obtain",
                "packai.settings.tooltip.prefer_obtain",
                ControlType.LIST,
                "setPreferObtain",
                PackAiConfig::preferObtain,
                PackAiConfig::setPreferObtain));
        built.add(e(
                "ui.recipeCategoryOrder",
                UiCategory.RECIPE,
                "packai.settings.recipe_cats",
                "packai.settings.tooltip.recipe_cats",
                ControlType.LIST,
                "setRecipeCategoryPrefs",
                () -> String.join(";", PackAiConfig.recipeCategoryOrder()),
                v -> {
                    /* Order-only write — keep existing hidden prefs (do not wipe). */
                    List<String> order = splitSemi(v);
                    PackAiConfig.setRecipeCategoryPrefs(order, PackAiConfig.recipeCategoryHidden());
                }));
        built.add(e(
                "ui.recipeCardsPerItem",
                UiCategory.RECIPE,
                "packai.settings.recipe_cards_per_item",
                "packai.settings.tooltip.recipe_cards_per_item",
                ControlType.NUMBER,
                "setRecipeCardsPerItem",
                () -> Integer.toString(PackAiConfig.recipeCardsPerItem()),
                v -> PackAiConfig.setRecipeCardsPerItem(
                        parseNumberInput(v, PackAiConfig.recipeCardsPerItem(), 1, 8))));
        built.add(e(
                "ui.recipeCardsPerItemUse",
                UiCategory.RECIPE,
                "packai.settings.recipe_cards_per_item_use",
                "packai.settings.tooltip.recipe_cards_per_item_use",
                ControlType.NUMBER,
                "setRecipeCardsPerItemUse",
                () -> Integer.toString(PackAiConfig.recipeCardsPerItemUse()),
                v -> PackAiConfig.setRecipeCardsPerItemUse(
                        parseNumberInput(v, PackAiConfig.recipeCardsPerItemUse(), 1, 8))));
        built.add(e(
                "ui.recipeCardsMode",
                UiCategory.RECIPE,
                "packai.settings.recipe_cards_mode",
                "packai.settings.tooltip.recipe_cards_mode",
                ControlType.LIST,
                "setRecipeCardsMode",
                PackAiConfig::recipeCardsMode,
                PackAiConfig::setRecipeCardsMode));
        built.add(e(
                "ui.recipeCardMirrorCategories",
                UiCategory.RECIPE,
                "packai.settings.recipe_card_mirror",
                "packai.settings.tooltip.recipe_card_mirror",
                ControlType.LIST,
                "setRecipeCardMirrorCategories",
                () -> PackAiConfig.RECIPE_CARD_MIRROR_CATEGORIES.get(),
                PackAiConfig::setRecipeCardMirrorCategories));
        built.add(e(
                "ui.modularToolSingleItem",
                UiCategory.RECIPE,
                "packai.settings.modular_tool_single_item",
                "packai.settings.tooltip.modular_tool_single_item",
                ControlType.TOGGLE,
                "setModularToolSingleItem",
                () -> Boolean.toString(PackAiConfig.modularToolSingleItem()),
                v -> PackAiConfig.setModularToolSingleItem(parseBool(v))));
        built.add(e(
                "ui.recipeBackend",
                UiCategory.RECIPE,
                "packai.settings.recipe_backend",
                "packai.settings.tooltip.recipe_backend",
                ControlType.LIST,
                "setRecipeBackend",
                PackAiConfig::recipeBackend,
                PackAiConfig::setRecipeBackend));
        // --- QUEST ---
        built.add(e(
                "ui.showHiddenQuests",
                UiCategory.QUEST,
                "packai.settings.show_hidden_quests",
                "packai.settings.tooltip.show_hidden_quests",
                ControlType.TOGGLE,
                "setShowHiddenQuests",
                () -> Boolean.toString(PackAiConfig.showHiddenQuests()),
                v -> PackAiConfig.setShowHiddenQuests(parseBool(v))));
        built.add(e(
                "ui.attachRelatedQuests",
                UiCategory.QUEST,
                "packai.settings.attach_quests",
                "packai.settings.tooltip.attach_quests",
                ControlType.TOGGLE,
                "setAttachRelatedQuests",
                () -> Boolean.toString(PackAiConfig.attachRelatedQuests()),
                v -> PackAiConfig.setAttachRelatedQuests(parseBool(v))));
        built.add(e(
                "ui.questMatchHotbar",
                UiCategory.QUEST,
                "packai.settings.quest_match_hotbar",
                "packai.settings.tooltip.quest_match_hotbar",
                ControlType.TOGGLE,
                "setQuestMatchHotbar",
                () -> Boolean.toString(PackAiConfig.questMatchHotbar()),
                v -> PackAiConfig.setQuestMatchHotbar(parseBool(v))));
        // --- INTERFACE ---
        built.add(e(
                "ui.sidebarSide",
                UiCategory.INTERFACE,
                "packai.settings.sidebar",
                "packai.settings.tooltip.sidebar",
                ControlType.LIST,
                "setSidebarSide",
                PackAiConfig::sidebarSide,
                PackAiConfig::setSidebarSide));
        built.add(e(
                "llm.showTokenUsage",
                UiCategory.INTERFACE,
                "packai.settings.show_token_usage",
                "packai.settings.tooltip.show_token_usage",
                ControlType.TOGGLE,
                "setShowTokenUsage",
                () -> Boolean.toString(PackAiConfig.showTokenUsage()),
                v -> PackAiConfig.setShowTokenUsage(parseBool(v))));
        built.add(e(
                "ui.ingredientNbtPolicy",
                UiCategory.INTERFACE,
                "packai.settings.ingredient_nbt",
                "packai.settings.tooltip.ingredient_nbt",
                ControlType.LIST,
                "setIngredientNbtPolicy",
                PackAiConfig::ingredientNbtPolicy,
                PackAiConfig::setIngredientNbtPolicy));
        built.add(e(
                "ui.ingredientTooltipAsReq",
                UiCategory.INTERFACE,
                "packai.settings.ingredient_tooltip_req",
                "packai.settings.tooltip.ingredient_tooltip_req",
                ControlType.TOGGLE,
                "setIngredientTooltipAsReq",
                () -> Boolean.toString(PackAiConfig.ingredientTooltipAsReq()),
                v -> PackAiConfig.setIngredientTooltipAsReq(parseBool(v))));
        // --- ADVANCED ---
        built.add(e(
                "ui.knowledgeEnabled",
                UiCategory.ADVANCED,
                "packai.settings.knowledge_enabled",
                "packai.settings.tooltip.knowledge_enabled",
                ControlType.TOGGLE,
                "setKnowledgeEnabled",
                () -> Boolean.toString(PackAiConfig.knowledgeEnabled()),
                v -> PackAiConfig.setKnowledgeEnabled(parseBool(v))));
        built.add(e(
                "ui.knowledgeRemote",
                UiCategory.ADVANCED,
                "packai.settings.knowledge_remote",
                "packai.settings.tooltip.knowledge_remote",
                ControlType.TOGGLE,
                "setKnowledgeRemote",
                () -> Boolean.toString(PackAiConfig.knowledgeRemote()),
                v -> PackAiConfig.setKnowledgeRemote(parseBool(v))));
        built.add(e(
                "ui.knowledgeCacheMaxMb",
                UiCategory.ADVANCED,
                "packai.settings.knowledge_cache_mb",
                "packai.settings.tooltip.knowledge_cache_mb",
                ControlType.NUMBER,
                "setKnowledgeCacheMaxMb",
                () -> Integer.toString(PackAiConfig.knowledgeCacheMaxMb()),
                v -> PackAiConfig.setKnowledgeCacheMaxMb(
                        parseNumberInput(v, PackAiConfig.knowledgeCacheMaxMb(), 1, 512))));
        built.add(e(
                "ui.knowledgeUrl",
                UiCategory.ADVANCED,
                "packai.settings.knowledge_url",
                "packai.settings.tooltip.knowledge_url",
                ControlType.TEXT,
                "setKnowledgeUrl",
                PackAiConfig::knowledgeUrl,
                PackAiConfig::setKnowledgeUrl));
        built.add(e(
                "ui.ingredientNbtSkipPatterns",
                UiCategory.ADVANCED,
                "packai.settings.ingredient_nbt_skip",
                "packai.settings.tooltip.ingredient_nbt_skip",
                ControlType.LIST,
                "setIngredientNbtSkipPatterns",
                () -> PackAiConfig.INGREDIENT_NBT_SKIP_PATTERNS.get(),
                PackAiConfig::setIngredientNbtSkipPatterns));
        built.add(e(
                "ui.ingredientNbtKeepPatterns",
                UiCategory.ADVANCED,
                "packai.settings.ingredient_nbt_keep",
                "packai.settings.tooltip.ingredient_nbt_keep",
                ControlType.LIST,
                "setIngredientNbtKeepPatterns",
                () -> PackAiConfig.INGREDIENT_NBT_KEEP_PATTERNS.get(),
                PackAiConfig::setIngredientNbtKeepPatterns));
        // --- DEBUG ---
        built.add(e(
                "llm.logFullPrompt",
                UiCategory.DEBUG,
                "packai.settings.log_full_prompt",
                "packai.settings.tooltip.log_full_prompt",
                ControlType.TOGGLE,
                "setLogFullPrompt",
                () -> Boolean.toString(PackAiConfig.logFullPrompt()),
                v -> PackAiConfig.setLogFullPrompt(parseBool(v))));
        built.add(e(
                "llm.askTraceJsonl",
                UiCategory.DEBUG,
                "packai.settings.ask_trace_jsonl",
                "packai.settings.tooltip.ask_trace_jsonl",
                ControlType.TOGGLE,
                "setAskTraceJsonl",
                () -> Boolean.toString(PackAiConfig.askTraceJsonl()),
                v -> PackAiConfig.setAskTraceJsonl(parseBool(v))));
        built.add(e(
                "llm.askTraceKeepFiles",
                UiCategory.DEBUG,
                "packai.settings.ask_trace_keep_files",
                "packai.settings.tooltip.ask_trace_keep_files",
                ControlType.NUMBER,
                "setAskTraceKeepFiles",
                () -> Integer.toString(PackAiConfig.askTraceKeepFiles()),
                v -> PackAiConfig.setAskTraceKeepFiles(parseNumberInput(
                        v, PackAiConfig.askTraceKeepFiles(), AskTrace.KEEP_MIN, AskTrace.KEEP_MAX))));
        built.add(e(
                "llm.traceKeepDays",
                UiCategory.DEBUG,
                "packai.settings.trace_keep_days",
                "packai.settings.tooltip.trace_keep_days",
                ControlType.NUMBER,
                "setAskTraceKeepDays",
                () -> Integer.toString(PackAiConfig.askTraceKeepDays()),
                v -> PackAiConfig.setAskTraceKeepDays(parseNumberInput(
                        v,
                        PackAiConfig.askTraceKeepDays(),
                        AskTrace.KEEP_DAYS_MIN,
                        AskTrace.KEEP_DAYS_MAX))));
        built.add(e(
                "ui.scanModJars",
                UiCategory.DEBUG,
                "packai.settings.scan_mod_jars",
                "packai.settings.tooltip.scan_mod_jars",
                ControlType.TOGGLE,
                "setScanModJars",
                () -> Boolean.toString(PackAiConfig.scanModJars()),
                v -> PackAiConfig.setScanModJars(parseBool(v))));
        built.add(e(
                "ui.unpackStoredItems",
                UiCategory.DEBUG,
                "packai.settings.unpack_stored_items",
                "packai.settings.tooltip.unpack_stored_items",
                ControlType.TOGGLE,
                "setUnpackStoredItems",
                () -> Boolean.toString(PackAiConfig.unpackStoredItems()),
                v -> PackAiConfig.setUnpackStoredItems(parseBool(v))));
        built.add(e(
                "ui.packIndexClipRadius",
                UiCategory.DEBUG,
                "packai.settings.pack_index_clip_radius",
                "packai.settings.tooltip.pack_index_clip_radius",
                ControlType.NUMBER,
                "setPackIndexClipRadius",
                () -> Integer.toString(PackAiConfig.packIndexClipRadius()),
                v -> PackAiConfig.setPackIndexClipRadius(
                        parseNumberInput(v, PackAiConfig.packIndexClipRadius(), 5, 100))));

        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry entry : built) {
            if (EXCLUDED.contains(entry.setterName)) {
                continue;
            }
            if (entry.setterName == null || entry.setterName.isBlank() || !entry.setterName.startsWith("set")) {
                throw new IllegalStateException("fail-closed: missing set* for " + entry.path);
            }
            if (map.put(entry.path, entry) != null) {
                throw new IllegalStateException("duplicate path: " + entry.path);
            }
        }
        ALL = Collections.unmodifiableList(List.copyOf(map.values()));
        BY_PATH = Collections.unmodifiableMap(map);
    }

    private SettingsRegistry() {}

    public static List<Entry> all() {
        return ALL;
    }

    public static Entry byPath(String path) {
        return BY_PATH.get(path);
    }

    public static List<Entry> byCategory(UiCategory category) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (e.category == category) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * §4 invariant 3: placeholder display must not be written back to config.
     * Matches masked / “already set” UI strings (••• / 已設定 / 已设置 / Configured).
     */
    public static boolean isPlaceholderValue(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return false;
        }
        if ("•••".equals(t) || "...".equals(t) || "***".equals(t)) {
            return true;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return "已設定".equals(t)
                || "已设置".equals(t)
                || "configured".equals(lower)
                || "set".equals(lower);
    }

    private static Entry e(
            String path,
            UiCategory category,
            String labelKey,
            String tooltipKey,
            ControlType type,
            String setterName,
            Supplier<String> getter,
            Consumer<String> setter) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(setterName, "setterName");
        return new Entry(path, category, labelKey, tooltipKey, type, setterName, getter, setter, false);
    }

    /** Same as {@link #e} but {@code hiddenInUi=true} (still in registry／reset). */
    private static Entry eHidden(
            String path,
            UiCategory category,
            String labelKey,
            String tooltipKey,
            ControlType type,
            String setterName,
            Supplier<String> getter,
            Consumer<String> setter) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(setterName, "setterName");
        return new Entry(path, category, labelKey, tooltipKey, type, setterName, getter, setter, true);
    }

    private static boolean parseBool(String v) {
        if (v == null) {
            return false;
        }
        String t = v.trim().toLowerCase(Locale.ROOT);
        return "true".equals(t) || "1".equals(t) || "on".equals(t) || "yes".equals(t);
    }

    /**
     * NUMBER setter parse: empty / non-digit / negative → keep {@code current}; else clamp
     * [{@code min},{@code max}] (matches PackAiConfig {@code defineInRange}).
     */
    public static int parseNumberInput(String v, int current, int min, int max) {
        if (v == null || v.isBlank()) {
            return current;
        }
        try {
            int n = Integer.parseInt(v.trim());
            if (n < 0) {
                return current;
            }
            return Math.max(min, Math.min(max, n));
        } catch (NumberFormatException ex) {
            return current;
        }
    }

    private static List<String> splitSemi(String v) {
        if (v == null || v.isBlank()) {
            return List.of();
        }
        String[] parts = v.split(";");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
