package com.skps9.packai.client.emi;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.routes.ItemManagerBridge;

import net.minecraft.world.item.ItemStack;

/** EMI favorites injection via reflection (runtime package not on compile classpath). */
public final class EmiFavoriteBridge implements ItemManagerBridge {
    private static final String EMI_API = "dev.emi.emi.api.EmiApi";
    private static final String EMI_FAVORITES = "dev.emi.emi.runtime.EmiFavorites";
    private static final String EMI_PERSISTENT = "dev.emi.emi.runtime.EmiPersistentData";
    private static final String EMI_STACK = "dev.emi.emi.api.stack.EmiStack";
    private static final String EMI_INGREDIENT = "dev.emi.emi.api.stack.EmiIngredient";
    private static final String EMI_RECIPE = "dev.emi.emi.api.recipe.EmiRecipe";

    private final List<Object> ours = new ArrayList<>();

    private volatile boolean probed;
    private volatile boolean available;
    private Method mEmiStackOf;
    private Method mCanFavorite;
    private Method mAddFavoriteAt;
    private Method mRemoveFavorite;
    private Class<?> emiStackClass;
    private Class<?> emiIngredientClass;
    private Class<?> emiRecipeClass;

    @Override
    public Capability capability() {
        return probe() ? Capability.APPEND_ONLY : Capability.NONE;
    }

    @Override
    public int inject(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty() || !probe()) {
            return 0;
        }
        int index = 0;
        int ok = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            try {
                Object ingredient = mEmiStackOf.invoke(null, stack);
                if (ingredient == null) {
                    continue;
                }
                boolean skip = false;
                try {
                    if (!(boolean) mCanFavorite.invoke(null, ingredient, null)) {
                        skip = true;
                    }
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    // canFavorite(null recipe) may throw; still try addFavoriteAt
                }
                if (skip) {
                    continue;
                }
                mAddFavoriteAt.invoke(null, ingredient, index);
                index++;
                ours.add(ingredient);
                ok++;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                PackAiMod.LOGGER.debug("[packai] EmiFavoriteBridge inject skip", e);
            }
        }
        return ok;
    }

    @Override
    public int clear() {
        if (!probe() || ours.isEmpty()) {
            ours.clear();
            return 0;
        }
        int removed = 0;
        List<Object> copy = new ArrayList<>(ours);
        ours.clear();
        for (Object ingredient : copy) {
            try {
                mRemoveFavorite.invoke(null, ingredient);
                removed++;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                PackAiMod.LOGGER.debug("[packai] EmiFavoriteBridge clear skip", e);
            }
        }
        persistEmiFavorites();
        return removed;
    }

    private static void persistEmiFavorites() {
        try {
            Class<?> favorites = Class.forName(EMI_FAVORITES);
            try {
                favorites.getMethod("save").invoke(null);
                return;
            } catch (NoSuchMethodException ignored) {
                // try EmiPersistentData
            }
            Class<?> persistent = Class.forName(EMI_PERSISTENT);
            persistent.getMethod("save").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] emi save unavailable");
        }
    }

    private boolean probe() {
        if (probed) {
            return available;
        }
        probed = true;
        try {
            Class.forName(EMI_API);
            Class<?> favorites = Class.forName(EMI_FAVORITES);
            emiStackClass = Class.forName(EMI_STACK);
            emiIngredientClass = Class.forName(EMI_INGREDIENT);
            emiRecipeClass = Class.forName(EMI_RECIPE);
            mEmiStackOf = emiStackClass.getMethod("of", ItemStack.class);
            mCanFavorite = favorites.getMethod("canFavorite", emiIngredientClass, emiRecipeClass);
            mAddFavoriteAt = favorites.getMethod("addFavoriteAt", emiIngredientClass, int.class);
            mRemoveFavorite = favorites.getMethod("removeFavorite", emiIngredientClass);
            available = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] EmiFavoriteBridge EMI not available", e);
            available = false;
        }
        return available;
    }
}
