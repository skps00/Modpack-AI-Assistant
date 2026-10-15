package com.skps9.packai.logic;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

/**
 * LLM exit for ambiguous questions. Does not produce player text — queues
 * {@link AskLoopState#pendingClarify()} for {@link AskEngine} to turn into a
 * lang-key body. Non-empty replies so offer-gate {@code noRetry} never trips.
 *
 * <p>DSML / {@code [[tools]]} paths pass empty {@code argumentsJson} →
 * {@link ClarifyArgs#TOOL_MISS} only (native tools are the normal path).
 */
public final class ClarifyAskTool implements AskTool {
    static final String MSG_OK = "[CLARIFY] queued — output nothing further this turn";
    static final String MSG_LIMIT =
            "[CLARIFY_LIMIT] already asked once — answer with your best guess and mark it uncertain";
    static final String MSG_MISS = "[CLARIFY_MISS] need guess or candidates";

    @Override
    public String name() {
        return "ask_clarify";
    }

    @Override
    public String description() {
        return "Ask the player to clarify when the question is ambiguous "
                + "(which item/mod, or what they want). "
                + "Pass guess and/or candidates (≤4). why is for trace only. "
                + "After a successful call, output nothing further this turn.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"guess\":{\"type\":\"string\"},"
                + "\"candidates\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"why\":{\"type\":\"string\"}"
                + "},\"required\":[],\"additionalProperties\":false}";
    }

    @Override
    public String llmDescription() {
        return "Call when unsure which item/mod or what the player wants. "
                + "guess=best guess (optional); candidates=up to 4 options (optional); "
                + "why=short reason for trace. Need guess or candidates. "
                + "On success, output nothing else this turn. "
                + "If already asked once this chat, do not call again — answer with best guess + uncertain.";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        AskLoopState loop = env == null ? null : env.loop;
        String raw = args == null ? "" : args.argumentsJson;
        String code = ClarifyArgs.record(loop, raw);
        return switch (code) {
            case ClarifyArgs.TOOL_OK -> MSG_OK;
            case ClarifyArgs.TOOL_LIMIT -> MSG_LIMIT;
            default -> MSG_MISS;
        };
    }
}
