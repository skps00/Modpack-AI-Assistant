package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Reads bee / gene "Type:" lines from item tooltips (e.g. Productive Bees gene_stack).
 * Does not dump unrelated NBT values.
 */
public final class NbtQualifier {
    private static final int CACHE_MAX = 256;
    private static final Pattern TYPE_LINE = Pattern.compile(
            "(?i)^type:\\s*([a-z0-9_.-]+):([a-z0-9_/.-]+)(\\s*\\(\\d+%\\))?$");

    @FunctionalInterface
    public interface TooltipLinesSource {
        List<String> lines(ItemStack stack);
    }

    private static volatile TooltipLinesSource tooltipLinesSource;

    private static final Map<String, String> CACHE = new LinkedHashMap<>(CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_MAX;
        }
    };

    private NbtQualifier() {}

    /** Harness: inject tooltip lines without a full client item. */
    public static void setTooltipLinesSourceForTest(TooltipLinesSource source) {
        tooltipLinesSource = source;
    }

    public static void resetTooltipLinesSourceForTest() {
        tooltipLinesSource = null;
        CACHE.clear();
    }

    public static String qualifier(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty() || !stack.hasTag()) {
                return "";
            }
            CompoundTag tag = stack.getTag();
            if (tag == null || isConsumableOnlyTag(tag)) {
                return "";
            }
            String cacheKey = cacheKey(stack, tag);
            String cached = CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            String resolved = resolveFromTooltip(stack);
            CACHE.put(cacheKey, resolved);
            return resolved;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isConsumableOnlyTag(CompoundTag tag) {
        if (tag.isEmpty()) {
            return true;
        }
        for (String key : tag.getAllKeys()) {
            if (!"Damage".equals(key) && !"RepairCost".equals(key) && !"display".equals(key)) {
                return false;
            }
        }
        return true;
    }

    private static String cacheKey(ItemStack stack, CompoundTag tag) {
        ResourceLocation id = Registry.ITEM.getKey(stack.getItem());
        String itemId = id == null ? "?" : id.toString();
        return itemId + "#" + tag.hashCode();
    }

    private static String resolveFromTooltip(ItemStack stack) {
        List<String> lines = tooltipLines(stack);
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Matcher m = TYPE_LINE.matcher(line.trim());
            if (!m.matches()) {
                continue;
            }
            String pathPart = m.group(2);
            return formatTypeLabel(pathPart);
        }
        return "";
    }

    private static List<String> tooltipLines(ItemStack stack) {
        TooltipLinesSource src = tooltipLinesSource;
        if (src != null) {
            List<String> injected = src.lines(stack);
            return injected == null ? List.of() : injected;
        }
        Minecraft mc = Minecraft.getInstance();
        var player = mc == null ? null : mc.player;
        List<String> out = new ArrayList<>();
        for (var component : stack.getTooltipLines(player, TooltipFlag.Default.NORMAL)) {
            if (component != null) {
                out.add(component.getString());
            }
        }
        return out;
    }

    private static String formatTypeLabel(String pathPart) {
        if (pathPart == null || pathPart.isBlank()) {
            return "";
        }
        int slash = pathPart.lastIndexOf('/');
        String leaf = slash >= 0 ? pathPart.substring(slash + 1) : pathPart;
        String spaced = leaf.replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(spaced.charAt(0))
                + spaced.substring(1).toLowerCase(Locale.ROOT);
    }
}
