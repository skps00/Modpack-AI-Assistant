package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Enrich JEI ingredient labels with real craft constraints (enchant / custom-data ints).
 * Supports both sample≠gate and sample=gate in the same pack — see {@link ExtrasMode}.
 */
public final class IngredientReqHints {
    private static final int MAX_EXTRAS = 8;
    private static final int MAX_NBT_INTS = 8;

    /**
     * How aggressively to attach NBT / tooltip extras.
     * <ul>
     *   <li>{@link #NONE} — display name only</li>
     *   <li>{@link #KEEP_ONLY} — only keep-pattern gates (auto when bare stack accepted)</li>
     *   <li>{@link #ALL} — all extras minus skip patterns</li>
     * </ul>
     */
    public enum ExtrasMode {
        NONE,
        KEEP_ONLY,
        ALL
    }

    private IngredientReqHints() {}

    /** Map config policy (+ optional bare accept) to an extras mode. */
    public static ExtrasMode modeForPolicy(String policy, boolean acceptsBare) {
        if ("never".equals(policy)) {
            return ExtrasMode.NONE;
        }
        if ("always".equals(policy)) {
            return ExtrasMode.ALL;
        }
        // auto: bare accepted → sample may still hold keep-pattern gates; else full filter
        return acceptsBare ? ExtrasMode.KEEP_ONLY : ExtrasMode.ALL;
    }

