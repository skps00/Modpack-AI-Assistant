package com.skps9.packai.client.jei;

import java.nio.IntBuffer;
import java.util.Optional;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.RecipeCard;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Rect2i;
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
 * Scaled cards: 1:1 into offscreen {@link TextureTarget} (incl. hover), then blit — except when the
 * layout has fluids (pose fallback): some fluid blits ignore FBO bind when scissor is off.
 */
public final class JeiLayoutDraw {
    /**
     * Room past {@link IRecipeLayoutDrawable#getRect()} for category.draw decorations
     * (clock / flame) that paint slightly outside the reported rect.
     * ponytail: pad heuristic, not measured ink bounds — FBO/measure if still clipped.
     */
    public static final int OUTSIDE_DRAW_PAD = 14;

    /** Cap FBO edge so a huge Create grid cannot allocate a 4k texture per frame resize. */
    private static final int MAX_FBO_EDGE = 512;

    private static TextureTarget layoutFbo;
    private static int fboW;
    private static int fboH;

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
            // ponytail: FBO disables chat scissor; some fluid blits then paint slot-local XY onto
            // the main FB (orphan corner). Pose keeps scissor + FluidTankRenderer under PoseStack.
            // Do NOT overlay Pack AI drawFluidSlot — JEI drawRecipe owns tank look.
            boolean skipFbo = card.hasPlacedFluids()
                    || (card.fluidInputs() != null && !card.fluidInputs().isEmpty())
                    || (card.fluidOutputs() != null && !card.fluidOutputs().isEmpty());
            if (scale < 0.999f) {
                if (skipFbo || !drawScaledViaFbo(graphics, drawable, card, left, top, scale, mouseX, mouseY)) {
                    drawScaledPoseFallback(graphics, drawable, left, top, scale, mouseX, mouseY);
                }
            } else {
                drawable.setPosition(left, top);
                // drawRecipe = body only (slots + FluidTankRenderer); highlight separate
                drawable.drawRecipe(graphics, mouseX, mouseY);
                drawSlotHoverHighlight(graphics, drawable, mouseX, mouseY);
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
            int[] jeiMouse = mapScreenMouseToJei(left, top, scale, mouseX, mouseY);
            if (scale < 0.999f) {
                drawable.setPosition(0, 0);
            } else {
                drawable.setPosition(left, top);
            }
            var hit = drawable.getSlotUnderMouse(jeiMouse[0], jeiMouse[1]);
            if (hit.isEmpty()) {
                drawable.setPosition(0, 0);
                return Optional.empty();
            }
            var under = hit.get();
            Rect2i r = under.slot().getRect();
            var offset = under.offset();
            int jeiX = offset.x() + r.getX();
            int jeiY = offset.y() + r.getY();
            int sx;
            int sy;
            int sw;
            int sh;
            if (scale < 0.999f) {
                float s = Math.max(0.001f, scale);
                sx = left + Math.round(jeiX * s);
                sy = top + Math.round(jeiY * s);
                sw = Math.max(1, Math.round(r.getWidth() * s));
                sh = Math.max(1, Math.round(r.getHeight() * s));
            } else {
                sx = jeiX;
                sy = jeiY;
                sw = Math.max(1, r.getWidth());
                sh = Math.max(1, r.getHeight());
            }
            ItemStack item = drawable
                    .getIngredientUnderMouse(jeiMouse[0], jeiMouse[1], VanillaTypes.ITEM_STACK)
                    .orElse(ItemStack.EMPTY);
            FluidStack fluid = drawable
                    .getIngredientUnderMouse(jeiMouse[0], jeiMouse[1], NeoForgeTypes.FLUID_STACK)
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
     * Screen mouse → coords for {@link IRecipeLayoutDrawable#getItemStackUnderMouse} / FBO draw.
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

    /**
     * Native-scale JEI → FBO → scaled blit. Returns false to trigger pose fallback.
     */
    private static boolean drawScaledViaFbo(
            GuiGraphics graphics,
            IRecipeLayoutDrawable<?> drawable,
            RecipeCard card,
            int left,
            int top,
            float scale,
            int mouseX,
            int mouseY
    ) {
        int nativeW = Math.min(MAX_FBO_EDGE, layoutFitWidth(card));
        int nativeH = Math.min(MAX_FBO_EDGE, layoutFitHeight(card));
        if (nativeW <= 0 || nativeH <= 0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }
        try {
            graphics.flush();

            int[] scissor = saveScissor();
            RenderSystem.disableScissor();

            TextureTarget fbo = ensureFbo(nativeW, nativeH);
            fbo.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            fbo.clear(Minecraft.ON_OSX);
            fbo.bindWrite(true);

            RenderSystem.backupProjectionMatrix();
            Matrix4f ortho = new Matrix4f().setOrtho(0.0F, nativeW, nativeH, 0.0F, 1000.0F, 3000.0F);
            RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);

            var modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.identity();
            modelView.translate(0.0F, 0.0F, -2000.0F);
            RenderSystem.applyModelViewMatrix();

            GuiGraphics fboGraphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            drawable.setPosition(0, 0);
            int[] jeiMouse = mapScreenMouseToJei(left, top, scale, mouseX, mouseY);
            // Clamp mouse into FBO so off-card hover does not confuse JEI overlays.
            int jx = jeiMouse[0];
            int jy = jeiMouse[1];
            if (jx < 0 || jy < 0 || jx >= nativeW || jy >= nativeH) {
                jx = -1;
                jy = -1;
            }
            // Body first; hover highlight baked into FBO so blit scales it with the card.
            drawable.drawRecipe(fboGraphics, jx, jy);
            drawSlotHoverHighlight(fboGraphics, drawable, jx, jy);
            fboGraphics.flush();

            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();

            mc.getMainRenderTarget().bindWrite(true);
            restoreScissor(scissor);

            int destW = Math.max(1, Math.round(nativeW * scale));
            int destH = Math.max(1, Math.round(nativeH * scale));
            blitFbo(graphics, fbo, left, top, destW, destH);
            return true;
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI layout FBO draw failed, pose fallback: {}", t.toString());
            try {
                Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            } catch (Throwable ignored) {
                // ignore
            }
            try {
                RenderSystem.restoreProjectionMatrix();
            } catch (Throwable ignored) {
                // ignore — may not have been backed up
            }
            return false;
        }
    }

    private static void drawScaledPoseFallback(
            GuiGraphics graphics,
            IRecipeLayoutDrawable<?> drawable,
            int left,
            int top,
            float scale,
            int mouseX,
            int mouseY
    ) {
        drawable.setPosition(0, 0);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1.0f);
        // Body at -1,-1; hover uses mapped mouse in unscaled layout space under this pose.
        drawable.drawRecipe(graphics, -1, -1);
        int[] jeiMouse = mapScreenMouseToJei(left, top, scale, mouseX, mouseY);
        drawSlotHoverHighlight(graphics, drawable, jeiMouse[0], jeiMouse[1]);
        pose.popPose();
    }

