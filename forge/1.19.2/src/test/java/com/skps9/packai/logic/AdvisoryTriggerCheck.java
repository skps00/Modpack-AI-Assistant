package com.skps9.packai.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * No-Minecraft harness for S-A2 {@link PackIndex#isAdvisoryQuestion}.
 * Reads {@code docs/plans/2026-10-11-sa2-trigger-cases.json} (argv[0] or env
 * {@code PACKAI_ADVISORY_CASES}).
 */
public final class AdvisoryTriggerCheck {
    private static final Path DEFAULT_CASES = Path.of(
            "docs/plans/2026-10-11-sa2-trigger-cases.json");

    private AdvisoryTriggerCheck() {}

    public static void main(String[] args) throws Exception {
        Path casesPath = resolveCases(args);
        if (!Files.isRegularFile(casesPath)) {
            System.err.println("FAIL missing cases: " + casesPath.toAbsolutePath());
            System.exit(1);
        }
        String text = Files.readString(casesPath, StandardCharsets.UTF_8);
        JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
        int pass = 0;
        int fail = 0;
        int n = 0;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String question = o.has("question") ? o.get("question").getAsString() : "";
            boolean expected = o.has("expected") && o.get("expected").getAsBoolean();
            n++;
            PackIndex.AdvisoryTrigger hit = PackIndex.matchAdvisoryQuestion(question);
            boolean actual = hit.matched();
            if (actual == expected) {
                pass++;
            } else {
                fail++;
                System.out.println(
                        "FAIL question=" + question
                                + " expected=" + expected
                                + " actual=" + actual
                                + " matchedTerm=" + hit.matchedTerm());
            }
        }
        System.out.println("trigger_cases=" + n + " pass=" + pass + " fail=" + fail);
        if (fail > 0 || n < 20) {
            if (n < 20) {
                System.err.println("FAIL need >=20 cases, got " + n);
            }
            System.exit(1);
        }
    }

    private static Path resolveCases(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        String env = System.getenv("PACKAI_ADVISORY_CASES");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        // Prefer repo-root relative; also try cwd=forge/1.19.2 → ../..
        Path a = DEFAULT_CASES;
        if (Files.isRegularFile(a)) {
            return a;
        }
        Path b = Path.of("..").resolve("..").resolve(DEFAULT_CASES).normalize();
        if (Files.isRegularFile(b)) {
            return b;
        }
        return a;
    }
}
