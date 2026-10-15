package com.skps9.packai.client.chat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.skps9.packai.logic.AskLoopState;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.AskToolLoop;
import com.skps9.packai.logic.AskToolOfferGate;
import com.skps9.packai.logic.ClarifyArgs;
import com.skps9.packai.logic.ClarifyBody;
import com.skps9.packai.logic.TokenUsage;

/**
 * Headless clarify harness (§4.2). Package {@code client.chat} so
 * {@link ChatSession#resetForCheck()} is reachable. Run with {@code -ea}.
 */
public final class ClarifyAskCheck {
    private ClarifyAskCheck() {}

    public static void main(String[] args) {
        ChatSession.resetForCheck();

        // --- ClarifyArgs.record: raw guess/candidates → pending ---
        AskLoopState loop = AskLoopState.start("哪部反應爐？", "", List.of(), System.currentTimeMillis() + 60_000L);
        loop.setLang("zh_tw");
        String ok = ClarifyArgs.record(loop,
                "{\"guess\":\"mekanismgenerators:fission_reactor_casing\","
                        + "\"candidates\":[\"mekanismgenerators:fission_reactor_casing\","
                        + "\"biggerreactors:reactor_terminal\"],\"why\":\"ambiguous reactor\"}");
        assert ClarifyArgs.TOOL_OK.equals(ok) : ok;
        assert loop.pendingClarify() != null : "pending null after OK";
        assert loop.pendingClarify().hasMaterial() : loop.pendingClarify();
        assert loop.pendingClarify().guess().contains("fission_reactor") : loop.pendingClarify();
        assert loop.pendingClarify().candidates().size() == 2 : loop.pendingClarify();

        // empty args → MISS, pending stays if already set — use fresh loop
        AskLoopState emptyLoop = AskLoopState.start("?", "", List.of(), System.currentTimeMillis() + 60_000L);
        assert ClarifyArgs.TOOL_MISS.equals(ClarifyArgs.record(emptyLoop, "")) : "empty miss";
        assert emptyLoop.pendingClarify() == null;
        assert ClarifyArgs.TOOL_MISS.equals(ClarifyArgs.record(emptyLoop, "{}")) : "empty object miss";
        assert ClarifyArgs.TOOL_MISS.equals(ClarifyArgs.record(emptyLoop, "not-json")) : "bad json miss";

        // already asked once → LIMIT; pending stays null on fresh loop
        ChatSession.markClarifyAsked();
        AskLoopState limited = AskLoopState.start("again", "", List.of(), System.currentTimeMillis() + 60_000L);
        String lim = ClarifyArgs.record(limited,
                "{\"guess\":\"minecraft:diamond\",\"candidates\":[],\"why\":\"x\"}");
        assert ClarifyArgs.TOOL_LIMIT.equals(lim) : lim;
        assert limited.pendingClarify() == null : "limit must not set pending";
        assert limited.clarifyRefused() : "refused flag";

        ChatSession.resetForCheck();
        assert !ChatSession.clarifyAsked() : "resetForCheck clears clarify";
        AskLoopState afterReset = AskLoopState.start("ok", "", List.of(), System.currentTimeMillis() + 60_000L);
        assert ClarifyArgs.TOOL_OK.equals(ClarifyArgs.record(afterReset, "{\"guess\":\"x\"}")) : "after reset";
        assert afterReset.pendingClarify() != null;

        // --- ClarifyBody ---
        Map<String, String> names = Map.of(
                "mekanismgenerators:fission_reactor_casing", "Fission Reactor Casing",
                "biggerreactors:reactor_terminal", "Reactor Terminal");
        afterReset.setPendingClarify(new ClarifyArgs.PendingClarify(
                "mekanismgenerators:fission_reactor_casing",
                List.of(),
                "why"));
        afterReset.setLang("en_us");
        String confirm = ClarifyBody.build(afterReset, names);
        assert confirm.toLowerCase().contains("fission") || confirm.contains("Fission") : confirm;
        assert !confirm.contains("[Sources]") : confirm;
        assert !confirm.contains("<!--packai:items=") : confirm;
        assert !confirm.contains("→") : confirm;

        afterReset.setPendingClarify(new ClarifyArgs.PendingClarify(
                "",
                List.of(
                        "mekanismgenerators:fission_reactor_casing",
                        "biggerreactors:reactor_terminal",
                        "extra:one",
                        "extra:two",
                        "extra:three"),
                ""));
        String which = ClarifyBody.build(afterReset, names);
        assert which.contains("1)") && which.contains("2)") : which;
        assert which.contains("4)") : which;
        assert !which.contains("5)") : "candidates capped at 4: " + which;
        assert which.contains(" — ") : which;

        String unclear = ClarifyBody.buildFallback(afterReset, Map.of());
        assert unclear != null && !unclear.isBlank() : unclear;
        assert !unclear.contains("[Sources]") : unclear;

        String withMark = ClarifyBody.appendUncertain("some miss", "en_us");
        assert withMark.contains("some miss") : withMark;
        assert withMark.length() > "some miss".length() : withMark;

        // --- offer gate: ask_clarify not dropped ---
        AskToolOfferGate gate = new AskToolOfferGate();
        Set<String> offered = gate.offer(
                "which reactor?",
                "minecraft:stone",
                AskToolOfferGate.fixed(null, null, null),
                AskToolLoop.CAPABLE_TOOLS);
        assert offered.contains("ask_clarify") : offered;
        assert AskToolLoop.CAPABLE_TOOLS.contains("ask_clarify");
        assert AskToolLoop.RAW_ARGS_TOOLS.contains("ask_clarify");
        assert AskToolLoop.CAPABLE_TOOLS.size() == 16 : AskToolLoop.CAPABLE_TOOLS.size();

        // --- AskResult.clarify ---
        AskResult cr = AskResult.clarify("Did you mean X?", TokenUsage.NONE);
        assert AskResult.DISPLAY_SRC_CLARIFY.equals(cr.displaySrc()) : cr.displaySrc();
        assert cr.suggestedItemIds().isEmpty() : cr.suggestedItemIds();
        TokenUsage u15 = new TokenUsage(10, 5, 15, -1, -1);
        AskResult withUsage = AskResult.clarify("Y", u15).withTokenUsage(u15);
        assert withUsage.tokenUsage().totalTokens() == 15 : withUsage.tokenUsage();

        // --- preserveRawArgsJson ---
        String raw = "{\"guess\":\"a\",\"candidates\":[\"b\"]}";
        assert raw.equals(ClarifyArgs.preserveRawArgsJson(raw)) : ClarifyArgs.preserveRawArgsJson(raw);
        assert "".equals(ClarifyArgs.preserveRawArgsJson("[]")) : "array rejected";
        assert "".equals(ClarifyArgs.preserveRawArgsJson("")) : "blank";
        String huge = "{\"guess\":\"" + "x".repeat(3000) + "\"}";
        assert "".equals(ClarifyArgs.preserveRawArgsJson(huge)) : "oversized";

        // --- canonicalizeCall keeps raw for ask_clarify ---
        var call = AskToolLoop.canonicalizeCall(
                "ask_clarify", "", "", "", List.of(), "c1", raw);
        assert call != null : "canonicalize null";
        assert raw.equals(call.argumentsJson()) : call.argumentsJson();
        var dropped = AskToolLoop.canonicalizeCall(
                "ask_clarify", "", "", "", List.of(), "c2", "not-json");
        assert dropped != null && "".equals(dropped.argumentsJson()) : dropped;

        ChatSession.resetForCheck();
        System.out.println("ClarifyAskCheck OK");
    }
}
