package com.skps9.packai.client.chat;

import java.util.List;

import com.skps9.packai.logic.RecipeCard;

import net.minecraft.world.item.ItemStack;

/**
 * One chat turn for Pack AI (user or assistant).
 *
 * @param heldItemLabel     display name of context item when the user asked (user only)
 * @param heldItemId        registry id (user only)
 * @param heldIcon          full stack copy for correct mod icon/NBT render (user only)
 * @param suggestedItemIds  AI-recommended items (assistant only)
 * @param recipeCards       JEI mini recipe cards (assistant only)
 */
public record ChatMessage(
        Role role,
        String text,
        String heldItemLabel,
        String heldItemId,
        ItemStack heldIcon,
        List<String> suggestedItemIds,
        List<RecipeCard> recipeCards
) {
    public enum Role {
        USER,
        ASSISTANT
    }

    public static ChatMessage user(String text) {
        return user(text, "", "", ItemStack.EMPTY);
    }

    public static ChatMessage user(String text, String heldItemLabel, String heldItemId) {
        return user(text, heldItemLabel, heldItemId, ItemStack.EMPTY);
    }

    public static ChatMessage user(String text, String heldItemLabel, String heldItemId, ItemStack heldIcon) {
        return new ChatMessage(
                Role.USER,
                text == null ? "" : text,
                heldItemLabel == null ? "" : heldItemLabel,
                heldItemId == null ? "" : heldItemId,
                copyIcon(heldIcon),
                List.of(),
                List.of());
    }

    public static ChatMessage assistant(String text) {
        return assistant(text, List.of(), List.of());
    }

    public static ChatMessage assistant(String text, List<String> suggestedItemIds) {
        return assistant(text, suggestedItemIds, List.of());
    }

    public static ChatMessage assistant(
            String text,
            List<String> suggestedItemIds,
            List<RecipeCard> recipeCards
    ) {
        return new ChatMessage(
                Role.ASSISTANT,
                text == null ? "" : text,
                "",
                "",
                ItemStack.EMPTY,
                suggestedItemIds == null || suggestedItemIds.isEmpty()
                        ? List.of()
                        : List.copyOf(suggestedItemIds),
                recipeCards == null || recipeCards.isEmpty()
                        ? List.of()
                        : List.copyOf(recipeCards));
    }

    public boolean isUser() {
        return role == Role.USER;
    }

    public String apiRole() {
        return isUser() ? "user" : "assistant";
    }

    public boolean hasHeldItem() {
        return heldItemLabel != null && !heldItemLabel.isBlank();
    }

    public boolean hasSuggestedItems() {
        return suggestedItemIds != null && !suggestedItemIds.isEmpty();
    }

    public boolean hasRecipeCards() {
        return recipeCards != null && !recipeCards.isEmpty();
    }

    /** Icon stack for UI (never null). */
    public ItemStack iconOrEmpty() {
        return heldIcon == null || heldIcon.isEmpty() ? ItemStack.EMPTY : heldIcon;
    }

    private static ItemStack copyIcon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return stack.copy();
    }
}
