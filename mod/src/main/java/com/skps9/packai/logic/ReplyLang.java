package com.skps9.packai.logic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Player/LLM-facing strings loaded from {@code assets/packai/lang/*.json}
 * ({@code packai.reply.*} keys). Works in-game and in headless checks.
 */
public final class ReplyLang {
    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> BUNDLES = loadBundles();

    private ReplyLang() {}

    public static String current() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getLanguageManager() != null) {
                String code = mc.getLanguageManager().getSelected();
                if (code != null && !code.isBlank()) {
                    return code.trim();
                }
            }
        } catch (Throwable ignored) {
            // headless / early init
        }
        return "zh_tw";
    }

    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return "zh_tw";
        }
        return code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    public static boolean isChinese(String code) {
        return normalize(code).startsWith("zh");
    }

    public static boolean isTraditionalChinese(String code) {
        String c = normalize(code);
        return c.startsWith("zh_tw") || c.startsWith("zh_hk") || "zh_hant".equals(c);
    }

    /** Translate {@code packai.reply.*} for the given language code. */
    public static String tr(String code, String key, Object... args) {
        String lang = isChinese(code) ? "zh_tw" : "en_us";
        String template = lookup(lang, key);
        if (template == null) {
            template = lookup("en_us", key);
        }
        if (template == null) {
            return key;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (Exception e) {
            return template;
        }
    }

    private static String lookup(String lang, String key) {
        Map<String, String> bundle = BUNDLES.get(lang);
        return bundle == null ? null : bundle.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, String>> loadBundles() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (String lang : List.of("zh_tw", "en_us")) {
            String path = "/assets/packai/lang/" + lang + ".json";
            try (InputStream in = ReplyLang.class.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                Map<String, String> all = GSON.fromJson(
                        new InputStreamReader(in, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, String>>() {}.getType());
                Map<String, String> reply = new LinkedHashMap<>();
                if (all != null) {
                    for (Map.Entry<String, String> e : all.entrySet()) {
                        if (e.getKey() != null && e.getKey().startsWith("packai.reply.") && e.getValue() != null) {
                            reply.put(e.getKey(), e.getValue());
                        }
                    }
                }
                out.put(lang, reply);
            } catch (Exception ignored) {
                // missing resource in odd classpaths
            }
        }
        return out;
    }

    public static String sourceHeader(String code) {
        return tr(code, "packai.reply.source_header");
    }

    public static String sourceJoin(String code) {
        return tr(code, "packai.reply.source_join");
    }

    public static String quote(String code, String inner) {
        return tr(code, "packai.reply.quote", inner == null ? "" : inner);
    }

    public static String labelQuestBook(String code) {
        return tr(code, "packai.reply.label.quest_book");
    }

    public static String labelLocalRecipes(String code) {
        return tr(code, "packai.reply.label.local_recipes");
    }

    public static String labelAcquire(String code) {
        return tr(code, "packai.reply.label.acquire");
    }

    public static String labelWeb(String code) {
        return tr(code, "packai.reply.label.web");
    }

    public static String labelAiOnly(String code) {
        return tr(code, "packai.reply.label.ai_only");
    }

    public static String labelAiModel(String code) {
        return tr(code, "packai.reply.label.ai_model");
    }

    public static String labelNone(String code) {
        return tr(code, "packai.reply.label.none");
    }

    public static String labelAcquireOffline(String code) {
        return tr(code, "packai.reply.label.acquire_offline");
    }

    public static String unnamedQuest(String code) {
        return tr(code, "packai.reply.unnamed_quest");
    }

    public static String unnamedChapter(String code) {
        return tr(code, "packai.reply.unnamed_chapter");
    }

    public static String relatedQuest(String itemName, String code) {
        return tr(code, "packai.reply.related_quest", itemName == null ? "" : itemName);
    }

    public static String chapterQuest(String chapter, String code) {
        return tr(code, "packai.reply.chapter_quest", chapter == null ? "" : chapter);
    }

    public static String unknownItem(String code) {
        return tr(code, "packai.reply.unknown_item");
    }

    public static String packScript(String code) {
        return tr(code, "packai.reply.pack_script");
    }

    public static String packConfig(String code) {
        return tr(code, "packai.reply.pack_config");
    }

    public static String packData(String code) {
        return tr(code, "packai.reply.pack_data");
    }

    public static String notePackSpecific(String code) {
        return tr(code, "packai.reply.note_pack_specific");
    }

    public static String friendlyOffline(String code, String question) {
        StringBuilder sb = new StringBuilder(tr(code, "packai.reply.friendly_offline"));
        if (question != null && !question.isBlank()) {
            sb.append(tr(code, "packai.reply.your_question")).append(question);
        }
        return sb.toString();
    }

    public static String shapedRecipe(String code, String out, String mats) {
        String q = quote(code, out);
        return tr(code, "packai.reply.shaped_recipe", q, mats, q);
    }

    public static String shapelessRecipe(String code, String out, String mats) {
        String q = quote(code, out);
        return tr(code, "packai.reply.shapeless_recipe", q, mats, q);
    }

    public static String removedRecipe(String code) {
        return tr(code, "packai.reply.removed_recipe");
    }

    public static String patternFallback(String code) {
        return tr(code, "packai.reply.pattern_fallback");
    }

    public static String queryFailed(String code, String msg) {
        return tr(code, "packai.reply.query_failed", msg == null ? "" : msg);
    }

    public static String llmCallFailed(String code, String detail) {
        return tr(code, "packai.reply.llm_call_failed", detail == null ? "" : detail);
    }

    public static String cloudNoKey(String code) {
        return tr(code, "packai.reply.cloud_no_key");
    }

    public static String ollamaDown(String code, String base) {
        return tr(code, "packai.reply.ollama_down", base == null ? "" : base);
    }

    public static String tipOfflineQuest(String code) {
        return tr(code, "packai.reply.tip_offline_quest");
    }

    public static String tipQuestSummaryNoAi(String code) {
        return tr(code, "packai.reply.tip_quest_summary_no_ai");
    }

    public static String tipOfflineEmpty(String code) {
        return tr(code, "packai.reply.tip_offline_empty");
    }

    public static String tipNeedLlm(String code) {
        return tr(code, "packai.reply.tip_need_llm");
    }

    public static String questOverrideNotice(String code) {
        return tr(code, "packai.reply.quest_override_notice");
    }

    public static String questFactLine(String code, String title, String desc) {
        String line = tr(code, "packai.reply.quest_fact_prefix", quote(code, title));
        if (desc != null && !desc.isBlank()) {
            line = line + " — " + desc;
        }
        return line;
    }

    public static String guideHeader(String code, boolean rich) {
        return tr(code, rich ? "packai.reply.guide_header_rich" : "packai.reply.guide_header");
    }

    public static String guideChapterQuest(String code, int i, String chapter, String title) {
        return tr(code, "packai.reply.guide_chapter_quest", i, chapter, title);
    }

    public static String guideDesc(String code, String d) {
        return tr(code, "packai.reply.guide_desc", d);
    }

    public static String guideDescFallback(String code) {
        return tr(code, "packai.reply.guide_desc_fallback");
    }

    public static String guideNeeds(String code) {
        return tr(code, "packai.reply.guide_needs");
    }

    public static String guideEtc(String code) {
        return tr(code, "packai.reply.guide_etc");
    }

    public static String guideMore(String code) {
        return tr(code, "packai.reply.guide_more");
    }

    public static String guideStuckHint(String code) {
        return tr(code, "packai.reply.guide_stuck");
    }

    public static String guideConflict(String code) {
        return tr(code, "packai.reply.guide_conflict");
    }

    public static String fishing(String code) {
        return tr(code, "packai.reply.fishing");
    }

    public static String loot(String code) {
        return tr(code, "packai.reply.loot");
    }

    public static String trade(String code) {
        return tr(code, "packai.reply.trade");
    }

    public static String fishingKind(String code) {
        return tr(code, "packai.reply.fishing_kind");
    }

    public static String lootKind(String code) {
        return tr(code, "packai.reply.loot_kind");
    }

    public static String tradeKind(String code) {
        return tr(code, "packai.reply.trade_kind");
    }

    public static String scriptNeeds(String code, String need) {
        return tr(code, "packai.reply.script_needs", need);
    }

    public static String scriptRemoved(String code) {
        return tr(code, "packai.reply.script_removed");
    }

    /** How to obtain via scripted interaction (right/left click, break, entity, food…). */
    public static String interactGet(String code, String heldName, String targetName, String via) {
        String action = interactViaLabel(code, via);
        if (heldName == null || heldName.isBlank()) {
            return tr(code, "packai.reply.interact_get_target_only", action, quote(code, targetName));
        }
        return tr(code, "packai.reply.interact_get", action, quote(code, heldName), quote(code, targetName));
    }

    public static String interactUse(String code, String targetName, String resultName, String via) {
        return tr(
                code,
                "packai.reply.interact_use",
                interactViaLabel(code, via),
                quote(code, targetName),
                quote(code, resultName));
    }

    public static String interactUseSelf(String code, String resultName, String via) {
        return tr(code, "packai.reply.interact_use_self", interactViaLabel(code, via), quote(code, resultName));
    }

    public static String interactAsTarget(String code, String heldName, String resultName, String via) {
        String action = interactViaLabel(code, via);
        if (heldName == null || heldName.isBlank()) {
            return tr(code, "packai.reply.interact_as_target_any", action, quote(code, resultName));
        }
        return tr(code, "packai.reply.interact_as_target", action, quote(code, heldName), quote(code, resultName));
    }

    public static String interactViaLabel(String code, String via) {
        if (via == null || via.isBlank() || "_".equals(via)) {
            return tr(code, "packai.reply.interact_via.right_click");
        }
        String key = switch (via.trim().toLowerCase(Locale.ROOT)) {
            case "left_click" -> "packai.reply.interact_via.left_click";
            case "break" -> "packai.reply.interact_via.break";
            case "entity" -> "packai.reply.interact_via.entity";
            case "food" -> "packai.reply.interact_via.food";
            default -> "packai.reply.interact_via.right_click";
        };
        return tr(code, key);
    }

    /** Item tooltip / organ description line. */
    public static String itemDesc(String code, String text) {
        return tr(code, "packai.reply.item_desc", text == null ? "" : text);
    }

    /** Passive score line (e.g. health=2). */
    public static String itemScore(String code, String scoreEq) {
        return tr(code, "packai.reply.item_score", scoreEq == null ? "" : scoreEq);
    }

    /** Active trigger / event hook (e.g. key_active, entity_tick). */
    public static String itemTriggers(String code, String eventId) {
        return tr(code, "packai.reply.item_triggers", eventId == null ? "" : eventId);
    }

    public static String rightClickGet(String code, String heldName, String blockName) {
        return interactGet(code, heldName, blockName, "right_click");
    }

    public static String rightClickUse(String code, String blockName, String resultName) {
        return interactUse(code, blockName, resultName, "right_click");
    }

    public static String rightClickOnBlock(String code, String heldName, String resultName) {
        return interactAsTarget(code, heldName, resultName, "right_click");
    }

    public static String compactCycle(String code, String need) {
        return tr(code, "packai.reply.compact_cycle", quote(code, need));
    }

    public static String localAcquireHeader(String code, String name) {
        return tr(code, "packai.reply.local_acquire_header", quote(code, name));
    }

    public static boolean isScriptNeedsLine(String line) {
        if (line == null) {
            return false;
        }
        return line.contains(tr("zh_tw", "packai.reply.detect.script_needs"))
                || line.contains(tr("en_us", "packai.reply.detect.script_needs"));
    }

    public static boolean isScriptRemovedLine(String line) {
        if (line == null) {
            return false;
        }
        return line.contains(tr("zh_tw", "packai.reply.detect.script_removed"))
                || line.contains(tr("en_us", "packai.reply.detect.script_removed"));
    }

    public static boolean isLlmSetupError(String answer) {
        if (answer == null) {
            return false;
        }
        return answer.startsWith(tr("zh_tw", "packai.reply.detect.llm_failed"))
                || answer.startsWith(tr("en_us", "packai.reply.detect.llm_failed"))
                || answer.startsWith(tr("zh_tw", "packai.reply.detect.cloud"))
                || answer.startsWith(tr("en_us", "packai.reply.detect.cloud"))
                || answer.startsWith(tr("zh_tw", "packai.reply.detect.ollama"))
                || answer.startsWith(tr("en_us", "packai.reply.detect.ollama"));
    }

    public static String webHeaderMixed(String code) {
        return tr(code, "packai.reply.web_header_mixed");
    }

    public static String webHeaderStrict(String code) {
        return tr(code, "packai.reply.web_header_strict");
    }

    public static String webHeaderLocalOverride(String code) {
        return tr(code, "packai.reply.web_header_local");
    }

    public static String jeiNoRecipes(String code) {
        return tr(code, "packai.reply.jei_no_recipes");
    }

    public static String jeiHintEmpty(String code) {
        return tr(code, "packai.reply.jei_hint_empty");
    }

    public static String jeiHeader(String code, String itemName, String skipLabel) {
        return jeiHeader(code, itemName, "", skipLabel);
    }

    public static String jeiHeader(String code, String itemName, String itemId, String skipLabel) {
        String idPart = itemId == null || itemId.isBlank() ? "" : " [" + itemId + "]";
        return tr(code, "packai.reply.jei_header", quote(code, itemName), idPart, skipLabel);
    }

    public static String jeiEmpty(String code, String itemName) {
        return tr(code, "packai.reply.jei_empty", quote(code, itemName));
    }

    public static String jeiSectionRecipes(String code) {
        return tr(code, "packai.reply.jei_section_recipes");
    }

    public static String jeiSectionUses(String code) {
        return tr(code, "packai.reply.jei_section_uses");
    }

    public static String jeiSectionCatalyst(String code) {
        return tr(code, "packai.reply.jei_section_catalyst");
    }

    public static String jeiZeroUseful(String code, int skipped) {
        return tr(code, "packai.reply.jei_zero_useful", skipped);
    }

    public static String jeiTotals(String code, int useful, int skipped) {
        String s = tr(code, "packai.reply.jei_totals", useful);
        if (skipped > 0) {
            s += tr(code, "packai.reply.jei_totals_skipped", skipped);
        }
        return s + "\n";
    }

    public static String jeiTruncated(String code, int useful) {
        return tr(code, "packai.reply.jei_truncated", useful);
    }

    public static String jeiSkipped(String code, String cat, int n, String reason) {
        return tr(code, "packai.reply.jei_skipped", cat, n, reason);
    }

    public static String jeiCatCount(
            String code, String cat, int useful, Integer unique, int spam, boolean hitCap, int maxScan
    ) {
        StringBuilder section = new StringBuilder();
        section.append(tr(code, "packai.reply.jei_cat_count", cat, useful));
        if (unique != null) {
            section.append(tr(code, "packai.reply.jei_cat_unique", unique));
        }
        if (spam > 0) {
            section.append(tr(code, "packai.reply.jei_cat_spam", spam));
        }
        if (hitCap) {
            section.append(tr(code, "packai.reply.jei_cat_cap", maxScan));
        }
        section.append('\n');
        return section.toString();
    }

    public static String jeiNoMats(String code) {
        return tr(code, "packai.reply.jei_no_mats");
    }

    public static String jeiNoOut(String code) {
        return tr(code, "packai.reply.jei_no_out");
    }

    public static String jeiMachineLine(String code, String cats, String in, String out) {
        return tr(code, "packai.reply.jei_machine_line", quote(code, cats), in, out);
    }

    public static String spamSkipLabel(String code) {
        return tr(code, "packai.reply.spam_skip_label");
    }

    public static String craftPreferenceHint(String code) {
        return craftPreferenceHint(code, "craft");
    }

    public static String craftPreferenceHint(String code, String preferObtain) {
        String base = tr(code, "packai.reply.craft_pref_base");
        String path = preferObtain == null ? "craft" : preferObtain.trim().toLowerCase(Locale.ROOT);
        if ("last".equals(path)) {
            path = "craft";
        } else if ("first".equals(path)) {
            path = "quest";
        } else if ("normal".equals(path)) {
            path = "balanced";
        }
        String suffixKey = switch (path) {
            case "quest" -> "packai.reply.craft_pref.quest";
            case "loot" -> "packai.reply.craft_pref.loot";
            case "balanced" -> "packai.reply.craft_pref.balanced";
            default -> "packai.reply.craft_pref.craft";
        };
        return base + tr(code, suffixKey);
    }

    public static String[] seasonSubs(String code) {
        List<String> out = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            out.add(tr(code, "packai.reply.season_sub." + i));
        }
        return out.toArray(String[]::new);
    }

    public static String seasonSerene(String code, String subName, long day) {
        return tr(code, "packai.reply.season_serene", subName, day);
    }

    public static String seasonFarmersDelight(String code) {
        return tr(code, "packai.reply.season_fd");
    }

    public static String psiPromptAddon(String code) {
        return tr(code, "packai.reply.psi_addon");
    }

    public static String sourcesInstruction(String code) {
        return tr(code, "packai.reply.sources_instruction");
    }

    public static String llmStyle(String code) {
        return tr(
                code,
                "packai.reply.llm_style",
                craftPreferenceHint(code, com.skps9.packai.config.PackAiConfig.preferObtain()),
                sourcesInstruction(code));
    }

    public static String llmRules(String code, boolean questOverride, boolean questConflict, String policy) {
        if (questOverride) {
            return tr(code, "packai.reply.llm_rules.override");
        }
        if (questConflict) {
            return tr(code, "packai.reply.llm_rules.conflict");
        }
        if ("local_only".equals(policy)) {
            return tr(code, "packai.reply.llm_rules.local_only");
        }
        if ("mixed".equals(policy)) {
            return tr(code, "packai.reply.llm_rules.mixed");
        }
        return tr(code, "packai.reply.llm_rules.default");
    }

    public static String llmSystemLead(String code, String langName) {
        String name = langName == null ? code : langName;
        return tr(code, "packai.reply.llm_system_lead", name, code, name);
    }

    public static String factCheck(String code) {
        return tr(code, "packai.reply.fact_check");
    }

    public static String llmApiKeyHint(String code, int keyLen) {
        return tr(code, "packai.reply.llm_api_key_hint", keyLen);
    }

    public static String humanAcquireLabel(String code, String rel) {
        if (rel == null || rel.isBlank()) {
            return packData(code);
        }
        String pl = rel.replace('\\', '/');
        String lower = pl.toLowerCase(Locale.ROOT);
        String name = pl;
        int slash = pl.lastIndexOf('/');
        if (slash >= 0 && slash < pl.length() - 1) {
            name = pl.substring(slash + 1);
        }
        name = name.replaceFirst("\\.[^.]+$", "").replace('_', ' ');
        String kind;
        if (PackIndex.isFishingPath(lower)) {
            kind = fishingKind(code);
        } else if (PackIndex.isLootPath(lower)) {
            kind = lootKind(code);
        } else {
            kind = tradeKind(code);
        }
        return kind + quote(code, name);
    }

    public static String jeiSkippedGeneric(String code, String catTitle, int n) {
        return tr(code, "packai.reply.jei_skipped_generic", catTitle, n);
    }
}
