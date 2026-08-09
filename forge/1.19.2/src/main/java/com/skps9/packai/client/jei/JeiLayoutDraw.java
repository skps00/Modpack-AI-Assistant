package com.skps9.packai.client.jei;

import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.gui.GuiGraphics;
import com.skps9.packai.logic.RecipeCard;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.renderer.Rect2i;

/**
 * Draws JEI category background + extras (flame / clock / arrows) via official layout drawable.
 * <p>
 * ponytail: no offscreen FBO — reuse {@link IRecipeManager#createRecipeLayoutDrawable}.
 * Ceiling: pose-scale breaks JEI internal mouse highlight; we pass -1,-1 when scaled and
 * rely on Pack AI placed-slot hover. Upgrade: true FBO blit if mods need pixel-perfect scale.
 */
public final class JeiLayoutDraw {
    private JeiLayoutDraw() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static RecipeCard attach(
            RecipeCard card,
            IRecipeManager recipes,
            IRecipeCategory<?> category,
            Object recipe
    ) {
        if (card == null || card.isEmpty() || card.layout() != RecipeCard.Layout.SHAPED
                || recipes == null || category == null || recipe == null) {
            return card;
        }
        if (card.jeiLayout() instanceof IRecipeLayoutDrawable) {
            return card;
        }
        try {
            Optional<IRecipeLayoutDrawable> opt = recipes.createRecipeLayoutDrawable(
                    (IRecipeCategory) category,
                    recipe,
                    JeiRecipeLayoutCollector.emptyFocus());
            if (opt.isEmpty()) {
                return card;
            }
            return card.withJeiLayout(opt.get());
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI layout drawable skipped: {}", t.toString());
            return card;
        }
    }

    public static boolean hasLayout(RecipeCard card) {
        return card != null && card.jeiLayout() instanceof IRecipeLayoutDrawable;
    }

    public static int width(RecipeCard card) {
        Rect2i r = rect(card);
        return r == null ? 0 : Math.max(1, r.getWidth());
    }

    public static int height(RecipeCard card) {
        Rect2i r = rect(card);
        return r == null ? 0 : Math.max(1, r.getHeight());
    }

    /**
     * @return true if JEI layout was drawn (caller should skip slot-harvest paint)
     */
    public static boolean draw(
            GuiGraphics graphics,
            RecipeCard card,
            int left,
            int top,
            float scale,
            int mouseX,
            int mouseY
    ) {
        if (!(card != null && card.jeiLayout() instanceof IRecipeLayoutDrawable<?> drawable)) {
            return false;
        }
        try {
            drawable.tick();
            var pose = graphics.pose();
            if (scale < 0.999f) {
                drawable.setPosition(0, 0);
                pose.pushPose();
                pose.translate(left, top, 0);
                pose.scale(scale, scale, 1.0f);
                // ponytail: scaled pose ≠ JEI hit-test space
                drawable.drawRecipe(pose, -1, -1);
                pose.popPose();
            } else {
                drawable.setPosition(left, top);
                drawable.drawRecipe(pose, mouseX, mouseY);
            }
            drawable.setPosition(0, 0);
            return true;
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI layout draw failed: {}", t.toString());
            return false;
        }
    }

    private static Rect2i rect(RecipeCard card) {
        if (!(card != null && card.jeiLayout() instanceof IRecipeLayoutDrawable<?> drawable)) {
            return null;
        }
        try {
            drawable.setPosition(0, 0);
            return drawable.getRect();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
