package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.api.AskToolCall;
import com.skps9.packai.logic.AskToolLoop.LlmBridge;

/**
 * {@code ask_clarify} success must end the capable tool loop (no second
 * {@link LlmBridge#completeWithTools}). Run with {@code -ea}.
 */
public final class ClarifyLoopStopCheck {
    private ClarifyLoopStopCheck() {}

    public static void main(String[] args) {
        clarifyStopsAfterNativeToolCall();
        negativeControlWithoutClarifyStop();
        System.out.println("ClarifyLoopStopCheck OK");
    }

    private static void clarifyStopsAfterNativeToolCall() {
        AskToolLoop.testLastClarifyStopHops = -1;
        AskToolLoop loop = AskToolLoop.INSTANCE;

        AskLoopState state = AskLoopState.start(
                "which reactor?", "", List.of(), System.currentTimeMillis() + 60_000L);
        state.setIntent(AskLoopState.Intent.CRAFT);
        state.noteShot0("jei_lookup", "OUTPUT", List.of(), "recipe context");

        String clarifyArgs =
                "{\"guess\":\"mekanismgenerators:fission_reactor_casing\","
                        + "\"candidates\":[\"biggerreactors:reactor_terminal\"],"
                        + "\"why\":\"Which reactor do you mean?\"}";

        loop.replaceAll(List.of(clarifyTool(state)));

        FakeLlm llm = new FakeLlm();
        llm.completeQueue = List.of(
                new LlmRound(
                        200,
                        "",
                        List.of(new AskToolCall(
                                "ask_clarify", "", "", List.of(), "call_clarify", clarifyArgs)),
                        false),
                new LlmRound(200, "", List.of(), false));

        loop.firstAsk(state, llm);

        assert llm.completes == 1 : "expected one LLM round, got " + llm.completes;
        assert state.pendingClarify() != null : "pendingClarify must be set";
        assert state.pendingClarify().hasMaterial() : state.pendingClarify();
        assert AskToolLoop.testLastClarifyStopHops >= 0 : "clarify_stop log path must run";
        System.out.println("clarifyStopsAfterNativeToolCall OK");
    }

    private static void negativeControlWithoutClarifyStop() {
        AskToolLoop.testSkipClarifyStop = true;
        try {
            AskToolLoop.testLastClarifyStopHops = -1;
            AskToolLoop loop = AskToolLoop.INSTANCE;

            AskLoopState state = AskLoopState.start(
                    "which reactor?", "", List.of(), System.currentTimeMillis() + 60_000L);
            state.setIntent(AskLoopState.Intent.CRAFT);
            state.noteShot0("jei_lookup", "OUTPUT", List.of(), "recipe context");

            String clarifyArgs =
                    "{\"guess\":\"minecraft:diamond\",\"candidates\":[],\"why\":\"Which one?\"}";

            loop.replaceAll(List.of(clarifyTool(state)));

            FakeLlm llm = new FakeLlm();
            llm.completeQueue = List.of(
                    new LlmRound(
                            200,
                            "",
                            List.of(new AskToolCall(
                                    "ask_clarify", "", "", List.of(), "call_clarify", clarifyArgs)),
                            false),
                    new LlmRound(200, "", List.of(), false));

            loop.firstAsk(state, llm);

            if (llm.completes != 2) {
                throw new AssertionError(
                        "negative control FAILED: without clarify_stop, expected 2 completeWithTools,"
                                + " got "
                                + llm.completes);
            }
            System.out.println("negativeControlWithoutClarifyStop OK");
        } finally {
            AskToolLoop.testSkipClarifyStop = false;
        }
    }

    private static AskTool clarifyTool(AskLoopState state) {
        return new AskTool() {
            @Override
            public String name() {
                return "ask_clarify";
            }

            @Override
            public String run(AskToolArgs args) {
                String raw = args == null ? "" : args.argumentsJson;
                ClarifyArgs.record(state, raw);
                return ClarifyAskTool.MSG_OK;
            }

            @Override
            public String description() {
                return "test clarify";
            }

            @Override
            public String argsSchemaJson() {
                return "{}";
            }
        };
    }

    private static final class FakeLlm implements LlmBridge {
        int completes;
        List<LlmRound> completeQueue = List.of();
        int completeIdx;

        @Override
        public String askNoTools() {
            return "";
        }

        @Override
        public LlmRound completeWithTools(List<String> toolNames) {
            completes++;
            if (completeIdx < completeQueue.size()) {
                return completeQueue.get(completeIdx++);
            }
            return new LlmRound(200, "", List.of(), false);
        }

        @Override
        public void rememberNoNativeTools() {}

        @Override
        public boolean noNativeTools() {
            return false;
        }
    }
}
