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
import com.skps9.packai.config.PackAiConfig;

/** Routes to cloud / Ollama / none based on llm.mode. */
public final class LlmClient {
    private static final Gson GSON = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public String ask(
            String question,
            String heldItemId,
            List<String> focusMods,
            List<String> graphFacts,
            List<String> sources,
            String policy,
            boolean questOverride,
            boolean questConflict
    ) {
        String mode = PackAiConfig.resolvedMode();
        if ("offline".equals(mode)) {
            return null;
        }

        String apiKey = resolveApiKey();
        String cloudBase = trimSlash(safe(PackAiConfig.API_BASE_URL.get()));
        String ollamaBase = trimSlash(safe(PackAiConfig.OLLAMA_BASE_URL.get()));
        String model;
        String base;
        String authKey;
        boolean usingCloud;

        if ("cloud".equals(mode)) {
            if (apiKey.isEmpty()) {
                return "目前為 cloud 模式，但未設定 API key。請在 config/packai-client.toml 填 llm.apiKey，"
                        + "或設環境變數 PACKAI_API_KEY；也可改 llm.mode 為 auto / ollama / offline。";
            }
            base = cloudBase.isEmpty() ? "https://api.openai.com/v1" : cloudBase;
            model = defaultModel(safe(PackAiConfig.MODEL.get()), "gpt-4o-mini");
            authKey = apiKey;
            usingCloud = true;
        } else if ("ollama".equals(mode)) {
            base = ollamaBase.isEmpty() ? "http://127.0.0.1:11434/v1" : ollamaBase;
            if (!ollamaReachable(base)) {
                return "目前為 ollama 模式，但連不上本機 Ollama（" + base + "）。"
                        + "請先啟動 Ollama 並 pull 模型，或改 llm.mode。";
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

        PackAiMod.LOGGER.info("Pack AI LLM mode={} via {} model={} keyLen={}",
                mode, usingCloud ? "cloud" : "ollama", model, usingCloud ? apiKey.length() : 0);

        String style = "主文必須白話（作法／材料／步驟）。絕對禁止：物品ID（如 mod:item）、檔案路徑、KubeJS／腳本程式碼、JSON。"
                + "材料與物品只用可讀名稱。文末【來源】只寫「整合包任務書」或「整合包本地配方」。";
        String rules;
        if (questOverride) {
            rules = "玩家表示任務書有誤：依本地事實回答，標明任務可能有誤。";
        } else if (questConflict) {
            rules = "任務可能過時：以本地魔改為準。";
        } else if ("local_only".equals(policy)) {
            rules = "以本地／圖事實為準，勿用通用 wiki 覆蓋。";
        } else {
            rules = "可結合常識，但與本地衝突時以本地為準。";
        }

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("question", question);
        user.put("heldItem", heldItemId == null || heldItemId.isBlank() ? null : Plainify.displayName(heldItemId));
        user.put("focusMods", focusMods);
        user.put("sources", List.of("整合包任務書或本地配方"));
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
        sys.addProperty("content", "你是 Minecraft 整合包助手。使用繁體中文。" + style + rules);
        messages.add(sys);
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
                    hint = "\n提示：curl 若成功，請直接改實例 config/packai-client.toml 的 llm.apiKey（完整 sk-…），"
                            + "或設環境變數 PACKAI_API_KEY；遊戲內設定框可能存錯。目前 key 長度=" + apiKey.length();
                }
                return "AI 呼叫失敗 HTTP " + res.statusCode() + ": " + res.body() + hint;
            }
            JsonObject obj = GSON.fromJson(res.body(), JsonObject.class);
            return obj.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        } catch (Exception e) {
            return "AI 呼叫失敗：" + e.getMessage();
        }
    }

    /**
     * Env PACKAI_API_KEY wins (avoids NeoForge config UI mangling); else sanitized config value.
     */
    static String resolveApiKey() {
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

    private static String defaultModel(String configured, String fallback) {
        return configured.isEmpty() ? fallback : configured;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
