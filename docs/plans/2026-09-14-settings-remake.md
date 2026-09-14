# 2026-09-14 — Settings 頁重做計畫（v1，等 SK 批）

> SK 定案：**結構 c**（左側分類樹＋右側可捲動清單，參考 Create mod settings 頁）／**過時項直接刪（3a）**／新設定由我提議。
> 參考實作（真源）：Create `mc1.19/0.5.1` branch `src/main/java/com/simibubi/create/foundation/config/ui/`——`ConfigScreen.java`（mod 清單）、`SubMenuConfigScreen.java`（單一 config 群組：可捲動清單＋搜尋框＋resetAll／saveChanges／discardChanges／goBack）、`ConfigScreenList.java`（`ObjectSelectionList` 子類：entry 有 label／tooltip／path／dirty 動畫／搜尋高亮；`entries/` 下有 `BooleanEntry`／`EnumEntry`／`NumberEntry`／`SubMenuEntry`／`ValueEntry`；`ConfigAnnotations` 提供「需要重啟遊戲／重登」標記）。
> 狀態：**計畫（未實作）**。範圍：**只改 `forge/1.19.2`**（NeoForge 已暫停）。
> 現況審計（本輪親跑）：`PackAiConfig` 共 40 個設定項；現 settings 頁（`PackAiSettingsScreen`）4 tabs／28 控件＋2 子畫面（`WebSearchSettingsScreen`／`ModelPickerScreen`）。

## A. 現況問題（證據）

1. **7 項冇任何 UI**（玩家只能手改 `config/packai-client.toml`）：`recipeCategoryOrder`、`recipeCategoryHidden`、`recipeCardMirrorCategories`、`ingredientNbtSkipPatterns`、`ingredientNbtKeepPatterns`、`ollamaBaseUrl`、`ollamaModel`。
2. **過時／死字串（3a：直接刪）**：`packai.settings.hint`（編輯者備註＋提及已暫停嘅 NeoForge）、`packai.settings.save_all`（無控件引用）、`modularToolSingleItem` 設定（單選上線後無意義）、多選文案（`invpick.cap`／`invpick.modular_one_only`／tooltip「會清除多選」／「多選 extras」）。
3. **存檔不一致**：35 個 setter 中 **16 個冇即時 `SPEC.save()`**（`setMode`／`setCloudModel`／`setUiModel`／`setOllamaModel`／`setApiKey`／`setWebSearchEnabled`／`setTavilyApiKey`／`setSerperApiKey`／`setMaxJeiChars`／`setHistoryTurns`／`setMaxFacts`／`setSidebarSide`／`setPreferObtain`／`setRecipeCategoryPrefs`／`setIngredientNbtPolicy`／`setIngredientTooltipAsReq`）→ 改完靠 Forge 退出時寫盤，crash／強制關就掉。
4. **版面已逼爆**：screen 自己註釋「Four tabs so 480p fits」；無法再加項。
5. **無搜尋**：40 項要靠人手找。

## B. 目標版面（照 Create 風格，我方簡化版）

```
┌ Pack AI 設定 ───────────────────────────────┐
│ [搜尋…                ]  ● 有未儲存改動      │
├──────────────┬──────────────────────────────┤
│ 連線         │ ▸ 連線                        │  ← 右側：可捲動 entry 清單
│ 回答         │   API 位址      [……       ]   │     每行：label ＋ 控制 ＋ tooltip
│ 配方         │   模式          [ 自動  ▾ ]   │     dirty 行有標記／需要重啟 badge
│ 任務         │ ▸ 回答                        │
│ 介面         │   歷史輪數      [ 8 ]━●──     │
│ 進階         │   最大 JEI 字數 [ 4000   ]    │
│ 除錯         │   回答語言      [ 跟隨遊戲 ▾] │
├──────────────┴──────────────────────────────┤
│ 說明：滑到／揀到嘅設定嘅一句話解釋            │  ← 底部描述面板（唔靠 tooltip 都得）
│ [重設本頁] [捨棄改動] [儲存]        [返回]   │
└─────────────────────────────────────────────┘
```

- 左側分類＝7 個（連線／回答／配方／任務／介面／進階／除錯）；每項一張卡有「即時生效」或「需重開遊戲」badge（照 Create `ConfigAnnotations` 概念）。
- 右側可捲動（`ObjectSelectionList`，`getRowWidth()` 扣 scrollbar）；**搜尋框**過濾 label／key／tooltip，命中會 highlight（Create 用 `highlight` annotation＋動畫；我哋用簡單高亮即可）。
- 改動先入記憶體 pending，按「儲存」才 `SPEC.save()`；「捨棄改動」還原；離開時有未存改動要提示（Create 行為）。

## C. 要做嘅嘢（實作清單）

