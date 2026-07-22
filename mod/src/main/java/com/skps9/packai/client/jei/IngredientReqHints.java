package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Enrich JEI ingredient labels with NBT / component extras the hover name omits
 * (enchantments, custom-data counts, extra tooltip requirement lines).
 * Mod-agnostic: no hard-coded mod mechanics in the LLM-facing text beyond what the stack shows.
 */
public final class IngredientReqHints {
    private static final int MAX_EXTRAS = 6;
    private static final int MAX_NBT_INTS = 4;
    /** Skip noisy / non-requirement keys when summarizing custom data. */
    private static final Pattern SKIP_NBT_KEY = Pattern.compile(
            ".*(uuid|uid|color|texture|model|time|damage|maxdamage|hash|seed).*",
            Pattern.CASE_INSENSITIVE);

    private IngredientReqHints() {}

    /**
     * Display name plus compact extras, e.g.
     * {@code Named Blade（RepairCounter≥3、Sharpness III）}.
     */
    public static String richLabel(ItemStack stack, String lang) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String name = Plainify.stripMcFormat(stack.getHoverName().getString()).trim();
        if (name.isEmpty()) {
            name = "?";
        }
        List<String> extras = collectExtras(stack);
        if (extras.isEmpty()) {
            return name;
        }
        String join = ReplyLang.sourceJoin(lang);
        return name + "（" + String.join(join, extras) + "）";
    }

    static List<String> collectExtras(ItemStack stack) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        addEnchantLabels(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY), out);
        addEnchantLabels(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY), out);
        addCustomDataInts(stack, out);
        if (out.size() < MAX_EXTRAS) {
            addExtraTooltipLines(stack, out);
        }
        List<String> list = new ArrayList<>(out);
        if (list.size() > MAX_EXTRAS) {
            return List.copyOf(list.subList(0, MAX_EXTRAS));
        }
        return List.copyOf(list);
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
                if (!label.isEmpty()) {
                    out.add(label);
                }
            }
        } catch (Exception ignored) {
            // mapping differences
        }
    }

    private static void addCustomDataInts(ItemStack stack, LinkedHashSet<String> out) {
        try {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                return;
            }
            Map<String, Integer> found = new LinkedHashMap<>();
            walkInts(data.copyTag(), found, 0);
            int n = 0;
            for (Map.Entry<String, Integer> e : found.entrySet()) {
                if (n >= MAX_NBT_INTS || out.size() >= MAX_EXTRAS) {
                    break;
                }
                out.add(e.getKey() + "≥" + e.getValue());
                n++;
            }
        } catch (Exception ignored) {
            // no custom data
        }
    }

    private static void walkInts(CompoundTag tag, Map<String, Integer> out, int depth) {
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
            if (type == Tag.TAG_INT || type == Tag.TAG_SHORT || type == Tag.TAG_BYTE || type == Tag.TAG_LONG) {
                int v = tag.getInt(key);
                if (v > 0 && !SKIP_NBT_KEY.matcher(key).matches()) {
                    // Keep original key — LLM/prompt stay mod-agnostic; value is the requirement.
                    out.merge(key, v, Math::max);
                }
            } else if (type == Tag.TAG_COMPOUND) {
                walkInts(tag.getCompound(key), out, depth + 1);
            }
        }
    }

    /**
     * Tooltip lines beyond the item name that look like requirements
     * (enchant-like or contain a number), without mod-specific keyword lists.
     */
    private static void addExtraTooltipLines(ItemStack stack, LinkedHashSet<String> out) {
        String name = Plainify.stripMcFormat(stack.getHoverName().getString()).trim();
        for (String s : tooltipStrings(stack)) {
            if (out.size() >= MAX_EXTRAS) {
                break;
            }
            if (s.equals(name) || s.isBlank()) {
                continue;
            }
            if (isNoiseTooltip(s)) {
                continue;
            }
            if (looksLikeRequirementLine(s)) {
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
            // advanced id lines / registry paths
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
        // Any digit → likely a count/level requirement shown on the JEI ghost stack
        if (t.chars().anyMatch(Character::isDigit)) {
            return true;
        }
        // Enchantment-style roman numerals without digits
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
