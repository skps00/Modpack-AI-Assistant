package com.skps9.packai.client.chat;

import java.util.List;

import com.skps9.packai.logic.QuestGuide;

/** Runnable check: sticky quest merge across Asks; session reset without JEI/MC. */
public final class ChatSessionPersistCheck {
    private ChatSessionPersistCheck() {}

    private static QuestGuide.Hit hit(String id, String title) {
        return new QuestGuide.Hit(
                "ch", title, "d", "src", List.of(), 0, false, id, "ftbquests", false);
    }

    public static void main(String[] args) {
        ChatSession.resetForCheck();
        assert !ChatSession.isBusy();
        assert ChatSession.lastQuests().isEmpty();

        ChatSession.setBusy(true);
        ChatSession.setLastQuests(List.of(hit("ABCDEF0123456789", "Title")));
        assert ChatSession.isBusy();
        assert ChatSession.lastQuests().size() == 1;
        assert "ABCDEF0123456789".equalsIgnoreCase(ChatSession.lastQuests().get(0).questId());

        // Empty reply must not wipe sticky slots (same as non-quest Ask finish).
        ChatSession.setLastQuests(List.of());
        assert ChatSession.lastQuests().size() == 1;

        // Newer Ask prepends; unique by questId; cap MAX_QUEST_SLOTS.
        ChatSession.setLastQuests(List.of(hit("1111111111111111", "B"), hit("2222222222222222", "C")));
        assert ChatSession.lastQuests().size() == 3;
        assert "1111111111111111".equalsIgnoreCase(ChatSession.lastQuests().get(0).questId());
        assert "2222222222222222".equalsIgnoreCase(ChatSession.lastQuests().get(1).questId());
        assert "ABCDEF0123456789".equalsIgnoreCase(ChatSession.lastQuests().get(2).questId());

        ChatSession.setLastQuests(List.of(hit("3333333333333333", "D")));
        assert ChatSession.lastQuests().size() == ChatSession.MAX_QUEST_SLOTS;
        assert "3333333333333333".equalsIgnoreCase(ChatSession.lastQuests().get(0).questId());
        assert "1111111111111111".equalsIgnoreCase(ChatSession.lastQuests().get(1).questId());
        assert "2222222222222222".equalsIgnoreCase(ChatSession.lastQuests().get(2).questId());

        // Duplicate id moves to front via incoming order, no double slot.
        ChatSession.setLastQuests(List.of(hit("1111111111111111", "B2")));
        assert ChatSession.lastQuests().size() == ChatSession.MAX_QUEST_SLOTS;
        assert "1111111111111111".equalsIgnoreCase(ChatSession.lastQuests().get(0).questId());
        assert "B2".equals(ChatSession.lastQuests().get(0).title());

        ChatSession.resetForCheck();
        assert !ChatSession.isBusy();
        assert ChatSession.lastQuests().isEmpty();
        System.out.println("ChatSessionPersistCheck OK");
    }
}
