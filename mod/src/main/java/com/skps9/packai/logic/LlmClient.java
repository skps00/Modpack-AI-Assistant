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
import com.skps9.packai.logic.CraftPriority;

/** Routes to cloud / Ollama / none based on llm.mode. */
public final class LlmClient {
    private static final Gson GSON = new Gson();
    /**
     * Fact-check discipline for the model (system prompt). Prefer blank over inventing.
     */
    private static final String FACT_CHECK =
            "你必須在回答前先進行「事實檢查思考」(fact-check thinking)。"
                    + "除非使用者明確提供、或資料中確實存在，否則不得假設、推測或自行創造內容。"
                    + "嚴格依據來源：僅使用使用者提供的內容、你內部明確記載的知識、或經明確查證的資料（本請求中的 question／heldItem／hotbar／jei／graphFacts／對話歷史）。"
                    + "若資訊不足，請直接說明「沒有足夠資料」或「我無法確定」，不要臆測。"
                    + "顯示思考依據：若你引用資料或推論，請說明你依據的段落或理由；若是個人分析或估計，必須明確標註「這是推論」或「這是假設情境」。"
                    + "避免裝作知道：不可為了讓答案完整而「補完」不存在的內容；若遇到模糊或不完整的問題，請先回問確認或提出選項，而非自行決定。"
                    + "保持語意一致：不可改寫或擴大使用者原意；若需重述，應明確標示為「重述版本」，並保持語義對等。"
                    + "回答格式：若有明確資料，回答並附上依據；若無明確資料：回答「無法確定」並說明原因。"
                    + "不要在回答中使用「應該是」「可能是」「我猜」等模糊語氣，除非使用者要求。"
                    + "產出前檢查：a.有清楚的依據 b.未超出題目範圍 c.沒有出現任何未被明確提及的人名、數字、事件或假設。"
                    + "最終原則：寧可空白，不可捏造。";
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

        String apiKey = resolveApiKey();
        String cloudBase = normalizeApiBaseUrl(PackAiConfig.API_BASE_URL.get());
        String ollamaBase = normalizeApiBaseUrl(PackAiConfig.OLLAMA_BASE_URL.get());
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

        String langCode = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        String langName = replyLanguageName(langCode);
        PackAiMod.LOGGER.info("Pack AI LLM mode={} via {} model={} lang={} keyLen={}",
                mode, usingCloud ? "cloud" : "ollama", model, langCode, usingCloud ? apiKey.length() : 0);

        String style = "主文必須白話（作法／材料／步驟），給 Minecraft 遊戲內純文字顯示。"
                + "絕對禁止：emoji／表情符號、Markdown（不要用 # ** ` - 清單標題）、物品ID、檔案路徑、KubeJS／腳本、JSON。"
                + "材料與物品只用可讀名稱。"
                + CraftPriority.preferenceHint()
                + "若有 jei 欄位：優先用它說明合成配方與用途（等同遊戲內 JEI 按 R／U），以及「作為機器／工作站」的特殊配方（JEI 催化劑）；"
                + "JEI 列表已依推薦順序排序。"
                + "若資料含本地獲取／掉落／釣魚／交易／腳本配方：必須一併說明 JEI 可能沒列出的取得方式；與 JEI 衝突時標明來源差異。"
                + "若出現「壓縮循環」或 9 合 1 磚塊再拆回材料：那只是收納互轉，絕對不要當成主要取得／合成進度；除非玩家在問壓縮或空間。"
                + "提到任務時只用任務名稱／章節名稱，絕對不要寫出任務 ID（例如一長串十六進位）。"
                + "推薦物品時，回答最末另起一行機器標記（勿寫進正文）：<!--packai:items=mod:id,mod:id2--> 使用 registry id；"
                + "同名不同模組的物品請都列出以便玩家辨識。"
                + "若有【網搜】：只可引用其中與 Minecraft／模組相關的內容；與 JEI／任務書／本地腳本衝突時一律以本地為準，並可提醒整合包可能已魔改。"
                + "每則回答結尾必須另起一行寫【來源】，列出你實際引用的資料（至少一項），例如：JEI、JEI 機器配方、整合包任務書、整合包本地配方、整合包掉落表、整合包釣魚、整合包交易、網搜（模組資料）、AI 推論；不可省略【來源】。";
        String rules;
        if (questOverride) {
            rules = "玩家表示任務書有誤：依本地事實回答，標明任務可能有誤。";
        } else if (questConflict) {
            rules = "任務可能過時：以本地魔改為準。";
        } else if ("local_only".equals(policy)) {
            rules = "此物品／題目有本地覆寫：以本地／圖事實為準，勿用通用 wiki 覆蓋；忽略與本地衝突的網搜。";
        } else if ("mixed".equals(policy)) {
            rules = "整合包可能只魔改部分內容；此題目未見針對該物品的本地覆寫，可參考網搜模組資料，仍以 JEI／本地為準。";
        } else {
            rules = "可結合常識與網搜模組資訊，但與本地衝突時以本地為準。";
        }

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("question", question);
        user.put("replyLanguage", langCode);
        ItemRef held = heldItem == null ? ItemRef.NONE : heldItem;
        // Only the on-screen hover text — what the player actually sees.
        user.put("heldItem", held.isPresent() ? held.label() : null);
        if (hotbarItems != null && !hotbarItems.isEmpty()) {
            List<String> bag = new ArrayList<>();
            for (ItemRef ref : hotbarItems) {
                if (ref != null && ref.isPresent() && bag.size() < 9) {
                    bag.add(ref.label());
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
        user.put("sources", List.of(
                "整合包任務書或本地配方",
                "JEI（若有）",
                "整合包掉落表／釣魚／交易／腳本（若有）",
                "網搜（僅 Minecraft mod，若有）"));
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
        sys.addProperty("content", "你是 Minecraft 整合包助手。請用" + langName + "回答（遊戲語系：" + langCode + "）。"
                + FACT_CHECK
                + style + rules
                + "若有先前對話，請延續上下文回答。");
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
