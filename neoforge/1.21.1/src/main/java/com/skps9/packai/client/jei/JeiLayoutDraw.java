package com.skps9.packai.client.jei;

import java.util.Optional;

import com.mojang.blaze3d.platform.Lighting;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.RecipeCard;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Draws JEI category background + extras (flame / clock / arrows) via official layout drawable.
 * Fluids come from JEI {@code RecipeSlot} → {@code FluidTankRenderer} inside
 * {@link IRecipeLayoutDrawable#drawRecipe} — Pack AI must not overlay a custom tank painter.
 * <p>
 * Slot hover highlight lives in JEI {@code drawOverlays} / {@code IRecipeSlotDrawable#drawHoverOverlays}
 * — <em>not</em> in {@code drawRecipe}. Pack AI calls {@link #drawSlotHoverHighlight} after the body;
 * full {@code drawOverlays} skipped so JEI tooltips do not fight Pack AI
 * {@link #layoutHoverUnderMouse}.
 * <p>
 * Always draw 1:1 ({@code setPosition(left,top)}). Do <em>not</em> {@code PoseStack.scale} —
 * JEI slot/arrow blits vs item render under non-1 scale left Create Sawing OUTPUT drifting toward
 * the arrow. Chat scissor clips overflow; card height uses full {@link #height}.
 * <p>
 * Before/after {@code drawRecipe}: {@code Lighting.setupForFlatItems} — Create
 * {@code GuiGameElement} may leave {@code setupFor3DItems} when {@code customLighting==null}.
 * Do <em>not</em> reset ModelView to identity: that wipes the GUI matrix JEI needs and blanks
 * the whole layout panel (caption/catalyst still draw via PoseStack alone).
 * <p>
 * Hexerei Woodcutter / PestleAndMortar {@code category.draw} calls {@code PoseStack.scale(0.6)}
 * without push; JEI {@code drawRecipe} paints slots <em>after</em> that draw, so icons shrink vs
 * the 1:1 background. Pack AI reorders Hexerei: background → slots@1.0 → extras (push/pop).
 */
public final class JeiLayoutDraw {
    /**
     * Room past {@link IRecipeLayoutDrawable#getRect()} for category.draw decorations
     * (clock / flame) that paint slightly outside the reported rect.
     * Chat stride uses a smaller overflow; do not fold this into {@code shapedScale}.
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
     * JEI rect ∪ placed extents + {@link #OUTSIDE_DRAW_PAD} — clip-room heuristic.
     * Not used for {@code shapedScale} (that uses {@link #width}/{@link #height} only).
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
        if (card != null && card.placedFluids() != null) {
            for (RecipeCard.PlacedFluid p : card.placedFluids()) {
                if (p != null) {
                    w = Math.max(w, p.x() + p.width());
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
        if (card != null && card.placedFluids() != null) {
            for (RecipeCard.PlacedFluid p : card.placedFluids()) {
                if (p != null) {
                    h = Math.max(h, p.y() + p.height());
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
            // Do NOT overlay Pack AI drawFluidSlot — JEI drawRecipe owns tank look.
            // Always 1:1 (ignore scale). Flat lighting only — do NOT identity ModelView
            // (wipes GUI matrix → blank cards). Create AnimatedSaw may leave 3D lighting.
            Lighting.setupForFlatItems();
            drawable.setPosition(left, top);
            if (isHexereiCategory(drawable.getRecipeCategory())) {
                drawHexereiSlotsBeforeExtras(graphics, drawable, mouseX, mouseY);
            } else {
                drawable.drawRecipe(graphics, mouseX, mouseY);
            }
            Lighting.setupForFlatItems();
            drawSlotHoverHighlight(graphics, drawable, mouseX, mouseY);
            drawable.setPosition(0, 0);
            return true;
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI layout draw failed: {}", t.toString());
            try {
                if (card.jeiLayout() instanceof IRecipeLayoutDrawable<?> d) {
                    d.setPosition(0, 0);
                }
            } catch (Throwable ignored) {
                // ignore reset failure
            }
            return false;
        }
    }

    /**
     * Hexerei JEI categories (uid namespace {@code hexerei} or {@code net.joefoxe.hexerei.*}).
     * Woodcutter / Mortar leak {@code pose.scale(0.6)} into JEI slot draw.
     */
    static boolean isHexereiCategory(IRecipeCategory<?> category) {
        if (category == null) {
            return false;
        }
        try {
            var type = category.getRecipeType();
            if (type != null) {
                ResourceLocation uid = type.getUid();
                if (uid != null && "hexerei".equals(uid.getNamespace())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // fall through to class name
        }
        try {
            return category.getClass().getName().startsWith("net.joefoxe.hexerei");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * JEI {@code drawRecipe} order is bg → {@code category.draw} → slots. Hexerei scales the pose
     * in {@code draw} without push, so slots render at 0.6 vs 1:1 background.
     * Reorder: bg → slots@1.0 → extras (push/pop isolates in-place scale).
     * ponytail: Hexerei-only; vanilla / Create / FTB keep {@code drawRecipe}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void drawHexereiSlotsBeforeExtras(
            GuiGraphics graphics,
            IRecipeLayoutDrawable<?> drawable,
            int mouseX,
            int mouseY
    ) {
        IRecipeCategory category = drawable.getRecipeCategory();
        Object recipe = drawable.getRecipe();
        Rect2i area = drawable.getRect();
        int ax = area.getX();
        int ay = area.getY();
        IDrawable bg = category.getBackground();
        IRecipeSlotsView slotsView = drawable.getRecipeSlotsView();
        var pose = graphics.pose();

        pose.pushPose();
        pose.translate(ax, ay, 0);
        if (bg != null) {
            bg.draw(graphics, 0, 0);
        }
        for (IRecipeSlotView slotView : slotsView.getSlotViews()) {
            if (slotView instanceof IRecipeSlotDrawable slot) {
                slot.draw(graphics);
            }
        }
        pose.pushPose();
        try {
            category.draw(recipe, slotsView, graphics, (double) (mouseX - ax), (double) (mouseY - ay));
        } finally {
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * JEI slot under mouse mapped into Pack AI screen space (scaled card pose).
     * Uses {@link IRecipeLayoutDrawable#getSlotUnderMouse} + ingredient types — same hit path as
     * JEI recipe screen overlays (without drawing JEI tooltips).
     */
    public record LayoutHover(int x0, int y0, int x1, int y1, ItemStack item, FluidStack fluid) {
        public LayoutHover {
            item = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy();
            fluid = fluid == null || fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
        }

        public boolean isEmpty() {
            return item.isEmpty() && (fluid == null || fluid.isEmpty());
        }
    }

    public static Optional<LayoutHover> layoutHoverUnderMouse(
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
            // Draw is always 1:1 at setPosition(left,top) — hover must match (ignore scale).
            drawable.setPosition(left, top);
            var hit = drawable.getSlotUnderMouse(mouseX, mouseY);
            if (hit.isEmpty()) {
                drawable.setPosition(0, 0);
                return Optional.empty();
            }
            var under = hit.get();
            Rect2i r = under.slot().getRect();
            var offset = under.offset();
            int sx = offset.x() + r.getX();
            int sy = offset.y() + r.getY();
            int sw = Math.max(1, r.getWidth());
            int sh = Math.max(1, r.getHeight());
            ItemStack item = drawable
                    .getIngredientUnderMouse(mouseX, mouseY, VanillaTypes.ITEM_STACK)
                    .orElse(ItemStack.EMPTY);
            FluidStack fluid = drawable
                    .getIngredientUnderMouse(mouseX, mouseY, NeoForgeTypes.FLUID_STACK)
                    .orElse(FluidStack.EMPTY);
            drawable.setPosition(0, 0);
            LayoutHover hover = new LayoutHover(sx, sy, sx + sw, sy + sh, item, fluid);
            return hover.isEmpty() ? Optional.empty() : Optional.of(hover);
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI layout hover skipped: {}", t.toString());
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
     * JEI native slot hover (semi-transparent white), without JEI tooltips.
     * Matches {@code RecipeLayout.drawOverlays} highlight path only.
     */
    @SuppressWarnings("removal") // JEI 19.34+: drawHoverOverlays deprecated; still what RecipeLayout.drawOverlays uses
    static void drawSlotHoverHighlight(
            GuiGraphics graphics,
            IRecipeLayoutDrawable<?> drawable,
            int jeiMouseX,
            int jeiMouseY
    ) {
        if (jeiMouseX < 0 || jeiMouseY < 0) {
            return;
        }
        try {
            var hit = drawable.getSlotUnderMouse(jeiMouseX, jeiMouseY);
            if (hit.isEmpty()) {
                return;
            }
            var result = hit.get();
            var pose = graphics.pose();
            pose.pushPose();
            var offset = result.offset();
            pose.translate(offset.x(), offset.y(), 0);
            result.slot().drawHoverOverlays(graphics);
            pose.popPose();
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI slot hover highlight skipped: {}", t.toString());
        }
    }

    /**
     * Item under mouse in a Pack AI–placed (possibly scaled) JEI layout.
     * Prefer {@link #layoutHoverUnderMouse} when fluid slots matter too.
     */
    public static Optional<ItemStack> itemUnderMouse(
            RecipeCard card,
            int left,
            int top,
            float scale,
            int mouseX,
            int mouseY
    ) {
        return layoutHoverUnderMouse(card, left, top, scale, mouseX, mouseY)
                .map(LayoutHover::item)
                .filter(stack -> stack != null && !stack.isEmpty());
    }

    /**
     * Screen mouse → JEI layout coords. Layout draw is always 1:1 at {@code setPosition(left,top)};
     * {@code scale} kept for API compat with callers that still pass shapedScale.
     */
    static int[] mapScreenMouseToJei(int left, int top, float scale, int mouseX, int mouseY) {
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
