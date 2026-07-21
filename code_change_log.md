# 代碼變更與問題日誌

## [2026-07-21 16:15:00] 操作類型：修改
- **文件路徑**：README.md、code_change_log.md
- **變更摘要**：更新 README：Y 鍵按住思考、進度條、來源標示、重新生成等完整功能說明
- **遇到的問題**：無
- **備註**：隨 git push 一併提交

## [2026-07-21 16:10:00] 操作類型：修改
- **文件路徑**：ThinkProgressBar、PackAiTooltipHandler、ThinkHoldTracker、ClientSetup、lang、code_change_log.md
- **變更摘要**：進度條改在 tooltip 下方直接繪製（JEI 可用）；恢復僅按住 Y 觸發，移除按一下
- **遇到的問題**：
  - 問題1：JEI tooltip 不走 GatherComponents，進度條不顯示
  - 解決方案：RenderTooltipEvent.Post 手動繪製分段條
  - 狀態：✅ 已解決
- **備註**：助手畫面內仍可用 Y 即時 JEI 思考

## [2026-07-21 16:05:00] 操作類型：修改
- **文件路徑**：ClientSetup、JeiTargetResolver、TooltipHover、PackAiTooltipHandler、lang、code_change_log.md
- **變更摘要**：修復 Y 鍵無反應：tooltip 追蹤懸停物品、按一下 Y 也可觸發、強化 JEI 懸停與按鍵偵測、失敗時 toast 提示
- **遇到的問題**：
  - 問題1：僅按住 Y 且 JEI API 懸停失敗時完全無反應
  - 解決方案：TooltipHover 備援 + consumeClick 單次觸發 + 提示訊息
  - 狀態：✅ 已解決
- **備註**：須在 GUI（背包/JEI）內使用，非遊戲中空手

## [2026-07-21 16:00:00] 操作類型：新增 | 修改
- **文件路徑**：ReplySources.java、AskEngine.java、LlmClient.java、RoadmapChecks.java、code_change_log.md
- **變更摘要**：每則 AI 回覆強制顯示【來源】；LLM 若漏寫則依 JEI／任務書／本地腳本／網搜等自動補上
- **遇到的問題**：無
- **備註**：API 設定錯誤等系統訊息不附加來源行

## [2026-07-21 15:56:00] 操作類型：修改
- **文件路徑**：ClientSetup.java、code_change_log.md
- **變更摘要**：JEI／背包「按住思考」預設鍵由 F 改為 Y
- **遇到的問題**：無
- **備註**：tooltip 會自動顯示目前綁定的鍵名

## [2026-07-21 15:52:00] 操作類型：新增 | 修改
- **文件路徑**：ThinkHoldTracker、ThinkProgressTooltip、PackAiTooltipHandler、ClientSetup、JeiTargetResolver、AiAssistantScreen、AskService、lang、code_change_log.md
- **變更摘要**：Create Ponder 風格：物品 tooltip「按住 F」提示 + 分段進度條，滿格自動開啟助手並提問
- **遇到的問題**：
  - 問題1：TooltipComponent 不能加進 ItemTooltipEvent 文字列表
  - 解決方案：改用 RenderTooltipEvent.GatherComponents + RegisterClientTooltipComponentFactoriesEvent
  - 狀態：✅ 已解決
- **備註**：助手內「JEI 思考」按鈕仍為即時觸發；換 jar 前請關閉遊戲

## [2026-07-21 15:35:00] 操作類型：新增 | 修改
- **文件路徑**：AiAssistantScreen、ChatSession、ChatMessage、AskResult、ItemResolver、JeiTargetResolver、CraftPriority、SeasonContext、PsiHelper、AskService、JeiLookup、LlmClient、ClientSetup、lang、RoadmapChecks、code_change_log.md
- **變更摘要**：待辦路線圖實作：重新生成、推薦物品圖示、空手/JEI 思考、合成優先排序、季節提示、Psi 術式提示
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：DPS 估算仍略過；Psi 僅 LLM 描述術式未寫入 CAD

