package com.skps9.packai.logic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.config.PackAiConfig;

/** Routes to cloud / Ollama / none based on llm.mode. */
public final class LlmClient {
    private static final Gson GSON = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public String ask(
            String question,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            List<String> focusMods,
            List<String> graphFacts,
            List<String> sources,
            String policy,
            boolean questOverride,
            boolean questConflict
    ) {
        return ask(question, heldItem, hotbarItems, focusMods, graphFacts, sources, policy,
                questOverride, questConflict, null, List.of(), null);
    }

    public String ask(
            String question,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            List<String> focusMods,
            List<String> graphFacts,
            List<String> sources,
            String policy,
            boolean questOverride,
            boolean questConflict,
            String jeiFacts
    ) {
        return ask(question, heldItem, hotbarItems, focusMods, graphFacts, sources, policy,
                questOverride, questConflict, jeiFacts, List.of(), null);
    }

    public String ask(
            String question,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            List<String> focusMods,
            List<String> graphFacts,
            List<String> sources,
            String policy,
            boolean questOverride,
            boolean questConflict,
            String jeiFacts,
            List<ChatMessage> history
    ) {
        return ask(question, heldItem, hotbarItems, focusMods, graphFacts, sources, policy,
                questOverride, questConflict, jeiFacts, history, null);
    }

