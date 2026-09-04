package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.api.AskToolCall;

/**
 * One chat/completions HTTP result. {@code protocolProbe} is HTTP 400 while native
 * {@code tools} were sent — caller must not increment {@code MAX_LLM_ROUNDS}.
 */
public record LlmRound(
        int httpStatus,
        String content,
        List<AskToolCall> toolCalls,
        boolean protocolProbe,
        String reasoningContent
) {
    public LlmRound(int httpStatus, String content, List<AskToolCall> toolCalls, boolean protocolProbe) {
        this(httpStatus, content, toolCalls, protocolProbe, "");
    }

    public LlmRound {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        reasoningContent = reasoningContent == null ? "" : reasoningContent;
    }

    public static LlmRound of(int httpStatus, String content) {
        return new LlmRound(httpStatus, content, List.of(), false);
    }

    public boolean ok() {
        return httpStatus >= 200 && httpStatus < 400;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
