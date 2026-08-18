package com.skps9.packai.client.jei;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.JeiInfoFacts;
import com.skps9.packai.logic.Plainify;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Harvest JEI Information / 信息 recipe text + item slots for Ask FACT.
 */
public final class JeiInfoPages {
    private static final int MAX_SCAN = 200;
    private static final int MAX_PAGES = 12;
    private static final int MAX_TEXT = 400;

    private JeiInfoPages() {}

    public static String dump(ItemStack focus, String lang) {
        List<String> lines = collectDumpLines(focus);
        if (lines.isEmpty()) {
            return "";
        }
        String header = lang != null && lang.toLowerCase().startsWith("en")
                ? "JEI information:\n"
                : "JEI information：\n";
        StringBuilder sb = new StringBuilder(header);
        for (String line : lines) {
            sb.append("  ").append(line).append('\n');
        }
        return sb.toString().trim();
    }

    static List<String> collectDumpLines(ItemStack focus) {
        if (focus == null || focus.isEmpty() || !ModList.get().isLoaded("jei")) {
            return List.of();
        }
        try {
            return collectUnsafe(focus);
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI information harvest skipped: {}", t.toString());
            return List.of();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<String> collectUnsafe(ItemStack focus) {
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return List.of();
        }
        String focusId = itemId(focus);
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IIngredientManager ingredients = runtime.getIngredientManager();
        List<IRecipeCategory<?>> cats;
        try {
            cats = recipes.createRecipeCategoryLookup().includeHidden().get().toList();
        } catch (Throwable t) {
            cats = recipes.createRecipeCategoryLookup().get().toList();
        }
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (IRecipeCategory<?> category : cats) {
            if (lines.size() >= MAX_PAGES) {
                break;
            }
            String uid = JeiCategoryCatalog.categoryUid(category);
            String title = Plainify.stripMcFormat(category.getTitle().getString());
            if (!JeiInfoFacts.isInfoCategory(uid, title)) {
                continue;
            }
            RecipeType type = category.getRecipeType();
            List<?> found;
            try {
                found = recipes.createRecipeLookup(type).includeHidden().get()
                        .limit(MAX_SCAN).toList();
            } catch (Throwable t) {
                continue;
            }
            IRecipeCategory raw = category;
            for (Object recipe : found) {
                if (lines.size() >= MAX_PAGES) {
                    break;
                }
                JeiRecipeLayoutCollector.CollectedLayout layout = null;
                try {
                    layout = JeiRecipeLayoutCollector.collect(raw, recipe, ingredients);
                } catch (Throwable ignored) {
                    // text-only
                }
                List<String> allIds = new ArrayList<>();
                List<String> outIds = new ArrayList<>();
                if (layout != null) {
                    collectIds(layout, allIds, outIds);
                }
                String text = extractText(raw, recipe);
                if (text.isBlank()) {
                    continue;
                }
                boolean hit = focusId.isEmpty()
                        || allIds.stream().anyMatch(id -> JeiInfoFacts.sameItem(id, focusId))
                        || JeiInfoFacts.mentionsFocus(text, focusId);
                if (!hit) {
                    continue;
                }
                JeiInfoFacts.Kind kind = JeiInfoFacts.classify(focusId, outIds, text);
                List<String> related = new ArrayList<>();
                for (String id : allIds) {
                    if (!JeiInfoFacts.sameItem(id, focusId)) {
                        related.add(id);
                    }
                }
                lines.add(JeiInfoFacts.dumpLine(kind, text, related));
            }
        }
        return List.copyOf(lines);
    }

    private static void collectIds(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            List<String> allIds,
            List<String> outIds
    ) {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        LinkedHashSet<String> outs = new LinkedHashSet<>();
        for (RecipeIngredientRole role : RecipeIngredientRole.values()) {
            List<ItemStack> stacks;
            try {
                stacks = layout.itemStacks(role);
            } catch (Throwable t) {
                continue;
            }
            for (ItemStack stack : stacks) {
                String id = itemId(stack);
                if (id.isEmpty()) {
                    continue;
                }
                all.add(id);
                if (role == RecipeIngredientRole.OUTPUT) {
                    outs.add(id);
                }
            }
        }
        allIds.addAll(all);
        outIds.addAll(outs);
    }

    static String extractText(IRecipeCategory<?> category, Object recipe) {
        LinkedHashSet<String> notes = new LinkedHashSet<>();
        harvestReflect(recipe, notes, 0, new IdentityHashMap<>());
        try {
            for (String n : JeiReqNotes.harvest(category, recipe, null)) {
                if (n != null && !n.isBlank()) {
                    notes.add(n.trim());
                }
            }
        } catch (Throwable ignored) {
            // optional
        }
        if (notes.isEmpty()) {
            return "";
        }
        String joined = String.join(" ", notes).trim();
        if (joined.length() > MAX_TEXT) {
            joined = joined.substring(0, MAX_TEXT);
        }
        return Plainify.stripMcFormat(joined).trim();
    }

    private static void harvestReflect(
            Object value, LinkedHashSet<String> notes, int depth, Map<Object, Boolean> seen
    ) {
        if (value == null || depth > 3 || notes.size() >= 8) {
            return;
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            return;
        }
        if (value instanceof ItemStack) {
            return;
        }
        if (value instanceof Component component) {
            acceptText(notes, component.getString());
            return;
        }
        if (value instanceof FormattedText formatted) {
            acceptText(notes, formatted.getString());
            return;
        }
        if (value instanceof CharSequence cs) {
            acceptText(notes, cs.toString());
            return;
        }
        if (value instanceof Collection<?> col) {
            for (Object o : col) {
                harvestReflect(o, notes, depth + 1, seen);
                if (notes.size() >= 8) {
                    return;
                }
            }
            return;
        }
        if (value.getClass().isArray()) {
            int n = Array.getLength(value);
            for (int i = 0; i < n && i < 32; i++) {
                harvestReflect(Array.get(value, i), notes, depth + 1, seen);
            }
            return;
        }
        String cn = value.getClass().getName();
        if (cn.startsWith("java.") || cn.startsWith("javax.") || cn.startsWith("sun.")) {
            return;
        }
        for (Class<?> c = value.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field f : fields) {
                String fn = f.getName().toLowerCase();
                if (fn.contains("ingredient") && !fn.contains("desc") && !fn.contains("text")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    harvestReflect(f.get(value), notes, depth + 1, seen);
                } catch (Throwable ignored) {
                    // skip
                }
            }
        }
    }

    private static void acceptText(LinkedHashSet<String> notes, String raw) {
        String cleaned = Plainify.stripMcFormat(raw == null ? "" : raw).trim();
        if (cleaned.length() < 2 || cleaned.length() > MAX_TEXT) {
            return;
        }
        if (cleaned.contains("://") || cleaned.startsWith("mezz.jei")) {
            return;
        }
        notes.add(cleaned);
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }
}