## [2026-07-21 15:24:00] 操作類型：修改 | 新增
- **文件路徑**：JeiUniversalSpam.java、JeiLookup.java、JeiSpamFilterCheck.java、code_change_log.md
- **變更摘要**：擴大 JEI 通用配方過濾：ae2:facade、framedblocks:framed_*、cover／camo／disguise 等，不只 integrateddynamics facade
- **遇到的問題**：
  - 問題1：每個方塊都掛 facade／framed／cover 用途，淹沒真正配方
  - 解決方案：集中 JeiUniversalSpam 路徑規則＋分類關鍵字；80% 產出為 spam 時整類略過
  - 狀態：✅ 已解決
- **備註**：chipped 等真實雕刻方塊不過濾

## [2026-07-21 15:21:00] 操作類型：修改 | 新增
- **文件路徑**：JeiLookup.java、JeiSpamFilterCheck.java、code_change_log.md
- **變更摘要**：略過通用 Facade 類配方（如 integrateddynamics:facade 套用幾乎所有方塊），避免洗版
- **遇到的問題**：
  - 問題1：JEI 對每個方塊都掛 facade 用途／配方，淹沒真正機器配方
  - 解決方案：依 item id／category uid 過濾；同類大量相同產出也整類略過並註明
  - 狀態：✅ 已解決
