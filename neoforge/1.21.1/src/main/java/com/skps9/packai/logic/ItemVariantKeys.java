package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Stack-aware identity for same-registry-id NBT variants (Tetra {@code scroll_rolled} schematics).
 * Extracts schematic ids from CustomData without a Tetra hard-dep.
 */
public final class ItemVariantKeys {
    public static final String HEADER = ItemVariantKeysText.HEADER;

    private ItemVariantKeys() {}

    /** PURPOSE line when schematics present; empty otherwise. */
    public static String purposeLine(ItemStack stack) {
        return ItemVariantKeysText.purposeLine(schematics(stack));
    }

    /** Schematic resource ids from sample CustomData ({@code s} list / similar). */
    public static List<String> schematics(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        try {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                return List.of();
            }
            CompoundTag tag = data.copyTag();
            if (tag == null || tag.isEmpty()) {
                return List.of();
            }
            return schematicsFromTag(tag);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    /**
     * Disambiguators for quest / PURPOSE filtering: schematic tokens, else distinctive label tokens.
     */
    public static List<String> disambiguators(ItemRef ref) {
        if (ref == null || !ref.isPresent()) {
            return List.of();
        }
        List<String> fromNbt = ItemVariantKeysText.expandTokens(schematics(ref.sample()));
        if (!fromNbt.isEmpty()) {
            return fromNbt;
        }
        return labelDisambiguators(ref.id(), ref.label());
    }

    /** Match tokens for PackIndex hints (schematic path pieces). */
    public static List<String> hintExtras(ItemRef ref) {
        return ItemVariantKeysText.expandTokens(schematics(ref == null ? null : ref.sample()));
    }

    /** True when stack has schematic/NBT variant keys. */
    public static boolean hasVariantKeys(ItemStack stack) {
        return !schematics(stack).isEmpty();
    }

    /**
     * JEI soft-prefer tokens: schematic path pieces when present; else distinctive display-name words.
     * (Do not mix label words like "scroll" into schematic prefer — that collides Tetra siblings.)
     */
    public static List<String> preferTokens(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        List<String> fromNbt = ItemVariantKeysText.expandTokens(schematics(stack));
        if (!fromNbt.isEmpty()) {
            return fromNbt;
        }
        String id = "";
        try {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key != null) {
                id = key.toString();
            }
        } catch (Throwable ignored) {
            // soft
        }
        String label = Plainify.stripMcFormat(stack.getHoverName().getString());
        return labelDisambiguators(id, label);
    }

    /** Blob mentions any prefer/disambiguator token. */
    public static boolean mentions(String blob, List<String> tokens) {
        return ItemVariantKeysText.mentionsAny(blob, List.of(), tokens);
    }

    /** Soft prefer: keep hits that mention tokens; if none do, keep all. */
    public static <T> List<T> preferMentioning(
            List<T> hits, List<String> tokens, java.util.function.Function<T, Boolean> mentions
    ) {
        return ItemVariantKeysText.preferMentioning(hits, tokens, mentions);
    }

    static List<String> schematicsFromTag(CompoundTag tag) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (tag == null) {
            return List.of();
        }
        collectFromList(tag.get("s"), out);
        collectFromList(tag.get("schematics"), out);
        collectFromList(tag.get("Schematics"), out);
        String one = tag.getString("schematic");
        if (one != null && !one.isBlank()) {
            out.add(one.trim());
        }
        one = tag.getString("key");
        if (one != null && !one.isBlank() && one.contains(":")) {
            out.add(one.trim());
        }
        return new ArrayList<>(out);
    }

    private static void collectFromList(Tag raw, LinkedHashSet<String> out) {
        if (!(raw instanceof ListTag list) || list.isEmpty()) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            try {
                Tag el = list.get(i);
                if (el.getId() == Tag.TAG_STRING) {
                    String s = list.getString(i);
                    if (s != null && !s.isBlank()) {
                        out.add(s.trim());
                    }
                } else if (el.getId() == Tag.TAG_COMPOUND) {
                    CompoundTag c = list.getCompound(i);
                    for (String k : List.of("id", "schematic", "key", "name")) {
                        String v = c.getString(k);
                        if (v != null && !v.isBlank()) {
                            out.add(v.trim());
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // soft-fail one entry
            }
        }
    }

    private static List<String> labelDisambiguators(String id, String label) {
        if (label == null || label.isBlank()) {
            return List.of();
        }
        String norm = label.trim().toLowerCase(Locale.ROOT);
        if (id != null) {
            String path = id;
            int c = id.indexOf(':');
            if (c >= 0) {
                path = id.substring(c + 1);
            }
            String pathNorm = path.toLowerCase(Locale.ROOT).replace('_', ' ');
            if (norm.equals(pathNorm) || norm.equals(id.toLowerCase(Locale.ROOT)) || norm.equals(path.toLowerCase(Locale.ROOT))) {
                return List.of();
            }
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String p : norm.split("[\\s|/,_\\-()\\[\\]·•]+")) {
            if (p.length() >= 2) {
                out.add(p);
            }
        }
        return new ArrayList<>(out);
    }
}
