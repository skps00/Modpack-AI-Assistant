package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.client.jei.AskJeiClient;
import com.skps9.packai.client.jei.JeiCategoryIndex;
import com.skps9.packai.client.jei.JeiFocusMatch;
import com.skps9.packai.client.jei.JeiRecipeCards;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Collect JEI cards for an item+role and schedule them on the AI card strip (emission channel).
 * role: output | upgrade | uses (uses → INPUT / purpose-as-material).
 */
public final class RenderRecipeCardsAskTool implements AskTool {
    private static final int PER_CALL_CAP = 6;
    /** Scan wider than emit cap so filterRole can still see non-craft categories. */
    private static final int SCAN_CAP = 24;

    @Override
    public String name() {
        return "render_recipe_cards";
    }

    @Override
    public String description() {
        return "Show JEI recipe cards under the answer (card strip). "
                + "item_id=mod:id (or item=); role=output|upgrade|uses; machine=optional category title "
                + "substring (e.g. 奥术砧). Prefer item_search first when the id is unknown. "
                + "Do NOT write [[recipe_card…]] markers in the answer text.";
    }

    @Override
    public String llmDescription() {
        return "Show JEI recipe cards under the answer (card strip). "
                + "item_id=mod:id (or item=); role=output|upgrade|uses; machine=optional category substring. "
                + "Do NOT write [[recipe_card…]] markers in answer text.";
    }

