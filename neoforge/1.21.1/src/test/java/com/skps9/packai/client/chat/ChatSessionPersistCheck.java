package com.skps9.packai.client.chat;

import java.util.List;

import com.skps9.packai.logic.QuestGuide;

/** Runnable check: session keeps quests / busy across “screen close”. */
public final class ChatSessionPersistCheck {
    private ChatSessionPersistCheck() {}

    public static void main(String[] args) {
        ChatSession.clear();
        assert !ChatSession.isBusy();
        assert ChatSession.lastQuests().isEmpty();

        ChatSession.setBusy(true);
        ChatSession.setLastQuests(List.of(new QuestGuide.Hit(
                "ch", "Title", "d", "src", List.of(), 0, false, "ABCDEF0123456789", "ftbquests")));
        assert ChatSession.isBusy();
        assert ChatSession.lastQuests().size() == 1;
        assert "ABCDEF0123456789".equalsIgnoreCase(ChatSession.lastQuests().get(0).questId());

        ChatSession.clear();
        assert !ChatSession.isBusy();
        assert ChatSession.lastQuests().isEmpty();
        System.out.println("ChatSessionPersistCheck OK");
    }
}
