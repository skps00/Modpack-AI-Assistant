package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.gui.GuiGraphics;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

/**
 * Holds JEI chemical / gas / slurry copies so recipe cards can render them
 * without a hard Mekanism dependency.
 */
public final class JeiSoftIngredients {
    private static final Map<String, Entry> BY_ID = new ConcurrentHashMap<>();
    private static int seq;

    private record Entry(IIngredientType<?> type, Object ingredient) {}

    private JeiSoftIngredients() {}

    public static String put(ITypedIngredient<?> typed, IIngredientManager manager) {
        if (typed == null) {
            return "";
        }
        return put(typed.getType(), typed.getIngredient(), manager);
    }

    public static String put(JeiRecipeLayoutCollector.CollectedIngredient typed, IIngredientManager manager) {
        if (typed == null) {
            return "";
        }
        return put(typed.type(), typed.ingredient(), manager);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static String put(IIngredientType<?> type, Object ingredient, IIngredientManager manager) {
        if (type == null || ingredient == null || manager == null) {
            return "";
        }
        try {
            if (BY_ID.size() > 200) {
                BY_ID.clear();
            }
            IIngredientHelper helper = manager.getIngredientHelper((IIngredientType) type);
            Object copy = helper.copyIngredient(ingredient);
            String id = "soft-" + (seq++) + "-" + Integer.toHexString(System.identityHashCode(copy));
            BY_ID.put(id, new Entry(type, copy));
            return id;
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JeiSoftIngredients.put failed: {}", t.toString());
            return "";
        }
    }

    public static boolean render(GuiGraphics graphics, String softId, int x, int y) {
        if (graphics == null) {
            return false;
        }
        return render(graphics.pose(), softId, x, y);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean render(PoseStack pose, String softId, int x, int y) {
        Entry entry = BY_ID.get(softId);
        if (entry == null || pose == null) {
            return false;
        }
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return false;
        }
        try {
            IIngredientRenderer renderer = opt.get().getIngredientManager().getIngredientRenderer((IIngredientType) entry.type());
            renderer.render(pose, entry.ingredient(), x, y);
            return true;
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JeiSoftIngredients.render failed: {}", t.toString());
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<Component> tooltip(String softId) {
        Entry entry = BY_ID.get(softId);
        if (entry == null) {
            return List.of();
        }
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return List.of();
        }
        try {
            IIngredientRenderer renderer = opt.get().getIngredientManager().getIngredientRenderer((IIngredientType) entry.type());
            TooltipFlag flag = Minecraft.getInstance().options.advancedItemTooltips
                    ? TooltipFlag.Default.ADVANCED
                    : TooltipFlag.Default.NORMAL;
            List<?> raw = renderer.getTooltip(entry.ingredient(), flag);
            List<Component> out = new ArrayList<>();
            if (raw != null) {
                for (Object line : raw) {
                    if (line instanceof Component component) {
                        out.add(component);
                    } else if (line != null) {
                        out.add(Component.literal(String.valueOf(line)));
                    }
                }
            }
            return out;
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JeiSoftIngredients.tooltip failed: {}", t.toString());
            return List.of();
        }
    }

    public static void clear() {
        BY_ID.clear();
    }
}
