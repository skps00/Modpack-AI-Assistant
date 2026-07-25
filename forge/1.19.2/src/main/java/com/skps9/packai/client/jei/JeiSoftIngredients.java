package com.skps9.packai.client.jei;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.PackAiMod;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.network.chat.Component;

/**
 * Soft chemical cache for future machine cards. Parity UI uses text cards only.
 */
public final class JeiSoftIngredients {
    private static final Map<String, Entry> BY_ID = new ConcurrentHashMap<>();
    private static int seq;

    private record Entry(Object type, Object ingredient) {}

    private JeiSoftIngredients() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static String put(ITypedIngredient typed, IIngredientManager manager) {
        if (typed == null || manager == null) {
            return "";
        }
        try {
            if (BY_ID.size() > 200) {
                BY_ID.clear();
            }
            IIngredientHelper helper = manager.getIngredientHelper(typed.getType());
            Object copy = helper.copyIngredient(typed.getIngredient());
            String id = "soft-" + (seq++) + "-" + Integer.toHexString(System.identityHashCode(copy));
            BY_ID.put(id, new Entry(typed.getType(), copy));
            return id;
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JeiSoftIngredients.put failed: {}", t.toString());
            return "";
        }
    }

    public static boolean render(PoseStack pose, String softId, int x, int y) {
        return false;
    }

    public static List<Component> tooltip(String softId) {
        return List.of();
    }

    public static void clear() {
        BY_ID.clear();
    }
}
