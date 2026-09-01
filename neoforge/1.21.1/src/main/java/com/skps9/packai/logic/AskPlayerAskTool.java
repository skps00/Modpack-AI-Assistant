package com.skps9.packai.logic;

import java.util.List;

/**
 * Capable-loop {@code ask_player} tool. v1 sentinel only; UI wiring is Phase 3.
 *
 * <p>Returns {@code [ASK_PLAYER] question=...|options=...} so the model (or a later
 * bridge) can surface uncertainty to the player instead of inventing.
 */
public final class AskPlayerAskTool implements AskTool {
    @Override
    public String name() {
        return "ask_player";
    }

    @Override
    public String run(AskToolArgs args) {
        String question = "";
        List<String> options = List.of();
        if (args != null) {
            question = args.question == null ? "" : args.question.trim();
            // ponytail: AskToolArgs has no options field yet — reuse variantKeys as the string list.
            if (args.variantKeys != null && !args.variantKeys.isEmpty()) {
                options = args.variantKeys;
            }
        }
        String opts = options.isEmpty() ? "" : String.join(",", options);
        return "[ASK_PLAYER] question=" + question + "|options=" + opts;
    }
}