package com.skps9.packai.client.jei;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.AskNameResolve;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.RecipeCard;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Exact display-name match across JEI ingredient types (items + entities + chemicals).
 * Used when empty-hand Ask names a punctuation label like {@code ???}.
 */
public final class JeiTypedLookup {
    private JeiTypedLookup() {}

    public static List<RecipeCard> cardsForQuestion(String question) {
        if (!ModList.get().isLoaded("jei")) {
            return List.of();
        }
        String core = AskNameResolve.nameCore(question);
        Hit overlay = overlayHit();
        if (overlay != null && (core.isBlank()
                || overlay.label.equalsIgnoreCase(core)
                || AskNameResolve.labelMatches(core, overlay.label))) {
            return cardsForHit(overlay);
        }
        if (!AskNameResolve.coreUseful(core)) {
            return List.of();
        }
        Hit exact = findExactLabel(core);
        return exact == null ? List.of() : cardsForHit(exact);
    }

    static List<RecipeCard> cardsForHit(Hit hit) {
        if (hit == null || hit.ingredient == null || hit.type == null) {
            return List.of();
        }
        int perOut = PackAiConfig.recipeCardsPerItem();
        int perUse = PackAiConfig.recipeCardsPerItemUse();
        if (hit.ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            return JeiRecipeCards.forItem(stack, perOut, perUse);
        }
        return JeiRecipeCards.forTyped(hit.type, hit.ingredient, perOut, perUse);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Hit findExactLabel(String label) {
        String want = norm(label);
        if (want.isEmpty()) {
            return null;
        }
        try {
            Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
            if (opt.isEmpty()) {
                return null;
            }
            IIngredientManager manager = opt.get().getIngredientManager();
            Hit itemHit = null;
            Hit other = null;
            for (IIngredientType type : registeredTypes(manager)) {
                if (type == null) {
                    continue;
                }
                IIngredientHelper helper = manager.getIngredientHelper(type);
                Collection all = manager.getAllIngredients(type);
                if (helper == null || all == null) {
                    continue;
                }
                for (Object ingredient : all) {
                    if (ingredient == null || !helper.isValidIngredient(ingredient)) {
                        continue;
                    }
                    String name = norm(String.valueOf(helper.getDisplayName(ingredient)));
                    if (!want.equals(name)) {
                        continue;
                    }
                    String id = resourceId(helper, ingredient);
                    Hit hit = new Hit(type, ingredient, id, name);
                    if (ingredient instanceof ItemStack) {
                        if (itemHit == null) {
                            itemHit = hit;
                        }
                    } else if (other == null) {
                        other = hit;
                    }
                    if (other != null) {
                        return other;
                    }
                }
            }
            return other != null ? other : itemHit;
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI typed label scan skipped: {}", e.toString());
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Hit overlayHit() {
        try {
            Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
            if (opt.isEmpty()) {
                return null;
            }
            IJeiRuntime runtime = opt.get();
            IIngredientManager manager = runtime.getIngredientManager();
            Hit hit = overlayFrom(runtime.getIngredientListOverlay(), manager);
            if (hit != null) {
                return hit;
            }
            hit = overlayFromRuntime(runtime, "getBookmarkOverlay", manager);
            if (hit != null) {
                return hit;
            }
            return overlayFromRuntime(runtime, "getRecipesGui", manager);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Hit overlayFromRuntime(IJeiRuntime runtime, String getter, IIngredientManager manager) {
        try {
            Object overlay = runtime.getClass().getMethod(getter).invoke(runtime);
            return overlayFrom(overlay, manager);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Hit overlayFrom(Object overlay, IIngredientManager manager) {
        if (overlay == null || manager == null) {
            return null;
        }
        for (IIngredientType type : registeredTypes(manager)) {
            if (type == null) {
                continue;
            }
            try {
                Object ingredient = overlay.getClass()
                        .getMethod("getIngredientUnderMouse", IIngredientType.class)
                        .invoke(overlay, type);
                if (ingredient == null) {
                    continue;
                }
                IIngredientHelper helper = manager.getIngredientHelper(type);
                if (helper == null || !helper.isValidIngredient(ingredient)) {
                    continue;
                }
                String name = norm(String.valueOf(helper.getDisplayName(ingredient)));
                if (name.isEmpty()) {
                    continue;
                }
                return new Hit(type, ingredient, resourceId(helper, ingredient), name);
            } catch (ReflectiveOperationException ignored) {
                // overlay API mismatch
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Collection<IIngredientType<?>> registeredTypes(IIngredientManager manager) {
        try {
            return manager.getRegisteredIngredientTypes();
        } catch (Throwable t) {
            try {
                Object types = manager.getClass().getMethod("getIngredientTypes").invoke(manager);
                if (types instanceof Collection<?> c) {
                    return (Collection<IIngredientType<?>>) c;
                }
            } catch (ReflectiveOperationException ignored) {
                // ponytail: JEI API mismatch
            }
            return List.of();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String resourceId(IIngredientHelper helper, Object ingredient) {
        try {
            Object loc = helper.getResourceLocation(ingredient);
            return loc == null ? "" : loc.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        return Plainify.stripMcFormat(raw).trim().toLowerCase(Locale.ROOT);
    }

    record Hit(IIngredientType<?> type, Object ingredient, String id, String label) {}
}
