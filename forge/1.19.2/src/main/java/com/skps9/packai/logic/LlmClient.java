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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.api.AskToolCall;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.config.PackAiConfig;

/** Routes to cloud / Ollama / none based on llm.mode. */
public final class LlmClient {
    private static final Gson GSON = new Gson();
    private static final Set<String> URLS_WITHOUT_NATIVE_TOOLS = ConcurrentHashMap.newKeySet();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    /** Usage from the most recent successful {@link #ask} HTTP response (else {@link TokenUsage#NONE}). */
    private volatile TokenUsage lastUsage = TokenUsage.NONE;
    private volatile TokenUsage cumulativeUsage = TokenUsage.NONE;
    private volatile String lastBase = "";

    /** Token usage from the last {@link #ask} call on this instance (Ask is single-flight). */
    public TokenUsage lastUsage() {
        TokenUsage u = this.lastUsage;
        return u == null ? TokenUsage.NONE : u;
    }

    /** Reset the per-user-ask accumulator (call once at the top of AskEngine.ask). */
    public void resetUsageAccumulator() {
        this.cumulativeUsage = TokenUsage.NONE;
    }

    /** Sum of usage across all LLM rounds of the current ask (lastUsage is only the last round). */
    public TokenUsage cumulativeUsage() {
        TokenUsage u = this.cumulativeUsage;
        return u == null ? TokenUsage.NONE : u;
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
                questOverride, questConflict, jeiFacts, history, null, null);
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
        return ask(question, heldItem, hotbarItems, focusMods, graphFacts, sources, policy,
                questOverride, questConflict, jeiFacts, history, replyLang, null);
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
            String replyLang,
            String purposeFacts
    ) {
        LlmRound round = completeRound(
                question, heldItem, hotbarItems, focusMods, graphFacts, sources, policy,
                questOverride, questConflict, jeiFacts, history, replyLang, purposeFacts,
                null, null, Duration.ofSeconds(90), List.of());
        return round == null ? null : round.content();
    }

    /**
     * One-shot chat with custom system/user (no tools, no Pack-AI ask prompt).
     * Returns model content, or null on offline / HTTP fail / exception.
     * Used for lightweight hops (e.g. ask intent classify).
     */
    public String chatOnce(String system, String user, double temperature, Duration timeout) {
        this.lastUsage = TokenUsage.NONE;
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
                return null;
            }
            base = cloudBase.isEmpty() ? "https://api.openai.com/v1" : cloudBase;
            model = defaultModel(safe(PackAiConfig.MODEL.get()), "gpt-4o-mini");
            authKey = apiKey;
            usingCloud = true;
        } else if ("ollama".equals(mode)) {
            base = ollamaBase.isEmpty() ? "http://127.0.0.1:11434/v1" : ollamaBase;
            if (!ollamaReachable(base)) {
                return null;
            }
            model = defaultModel(safe(PackAiConfig.OLLAMA_MODEL.get()), "llama3.2");
            authKey = "ollama";
            usingCloud = false;
        } else if (!apiKey.isEmpty()) {
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
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", temperature);
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", system == null ? "" : system);
        messages.add(sys);
        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", user == null ? "" : user);
        messages.add(usr);
        body.add("messages", messages);
        this.lastBase = base;
        Duration httpTimeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        if (httpTimeout.isZero() || httpTimeout.isNegative()) {
            httpTimeout = Duration.ofSeconds(20);
        }
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/chat/completions"))
                    .timeout(httpTimeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
            if (!authKey.isEmpty()) {
                rb.header("Authorization", "Bearer " + authKey);
            }
            HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = res.statusCode();
            if (status >= 400) {
                PackAiMod.LOGGER.info(
                        "Pack AI chatOnce HTTP {} via {} model={}",
                        status, usingCloud ? "cloud" : "ollama", model);
                return null;
            }
            JsonObject obj = GSON.fromJson(res.body(), JsonObject.class);
            TokenUsage usage = TokenUsage.fromResponse(obj);
            this.lastUsage = usage;
            if (usage.isPresent()) {
                this.cumulativeUsage = this.cumulativeUsage.plus(usage);
            }
            JsonObject message = obj.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message");
            if (!message.has("content") || message.get("content").isJsonNull()) {
                return null;
            }
            JsonElement c = message.get("content");
            String content = c.isJsonPrimitive() ? c.getAsString() : c.toString();
            return content == null || content.isBlank() ? null : content;
        } catch (Exception e) {
            PackAiMod.LOGGER.info("Pack AI chatOnce failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean urlLacksNativeTools() {
        String base = lastBase;
        return base != null && !base.isBlank() && URLS_WITHOUT_NATIVE_TOOLS.contains(base);
    }

    /**
     * One chat/completions round. {@code toolNames} null/empty → no {@code tools} schema.
     * HTTP 400 while tools were sent → {@link LlmRound#protocolProbe()} (do not count as a round).
     * 401/429 never switch protocol.
     */
    public LlmRound completeRound(
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
            String replyLang,
            String purposeFacts,
            List<String> toolNames,
            Duration timeout
    ) {
        return completeRound(
                question, heldItem, hotbarItems, focusMods, graphFacts, sources, policy,
                questOverride, questConflict, jeiFacts, history, replyLang, purposeFacts,
                null, toolNames, timeout, List.of());
    }

    public LlmRound completeRound(
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
            String replyLang,
            String purposeFacts,
            String jeiFocusItemId,
            List<String> toolNames,
            Duration timeout,
            List<ToolChatTurn> toolTurns
    ) {
        this.lastUsage = TokenUsage.NONE;
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
                return LlmRound.of(0, ReplyLang.cloudNoKey(langCode));
            }
            base = cloudBase.isEmpty() ? "https://api.openai.com/v1" : cloudBase;
            model = defaultModel(safe(PackAiConfig.MODEL.get()), "gpt-4o-mini");
            authKey = apiKey;
            usingCloud = true;
        } else if ("ollama".equals(mode)) {
            base = ollamaBase.isEmpty() ? "http://127.0.0.1:11434/v1" : ollamaBase;
            if (!ollamaReachable(base)) {
                return LlmRound.of(0, ReplyLang.ollamaDown(langCode, base));
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
            List<String> schematics = ItemVariantKeys.schematics(held.sample());
            if (!schematics.isEmpty()) {
                heldObj.put("schematics", String.join(",", schematics));
                heldObj.put("variantKey", schematics.get(0));
            }
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
        // JEI card focus when question has no mod:id and held empty.
        if (!user.containsKey("focusItemId")
                && jeiFocusItemId != null
                && !jeiFocusItemId.isBlank()) {
            user.put("focusItemId", jeiFocusItemId.trim());
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
                // Legacy key kept for older prompt habits; alsoSelected = inv-pick co-subjects.
                user.put("hotbar", bag);
                user.put("alsoSelected", bag);
                user.put("answerAllSelected", Boolean.TRUE);
                List<Map<String, String>> all = new ArrayList<>();
                if (held.isPresent()) {
                    Map<String, String> focusRow = new LinkedHashMap<>();
                    focusRow.put("id", held.id());
                    focusRow.put("name", held.label());
                    all.add(focusRow);
                }
                all.addAll(bag);
                user.put("selectedItems", all);
            }
        }
        if (jeiFacts != null && !jeiFacts.isBlank()) {
            user.put("jei", jeiFacts);
        }
        if (purposeFacts != null && !purposeFacts.isBlank()) {
            user.put("purpose", purposeFacts);
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
        int factCap = PackAiConfig.maxFacts();
        if (graphFacts != null) {
            for (String f : graphFacts) {
                if (readableFacts.size() >= factCap) {
                    break;
                }
                readableFacts.add(Plainify.humanizeGraphFact(f));
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
                + PackAuthorAgents.systemAddon(langCode)
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
        if (toolTurns != null) {
            for (ToolChatTurn turn : toolTurns) {
                if (turn != null) {
                    messages.add(turn.toMessageJson());
                }
            }
        }
        body.add("messages", messages);
        this.lastBase = base;
        boolean sendTools = toolNames != null && !toolNames.isEmpty()
                && !PackAiConfig.askNativeToolsOff()
                && (PackAiConfig.askNativeToolsForce()
                        || !URLS_WITHOUT_NATIVE_TOOLS.contains(base));
        if (sendTools) {
            body.add("tools", nativeToolsSchema(toolNames));
        }
        logFullPromptIfEnabled(messages);

        Duration httpTimeout = timeout == null ? Duration.ofSeconds(90) : timeout;
        if (httpTimeout.isZero() || httpTimeout.isNegative()) {
            httpTimeout = Duration.ofMillis(1);
        }
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/chat/completions"))
                    .timeout(httpTimeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
            if (!authKey.isEmpty()) {
                rb.header("Authorization", "Bearer " + authKey);
            }
            HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = res.statusCode();
            if (status >= 400) {
                String hint = "";
                if (status == 401 && usingCloud) {
                    hint = ReplyLang.llmApiKeyHint(langCode, apiKey.length());
                }
                boolean probe = sendTools && status == 400;
                if (probe) {
                    URLS_WITHOUT_NATIVE_TOOLS.add(base);
                    PackAiMod.LOGGER.info("Pack AI LLM tools unsupported at {} (HTTP 400); remember URL", base);
                }
                return new LlmRound(
                        status,
                        ReplyLang.llmCallFailed(langCode, " HTTP " + status + ": " + res.body() + hint),
                        List.of(),
                        probe);
            }
            JsonObject obj = GSON.fromJson(res.body(), JsonObject.class);
            TokenUsage usage = TokenUsage.fromResponse(obj);
            this.lastUsage = usage;
            if (usage.isPresent()) {
                this.cumulativeUsage = this.cumulativeUsage.plus(usage);
            }
            if (usage.isPresent()) {
                PackAiMod.LOGGER.info(
                        "Pack AI LLM usage prompt={} completion={} total={}",
                        usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
            }
            JsonObject message = obj.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message");
            String content = "";
            if (message.has("content") && !message.get("content").isJsonNull()) {
                JsonElement c = message.get("content");
                content = c.isJsonPrimitive() ? c.getAsString() : c.toString();
            }
            String reasoningContent = "";
            if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
                JsonElement r = message.get("reasoning_content");
                reasoningContent = r.isJsonPrimitive() ? r.getAsString() : r.toString();
            }
            List<AskToolCall> calls = parseNativeToolCalls(message);
            return new LlmRound(status, content, calls, false, reasoningContent);
        } catch (Exception e) {
            return LlmRound.of(0, ReplyLang.llmCallFailed(langCode, "：" + e.getMessage()));
        }
    }

    /** Model-facing teaching line when a native tool returns empty. */
    public static String toolMissNote(String name, String item) {
        String n = name == null ? "" : name;
        String id = item == null ? "" : item;
        if ("jei_lookup".equals(name)) {
            return "[TOOL_MISS] jei_lookup empty for '" + id + "' — JEI returned nothing at this dump_level. "
                    + "If the call was INFO: INFO covers only JEI info pages, so empty INFO does NOT mean no recipes/uses "
                    + "— re-call with dump_level=OUTPUT. If OUTPUT was already empty, the item has no JEI recipe/use listed. "
                    + "Do not invent.";
        }
        if ("acquire".equals(name)) {
            return "[TOOL_MISS] acquire empty — pack index has no loot/trade/quest/script path for '" + id
                    + "'. Say unknown/obtain unknown; do not invent.";
        }
        if ("guide_fetch".equals(name)) {
            return "[TOOL_MISS] guide_fetch empty — no Patchouli entry for '" + id
                    + "'. Check JEI info instead; do not invent.";
        }
        if ("quest_fetch".equals(name)) {
            return "[TOOL_MISS] quest_fetch empty — no quest entry for '" + id
                    + "'. Check guide/JEI instead; do not invent.";
        }
        if ("consume_use".equals(name)) {
            return "[TOOL_MISS] consume_use empty — '" + id
                    + "' has no right-click consume use. Check JEI info for use; do not invent.";
        }
        if ("purpose_lookup".equals(name)) {
            return "[TOOL_MISS] purpose_lookup empty — no purpose facts for '" + id
                    + "'. State this; do not invent.";
        }
        if ("enchant_lookup".equals(name)) {
            return "[TOOL_MISS] enchant_lookup empty — no canEnchant enchants for '" + id
                    + "'. State this; do not invent.";
        }
        if ("repair_lookup".equals(name)) {
            return "[TOOL_MISS] repair_lookup empty — do not invent; best-effort scan found no anvil material; "
                    + "the item may still have mod-specific repair paths (quest or special anvil recipes), "
                    + "do not claim it cannot be repaired";
        }
        if ("tool_build".equals(name)) {
            return "[TOOL_MISS] tool_build empty — no Tetra build parts for '" + id
                    + "'. Check JEI recipe instead; do not invent.";
        }
        if ("tetra_use".equals(name)) {
            return "[TOOL_MISS] tetra_use empty — no Tetra workbench install/use for '" + id
                    + "'. Check JEI info instead; do not invent.";
        }
        if ("worldgen_lookup".equals(name)) {
            return "[TOOL_MISS] worldgen_lookup empty — no worldgen/ore entry for '" + id
                    + "'. Say unknown; do not invent.";
        }
        if ("show_recipe_card".equals(name) || "render_recipe_cards".equals(name)) {
            return "[TOOL_MISS] render_recipe_cards empty — no JEI card for that item/role. "
                    + "Do not retry the same item_id+role+machine; try another role or omit machine; "
                    + "answer with text only if still empty. Do not invent.";
        }
        return "[TOOL_MISS] " + n + " empty — do not invent";
    }

    static String toolSchemaDescription(String name) {
        if ("item_search".equals(name)) {
            return "Find pack item ids by display name or partial id. "
                    + "query=player wording. Returns top hits; then call render_recipe_cards. No cards attached.";
        }
        if ("render_recipe_cards".equals(name)) {
            return "Show JEI recipe cards under the answer (card strip). "
                    + "item_id=mod:id (or item=); role=output|upgrade|uses; machine=optional category substring. "
                    + "Do NOT write [[recipe_card…]] markers in answer text.";
        }
        if ("show_recipe_card".equals(name)) {
            return "RETIRED — use render_recipe_cards.";
        }
        if ("jei_lookup".equals(name)) {
            return "JEI recipes/uses/catalysts. dump_level=SLIM|OUTPUT|INFO. "
                    + "INFO = JEI Information/信息 pages (page text + related item ids). "
                    + "Call dump_level=INFO for 取得/用途 when the item has 信息 tabs. "
                    + "jei_info_use = how to use (other-output carry-X-to-get-Y = use of X, not obtain of X). "
                    + "jei_info_acquire = how to get. If INFO returned text, never write 未标明 / does not specify.";
        }
        if ("acquire".equals(name)) {
            return "Pack-local acquire path (loot/trade/quest/script). item=mod:id; dump_level=SLIM|OUTPUT. "
                    + "Example: acquire(item='minecraft:iron_pickaxe', dump_level='OUTPUT').";
        }
        if ("guide_fetch".equals(name)) {
            return "Fetch pack guidebook/Patchouli entry. item=mod:id or query=text.";
        }
        if ("quest_fetch".equals(name)) {
            return "Fetch quest-book entry. item=mod:id or query=text.";
        }
        if ("consume_use".equals(name)) {
            return "How to use via right-click consume. item=mod:id.";
        }
        if ("purpose_lookup".equals(name)) {
            return "Item purpose/how-to-use facts. item=mod:id.";
        }
        if ("enchant_lookup".equals(name)) {
            return "Registry enchants that canEnchant this item (book/anvil path). "
                    + "item=mod:id optional; omit/empty = current focus/held.";
        }
        if ("repair_lookup".equals(name)) {
            return "Anvil repair materials for this item (iron/gold/diamond etc). "
                    + "item=mod:id optional; omit/empty = current focus/held. "
                    + "Call when asked how to repair/fix/restore durability. "
                    + "Scan is best-effort — if empty, the item may still have mod-specific repair paths "
                    + "(quest/anvil recipes), do not claim 'cannot be repaired'.";
        }
        if ("tool_build".equals(name)) {
            return "Tetra tool build parts/slots. item=mod:id.";
        }
        if ("tetra_use".equals(name)) {
            return "Tetra workbench install/use. item=mod:id.";
        }
        if ("worldgen_lookup".equals(name)) {
            return "Worldgen/ore/feature lookup. item=mod:id or query.";
        }
        return name == null ? "" : name;
    }

    static JsonArray toolSchemaRequired(String name) {
        JsonArray req = new JsonArray();
        if ("item_search".equals(name)) {
            req.add("query");
        } else if ("render_recipe_cards".equals(name)) {
            req.add("role");
        } else if ("enchant_lookup".equals(name) || "repair_lookup".equals(name)) {
            // item optional — omit/empty uses focus stack
        } else {
            req.add("item");
        }
        return req;
    }

    static JsonArray nativeToolsSchema(List<String> names) {
        JsonArray arr = new JsonArray();
        for (String name : names) {
            if (name == null || name.isBlank() || !AskToolLoop.ALLOWLIST.contains(name)) {
                continue;
            }
            JsonObject t = new JsonObject();
            t.addProperty("type", "function");
            JsonObject fn = new JsonObject();
            fn.addProperty("name", name);
            fn.addProperty("description", toolSchemaDescription(name));
            JsonObject params = new JsonObject();
            params.addProperty("type", "object");
            JsonObject props = new JsonObject();
            if ("item_search".equals(name)) {
                JsonObject query = new JsonObject();
                query.addProperty("type", "string");
                props.add("query", query);
                JsonObject item = new JsonObject();
                item.addProperty("type", "string");
                props.add("item", item);
            } else if ("render_recipe_cards".equals(name)) {
                JsonObject itemId = new JsonObject();
                itemId.addProperty("type", "string");
                props.add("item_id", itemId);
                JsonObject item = new JsonObject();
                item.addProperty("type", "string");
                props.add("item", item);
                JsonObject role = new JsonObject();
                role.addProperty("type", "string");
                role.addProperty("description", "output | upgrade | uses");
                props.add("role", role);
                JsonObject machine = new JsonObject();
                machine.addProperty("type", "string");
                props.add("machine", machine);
                JsonObject query = new JsonObject();
                query.addProperty("type", "string");
                props.add("query", query);
            } else {
                JsonObject item = new JsonObject();
                item.addProperty("type", "string");
                props.add("item", item);
                JsonObject keys = new JsonObject();
                keys.addProperty("type", "array");
                JsonObject items = new JsonObject();
                items.addProperty("type", "string");
                keys.add("items", items);
                props.add("variant_keys", keys);
                JsonObject level = new JsonObject();
                level.addProperty("type", "string");
                if ("jei_lookup".equals(name)) {
                    level.addProperty("description",
                            "SLIM, OUTPUT, or INFO. INFO = JEI Information/信息 pages only.");
                }
                props.add("dump_level", level);
                JsonObject query = new JsonObject();
                query.addProperty("type", "string");
                props.add("query", query);
                JsonObject cardIdx = new JsonObject();
                cardIdx.addProperty("type", "string");
                props.add("card_index", cardIdx);
            }
            params.add("properties", props);
            params.add("required", toolSchemaRequired(name));
            params.addProperty("additionalProperties", false);
            fn.add("parameters", params);
            t.add("function", fn);
            arr.add(t);
        }
        return arr;
    }

    static List<AskToolCall> parseNativeToolCalls(JsonObject message) {
        if (message == null || !message.has("tool_calls") || !message.get("tool_calls").isJsonArray()) {
            return List.of();
        }
        List<AskToolCall> out = new ArrayList<>();
        for (JsonElement el : message.getAsJsonArray("tool_calls")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject call = el.getAsJsonObject();
            JsonObject fn = call.has("function") && call.get("function").isJsonObject()
                    ? call.getAsJsonObject("function") : call;
            String name = fn.has("name") ? fn.get("name").getAsString() : "";
            String callId = "";
            if (call.has("id") && call.get("id").isJsonPrimitive()) {
                callId = call.get("id").getAsString();
            }
            String argsJson = "";
            if (fn.has("arguments")) {
                JsonElement a = fn.get("arguments");
                argsJson = a.isJsonPrimitive() ? a.getAsString() : a.toString();
            }
            String item = "";
            String dump = "";
            String query = "";
            List<String> keys = List.of();
            try {
                JsonObject args = GSON.fromJson(argsJson, JsonObject.class);
                if (args != null) {
                    if (args.has("item_id")) {
                        item = args.get("item_id").getAsString();
                    }
                    if (item.isBlank() && args.has("item")) {
                        item = args.get("item").getAsString();
                    }
                    if (args.has("role")) {
                        dump = args.get("role").getAsString();
                    }
                    if (dump.isBlank() && args.has("dump_level")) {
                        dump = args.get("dump_level").getAsString();
                    }
                    if (args.has("machine")) {
                        query = args.get("machine").getAsString();
                    }
                    if (query.isBlank() && args.has("query")) {
                        query = args.get("query").getAsString();
                    }
                    if (item.isBlank() && !query.isBlank() && !AskToolLoop.isDumpLevel(query)
                            && !"item_search".equals(name)) {
                        item = query;
                    }
                    if ("item_search".equals(name) && item.isBlank() && !query.isBlank()) {
                        item = query;
                    }
                    if (dump.isBlank() && args.has("card_index")) {
                        dump = args.get("card_index").getAsString();
                    }
                    if (args.has("variant_keys") && args.get("variant_keys").isJsonArray()) {
                        List<String> ks = new ArrayList<>();
                        for (JsonElement k : args.getAsJsonArray("variant_keys")) {
                            if (k.isJsonPrimitive()) {
                                ks.add(k.getAsString());
                            }
                        }
                        keys = ks;
                    }
                }
            } catch (Exception ignored) {
                // drop malformed arguments
            }
            AskToolCall mapped = AskToolLoop.canonicalizeCall(name, item, dump, query, keys, callId, argsJson);
            if (mapped != null) {
                out.add(mapped);
            }
        }
        return out;
    }

    /**
     * When {@link PackAiConfig#logFullPrompt()} is on, dump the exact messages array
     * sent to chat/completions (no API key). Chunked so log sinks do not silently truncate.
     */
    private static void logFullPromptIfEnabled(JsonArray messages) {
        if (!PackAiConfig.logFullPrompt() || messages == null) {
            return;
        }
        String full = GSON.toJson(messages);
        PackAiMod.LOGGER.info("Pack AI LLM full prompt begin ({} chars, {} messages)",
                full.length(), messages.size());
        final int chunk = 6000;
        for (int i = 0; i < full.length(); i += chunk) {
            int end = Math.min(i + chunk, full.length());
            PackAiMod.LOGGER.info("Pack AI LLM full prompt [{}-{}]: {}", i, end, full.substring(i, end));
        }
        PackAiMod.LOGGER.info("Pack AI LLM full prompt end");
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
