# 代碼變更與問題日誌

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
