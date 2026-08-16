package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Compact IO labels for Ask recipe-card catalog lines.
 * Aggregates duplicate slots ({@code 禁书片段×9}) so the LLM does not under-count
 * when a 3×3 uses the same item in every cell.
 */
public final class RecipeIoSummary {
    /** Cap distinct item kinds listed (not total slot count). */
    public static final int MAX_UNIQUE = 12;

    private RecipeIoSummary() {}

    /**
     * Human names for catalog: one entry per registry id, count = sum of stack counts
     * across slots (empty skipped). {@code Name} or {@code Name×N}.
     */
    public static String joinStackNames(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return "";
        }
        LinkedHashMap<String, Agg> byId = new LinkedHashMap<>();
        for (ItemStack st : stacks) {
            if (st == null || st.isEmpty()) {
                continue;
            }
            String id = idOf(st);
            String name = Plainify.stripMcFormat(st.getHoverName().getString());
            if (name == null || name.isBlank()) {
                name = "?";
            }
            int add = Math.max(1, st.getCount());
            Agg prev = byId.get(id);
            if (prev == null) {
                byId.put(id, new Agg(name, add));
            } else {
                prev.count += add;
            }
        }
        if (byId.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Agg> e : byId.entrySet()) {
            if (n >= MAX_UNIQUE) {
                sb.append("…");
                break;
            }
            if (n > 0) {
                sb.append(", ");
            }
            Agg a = e.getValue();
            sb.append(a.name);
            if (a.count > 1) {
                sb.append('×').append(a.count);
            }
            n++;
        }
        return sb.toString();
    }

    /**
     * Test helper: aggregate already-resolved name/count pairs (no ItemStack).
     * Same formatting rules as {@link #joinStackNames}.
     */
    public static String joinNamedCounts(List<String> names, List<Integer> counts) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        LinkedHashMap<String, Agg> byKey = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (name == null || name.isBlank()) {
                continue;
            }
            int add = 1;
            if (counts != null && i < counts.size() && counts.get(i) != null) {
                add = Math.max(1, counts.get(i));
            }
            String key = name.trim().toLowerCase(Locale.ROOT);
            Agg prev = byKey.get(key);
            if (prev == null) {
                byKey.put(key, new Agg(name.trim(), add));
            } else {
                prev.count += add;
            }
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Agg a : byKey.values()) {
            if (n >= MAX_UNIQUE) {
                sb.append("…");
                break;
            }
            if (n > 0) {
                sb.append(", ");
            }
            sb.append(a.name);
            if (a.count > 1) {
                sb.append('×').append(a.count);
            }
            n++;
        }
        return sb.toString();
    }

    /**
     * Extra (entity / gas / …) labels for FACT. Reuses {@link #joinNamedCounts}.
     * Appends helper resource id only when {@link #looksLikeResourceId} — never from display name.
     */
    public static String joinExtraLabels(List<RecipeExtra> extras) {
        if (extras == null || extras.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (RecipeExtra extra : extras) {
            if (extra == null || extra.label() == null || extra.label().isBlank()) {
                continue;
            }
            String name = extra.label().trim();
            String id = extra.uniqueId() == null ? "" : extra.uniqueId().trim();
            if (looksLikeResourceId(id)) {
                name = name + " (" + id + ")";
            }
            names.add(name);
            long amt = extra.amount();
            counts.add(amt > 1L && amt <= Integer.MAX_VALUE ? (int) amt : 1);
        }
        return joinNamedCounts(names, counts);
    }

    /**
     * Output side: item names + fluid display names + extra labels.
     * Fluid names must already be resolved (no registry invent from display).
     */
    public static String joinOutputSide(List<ItemStack> items, List<String> fluidNames, List<RecipeExtra> extras) {
        return joinCommaParts(joinStackNames(items), joinNamedCounts(fluidNames, null), joinExtraLabels(extras));
    }

    /** {@code namespace:path} from a helper — not a display name. */
    public static boolean looksLikeResourceId(String raw) {
        if (raw == null || raw.isBlank() || raw.indexOf(' ') >= 0) {
            return false;
        }
        String s = raw.trim();
        int c = s.indexOf(':');
        return c > 0 && c < s.length() - 1 && s.indexOf(':') == s.lastIndexOf(':');
    }

    private static String joinCommaParts(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    /** Non-empty input slot count for CRAFTING_3X3 / SHAPED / FLOW. */
    public static int countFilledInputs(RecipeCard card) {
        if (card == null) {
            return 0;
        }
        int n = 0;
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3 && card.grid() != null) {
            for (ItemStack st : card.grid()) {
                if (st != null && !st.isEmpty()) {
                    n++;
                }
            }
            return n;
        }
        if (card.layout() == RecipeCard.Layout.SHAPED && card.placedInputs() != null) {
            for (RecipeCard.PlacedItem p : card.placedInputs()) {
                if (p != null
                        && p.kind() == RecipeCard.SlotKind.INPUT
                        && p.stack() != null
                        && !p.stack().isEmpty()) {
                    n++;
                }
            }
            return n;
        }
        if (card.inputs() != null) {
            for (ItemStack st : card.inputs()) {
                if (st != null && !st.isEmpty()) {
                    n++;
                }
            }
        }
        return n;
    }

    private static String idOf(ItemStack st) {
        try {
            ResourceLocation key = Registry.ITEM.getKey(st.getItem());
            if (key != null) {
                return key.toString().toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
            // headless
        }
        String name = Plainify.stripMcFormat(st.getHoverName().getString());
        return name == null ? "?" : name.toLowerCase(Locale.ROOT);
    }

    private static final class Agg {
        final String name;
        int count;

        Agg(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}
