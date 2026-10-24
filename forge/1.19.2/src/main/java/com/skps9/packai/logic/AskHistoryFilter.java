package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.client.chat.ChatMessage;

/**
 * Drops assistant turns that must not be replayed into the next LLM request
 * (miss retry notices, body-unavailable stubs, etc.). UI chat keeps them.
 */
public final class AskHistoryFilter {
    private static final String[] LANGS = {"en_us", "zh_cn", "zh_tw"};

    private AskHistoryFilter() {}

    public static List<ChatMessage> forLlm(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> out = new ArrayList<>(history.size());
        for (ChatMessage msg : history) {
            if (msg != null && !excludeFromLlm(msg)) {
                out.add(msg);
            }
        }
        return List.copyOf(out);
    }

    public static boolean excludeFromLlm(ChatMessage msg) {
        if (msg == null) {
            return true;
        }
        if (msg.role() != ChatMessage.Role.ASSISTANT) {
            return false;
        }
        if (msg.synthetic()) {
            return true;
        }
        return isFallbackAssistantText(msg.text());
    }

    /**
     * Lang-key fallback when older sessions lack {@link ChatMessage#synthetic()}.
     */
    public static boolean isFallbackAssistantText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        for (String lang : LANGS) {
            String bodyUnavailable = ReplyLang.askBodyUnavailable(lang).trim();
            if (!bodyUnavailable.isEmpty() && t.contains(bodyUnavailable)) {
                return true;
            }
            String missPrefix = missRetryPrefix(lang);
            if (!missPrefix.isEmpty() && (t.startsWith(missPrefix) || t.contains(missPrefix))) {
                return true;
            }
            for (String reasonKey : List.of("llm_empty", "llm_error", "jei_empty", "model_miss")) {
                String reason = ReplyLang.askMissReason(lang, reasonKey);
                if (!reason.isEmpty() && t.contains(reason)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String missRetryPrefix(String lang) {
        String template = ReplyLang.tr(lang, "packai.reply.ask_miss_retry");
        if (template == null || template.isBlank() || template.equals("packai.reply.ask_miss_retry")) {
            return "";
        }
        int slot = template.indexOf("%s");
        if (slot < 0) {
            return template.trim();
        }
        return template.substring(0, slot).trim();
    }
}
