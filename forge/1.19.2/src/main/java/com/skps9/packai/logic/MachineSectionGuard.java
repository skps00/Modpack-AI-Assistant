package com.skps9.packai.logic;

import java.util.List;
import java.util.Locale;

import com.skps9.packai.PackAiMod;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Drops {@code [Machine]} payload when it does not relate to the Ask focus item.
 */
public final class MachineSectionGuard {
    /** Test hook: when false, {@link #filterMachineSection} is a no-op (negative control). */
    static boolean GUARD_ENABLED = true;

    private MachineSectionGuard() {}

    public static String filterMachineSection(
            String machineSection,
            String focusItemId,
            String question,
            String recipeGetText,
            List<RecipeCard> cards
    ) {
        if (!GUARD_ENABLED) {
            return machineSection == null ? "" : machineSection;
        }
        if (machineSection == null || machineSection.isBlank()) {
            return "";
        }
        if (relatesToFocus(focusItemId, machineSection, question, recipeGetText, cards)) {
            return machineSection;
        }
        String machineName = primaryMachineLabel(machineSection);
        String focus = focusItemId == null ? "" : focusItemId.trim();
        PackAiMod.LOGGER.info(
                "Pack AI machine_section skip reason=unrelated machine={} focus={}",
                machineName,
                focus);
        return "";
    }

    static boolean relatesToFocus(
            String focusItemId,
            String machineSection,
            String question,
            String recipeGetText,
            List<RecipeCard> cards
    ) {
        if (machineSection == null || machineSection.isBlank()) {
            return true;
        }
        if (PackIndex.isMachineQuestion(question)) {
            return true;
        }
        String focus = focusItemId == null ? "" : focusItemId.trim().toLowerCase(Locale.ROOT);
        if (focus.isEmpty()) {
            return true;
        }
        String section = machineSection.toLowerCase(Locale.ROOT);
        if (sectionContainsFocus(section, focus)) {
            return true;
        }
        String label = primaryMachineLabel(machineSection).toLowerCase(Locale.ROOT);
        if (!label.isBlank() && machineLabelMatchesFocus(label, focus)) {
            return true;
        }
        if (cards != null) {
            for (RecipeCard card : cards) {
                if (card == null || card.isEmpty()) {
                    continue;
                }
                if (!cardContainsItemId(card, focus)) {
                    continue;
                }
                if (label.isBlank() || cardMentionsMachine(card, label)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sectionContainsFocus(String sectionLower, String focusLower) {
        if (sectionLower.contains(focusLower)) {
            return true;
        }
        String tail = pathTail(focusLower);
        if (tail.length() >= 4 && sectionLower.contains(tail)) {
            return true;
        }
        String spaced = tail.replace('_', ' ');
        return spaced.length() >= 4 && sectionLower.contains(spaced);
    }

    private static boolean machineLabelMatchesFocus(String labelLower, String focusLower) {
        String normLabel = alnumOnly(labelLower);
        String tail = pathTail(focusLower);
        String normPath = alnumOnly(tail);
        if (normPath.length() < 4 || normLabel.length() < 4) {
            return false;
        }
        return normLabel.contains(normPath) || normPath.contains(normLabel);
    }

    private static String primaryMachineLabel(String machineSection) {
        if (machineSection == null) {
            return "";
        }
        for (String line : machineSection.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.equals("[Machine]")
                    || t.equals("【機器】")
                    || t.equals("【机器】")
                    || t.startsWith("## ")) {
                continue;
            }
            if (t.regionMatches(true, 0, "JEI:", 0, 4)) {
                return t.substring(4).trim();
            }
            return t;
        }
        return "";
    }

    private static boolean cardContainsItemId(RecipeCard card, String focusLower) {
        return stackListContains(card.inputs(), focusLower)
                || stackListContains(card.outputs(), focusLower)
                || stackListContains(card.catalysts(), focusLower)
                || stackListContains(card.grid(), focusLower);
    }

    private static boolean cardMentionsMachine(RecipeCard card, String machineLabelLower) {
        String normMachine = alnumOnly(machineLabelLower);
        if (normMachine.length() < 4) {
            return false;
        }
        for (ItemStack stack : card.catalysts()) {
            if (stackNameMatches(stack, normMachine)) {
                return true;
            }
        }
        String cat = card.categoryTitle() == null ? "" : card.categoryTitle().toLowerCase(Locale.ROOT);
        return alnumOnly(cat).contains(normMachine) || normMachine.contains(alnumOnly(cat));
    }

    private static boolean stackListContains(List<ItemStack> stacks, String focusLower) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String id = stackId(stack);
            if (!id.isEmpty() && id.equals(focusLower)) {
                return true;
            }
        }
        return false;
    }

    private static boolean stackNameMatches(ItemStack stack, String normMachine) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String id = stackId(stack);
        if (!id.isEmpty() && alnumOnly(id).contains(normMachine)) {
            return true;
        }
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return alnumOnly(name).contains(normMachine);
    }

    private static String pathTail(String id) {
        int slash = id.lastIndexOf(':');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }

    private static String stackId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
    }

    private static String alnumOnly(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                b.append(Character.toLowerCase(c));
            }
        }
        return b.toString();
    }
}