- **備註**：path 為 facade、*_facade、facade/*

## [2026-07-21 15:17:55] 操作類型：修改
- **文件路徑**：JeiLookup.java、code_change_log.md
- **變更摘要**：JEI 改為完整掃描各分類全部配方（R／U／催化劑），去重後交給 LLM；單分類上限 2000、輸出約 12k 字防卡頓
- **遇到的問題**：
  - 問題1：先前每分類只取樣數筆，特殊機器列表不完整
  - 解決方案：去掉 per-cat 取樣上限；去重＋字數截斷並標明掃描合計
  - 狀態：✅ 已解決
- **備註**：單分類超過 2000 筆會註明可能還有更多（防客戶端卡死）

## [2026-07-21 15:16:00] 操作類型：修改
- **文件路徑**：JeiLookup.java、LlmClient.java、code_change_log.md
- **變更摘要**：JEI 查詢補上 CATALYST（機器／工作站）角色，並提高特殊配方取樣上限，避免漏掉 Environmental Accumulator 等機器轉換
- **遇到的問題**：
  - 問題1：只查 INPUT／OUTPUT，機器配方在 JEI 屬催化劑，AI 只看到自動攪拌用途
  - 解決方案：新增 asCatalyst 區段；機器類每分類最多 8 筆；prompt 提醒機器配方
  - 狀態：✅ 已解決
- **備註**：仍只取樣部分配方並提示玩家用 JEI 看完整列表

## [2026-07-21 15:06:00] 操作類型：修改
- **文件路徑**：ChatMessage.java、ChatSession.java、AiAssistantScreen.java、code_change_log.md
- **變更摘要**：手上物品改顯示在輸入框上方，並寫入每則使用者訊息（歷史顯示 [物品]＋圖示）
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：ChatMessage 新增 heldItemLabel／heldItemId

## [2026-07-21 15:03:00] 操作類型：修改
- **文件路徑**：AiAssistantScreen.java、ChatSession.java、lang/*.json、code_change_log.md
- **變更摘要**：助手畫面標題下顯示手上物品圖示與名稱；等待回覆時凍結為「詢問中」物品
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：空手顯示「（空）」

## [2026-07-21 14:59:13] 操作類型：修改
- **文件路徑**：AiAssistantScreen.java、code_change_log.md
- **變更摘要**：精簡助手主畫面按鈕：移除模式／模型／重整（設定頁已有）、手上怎麼用（與手上下一步重複）、任務書不對（輸入同句即可觸發）
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：保留 提問／清除／手上下一步／任務下一步

## [2026-07-21 14:54:25] 操作類型：修改 | 新增
- **文件路徑**：ChatSession.java、AiAssistantScreen.java、ReplyNotifier.java、QuestBookOpener.java、AiClientCommands.java、lang/*.json、code_change_log.md
- **變更摘要**：等待回覆時關閉 GUI → 遊戲內 toast／聊天提醒；任務連結存 session，重開可點；聊天可點開任務
- **遇到的問題**：
  - 問題1：回覆回呼寫在已關閉的 Screen，questLinks 丟失
  - 解決方案：busy／lastQuests 存 ChatSession；關 GUI 時 ReplyNotifier；/packai quest 與點擊事件
  - 狀態：✅ 已解決
- **備註**：勿在遊戲開啟時覆蓋 jar（會導致語系 ZLIB EOF）

## [2026-07-21 14:41:00] 操作類型：修改 | 新增
- **文件路徑**：QuestGuide.java、AskEngine.java、QuestLocalePreferCheck.java、code_change_log.md
- **變更摘要**：任務標題跟隨 Minecraft 語系；略過非偏好／非英語的 FTB lang，避免西語因字串較長蓋過英語
- **遇到的問題**：
  - 問題1：多語系合併時 es_* 與 en_* 同分，betterTitle 選較長字串 → 西語勝出
  - 解決方案：依 client language 計分；只索引偏好語系族＋en_*；AskEngine 傳入 replyLang
  - 狀態：✅ 已解決
- **備註**：遊戲設為西語時仍會顯示西語

## [2026-07-21 14:40:00] 操作類型：修改 | 新增
- **文件路徑**：QuestGuide.java、AiAssistantScreen.java、AskEngine.java、LlmClient.java、QuestDisplayNameCheck.java、code_change_log.md
- **變更摘要**：任務顯示改用可讀名稱（displayTitle），禁止用 hex 任務 ID 當標題
- **遇到的問題**：
  - 問題1：無標題時用 quest id 當按鈕／摘要文字
  - 解決方案：displayTitle 優先 lang／翻譯鍵葉節／物品名；LLM 禁止輸出任務 ID
  - 狀態：✅ 已解決
- **備註**：open_book 仍用內部 questId，僅對玩家隱藏

## [2026-07-21 14:35:00] 操作類型：修改
- **文件路徑**：AskEngine.java、WebSearch.java、LlmClient.java、PackAiConfig.java、PartialPackPolicyCheck.java、README、code_change_log.md
- **變更摘要**：部分魔改整合包改為依「此物品是否被本地覆寫」決定網搜；新增 mixed 政策
- **遇到的問題**：
  - 問題1：整包有 kubejs 就一律 local_only，未魔改的模組也無法網搜
  - 解決方案：isHeldLocallyTouched；未覆寫 → mixed／online_ok 仍可網搜
  - 狀態：✅ 已解決
- **備註**：與此物品相關的 remove／腳本配方／snippet 仍擋網搜

## [2026-07-21 14:30:00] 操作類型：新增 | 修改
- **文件路徑**：WebSearch.java、PackAiConfig.java、AskEngine.java、LlmClient.java、WebSearchCheck.java、README、code_change_log.md
- **變更摘要**：mod 內網搜（Tavily／Serper）；只保留 Minecraft mod 相關結果；僅 online_ok 時啟用
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：無新 Maven 依賴；local_only／offline 不網搜；非 mod 網域結果丟棄

## [2026-07-21 14:25:00] 操作類型：修改
- **文件路徑**：PackIndex.java、AskEngine.java、LlmClient.java、AcquireFactsCheck.java、code_change_log.md
- **變更摘要**：辨識材料↔磚塊壓縮循環；降權／標註「不是主要取得方式」，避免誤導 AI
- **遇到的問題**：
  - 問題1：互轉配方被當成主要取得路徑
  - 解決方案：雙向 recipe_needs 或 unit←storage 視為壓縮循環；餵 LLM 時略過循環邊並加 prompt
  - 狀態：✅ 已解決
- **備註**：磚塊由材料合成仍保留為正常配方

## [2026-07-21 14:15:00] 操作類型：修改
- **文件路徑**：PackIndex.java、AskEngine.java、LlmClient.java、AcquireFactsCheck.java、README、code_change_log.md
- **變更摘要**：本地獲取辨識釣魚 loot（fishing／fisherman），事實標為「釣魚」並餵 LLM
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：路徑含 fishing 時用 -[fish]->，優先於一般掉落標籤

## [2026-07-21 14:10:00] 操作類型：修改 | 新增
- **文件路徑**：PackIndex.java、AskEngine.java、LlmClient.java、AcquireFactsCheck.java、README、code_change_log.md
- **變更摘要**：JEI 以外補本地獲取：loot table／villager 交易索引 + shaped 腳本圖；acquireFactsFor 餵 LLM
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：不掃 mod jar；openloader/data 一併索引

## [2026-07-21 14:00:00] 操作類型：修改
- **文件路徑**：LlmClient.java、code_change_log.md
- **變更摘要**：system prompt 加入事實檢查思考規則（寧可空白不可捏造）
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：來源限定為本請求欄位與對話歷史；遊戲內仍禁 emoji／Markdown

## [2026-07-21 13:58:00] 操作類型：修改 | 新增
- **文件路徑**：AskService.java、AskEngine.java、LlmClient.java、ReplyLangCheck.java、code_change_log.md
- **變更摘要**：LLM 回覆跟隨 Minecraft 遊戲語系（zh_tw／en_us 等），不再寫死繁體中文
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：AskService 讀 LanguageManager.getSelected()；system prompt + user.replyLanguage

## [2026-07-21 13:50:00] 操作類型：修改 | 新增
- **文件路徑**：QuestGuide.java、QuestGuideIdCheck.java、code_change_log.md
- **變更摘要**：修正「開啟任務」跳錯題：改從 quests 陣列／lang 的 quest.HEX.title 取真正任務 ID，不再用 nearestId 誤抓 task／reward／上一則
- **遇到的問題**：
  - 問題1：按鈕標題對但 open_book 開到隨機／上一則任務
  - 解決方案：解析 `quests: [` 物件深度-1 的 id；lang 用 `quest.HEX.title`；同 ID 合併並偏好 zh/en 標題
  - 狀態：✅ 已解決
- **備註**：ATM10 實檔 smoke：Andesite Alloys → 0F16498769DFB3B0；QuestGuideIdCheck OK

## [2026-07-21 13:40:00] 操作類型：新增 | 修改
- **文件路徑**：ChatMessage.java、ChatSession.java、AiAssistantScreen.java、AskService/AskEngine/LlmClient、ClientSetup、語系、README
- **變更摘要**：多輪聊天 UI（可捲動歷史＋底部輸入）；最近 8 則送給 LLM；清除對話／登出清空
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：不寫檔；waiting 用 replaceLastAssistant

## [2026-07-21 13:26:00] 操作類型：新增 | 修改
- **文件路徑**：ModelPickerScreen.java、AiAssistantScreen.java、PackAiSettingsScreen.java、zh_tw.json、en_us.json
- **變更摘要**：新增可搜尋的模型選擇 GUI；助手／設定頁改為開啟選擇器取代 CycleButton
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：列表可滾輪；重整鈕在選擇器內

## [2026-07-21 13:21:59] 操作類型：修改 | 新增
- **文件路徑**：Plainify.java、AskResult.java、LlmClient.java、MinecraftUiTextCheck.java
- **變更摘要**：回答顯示前剝除 emoji 與簡易 Markdown；提示詞禁止 emoji／Markdown（MC 字型無法顯示）
- **遇到的問題**：
  - 問題1：AI 回覆含 emoji／## ** 在遊戲內變方塊或缺字元
  - 解決方案：forMinecraftUi 過濾；AskResult 出口統一清理
  - 狀態：✅ 已解決
- **備註**：jar 已重建

## [2026-07-21 13:14:30] 操作類型：修改
- **文件路徑**：ModelCatalog.java、PackAiSettingsScreen.java、AiAssistantScreen.java
- **變更摘要**：修復開設定／助手時模型 refresh→rebuildWidgets→init 無限迴圈導致遊戲「沒有回應」
- **遇到的問題**：
  - 問題1：快取未過期時 refreshAsync 仍立刻呼叫 onClientDone→rebuild→init→再 refresh
  - 解決方案：新鮮快取不再觸發 callback；每畫面只自動 refresh 一次（autoRefreshScheduled）
  - 狀態：✅ 已解決
- **備註**：手動「重整」仍可 force 拉取

## [2026-07-21 13:11:00] 操作類型：修改
- **文件路徑**：LlmClient.java、ModelCatalog.java、PackAiSettingsScreen.java、README.md
- **變更摘要**：normalizeApiBaseUrl 自動剝掉誤貼的 /chat/completions，避免 OpenRouter 等變成雙路徑 404
- **遇到的問題**：
  - 問題1：使用者把 apiBaseUrl 填成 https://openrouter.ai/api/v1/chat/completions，程式再加 /chat/completions → HTTP 404
  - 解決方案：儲存／請求前正規化 base；設定頁 hint 改為只要到 /v1
  - 狀態：✅ 已解決
- **備註**：OpenRouter 模型 id 通常為 deepseek/… 而非 deepseek-v4-pro

## [2026-07-21 13:05:00] 操作類型：修改
- **文件路徑**：README.md
- **變更摘要**：更新說明：JEI R/U、完整 tooltip、動態模型清單／重整鈕、任務命中仍呼叫 LLM、可選 Mixin
- **遇到的問題**：
  - 問題1：舊 README 仍寫「無 Mixin、無 JEI」已過時
  - 解決方案：改寫玩家步驟與行為／相容章節
  - 狀態：✅ 已解決
- **備註**：無

## [2026-07-21 13:02:30] 操作類型：修改
- **文件路徑**：AiAssistantScreen.java、PackAiSettingsScreen.java、ModelCatalog.java、zh_tw.json、en_us.json
- **變更摘要**：模型列旁新增「重整」按鈕，強制清除快取並重新拉取 Cloud／Ollama 模型清單
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：offline 時按鈕停用

## [2026-07-21 12:58:54] 操作類型：新增 | 修改
- **文件路徑**：ModelCatalog.java、AiAssistantScreen.java、PackAiSettingsScreen.java、ModelCatalogCheck.java
- **變更摘要**：模型清單改為自動從 Cloud `/models` 與 Ollama `/api/tags` 拉取（快取 5 分鐘），失敗則用內建 fallback
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：開 GUI／存 key／改 mode 會 refresh；過濾 embedding 等非對話模型

## [2026-07-21 12:47:17] 操作類型：新增 | 修改
- **文件路徑**：JeiLookup.java、PackAiJeiPlugin.java、AskService/AskEngine/LlmClient、build.gradle、neoforge.mods.toml、gradle.properties
- **變更摘要**：可選整合 JEI：查手上物品配方（R）與用途（U），摘要優先餵給 LLM
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：JEI optional；進世界等 JEI runtime 就緒後才有資料。jar: packai-0.1.0.jar

## [2026-07-21 12:27:19] 操作類型：新增 | 修改
- **文件路徑**：TooltipCapture.java、ScreenMixin.java、packai.mixins.json、neoforge.mods.toml、GameContextCollector.java、AskService.java、ItemRef.java
- **變更摘要**：詢問時展開完整物品 tooltip（假裝 Shift/Ctrl/Alt），把玩家可見描述（含按鍵才顯示的行）送給 LLM
- **遇到的問題**：
  - 問題1：許多模組用 Screen.hasShiftDown() 才附加說明，只抓 hoverName 不夠
  - 解決方案：Mixin 在 TooltipCapture 期間回傳 true；主執行緒 collect 後再 async ask
  - 狀態：✅ 已解決
- **備註**：少數模組直接讀 GLFW 按鍵狀態仍可能抓不到；上限約 900 字

## [2026-07-21 12:24:39] 操作類型：修改
- **文件路徑**：ItemRef.java、LlmClient.java、GameContextCollector.java、AskService.java、ItemRefCheck.java
- **變更摘要**：LLM 只送玩家畫面上的 hover 文字（heldItem／hotbar 字串）；拿掉 tags／categories
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：內部比對仍用 registry id；jar 已重建

## [2026-07-21 12:22:37] 操作類型：新增 | 修改
- **文件路徑**：ItemRef.java、GameContextCollector.java、AskService.java、AskEngine.java、LlmClient.java、PackIndex.java、ItemRefCheck.java
- **變更摘要**：物品改傳 hover 名稱 + item tags（分類）給檢索與 LLM，不再只送 registry id 轉名
- **遇到的問題**：
  - 問題1：同 id 靠 NBT／變體名稱不同時 AI 無法分辨
  - 解決方案：ItemRef(id, displayName, tags)；LLM 收 name+categories；retrieve 加 hintTokens
  - 狀態：✅ 已解決
- **備註**：未傾倒完整 Data Components（藥水／附魔等）；需要時再擴。jar: packai-0.1.0.jar

## [2026-07-21 12:09:16] 操作類型：修改
- **文件路徑**：mod/src/main/java/com/skps9/packai/logic/AskEngine.java
- **變更摘要**：非 offline 時任務命中不再短路跳過 LLM；改將任務摘要餵給 API，成功後仍附開啟任務按鈕
- **遇到的問題**：
  - 問題1：畫面出現【任務導引】+ raw key，使用者以為 API 壞了
  - 解決方案：查 AskEngine：`!questHits.isEmpty() && !override` 會直接 return formatGuide；改為僅 offline 才短路，online 必呼叫 llm.ask
  - 狀態：✅ 已解決
- **備註**：API 失敗時仍回退任務書摘要並加提示；高信心配方短路僅在無任務命中時

## [2026-07-21 11:57:30] 操作類型：修改
- **文件路徑**：mod/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java、mod/src/test/java/.../AssistantLayoutCheck.java
- **變更摘要**：覆核版面邏輯：inputY 永不依任務數；任務按鈕貼輸入框往上排；無任務不預留空槽
- **遇到的問題**：
  - 問題1：先前固定預留 3 槽導致無任務時空洞，且按鈕從槽頂排會離底偏遠
  - 解決方案：questStrip 依實際數量；qy 從 inputY-questStrip 起算；inputY 只由 bottomStack 決定
  - 狀態：✅ 已解決
- **備註**：答案區高度會隨任務按鈕出現而縮，但底部控制列座標不變

## [2026-07-21 11:52:40] 操作類型：修改
- **文件路徑**：mod/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java
- **變更摘要**：輸入框與操作按鈕固定貼底；任務槽只佔答案區下方，不再把 bottomStack 往上推
- **遇到的問題**：
  - 問題1：先前 questBlock 同時加進 bottomStack，輸入區離螢幕底偏高
  - 解決方案：bottomStack 只含輸入+4 列按鈕；questBlock 只縮 answerBottom
  - 狀態：✅ 已解決
- **備註**：接續 11:51 防跳動修正

## [2026-07-21 11:51:22] 操作類型：修改
- **文件路徑**：mod/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java
- **變更摘要**：回答出現時固定預留 3 個任務按鈕槽位，避免 GUI 因 questBlock 高度變化而跳動
- **遇到的問題**：
  - 問題1：有答案且含任務連結時 rebuildWidgets 會依 questCount 重算 bottomStack，輸入框與按鈕整排上移
  - 解決方案：MAX_QUEST_SLOTS=3 恆預留高度；無任務時留空白槽、有任務才放按鈕
  - 狀態：✅ 已解決
- **備註**：compileJava SUCCESS

## [2026-07-20 21:05:02] 操作類型：新增
- **文件路徑**：mod/、bridge/、code_change_log.md、計畫文件
- **變更摘要**：建立僅客戶端 Pack AI 骨架；Bridge 依已載入 mod list 裁剪索引/搜尋以提升效率
- **遇到的問題**：
  - 問題1：跨專案寫入 Earth Online App 日誌需核准
  - 解決方案：核准後寫入；本專案同步保留日誌
  - 狀態：✅ 已解決
- **備註**：快捷鍵為主、/ai 備援；NeoForge 1.21.1

## [2026-07-20 21:11:57] 操作類型：新增
- **文件路徑**：mod/src/.../packai/**、bridge/**
- **變更摘要**：實作 client-only Pack AI；請求帶 modIds；Bridge 依模組列表開關掃描器並聚焦搜尋
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：test_mod_filter.py 通過；gradle compileJava BUILD SUCCESSFUL

## [2026-07-20 21:25:13] 操作類型：修改
- **文件路徑**：bridge/rag.py、mod_filter.py、main.py、llm.py；mod/.../GameContextCollector.java、AskService.java、ClientSetup.java、AiAssistantScreen.java；bridge/test_rag_eff.py
- **變更摘要**：路徑先裁+倒排+按需讀檔；fingerprint 瘦身 context；高信心跳過 LLM；進世界 warmup
- **遇到的問題**：
  - 問題1：把整個 kubejs/datapacks 當 pack-global 會讓 focus 失效
  - 解決方案：focus 模式下只留路徑含 focus mod 或 overrides/readme；否則才退回整棵 pack tree
  - 狀態：✅ 已解決
- **備註**：test_mod_filter / test_rag_eff 通過；compileJava SUCCESS

## [2026-07-20 21:37:18] 操作類型：新增
- **文件路徑**：bridge/pack_modified.py、web_search.py；修改 main.py、llm.py、.env.example、README.md、test_pack_modified.py
- **變更摘要**：魔改偵測決定 local_only / online_ok；Tavily/Serper 網搜；軟退回附警告
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：test_pack_modified / test_mod_filter / test_rag_eff 通過

## [2026-07-21 01:55:44] 操作類型：新增
- **文件路徑**：bridge/plainify.py、quests.py、pack_graph.py；main.py、llm.py、rag.py；mod ClientSetup/AskService/AiAssistantScreen；tests；README
- **變更摘要**：白話配方、FTB任務導引（卡住不override／有錯才override／最多3筆）、Pack Graph降token、雙warmup、相容約束
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：全部 bridge 自檢通過；compileJava SUCCESS

## [2026-07-21 07:30:56] 操作類型：新增 | 修改
- **文件路徑**：mod/src/main/java/com/skps9/packai/logic/*、AskService.java、PackAiConfig.java、README.md
- **變更摘要**：改為單一客戶端 JAR：AskEngine 內建索引/任務/plainify/雙後端 LLM，移除對 Python Bridge 的依賴
- **遇到的問題**：
  - 問題1：無（compileJava BUILD SUCCESSFUL）
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：bridge/ 僅供參考；玩家只需 mods 內 jar + 選用 API key 或 Ollama

## [2026-07-21 08:01:14] 操作類型：修改
- **文件路徑**：mod/.../LlmClient.java、PackAiConfig.java、README.md
- **變更摘要**：curl 可用但模組 401：清理 apiKey（引號/Bearer/空白）、支援 PACKAI_API_KEY；401 提示改 toml
- **遇到的問題**：
  - 問題1：遊戲內設定框存的 key 與 curl 成功的 key 不一致／損壞
  - 解決方案：sanitize + env 覆寫 + 建議直接改 packai-client.toml
  - 狀態：✅ 已解決
- **備註**：請使用者重編 jar 後改 toml 或設環境變數

## [2026-07-21 08:07:40] 操作類型：修改
- **文件路徑**：mod/.../AiAssistantScreen.java
- **變更摘要**：答案區改為可滾輪捲動全高面板，移除只顯示 6 行的截斷
- **遇到的問題**：
  - 問題1：長回覆被 maxLines=6 截成 …
  - 解決方案：scissor + scrollOffset，輸入與按鈕下移
  - 狀態：✅ 已解決
- **備註**：需換成新 jar

## [2026-07-21 08:14:43] 操作類型：修改
- **文件路徑**：mod/.../PackAiConfig.java、LlmClient.java、AskEngine.java、README.md
- **變更摘要**：新增 llm.mode（auto/cloud/ollama/offline）供玩家選擇後端
- **遇到的問題**：
  - 問題1：無
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：預設 auto；非法值當 auto

## [2026-07-21 08:16:46] 操作類型：修改
- **文件路徑**：mod/.../AiAssistantScreen.java、PackAiConfig.java、lang/*.json、README.md
- **變更摘要**：助手 GUI 加入模式 CycleButton（auto/cloud/ollama/offline）並寫回設定
- **遇到的問題**：
  - 問題1：編譯驗證中
  - 解決方案：gradlew jar
  - 狀態：❌ 未解決（進行中）
- **備註**：點擊循環切換

## [2026-07-21 08:17:04] 操作類型：修改
- **文件路徑**：mod/.../AiAssistantScreen.java、PackAiConfig.java、lang/*.json、README.md
- **變更摘要**：助手 GUI 加入模式 CycleButton（auto/cloud/ollama/offline）並寫回設定
- **遇到的問題**：無（jar BUILD SUCCESSFUL）
- **備註**：點擊循環切換；SPEC.save() 持久化

## [2026-07-21 08:23:37] 操作類型：修改
- **文件路徑**：mod/.../AiAssistantScreen.java、PackAiConfig.java、lang/*.json、README.md
- **變更摘要**：助手 GUI 加入模型 CycleButton（雲端/Ollama 預設清單），寫入 MODEL / OLLAMA_MODEL
- **遇到的問題**：無
- **備註**：離線模式停用模型按鈕；切換模式會重建清單

## [2026-07-21 09:28:56] 操作類型：修改
- **文件路徑**：mod/.../AiAssistantScreen.java、PackAiConfig.java、LlmClient.java、lang、README
- **變更摘要**：助手 GUI 新增 API Key 輸入（max 512）+ 儲存，可遊戲內貼上完整 key
- **遇到的問題**：無
- **備註**：畫面顯示遮罩；寫入 packai-client.toml

## [2026-07-21 09:31:17] 操作類型：修改 | 新增
- **文件路徑**：PackAiSettingsScreen.java、PackAiMod.java、AiAssistantScreen.java、lang、README
- **變更摘要**：API key 改到 Mods→Packai 自訂設定頁（max 512 可貼）；聊天 GUI 移除 key 欄
- **遇到的問題**：無
- **備註**：IConfigScreenFactory 註冊自訂畫面

## [2026-07-21 10:17:21] 操作類型：修改
- **文件路徑**：mod/.../QuestGuide.java、AskEngine.java
- **變更摘要**：offline 模式顯示較完整任務內容（說明/物品/檔案），並放寬任務匹配後備
- **遇到的問題**：無
- **備註**：rich formatGuide；matchForOffline

## [2026-07-21 10:19:42] 操作類型：修改
- **文件路徑**：Plainify.java、QuestGuide.java、LlmClient.java
- **變更摘要**：玩家可見文字去除物品ID／檔案路徑／程式碼感；改白話名稱與「任務書／本地配方」來源
- **遇到的問題**：無
- **備註**：displayName + humanizeText

## [2026-07-21 10:44:22] 操作類型：新增 | 修改
- **文件路徑**：AskResult、QuestBookOpener、QuestGuide、AskEngine、AskService、AiAssistantScreen、lang
- **變更摘要**：助手顯示任務並提供「開啟任務」按鈕，點擊以 /ftbquests open_book 跳轉
- **遇到的問題**：無
- **備註**：無 Mixin；Heracles 僅提示手動開啟

## [2026-07-21 11:09:34] 操作類型：修改
- **文件路徑**：QuestGuide.java、PackIndex.java
- **變更摘要**：索引／任務比對略過 FTB reward_tables，避免獎勵表被當成任務
- **遇到的問題**：無
- **備註**：isRewardTablePath

## [2026-07-21 11:25:55] 操作類型：修改
- **文件路徑**：AskEngine、AskService、QuestGuide、PackIndex、ModScanners、PackAiConfig、UI screens
- **變更摘要**：修復 hotbar 傳入、totalHint、match 回退、auto 模型欄位、多任務區間解析、PackIndex 鎖
- **遇到的問題**：無
- **備註**：準備 push
