package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.skps9.packai.logic.FormatRequirements;
import com.skps9.packai.logic.Plainify;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.gui.widgets.IScrollBoxWidget;
import mezz.jei.api.gui.widgets.IScrollGridWidget;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.gui.widgets.ITextWidget;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/**
 * Harvest JEI-visible non-slot notes (XP / cook time / stress / …).
 * JEI 19: prefer {@code createRecipeExtras} text widgets; also capture
 * {@link GuiGraphics} draws via mixin while {@link #capturing()}.
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

    public static void offerDrawnText(String raw) {
        List<String> sink = CAPTURE.get();
        if (sink == null) {
            return;
        }
        acceptList(sink, raw);
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
                // empty ok
            }
        }
        LinkedHashSet<String> notes = new LinkedHashSet<>();

        // JEI 19 text widgets (smelting XP / time live here).
        try {
            TextCaptureExtras extras = new TextCaptureExtras(notes);
            IRecipeCategory raw = (IRecipeCategory) category;
            try {
                raw.createRecipeExtras(extras, recipe, slots, EmptyFocus.INSTANCE);
            } catch (AbstractMethodError | NoSuchMethodError ignored) {
                raw.createRecipeExtras(extras, recipe, EmptyFocus.INSTANCE);
            }
        } catch (Throwable ignored) {
            // optional
        }

        // Legacy draw() path — Font/GuiGraphics mixin captures while active.
        List<String> drawSink = new ArrayList<>();
        CAPTURE.set(drawSink);
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                ((IRecipeCategory) category).draw(recipe, slots, graphics, -1.0D, -1.0D);
            }
        } catch (Throwable ignored) {
            // GL / ctor — leave draw path empty
        } finally {
            CAPTURE.remove();
        }
        for (String s : drawSink) {
            acceptSet(notes, s);
        }

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
                            acceptSet(notes, tip.getString());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // optional
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

    private static void acceptList(List<String> sink, String raw) {
        String cleaned = clean(raw);
        if (cleaned.isEmpty()) {
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

    private static void acceptSet(LinkedHashSet<String> sink, String raw) {
        String cleaned = clean(raw);
        if (cleaned.isEmpty() || sink.size() >= MAX) {
            return;
        }
        sink.add(cleaned);
    }

    private static String clean(String raw) {
        String cleaned = Plainify.stripMcFormat(raw == null ? "" : raw).trim();
        if (cleaned.isEmpty() || cleaned.length() > 96 || cleaned.length() <= 1) {
            return "";
        }
        if (FormatRequirements.isIngredientGateNoise(cleaned)) {
            return "";
        }
        return cleaned;
    }

    /** Minimal extras builder — only {@code addText} matters for notes. */
    private static final class TextCaptureExtras implements IRecipeExtrasBuilder {
        private final LinkedHashSet<String> notes;

        TextCaptureExtras(LinkedHashSet<String> notes) {
            this.notes = notes;
        }

        @Override
        public IRecipeSlotDrawablesView getRecipeSlots() {
            return () -> List.of();
        }

        @Override
        public void addDrawable(IDrawable drawable, int x, int y) {}

        @Override
        public IPlaceable<?> addDrawable(IDrawable drawable) {
            return NoopPlaceable.INSTANCE;
        }

        @Override
        public void addWidget(IRecipeWidget widget) {}

        @Override
        public void addSlottedWidget(ISlottedRecipeWidget widget, List<IRecipeSlotDrawable> slots) {}

        @Override
        public void addInputHandler(IJeiInputHandler inputHandler) {}

        @Override
        public void addGuiEventListener(IJeiGuiEventListener listener) {}

        @Override
        public IScrollBoxWidget addScrollBoxWidget(int width, int height, int x, int y) {
            return null;
        }

        @Override
        public IScrollGridWidget addScrollGridWidget(List<IRecipeSlotDrawable> slots, int columns, int visibleRows) {
            return null;
        }

        @Override
        public IPlaceable<?> addRecipeArrow() {
            return NoopPlaceable.INSTANCE;
        }

        @Override
        public IPlaceable<?> addRecipePlusSign() {
            return NoopPlaceable.INSTANCE;
        }

        @Override
        public IPlaceable<?> addAnimatedRecipeArrow(int ticksPerCycle) {
            return NoopPlaceable.INSTANCE;
        }

        @Override
        public IPlaceable<?> addAnimatedRecipeFlame(int ticksPerCycle) {
            return NoopPlaceable.INSTANCE;
        }

        @Override
        public ITextWidget addText(List<FormattedText> text, int maxWidth, int maxHeight) {
            if (text != null) {
                for (FormattedText t : text) {
                    if (t != null) {
                        acceptSet(this.notes, t.getString());
                    }
                }
            }
            return NoopTextWidget.INSTANCE;
        }
    }

    private static final class NoopPlaceable implements IPlaceable<NoopPlaceable> {
        static final NoopPlaceable INSTANCE = new NoopPlaceable();

        @Override
        public NoopPlaceable setPosition(int x, int y) {
            return this;
        }

        @Override
        public int getWidth() {
            return 0;
        }

        @Override
        public int getHeight() {
            return 0;
        }
    }

    private static final class NoopTextWidget implements ITextWidget {
        static final NoopTextWidget INSTANCE = new NoopTextWidget();

        @Override
        public ITextWidget setFont(net.minecraft.client.gui.Font font) {
            return this;
        }

        @Override
        public ITextWidget setColor(int color) {
            return this;
        }

        @Override
        public ITextWidget setLineSpacing(int spacing) {
            return this;
        }

        @Override
        public ITextWidget setShadow(boolean shadow) {
            return this;
        }

        @Override
        public ITextWidget setTextAlignment(mezz.jei.api.gui.placement.HorizontalAlignment alignment) {
            return this;
        }

        @Override
        public ITextWidget setTextAlignment(mezz.jei.api.gui.placement.VerticalAlignment alignment) {
            return this;
        }

        @Override
        public ITextWidget setPosition(int x, int y) {
            return this;
        }

        @Override
        public int getWidth() {
            return 0;
        }

        @Override
        public int getHeight() {
            return 0;
        }
    }

    private static final class EmptyFocus implements IFocusGroup {
        static final EmptyFocus INSTANCE = new EmptyFocus();

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public List<mezz.jei.api.recipe.IFocus<?>> getAllFocuses() {
            return List.of();
        }

        @Override
        public java.util.stream.Stream<mezz.jei.api.recipe.IFocus<?>> getFocuses(RecipeIngredientRole role) {
            return java.util.stream.Stream.empty();
        }

        @Override
        public <T> java.util.stream.Stream<mezz.jei.api.recipe.IFocus<T>> getFocuses(
                mezz.jei.api.ingredients.IIngredientType<T> ingredientType
        ) {
            return java.util.stream.Stream.empty();
        }

        @Override
        public <T> java.util.stream.Stream<mezz.jei.api.recipe.IFocus<T>> getFocuses(
                mezz.jei.api.ingredients.IIngredientType<T> ingredientType,
                RecipeIngredientRole role
        ) {
            return java.util.stream.Stream.empty();
        }
    }
}
