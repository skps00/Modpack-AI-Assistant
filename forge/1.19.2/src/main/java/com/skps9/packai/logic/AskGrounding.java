package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounding: craft/obtain claims must match this focus + variant keys.
 * Other-variant recipes in FACT are not support. Max 1 extra lookup, new args only.
 */
public final class AskGrounding {
    private static final Pattern ITEM_MARKER = Pattern.compile("\\{\\{item:([^}]+)\\}\\}");

    private AskGrounding() {}

    public record Result(boolean grounded, String lookupTool, AskToolArgs lookupArgs) {
        public static Result ok() {
            return new Result(true, null, null);
        }

        public static Result keep() {
            return new Result(false, null, null);
        }

        public static Result lookup(String tool, AskToolArgs args) {
            return new Result(false, tool, args);
        }

        public boolean needsLookup() {
            return lookupTool != null && !lookupTool.isBlank();
        }
    }

    public static Result check(String reply, AskLoopState state) {
        if (state == null || state.intent() == AskLoopState.Intent.PURPOSE) {
            return Result.ok();
        }
        List<String> keys = state.variantKeys();
        String jei = state.jeiText() == null ? "" : state.jeiText();
        if (!keys.isEmpty() && !containsAny(jei, keys) && !state.jeiStationTemplate()) {
            // Other-variant dump ≠ support for this focus.
            AskToolArgs args = AskToolArgs.from(state, state.dumpLevel(), keys);
            String fp = AskToolLoop.fingerprint("jei_lookup", state.itemId(), state.dumpLevel(), keys);
            if (!state.alreadyRan(fp) && state.groundingLookups() < 1) {
                return Result.lookup("jei_lookup", args);
            }
            return Result.keep();
        }
        if (state.intent() == AskLoopState.Intent.CRAFT && state.craftEmpty()) {
            return Result.keep();
        }
        if (state.intent() == AskLoopState.Intent.OBTAIN && state.obtainEmpty()
                && AskLoopState.isEmptyOrMiss(state.guideText())
                && AskLoopState.isEmptyOrMiss(state.questText())) {
            return Result.keep();
        }
        if (reply == null || reply.isBlank()) {
            return Result.keep();
        }
        String fact = state.factBlob();
        String focus = state.itemId() == null ? "" : state.itemId().toLowerCase(Locale.ROOT);
        Matcher m = ITEM_MARKER.matcher(reply);
        while (m.find()) {
            String id = m.group(1).trim().toLowerCase(Locale.ROOT);
            int brace = id.indexOf('{');
            if (brace >= 0) {
                id = id.substring(0, brace);
            }
            if (id.isEmpty() || id.equals(focus)) {
                continue;
            }
            if (fact.toLowerCase(Locale.ROOT).contains(id)) {
                continue;
            }
            return Result.keep();
        }
        if (!keys.isEmpty() && !containsAny(reply, keys) && !containsAny(fact, keys)
                && !state.jeiStationTemplate()) {
            return Result.keep();
        }
        return Result.ok();
    }

    static boolean containsAny(String hay, List<String> needles) {
        if (hay == null || hay.isBlank() || needles == null || needles.isEmpty()) {
            return false;
        }
        String h = hay.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (n != null && !n.isBlank() && h.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
