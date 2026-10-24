package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.client.chat.ChatMessage;

/** LLM history must drop miss-retry / synthetic assistant turns. Run with -ea. */
public final class AskHistoryFilterCheck {
    private AskHistoryFilterCheck() {}

    public static void main(String[] args) {
        keepsNormalAssistant();
        dropsSyntheticFlag();
        dropsFallbackTextThreeLangs();
        negativeControlUnfilteredLarger();
        printSendHistoryBeforeAfter();
        System.out.println("AskHistoryFilterCheck OK");
    }

    private static void keepsNormalAssistant() {
        List<ChatMessage> in = List.of(
                ChatMessage.harnessAssistant("Use iron and sticks at a crafting table.", false));
        List<ChatMessage> out = AskHistoryFilter.forLlm(in);
        assert out.size() == 1 : out;
        System.out.println("keepsNormalAssistant OK");
    }

    private static void dropsSyntheticFlag() {
        List<ChatMessage> in = List.of(
                ChatMessage.harnessAssistant("notice", true));
        assert AskHistoryFilter.forLlm(in).isEmpty() : AskHistoryFilter.forLlm(in);
        System.out.println("dropsSyntheticFlag OK");
    }

    private static void dropsFallbackTextThreeLangs() {
        for (String lang : List.of("en_us", "zh_cn", "zh_tw")) {
            String reason = ReplyLang.askMissReason(lang, "llm_empty");
            String body = ReplyLang.askMissRetry(lang, reason);
            List<ChatMessage> in = List.of(ChatMessage.harnessAssistant(body, false));
            List<ChatMessage> out = AskHistoryFilter.forLlm(in);
            assert out.isEmpty() : lang + " " + out;
        }
        System.out.println("dropsFallbackTextThreeLangs OK");
    }

    private static void negativeControlUnfilteredLarger() {
        String body = ReplyLang.askMissRetry("en_us", ReplyLang.askMissReason("en_us", "llm_empty"));
        List<ChatMessage> in = new ArrayList<>();
        in.add(ChatMessage.harnessAssistant(body, false));
        in.add(ChatMessage.harnessAssistant("Real answer with enough detail to not be a miss.", false));
        int raw = in.size();
        int filtered = AskHistoryFilter.forLlm(in).size();
        assert raw > filtered : "negative control: filter must drop rows raw=" + raw + " filtered=" + filtered;
        if (raw == filtered) {
            throw new AssertionError("negative control FAILED: no filtering applied");
        }
        System.out.println("negativeControlUnfilteredLarger OK raw=" + raw + " filtered=" + filtered);
    }

    private static void printSendHistoryBeforeAfter() {
        String miss = ReplyLang.askMissRetry(
                "en_us", ReplyLang.askMissReason("en_us", "llm_empty"));
        List<ChatMessage> in = List.of(
                ChatMessage.harnessAssistant("Prior good answer about crafting.", false),
                ChatMessage.harnessAssistant(miss, false),
                ChatMessage.harnessAssistant("Follow-up answer after retry.", false));
        System.out.println("send.history BEFORE turns=" + in.size() + " " + historyJson(in));
        List<ChatMessage> out = AskHistoryFilter.forLlm(in);
        System.out.println("send.history AFTER  turns=" + out.size() + " " + historyJson(out));
    }

    private static String historyJson(List<ChatMessage> msgs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < msgs.size(); i++) {
            ChatMessage m = msgs.get(i);
            if (i > 0) {
                sb.append(',');
            }
            String text = m.text() == null ? "" : m.text().replace("\n", "\\n");
            if (text.length() > 120) {
                text = text.substring(0, 120) + "…";
            }
            sb.append("{\"role\":\"").append(m.apiRole()).append("\",\"content\":\"").append(text).append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }
}
