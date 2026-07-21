# 代碼變更與問題日誌

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
