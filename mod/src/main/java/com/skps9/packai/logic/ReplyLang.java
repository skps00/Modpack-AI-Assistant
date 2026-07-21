package com.skps9.packai.logic;

import java.util.Locale;

/**
 * Localize engine-built player-facing strings (zh vs en).
 * Button labels stay in assets/packai/lang; this covers dynamic text.
 */
public final class ReplyLang {
    private ReplyLang() {}

    public static String current() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getLanguageManager() != null) {
                String code = mc.getLanguageManager().getSelected();
                if (code != null && !code.isBlank()) {
                    return code.trim();
                }
            }
        } catch (Throwable ignored) {
            // headless / early init
        }
        return "zh_tw";
    }

    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return "zh_tw";
        }
        return code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    public static boolean isChinese(String code) {
        return normalize(code).startsWith("zh");
    }

    public static boolean isTraditionalChinese(String code) {
        String c = normalize(code);
        return c.startsWith("zh_tw") || c.startsWith("zh_hk") || "zh_hant".equals(c);
    }

    static String pick(String code, String zh, String en) {
        return isChinese(code) ? zh : en;
    }

    public static String sourceHeader(String code) {
        return pick(code, "【來源】", "[Sources] ");
    }

    public static String sourceJoin(String code) {
        return pick(code, "、", ", ");
    }

    public static String quote(String code, String inner) {
        return pick(code, "「" + inner + "」", "\"" + inner + "\"");
    }

    public static String labelQuestBook(String code) {
        return pick(code, "整合包任務書", "pack quest book");
    }

    public static String labelLocalRecipes(String code) {
        return pick(code, "整合包本地配方", "pack local recipes");
    }

    public static String labelAcquire(String code) {
        return pick(code, "整合包掉落表／釣魚／交易", "pack loot / fishing / trades");
    }

    public static String labelWeb(String code) {
        return pick(code, "網搜（模組資料）", "web search (mod docs)");
    }

    public static String labelAiOnly(String code) {
        return pick(code,
                "AI 模型（未引用 JEI／任務書／本地資料）",
                "AI model (no JEI / quest / local data cited)");
    }

    public static String labelAiModel(String code) {
        return pick(code, "AI 模型", "AI model");
    }

    public static String labelNone(String code) {
        return pick(code, "無（離線／未找到資料）", "none (offline / no data)");
    }

    public static String labelAcquireOffline(String code) {
        return pick(code,
                "整合包掉落表／釣魚／交易／本地腳本",
                "pack loot / fishing / trades / local scripts");
    }

    public static String unnamedQuest(String code) {
        return pick(code, "未命名任務", "Untitled quest");
    }

    public static String unnamedChapter(String code) {
        return pick(code, "（未命名）", "(unnamed)");
    }

    public static String relatedQuest(String itemName, String code) {
        String name = itemName == null ? "" : itemName;
        return pick(code, name + "相關任務", name + " related quest");
    }

    public static String chapterQuest(String chapter, String code) {
        String ch = chapter == null ? "" : chapter;
        return pick(code, ch + "任務", ch + " quest");
    }

    public static String unknownItem(String code) {
        return pick(code, "（未知物品）", "(unknown item)");
    }

    public static String packScript(String code) {
        return pick(code, "整合包腳本", "pack script");
    }

    public static String packConfig(String code) {
        return pick(code, "整合包設定", "pack config");
    }

    public static String packData(String code) {
        return pick(code, "整合包資料", "pack data");
    }

    public static String notePackSpecific(String code) {
        return pick(code,
                "\n【注意】此為本包設定，可能與通用 wiki 不同。",
                "\n[Note] This is pack-specific and may differ from generic wikis.");
    }

    public static String friendlyOffline(String code, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append(pick(code,
                "目前無法用 AI 詳細說明（離線或未設定模型）。\n建議：打開任務書查看相關任務，或用 JEI／EMI 查手上物品的配方。\n",
                "AI detail is unavailable (offline or no model configured).\nTip: check the quest book, or look up the held item in JEI/EMI.\n"));
        if (question != null && !question.isBlank()) {
            sb.append(pick(code, "你的問題：", "Your question: ")).append(question);
        }
        return sb.toString();
    }

    public static String shapedRecipe(String code, String out, String mats) {
        return pick(code,
                "【作法】用工作台（有序）合成" + quote(code, out)
                        + "\n【材料】" + mats
                        + "\n【步驟】1. 打開工作台 2. 依配方擺放 3. 取出" + quote(code, out),
                "[How] Crafting table (shaped): " + quote(code, out)
                        + "\n[Materials] " + mats
                        + "\n[Steps] 1. Open crafting table 2. Place pattern 3. Take " + quote(code, out));
    }

    public static String shapelessRecipe(String code, String out, String mats) {
        return pick(code,
                "【作法】用工作台（無序）合成" + quote(code, out)
                        + "\n【材料】" + mats
                        + "\n【步驟】1. 打開工作台 2. 放入材料 3. 取出" + quote(code, out),
                "[How] Crafting table (shapeless): " + quote(code, out)
                        + "\n[Materials] " + mats
                        + "\n[Steps] 1. Open crafting table 2. Add materials 3. Take " + quote(code, out));
    }

    public static String removedRecipe(String code) {
        return pick(code,
                "【作法】整合包已移除或封鎖某些原版／模組配方\n【步驟】請改看任務書，或用 JEI／EMI 查還有哪些可用作法",
                "[How] The pack removed or blocked some vanilla/mod recipes\n[Steps] Check the quest book, or JEI/EMI for remaining options");
    }

    public static String patternFallback(String code) {
        return pick(code, "（請用 JEI／EMI 對照配方格子）", "(check the crafting grid in JEI/EMI)");
    }

    public static String queryFailed(String code, String msg) {
        return pick(code, "查詢失敗：", "Query failed: ") + msg;
    }

    public static String llmCallFailed(String code, String detail) {
        return pick(code, "AI 呼叫失敗", "AI call failed") + detail;
    }

    public static String cloudNoKey(String code) {
        return pick(code,
                "目前為 cloud 模式，但未設定 API key。請在 config/packai-client.toml 填 llm.apiKey，"
                        + "或設環境變數 PACKAI_API_KEY；也可改 llm.mode 為 auto / ollama / offline。",
                "Cloud mode is on but no API key is set. Put llm.apiKey in config/packai-client.toml, "
                        + "or set PACKAI_API_KEY; or switch llm.mode to auto / ollama / offline.");
    }

    public static String ollamaDown(String code, String base) {
        return pick(code,
                "目前為 ollama 模式，但連不上本機 Ollama（" + base + "）。請先啟動 Ollama 並 pull 模型，或改 llm.mode。",
                "Ollama mode is on but cannot reach Ollama (" + base + "). Start Ollama and pull a model, or change llm.mode.");
    }

    public static String tipOfflineQuest(String code) {
        return pick(code,
                "\n\n提示：目前為 offline 模式，以上為任務書內容（未呼叫 LLM）。",
                "\n\nNote: offline mode — quest book summary only (no LLM).");
    }

    public static String tipQuestSummaryNoAi(String code) {
        return pick(code,
                "\n\n提示：AI 未回覆，以上為任務書摘要。",
                "\n\nNote: AI did not reply; quest book summary above.");
    }

    public static String tipOfflineEmpty(String code) {
        return pick(code,
                "\n\n提示：目前為 offline 模式（不呼叫 LLM）。未找到可顯示的任務內容；可改 llm.mode 或確認已安裝 FTB Quests／Heracles。",
                "\n\nNote: offline mode (no LLM). No quest content found; change llm.mode or install FTB Quests/Heracles.");
    }

    public static String tipNeedLlm(String code) {
        return pick(code,
                "\n\n提示：在 Mods → Packai 設定 llm.mode 與 API key，或安裝並啟動 Ollama。",
                "\n\nTip: set llm.mode and API key under Mods → Packai, or install and start Ollama.");
    }

    public static String questOverrideNotice(String code) {
        return pick(code,
                "【注意：已略過任務書導引（你表示任務可能有誤）】\n",
                "[Note: skipped quest-book guide (you said the quest may be wrong)]\n");
    }

    public static String questFactLine(String code, String title, String desc) {
        String head = pick(code, "任務書：", "Quest: ");
        return head + quote(code, title) + (desc == null || desc.isBlank() ? "" : " — " + desc);
    }

    public static String guideHeader(String code, boolean rich) {
        if (rich) {
            return pick(code, "【任務內容】根據整合包任務書：\n", "[Quest content] From the pack quest book:\n");
        }
        return pick(code,
                "【任務導引】任務書裡有相關內容。可點下方按鈕開啟任務：\n",
                "[Quest guide] Related quests found. Use the buttons below to open them:\n");
    }

    public static String guideChapterQuest(String code, int i, String chapter, String title) {
        return pick(code,
                i + ". 章節：" + chapter + "　任務：" + title + "\n",
                i + ". Chapter: " + chapter + "  Quest: " + title + "\n");
    }

    public static String guideDesc(String code, String d) {
        return pick(code, "   說明：" + d + "\n", "   Summary: " + d + "\n");
    }

    public static String guideDescFallback(String code) {
        return pick(code,
                "   說明：請點下方按鈕在任務書中查看。\n",
                "   Summary: open the quest book via the button below.\n");
    }

    public static String guideNeeds(String code) {
        return pick(code, "   可能需要：", "   May need: ");
    }

    public static String guideEtc(String code) {
        return pick(code, "等", "…");
    }

    public static String guideMore(String code) {
        return pick(code,
                "還有其他相關任務，可在任務書裡搜尋。\n",
                "More related quests may exist — search in the quest book.\n");
    }

    public static String guideStuckHint(String code) {
        return pick(code,
                "若只是卡住／看不懂，請先照任務說明做。\n",
                "If you are stuck, follow the quest instructions first.\n");
    }

    public static String guideConflict(String code) {
        return pick(code,
                "\n【警告】任務內容可能已過時，以下依整合包實際配方：\n",
                "\n[Warning] Quest text may be outdated; pack recipes below take priority:\n");
    }

    public static String fishing(String code) {
        return pick(code, "釣魚：", "Fishing: ");
    }

    public static String loot(String code) {
        return pick(code, "掉落：", "Loot: ");
    }

    public static String trade(String code) {
        return pick(code, "交易：", "Trade: ");
    }

    public static String fishingKind(String code) {
        return pick(code, "釣魚", "Fishing");
    }

    public static String lootKind(String code) {
        return pick(code, "掉落表", "Loot table");
    }

    public static String tradeKind(String code) {
        return pick(code, "交易", "Trade");
    }

    public static String scriptNeeds(String code, String need) {
        return pick(code, "腳本配方需要：", "Script recipe needs: ") + need;
    }

    public static String scriptRemoved(String code) {
        return pick(code, "腳本已移除原配方（整合包有改動）", "Script removed the original recipe (pack change)");
    }

    public static String compactCycle(String code, String need) {
        return pick(code,
                "壓縮循環（材料↔磚塊，不是主要取得方式）：與" + quote(code, need) + "互轉",
                "Compression cycle (storage packing, not a real obtain path): swaps with " + quote(code, need));
    }

    public static String localAcquireHeader(String code, String name) {
        return pick(code, "【本地獲取】", "[Local acquire] ") + quote(code, name);
    }

    public static boolean isScriptNeedsLine(String line) {
        return line != null && (line.contains("腳本配方需要：") || line.contains("Script recipe needs:"));
    }

    public static boolean isScriptRemovedLine(String line) {
        return line != null && (line.contains("腳本已移除") || line.contains("Script removed"));
    }

    public static boolean isLlmSetupError(String answer) {
        if (answer == null) {
            return false;
        }
        return answer.startsWith("AI 呼叫失敗")
                || answer.startsWith("AI call failed")
                || answer.startsWith("目前為 cloud 模式")
                || answer.startsWith("Cloud mode is on")
                || answer.startsWith("目前為 ollama 模式")
                || answer.startsWith("Ollama mode is on");
    }

    public static String webHeaderMixed(String code) {
        return pick(code,
                "【網搜｜僅 Minecraft mod；整合包其他部分可能已魔改，此題目未見本地覆寫，僅供參考】\n",
                "[Web｜Minecraft mods only; other pack areas may be modified; no local override for this topic — reference only]\n");
    }

    public static String webHeaderStrict(String code) {
        return pick(code,
                "【網搜｜僅 Minecraft mod 相關，整合包魔改時可能不準】\n",
                "[Web｜Minecraft mod related only; pack changes may differ]\n");
    }

    public static String jeiNoRecipes(String code) {
        return pick(code, "【JEI】手上物品無 JEI 配方資料。\n", "[JEI] No JEI recipe data for the held item.\n");
    }

    public static String jeiHintEmpty(String code) {
        return pick(code,
                "【JEI 提示】未持物品：在問題中寫 mod:id，或開 JEI 把游標停在物品上再提問。\n",
                "[JEI tip] No held item: type mod:id in the question, or hover an item in JEI then ask.\n");
    }

    public static String jeiHeader(String code, String itemName, String skipLabel) {
        return jeiHeader(code, itemName, "", skipLabel);
    }

    public static String jeiHeader(String code, String itemName, String itemId, String skipLabel) {
        String idPart = itemId == null || itemId.isBlank() ? "" : " [" + itemId + "]";
        return pick(code,
                "【JEI 資料】物品" + quote(code, itemName) + idPart + "（已完整掃描；已略過" + skipLabel + "）\n",
                "[JEI] Item " + quote(code, itemName) + idPart + " (full scan; skipped " + skipLabel + ")\n");
    }

    public static String jeiEmpty(String code, String itemName) {
        return pick(code,
                "【JEI 資料】" + quote(code, itemName) + "目前沒有可顯示的配方、用途或機器配方。",
                "[JEI] " + quote(code, itemName) + " has no showable recipes, uses, or machine recipes.");
    }

    public static String jeiSectionRecipes(String code) {
        return pick(code, "配方（如何製作，等同 JEI 按 R）", "Recipes (how to make; JEI R)");
    }

    public static String jeiSectionUses(String code) {
        return pick(code, "用途（用在何處，等同 JEI 按 U）", "Uses (where used; JEI U)");
    }

    public static String jeiSectionCatalyst(String code) {
        return pick(code, "作為機器／工作站的配方（JEI 催化劑；特殊合成多在此）", "As machine/workstation (JEI catalyst)");
    }

    public static String jeiZeroUseful(String code, int skipped) {
        return pick(code,
                "（有用配方 0 筆；已略過通用配方 " + skipped + " 筆）\n",
                "(0 useful recipes; skipped " + skipped + " generic)\n");
    }

    public static String jeiTotals(String code, int useful, int skipped) {
        String s = pick(code, "【JEI 掃描合計】有用 " + useful + " 筆", "[JEI scan] useful " + useful);
        if (skipped > 0) {
            s += pick(code, "；略過通用 " + skipped + " 筆", "; skipped generic " + skipped);
        }
        return s + "\n";
    }

    public static String jeiTruncated(String code, int useful) {
        return pick(code,
                "…\n（文字過長已截斷；有用 " + useful + " 筆，請在 JEI 查看細節）",
                "…\n(truncated; " + useful + " useful — see JEI for details)");
    }

    public static String jeiSkipped(String code, String cat, int n, String reason) {
        return pick(code,
                "• [" + cat + "] 略過 " + n + " 筆（" + reason + "）\n",
                "• [" + cat + "] skipped " + n + " (" + reason + ")\n");
    }

    public static String jeiCatCount(String code, String cat, int useful, Integer unique, int spam, boolean hitCap, int maxScan) {
        StringBuilder section = new StringBuilder();
        section.append(pick(code, "• [" + cat + "] 共 " + useful + " 筆", "• [" + cat + "] " + useful + " recipes"));
        if (unique != null) {
            section.append(pick(code, "（去重後 " + unique + " 種）", " (" + unique + " unique)"));
        }
        if (spam > 0) {
            section.append(pick(code, "；另略過通用 " + spam + " 筆", "; also skipped " + spam + " generic"));
        }
        if (hitCap) {
            section.append(pick(code, "（已達單分類掃描上限 " + maxScan + "）", " (hit per-category scan cap " + maxScan + ")"));
        }
        section.append('\n');
        return section.toString();
    }

    public static String jeiNoMats(String code) {
        return pick(code, "（無材料）", "(no inputs)");
    }

    public static String jeiNoOut(String code) {
        return pick(code, "（無產物）", "(no outputs)");
    }

    public static String jeiMachineLine(String code, String cats, String in, String out) {
        return pick(code,
                "機器" + quote(code, cats) + "： " + in + " → " + out,
                "Machine " + quote(code, cats) + ": " + in + " → " + out);
    }

    public static String spamSkipLabel(String code) {
        return pick(code,
                "外觀／包覆／Framed 類（facade、framed_*、cover 等，幾乎套用所有方塊）",
                "cosmetic/facade/framed covers (apply to almost every block)");
    }

    public static String craftPreferenceHint(String code) {
        return pick(code,
                "推薦合成優先順序：工作台 > 切石 > 熔爐/高爐 > 煙燻/營火 > 機械加工 > 自動攪拌機 > Minecolonies 市民；同類多條路線時優先高速/產量高的。",
                "Prefer craft routes: crafting table > stonecutter > furnace/blast > smoker/campfire > machines > auto stirrer > Minecolonies citizens; when tied prefer faster/higher yield.");
    }

    public static String[] seasonSubs(String code) {
        if (isChinese(code)) {
            return new String[] {
                    "初春", "仲春", "晚春", "初夏", "仲夏", "晚夏",
                    "初秋", "仲秋", "晚秋", "初冬", "仲冬", "晚冬"
            };
        }
        return new String[] {
                "Early Spring", "Mid Spring", "Late Spring", "Early Summer", "Mid Summer", "Late Summer",
                "Early Autumn", "Mid Autumn", "Late Autumn", "Early Winter", "Mid Winter", "Late Winter"
        };
    }

    public static String seasonSerene(String code, String subName, long day) {
        return pick(code,
                "【季節】Serene Seasons 估算：" + subName + "（第 " + day + " 天；種植請對照遊戲內季節日曆）\n",
                "[Season] Serene Seasons estimate: " + subName + " (day " + day + "; check in-game season calendar)\n");
    }

    public static String seasonFarmersDelight(String code) {
        return pick(code,
                "【季節】Farmer's Delight 部分作物受季節影響；請結合上方季節說明可否種植。\n",
                "[Season] Some Farmer's Delight crops are seasonal; use the season above for plantability.\n");
    }

    public static String psiPromptAddon(String code) {
        return pick(code,
                "【Psi】玩家想設計 Psi 術式：用與回答相同的語言說明 trick 組合思路（向量、實體、運動、偵測等），"
                        + "列出建議的 trick 名稱與順序；提醒在 CAD 中組裝與測試 PSI 消耗。"
                        + "勿捏造不存在的 trick 名稱；不確定時建議查 JEI 的 Psi 分類。",
                "[Psi] Player wants a Psi spell: explain trick composition (vectors, entities, motion, sensors) "
                        + "in the same language as the answer; list suggested trick names/order; remind to assemble in CAD and test PSI cost. "
                        + "Do not invent nonexistent tricks; if unsure, suggest JEI Psi categories.");
    }

    public static String sourcesInstruction(String code) {
        if (isChinese(code)) {
            return "每則回答結尾必須另起一行寫【來源】，列出你實際引用的資料（至少一項），"
                    + "例如：JEI、整合包任務書、整合包本地配方、網搜（模組資料）、AI 推論；不可省略【來源】。"
                    + "【來源】標籤與標籤內容都必須使用與主文相同的語言。";
        }
        return "End every answer with a new line starting with [Sources], listing what you used "
                + "(at least one), e.g. JEI, pack quest book, pack local recipes, web search (mod docs), AI inference. "
                + "Do not omit [Sources]. Write the [Sources] header and labels in the same language as the answer body.";
    }

    public static String llmStyle(String code) {
        if (isChinese(code)) {
            return "主文必須白話（作法／材料／步驟），給 Minecraft 遊戲內純文字顯示。"
                    + "絕對禁止：emoji／表情符號、Markdown（不要用 # ** ` - 列表標題）、物品ID、檔案路徑、KubeJS／腳本、JSON。"
                    + "材料與物品只用可讀名稱。"
                    + craftPreferenceHint(code)
                    + "若有 jei 欄位：優先用它說明合成配方與用途（等同遊戲內 JEI 按 R／U），以及「作為機器／工作站」的特殊配方（JEI 催化劑）；"
                    + "JEI 列表已依推薦順序排序。"
                    + "若資料含本地獲取／掉落／釣魚／交易／腳本配方：必須一併說明 JEI 可能沒列出的取得方式；與 JEI 衝突時標明來源差異。"
                    + "若出現「壓縮循環」或 9 合 1 磚塊再拆回材料：那只是收納互轉，絕對不要當成主要取得／合成進度；除非玩家在問壓縮或空間。"
                    + "提到任務時只用任務名稱／章節名稱，絕對不要寫出任務 ID（例如一長串十六進位）。"
                    + "推薦物品時，回答最末另起一行機器標記（勿寫進正文）：<!--packai:items=mod:id,mod:id2--> 使用 registry id；"
                    + "同名不同模組的物品請都列出以便玩家辨識。"
                    + "若有【網搜】：只可引用其中與 Minecraft／模組相關的內容；與 JEI／任務書／本地腳本衝突時一律以本地為準，並可提醒整合包可能已魔改。"
                    + sourcesInstruction(code);
        }
        return "Write plain steps (how / materials / steps) for in-game Minecraft text. "
                + "Never use emoji, Markdown (# ** ` - headers), item IDs, file paths, KubeJS/scripts, or JSON. "
                + "Use readable item names only. "
                + craftPreferenceHint(code)
                + " If jei is present: prefer it for recipes/uses (like JEI R/U) and catalyst/machine recipes; JEI list is already preference-sorted. "
                + "If local acquire/loot/fish/trade/script facts exist, include obtain paths JEI may miss; note conflicts with JEI. "
                + "Compression 9↔1 packing is storage only — never treat as main obtain/progression unless asked. "
                + "For quests use quest/chapter names only — never hex quest IDs. "
                + "When recommending items, end with machine marker only: <!--packai:items=mod:id,mod:id2-->. "
                + "List same-name items from different mods. "
                + "If [Web] is present: only Minecraft/mod content; local JEI/quests/scripts win on conflict. "
                + sourcesInstruction(code);
    }

    public static String llmRules(String code, boolean questOverride, boolean questConflict, String policy) {
        if (questOverride) {
            return pick(code,
                    "玩家表示任務書有誤：依本地事實回答，標明任務可能有誤。",
                    "Player says the quest book is wrong: answer from local facts and mark that the quest may be wrong.");
        }
        if (questConflict) {
            return pick(code, "任務可能過時：以本地魔改為準。", "Quest may be outdated: prefer pack modifications.");
        }
        if ("local_only".equals(policy)) {
            return pick(code,
                    "此物品／題目有本地覆寫：以本地／圖事實為準，勿用通用 wiki 覆蓋；忽略與本地衝突的網搜。",
                    "This topic has local overrides: prefer local/graph facts; ignore conflicting web results.");
        }
        if ("mixed".equals(policy)) {
            return pick(code,
                    "整合包可能只魔改部分內容；此題目未見針對該物品的本地覆寫，可參考網搜模組資料，仍以 JEI／本地為準。",
                    "The pack may only modify some content; no local override for this item — web mod docs ok, but JEI/local win.");
        }
        return pick(code,
                "可結合常識與網搜模組資訊，但與本地衝突時以本地為準。",
                "You may use common sense and web mod info, but local data wins on conflict.");
    }

    public static String llmSystemLead(String code, String langName) {
        return pick(code,
                "你是 Minecraft 整合包助手。請用" + langName + "回答（遊戲語系：" + code + "）。"
                        + "無論問題或對話歷史是什麼語言，主文與【來源】都必須用" + langName + "。"
                        + "若有先前對話，請延續上下文回答。",
                "You are a Minecraft modpack assistant. Answer in " + langName + " (game language: " + code + "). "
                        + "Regardless of the language of the question or chat history, write the answer body and [Sources] in "
                        + langName + ". "
                        + "Continue prior conversation context when present.");
    }

    /** Fact-check discipline for the LLM system prompt. */
    public static String factCheck(String code) {
        if (isChinese(code)) {
            return "你必須先辨識玩家問的物品：優先使用 question／heldItem.id／jei 標題中的 registry id 與可讀名稱。"
                    + "若已明確知道是哪個物品（有 id 或明確名稱），不要回答「無法確定是什麼物品」。"
                    + "事實檢查：不可捏造整合包獨有配方／任務；但若 jei 有資料，必須依 jei 說明。"
                    + "若 jei／本地沒有該物品的整合包覆寫，可用原版／該模組的標準知識說明用途與常見取得方式，並標明「通用知識（非本包覆寫）」。"
                    + "只有在連物品身份都無法從 question／heldItem／jei 辨識時，才說「無法確定」。"
                    + "提到任務只用任務名稱，不要寫任務 ID。"
                    + "最終原則：寧可標明依據不足，不可捏造本包獨有內容；但已知物品不可裝傻。";
        }
        return "First identify the item from question / heldItem.id / JEI header (registry id + name). "
                + "If the item identity is clear, never say you cannot tell what the item is. "
                + "Do not invent pack-unique recipes or quests. If JEI data is present, follow it. "
                + "If JEI/local has no pack override for that item, you may use standard vanilla/mod knowledge "
                + "for uses and common obtain methods, labeled as general knowledge (not a pack override). "
                + "Only say uncertain when the item identity itself cannot be resolved from the request. "
                + "Use quest names only, never quest IDs. "
                + "Blank beats fabricating pack-unique content, but do not pretend a known item is unknown.";
    }

    public static String llmApiKeyHint(String code, int keyLen) {
        return pick(code,
                "\n提示：curl 若成功，請直接改實例 config/packai-client.toml 的 llm.apiKey（完整 sk-…），"
                        + "或設環境變數 PACKAI_API_KEY；遊戲內設定框可能存錯。目前 key 長度=" + keyLen,
                "\nTip: if curl works, set llm.apiKey in config/packai-client.toml (full sk-…), "
                        + "or PACKAI_API_KEY; the in-game field may be wrong. Current key length=" + keyLen);
    }

    public static String humanAcquireLabel(String code, String rel) {
        if (rel == null || rel.isBlank()) {
            return packData(code);
        }
        String pl = rel.replace('\\', '/');
        String lower = pl.toLowerCase(Locale.ROOT);
        String name = pl;
        int slash = pl.lastIndexOf('/');
        if (slash >= 0 && slash < pl.length() - 1) {
            name = pl.substring(slash + 1);
        }
        name = name.replaceFirst("\\.[^.]+$", "").replace('_', ' ');
        String kind;
        if (PackIndex.isFishingPath(lower)) {
            kind = fishingKind(code);
        } else if (PackIndex.isLootPath(lower)) {
            kind = lootKind(code);
        } else {
            kind = tradeKind(code);
        }
        return kind + quote(code, name);
    }

    public static String jeiSkippedGeneric(String code, String catTitle, int n) {
        return pick(code,
                "• [" + catTitle + "] 略過 " + n + " 筆通用配方\n",
                "• [" + catTitle + "] skipped " + n + " generic recipes\n");
    }
}
