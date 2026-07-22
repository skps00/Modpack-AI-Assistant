package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.CraftPriority;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.RecipeCategoryPrefs;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.neoforged.fml.ModList;

/**
 * Lists JEI recipe categories for the settings screen (order + visibility).
 */
public final class JeiCategoryCatalog {
    private JeiCategoryCatalog() {}

    public record Entry(String uid, String title) {}

    /** UI row: drag order + enabled flag. */
    public record Row(String uid, String title, boolean enabled) {
        public Row withEnabled(boolean on) {
            return new Row(uid, title, on);
        }
    }

    public static boolean jeiAvailable() {
        return ModList.get().isLoaded("jei") && PackAiJeiPlugin.runtime().isPresent();
    }

    /** All non-spam JEI categories currently registered. */
    public static List<Entry> allEntries() {
        if (!ModList.get().isLoaded("jei")) {
            return List.of();
        }
        try {
            return collectEntries();
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI category catalog skipped: {}", e.toString());
            return List.of();
        }
    }

    /**
     * Rows for the settings list: custom order first, then remaining by heuristic;
     * applies hidden prefs.
     */
    public static List<Row> rowsForUi() {
        List<Entry> all = allEntries();
        if (all.isEmpty()) {
            return List.of();
        }
        Map<String, Entry> byUid = new HashMap<>();
        for (Entry e : all) {
            byUid.put(e.uid(), e);
        }
        Set<String> hidden = RecipeCategoryPrefs.hiddenSet();
        List<String> order = PackAiConfig.recipeCategoryOrder();
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (!order.isEmpty()) {
            for (String uid : order) {
                Entry e = byUid.get(uid);
                if (e == null || !seen.add(uid)) {
                    continue;
                }
                rows.add(new Row(uid, e.title(), !hidden.contains(uid)));
            }
        }

        List<Entry> rest = new ArrayList<>();
        for (Entry e : all) {
            if (!seen.contains(e.uid())) {
                rest.add(e);
            }
        }
        rest.sort(Comparator
                .comparingInt((Entry e) -> CraftPriority.categoryTier(e.title()))
                .thenComparingInt(e -> CraftPriority.speedTier(e.title()))
                .thenComparing(Entry::title));
        for (Entry e : rest) {
            rows.add(new Row(e.uid(), e.title(), !hidden.contains(e.uid())));
        }
        return rows;
    }

    public static void saveRows(List<Row> rows) {
        if (rows == null) {
            return;
        }
        List<String> order = new ArrayList<>();
        Set<String> hidden = new LinkedHashSet<>();
        for (Row r : rows) {
            if (r == null || r.uid() == null || r.uid().isBlank()) {
                continue;
            }
            order.add(r.uid());
            if (!r.enabled()) {
                hidden.add(r.uid());
            }
        }
        PackAiConfig.setRecipeCategoryPrefs(order, hidden);
    }

    public static String categoryUid(IRecipeCategory<?> category) {
        if (category == null) {
            return "";
        }
        try {
            RecipeType<?> type = category.getRecipeType();
            if (type == null) {
                return "";
            }
            Object u = type.getUid();
            return u == null ? "" : u.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Entry> collectEntries() {
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return List.of();
        }
        IRecipeManager recipes = opt.get().getRecipeManager();
        List<IRecipeCategory<?>> categories = recipes.createRecipeCategoryLookup()
                .get()
                .toList();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Entry> out = new ArrayList<>();
        for (IRecipeCategory<?> category : categories) {
            String uid = categoryUid(category);
            if (uid.isBlank() || !seen.add(uid)) {
                continue;
            }
            String title = Plainify.stripMcFormat(category.getTitle().getString());
            RecipeType type = category.getRecipeType();
            if (JeiUniversalSpam.isSpamCategory(type, title)) {
                continue;
            }
            out.add(new Entry(uid, title.isBlank() ? uid : title));
        }
        return List.copyOf(out);
    }
}
