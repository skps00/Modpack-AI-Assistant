package com.skps9.packai.config;

import java.util.Locale;
import java.util.Set;

import com.skps9.packai.logic.LlmClient;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config for in-mod AI (no external Bridge required).
 */
public final class PackAiConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> MODE;
    public static final ModConfigSpec.ConfigValue<String> API_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;
    public static final ModConfigSpec.ConfigValue<String> MODEL;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_MODEL;
    public static final ModConfigSpec.BooleanValue ALLOW_WEB_SEARCH;

    private static final Set<String> MODES = Set.of("auto", "cloud", "ollama", "offline");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("llm");
        MODE = b.comment(
                        "LLM backend: auto | cloud | ollama | offline.",
                        "auto = key→cloud else Ollama else local; cloud/ollama force that backend; offline = no LLM.")
                .define("mode", "auto");
        API_BASE_URL = b.comment("OpenAI-compatible API base (used in cloud / auto with key).")
                .define("apiBaseUrl", "https://api.openai.com/v1");
        API_KEY = b.comment(
                        "Cloud API key (sk-...). Prefer Mods → Packai settings screen (full paste);",
                        "or edit packai-client.toml / env PACKAI_API_KEY. Avoid NeoForge default string box.")
                .define("apiKey", "");
        MODEL = b.comment("Model id for cloud API.")
                .define("model", "gpt-4o-mini");
        OLLAMA_BASE_URL = b.comment("Local Ollama OpenAI-compatible base.")
                .define("ollamaBaseUrl", "http://127.0.0.1:11434/v1");
        OLLAMA_MODEL = b.comment("Ollama model name.")
                .define("ollamaModel", "llama3.2");
        b.pop();
        ALLOW_WEB_SEARCH = b.comment("Reserved; web search disabled in first in-mod build unless key later.")
                .define("allowWebSearch", false);
        SPEC = b.build();
    }

    /** Normalized mode: auto, cloud, ollama, or offline. */
    public static String resolvedMode() {
        String raw = MODE.get();
        if (raw == null || raw.isBlank()) {
            return "auto";
        }
        String m = raw.trim().toLowerCase(Locale.ROOT);
        return MODES.contains(m) ? m : "auto";
    }

    /** Persist mode from GUI; invalid values become auto. */
    public static void setMode(String mode) {
        String m = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        MODE.set(MODES.contains(m) ? m : "auto");
        SPEC.save();
    }

    /** Persist cloud model id from GUI. */
    public static void setCloudModel(String model) {
        String m = model == null ? "" : model.trim();
        if (!m.isEmpty()) {
            MODEL.set(m);
            SPEC.save();
        }
    }

    /** Persist Ollama model name from GUI. */
    public static void setOllamaModel(String model) {
        String m = model == null ? "" : model.trim();
        if (!m.isEmpty()) {
            OLLAMA_MODEL.set(m);
            SPEC.save();
        }
    }

    /**
     * Persist API key from in-game assistant box (max length friendly).
     * Empty string clears the config key.
     */
    public static void setApiKey(String key) {
        String cleaned = LlmClient.sanitizeApiKey(key);
        API_KEY.set(cleaned);
        SPEC.save();
    }

    private PackAiConfig() {}
}