    /**
     * Display name, plus extras per {@code mode}.
     */
    public static String richLabel(ItemStack stack, String lang, ExtrasMode mode) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String name = Plainify.stripMcFormat(stack.getHoverName().getString()).trim();
        if (name.isEmpty()) {
            name = "?";
        }
        if (mode == null || mode == ExtrasMode.NONE) {
            return name;
        }
        List<String> extras = collectExtras(stack, mode);
        if (extras.isEmpty()) {
            return name;
        }
        String join = ReplyLang.sourceJoin(lang);
        return name + "（" + String.join(join, extras) + "）";
    }

    /**
     * Legacy: {@code nbtMatters=false} → {@link ExtrasMode#NONE}; true → {@link ExtrasMode#ALL}.
     */
    public static String richLabel(ItemStack stack, String lang, boolean nbtMatters) {
        return richLabel(stack, lang, nbtMatters ? ExtrasMode.ALL : ExtrasMode.NONE);
    }

    /** Convenience: attach all filtered extras. */
    public static String richLabel(ItemStack stack, String lang) {
        return richLabel(stack, lang, ExtrasMode.ALL);
    }

    /**
     * Label a vanilla {@link Ingredient}: prefer gates from the original recipe request
     * ({@link RecipeIngredientGates}), then sample extras per policy.
     */
    public static String labelForIngredient(Ingredient ingredient, String lang) {
        return labelForIngredient(ingredient, lang, ItemStack.EMPTY);
    }

    /**
     * @param prefer when ingredient accepts this stack (e.g. #planks + spruce), use its name
     *               instead of the first tag sample (usually oak).
     */
    public static String labelForIngredient(Ingredient ingredient, String lang, ItemStack prefer) {
        if (ingredient == null || ingredient.isEmpty()) {
            return "";
        }
        ItemStack[] items = ingredient.getItems();
        if (items == null || items.length == 0) {
            return "";
        }
        List<ItemStack> alts = new ArrayList<>();
        for (ItemStack s : items) {
            if (s != null && !s.isEmpty()) {
                alts.add(s);
            }
        }
        if (alts.isEmpty()) {
            return "";
        }
        String base = labelForAlternatives(alts, prefer, lang);
        if (base.isEmpty()) {
            return "";
        }
        String policy = PackAiConfig.ingredientNbtPolicy();
        if ("never".equals(policy) || alts.size() > 1) {
            // Multi-choice: tag / any-of is enough — skip sample NBT noise.
            return base;
        }
        ItemStack sample = pickSample(ingredient, items, prefer);
        LinkedHashSet<String> extras = new LinkedHashSet<>();
        extras.addAll(RecipeIngredientGates.fromIngredient(ingredient));
        boolean bare = acceptsBare(ingredient, items);
        ExtrasMode mode = modeForPolicy(policy, bare);
        if (mode != ExtrasMode.NONE) {
            extras.addAll(collectExtras(sample, mode));
        }
        if (extras.isEmpty() || base.indexOf('（') >= 0) {
            return base;
        }
        List<String> list = new ArrayList<>(extras);
        if (list.size() > MAX_EXTRAS) {
            list = list.subList(0, MAX_EXTRAS);
        }
        String join = ReplyLang.sourceJoin(lang);
        return base + "（" + String.join(join, list) + "）";
    }

    /**
     * Label a JEI slot / tag OR-list: one sample name + {@code #tag} or any-of (N).
     */
    public static String labelForAlternatives(List<ItemStack> alts, ItemStack prefer, String lang) {
        if (alts == null || alts.isEmpty()) {
            return "";
        }
        List<ItemStack> clean = new ArrayList<>();
        for (ItemStack s : alts) {
            if (s != null && !s.isEmpty()) {
                clean.add(s);
            }
        }
        if (clean.isEmpty()) {
            return "";
        }
        ItemStack sample = pickPrefer(clean, prefer);
        String name = Plainify.stripMcFormat(sample.getHoverName().getString()).trim();
        if (name.isEmpty()) {
            name = "?";
        }
        if (clean.size() == 1) {
            return name;
        }
        String tag = commonTagId(clean);
        if (tag != null && !tag.isEmpty()) {
            return name + "（" + tag + "）";
        }
        return name + "（" + ReplyLang.anyOfN(lang, clean.size()) + "）";
    }

    /**
     * Collapse a flat JEI ingredient list (slot0 alts…, slot1 alts…) into one sample per
     * logical OR-group. Groups consecutive stacks that share a collapsible item tag.
     */
    public static List<ItemStack> collapseAlternatives(List<ItemStack> flat, ItemStack prefer) {
        List<ItemStack> out = new ArrayList<>();
        if (flat == null || flat.isEmpty()) {
            return out;
        }
        int i = 0;
        while (i < flat.size()) {
            int j = i + 1;
            while (j < flat.size() && sharesCollapsibleTag(flat.subList(i, j + 1))) {
                j++;
            }
            out.add(pickPrefer(flat.subList(i, j), prefer).copy());
            i = j;
        }
        return out;
    }

    /** True when {@code group} (size≥2) shares a tag that looks like OR-alternatives. */
    public static boolean sharesCollapsibleTag(List<ItemStack> group) {
        return commonTagId(group) != null;
    }

    /**
     * Narrowest shared item tag id ({@code #ns:path}), or null if not an OR-tag group.
     * Prefers exact size match, then pack-specific tags (not {@code c}/{@code forge}/…),
     * so incremental collapse of large tags works while copper+iron under {@code #c:ingots} do not.
     */
    public static String commonTagId(List<ItemStack> group) {
        if (group == null || group.size() < 2) {
            return null;
        }
        Set<TagKey<Item>> common = null;
        for (ItemStack stack : group) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            Set<TagKey<Item>> tags = new HashSet<>();
            stack.getItem().builtInRegistryHolder().tags().forEach(tags::add);
            if (tags.isEmpty()) {
                return null;
            }
            if (common == null) {
                common = new HashSet<>(tags);
            } else {
                common.retainAll(tags);
            }
            if (common.isEmpty()) {
                return null;
            }
        }
        if (common == null || common.isEmpty()) {
            return null;
        }
        int n = group.size();
        TagKey<Item> bestExact = null;
        int bestExactSize = Integer.MAX_VALUE;
        TagKey<Item> bestPack = null;
        int bestPackSize = Integer.MAX_VALUE;
        TagKey<Item> bestLoose = null;
        int bestLooseSize = Integer.MAX_VALUE;
        for (TagKey<Item> tag : common) {
            int size = tagSize(tag);
            if (size < n) {
                continue;
            }
            if (size == n && size < bestExactSize) {
                bestExact = tag;
                bestExactSize = size;
            }
            if (!isBroadTagNamespace(tag) && size < bestPackSize) {
                bestPack = tag;
                bestPackSize = size;
            }
            if (n >= 3 && size < bestLooseSize) {
                bestLoose = tag;
                bestLooseSize = size;
            }
        }
        TagKey<Item> chosen = bestExact != null ? bestExact : (bestPack != null ? bestPack : bestLoose);
        if (chosen == null) {
            return null;
        }
        ResourceLocation id = chosen.location();
        return id == null ? null : "#" + id;
    }

    /** Vanilla / common convention tags — too wide to treat size-2 as OR-alternatives. */
    static boolean isBroadTagNamespace(TagKey<Item> tag) {
        if (tag == null || tag.location() == null) {
            return true;
        }
        String ns = tag.location().getNamespace();
        return "minecraft".equals(ns) || "c".equals(ns) || "forge".equals(ns) || "neoforge".equals(ns);
    }

    private static int tagSize(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag).map(set -> {
            int n = 0;
            for (Holder<Item> ignored : set) {
                n++;
                if (n > 4096) {
                    break;
                }
            }
            return n;
        }).orElse(0);
    }

    static ItemStack pickPrefer(List<ItemStack> alts, ItemStack prefer) {
        if (prefer != null && !prefer.isEmpty()) {
            for (ItemStack s : alts) {
                if (s != null && !s.isEmpty() && ItemStack.isSameItemSameComponents(s, prefer)) {
                    return s;
                }
            }
            for (ItemStack s : alts) {
                if (s != null && !s.isEmpty() && s.is(prefer.getItem())) {
                    return s;
                }
            }
        }
        for (ItemStack s : alts) {
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    static ItemStack pickSample(Ingredient ingredient, ItemStack[] items, ItemStack prefer) {
        if (prefer != null && !prefer.isEmpty()) {
            try {
                if (ingredient != null && ingredient.test(prefer)) {
                    return prefer;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (items == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack s : items) {
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** True if any listed item matches the ingredient as a bare (no-component) stack. */
    public static boolean acceptsBare(Ingredient ingredient, ItemStack[] items) {
        if (ingredient == null || items == null) {
            return false;
        }
        for (ItemStack s : items) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            ItemStack bare = new ItemStack(s.getItem(), 1);
            try {
                if (ingredient.test(bare)) {
                    return true;
                }
            } catch (Exception ignored) {
                // custom ingredient edge cases
            }
        }
        return false;
    }

    static List<String> collectExtras(ItemStack stack, ExtrasMode mode) {
        if (mode == null || mode == ExtrasMode.NONE) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (mode == ExtrasMode.ALL) {
            addEnchantLabels(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY), out);
            addEnchantLabels(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY), out);
        }
        addCustomDataInts(stack, out, mode);
        boolean tip = PackAiConfig.ingredientTooltipAsReq() || mode == ExtrasMode.KEEP_ONLY;
        if (tip && out.size() < MAX_EXTRAS) {
            addExtraTooltipLines(stack, out, mode);
        }
        List<String> list = new ArrayList<>(out);
        if (list.size() > MAX_EXTRAS) {
            return List.copyOf(list.subList(0, MAX_EXTRAS));
        }
        return List.copyOf(list);
    }

    /** True if text matches a configured skip substring (display noise). */
    public static boolean matchesSkip(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return containsAny(text, PackAiConfig.ingredientNbtSkipPatterns());
    }

    /** True if text matches a configured keep substring (likely craft gate). */
    public static boolean matchesKeep(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (containsAny(text, PackAiConfig.ingredientNbtKeepPatterns())) {
            return true;
        }
        // Generic: modid:attr style keys (organ scores, custom attributes) — not brand names.
        return looksLikeNamespacedAttrKey(text);
    }

    /**
     * {@code namespace:path} numeric attribute keys used by many packs as sample gates.
     * Excludes vanilla/loader namespaces to limit noise.
     */
    static boolean looksLikeNamespacedAttrKey(String text) {
        String key = text.trim();
        if (key.length() < 3 || key.length() > 64 || key.indexOf(' ') >= 0) {
            return false;
        }
        int colon = key.indexOf(':');
        if (colon <= 0 || colon >= key.length() - 1 || key.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        String ns = key.substring(0, colon).toLowerCase(Locale.ROOT);
        if ("minecraft".equals(ns) || "forge".equals(ns) || "neoforge".equals(ns) || "c".equals(ns)) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == ':' || c == '_' || c == '/' || c == '.') {
                continue;
            }
            if (c >= 'a' && c <= 'z') {
                continue;
            }
            if (c >= 'A' && c <= 'Z') {
                continue;
            }
            if (c >= '0' && c <= '9') {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean containsAny(String text, List<String> patterns) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String pat : patterns) {
            if (!pat.isEmpty() && lower.contains(pat)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this key/line should be attached under {@code mode} (skip always wins). */
    static boolean allowExtra(String text, ExtrasMode mode) {
        if (text == null || text.isBlank() || matchesSkip(text)) {
            return false;
        }
        if (mode == ExtrasMode.KEEP_ONLY) {
            return matchesKeep(text);
        }
        return mode == ExtrasMode.ALL;
    }

    private static void addEnchantLabels(ItemEnchantments enchants, LinkedHashSet<String> out) {
        if (enchants == null || enchants.isEmpty()) {
            return;
        }
        try {
            for (var entry : enchants.entrySet()) {
                if (out.size() >= MAX_EXTRAS) {
                    break;
                }
                Holder<Enchantment> holder = entry.getKey();
                int level = entry.getIntValue();
                if (holder == null || level <= 0) {
                    continue;
                }
                String label = Plainify.stripMcFormat(Enchantment.getFullname(holder, level).getString()).trim();
                if (!label.isEmpty() && !matchesSkip(label)) {
                    out.add(label);
                }
            }
        } catch (Exception ignored) {
            // mapping differences
        }
    }

    private static void addCustomDataInts(ItemStack stack, LinkedHashSet<String> out, ExtrasMode mode) {
        try {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                return;
            }
            Map<String, Double> found = new LinkedHashMap<>();
            LinkedHashSet<String> stringGates = new LinkedHashSet<>();
            walkNumbers(data.copyTag(), found, 0, mode);
            walkStringGates(data.copyTag(), stringGates, 0, mode);
            int n = 0;
            for (Map.Entry<String, Double> e : found.entrySet()) {
                if (n >= MAX_NBT_INTS || out.size() >= MAX_EXTRAS) {
                    break;
                }
                String key = e.getKey();
                // Original SlashBlade refine is stored as RepairCounter on the sample.
                if ("repaircounter".equals(key.toLowerCase(Locale.ROOT))) {
                    key = "refine";
                }
                out.add(key + "≥" + formatNum(e.getValue()));
                n++;
            }
            for (String s : stringGates) {
                if (out.size() >= MAX_EXTRAS) {
                    break;
                }
                out.add(s);
            }
        } catch (Exception ignored) {
            // no custom data
        }
    }

    /**
     * Walk numeric NBT (ints + floats). Organ packs (chest cavity style) store scores as
     * {@code double}/{@code float} under keys like {@code chestcavity:health}, often non-zero
     * including negatives — those are still craft-relevant sample gates.
     */
    private static void walkNumbers(CompoundTag tag, Map<String, Double> out, int depth, ExtrasMode mode) {
        if (tag == null || depth > 6 || out.size() >= MAX_NBT_INTS) {
            return;
        }
        for (String key : tag.getAllKeys()) {
            if (out.size() >= MAX_NBT_INTS) {
                return;
            }
            Tag child = tag.get(key);
            if (child == null) {
                continue;
            }
            byte type = child.getId();
            if (type == Tag.TAG_INT || type == Tag.TAG_SHORT || type == Tag.TAG_BYTE || type == Tag.TAG_LONG
                    || type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE) {
                double v = type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE
                        ? tag.getDouble(key)
                        : tag.getLong(key);
                if (v != 0.0d && allowExtra(key, mode)) {
                    out.merge(key, v, (a, b) -> Math.abs(a) >= Math.abs(b) ? a : b);
                }
            } else if (type == Tag.TAG_COMPOUND) {
                walkNumbers(tag.getCompound(key), out, depth + 1, mode);
            }
        }
    }

    private static void walkStringGates(CompoundTag tag, LinkedHashSet<String> out, int depth, ExtrasMode mode) {
        if (tag == null || depth > 4 || out.size() >= MAX_EXTRAS) {
            return;
        }
        for (String key : tag.getAllKeys()) {
            if (out.size() >= MAX_EXTRAS) {
                return;
            }
            Tag child = tag.get(key);
            if (child == null) {
                continue;
            }
            byte type = child.getId();
            if (type == Tag.TAG_STRING) {
                String v = tag.getString(key);
                if (v != null && !v.isBlank() && allowExtra(key, mode)) {
                    String shown = v;
                    int c = shown.indexOf(':');
                    if (c >= 0 && c < shown.length() - 1) {
                        shown = shown.substring(c + 1);
                    }
                    if (shown.length() > 32) {
                        shown = shown.substring(0, 32);
                    }
                    out.add(key + "=" + shown);
                }
            } else if (type == Tag.TAG_COMPOUND) {
                walkStringGates(tag.getCompound(key), out, depth + 1, mode);
            }
        }
    }

    static String formatNum(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "0";
        }
        long asLong = (long) v;
        if (v == (double) asLong) {
            return Long.toString(asLong);
        }
        String s = String.format(Locale.ROOT, "%.4f", v);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && s.charAt(end - 1) == '.') {
            end--;
        }
        return s.substring(0, end);
    }

    private static void addExtraTooltipLines(ItemStack stack, LinkedHashSet<String> out, ExtrasMode mode) {
        String name = Plainify.stripMcFormat(stack.getHoverName().getString()).trim();
        for (String s : tooltipStrings(stack)) {
            if (out.size() >= MAX_EXTRAS) {
                break;
            }
            if (s.equals(name) || s.isBlank() || isNoiseTooltip(s) || !allowExtra(s, mode)) {
                continue;
            }
            if (mode == ExtrasMode.KEEP_ONLY || looksLikeRequirementLine(s)) {
                out.add(s);
            }
        }
    }

    private static boolean isNoiseTooltip(String s) {
        String t = s.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (t.startsWith("Durability:") || t.startsWith("耐久：") || t.startsWith("耐久:")) {
            return true;
        }
        if (lower.startsWith("minecraft:") || lower.contains(":")) {
            if (t.indexOf(' ') < 0) {
                return true;
            }
        }
        return lower.startsWith("#") || lower.startsWith("nbt:");
    }

    private static boolean looksLikeRequirementLine(String s) {
        String t = s.trim();
        if (t.length() < 2 || t.length() > 48) {
            return false;
        }
        if (t.chars().anyMatch(Character::isDigit)) {
            return true;
        }
        return t.matches(".*\\s(I|II|III|IV|V|VI|VII|VIII|IX|X)$");
    }

    private static List<String> tooltipStrings(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return List.of();
        }
        try {
            Item.TooltipContext ctx = Item.TooltipContext.of(mc.level);
            List<Component> lines = stack.getTooltipLines(ctx, mc.player, TooltipFlag.Default.ADVANCED);
            List<String> out = new ArrayList<>();
            int n = 0;
            for (Component line : lines) {
                if (n++ > 24) {
                    break;
                }
                String s = Plainify.stripMcFormat(line.getString()).trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
