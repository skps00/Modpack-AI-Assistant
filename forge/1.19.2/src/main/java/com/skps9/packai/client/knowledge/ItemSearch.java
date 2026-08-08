package com.skps9.packai.client.knowledge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.jei.PackAiJeiPlugin;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.logic.Plainify;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Minimal pack item search by display name / registry id.
 * Prefers JEI ingredient list (NBT variants); merges registry defaults.
 */
public final class ItemSearch {
    public static final int DEFAULT_LIMIT = 10;
    /**
     * ponytail: bounded heap while full-scanning; no early-break that freezes first 80 JEI hits.
     * Upgrade: debounce + prefix index.
     */
    private static final int SCAN_CANDIDATE_CAP = 80;

    public record Hit(ItemStack stack, String id, String label) {
        public Hit {
            stack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            id = id == null ? "" : id;
            label = label == null ? "" : label;
        }
    }

    private ItemSearch() {}

    public static List<Hit> search(String query, int limit) {
        String q = norm(query);
        if (q.isEmpty() || limit <= 0) {
            return List.of();
        }
        int cap = Math.min(Math.max(limit, 1), DEFAULT_LIMIT);
        Map<String, Scored> best = new LinkedHashMap<>();
        if (ModList.get().isLoaded("jei")) {
            try {
                Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
                if (opt.isPresent()) {
                    Collection<ItemStack> all =
                            opt.get().getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK);
                    for (ItemStack stack : all) {
                        consider(best, stack, q);
                    }
                }
            } catch (NoClassDefFoundError | Exception e) {
                PackAiMod.LOGGER.debug("ItemSearch JEI scan failed: {}", e.toString());
            }
        }
        // Merge registry so later / missing JEI ids can still rank into top-N.
        for (var entry : Registry.ITEM.entrySet()) {
            consider(best, new ItemStack(entry.getValue()), q);
        }
        List<Scored> ranked = new ArrayList<>(best.values());
        ranked.sort(Comparator.comparingInt(Scored::score).thenComparing(Scored::label));
        List<Hit> out = new ArrayList<>(cap);
        for (Scored s : ranked) {
            if (out.size() >= cap) {
                break;
            }
            out.add(new Hit(s.stack(), s.id(), s.label()));
        }
        return List.copyOf(out);
    }

    private static void consider(Map<String, Scored> best, ItemStack stack, String q) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        if (key == null) {
            return;
        }
        String id = key.toString();
        String label = Plainify.stripMcFormat(stack.getHoverName().getString());
        int score = score(q, id, label);
        if (score >= 99) {
            return;
        }
        // Same multi-select key so Tetra scroll_rolled NBT siblings stay distinct.
        String dedupe = AskService.selectionKey(AskService.fromStack(stack));
        if (dedupe.isEmpty()) {
            dedupe = id.toLowerCase(Locale.ROOT) + "|" + norm(label);
        }
        Scored prev = best.get(dedupe);
        if (prev != null && score >= prev.score()) {
            return;
        }
        if (prev == null && best.size() >= SCAN_CANDIDATE_CAP && !admitOverWorst(best, score, label)) {
            return;
        }
        best.put(dedupe, new Scored(stack.copy(), id, label, score));
    }

    /** Drop one worst entry so {@code score}/{@code label} can enter the bounded heap. */
    private static boolean admitOverWorst(Map<String, Scored> best, int score, String label) {
        String worstKey = null;
        Scored worst = null;
        for (var e : best.entrySet()) {
            Scored s = e.getValue();
            if (worst == null
                    || s.score() > worst.score()
                    || (s.score() == worst.score() && s.label().compareTo(worst.label()) > 0)) {
                worst = s;
                worstKey = e.getKey();
            }
        }
        if (worst == null) {
            return true;
        }
        if (score > worst.score()) {
            return false;
        }
        if (score == worst.score() && label.compareTo(worst.label()) >= 0) {
            return false;
        }
        best.remove(worstKey);
        return true;
    }

    /**
     * Lower is better. 99 = no match.
     * Registry-id prefix/contains use path after {@code :} unless query includes {@code namespace:}.
     */
    static int score(String q, String id, String label) {
        if (q == null || q.isEmpty()) {
            return 99;
        }
        String idl = id == null ? "" : id.toLowerCase(Locale.ROOT);
        String nl = norm(label);
        if (idl.equals(q)) {
            return 0;
        }
        int colon = idl.indexOf(':');
        String path = colon >= 0 ? idl.substring(colon + 1) : idl;
        boolean qHasNs = q.indexOf(':') >= 0;
        if (qHasNs) {
            if (idl.startsWith(q)) {
                return 2;
            }
            if (idl.contains(q)) {
                return 3;
            }
        } else {
            if (path.equals(q) || idl.endsWith(":" + q)) {
                return 1;
            }
            if (path.startsWith(q)) {
                return 2;
            }
            if (path.contains(q)) {
                return 3;
            }
        }
        if (nl.equals(q)) {
            return 4;
        }
        if (nl.startsWith(q)) {
            return 5;
        }
        if (nl.contains(q)) {
            return 6;
        }
        return 99;
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return Plainify.stripMcFormat(s).trim().replace('「', '"').replace('」', '"').toLowerCase(Locale.ROOT);
    }

    private record Scored(ItemStack stack, String id, String label, int score) {}
}
