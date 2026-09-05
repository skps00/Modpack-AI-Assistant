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
    /**
     * When true, log complete chat messages JSON (system + history + user) to latest.log
     * before each LLM call. Default false — large / may include private question text.
     */
    public static final ModConfigSpec.BooleanValue LOG_FULL_PROMPT;
    /**
     * When true, show LLM token usage under each assistant reply (e.g. {@code 1.2k in · 400 out}).
     * Default true. Missing provider usage → hide the line.
     */
    public static final ModConfigSpec.BooleanValue SHOW_TOKEN_USAGE;
    /**
     * Ask native function-calling: {@code auto} (default) | {@code force} | {@code off}.
     * auto = first craft/obtain LLM round may send tools; HTTP 400 remembers URL.
     * force = always send tools (ignore remembered URL). off = never send tools.
     */
    public static final ModConfigSpec.ConfigValue<String> ASK_NATIVE_TOOLS;
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
     * Extra universal-replicator JEI category title substrings (comma-separated).
     * Cards whose categoryTitle contains any entry (lowercase match) lose to non-mirror
     * cards with the same recipe content. Default empty — content dedup covers most.
     */
    public static final ModConfigSpec.ConfigValue<String> RECIPE_CARD_MIRROR_CATEGORIES;
    /**
     * Max OUTPUT (obtain/craft) recipe cards per Ask item (focus and each also-selected).
     * Default 3. Independent of {@link #RECIPE_CARDS_PER_ITEM_USE}.
     */
    public static final ModConfigSpec.IntValue RECIPE_CARDS_PER_ITEM;
    /**
     * Max INPUT (uses as material) recipe cards per Ask item.
     * Default 3. Independent of {@link #RECIPE_CARDS_PER_ITEM}.
     */
    public static final ModConfigSpec.IntValue RECIPE_CARDS_PER_ITEM_USE;
    /**
     * When Ask shows JEI recipe cards: {@code keywords} | {@code ai} | {@code always} | {@code never}.
     * Default {@code ai} (LLM gate marker). See {@link com.skps9.packai.logic.RecipeCardsMode}.
     */
    public static final ModConfigSpec.ConfigValue<String> RECIPE_CARDS_MODE;
    /**
     * When true, background-scan {@code mods/*.jar} zip entries (recipes / loot_tables)
     * into {@code config/packai/jar-cache/}. Default false — safer for huge packs (NFWC).
     */
    public static final ModConfigSpec.BooleanValue SCAN_MOD_JARS;
    /**
     * When true, Ask PURPOSE may unpack common container NBT (shulker / bundle / backpack)
     * into a capped {@code [CONTAINED]} block. Default false (token-saving).
     */
    public static final ModConfigSpec.BooleanValue UNPACK_STORED_ITEMS;
    /**
     * Which recipe UI to ground Ask "how to get" on: {@code auto} | {@code jei} | {@code emi}.
     * {@code auto} = JEI first when both loaded; EMI is detect/stub until a full adapter ships.
     */
    public static final ModConfigSpec.ConfigValue<String> RECIPE_BACKEND;
    /**
     * PackIndex nearby snippet: lines before/after first item-id / hint hit (not file head).
     * Default 30; clamped 5–100.
     */
    public static final ModConfigSpec.IntValue PACK_INDEX_CLIP_RADIUS;
    /**
     * Guidebook Ask scope: {@code same_mod} (default) only pins books whose path ns equals the
     * focus item ns; {@code any_mod} allows any linked book (still capped).
     */
    public static final ModConfigSpec.ConfigValue<String> GUIDEBOOK_SCOPE;
    /**
     * When true, Ask may expand one hop of guidebook related entries (linksOut / same category)
     * after item or title hits. Default false — prefer miss over dilution.
     */
    public static final ModConfigSpec.BooleanValue GUIDEBOOK_RELATED_HOP;
    /**
     * Purpose-ask FACT block order: {@code purpose_first} (default) =
     * PURPOSE/GUIDE/CONSUME_USE → AS_INGREDIENT → obtain;
     * {@code ingredient_first} = as-ingredient / get may lead (older style).
     */
    public static final ModConfigSpec.ConfigValue<String> ASK_PURPOSE_ORDER;

    private static final Set<String> MODES = Set.of("auto", "cloud", "ollama", "offline");
    private static final Set<String> SIDEBARS = Set.of("left", "right");
    private static final Set<String> PREFER_OBTAINS = Set.of("craft", "quest", "loot", "balanced");
    private static final Set<String> ASK_PURPOSE_ORDERS = Set.of("purpose_first", "ingredient_first");
    private static final Set<String> ASK_NATIVE_TOOLS_MODES = Set.of("auto", "force", "off");
    private static final Set<String> INGREDIENT_NBT_POLICIES = Set.of("auto", "always", "never");
    private static final Set<String> RECIPE_BACKENDS = Set.of("auto", "jei", "emi");
    private static final Set<String> RECIPE_CARDS_MODES = Set.of("keywords", "ai", "always", "never");
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
        LOG_FULL_PROMPT = b.comment(
                        "If true, log the complete LLM chat messages (system + history + user JSON)",
                        "to latest.log before each Ask call. Default false — logs can be huge",
                        "and may include private questions. Toggle in Pack AI Settings → Ask,",
                        "or edit packai-client.toml [llm].")
                .define("logFullPrompt", false);
        SHOW_TOKEN_USAGE = b.comment(
                        "If true, show prompt/completion token counts under each Ask reply",
                        "(e.g. 1.2k in · 400 out). Default true. Hide when the API omits usage.",
                        "Toggle in Pack AI Settings → Ask, or edit packai-client.toml [llm].")
                .define("showTokenUsage", true);
        ASK_NATIVE_TOOLS = b.comment(
                        "Ask native function-calling: auto | force | off.",
                        "auto (default) = send the five tools on first craft/obtain LLM round;",
                        "HTTP 400 remembers the URL and falls back to today's no-tools path.",
                        "force = always send tools (ignore remembered URL).",
                        "off = never send tools (today's marker/FACT path).",
                        "Not a second Ask product. Edit packai-client.toml [llm].")
                .define("askNativeTools", "auto");
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
        RECIPE_CARD_MIRROR_CATEGORIES = b.comment(
                        "Extra universal-replicator JEI categories to drop when the same recipe content exists elsewhere (comma-separated substrings of the category title; e.g. 动力合成器,搅拌机). Default empty — content dedup already covers most replicators.",
                        "額外「萬用複製機」JEI 分類：同內容已有其他卡時可丟棄（逗號分隔、對 category 標題做小寫子字串匹配；例：动力合成器,搅拌机）。預設空——內容去重已覆蓋多數複製機。")
                .define("recipeCardMirrorCategories", "");
        RECIPE_CARDS_PER_ITEM = b.comment(
                        "Max JEI OUTPUT (obtain/craft) recipe cards per Ask item (focus + each also-selected).",
                        "Default 3. Independent of recipeCardsPerItemUse (INPUT/uses).")
                .defineInRange("recipeCardsPerItem", 3, 1, 8);
        RECIPE_CARDS_PER_ITEM_USE = b.comment(
                        "Max JEI INPUT (uses as material) recipe cards per Ask item (focus + each also-selected).",
                        "Default 3. Independent of recipeCardsPerItem (OUTPUT/obtain).")
                .defineInRange("recipeCardsPerItemUse", 3, 1, 8);
        RECIPE_CARDS_MODE = b.comment(
                        "When Ask shows JEI recipe cards: keywords | ai | always | never.",
                        "ai (default) = LLM emits [[recipe_cards:on]] to show (else off); card bodies still JEI only.",
                        "keywords = craft/how-to-get keyword gate.",
                        "Offline / no cloud key → keywords fallback. always = ignore keywords; never = no cards.")
                .define("recipeCardsMode", "ai");
        SCAN_MOD_JARS = b.comment(
                        "If true, background-scan mods/*.jar zip entries (data/**/recipes|loot_tables)",
                        "into config/packai/jar-cache/ and inject short [JAR] hints into Ask.",
                        "Default false — safer for huge packs; enable in Mods → Pack AI → Ask.",
                        "No decompile; unchanged jars skipped via zip central-dir SHA-256 fingerprint.")
                .define("scanModJars", false);
        UNPACK_STORED_ITEMS = b.comment(
                        "If true, Ask PURPOSE unpacks common container NBT (BlockEntityTag.Items,",
                        "bundle Items, Inventory/contents, …) into a capped [CONTAINED] name×count list.",
                        "Default false — saves tokens; enable in Mods → Pack AI → Ask.",
                        "Unknown containers soft-fail (no crash).")
                .define("unpackStoredItems", false);
        RECIPE_BACKEND = b.comment(
                        "Recipe UI for Ask how-to-get grounding: auto | jei | emi.",
                        "auto = use JEI when loaded (preferred if both JEI+EMI); else EMI stub;",
                        "emi = prefer EMI (Preview gap until adapter); jei = JEI only.",
                        "Instance JEI/EMI always beats web/wiki for recipes.")
                .define("recipeBackend", "auto");
        PACK_INDEX_CLIP_RADIUS = b.comment(
                        "PackIndex nearby script clip: lines before/after first item-id / hint hit.",
                        "Higher = more KubeJS context around the match (more tokens).",
                        "Default 30. Edit via Mods → Pack AI → Ask, or packai-client.toml [ui].")
                .defineInRange("packIndexClipRadius", 30, 5, 100);
        GUIDEBOOK_SCOPE = b.comment(
                        "Ask [GUIDE] book scope: same_mod (default) | any_mod.",
                        "same_mod = only pin Patchouli books whose resource ns equals the focus item ns.",
                        "any_mod = allow any book that links the item (still length-capped).",
                        "Guide text is advisory — recipes/quests/unlock data win on conflict.")
                .define("guidebookScope", "same_mod");
        GUIDEBOOK_RELATED_HOP = b.comment(
                        "If true, after guidebook item/title hits, expand one hop via entry links",
                        "or same category (still filtered by guidebookScope). Default false.")
                .define("guidebookRelatedHop", false);
        ASK_PURPOSE_ORDER = b.comment(
                        "Purpose-ask FACT order: purpose_first (default) | ingredient_first.",
                        "purpose_first = PURPOSE/[GUIDE]/[CONSUME_USE]/tooltip → [AS_INGREDIENT] → obtain.",
                        "ingredient_first = older style — as-ingredient / how-to-get may lead before how-to-use.")
                .define("askPurposeOrder", "purpose_first");
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

    private static volatile String mirrorCategoriesCachedRaw;
    private static volatile List<String> mirrorCategoriesCached = List.of();

    /**
     * True when {@code categoryTitle} matches a configured mirror-replicator substring
     * ({@code recipeCardMirrorCategories}, comma-separated, lowercase contains).
     */
    public static boolean isMirrorReplicatorCategory(String categoryTitle) {
        if (categoryTitle == null || categoryTitle.isBlank()) {
            return false;
        }
        String raw = RECIPE_CARD_MIRROR_CATEGORIES.get();
        if (raw == null) {
            raw = "";
        }
        List<String> needles = mirrorCategoriesCached;
        if (!raw.equals(mirrorCategoriesCachedRaw)) {
            List<String> parsed = new ArrayList<>();
            for (String part : raw.split(",")) {
                String s = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
                if (!s.isEmpty()) {
                    parsed.add(s);
                }
            }
            needles = List.copyOf(parsed);
            mirrorCategoriesCached = needles;
            mirrorCategoriesCachedRaw = raw;
        }
        if (needles.isEmpty()) {
            return false;
        }
        String t = categoryTitle.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (t.contains(n)) {
                return true;
            }
        }
        return false;
    }

    /** Max OUTPUT (obtain) recipe cards per Ask item (1–8). */
    public static int recipeCardsPerItem() {
        Integer v = RECIPE_CARDS_PER_ITEM.get();
        return v == null ? 3 : Math.max(1, Math.min(8, v));
    }

    public static void setRecipeCardsPerItem(int n) {
        RECIPE_CARDS_PER_ITEM.set(Math.max(1, Math.min(8, n)));
        SPEC.save();
    }

    /** Max INPUT (uses) recipe cards per Ask item (1–8). */
    public static int recipeCardsPerItemUse() {
        Integer v = RECIPE_CARDS_PER_ITEM_USE.get();
        return v == null ? 3 : Math.max(1, Math.min(8, v));
    }

    public static void setRecipeCardsPerItemUse(int n) {
        RECIPE_CARDS_PER_ITEM_USE.set(Math.max(1, Math.min(8, n)));
        SPEC.save();
    }

    /** When Ask shows JEI cards: ai (default), keywords, always, or never. */
    public static String recipeCardsMode() {
        String raw = RECIPE_CARDS_MODE.get();
        if (raw == null || raw.isBlank()) {
            return "ai";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return RECIPE_CARDS_MODES.contains(s) ? s : "ai";
    }

    public static void setRecipeCardsMode(String mode) {
        String s = mode == null ? "ai" : mode.trim().toLowerCase(Locale.ROOT);
        RECIPE_CARDS_MODE.set(RECIPE_CARDS_MODES.contains(s) ? s : "ai");
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

    /** Default false: do not unpack shulker/bundle/backpack contents into Ask PURPOSE. */
    public static boolean unpackStoredItems() {
        return Boolean.TRUE.equals(UNPACK_STORED_ITEMS.get());
    }

    /** Default false: skip dumping full LLM messages JSON to latest.log. */
    public static boolean logFullPrompt() {
        return Boolean.TRUE.equals(LOG_FULL_PROMPT.get());
    }

    public static void setLogFullPrompt(boolean enabled) {
        LOG_FULL_PROMPT.set(enabled);
        SPEC.save();
    }

    /** Default true: show LLM token usage under assistant replies when the API reports it. */
    public static boolean showTokenUsage() {
        return Boolean.TRUE.equals(SHOW_TOKEN_USAGE.get());
    }

    public static void setShowTokenUsage(boolean enabled) {
        SHOW_TOKEN_USAGE.set(enabled);
        SPEC.save();
    }

    public static void setUnpackStoredItems(boolean enabled) {
        UNPACK_STORED_ITEMS.set(enabled);
        SPEC.save();
    }

    /** Normalized: {@code auto}, {@code jei}, or {@code emi}. */
    public static String recipeBackend() {
        String raw = RECIPE_BACKEND.get();
        if (raw == null || raw.isBlank()) {
            return "auto";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return RECIPE_BACKENDS.contains(s) ? s : "auto";
    }

    public static void setRecipeBackend(String backend) {
        String s = backend == null ? "auto" : backend.trim().toLowerCase(Locale.ROOT);
        RECIPE_BACKEND.set(RECIPE_BACKENDS.contains(s) ? s : "auto");
        SPEC.save();
    }

    /** PackIndex nearby clip line radius (5–100, default 30). */
    public static int packIndexClipRadius() {
        Integer v = PACK_INDEX_CLIP_RADIUS.get();
        return v == null ? 30 : Math.max(5, Math.min(100, v));
    }

    public static void setPackIndexClipRadius(int radius) {
        PACK_INDEX_CLIP_RADIUS.set(Math.max(5, Math.min(100, radius)));
        SPEC.save();
    }

    /** Ask [GUIDE] scope: same_mod (default) | any_mod. */
    public static String guidebookScope() {
        String raw = GUIDEBOOK_SCOPE.get();
        if (raw == null || raw.isBlank()) {
            return "same_mod";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("any_mod".equals(s) || "any".equals(s) || "cross_mod".equals(s)) {
            return "any_mod";
        }
        return "same_mod";
    }

    public static void setGuidebookScope(String scope) {
        String s = scope == null ? "same_mod" : scope.trim().toLowerCase(Locale.ROOT);
        if (!"any_mod".equals(s) && !"any".equals(s) && !"cross_mod".equals(s)) {
            s = "same_mod";
        } else {
            s = "any_mod";
        }
        GUIDEBOOK_SCOPE.set(s);
        SPEC.save();
    }

    /** Default false: no related-entry hop on Ask [GUIDE]. */
    public static boolean guidebookRelatedHop() {
        return Boolean.TRUE.equals(GUIDEBOOK_RELATED_HOP.get());
    }

    public static void setGuidebookRelatedHop(boolean enabled) {
        GUIDEBOOK_RELATED_HOP.set(enabled);
        SPEC.save();
    }

    /**
     * Purpose-ask FACT order: {@code purpose_first} (default) or {@code ingredient_first}.
     * Aliases: {@code obtain_first} → ingredient_first.
     */
    public static String askPurposeOrder() {
        String raw = ASK_PURPOSE_ORDER.get();
        if (raw == null || raw.isBlank()) {
            return "purpose_first";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("ingredient_first".equals(s) || "obtain_first".equals(s) || "get_first".equals(s)) {
            return "ingredient_first";
        }
        return ASK_PURPOSE_ORDERS.contains(s) ? s : "purpose_first";
    }

    public static void setAskPurposeOrder(String order) {
        String s = order == null ? "purpose_first" : order.trim().toLowerCase(Locale.ROOT);
        if ("obtain_first".equals(s) || "get_first".equals(s)) {
            s = "ingredient_first";
        }
        ASK_PURPOSE_ORDER.set(ASK_PURPOSE_ORDERS.contains(s) ? s : "purpose_first");
        SPEC.save();
    }

    /** auto (default) | force | off. Unknown → auto. */
    public static String askNativeToolsMode() {
        String raw = ASK_NATIVE_TOOLS.get();
        if (raw == null || raw.isBlank()) {
            return "auto";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return ASK_NATIVE_TOOLS_MODES.contains(s) ? s : "auto";
    }

    public static boolean askNativeToolsOff() {
        return "off".equals(askNativeToolsMode());
    }

    public static boolean askNativeToolsForce() {
        return "force".equals(askNativeToolsMode());
    }

    public static void setAskNativeToolsMode(String mode) {
        String s = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        ASK_NATIVE_TOOLS.set(ASK_NATIVE_TOOLS_MODES.contains(s) ? s : "auto");
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
