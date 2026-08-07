package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Soft-fail summary of items stored inside a container stack (shulker / bundle / backpack).
 * Used by Ask PURPOSE when {@code unpackStoredItems} is on.
 * <p>Neo 1.21: DataComponents first, then CUSTOM_DATA / BLOCK_ENTITY_DATA list tags.
 */
public final class ContainedItems {
    public static final String HEADER = ContainedItemsText.HEADER;
    /** Cap listed contents so PURPOSE tokens stay bounded. */
    static final int MAX_LINES = ContainedItemsText.MAX_LINES;

    private static final String[] LIST_KEYS = {
            "Items", "Inventory", "inventory", "contents", "StorageItems", "Inv"
    };

    private ContainedItems() {}

    /**
     * Summarize contained stacks. Empty when none / unknown / soft-fail.
     * Does not check the config switch — caller gates with {@code PackAiConfig.unpackStoredItems()}.
     */
    public static String summarize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            List<String> lines = new ArrayList<>();
            collectFromComponents(stack, lines);
            if (lines.isEmpty()) {
                collectFromCustomData(stack, lines);
            }
            return formatBlock(lines);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Build {@code [CONTAINED]} block; empty when no lines. Package-visible for check. */
    static String formatBlock(List<String> entryLines) {
        return ContainedItemsText.formatBlock(entryLines);
    }

    static String entryLine(String name, int count) {
        return ContainedItemsText.entryLine(name, count);
    }

    static List<String> linesFromItemCompounds(ListTag list) {
        List<String> out = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return out;
        }
        for (int i = 0; i < list.size(); i++) {
            if (out.size() >= MAX_LINES + 8) {
                break;
            }
            try {
                if (list.get(i).getId() != Tag.TAG_COMPOUND) {
                    continue;
                }
                CompoundTag c = list.getCompound(i);
                String line = lineFromCompound(c);
                if (!line.isEmpty()) {
                    out.add(line);
                }
            } catch (Throwable ignored) {
                // soft-fail one slot
            }
        }
        return out;
    }

    private static void collectFromComponents(ItemStack stack, List<String> out) {
        try {
            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
            if (container != null) {
                for (ItemStack inner : container.nonEmptyItems()) {
                    addStackLine(out, inner);
                    if (out.size() >= MAX_LINES + 8) {
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        try {
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) {
                bundle.itemCopyStream().forEach(inner -> {
                    if (out.size() < MAX_LINES + 8) {
                        addStackLine(out, inner);
                    }
                });
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
    }

    private static void collectFromCustomData(ItemStack stack, List<String> out) {
        try {
            CustomData blockData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (blockData != null) {
                appendFromRootTag(blockData.copyTag(), out);
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        if (!out.isEmpty()) {
            return;
        }
        try {
            CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
            if (custom != null) {
                appendFromRootTag(custom.copyTag(), out);
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
    }

    private static void appendFromRootTag(CompoundTag root, List<String> out) {
        ListTag list = findItemList(root);
        if (list == null) {
            return;
        }
        for (String line : linesFromItemCompounds(list)) {
            if (out.size() >= MAX_LINES + 8) {
                break;
            }
            out.add(line);
        }
    }

    private static void addStackLine(List<String> out, ItemStack inner) {
        if (inner == null || inner.isEmpty()) {
            return;
        }
        String line = entryLine(label(inner), inner.getCount());
        if (!line.isEmpty()) {
            out.add(line);
        }
    }

    private static String lineFromCompound(CompoundTag c) {
        if (c == null || c.isEmpty()) {
            return "";
        }
        String id = readItemId(c);
        if (id.isEmpty()) {
            return "";
        }
        int count = readCount(c);
        String name = resolveIdLabel(id);
        return entryLine(name.isEmpty() ? id : name, count);
    }

    private static String label(ItemStack stack) {
        try {
            String hover = stack.getHoverName().getString().trim();
            if (!hover.isEmpty()) {
                return hover;
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static String resolveIdLabel(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                return id;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) {
                return id;
            }
            ItemStack probe = new ItemStack(item);
            String hover = probe.getHoverName().getString().trim();
            return hover.isEmpty() ? id : hover;
        } catch (Throwable ignored) {
            return id;
        }
    }

    private static String readItemId(CompoundTag c) {
        if (c.contains("id", Tag.TAG_STRING)) {
            return c.getString("id").trim();
        }
        if (c.contains("Item", Tag.TAG_STRING)) {
            return c.getString("Item").trim();
        }
        return "";
    }

    private static int readCount(CompoundTag c) {
        if (c.contains("count", Tag.TAG_ANY_NUMERIC)) {
            return Math.max(1, c.getInt("count"));
        }
        if (c.contains("Count", Tag.TAG_ANY_NUMERIC)) {
            return Math.max(1, c.getByte("Count") & 0xFF);
        }
        return 1;
    }

    private static ListTag findItemList(CompoundTag root) {
        ListTag direct = firstList(root);
        if (direct != null) {
            return direct;
        }
        if (root.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return firstList(root.getCompound("BlockEntityTag"));
        }
        return null;
    }

    private static ListTag firstList(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        for (String key : LIST_KEYS) {
            if (tag.contains(key, Tag.TAG_LIST)) {
                ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
                if (!list.isEmpty()) {
                    return list;
                }
            }
        }
        return null;
    }
}
