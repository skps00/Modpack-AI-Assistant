package com.skps9.packai.logic;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * One follow-up chat message for the native-tools loop: assistant {@code tool_calls}
 * or {@code role:tool} result. Not a UI {@link com.skps9.packai.client.chat.ChatMessage}.
 */
public record ToolChatTurn(
        String role,
        String content,
        String toolCallId,
        List<AskToolCall> assistantCalls,
        String reasoningContent
) {
    public ToolChatTurn {
        role = role == null ? "" : role;
        content = content == null ? "" : content;
        toolCallId = toolCallId == null ? "" : toolCallId;
        assistantCalls = assistantCalls == null ? List.of() : List.copyOf(assistantCalls);
        reasoningContent = reasoningContent == null ? "" : reasoningContent;
    }

    public static ToolChatTurn assistant(String content, List<AskToolCall> calls) {
        return assistant(content, calls, "");
    }

    public static ToolChatTurn assistant(String content, List<AskToolCall> calls, String reasoningContent) {
        return new ToolChatTurn("assistant", content, "", calls, reasoningContent);
    }

    public static ToolChatTurn tool(String toolCallId, String content) {
        return new ToolChatTurn("tool", content, toolCallId, List.of(), "");
    }

    public JsonObject toMessageJson() {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        if ("tool".equals(role)) {
            o.addProperty("tool_call_id", toolCallId.isBlank() ? "call_unknown" : toolCallId);
            o.addProperty("content", content);
            return o;
        }
        o.addProperty("content", content);
        if (!reasoningContent.isBlank()) {
            o.addProperty("reasoning_content", reasoningContent);
        }
        JsonArray calls = new JsonArray();
        int i = 0;
        for (AskToolCall c : assistantCalls) {
            if (c == null) {
                continue;
            }
            JsonObject call = new JsonObject();
            String id = c.toolCallId().isBlank() ? ("call_" + c.name() + "_" + i) : c.toolCallId();
            call.addProperty("id", id);
            call.addProperty("type", "function");
            JsonObject fn = new JsonObject();
            fn.addProperty("name", c.name());
            fn.addProperty("arguments", c.argumentsJson().isBlank() ? "{}" : c.argumentsJson());
            call.add("function", fn);
            calls.add(call);
            i++;
        }
        if (!calls.isEmpty()) {
            o.add("tool_calls", calls);
        }
        return o;
    }
}
