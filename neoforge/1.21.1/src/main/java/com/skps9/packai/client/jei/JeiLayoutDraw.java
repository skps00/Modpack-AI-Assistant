package com.skps9.packai.client.jei;

import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.RecipeCard;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * Draws JEI category background + extras (flame / clock / arrows) via official layout drawable.
 * <p>
 * ponytail: no offscreen FBO — reuse {@link IRecipeManager#createRecipeLayoutDrawable}.
 * Ceiling: pose-scale breaks JEI internal mouse highlight; we pass -1,-1 when scaled and
 * map mouse back via {@link #itemUnderMouse} for Pack AI tooltips. Upgrade: true FBO blit
 * if mods need pixel-perfect scale + JEI native overlay highlights.
 */
public final class JeiLayoutDraw {
    /**
     * Room past {@link IRecipeLayoutDrawable#getRect()} for category.draw decorations
     * (clock / flame) that paint slightly outside the reported rect.
     * ponytail: pad heuristic, not measured ink bounds — FBO/measure if still clipped.
     */
    public static final int OUTSIDE_DRAW_PAD = 14;

    private JeiLayoutDraw() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static RecipeCard attach(
            RecipeCard card,
            IRecipeManager recipes,
            IRecipeCategory<?> category,
            Object recipe
    ) {
        return attach(card, recipes, category, recipe, null);
    }

    /**
     * Prefer JEI focus group from Ask lookup; fall back to empty focus.
     * All card layouts (CRAFTING_3X3 / FLOW / SHAPED) — JEI may still return empty.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static RecipeCard attach(
            RecipeCard card,
            IRecipeManager recipes,
            IRecipeCategory<?> category,
            Object recipe,
            mezz.jei.api.recipe.IFocusGroup focusGroup
    ) {
        if (card == null || card.isEmpty()
                || recipes == null || category == null || recipe == null) {
            return card;
        }
        if (card.jeiLayout() instanceof IRecipeLayoutDrawable) {
            return card;
        }
        mezz.jei.api.recipe.IFocusGroup primary = focusGroup != null
                ? focusGroup
                : JeiRecipeLayoutCollector.emptyFocus();
        try {
            Optional<IRecipeLayoutDrawable> opt = recipes.createRecipeLayoutDrawable(
                    (IRecipeCategory) category,
                    recipe,
                    primary);
            if (opt.isEmpty() && focusGroup != null) {
                opt = recipes.createRecipeLayoutDrawable(
                        (IRecipeCategory) category,
                        recipe,
                        JeiRecipeLayoutCollector.emptyFocus());
            }
            if (opt.isEmpty()) {
                // Never-empty API — still get a drawable (or error placeholder) for crafting/etc.
                IRecipeLayoutDrawable<?> forced = recipes.createRecipeLayoutDrawableOrShowError(
                        (IRecipeCategory) category,
                        recipe,
                        primary);
                if (forced != null) {
                    return card.withJeiLayout(forced);
                }
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
     * Size for scale + card body: JEI rect ∪ placed slot extents + {@link #OUTSIDE_DRAW_PAD}.
     * Keeps footer / next chat line from covering clock/flame drawn just outside getRect.
     */
    public static int layoutFitWidth(RecipeCard card) {
        int w = width(card);
        if (card != null && card.placedInputs() != null) {
            for (RecipeCard.PlacedItem p : card.placedInputs()) {
                if (p != null) {
                    w = Math.max(w, p.x() + 16);
                }
            }
        }
        return Math.max(1, w + OUTSIDE_DRAW_PAD);
    }

    /** @see #layoutFitWidth */
    public static int layoutFitHeight(RecipeCard card) {
        int h = height(card);
        if (card != null && card.placedInputs() != null) {
            for (RecipeCard.PlacedItem p : card.placedInputs()) {
                if (p != null) {
                    h = Math.max(h, p.y() + 16);
                }
            }
        }
        return Math.max(1, h + OUTSIDE_DRAW_PAD);
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
            if (scale < 0.999f) {
                drawable.setPosition(0, 0);
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(left, top, 0);
                pose.scale(scale, scale, 1.0f);
                // ponytail: scaled pose ≠ JEI hit-test space
                drawable.drawRecipe(graphics, -1, -1);
                pose.popPose();
            } else {
                drawable.setPosition(left, top);
                drawable.drawRecipe(graphics, mouseX, mouseY);
            }
            // reset so later size queries stay origin-relative
            drawable.setPosition(0, 0);
            return true;
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI layout draw failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Item under mouse in a Pack AI–placed (possibly scaled) JEI layout.
     * Maps screen mouse into JEI hit-test space — required when {@link #draw} used -1,-1.
     */
    public static Optional<ItemStack> itemUnderMouse(
            RecipeCard card,
            int left,
            int top,
            float scale,
            int mouseX,
            int mouseY
    ) {
        if (!(card != null && card.jeiLayout() instanceof IRecipeLayoutDrawable<?> drawable)) {
            return Optional.empty();
        }
        try {
            int[] jeiMouse = mapScreenMouseToJei(left, top, scale, mouseX, mouseY);
            if (scale < 0.999f) {
                drawable.setPosition(0, 0);
            } else {
                drawable.setPosition(left, top);
            }
            Optional<ItemStack> hit = drawable.getItemStackUnderMouse(jeiMouse[0], jeiMouse[1]);
            drawable.setPosition(0, 0);
            if (hit == null || hit.isEmpty() || hit.get().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(hit.get().copy());
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI layout under-mouse skipped: {}", t.toString());
            try {
                if (card.jeiLayout() instanceof IRecipeLayoutDrawable<?> d) {
                    d.setPosition(0, 0);
                }
            } catch (Throwable ignored) {
                // ignore reset failure
            }
            return Optional.empty();
        }
    }

    /**
     * Screen mouse → coords for {@link IRecipeLayoutDrawable#getItemStackUnderMouse}.
     * Scaled draw keeps drawable at (0,0); unscaled uses {@code setPosition(left,top)}.
     */
    static int[] mapScreenMouseToJei(int left, int top, float scale, int mouseX, int mouseY) {
        if (scale < 0.999f) {
            float s = Math.max(0.001f, scale);
            return new int[]{
                    Math.round((mouseX - left) / s),
                    Math.round((mouseY - top) / s)
            };
        }
        return new int[]{mouseX, mouseY};
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
