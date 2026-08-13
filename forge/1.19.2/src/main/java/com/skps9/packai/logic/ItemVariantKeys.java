package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Stack-aware identity for same-registry-id NBT variants (Tetra {@code scroll_rolled} schematics).
 * Extracts schematic ids from common NBT keys without a Tetra hard-dep.
 * Nested paths (D8): allowlisted walk under {@code BlockEntityTag}/{@code data} with caps.
 */
public final class ItemVariantKeys {
    public static final String HEADER = ItemVariantKeysText.HEADER;

    /** ponytail: hard caps so full JEI scan never deep-walks huge NBT. Upgrade: per-mod parsers. */
    static final int MAX_DEPTH = 4;
    static final int MAX_SCHEMATICS = 16;
    static final int MAX_LIST_SCAN = 8;

    /** Compound keys we may descend into (Tetra scrolls nest under BlockEntityTag). */
    private static final String[] NEST_COMPOUNDS = {"BlockEntityTag", "tag"};
    /** List keys whose compound elements may hold schematic fields. */
    private static final String[] NEST_LISTS = {"data", "s", "schematics", "Schematics"};

    private ItemVariantKeys() {}

    /** PURPOSE line when schematics present; empty otherwise. */
    public static String purposeLine(ItemStack stack) {
        return ItemVariantKeysText.purposeLine(schematics(stack));
    }

    /**
     * Tetra scroll effect text from lang ({@code item.tetra.scroll.*.description[+_extended]}).
     * Empty when no variant keys or no translation. Soft-fails without Tetra.
     */
    public static String scrollEffectPurposeLines(ItemStack stack) {
        List<String> ids = schematics(stack);
        if (ids.isEmpty()) {
            return "";
        }
        try {
            return ItemVariantKeysText.scrollEffectPurposeLines(ids, ItemVariantKeys::i18n);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * True when stack looks like a Tetra rolled scroll (id path or schematic NBT).
     * Iron's / other variant NBT must not count — {@code schematics()} also reads {@code ISB_Spells}.
     */
    public static boolean looksLikeTetraScroll(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            ResourceLocation id = Registry.ITEM.getKey(stack.getItem());
            if (id == null || !"tetra".equals(id.getNamespace())) {
                return false;
            }
            if (id.getPath() != null
                    && id.getPath().toLowerCase(Locale.ROOT).contains("scroll")) {
                return true;
            }
            return !schematics(stack).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Tooltip keyword detect for Tetra scroll placement (see {@link ItemVariantKeysText}). */
    public static boolean tooltipHintsTetraScroll(String tip) {
        return ItemVariantKeysText.tooltipHintsTetraScroll(tip);
    }

    /**
     * Canonical Tetra scroll mechanics for PURPOSE ({@code [SCROLL_MECH]}): place near workbench.
     * Empty when not a tetra scroll / no translations.
     */
    public static String scrollMechanicsPurposeLines(ItemStack stack, String tooltip) {
        if (!looksLikeTetraScroll(stack) && !tooltipHintsTetraScroll(tooltip)) {
            return "";
        }
        try {
            return ItemVariantKeysText.scrollMechanicsPurposeLines(ItemVariantKeys::i18n);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Schematic datapack outcomes for PURPOSE ({@code [SCROLL_UNLOCK]} / {@code [SCROLL_MATERIALS]}).
     * Empty when no variant keys or JSON not found.
     */
    public static String scrollSchematicPurposeLines(ItemStack stack) {
        try {
            return TetraSchematicLookup.purposeLines(stack);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String i18n(String key) {
        try {
            String t = net.minecraft.client.resources.language.I18n.get(key);
            return t == null ? "" : t;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Schematic resource ids from sample NBT ({@code s} list / nested BlockEntityTag.data). */
    public static List<String> schematics(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        try {
            CompoundTag tag = stack.getTag();
            if (tag == null || tag.isEmpty()) {
                return List.of();
            }
            return schematicsFromTag(tag);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    /**
     * Search / prefer tokens from schematics (full id, path, last segment). Empty when none.
     * Call once at variant ingest; pass into scorers — do not re-walk NBT inside score loops.
     */
    public static List<String> schematicTokens(ItemStack stack) {
        return ItemVariantKeysText.expandTokens(schematics(stack));
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
            ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
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
        walkAllowlisted(tag, out, 0);
        if (out.size() <= MAX_SCHEMATICS) {
            return new ArrayList<>(out);
        }
        ArrayList<String> capped = new ArrayList<>(MAX_SCHEMATICS);
        for (String s : out) {
            if (capped.size() >= MAX_SCHEMATICS) {
                break;
            }
            capped.add(s);
        }
        return capped;
    }

    /** Allowlisted nested walk (D8): root + BlockEntityTag/data compounds only. */
    private static void walkAllowlisted(CompoundTag tag, LinkedHashSet<String> out, int depth) {
        if (tag == null || depth > MAX_DEPTH || out.size() >= MAX_SCHEMATICS) {
            return;
        }
        collectAtCompound(tag, out);
        if (out.size() >= MAX_SCHEMATICS) {
            return;
        }
        for (String k : NEST_COMPOUNDS) {
            if (tag.contains(k, Tag.TAG_COMPOUND)) {
                walkAllowlisted(tag.getCompound(k), out, depth + 1);
                if (out.size() >= MAX_SCHEMATICS) {
                    return;
                }
            }
        }
        for (String k : NEST_LISTS) {
            Tag raw = tag.get(k);
            if (!(raw instanceof ListTag list) || list.isEmpty()) {
                continue;
            }
            int n = Math.min(list.size(), MAX_LIST_SCAN);
            for (int i = 0; i < n; i++) {
                if (out.size() >= MAX_SCHEMATICS) {
                    return;
                }
                try {
                    Tag el = list.get(i);
                    if (el.getId() == Tag.TAG_COMPOUND) {
                        walkAllowlisted(list.getCompound(i), out, depth + 1);
                    }
                } catch (Throwable ignored) {
                    // soft-fail one entry
                }
            }
        }
    }

    private static void collectAtCompound(CompoundTag tag, LinkedHashSet<String> out) {
        collectFromList(tag.get("s"), out);
        collectFromList(tag.get("schematics"), out);
        collectFromList(tag.get("Schematics"), out);
        // Treatise scrolls store effects here (not schematics).
        collectFromList(tag.get("craftingEffects"), out);
        collectFromList(tag.get("crafting_effects"), out);
        String one = tag.getString("schematic");
        if (one != null && !one.isBlank() && out.size() < MAX_SCHEMATICS) {
            out.add(one.trim());
        }
        one = tag.getString("key");
        if (one != null && ItemVariantKeysText.acceptKey(one) && out.size() < MAX_SCHEMATICS) {
            out.add(one.trim());
        }
    }

    private static void collectFromList(Tag raw, LinkedHashSet<String> out) {
        if (!(raw instanceof ListTag list) || list.isEmpty()) {
            return;
        }
        int n = Math.min(list.size(), MAX_LIST_SCAN);
        for (int i = 0; i < n; i++) {
            if (out.size() >= MAX_SCHEMATICS) {
                return;
            }
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
                            if ("key".equals(k) && !ItemVariantKeysText.acceptKey(v)) {
                                continue;
                            }
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
