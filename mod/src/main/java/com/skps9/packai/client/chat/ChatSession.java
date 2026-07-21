package com.skps9.packai.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.skps9.packai.logic.QuestGuide;

/**
 * In-memory chat session (survives closing the assistant screen; cleared on logout / clear).
 */
public final class ChatSession {
    private static final int MAX_MESSAGES = 40;
    private static final int DEFAULT_LLM_TURNS = 8;

    private static final List<ChatMessage> MESSAGES = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean busy;
    private static List<QuestGuide.Hit> lastQuests = List.of();
    private static volatile LastAsk lastAsk;

    /** Parameters for the most recent ask (regenerate). */
    public record LastAsk(
            String question,
            boolean includeHotbar,
            boolean questOverride,
            String templateKey,
            String templateArg0,
            String templateArg1
    ) {
        public LastAsk(String question, boolean includeHotbar, boolean questOverride) {
            this(question, includeHotbar, questOverride, null, null, null);
        }

        public boolean hasTemplate() {
            return templateKey != null && !templateKey.isBlank();
        }
    }

    /** Prior history + last user turn for regenerate. */
    public record RegenerateRequest(
            String question,
            boolean includeHotbar,
            boolean questOverride,
            List<ChatMessage> prior,
            String templateKey,
            String templateArg0,
            String templateArg1
    ) {
        public boolean hasTemplate() {
            return templateKey != null && !templateKey.isBlank();
        }
    }

    private ChatSession() {}

    public static List<ChatMessage> snapshot() {
        synchronized (MESSAGES) {
            return List.copyOf(MESSAGES);
        }
    }

    public static void append(ChatMessage message) {
        if (message == null) {
            return;
        }
        synchronized (MESSAGES) {
            MESSAGES.add(message);
            while (MESSAGES.size() > MAX_MESSAGES) {
                MESSAGES.remove(0);
            }
        }
    }

    public static void replaceLastAssistant(String text, List<String> suggestedItemIds) {
        synchronized (MESSAGES) {
            if (!MESSAGES.isEmpty() && MESSAGES.get(MESSAGES.size() - 1).role() == ChatMessage.Role.ASSISTANT) {
                MESSAGES.set(MESSAGES.size() - 1, ChatMessage.assistant(text, suggestedItemIds));
            } else {
                MESSAGES.add(ChatMessage.assistant(text, suggestedItemIds));
            }
            while (MESSAGES.size() > MAX_MESSAGES) {
                MESSAGES.remove(0);
            }
        }
    }

    public static void clear() {
        synchronized (MESSAGES) {
            MESSAGES.clear();
        }
        busy = false;
        lastQuests = List.of();
        lastAsk = null;
    }

    public static boolean isEmpty() {
        synchronized (MESSAGES) {
            return MESSAGES.isEmpty();
        }
    }

    public static boolean isBusy() {
        return busy;
    }

    public static void setBusy(boolean value) {
        busy = value;
    }

    public static void setLastAsk(LastAsk ask) {
        lastAsk = ask;
    }

    public static Optional<LastAsk> lastAsk() {
        return Optional.ofNullable(lastAsk);
    }

    public static boolean canRegenerate() {
        if (busy || lastAsk == null) {
            return false;
        }
        synchronized (MESSAGES) {
            if (MESSAGES.isEmpty()) {
                return false;
            }
            int n = MESSAGES.size();
            ChatMessage last = MESSAGES.get(n - 1);
            if (last.role() == ChatMessage.Role.ASSISTANT) {
                return n >= 2 && MESSAGES.get(n - 2).role() == ChatMessage.Role.USER;
            }
            return last.role() == ChatMessage.Role.USER;
        }
    }

    /**
     * Drop trailing assistant (if any) and return params to re-run the last question.
     */
    public static Optional<RegenerateRequest> prepareRegenerate() {
        if (busy || lastAsk == null) {
            return Optional.empty();
        }
        synchronized (MESSAGES) {
            if (MESSAGES.isEmpty()) {
                return Optional.empty();
            }
            if (MESSAGES.get(MESSAGES.size() - 1).role() == ChatMessage.Role.ASSISTANT) {
                MESSAGES.remove(MESSAGES.size() - 1);
            }
            if (MESSAGES.isEmpty() || MESSAGES.get(MESSAGES.size() - 1).role() != ChatMessage.Role.USER) {
                return Optional.empty();
            }
            List<ChatMessage> prior = List.copyOf(MESSAGES.subList(0, MESSAGES.size() - 1));
            LastAsk la = lastAsk;
            return Optional.of(new RegenerateRequest(
                    la.question(),
                    la.includeHotbar(),
                    la.questOverride(),
                    prior,
                    la.templateKey(),
                    la.templateArg0(),
                    la.templateArg1()));
        }
    }

    /** Update the trailing user message text (e.g. after language change on regenerate). */
    public static void replaceLastUserText(String text) {
        synchronized (MESSAGES) {
            if (MESSAGES.isEmpty()) {
                return;
            }
            int i = MESSAGES.size() - 1;
            ChatMessage last = MESSAGES.get(i);
            if (last.role() != ChatMessage.Role.USER) {
                return;
            }
            MESSAGES.set(i, ChatMessage.user(text, last.heldItemLabel(), last.heldItemId()));
        }
    }

    public static List<QuestGuide.Hit> lastQuests() {
        return lastQuests;
    }

    public static void setLastQuests(List<QuestGuide.Hit> quests) {
        lastQuests = quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests);
    }

    public static List<ChatMessage> recentForLlm(int maxMessages) {
        int n = maxMessages <= 0 ? DEFAULT_LLM_TURNS : maxMessages;
        synchronized (MESSAGES) {
            if (MESSAGES.isEmpty()) {
                return List.of();
            }
            List<ChatMessage> copy = new ArrayList<>(MESSAGES);
            int from = Math.max(0, copy.size() - n);
            return List.copyOf(copy.subList(from, copy.size()));
        }
    }

    public static List<ChatMessage> recentForLlm() {
        return recentForLlm(DEFAULT_LLM_TURNS);
    }
}
