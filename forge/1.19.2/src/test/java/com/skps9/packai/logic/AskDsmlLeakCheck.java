package com.skps9.packai.logic;

import java.util.List;

import com.google.gson.JsonObject;
import com.skps9.packai.api.AskToolCall;

/**
 * NFWC 2026-09-14 leak: fullwidth double-pipe DSML in content, native tool_calls empty.
 * Run with -ea.
 */
public final class AskDsmlLeakCheck {
    private AskDsmlLeakCheck() {}

    /** Exact shape from latest.log (U+FF5C doubled; {@code calls} not {@code tool_calls}). */
    static final String NFWC_LEAK = ""
            + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C calls>\n"
            + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C invoke name=\"item_search\">\n"
            + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C parameter name=\"item\" string=\"true\">mrqx_extra_pack"
            + "</\uFF5C\uFF5CDSML\uFF5C\uFF5C parameter>\n"
            + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C parameter name=\"machine\" string=\"true\">mrqx_extra_pack"
            + "</\uFF5C\uFF5CDSML\uFF5C\uFF5C parameter>\n"
            + "</\uFF5C\uFF5CDSML\uFF5C\uFF5C invoke>\n"
            + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C invoke name=\"recipe_lookup\">\n"
            + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C parameter name=\"item\" string=\"true\">"
            + "mrqx_extra_pack:page_of_past</\uFF5C\uFF5CDSML\uFF5C\uFF5C parameter>\n"
            + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C parameter name=\"machine\" string=\"true\">合成台"
            + "</\uFF5C\uFF5CDSML\uFF5C\uFF5C parameter>\n"
            + "</\uFF5C\uFF5CDSML\uFF5C\uFF5C invoke>\n"
            + "</\uFF5C\uFF5CDSML\uFF5C\uFF5C calls>";

    public static void main(String[] args) {
        detectAndParseNfwcLeak();
        llmClientRecoversWhenNativeEmpty();
        System.out.println("AskDsmlLeakCheck OK");
    }

    static void detectAndParseNfwcLeak() {
        assert AskToolLoop.hasLeakedToolXml(NFWC_LEAK) : "must detect fullwidth DSML";
        assert AskToolLoop.hasEmbeddedToolDump(NFWC_LEAK);
        List<AskToolCall> parsed = AskToolLoop.parseEmbeddedToolCalls(NFWC_LEAK);
        assert parsed.size() >= 1 : parsed;
        AskToolCall search = null;
        for (AskToolCall c : parsed) {
            if ("item_search".equals(c.name())) {
                search = c;
                break;
            }
        }
        assert search != null : "expected item_search, got " + parsed;
        assert "mrqx_extra_pack".equals(search.itemId()) : search.itemId();
        System.out.println("detectAndParseNfwcLeak OK calls=" + parsed.size());
    }

    static void llmClientRecoversWhenNativeEmpty() {
        List<AskToolCall> recovered = LlmClient.recoverToolCalls(NFWC_LEAK, List.of());
        assert !recovered.isEmpty() : recovered;
        assert recovered.stream().anyMatch(c -> "item_search".equals(c.name())) : recovered;
        // Native non-empty must win (no overwrite).
        AskToolCall nativeCall = new AskToolCall("jei_lookup", "mod:x", "FULL", List.of());
        List<AskToolCall> kept = LlmClient.recoverToolCalls(NFWC_LEAK, List.of(nativeCall));
        assert kept.size() == 1 && "jei_lookup".equals(kept.get(0).name()) : kept;
        // Empty content / no dump → stay empty.
        assert LlmClient.recoverToolCalls("hello", List.of()).isEmpty();
        assert LlmClient.recoverToolCalls(NFWC_LEAK, null).stream()
                .anyMatch(c -> "item_search".equals(c.name()));
        // parseNative empty message
        JsonObject msg = new JsonObject();
        assert LlmClient.parseNativeToolCalls(msg).isEmpty();
        System.out.println("llmClientRecoversWhenNativeEmpty OK recovered=" + recovered.size());
    }
}
