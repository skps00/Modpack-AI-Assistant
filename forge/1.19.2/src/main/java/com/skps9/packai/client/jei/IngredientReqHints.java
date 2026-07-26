package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.skps9.packai.client.context.TooltipCapture;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Enrich JEI ingredient labels with real craft constraints (NBT / tooltip heuristics). */
public final class IngredientReqHints {
    private static final int MAX_EXTRAS = 8;
    private static final int MAX_NBT_INTS = 8;

    public enum ExtrasMode {
        NONE,
        KEEP_ONLY,
        ALL
    }

    private IngredientReqHints() {}

    public static ExtrasMode modeForPolicy(String policy, boolean acceptsBare) {
        if ("never".equals(policy)) {
            return ExtrasMode.NONE;
        }
        if ("always".equals(policy)) {
            return ExtrasMode.ALL;
        }
        return acceptsBare ? ExtrasMode.KEEP_ONLY : ExtrasMode.ALL;
    }

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

    public static String richLabel(ItemStack stack, String lang, boolean nbtMatters) {
        return richLabel(stack, lang, nbtMatters ? ExtrasMode.ALL : ExtrasMode.NONE);
    }

    public static String richLabel(ItemStack stack, String lang) {
        return richLabel(stack, lang, ExtrasMode.ALL);
    }

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
        // Gates / NBT extras still useful for custom ingredients (tag label already in base).
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
        if (extras.isEmpty()) {
            return base;
        }
        List<String> list = new ArrayList<>(extras);
        if (list.size() > MAX_EXTRAS) {
            list = list.subList(0, MAX_EXTRAS);
        }
        String join = ReplyLang.sourceJoin(lang);
        if (base.indexOf('（') >= 0) {
            return base;
        }
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
        int n = 0;
        for (Holder<Item> ignored : Registry.ITEM.getTagOrEmpty(tag)) {
            n++;
            if (n > 4096) {
                break;
            }
        }
        return n;
    }

    static ItemStack pickPrefer(List<ItemStack> alts, ItemStack prefer) {
        if (prefer != null && !prefer.isEmpty()) {
            for (ItemStack s : alts) {
                if (s != null && !s.isEmpty() && ItemStack.isSameItemSameTags(s, prefer)) {
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

    /** Prefer focus stack when tag/ingredient accepts it; else first non-empty sample. */
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

    public static boolean acceptsBare(Ingredient ingredient, ItemStack[] items) {
        if (ingredient == null || items == null) {
            return false;
        }
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack bare = new ItemStack(stack.getItem(), 1);
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
            addEnchantLabels(EnchantmentHelper.getEnchantments(stack), out);
        }
        if (stack.getItem() instanceof EnchantedBookItem) {
            ListTag stored = EnchantedBookItem.getEnchantments(stack);
            for (int i = 0; i < stored.size(); i++) {
                if (out.size() >= MAX_EXTRAS) {
                    break;
                }
                CompoundTag t = stored.getCompound(i);
                String label = t.getString("id") + ":" + t.getInt("lvl");
                if (!matchesSkip(label)) {
                    out.add(label);
                }
            }
        }
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            Map<String, Double> found = new LinkedHashMap<>();
            LinkedHashSet<String> stringGates = new LinkedHashSet<>();
            walkNumbers(tag, found, 0, mode);
            walkStringGates(tag, stringGates, 0, mode);
            int n = 0;
            for (Map.Entry<String, Double> entry : found.entrySet()) {
                if (n >= MAX_NBT_INTS || out.size() >= MAX_EXTRAS) {
                    break;
                }
                String key = entry.getKey();
                if ("repaircounter".equals(key.toLowerCase(Locale.ROOT))) {
                    key = "refine";
                }
                out.add(key + "≥" + formatNum(entry.getValue()));
                n++;
            }
            for (String stringGate : stringGates) {
                if (out.size() >= MAX_EXTRAS) {
                    break;
                }
                out.add(stringGate);
            }
        }
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

    public static boolean matchesSkip(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return containsAny(text, PackAiConfig.ingredientNbtSkipPatterns());
    }

    public static boolean matchesKeep(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (containsAny(text, PackAiConfig.ingredientNbtKeepPatterns())) {
            return true;
        }
        return looksLikeNamespacedAttrKey(text);
    }

    static boolean looksLikeNamespacedAttrKey(String text) {
        String key = text.trim();
        if (key.length() < 3 || key.length() > 64 || key.indexOf(' ') >= 0) {
            return false;
        }
        int colon = key.indexOf(':');
        if (colon <= 0 || colon >= key.length() - 1 || key.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        String namespace = key.substring(0, colon).toLowerCase(Locale.ROOT);
        if ("minecraft".equals(namespace) || "forge".equals(namespace) || "c".equals(namespace)) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == ':' || c == '_' || c == '/' || c == '.') {
                continue;
            }
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean containsAny(String text, List<String> patterns) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (!pattern.isEmpty() && lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    static boolean allowExtra(String text, ExtrasMode mode) {
        if (text == null || text.isBlank() || matchesSkip(text)) {
            return false;
        }
        if (mode == ExtrasMode.KEEP_ONLY) {
            return matchesKeep(text);
        }
        return mode == ExtrasMode.ALL;
    }

    private static void addEnchantLabels(Map<net.minecraft.world.item.enchantment.Enchantment, Integer> enchants, LinkedHashSet<String> out) {
        if (enchants == null || enchants.isEmpty()) {
            return;
        }
        for (var entry : enchants.entrySet()) {
            if (out.size() >= MAX_EXTRAS) {
                break;
            }
            var enchantment = entry.getKey();
            int level = entry.getValue();
            if (enchantment == null || level <= 0) {
                continue;
            }
            try {
                String label = Plainify.stripMcFormat(enchantment.getFullname(level).getString()).trim();
                if (!label.isEmpty() && !matchesSkip(label)) {
                    out.add(label);
                }
            } catch (Exception ignored) {
                out.add(enchantment.getDescriptionId() + ":" + level);
            }
        }
    }

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
                double value = type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE
                        ? tag.getDouble(key)
                        : tag.getLong(key);
                if (value != 0.0D && allowExtra(key, mode)) {
                    out.merge(key, value, (a, b) -> Math.abs(a) >= Math.abs(b) ? a : b);
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
                String value = tag.getString(key);
                if (value != null && !value.isBlank() && allowExtra(key, mode)) {
                    String shown = value;
                    int colon = shown.indexOf(':');
                    if (colon >= 0 && colon < shown.length() - 1) {
                        shown = shown.substring(colon + 1);
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

    static String formatNum(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        long asLong = (long) value;
        if (value == (double) asLong) {
            return Long.toString(asLong);
        }
        String formatted = String.format(Locale.ROOT, "%.4f", value);
        int end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        return formatted.substring(0, end);
    }

    private static void addExtraTooltipLines(ItemStack stack, LinkedHashSet<String> out, ExtrasMode mode) {
        String name = Plainify.stripMcFormat(stack.getHoverName().getString()).trim();
        for (String line : tooltipStrings(stack)) {
            if (out.size() >= MAX_EXTRAS) {
                break;
            }
            if (line.equals(name) || line.isBlank() || isNoiseTooltip(line) || !allowExtra(line, mode)) {
                continue;
            }
            if (mode == ExtrasMode.KEEP_ONLY || looksLikeRequirementLine(line)) {
                out.add(line);
            }
        }
    }

    private static boolean isNoiseTooltip(String text) {
        String shown = text.trim();
        String lower = shown.toLowerCase(Locale.ROOT);
        if (shown.startsWith("Durability:") || shown.startsWith("耐久：") || shown.startsWith("耐久:")) {
            return true;
        }
        if (lower.startsWith("minecraft:") || lower.contains(":")) {
            if (shown.indexOf(' ') < 0) {
                return true;
            }
        }
        return lower.startsWith("#") || lower.startsWith("nbt:");
    }

    private static boolean looksLikeRequirementLine(String text) {
        String shown = text.trim();
        if (shown.length() < 2 || shown.length() > 48) {
            return false;
        }
        if (shown.chars().anyMatch(Character::isDigit)) {
            return true;
        }
        return shown.matches(".*\\s(I|II|III|IV|V|VI|VII|VIII|IX|X)$");
    }

    private static List<String> tooltipStrings(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return List.of();
        }
        try {
            String tooltip = TooltipCapture.capture(stack, mc.player);
            if (tooltip == null || tooltip.isBlank()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            int n = 0;
            for (String line : tooltip.split("\n")) {
                if (n++ > 24) {
                    break;
                }
                String shown = Plainify.stripMcFormat(line).trim();
                if (!shown.isEmpty()) {
                    out.add(shown);
                }
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
