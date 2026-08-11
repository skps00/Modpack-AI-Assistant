package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.logic.FormatRequirements;
import com.skps9.packai.logic.Plainify;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;

/**
 * Harvest JEI-visible non-slot notes (XP / cook time / stress / …) drawn by the category.
 * Font draws are captured via {@link com.skps9.packai.mixin.FontDrawCaptureMixin} while
 * {@link #capturing()} is true. Empty on plain crafting — OK (failure mode #1A).
 */
public final class JeiReqNotes {
    private static final int MAX = 8;
    private static final ThreadLocal<List<String>> CAPTURE = new ThreadLocal<>();

    private static final IRecipeSlotsView EMPTY_SLOTS = new IRecipeSlotsView() {
        @Override
        public List<IRecipeSlotView> getSlotViews() {
            return List.of();
        }
    };

    private JeiReqNotes() {}

    public static boolean capturing() {
        return CAPTURE.get() != null;
    }

    /** Called from Font mixin while harvest is active. */
    public static void offerDrawnText(String raw) {
        List<String> sink = CAPTURE.get();
        if (sink == null) {
            return;
        }
        accept(sink, raw);
    }

    public static void offerDrawnText(Component component) {
        if (component == null) {
            return;
        }
        offerDrawnText(component.getString());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<String> harvest(
            IRecipeCategory<?> category,
            Object recipe,
            Object jeiLayoutDrawable
    ) {
        if (category == null || recipe == null) {
            return List.of();
        }
        IRecipeSlotsView slots = EMPTY_SLOTS;
        if (jeiLayoutDrawable instanceof IRecipeLayoutDrawable drawable) {
            try {
                IRecipeSlotsView view = drawable.getRecipeSlotsView();
                if (view != null) {
                    slots = view;
                }
            } catch (Throwable ignored) {
                // empty slots ok
            }
        }
        LinkedHashSet<String> notes = new LinkedHashSet<>();
        List<String> drawSink = new ArrayList<>();
        CAPTURE.set(drawSink);
        try {
            ((IRecipeCategory) category).draw(recipe, slots, new PoseStack(), -1.0D, -1.0D);
        } catch (Throwable ignored) {
            // JEI API gap / GL — leave empty
        } finally {
            CAPTURE.remove();
        }
        for (String s : drawSink) {
            accept(notes, s);
        }
        // Hover tooltips that some categories only expose via getTooltipStrings.
        try {
            int w = Math.max(16, category.getWidth());
            int h = Math.max(16, category.getHeight());
            for (int gy = 0; gy < 3; gy++) {
                for (int gx = 0; gx < 3; gx++) {
                    double mx = (gx + 0.5D) * w / 3.0D;
                    double my = (gy + 0.5D) * h / 3.0D;
                    List<Component> tips = ((IRecipeCategory) category).getTooltipStrings(recipe, slots, mx, my);
                    if (tips == null) {
                        continue;
                    }
                    for (Component tip : tips) {
                        if (tip != null) {
                            accept(notes, tip.getString());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // optional path
        }
        if (notes.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(notes);
        if (out.size() > MAX) {
            return List.copyOf(out.subList(0, MAX));
        }
        return List.copyOf(out);
    }

    private static void accept(List<String> sink, String raw) {
        String cleaned = Plainify.stripMcFormat(raw == null ? "" : raw).trim();
        if (cleaned.isEmpty() || cleaned.length() > 96) {
            return;
        }
        if (FormatRequirements.isIngredientGateNoise(cleaned)) {
            return;
        }
        // Skip single-glyph / arrow decorations.
        if (cleaned.length() <= 1) {
            return;
        }
        for (String existing : sink) {
            if (existing.equalsIgnoreCase(cleaned)) {
                return;
            }
        }
        if (sink.size() >= MAX) {
            return;
        }
        sink.add(cleaned);
    }

    private static void accept(LinkedHashSet<String> sink, String raw) {
        String cleaned = Plainify.stripMcFormat(raw == null ? "" : raw).trim();
        if (cleaned.isEmpty() || cleaned.length() > 96 || cleaned.length() <= 1) {
            return;
        }
        if (FormatRequirements.isIngredientGateNoise(cleaned)) {
            return;
        }
        if (sink.size() >= MAX) {
            return;
        }
        sink.add(cleaned);
    }
}