    public String ask(
            String question,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            List<String> focusMods,
            List<String> graphFacts,
            List<String> sources,
            String policy,
            boolean questOverride,
            boolean questConflict,
            String jeiFacts,
            List<ChatMessage> history,
            String replyLang
    ) {
        String mode = PackAiConfig.resolvedMode();
        if ("offline".equals(mode)) {
            return null;
        }

        String langCode = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        String apiKey = resolveApiKey();
        String cloudBase = normalizeApiBaseUrl(PackAiConfig.API_BASE_URL.get());
        String ollamaBase = normalizeApiBaseUrl(PackAiConfig.OLLAMA_BASE_URL.get());
        String model;
        String base;
        String authKey;
        boolean usingCloud;

        if ("cloud".equals(mode)) {
            if (apiKey.isEmpty()) {
                return ReplyLang.cloudNoKey(langCode);
            }
            base = cloudBase.isEmpty() ? "https://api.openai.com/v1" : cloudBase;
            model = defaultModel(safe(PackAiConfig.MODEL.get()), "gpt-4o-mini");
            authKey = apiKey;
            usingCloud = true;
        } else if ("ollama".equals(mode)) {
            base = ollamaBase.isEmpty() ? "http://127.0.0.1:11434/v1" : ollamaBase;
            if (!ollamaReachable(base)) {
                return ReplyLang.ollamaDown(langCode, base);
            }
            model = defaultModel(safe(PackAiConfig.OLLAMA_MODEL.get()), "llama3.2");
            authKey = "ollama";
            usingCloud = false;
        } else {
            // auto
            if (!apiKey.isEmpty()) {
                base = cloudBase.isEmpty() ? "https://api.openai.com/v1" : cloudBase;
                model = defaultModel(safe(PackAiConfig.MODEL.get()), "gpt-4o-mini");
                authKey = apiKey;
                usingCloud = true;
            } else if (ollamaReachable(ollamaBase.isEmpty() ? "http://127.0.0.1:11434/v1" : ollamaBase)) {
                base = ollamaBase.isEmpty() ? "http://127.0.0.1:11434/v1" : ollamaBase;
                model = defaultModel(safe(PackAiConfig.OLLAMA_MODEL.get()), "llama3.2");
                authKey = "ollama";
                usingCloud = false;
            } else {
                return null;
            }
        }

        String langName = replyLanguageName(langCode);
        PackAiMod.LOGGER.info("Pack AI LLM mode={} via {} model={} lang={} keyLen={}",
                mode, usingCloud ? "cloud" : "ollama", model, langCode, usingCloud ? apiKey.length() : 0);

        String style = ReplyLang.llmStyle(langCode);
        String rules = ReplyLang.llmRules(langCode, questOverride, questConflict, policy);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("question", question);
        user.put("replyLanguage", langCode);
        ItemRef held = heldItem == null ? ItemRef.NONE : heldItem;
        if (held.isPresent()) {
            Map<String, String> heldObj = new LinkedHashMap<>();
            heldObj.put("id", held.id());
            heldObj.put("name", held.label());
            user.put("heldItem", heldObj);
            user.put("focusItemId", held.id());
        } else {
            user.put("heldItem", null);
        }
        // Prefer explicit mod:id in the question when present.
        if (question != null) {
            java.util.Optional<String> qid = ItemResolver.idInQuestion(question);
            if (qid.isPresent()) {
                user.put("focusItemId", qid.get());
            }
        }
        if (hotbarItems != null && !hotbarItems.isEmpty()) {
            List<Map<String, String>> bag = new ArrayList<>();
            for (ItemRef ref : hotbarItems) {
                if (ref != null && ref.isPresent() && bag.size() < 9) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("id", ref.id());
                    row.put("name", ref.label());
                    bag.add(row);
                }
            }
            if (!bag.isEmpty()) {
                user.put("hotbar", bag);
            }
        }
        if (jeiFacts != null && !jeiFacts.isBlank()) {
            user.put("jei", jeiFacts);
        }
        user.put("focusMods", focusMods);
        if (ReplyLang.isChinese(langCode)) {
            user.put("sources", List.of(
                    "整合包任務書或本地配方",
                    "JEI（若有）",
                    "整合包掉落表／釣魚／交易／腳本（若有）",
                    "網搜（僅 Minecraft mod，若有）"));
        } else {
            user.put("sources", List.of(
                    "pack quest book or local recipes",
                    "JEI (if any)",
                    "pack loot / fishing / trades / scripts (if any)",
                    "web search (Minecraft mods only, if any)"));
        }
        // Keep short readable hints only — never raw paths for the model to echo
        List<String> readableFacts = new ArrayList<>();
        if (graphFacts != null) {
            for (String f : graphFacts) {
                if (readableFacts.size() >= 20) {
                    break;
                }
                readableFacts.add(Plainify.humanizeText(f.replace("-[", " → ").replace("]->", " ")));
            }
        }
        user.put("graphFacts", readableFacts);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.2);
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", ReplyLang.llmSystemLead(langCode, langName)
                + ReplyLang.factCheck(langCode)
                + style + rules);
        messages.add(sys);
        if (history != null) {
            for (ChatMessage msg : history) {
                if (msg == null || msg.text() == null || msg.text().isBlank()) {
                    continue;
                }
                JsonObject turn = new JsonObject();
                turn.addProperty("role", msg.apiRole());
                turn.addProperty("content", Plainify.forMinecraftUi(msg.text()));
                messages.add(turn);
            }
        }
        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", GSON.toJson(user));
        messages.add(usr);
        body.add("messages", messages);

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
            if (!authKey.isEmpty()) {
                rb.header("Authorization", "Bearer " + authKey);
            }
            HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() >= 400) {
                String hint = "";
                if (res.statusCode() == 401 && usingCloud) {
                    hint = ReplyLang.llmApiKeyHint(langCode, apiKey.length());
                }
                return ReplyLang.llmCallFailed(langCode, " HTTP " + res.statusCode() + ": " + res.body() + hint);
            }
            JsonObject obj = GSON.fromJson(res.body(), JsonObject.class);
            return obj.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        } catch (Exception e) {
            return ReplyLang.llmCallFailed(langCode, "：" + e.getMessage());
        }
    }

    /**
     * Map Minecraft language code to a human language name for the system prompt.
     */
    static String replyLanguageName(String code) {
        if (code == null || code.isBlank()) {
            return "繁體中文";
        }
        String c = code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (c.startsWith("zh_tw") || c.startsWith("zh_hk") || "zh_hant".equals(c)) {
            return "繁體中文";
        }
        if (c.startsWith("zh_cn") || c.startsWith("zh_sg") || "zh_hans".equals(c) || "zh".equals(c)) {
            return "简体中文";
        }
        if (c.startsWith("en")) {
            return "English";
        }
        if (c.startsWith("ja")) {
            return "日本語";
        }
        if (c.startsWith("ko")) {
            return "한국어";
        }
        if (c.startsWith("de")) {
            return "Deutsch";
        }
        if (c.startsWith("fr")) {
            return "Français";
        }
        if (c.startsWith("es")) {
            return "Español";
        }
        if (c.startsWith("pt")) {
            return "Português";
        }
        if (c.startsWith("ru")) {
            return "Русский";
        }
        if (c.startsWith("it")) {
            return "Italiano";
        }
        if (c.startsWith("pl")) {
            return "Polski";
        }
        if (c.startsWith("uk")) {
            return "Українська";
        }
        if (c.startsWith("vi")) {
            return "Tiếng Việt";
        }
        if (c.startsWith("th")) {
            return "ภาษาไทย";
        }
        return "the language of Minecraft locale \"" + c + "\"";
    }

    /**
     * Env PACKAI_API_KEY wins (avoids NeoForge config UI mangling); else sanitized config value.
     */
    public static String resolveApiKey() {
        String env = sanitizeApiKey(System.getenv("PACKAI_API_KEY"));
        if (!env.isEmpty()) {
            return env;
        }
        env = sanitizeApiKey(System.getenv("DEEPSEEK_API_KEY"));
        if (!env.isEmpty()) {
            return env;
        }
        return sanitizeApiKey(PackAiConfig.API_KEY.get());
    }

    /** Strip quotes, Bearer prefix, BOM, whitespace — common paste/config mistakes. */
    public static String sanitizeApiKey(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1).trim();
        }
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            s = s.substring(7).trim();
        }
        s = s.replace("\r", "").replace("\n", "").trim();
        return s;
    }

    private boolean ollamaReachable(String base) {
        if (base == null || base.isBlank()) {
            return false;
        }
        try {
            String probe = base.endsWith("/v1") ? base.substring(0, base.length() - 3) + "/api/tags" : base + "/api/tags";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(probe))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Normalize OpenAI-compatible API base.
     * Accepts either {@code https://host/v1} or a pasted full
     * {@code .../v1/chat/completions} URL (common mistake) and strips the path suffix.
     */
    public static String normalizeApiBaseUrl(String raw) {
        String s = safe(raw);
        if (s.isEmpty()) {
            return "";
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/chat/completions")) {
            s = s.substring(0, s.length() - "/chat/completions".length());
            while (s.endsWith("/")) {
                s = s.substring(0, s.length() - 1);
            }
        } else if (lower.endsWith("/completions") && !lower.endsWith("/chat/completions")) {
            // bare /completions (non-chat) — still treat as mistaken full endpoint
            s = s.substring(0, s.length() - "/completions".length());
            while (s.endsWith("/")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    private static String defaultModel(String configured, String fallback) {
        return configured.isEmpty() ? fallback : configured;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