    @Override
    public String toolMissNote(String item) {
        return "[TOOL_MISS] render_recipe_cards empty — no JEI card for that item/role. "
                + "Do not retry the same item_id+role+machine; try another role or omit machine; "
                + "answer with text only if still empty. Do not invent.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"item_id\":{\"type\":\"string\"},\"item\":{\"type\":\"string\"},"
                + "\"role\":{\"type\":\"string\",\"description\":\"output | upgrade | uses\"},"
                + "\"machine\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"}},"
                + "\"required\":[\"role\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        String itemId = resolveItemId(args, env);
        String role = resolveRole(args);
        String machine = resolveMachine(args);
        if (role.isBlank() || !isKnownRole(role)) {
            return "role must be output, upgrade, or uses";
        }
        if (itemId.isBlank()) {
            return missEmpty(itemId, role, 0, 0, 0);
        }
        ItemStack stack = resolveRenderItemStack(itemId, env);
        // Explicit item_id must not silently fall back to held (typed apple while holding wuren).
        if (stack.isEmpty()) {
            return missEmpty(itemId, role, 0, 0, 0);
        }
        long deadline = args == null ? System.currentTimeMillis() + 5_000L : args.deadlineMs;
        int maxOut = "uses".equals(role) ? 0 : SCAN_CAP;
        int maxIn = "uses".equals(role) ? SCAN_CAP : ("upgrade".equals(role) ? SCAN_CAP : 0);
        if ("upgrade".equals(role)) {
            // upgrade cards live in maintenance pass — need both sides probed
            maxOut = SCAN_CAP;
            maxIn = SCAN_CAP;
        }
        List<RecipeCard> pool;
        try {
            pool = AskJeiClient.recipeCardsForItem(stack, maxOut, maxIn, deadline);
        } catch (Throwable t) {
            PackAiMod.LOGGER.info(
                    "Pack AI renderCards item={} role={} scannedCats=? foundOutput=? afterFilter=0 err={}",
                    itemId, role, t.toString());
            AskTrace.renderCards(itemId, role, 0, 0, 0, 0, "", ItemVariantKeys.hasVariantKeys(stack));
            return missEmpty(itemId, role, 0, 0, 0);
        }
        int scanned = pool == null ? 0 : pool.size();
        int foundOutput = countRole(pool, "output");
        RenderMatchResult resolved = resolveScanOrCatalogFallback(pool, itemId, role, machine, env);
        List<RecipeCard> matched = resolved.matched();
        boolean catalogFallback = resolved.catalogFallback();
        boolean hasVariant = ItemVariantKeys.hasVariantKeys(stack);
        if (matched.isEmpty()) {
            PackAiMod.LOGGER.info(
                    "Pack AI renderCards item={} role={} scannedCats={} foundOutput={} afterFilter=0",
                    itemId, role, scanned, foundOutput);
            AskTrace.renderCards(itemId, role, scanned, foundOutput, 0, 0, "", hasVariant);
            return missEmpty(itemId, role, scanned, foundOutput, 0);
        }
        if (catalogFallback) {
            PackAiMod.LOGGER.info(
                    "Pack AI renderCards item={} role={} fallback=catalog cards={}",
                    itemId, role, matched.size());
        }
        int total = matched.size();
        PackAiMod.LOGGER.info(
                "Pack AI renderCards item={} role={} scannedCats={} foundOutput={} afterFilter={}",
                itemId, role, scanned, foundOutput, total);
        RecipeCard first = matched.get(0);
        AskTrace.renderCards(
                itemId, role, scanned, foundOutput, total,
                first.outputs() == null ? 0 : first.outputs().size(),
                first.primaryOutputId(),
                hasVariant);
        for (RecipeCard c : matched) {
            if (c == null) {
                continue;
            }
            AskTrace.card(
                    "check.cards",
                    c.categoryTitle(),
                    c.primaryOutputId(),
                    c.outputs() == null ? 0 : c.outputs().size(),
                    hasVariant,
                    c.sourceItemId(),
                    "tool_emit",
                    "role=" + (c.focusRole() == null ? role : c.focusRole().name()));
        }
        ModularFrameCards.KeepResult<RecipeCard> keepResult = ModularFrameCards.keepOnlyStandardRecipeCard(
                matched, RecipeCard::primaryOutputId, RecipeCard::layoutInputIds,
                c -> c.isInputUse() || c.isTrailingOptional(),
                env == null ? "" : env.frameStandardKeepOutputId,
                env == null ? java.util.List.<String>of() : env.frameStandardKeepInputIds);
        matched = keepResult.kept();
        if (keepResult.dropped() > 0 || keepResult.fallback()) {
            final String keepOut = env == null ? "" : env.frameStandardKeepOutputId;
            final String chosenIn;
            String in = "-";
            for (RecipeCard c : keepResult.kept()) {
                if (c != null && keepOut != null && !keepOut.isBlank()
                        && keepOut.equalsIgnoreCase(c.primaryOutputId())) {
                    List<String> ids = c.layoutInputIds();
                    if (ids != null && !ids.isEmpty()) {
                        in = String.join(",", ids);
                    }
                    break;
                }
            }
            chosenIn = in;
            PackAiMod.LOGGER.info(
                    "Pack AI frame-standard: keep-only kept={} chosenIn={} dropped={} mode={}",
                    keepOut, chosenIn, keepResult.dropped(),
                    keepResult.fallback() ? "fallback" : "exact");
        }
        if (matched.size() > PER_CALL_CAP) {
            // R7 uses: category diversity so one station (e.g. 自動合成×9) cannot fill all 6
            // R8-E: catalog input matches only — no diversity fill of leftover slots
            if ("uses".equals(role)) {
                matched = pickUsesPreferCatalog(matched, env, PER_CALL_CAP);
            } else {
                matched = List.copyOf(matched.subList(0, PER_CALL_CAP));
            }
        }
        if (env == null) {
            return digest(matched, sequentialRefs(matched.size()), role, total);
        }
        int room = AskLoopState.MAX_CARD_EMISSIONS - env.pendingEmissions.size();
        if (room <= 0) {
            return "累計卡數已達上限 " + AskLoopState.MAX_CARD_EMISSIONS + "，唔再出卡";
        }
        List<RecipeCard> emitted = new ArrayList<>();
        List<Integer> refs = new ArrayList<>();
        for (RecipeCard card : matched) {
            if (emitted.size() >= room) {
                break;
            }
            CardEmission em = new CardEmission(itemId, role, card);
            int ref = env.offerEmission(em);
            if (ref > 0) {
                emitted.add(card);
                refs.add(ref);
            }
        }
        if (emitted.isEmpty()) {
            if (env.pendingEmissions.size() >= AskLoopState.MAX_CARD_EMISSIONS) {
                return "累計卡數已達上限 " + AskLoopState.MAX_CARD_EMISSIONS + "，唔再出卡";
            }
            // B11 LD5: all matches were modular frame cards — not a JEI miss.
            if (env.suppressedFrameOffers > 0) {
                PackAiMod.LOGGER.info(
                        "Pack AI renderCards suppressedFrameOnly n={}",
                        env.suppressedFrameOffers);
                return "框架合成卡已隱藏（非本工具取得途徑）";
            }
            return missEmpty(itemId, role, scanned, foundOutput, 0);
        }
        String dig = digest(emitted, refs, role, total);
        if (total > PER_CALL_CAP) {
            dig = dig + "\n有 " + total + " 張，已出頭 " + emitted.size() + " 張，可加 machine=… 收窄";
        }
        return dig;
    }

    /** JEI scan + optional ask-catalog fallback when the live scan matches nothing. */
    record RenderMatchResult(List<RecipeCard> matched, boolean catalogFallback) {}

    /**
     * Filter/coalesce JEI scan; when empty, reuse cards already collected on this ask's catalog.
     * Non-empty scan → catalog is not consulted.
     */
    static RenderMatchResult resolveScanOrCatalogFallback(
            List<RecipeCard> scanPool,
            String itemId,
            String role,
            String machine,
            AskToolEnv env
    ) {
        List<RecipeCard> matched = filterRole(scanPool, role, machine);
        matched = JeiRecipeCards.coalesceMirrorEmission(matched);
        if (!matched.isEmpty()) {
            return new RenderMatchResult(matched, false);
        }
        List<RecipeCard> catalog = env == null || env.catalogCards == null
                ? List.of() : env.catalogCards;
        List<RecipeCard> fromCatalog = pickCatalogFallback(itemId, role, machine, catalog, SCAN_CAP);
        fromCatalog = JeiRecipeCards.coalesceMirrorEmission(fromCatalog);
        if (!fromCatalog.isEmpty()) {
            return new RenderMatchResult(fromCatalog, true);
        }
        List<RecipeCard> fromIndex = pickIndexFallback(itemId, role, machine, SCAN_CAP);
        fromIndex = JeiRecipeCards.coalesceMirrorEmission(fromIndex);
        return new RenderMatchResult(fromIndex, false);
    }

    static List<RecipeCard> pickIndexFallback(String itemId, String role, String machine, int cap) {
        if (!indexFallbackEnabled || cap <= 0 || itemId == null || itemId.isBlank()) {
            return List.of();
        }
        Item item = Registry.ITEM.get(new ResourceLocation(itemId.trim()));
        if (item == null) {
            return List.of();
        }
        List<RecipeCard> raw = JeiCategoryIndex.cardsViaIndex(item, role, cap);
        if (raw.isEmpty()) {
            return List.of();
        }
        return filterRole(raw, role, machine);
    }

    static volatile boolean indexFallbackEnabled = true;

    /**
     * Cards from {@link AskToolEnv#catalogCards} for render when JEI scan found none.
     * Output/upgrade: primary output (or focus source when output id blank) equals itemId;
     * uses: focus item as material ({@link RecipeCard#sourceItemId()}).
     */
    static List<RecipeCard> pickCatalogFallback(
            String itemId,
            String role,
            String machine,
            List<RecipeCard> catalog,
            int cap
    ) {
        if (catalog == null || catalog.isEmpty() || cap <= 0) {
            return List.of();
        }
        String want = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        if (want.isEmpty()) {
            return List.of();
        }
        List<RecipeCard> roleFiltered = filterRole(catalog, role, machine);
        if (roleFiltered.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> seenRecipeKeys = new java.util.LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();
        for (RecipeCard c : roleFiltered) {
            if (c == null || c.isEmpty() || !catalogItemMatches(c, role, want)) {
                continue;
            }
            String key = catalogRecipeKey(c);
            if (!key.isEmpty() && !seenRecipeKeys.add(key)) {
                continue;
            }
            out.add(c);
            if (out.size() >= cap) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static boolean catalogItemMatches(RecipeCard c, String role, String itemIdLower) {
        if ("uses".equals(role)) {
            String src = c.sourceItemId();
            return src != null && !src.isBlank() && src.equalsIgnoreCase(itemIdLower);
        }
        String outId = c.primaryOutputId();
        if (outId != null && !outId.isBlank() && outId.equalsIgnoreCase(itemIdLower)) {
            return true;
        }
        String src = c.sourceItemId();
        return src != null && !src.isBlank() && src.equalsIgnoreCase(itemIdLower);
    }

    /** Stable dedupe within catalog fallback (scan pool already empty). */
    private static String catalogRecipeKey(RecipeCard c) {
        String cat = c.categoryTitle() == null ? "" : c.categoryTitle();
        String out = c.primaryOutputId() == null ? "" : c.primaryOutputId();
        String src = c.sourceItemId() == null ? "" : c.sourceItemId();
        return cat + "|" + out + "|" + src + "|" + c.focusRole();
    }

    /**
     * JEI focus stack: bare id unless ask env has same registry id with NBT (focus / held sample).
     */
    static ItemStack resolveRenderItemStack(String itemId, AskToolEnv env) {
        ItemStack bare = ItemResolver.stackFromId(itemId);
        if (bare.isEmpty()) {
            return bare;
        }
        ItemStack tagged = pickEnvStackForItem(itemId, env, true);
        if (!tagged.isEmpty()) {
            int tagKeys = tagged.getTag() == null ? 0 : tagged.getTag().getAllKeys().size();
            PackAiMod.LOGGER.info(
                    "Pack AI renderCards item={} nbt_stack=true tags={}",
                    itemId, tagKeys);
            return tagged;
        }
        return bare;
    }

    /** Harness negative control: always use env focus stack (ignores item id). */
    static ItemStack harnessAlwaysEnvStack(String itemId, AskToolEnv env) {
        if (env != null && env.stack != null && !env.stack.isEmpty()) {
            return env.stack.copy();
        }
        return ItemResolver.stackFromId(itemId);
    }

    private static ItemStack pickEnvStackForItem(String itemId, AskToolEnv env, boolean requireTag) {
        if (env == null || itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        for (ItemStack cand : envStackCandidates(env)) {
            if (cand.isEmpty() || !JeiFocusMatch.sameRegistryId(cand, itemId)) {
                continue;
            }
            if (requireTag && !cand.hasTag()) {
                continue;
            }
            return cand.copy();
        }
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> envStackCandidates(AskToolEnv env) {
        List<ItemStack> out = new ArrayList<>(2);
        if (env.stack != null && !env.stack.isEmpty()) {
            out.add(env.stack);
        }
        if (env.held != null && env.held.hasSample()) {
            ItemStack held = env.held.sample();
            if (!held.isEmpty()) {
                out.add(held);
            }
        }
        return out;
    }

    /** Harness: simulate disabled catalog fallback (negative control in checks). */
    static RenderMatchResult harnessResolveWithoutCatalogFallback(
            List<RecipeCard> scanPool,
            String itemId,
            String role,
            String machine
    ) {
        List<RecipeCard> matched = filterRole(scanPool, role, machine);
        matched = JeiRecipeCards.coalesceMirrorEmission(matched);
        return new RenderMatchResult(matched, false);
    }

    /** Harness: catalog on, id_index off (negative control). */
    static RenderMatchResult harnessResolveWithoutIndexFallback(
            List<RecipeCard> scanPool,
            String itemId,
            String role,
            String machine,
            AskToolEnv env
    ) {
        boolean prev = indexFallbackEnabled;
        indexFallbackEnabled = false;
        try {
            return resolveScanOrCatalogFallback(scanPool, itemId, role, machine, env);
        } finally {
            indexFallbackEnabled = prev;
        }
    }

    /**
     * R8-E uses pick: when any catalog input-use card matches JEI scan, emit those only
     * (prompt order, up to cap) — no diversity fill of leftover slots. Empty catalog /
     * no overlap → {@link #pickUsesWithCategoryDiversity} unchanged.
     */
    static List<RecipeCard> pickUsesPreferCatalog(
            List<RecipeCard> matched, AskToolEnv env, int cap
    ) {
        if (matched == null || matched.isEmpty() || cap <= 0) {
            return List.of();
        }
        List<RecipeCard> catalog = env == null || env.catalogCards == null
                ? List.of() : env.catalogCards;
        if (catalog.isEmpty()) {
            return pickUsesWithCategoryDiversity(matched, cap);
        }
        java.util.LinkedHashMap<String, RecipeCard> matchedByKey = new java.util.LinkedHashMap<>();
        for (RecipeCard c : matched) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            matchedByKey.putIfAbsent(usesCatalogMatchKey(c), c);
        }
        List<RecipeCard> catalogUses = new ArrayList<>();
        java.util.LinkedHashSet<String> taken = new java.util.LinkedHashSet<>();
        for (RecipeCard cat : catalog) {
            if (cat == null || cat.isEmpty() || !cat.isInputUse()) {
                continue;
            }
            String key = usesCatalogMatchKey(cat);
            RecipeCard hit = matchedByKey.get(key);
            if (hit != null && taken.add(key)) {
                catalogUses.add(hit);
            }
        }
        if (catalogUses.isEmpty()) {
            return pickUsesWithCategoryDiversity(matched, cap);
        }
        List<RecipeCard> out = new ArrayList<>(Math.min(cap, catalogUses.size()));
        for (RecipeCard c : catalogUses) {
            if (out.size() >= cap) {
                break;
            }
            out.add(c);
        }
        return List.copyOf(out);
    }

    /** Match catalog ↔ JEI-scan uses cards (source item + category + primary output). */
    private static String usesCatalogMatchKey(RecipeCard c) {
        String src = c.sourceItemId() == null ? "" : c.sourceItemId();
        String cat = c.categoryTitle() == null ? "" : c.categoryTitle();
        String out = c.primaryOutputId() == null ? "" : c.primaryOutputId();
        return src + "|" + cat + "|" + out;
    }

    /**
     * R7 uses pick: preserve original order; pass1 ≤1 per categoryTitle, pass2 allow
     * 2nd from same category (max 2). Avoids 「自動合成×9」filling the whole cap.
     * Shared with AskService uses-supplement (R8) — not JeiRecipeCards.pickWithCategoryDiversity
     * (that helper needs questSigs + signature round-robin; Ask path only has ordered list).
     */
    public static List<RecipeCard> pickUsesWithCategoryDiversity(List<RecipeCard> matched, int cap) {
        if (matched == null || matched.isEmpty() || cap <= 0) {
            return List.of();
        }
        if (matched.size() <= cap) {
            return List.copyOf(matched);
        }
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.LinkedHashSet<RecipeCard> taken = new java.util.LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>(cap);
        for (RecipeCard c : matched) {
            if (out.size() >= cap) {
                break;
            }
            if (c == null || c.isEmpty()) {
                continue;
            }
            String k = c.categoryTitle() == null ? "?" : c.categoryTitle().trim().toLowerCase(Locale.ROOT);
            if (counts.getOrDefault(k, 0) >= 1) {
                continue;
            }
            out.add(c);
            taken.add(c);
            counts.merge(k, 1, Integer::sum);
        }
        if (out.size() < cap) {
            for (RecipeCard c : matched) {
                if (out.size() >= cap) {
                    break;
                }
                if (c == null || c.isEmpty() || taken.contains(c)) {
                    continue;
                }
                String k = c.categoryTitle() == null ? "?" : c.categoryTitle().trim().toLowerCase(Locale.ROOT);
                if (counts.getOrDefault(k, 0) >= 2) {
                    continue;
                }
                out.add(c);
                taken.add(c);
                counts.merge(k, 1, Integer::sum);
            }
        }
        // Pass 3: if still short (few categories), fill in original order up to cap
        if (out.size() < cap) {
            for (RecipeCard c : matched) {
                if (out.size() >= cap) {
                    break;
                }
                if (c == null || c.isEmpty() || taken.contains(c)) {
                    continue;
                }
                out.add(c);
                taken.add(c);
            }
        }
        return List.copyOf(out);
    }

    private static String missEmpty(String itemId, String role, int scanned, int foundOut, int after) {
        return "該 item 冇 role=" + role + " 嘅 JEI 卡（已掃完；勿用相同 args 重試；可改 role 或 machine=）";
    }

    private static int countRole(List<RecipeCard> pool, String role) {
        if (pool == null || pool.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (RecipeCard c : pool) {
            if (c != null && !c.isEmpty() && roleMatches(c, role)) {
                n++;
            }
        }
        return n;
    }

    static List<RecipeCard> filterRole(List<RecipeCard> pool, String role, String machine) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        String m = machine == null ? "" : machine.trim().toLowerCase(Locale.ROOT);
        List<RecipeCard> out = new ArrayList<>();
        for (RecipeCard c : pool) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (!roleMatches(c, role)) {
                continue;
            }
            if (!m.isEmpty()) {
                String cat = c.categoryTitle() == null ? "" : c.categoryTitle().toLowerCase(Locale.ROOT);
                if (!cat.contains(m)) {
                    continue;
                }
            }
            out.add(c);
        }
        return out;
    }

    private static boolean roleMatches(RecipeCard c, String role) {
        return switch (role) {
            case "output" -> "output".equals(c.promptRole()) || "quest".equals(c.promptRole());
            case "upgrade" -> c.isUpgrade();
            case "uses" -> c.isInputUse();
            default -> false;
        };
    }

    private static boolean isKnownRole(String role) {
        return "output".equals(role) || "upgrade".equals(role) || "uses".equals(role);
    }

    private static List<Integer> sequentialRefs(int n) {
        ArrayList<Integer> refs = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            refs.add(i);
        }
        return refs;
    }

    /**
     * R5.3 digest: ask-scope ASCII {@code [card:N]} + short brief
     * (category + ingredients → output id).
     */
    private static String digest(List<RecipeCard> cards, List<Integer> refs, String role, int totalFound) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(cards.size(), refs == null ? 0 : refs.size());
        for (int i = 0; i < n; i++) {
            RecipeCard c = cards.get(i);
            int ref = refs.get(i);
            if (sb.length() > 0) {
                sb.append('\n');
            }
            String machine = c.categoryTitle() == null || c.categoryTitle().isBlank()
                    ? "?" : c.categoryTitle();
            String brief = cardBriefAscii(c);
            sb.append("[card:").append(ref).append("] ").append(machine);
            if (!brief.isEmpty()) {
                sb.append(' ').append(brief);
            }
            sb.append(" role=").append(role);
        }
        return sb.toString();
    }

    /**
     * Ingredient brief from id-keyed frequencies. Skips {@code outputId} (id or display label).
     * At most {@code maxKinds} kinds; remainder shown as trailing {@code +…}.
     */
    static String briefFromFreq(
            java.util.LinkedHashMap<String, Integer> freqBySize,
            String outputId,
            int maxKinds
    ) {
        return briefFromFreq(freqBySize, null, outputId, maxKinds);
    }

    static String briefFromFreq(
            java.util.LinkedHashMap<String, Integer> freqBySize,
            java.util.LinkedHashMap<String, String> labelById,
            String outputId,
            int maxKinds
    ) {
        if (maxKinds <= 0 || freqBySize == null || freqBySize.isEmpty()) {
            return "";
        }
        String skip = outputId == null ? "" : outputId.trim();
        java.util.ArrayList<java.util.Map.Entry<String, Integer>> materials = new java.util.ArrayList<>();
        for (var e : freqBySize.entrySet()) {
            String id = e.getKey();
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!skip.isEmpty() && id.equalsIgnoreCase(skip)) {
                continue;
            }
            String label = labelById == null ? id : labelById.getOrDefault(id, id);
            if (!skip.isEmpty() && label.equalsIgnoreCase(skip)) {
                continue;
            }
            materials.add(e);
        }
        if (materials.isEmpty()) {
            return "";
        }
        int totalKinds = materials.size();
        int show = Math.min(maxKinds, totalKinds);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < show; i++) {
            var e = materials.get(i);
            String id = e.getKey();
            String label = labelById == null ? id : labelById.getOrDefault(id, id);
            if (b.length() > 0) {
                b.append('+');
            }
            b.append(label);
            int n = e.getValue() == null ? 1 : e.getValue();
            if (n > 1) {
                b.append('×').append(n);
            }
        }
        if (totalKinds > maxKinds) {
            b.append("+…");
        }
        return b.toString();
    }

    /** ASCII-leaning brief: {@code iron×2+stick -> minecraft:iron_sword}. */
    static String cardBriefAscii(RecipeCard c) {
        String outIdRaw = c.primaryOutputId();
        final String outId = outIdRaw == null ? "" : outIdRaw;
        java.util.List<ItemStack> slotList = c.layoutInputStacks();
        java.util.List<SlotOptions> slotOpts = c.slotOptions();
        String lang = ReplyLang.current();
        java.util.ArrayList<CardLabelMerge.Entry> mergeEntries = new java.util.ArrayList<>();
        if (slotList != null) {
            for (int i = 0; i < slotList.size(); i++) {
                ItemStack s = slotList.get(i);
                if (s == null || s.isEmpty()) {
                    continue;
                }
                var regKey = Registry.ITEM.getKey(s.getItem());
                String id = regKey != null ? regKey.toString() : "";
                if (id.isBlank()) {
                    continue;
                }
                if (outId != null && !outId.isBlank() && outId.equalsIgnoreCase(id)) {
                    continue;
                }
                String name = Plainify.stripMcFormat(s.getHoverName().getString());
                if (name.isBlank()) {
                    name = id;
                }
                String label;
                if (slotOpts == null) {
                    label = name;
                } else {
                    SlotOptions opt = i < slotOpts.size() ? slotOpts.get(i) : null;
                    label = CardSlotLabels.labelForStack(name, opt, s, lang);
                }
                int units = Math.max(1, s.getCount());
                for (int u = 0; u < units; u++) {
                    mergeEntries.add(new CardLabelMerge.Entry(id, label));
                }
            }
        }
        try {
            String outIdLog = c.primaryOutputId();
            if (outIdLog == null || outIdLog.isBlank()) {
                outIdLog = "?";
            }
            int gridN = slotList == null ? 0 : slotList.size();
            int resolved = 0;
            if (slotList != null) {
                for (ItemStack st : slotList) {
                    if (st != null && !st.isEmpty()) {
                        resolved++;
                    }
                }
            }
            int empty = Math.max(0, gridN - resolved);
            StringBuilder idsJoined = new StringBuilder();
            int shownIds = 0;
            int nonEmptyIds = 0;
            if (slotList != null) {
                for (ItemStack st : slotList) {
                    if (st == null || st.isEmpty()) {
                        continue;
                    }
                    nonEmptyIds++;
                    if (shownIds >= 9) {
                        continue;
                    }
                    if (shownIds > 0) {
                        idsJoined.append(',');
                    }
                    var key = Registry.ITEM.getKey(st.getItem());
                    idsJoined.append(key != null ? key.toString() : "?");
                    shownIds++;
                }
            }
            if (nonEmptyIds > 9) {
                idsJoined.append('…');
            }
            int slotsCand = 0;
            if (slotOpts != null) {
                for (SlotOptions o : slotOpts) {
                    if (o != null) {
                        slotsCand += o.count();
                    }
                }
            }
            PackAiMod.LOGGER.info(
                    "ask.card.slots out={} layout={} grid={} resolved={} empty={} slots={} ids={}",
                    outIdLog, c.layout(), gridN, resolved, empty, slotsCand, idsJoined);
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("ask.card.slots log skipped", t);
        }
        java.util.Set<String> skipOut = outId.isBlank()
                ? java.util.Set.of()
                : java.util.Set.of(outId);
        StringBuilder b = new StringBuilder(
                CardLabelMerge.merge(mergeEntries, skipOut, 6));
        String outLabel = outId == null ? "" : outId;
        if (outLabel.isBlank()) {
            if (c.outputs() != null && !c.outputs().isEmpty()) {
                ItemStack o = c.outputs().get(0);
                if (o != null && !o.isEmpty()) {
                    outLabel = Plainify.stripMcFormat(o.getHoverName().getString());
                }
            }
        }
        if (!outLabel.isBlank()) {
            if (b.length() > 0) {
                b.append(" -> ");
            }
            b.append(outLabel);
        }
        return b.toString();
    }

    private static String resolveItemId(AskToolArgs args, AskToolEnv env) {
        if (args != null) {
            String id = jsonString(args.argumentsJson, "item_id");
            if (id.isBlank()) {
                id = jsonString(args.argumentsJson, "item");
            }
            if (!id.isBlank()) {
                return id.trim();
            }
            if (args.itemId != null && !args.itemId.isBlank()) {
                return args.itemId.trim();
            }
        }
        if (env != null && env.held != null && env.held.isPresent()) {
            return env.held.id();
        }
        return "";
    }

    private static String resolveRole(AskToolArgs args) {
        if (args == null) {
            return "";
        }
        String role = jsonString(args.argumentsJson, "role");
        if (role.isBlank() && args.dumpLevel != null && !args.dumpLevel.isBlank()) {
            String d = args.dumpLevel.trim().toLowerCase(Locale.ROOT);
            // output/input overlap jei dump levels — still valid render roles
            if (isKnownRole(d)) {
                role = d;
            } else if ("input".equals(d)) {
                role = "uses";
            } else if (!AskToolLoop.isDumpLevel(args.dumpLevel)) {
                role = d;
            }
        }
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveMachine(AskToolArgs args) {
        if (args == null) {
            return "";
        }
        String m = jsonString(args.argumentsJson, "machine");
        if (m.isBlank()) {
            m = jsonString(args.argumentsJson, "query");
        }
        return m == null ? "" : m.trim();
    }

    private static String jsonString(String argsJson, String key) {
        if (argsJson == null || argsJson.isBlank() || key == null) {
            return "";
        }
        try {
            JsonObject o = JsonParser.parseString(argsJson).getAsJsonObject();
            if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
                return o.get(key).getAsString();
            }
        } catch (Exception ignored) {
            // malformed
        }
        return "";
    }
}