    private static TextureTarget ensureFbo(int w, int h) {
        if (layoutFbo == null || fboW != w || fboH != h) {
            if (layoutFbo != null) {
                layoutFbo.destroyBuffers();
            }
            layoutFbo = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            fboW = w;
            fboH = h;
        }
        return layoutFbo;
    }

    private static void blitFbo(GuiGraphics graphics, RenderTarget fbo, int x, int y, int w, int h) {
        graphics.flush();
        RenderSystem.assertOnRenderThread();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, fbo.getColorTextureId());

        Matrix4f matrix = graphics.pose().last().pose();
        // Mojang: width/height = allocated tex; viewWidth/viewHeight = requested. FBO Y is flipped.
        float u1 = fbo.width == 0 ? 1.0F : (float) fbo.viewWidth / (float) fbo.width;
        float v1 = fbo.height == 0 ? 1.0F : (float) fbo.viewHeight / (float) fbo.height;

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x, y + h, 0.0F).setUv(0.0F, 0.0F);
        buffer.addVertex(matrix, x + w, y + h, 0.0F).setUv(u1, 0.0F);
        buffer.addVertex(matrix, x + w, y, 0.0F).setUv(u1, v1);
        buffer.addVertex(matrix, x, y, 0.0F).setUv(0.0F, v1);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** @return null if scissor off; else [x,y,w,h] window pixels */
    private static int[] saveScissor() {
        if (!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            return null;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer box = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
            return new int[]{box.get(0), box.get(1), box.get(2), box.get(3)};
        }
    }

    private static void restoreScissor(int[] box) {
        if (box == null) {
            return;
        }
        RenderSystem.enableScissor(box[0], box[1], box[2], box[3]);
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