1. **新 screen 骨架**：`client/gui/config/PackAiConfigScreen.java`（左樹＋右清單＋搜尋＋底部描述＋按鈕）、`PackAiConfigList.java`（`ObjectSelectionList` 子類）、`entries/`（`ToggleEntry`／`CycleEntry`／`NumberEntry`／`TextEntry`／`ListEntry`／`ActionEntry`／`GroupHeader`）。
2. **Registry（單一真相）**：`PackAiConfigRegistry.java`——每個 config key 一筆：`key／分類／label lang key／tooltip lang key／entry 類型／取值 setter getter／badge（即時／需重開）`。**所有 config 項必須在此登記**（見 §E 防漏 check）。
3. **7 項補 UI**：JEI 類別排序／隱藏、mirror 機台類別、NBT skip／keep pattern（`ListEntry`：一行一個 pattern、可加減）、Ollama 網址（`TextEntry`）＋Ollama 模型（`TextEntry` 或由 `/api/tags` 拉清單嘅 `CycleEntry`）。
4. **刪過時（3a）**：3 條死字串／`modularToolSingleItem` 設定＋lang；多選文案跟 S1 單選一齊改；`PackAiConfig` 內無用 key 一併移除（要保留 toml 兼容？→ 見 §D 風險）。
5. **統一存檔語意**：所有 setter 加 `SPEC.save()`（或走 registry 統一 save），並加 harness 斷言「每個 setter 都 save」。
6. **新設定（我提議，等 SK 剔）**：
   - **S-1 回答語言**：`跟隨遊戲 / zh_tw / zh_cn / en_us`（而家係自動判，SK 成日用中文；加強制選項）
   - **S-2 Ollama 網址＋模型**（見 §C3）
   - **S-3 JEI 類別排序／隱藏 UI**（把 config-only 變可視化清單）
   - **S-4 mirror 機台類別 UI**
   - **S-5 NBT 過濾 pattern UI**
   - **S-6 「重置全部為預設」＋「重設本頁」**
   - **S-7 「開啟 trace 資料夾」按鈕**（一撳開檔案總管；用 `Util.getPlatform().openFile`）
   - **S-8 「清快取／重建索引」按鈕**（現時要重開遊戲）
   - **S-9 Web search 子畫面**：保留，但由主畫面一撳入去（而家已經係），加「測試連線」按鈕
   - **S-10 快捷鍵提示行**（`]` 開問答、其他鍵位）→ 純資訊卡
   - **唔做**：HUD／主題（屬 jarvis-hud，唔屬 packai）、NeoForge 相關（已暫停）
7. **影片／音效**：無（唔關事）。

## D. 風險／要 SK 留意

- **UI 面積**：480p 玩家（GUI scale 大）睇唔晒；對策＝可捲動＋分類少而清楚＋字型自適應（截斷＋tooltip）。
- **toml 兼容**：刪 key 會令舊 toml 殘留（Forge 會忽略）→ 安全，但要在 CHANGELOG 寫。
- **密鑰**：API key 欄位要遮罩（顯示 `•••`／`已設定`）＋唔可以 log 出嚟。
- **工作量**：中大型（新 5 個 class＋registry＋7 個 lang ×3 語言＋harness＋python check）；分兩批落（批一：骨架＋現有 28 項遷移＋統一存檔；批二：7 項補 UI＋新設定）。
- **回歸**：settings 係玩家唯一入口 → 批一落地後要 SK 真機逐頁驗（搜尋／改值／重啟後生效）。

## E. 防漏機制（SK「just in case we forgot some」）

新增 `tests/check_settings_registry.py`：
- 由 `PackAiConfig.java` regex 抽全部 config key（`defineInRange`／`define`／`defineEnum`）；
- 由 `PackAiConfigRegistry.java` 抽全部已登記 key；
- **斷言：每個 config key 恰好登記一次**；無 UI 者必須出現在檔內 `EXCLUDED` 清單並附一句原因（例：內部狀態／只剩遷移用途）；
- 反向斷言：registry 唔可以登記唔存在嘅 key；
- 加 harness `PackAiSettingsRegistryCheck`（Java 側同類斷言，headless）。

→ 日後新增 config 但唔記得加 UI，兩個 check 都會紅，唔會再「唔覺意漏」。

## F. 驗收（做完成點）

1. Java harness：registry 完整性／entry↔config 對映／setter 全部 save／分類無空。
2. `tests/check_settings_registry.py` PASS；python 全量 = baseline（3 FAIL 唔可以多）。
3. 真機：開 settings → 7 個分類逐頁睇；搜尋「key」「nbt」有命中；改 3 個值（toggle／數字／文字）→ 按儲存 → 重開遊戲仍然生效；「捨棄改動」可還原；未存改動離開有提示。
4. **元素重疊自動檢查**（SK 規則）：跑既有 OOB check（`tools/` 內，若無就打一個）＋截圖人工確認。
5. 兩次 code review（pass1 重構／pass2 三個月後脆弱位）。
