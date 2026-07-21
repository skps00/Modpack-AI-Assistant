package com.skps9.packai.client.chat;

import java.util.List;

/**
 * One chat turn for Pack AI (user or assistant).
 *
 * @param heldItemLabel     display name of main-hand item when the user asked (user only)
 * @param heldItemId        registry id for icon (user only)
 * @param suggestedItemIds  AI-recommended items (assistant only)
 */
public record ChatMessage(
        Role role,
        String text,
        String heldItemLabel,
        String heldItemId,
        List<String> suggestedItemIds
) {
    public enum Role {
        USER,
        ASSISTANT
    }

    public static ChatMessage user(String text) {
        return user(text, "", "");
    }

    public static ChatMessage user(String text, String heldItemLabel, String heldItemId) {
        return new ChatMessage(
                Role.USER,
                text == null ? "" : text,
                heldItemLabel == null ? "" : heldItemLabel,
                heldItemId == null ? "" : heldItemId,
                List.of());
    }

    public static ChatMessage assistant(String text) {
        return assistant(text, List.of());
    }

    public static ChatMessage assistant(String text, List<String> suggestedItemIds) {
        return new ChatMessage(
                Role.ASSISTANT,
                text == null ? "" : text,
                "",
                "",
                suggestedItemIds == null || suggestedItemIds.isEmpty()
                        ? List.of()
                        : List.copyOf(suggestedItemIds));
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
}
