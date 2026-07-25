package com.skps9.packai.client.jei;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.skps9.packai.client.tooltip.TooltipHover;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.Plainify;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Pick JEI lookup target: pin, rich same-id stack (hand/hover/name), else bare id.
 * Bare {@code ItemResolver.stackFromId} drops NBT — fatal for SlashBlade variants.
 */
public final class JeiTargetResolver {
    private static final Pattern NAME_BEFORE_ID = Pattern.compile(
            "「(.+?)」（\\s*([a-z0-9_]+:[a-z0-9_./-]+)\\s*）", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_BEFORE_ID_EN = Pattern.compile(
            "(?:What is\\s+)?(.+?)\\s*\\(\\s*([a-z0-9_]+:[a-z0-9_./-]+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static ItemStack pin = ItemStack.EMPTY;

    private JeiTargetResolver() {}

    /** Pin one item for the next {@link #resolve} (tooltip think). */
    public static void pin(ItemStack stack) {
        pin = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    public static void clearPin() {
        pin = ItemStack.EMPTY;
    }

    /** Pinned item for in-flight tooltip think (not yet consumed by AskService). */
    public static ItemStack pinnedOrEmpty() {
        return pin;
    }

    /** JEI hover, else container slot under mouse. */
    public static ItemStack hoveredItem(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack jei = jeiHoveredStack();
        if (!jei.isEmpty()) {
            return jei;
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> scr) {
            var slot = scr.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                return slot.getItem();
            }
        }
        ItemStack tip = TooltipHover.current();
        if (!tip.isEmpty()) {
            return tip;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack resolve(Minecraft mc, String question) {
        if (mc == null || mc.player == null) {
            return ItemStack.EMPTY;
        }
        if (!pin.isEmpty()) {
            return pin;
        }

        ItemStack held = mc.player.getMainHandItem();
        ItemStack hover = ItemStack.EMPTY;
        if (ModList.get().isLoaded("jei")) {
            hover = jeiHoveredStack();
        }
        if (hover.isEmpty()) {
            hover = hoveredItem(mc);
        }

        Optional<String> inQ = ItemResolver.idInQuestion(question);
        if (inQ.isPresent()) {
            String id = inQ.get();
            // Keep NBT: prefer live stacks with the same registry id.
            if (!held.isEmpty() && JeiFocusMatch.sameRegistryId(held, id)) {
                return held.copy();
            }
            if (!hover.isEmpty() && JeiFocusMatch.sameRegistryId(hover, id)) {
                return hover.copy();
            }
            String label = labelBesideId(question, id);
            if (!label.isBlank()) {
                ItemStack byName = SuggestIcons.resolve(id, label);
                if (!byName.isEmpty()) {
                    return byName;
                }
            }
            ItemStack bare = ItemResolver.stackFromId(id);
            if (!bare.isEmpty()) {
                return bare;
            }
        }

        if (!held.isEmpty()) {
            return held;
        }
        if (!hover.isEmpty()) {
            return hover;
        }
        return ItemStack.EMPTY;
    }

    /** Display name beside mod:id in ask templates (zh 「name」（id） / en name (id)). */
    static String labelBesideId(String question, String id) {
        if (question == null || id == null || id.isBlank()) {
            return "";
        }
        Matcher m = NAME_BEFORE_ID.matcher(question);
        while (m.find()) {
            if (id.equalsIgnoreCase(m.group(2))) {
                return Plainify.stripMcFormat(m.group(1)).trim();
            }
        }
        m = NAME_BEFORE_ID_EN.matcher(question);
        while (m.find()) {
            if (id.equalsIgnoreCase(m.group(2))) {
                return Plainify.stripMcFormat(m.group(1)).trim();
            }
        }
        return "";
    }

    private static ItemStack jeiHoveredStack() {
        try {
            Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
            if (opt.isEmpty()) {
                return ItemStack.EMPTY;
            }
            IJeiRuntime runtime = opt.get();
            ItemStack stack = stackFromOverlay(runtime.getIngredientListOverlay());
            if (!stack.isEmpty()) {
                return stack;
            }
            stack = stackFromRuntimeOverlay(runtime, "getBookmarkOverlay");
            if (!stack.isEmpty()) {
                return stack;
            }
            stack = stackFromRuntimeOverlay(runtime, "getRecipesGui");
            if (!stack.isEmpty()) {
                return stack;
            }
            return ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack stackFromRuntimeOverlay(IJeiRuntime runtime, String getter) {
        try {
            var method = runtime.getClass().getMethod(getter);
            return stackFromOverlay(method.invoke(runtime));
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack stackFromOverlay(Object overlay) {
        if (overlay == null) {
            return ItemStack.EMPTY;
        }
        try {
            var stack = overlay.getClass()
                    .getMethod("getIngredientUnderMouse", mezz.jei.api.ingredients.IIngredientType.class)
                    .invoke(overlay, VanillaTypes.ITEM_STACK);
            if (stack instanceof ItemStack item && !item.isEmpty()) {
                return item;
            }
        } catch (ReflectiveOperationException ignored) {
            // ponytail: overlay API mismatch — skip
        }
        return ItemStack.EMPTY;
    }
}
