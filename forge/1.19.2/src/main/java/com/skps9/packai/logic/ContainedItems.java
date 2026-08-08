package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Soft-fail summary of items stored inside a container stack (shulker / bundle / backpack NBT).
 * Used by Ask PURPOSE when {@code unpackStoredItems} is on.
 */
public final class ContainedItems {
    public static final String HEADER = ContainedItemsText.HEADER;
    /** Cap listed contents so PURPOSE tokens stay bounded. */
    static final int MAX_LINES = ContainedItemsText.MAX_LINES;

    /** Cheap list-tag names seen on vanilla + common backpack mods. */
    private static final String[] LIST_KEYS = {
            "Items", "Inventory", "inventory", "contents", "StorageItems", "Inv"
    };

    private ContainedItems() {}

    /**
     * Summarize contained stacks from sample NBT. Empty when none / unknown / soft-fail.
     * Does not check the config switch — caller gates with {@code PackAiConfig.unpackStoredItems()}.
     */
    public static String summarize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            CompoundTag tag = stack.getTag();
            if (tag == null || tag.isEmpty()) {
                return "";
            }
            ListTag list = findItemList(tag);
            if (list == null || list.isEmpty()) {
                return "";
            }
            return formatBlock(linesFromItemCompounds(list));
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Build {@code [CONTAINED]} block; empty when no lines. Package-visible for check. */
    static String formatBlock(List<String> entryLines) {
        return ContainedItemsText.formatBlock(entryLines);
    }

    /** {@code Dirt x64} / {@code Stick} when count 1. */
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
                // collect a few past cap so formatBlock can show "+N more"
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

    private static String lineFromCompound(CompoundTag c) {
        if (c == null || c.isEmpty()) {
            return "";
        }
        try {
            ItemStack inner = ItemStack.of(c);
            if (!inner.isEmpty()) {
                return entryLine(label(inner), inner.getCount());
            }
        } catch (Throwable ignored) {
            // fall through to id/Count
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
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static String resolveIdLabel(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                return id;
            }
            Item item = Registry.ITEM.get(rl);
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
        if (c.contains("Count", Tag.TAG_ANY_NUMERIC)) {
            return Math.max(1, c.getByte("Count") & 0xFF);
        }
        if (c.contains("count", Tag.TAG_ANY_NUMERIC)) {
            return Math.max(1, c.getInt("count"));
        }
        return 1;
    }

    /** Shulker {@code BlockEntityTag.Items}, bundle {@code Items}, or cheap generic list keys. */
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
