package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Parity (1.19.2): NBT-based extras. No DataComponents (1.20.5+).
 * Ingredient-gate polish limited — see VERSIONS gaps.
 */
public final class IngredientReqHints {
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
        return name + "（" + String.join(ReplyLang.sourceJoin(lang), extras) + "）";
    }

    public static String richLabel(ItemStack stack, String lang, boolean nbtMatters) {
        return richLabel(stack, lang, nbtMatters ? ExtrasMode.ALL : ExtrasMode.NONE);
    }

    public static String richLabel(ItemStack stack, String lang) {
        return richLabel(stack, lang, ExtrasMode.ALL);
    }

    public static String labelForIngredient(Ingredient ingredient, String lang) {
        if (ingredient == null || ingredient.isEmpty()) {
            return "";
        }
        ItemStack[] items = ingredient.getItems();
        if (items == null || items.length == 0) {
            return "";
        }
        ItemStack sample = ItemStack.EMPTY;
        for (ItemStack s : items) {
            if (s != null && !s.isEmpty()) {
                sample = s;
                break;
            }
        }
        if (sample.isEmpty()) {
            return "";
        }
        String name = Plainify.stripMcFormat(sample.getHoverName().getString()).trim();
        if (name.isEmpty()) {
            name = "?";
        }
        String policy = PackAiConfig.ingredientNbtPolicy();
        if ("never".equals(policy)) {
            return name;
        }
        LinkedHashSet<String> extras = new LinkedHashSet<>(RecipeIngredientGates.fromIngredient(ingredient));
        boolean bare = acceptsBare(ingredient, items);
        ExtrasMode mode = modeForPolicy(policy, bare);
        if (mode != ExtrasMode.NONE) {
            extras.addAll(collectExtras(sample, mode));
        }
        if (extras.isEmpty()) {
            return name;
        }
        return name + "（" + String.join(ReplyLang.sourceJoin(lang), new ArrayList<>(extras)) + "）";
    }

    private static boolean acceptsBare(Ingredient ingredient, ItemStack[] items) {
        try {
            ItemStack bare = items[0].copy();
            bare.setTag(null);
            return ingredient.test(bare);
        } catch (Exception e) {
            return true;
        }
    }

    private static List<String> collectExtras(ItemStack stack, ExtrasMode mode) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Map<net.minecraft.world.item.enchantment.Enchantment, Integer> enchants =
                EnchantmentHelper.getEnchantments(stack);
        for (var e : enchants.entrySet()) {
            out.add(e.getKey().getDescriptionId() + ":" + e.getValue());
        }
        if (stack.getItem() instanceof EnchantedBookItem) {
            ListTag stored = EnchantedBookItem.getEnchantments(stack);
            for (int i = 0; i < stored.size(); i++) {
                CompoundTag t = stored.getCompound(i);
                out.add(t.getString("id") + ":" + t.getInt("lvl"));
            }
        }
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            walkInts(tag, "", out, mode, 0);
        }
        return new ArrayList<>(out);
    }

    private static void walkInts(
            CompoundTag tag, String path, Set<String> out, ExtrasMode mode, int depth
    ) {
        if (tag == null || depth > 4 || out.size() > 12) {
            return;
        }
        for (String key : tag.getAllKeys()) {
            String full = path.isEmpty() ? key : path + "." + key;
            if (shouldSkip(full, mode)) {
                continue;
            }
            Tag child = tag.get(key);
            if (child == null) {
                continue;
            }
            byte type = child.getId();
            if (type == Tag.TAG_INT || type == Tag.TAG_SHORT || type == Tag.TAG_BYTE) {
                if (mode == ExtrasMode.KEEP_ONLY && !shouldKeep(full)) {
                    continue;
                }
                out.add(full + "=" + tag.getInt(key));
            } else if (type == Tag.TAG_COMPOUND) {
                walkInts(tag.getCompound(key), full, out, mode, depth + 1);
            }
        }
    }

    private static boolean shouldSkip(String key, ExtrasMode mode) {
        String k = key.toLowerCase(Locale.ROOT);
        for (String p : PackAiConfig.ingredientNbtSkipPatterns()) {
            if (!p.isBlank() && k.contains(p.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldKeep(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        for (String p : PackAiConfig.ingredientNbtKeepPatterns()) {
            if (!p.isBlank() && k.contains(p.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return k.contains(":") || k.contains("/");
    }
}
