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

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * Draws JEI category background + extras (flame / clock / arrows) via official layout drawable.
 * <p>
 * Scaled cards: render JEI at 1:1 into an offscreen {@link TextureTarget}, then blit scaled into
 * the Pack AI card so {@code drawRecipe(mouse)} can paint native slot highlights. Mouse mapping for
 * tooltips stays in {@link #itemUnderMouse}.
 * <p>
 * ponytail: one reused FBO; fall back to pose-scale + {@code -1,-1} if FBO fails.
 * Ceiling: FBO is logical GUI pixels (not guiScale×); large layouts allocate bigger textures;
 * GL scissor is saved/restored around the offscreen pass.
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
                if (!drawScaledViaFbo(graphics, drawable, card, left, top, scale, mouseX, mouseY)) {
                    drawScaledPoseFallback(graphics, drawable, left, top, scale);
                }
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
     * Maps screen mouse into JEI hit-test space — required when scaled (FBO or pose fallback).
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
            drawable.drawRecipe(fboGraphics, jx, jy);
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
            float scale
    ) {
        drawable.setPosition(0, 0);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1.0f);
        // ponytail: scaled pose ≠ JEI hit-test space — native highlight disabled
        drawable.drawRecipe(graphics, -1, -1);
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
