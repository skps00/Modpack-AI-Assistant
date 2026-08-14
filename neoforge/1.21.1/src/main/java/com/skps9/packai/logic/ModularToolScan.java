package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * ItemStack → {@link ToolBuildFacts} for Tetra modular tools. Soft-fails; no Tetra hard-dep.
 * Scrolls ({@link ItemVariantKeys#looksLikeTetraScroll}) return empty so schematic PURPOSE stays.
 * 1.21: Tetra module keys live in {@link DataComponents#CUSTOM_DATA} (same flat NBT as 1.19).
 * Public {@link #scan(ItemStack)} is the GUI list: {@link ToolBuildFacts.Part#itemId()} when
 * material JSON has {@code material.items}.
 */
public final class ModularToolScan {
    private static final Pattern MC_FMT = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final ToolBuildFacts.Scan EMPTY =
            new ToolBuildFacts.Scan(List.of(), List.of(), "");

    private ModularToolScan() {}

    /** PURPOSE {@code [TOOL_BUILD]} block, or empty. */
    public static String purposeLines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            if (ItemVariantKeys.looksLikeTetraScroll(stack)) {
                return "";
            }
        } catch (Throwable ignored) {
            // soft-fail: treat as not a scroll
        }
        String itemId = itemId(stack);
        CompoundTag tag = readTag(stack);
        if (tag == null || tag.isEmpty()) {
            return ToolBuildFacts.looksLikeTetraModularItem(itemId)
                    ? ToolBuildFacts.unparsedBlock()
                    : "";
        }
        ToolBuildFacts.Scan scan;
        try {
            scan = enrich(fromTag(tag));
        } catch (Throwable ignored) {
            return ToolBuildFacts.looksLikeTetraModularItem(itemId)
                    ? ToolBuildFacts.unparsedBlock()
                    : "";
        }
        if (!scan.isEmpty()) {
            return ToolBuildFacts.format(scan);
        }
        if (ToolBuildFacts.looksLikeTetraModularItem(itemId)) {
            return ToolBuildFacts.unparsedBlock();
        }
        return "";
    }

    /** Cap Ask strip part icons. ponytail: one row; upgrade: wrap. */
    static final int MAX_PART_ICONS = 6;

    /**
     * ItemStacks for installed part materials that have a real registry id.
     * Skips blank {@link ToolBuildFacts.Part#itemId()} — never fakes an item.
     */
    public static List<ItemStack> partItemStacks(ItemStack tool) {
        List<ItemStack> out = new ArrayList<>();
        for (ToolBuildFacts.Part p : scan(tool).parts()) {
            if (out.size() >= MAX_PART_ICONS) {
                break;
            }
            if (p == null || p.itemId().isBlank()) {
                continue;
            }
            ItemStack stack = ItemResolver.stackFromId(p.itemId());
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Structured parts for Ask GUI icons. Empty when not a parsed Tetra tool.
     * {@link ToolBuildFacts.Part#itemId()} is {@code ns:path} or blank — never a fake item.
     */
    public static ToolBuildFacts.Scan scan(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY;
        }
        try {
            if (ItemVariantKeys.looksLikeTetraScroll(stack)) {
                return EMPTY;
            }
        } catch (Throwable ignored) {
            // not a scroll
        }
        CompoundTag tag = readTag(stack);
        if (tag == null || tag.isEmpty()) {
            return EMPTY;
        }
        try {
            return enrich(fromTag(tag));
        } catch (Throwable ignored) {
            return EMPTY;
        }
    }

    static ToolBuildFacts.Scan fromTag(CompoundTag tag) {
        Map<String, String> strings = new LinkedHashMap<>();
        Map<String, Integer> ints = new LinkedHashMap<>();
        flatten(tag, strings, ints);
        return ToolBuildFacts.parse(strings, ints);
    }

    static ToolBuildFacts.Scan enrich(ToolBuildFacts.Scan scan) {
        if (scan == null || scan.isEmpty()) {
            return scan == null ? EMPTY : scan;
        }
        Path dir = gameDir();
        List<ToolBuildFacts.Part> parts = new ArrayList<>();
        for (ToolBuildFacts.Part p : scan.parts()) {
            String name = nameForPart(p);
            String item = "";
            try {
                item = TetraMaterialItems.itemIdFor(p.materialId(), dir);
            } catch (Throwable ignored) {
                item = "";
            }
            parts.add(p.withMeta(name, item));
        }
        List<ToolBuildFacts.Improvement> mods = new ArrayList<>();
        for (ToolBuildFacts.Improvement m : scan.mods()) {
            mods.add(m.withMeta(nameForImprovement(m), ""));
        }
        return new ToolBuildFacts.Scan(parts, mods, scan.rawSource());
    }

    static void flatten(CompoundTag tag, Map<String, String> strings, Map<String, Integer> ints) {
        if (tag == null) {
            return;
        }
        for (String key : tag.getAllKeys()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Tag raw = tag.get(key);
            if (raw == null) {
                continue;
            }
            byte id = raw.getId();
            if (id == Tag.TAG_STRING) {
                strings.put(key, tag.getString(key));
            } else if (id == Tag.TAG_INT || id == Tag.TAG_BYTE || id == Tag.TAG_SHORT) {
                ints.put(key, tag.getInt(key));
            } else if (id == Tag.TAG_COMPOUND) {
                flattenNestedModule(key, tag.getCompound(key), strings, ints);
            }
        }
    }

    /** Optional nested {@code {id, material, improvements:{k:v}}} — not Tetra 1.19 flat, but cheap. */
    static void flattenNestedModule(
            String slot,
            CompoundTag nested,
            Map<String, String> strings,
            Map<String, Integer> ints
    ) {
        if (nested == null || !nested.contains("id", Tag.TAG_STRING)) {
            return;
        }
        String module = nested.getString("id");
        if (module == null || module.isBlank()) {
            return;
        }
        strings.put(slot, module);
        if (nested.contains("material", Tag.TAG_STRING)) {
            String mat = nested.getString("material");
            if (mat != null && !mat.isBlank()) {
                strings.put(module + "_material", mat.trim());
            }
        }
        if (nested.contains("improvements", Tag.TAG_COMPOUND)) {
            CompoundTag im = nested.getCompound("improvements");
            for (String ik : im.getAllKeys()) {
                if (ik == null || ik.isBlank()) {
                    continue;
                }
                Tag v = im.get(ik);
                if (v != null && (v.getId() == Tag.TAG_INT
                        || v.getId() == Tag.TAG_BYTE
                        || v.getId() == Tag.TAG_SHORT)) {
                    ints.put(slot + ":" + ik, im.getInt(ik));
                }
            }
        }
    }

    static String nameForPart(ToolBuildFacts.Part p) {
        if (p == null) {
            return "";
        }
        String mat = p.materialId();
        String last = TetraMaterialItems.lastSegment(mat);
        String[] keys = {
                mat.isBlank() ? "" : "tetra.variant." + mat,
                last.isBlank() ? "" : "tetra.material." + last + ".prefix",
                last.isBlank() ? "" : "tetra.material." + last,
                last.isBlank() ? "" : "tetra.variant." + last,
                p.moduleId().isBlank() ? "" : "tetra.module." + p.moduleId() + ".item_name",
                p.moduleId().isBlank() ? "" : "tetra.module." + p.moduleId() + ".name"
        };
        for (String key : keys) {
            String t = i18nHonest(key);
            if (!t.isBlank()) {
                return t;
            }
        }
        String tmpl = i18nRaw(p.moduleId().isBlank() ? "" : "tetra.module." + p.moduleId() + ".material_name");
        String matName = last.isBlank() ? "" : i18nHonest("tetra.material." + last);
        if (tmpl.contains("%s") && !matName.isBlank()) {
            try {
                return stripFmt(String.format(Locale.ROOT, tmpl, matName));
            } catch (Throwable ignored) {
                return matName;
            }
        }
        return "";
    }

    static String nameForImprovement(ToolBuildFacts.Improvement m) {
        if (m == null || m.key().isBlank()) {
            return "";
        }
        String key = m.key();
        int colon = key.indexOf(':');
        String after = colon >= 0 ? key.substring(colon + 1) : key;
        String[] keys = {
                "tetra.improvement." + after,
                "tetra.improvement." + after.replace('/', '.'),
                "tetra.improvement." + key
        };
        for (String k : keys) {
            String t = i18nHonest(k);
            if (!t.isBlank()) {
                return t;
            }
        }
        return "";
    }

    private static String i18nHonest(String key) {
        String raw = i18nRaw(key);
        if (raw.isBlank() || raw.equals(key) || raw.contains("%")) {
            return "";
        }
        return stripFmt(raw);
    }

    private static String i18nRaw(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        try {
            if (!I18n.exists(key)) {
                return "";
            }
            String t = I18n.get(key);
            return t == null ? "" : t.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    static String stripFmt(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return MC_FMT.matcher(s).replaceAll("").trim();
    }

    private static Path gameDir() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) {
                return null;
            }
            return mc.gameDirectory.toPath();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CompoundTag readTag(ItemStack stack) {
        try {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                return null;
            }
            CompoundTag tag = data.copyTag();
            return tag == null || tag.isEmpty() ? null : tag;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String itemId(ItemStack stack) {
        try {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return key == null ? "" : key.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
