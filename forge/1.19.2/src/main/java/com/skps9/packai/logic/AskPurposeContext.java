package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * Splits item <em>purpose</em> (what it does / how you use it) from JEI-U
 * <em>as ingredient</em> (recipes that consume it). Guide-book facts use {@link #GUIDE_HEADER}.
 */
public final class AskPurposeContext {
    public static final String PURPOSE_HEADER = "[PURPOSE]";
    public static final String GUIDE_HEADER = "[GUIDE]";
    public static final String AS_INGREDIENT_HEADER = "[AS_INGREDIENT]";

    /** Cap listed tool actions so odd mod registries cannot flood the prompt. */
    static final int MAX_TOOL_ACTIONS = 16;

    private AskPurposeContext() {}

    /** Graph edges that describe function / interaction — not craft-input lists. */
    public static boolean isPurposeGraphFact(String gf) {
        if (gf == null || gf.isBlank()) {
            return false;
        }
        return gf.contains("-[desc]->")
                || gf.contains("-[score]->")
                || gf.contains("-[triggers]->")
                || gf.contains("-[on:")
                || gf.contains("-[right_click]->")
                || gf.contains("-[right_click_use]->")
                || gf.contains("-[right_click_as_block]->");
    }

    /**
     * Build LLM purpose block. Empty string when nothing useful.
     *
     * @param tooltip      full focus-item tooltip (may be multi-line)
     * @param purposeLines already-humanized interact / desc lines
     */
    public static String buildPurposeBlock(String tooltip, List<String> purposeLines) {
        return buildPurposeBlock(tooltip, purposeLines, null);
    }

    /**
     * @param guideFacts bare Patchouli／guide text (no header), or already starts with {@link #GUIDE_HEADER}
     */
    public static String buildPurposeBlock(String tooltip, List<String> purposeLines, String guideFacts) {
        List<String> body = new ArrayList<>();
        if (tooltip != null && !tooltip.isBlank()) {
            body.add(tooltip.trim());
        }
        if (purposeLines != null) {
            for (String line : purposeLines) {
                if (line != null && !line.isBlank()) {
                    body.add(line.trim());
                }
            }
        }
        StringBuilder out = new StringBuilder();
        if (!body.isEmpty()) {
            out.append(PURPOSE_HEADER).append('\n').append(String.join("\n", body));
        }
        String guide = normalizeGuide(guideFacts);
        if (!guide.isEmpty()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(guide);
        }
        return out.toString();
    }

    /** Merge tooltip + fuel／tool-action lines for {@code user.purpose} tooltip slot. */
    public static String withItemBehavior(String tooltip, List<String> behaviorLines) {
        List<String> parts = new ArrayList<>();
        if (tooltip != null && !tooltip.isBlank()) {
            parts.add(tooltip.trim());
        }
        if (behaviorLines != null) {
            for (String line : behaviorLines) {
                if (line != null && !line.isBlank()) {
                    parts.add(line.trim());
                }
            }
        }
        return String.join("\n", parts);
    }

    /**
     * Live Forge facts: furnace burn time + ToolActions the stack can perform.
     * Soft-fails to empty list if APIs throw or stack empty.
     */
    public static List<String> itemBehaviorLines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(2);
        try {
            int burn = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
            if (burn > 0) {
                out.add(formatFuelLine(burn));
            }
        } catch (Throwable ignored) {
            // soft-fail: missing remaps / exotic stacks
        }
        try {
            // Ensure stock ToolActions are registered before iterating the map.
            ToolActions.AXE_DIG.name();
            List<String> names = new ArrayList<>();
            for (ToolAction action : ToolAction.getActions()) {
                if (action == null) {
                    continue;
                }
                if (stack.canPerformAction(action)) {
                    names.add(action.name());
                }
            }
            String line = formatToolActionsLine(names);
            if (!line.isEmpty()) {
                out.add(line);
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        return out;
    }

    /** {@code Furnace fuel: 1600 ticks (~80s)} — empty when {@code burnTicks <= 0}. */
    public static String formatFuelLine(int burnTicks) {
        if (burnTicks <= 0) {
            return "";
        }
        int seconds = burnTicks / 20;
        return "Furnace fuel: " + burnTicks + " ticks (~" + seconds + "s)";
    }

    /** {@code Tool actions: axe_dig, shovel_dig} — empty when no names. */
    public static String formatToolActionsLine(List<String> actionNames) {
        if (actionNames == null || actionNames.isEmpty()) {
            return "";
        }
        List<String> sorted = new ArrayList<>();
        for (String n : actionNames) {
            if (n != null && !n.isBlank()) {
                sorted.add(n.trim());
            }
        }
        if (sorted.isEmpty()) {
            return "";
        }
        Collections.sort(sorted);
        boolean truncated = sorted.size() > MAX_TOOL_ACTIONS;
        if (truncated) {
            sorted = new ArrayList<>(sorted.subList(0, MAX_TOOL_ACTIONS));
        }
        String joined = String.join(", ", sorted);
        if (truncated) {
            joined = joined + ", …";
        }
        return "Tool actions: " + joined;
    }

    private static String normalizeGuide(String guideFacts) {
        if (guideFacts == null || guideFacts.isBlank()) {
            return "";
        }
        String g = guideFacts.trim();
        if (g.startsWith(GUIDE_HEADER)) {
            return g;
        }
        return GUIDE_HEADER + "\n" + g;
    }
}
