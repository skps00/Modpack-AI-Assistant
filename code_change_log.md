## [2026-08-11 13:50:00] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：RecipeUnlockGates、RecipeCard.unlockGates、JeiRecipeCards、AskService、AiAssistantScreen、FormatRequirements.footnoteLines；tests/check_recipe_unlock_gates.py、check_format_requirements.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：#1B — RecipeUnlockGates soft-read RecipeStages getStage + vanilla display-advancement recipe rewards → Gate → formatRequirements unlock 段；缺 mod 靜默
- **遇到的問題**：
  - 問題1：NFWC 有 GameStages、無 RecipeStages → 實機 stage-lock 難驗
  - 解決方案：單元／fixture mock（check_recipe_unlock_gates）；日誌註「B verified by mock only」
  - 狀態：✅ Forge jar→dist＋NFWC SHA256 8FB3DBC9C633547CB742CF2704B08E0F245DCFA23199252E024C6967D781B93A；Neo compileJava OK；fixtures OK；CUA skip（MC 未開 — 須重開 NFWC）
- **備註**：不實作 #1C；不碰 recipe-card slot 對齊。不 bump。Gate kinds＝STAGE（RecipeStages）／ADVANCEMENT（有 display 的 rewards.recipes）。GameStages 本體無 recipe→stage 表，靠 RecipeStages wrap。

## [2026-08-11 13:42:00] 操作類型：修改
- **文件路徑**：docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：配方卡 Create/Hexerei **item/slot 錯位**標為 known issue — **defer／ignore**（用戶明示先不管）；不擋 #1B
- **遇到的問題**：
  - 問題1：Pose vs item drift；FBO／pose.scale／ModelView identity 皆死路（identity → 空白卡回歸）
  - 解決方案：文件記 defer；畫卡維持 1:1 可見；對齊留後再開
  - 狀態：✅ 文件已記；無 feature 碼
- **備註**：下一實作＝#1B RecipeUnlockGates。順序仍 #1B→#1C→#5→#5b。

## [2026-08-11 13:31:47] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw.java；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：撤銷 `drawRecipe` 前 ModelView identity reset — 配方卡白底／槽／物品全消失回歸；保留 1:1＋`Lighting.setupForFlatItems`
- **遇到的問題**：
  - 問題1：jar SHA `75EE42A2…` 後 caption「配方：切割 · 动力锯」＋catalyst 仍在，JEI layout panel 全無（chat 文字正常）
  - 根因（FACT）：`RenderSystem.getModelViewStack()` push 後 `setIdentity`／`identity()` 抹掉 GUI 既有 ModelView；JEI `drawRecipe`／`ItemStackRenderer` 依賴該矩陣 → 畫出不可見（或 GL 壞態被 catch 略過）。AskService 仍 attach（`hasLayout` log 路徑未動）— draw-only
  - 解決方案：拿掉 ModelView push/identity/pop／apply；`draw` 只 `setupForFlatItems`＋`setPosition(left,top)`＋`drawRecipe`＋再 flat；仍禁 pose.scale／FBO
  - 狀態：✅ Forge jar→dist＋NFWC SHA256 DD85BBFA9C1F84067876F921FEC56A4627FF850FC0809F3D5EC89B12C9E57D4B；Neo compileJava OK；check_recipe_card_layout OK；CUA：執行中 JVM 仍舊 jar（`75EE42A2`）— zoom 確認 caption「配方：切割 · 动力锯」＋catalyst、layout 區空白；`latest.log` `jeiDrawable=true`（attach OK）。**新 jar 須完整重開 NFWC** 後再 Ask 驗白底
- **備註**：不 bump。對照回歸前可見卡 jar `5CFD1743…`。優先可見卡，Create 對齊暫次要。截圖 `dist/cua_recipe_cards_stale_classpath.png`（舊 classpath）。**2026-08-11 13:42：對齊正式 defer／ignore — 見 backlog Known issues。**

## [2026-08-11 13:21:50] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw.java；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：`drawRecipe` 前清 ModelView＋`Lighting.setupForFlatItems`（Create AnimatedSaw／GuiGameElement 髒狀態）；仍 1:1 無 FBO／無 pose.scale
- **遇到的問題**：
  - 問題1：jar `5CFD1743…` 已禁 scale，木工機／鋸木卡物品仍相對槽位偏移（對照 JEI 原生正常）
  - 根因（推論）：JEI `ItemStackRenderer` 把 Pose 乘進 ModelViewStack；Create `GuiGameElement.cleanUpLighting` 在 `customLighting==null` 時不還原（留 `setupFor3DItems`）；embed 連續畫卡可能留下髒 ModelView／lighting，槽 blit（Pose）與 item（ModelView）脫節
  - 解決方案：`draw` 內 `pose.push/pop`；ModelView `push`+identity+`applyModelViewMatrix`，draw 後 `pop`+apply；前後皆 `Lighting.setupForFlatItems`；hover 仍同 `setPosition(left,top)`
  - 狀態：❌ 回歸 — 見 2026-08-11 13:31:47（ModelView identity 抹 GUI 矩陣 → 卡不可見）；原標記 ✅ 作廢
- **備註**：不 bump。無 FBO／無 layout scale。**須完整重開 NFWC**（classpath 已換 jar）後 Ask 鋸木／木工機對照 JEI 槽內 icon。

## [2026-08-11 13:02:46] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：TokenUsage、LlmClient、AskResult、AskEngine、ChatMessage、ChatSession、AiAssistantScreen、PackAiConfig、PackAiSettingsScreen；lang en/zh_tw/zh_cn×2；tests/check_token_usage.py
- **變更摘要**：Ask 後顯示 LLM token usage（`prompt_tokens`／`completion_tokens`）；設定 `showTokenUsage` 預設開；無 usage 則隱藏
- **遇到的問題**：
  - 問題1：PowerShell ConvertTo-Json 弄壞 lang UTF-8；`git checkout` 誤還原後丟未提交 recipe-card 字串
  - 解決方案：自 `build/resources` 合併回 recipe_cards_*／requirements 等鍵，再用 Python UTF-8 加 token 鍵；Forge→Neo 補缺
  - 狀態：✅ Forge jar→dist＋NFWC SHA256 327FF47C32413C2DE09BA22CA3EA90098FF7675F5FDFA464509827BC6CE88B9B；Neo compileJava OK；check_token_usage OK
- **備註**：不 bump／無 $ 價表。Config：`[llm] showTokenUsage`（預設 true）。UI：助手回覆正文下灰字 `1.2k in · 400 out`。避開 JeiLayoutDraw。**須重開 NFWC**。

## [2026-08-11 13:07:20] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw、AiAssistantScreen；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：JEI 配方卡改永遠 1:1 畫（禁 pose.scale）— 修 Create 鋸木／木工 OUTPUT 浮在箭頭上、右槽空
- **遇到的問題**：
  - 問題1：FBO→pose-scale 後（SHA 155C9F5F…）症狀仍同：左 INPUT 對齊、OUTPUT 浮箭頭、右槽空、tooltip 在浮空 planks 上可用
  - 根因：`pose.scale` 下槽／箭頭 blit 走 PoseStack，JEI `ItemStackRenderer` 走 ModelViewStack←mulPose；Create Sawing OUTPUT(118,48) 偏移 ∝ 座標遠大於 INPUT(44,5)。`panelWidth-28` 對 177px 常仍 scale&lt;1。FBO 與 pose-scale 同源問題。
  - 解決方案：有 `jeiLayout` 時 `shapedScale=1`；`JeiLayoutDraw.draw` 只 `setPosition(left,top)+drawRecipe`；chat scissor 裁切過寬；draw 後 `Lighting.setupForFlatItems`（AnimatedSaw customLighting=null 時 cleanUp 不還原）
  - 狀態：✅ Forge jar→dist＋NFWC SHA256 5CFD174303877E579CF8218AA000DB2FAFD3EAF583AA1872D0B123B7AFAA5B43；Neo compileJava OK；check_recipe_card_layout OK；CUA pending restart
- **備註**：不 bump。Ask oak_planks／鋸木卡對照右槽內 planks×N。

## [2026-08-11 11:50:00] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw、AiAssistantScreen；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：修 JEI 配方卡物品圖相對槽位偏移（Create 鋸木／木工機類）— 縮放改純 pose，scale 用 getRect 非 layoutFit pad
- **遇到的問題**：
  - 問題1：Ask 卡上 log／planks 浮在空槽與箭頭上方，vanilla 3×3 正常
  - 根因：Create Sawing 177px＋OUTSIDE_DRAW_PAD→常 scale&lt;1 走 FBO；FBO 自訂 ortho＋category.draw（AnimatedSaw／GuiGameElement）後槽背景與 ItemStackRenderer 座標脫節；slot hover 同源
  - 解決方案：scaled 一律 `drawScaledPoseFallback`（停用 FBO 畫卡）；`shapedScale` 改 `width()`/`height()`；hover 仍 mapScreenMouseToJei
  - 狀態：❌ 未解決（pose-scale 仍脫節；見 2026-08-11 13:07:20）
- **備註**：可辨配方＝Create Cutting／Sawing（橡木原木→板）；中文 caption「柳木木工機」多半為 catalyst／機器名。不 bump。進世界後 Ask 鋸木／oak_planks 對照槽內 icon。

## [2026-08-11 11:36:41] 操作類型：修改
- **文件路徑**：forge+neo：PackAiConfig、PackAiSettingsScreen、JeiRecipeCards、AskService；lang en/zh_tw/zh_cn×2；tests/check_recipe_card_role_budget.py、check_jei_focus_id_strict.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：Recipe 分兩設定 — `recipeCardsPerItem`＝取得／OUTPUT（預設 3）、`recipeCardsPerItemUse`＝用途／INPUT（預設 3）；collect／Ask 各用各的
- **遇到的問題**：
  - 無
  - 狀態：✅ Forge jar→dist＋NFWC SHA256 204BC583FD8CEFBDAB84F050C2A440E23BE048C5106622CBCDDF63136780471B；Neo compileJava OK；check_recipe_card_role_budget／check_jei_focus_id_strict OK
- **備註**：不 bump。既有 toml 只改 obtain 數；use 缺 key → 預設 3。**須重開 NFWC**。 Recipe 分頁：取得卡數｜用途卡數，下一行 recipeCardsMode。

## [2026-08-11 11:32:00] 操作類型：修改
- **文件路徑**：forge+neo：JeiRecipeCards.collect／ensureCoreCraft／upgradeCraftingLayouts、AskService.collectAskRecipeCards、PackAiConfig comment；lang en/zh_tw/zh_cn×2（recipe_cards_per_item tooltip）；tests/check_recipe_card_role_budget.py、check_recipe_card_layout.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：recipeCardsPerItem=N → 每角色各最多 N（OUTPUT 與 INPUT 獨立，非共享餘額）；總 cap ≈ items×2×N
- **遇到的問題**：
  - 問題1：ensureCoreCraft／AskService 仍以 items×N 截斷會吃掉 INPUT
  - 解決方案：總 cap 改 2×N（totalCap／perItem*2）
  - 狀態：✅ Forge jar→dist＋NFWC SHA256 05D7B018341D0642BB6DE09B0F10007BEE9C8CD34F03CE8BD04165B258E91DC4；Neo compileJava OK；check_recipe_card_role_budget／check_recipe_card_layout／check_jei_focus_id_strict OK
- **備註**：不 bump／不新 config key。**須重開 NFWC** 後 Ask 同時有取得＋用途的物品 → N=3 可見最多 3 張 output＋最多 3 張 input。

## [2026-08-11 11:22:45] 操作類型：修改
- **文件路徑**：forge+neo：RecipeCard.FocusRole、JeiRecipeCards.collect、AskService.promptCardLine、AiAssistantScreen caption、PackAiConfig/tooltip、ReplyLang/lang×3、PackIndex.shouldAttach（+purpose）；tests/check_recipe_card_role_budget.py、check_ask_recipe_card_gate.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：材料也出卡 — JEI 配方卡在 recipeCardsPerItem 預算內先收 OUTPUT（取得），餘額填 INPUT（用作材料）；[RECIPE_CARDS] 標 role=output|input；caption「用作材料」；AI prompt 用途問也可 on
- **遇到的問題**：
  - 問題1：誤 Copy Forge RecipeCard 覆蓋 Neo → forge FluidStack／Registry
  - 解決方案：改回 BuiltInRegistries＋neoforge FluidStack
  - 問題2：isPurposeOrientedQuestion 不存在
  - 解決方案：用既有 isPurposeQuestion
  - 狀態：✅ Forge jar→dist＋NFWC SHA256 5D3A4DE47E1C0C70963AFB4201E32DBA84F338351FF30EA3ADF42FE0C2CB62CF；Neo compileJava OK；check_recipe_card_role_budget／check_ask_recipe_card_gate／check_reply_prompt_keys OK
- **備註**：不 bump／不硬編碼 blood_bottle。避開 #1B。**須重開 NFWC** 後 Ask `hexerei:blood_bottle`／用途 → 應見 Mixing Cauldron 等 INPUT 卡（caption 用作材料；catalog role=input）。

## [2026-08-11 11:06:28] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：RecipeCard.reqNotes、JeiReqNotes、FormatRequirements、JeiRecipeCards、AskService、AiAssistantScreen、Font/GuiGraphics mixin、ReplyLang/lang×3；tests/check_format_requirements.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：#1A 完成 — JEI draw／extras 文字 → reqNotes；formatRequirements 合併 Ask REQUIREMENTS＋卡腳註；濾 refine/kill；純 3×3 空 notes
- **遇到的問題**：
  - 問題1：JEI 11 Font.draw vs JEI 19 createRecipeExtras.addText
  - 解決方案：Forge Font mixin；Neo extras 捕獲＋GuiGraphics mixin；API miss → 空 notes
  - 問題2：誤 git checkout 掉 forge lang 未提交改動
  - 解決方案：從 neo lang 還原再插入 requirements keys
  - 狀態：✅ Forge jar→dist＋NFWC SHA256 16C4E812AD217E99A712C8FC66366299DE32B33638431252D7505750C485CE37；Neo compileJava OK；check_format_requirements OK
- **備註**：不 bump／不 commit。**須重開 NFWC** 後 Ask 熔煉／有 XP／stress 配方對照 JEI；純 Crafting 無噪音。
## [2026-08-11 11:03:10] 操作類型：修改
- **文件路徑**：forge+neo：AskEngine、JeiLookup；lang en/zh_tw/zh_cn×2（catalog／craft_pref／llm_style via update_reply_prompts）；tests/check_ask_ease_order.py、check_quest_demote_when_jei.py、check_reply_prompt_keys.py、update_reply_prompts.py；code_change_log.md
- **變更摘要**：Ask mysterious_trinket 類 — loot／chest 取得文先於任務書 JEI；prefer≠quest 時 OUTPUT JEI dump 跳過 Quests cat；prompt 強：acquire ease + card index
- **遇到的問題**：
  - 問題1：askEaseBand 已把 Quests 卡排後，但 mysterious_trinket 常無 Chest-Loot JEI cat → 唯一卡仍是 Quests；LLM「怎麼來」被 JEI Quests×N dump 帶著走，acquire chestloot 變其二
  - 根因：craft/balanced／purpose 事實塊把 `jeiLines`（或 questFactLines）放在 `acquireLines` 前；prompt 寫「JEI first」且 Quests-only JEI 仍算 hasRecipeGet
  - 解決方案：acquire 提前；OUTPUT summarize 跳過 quest cat（prefer≠quest）；catalog／craft_pref／llm_style 改 loot／ease 先於 one-shot quest
  - 狀態：✅ check_ask_ease_order／check_quest_demote／check_reply_prompt_keys OK；Forge jar→dist＋NFWC SHA256 FF7F8EFA…；Neo compileJava OK
- **備註**：不 bump／不硬編碼 mysterious_trinket。避開 IngredientReqHints。**須重開 NFWC** 後 Ask `mysterious_trinket`：正文應先 chestloot／掉落，任務書配方／卡在後。

## [2026-08-11 10:50:00] 操作類型：修改
- **文件路徑**：docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：使用者確認 NFWC 手動驗收完成 — AI marker 預設／`recipeCardsMode`、describe→card 交錯、ease-first 取得排序；backlog checklist 勾選 recipe cards mode
- **遇到的問題**：
  - 無
  - 狀態：✅ 僅文件；無碼／jar
- **備註**：下一片 backlog = #1A（JEI-visible reqNotes）。

## [2026-08-11 10:45:10] 操作類型：修改
- **文件路徑**：forge+neo：PackAiConfig、RecipeCardsMode；lang en/zh_tw/zh_cn×2（tooltip）；tests/check_recipe_cards_mode.py；code_change_log.md
- **變更摘要**：`recipeCardsMode` 預設改為 `ai`（新安裝／缺 key／空白／非法值）；tooltip 標 AI 為預設
- **遇到的問題**：
  - 問題1：既有 `packai-client.toml` 已寫 `recipeCardsMode=keywords` 會繼續 keywords
  - 解決方案：只改 Java `.define`／blank／invalid fallback；不改寫使用者 toml
  - 問題2：`RecipeCardsMode.parse` 的 `default` 分支同時涵蓋 `keywords` 字串
  - 解決方案：顯式 `case "keywords"`，`default`→AI
  - 狀態：✅ check_recipe_cards_mode OK；Forge jar→dist＋NFWC SHA256 F003A4ECC7A666A18A617DEC8391CE3F216468167BC819DB7EC13D4141B4F129；Neo compileJava OK
- **備註**：不 bump／不 commit。既有安裝需手動改設定或刪 key 才變 AI。

## [2026-08-11 10:35:39] 操作類型：修改
- **文件路徑**：forge+neo：RecipeCardsMode、AskService；lang en/zh_tw/zh_cn×2（ai_marker＋tooltip）；tests/check_recipe_cards_mode.py、update_reply_prompts.py（ZH_CN）；code_change_log.md（承接 10:29 WIP）
- **變更摘要**：AI 模式 prompt 強化（配方／合成／取得 → 必須 `[[recipe_cards:on]]`＋說明→`[[recipe_card:N]]`）；缺 gate 但有 N 標記→視同 on；tooltip 寫明 describe+card；修好 update_reply_prompts ZH_CN 過期
- **遇到的問題**：
  - 問題1：AI 模式「Prefer off」太嚴 → 用途／配方混問漏 `on` → 無卡
  - 解決方案：改 recipe_cards_ai_marker 為 recipe/craft/obtain（含混問）MUST on＋交錯；純用途 off；`resolveGateMarker` 見 `[[recipe_card:N]]` 當 on
  - 問題2：on 但無 N → 需仍顯示卡
  - 解決方案：沿用 RecipeEmbed fallback／appendUnused（有卡無 marker 則文末掛卡）
  - 狀態：✅ check_recipe_cards_mode／check_recipe_embed／check_reply_prompt_keys OK；Forge jar→dist＋NFWC SHA256 9A046F56A66D17DBABE14D6340D3FF8448909188BA0ABC97D1968E80E6B3ED62；Neo compileJava OK
- **備註**：不 bump／不 commit。**須重開 NFWC** 後設 recipeCardsMode=ai，Ask 配方／取得驗 text→card 交錯。

## [2026-08-11 10:29:12] 操作類型：修改
- **文件路徑**：forge+neo：RecipeEmbed、AskService、AiAssistantScreen、ReplyLang；lang en/zh_tw/zh_cn×2；tests/check_recipe_embed.py、check_reply_prompt_keys.py、update_reply_prompts.py；code_change_log.md
- **變更摘要**：Ask 配方卡改為 AI 短文→`[[recipe_card:N]]`→卡 交錯；prompt 附索引卡清單；有 lead-in 略過靜態 caption；保留 CAPTION_TO_CARD_GAP／CARD_OVERFLOW_PAD／CARD_BODY_TAIL
- **遇到的問題**：
  - 問題1：先前僅 caption「配方：Crafting」不夠 — 要導覽式說明
  - 解決方案：RecipeEmbed 認 `[[recipe_card:N]]`；有 recipe marker 時優先 fromMarkers（含多選）；item 自動掛卡僅在無 recipe marker；AskService 寫入 [RECIPE_CARDS] IO；UI 有前文則跳 caption
  - 問題2：並行 spacing 修補不可回退
  - 解決方案：不改 CAPTION_TO_CARD_GAP=4／CARD_OVERFLOW_PAD=6／CARD_BODY_TAIL=4；略 caption 時仍用 ChatLine.recipe 頂 pad
  - 狀態：✅ 交錯落地；AI gate／tooltip／ZH_CN／jar 由後續條目收尾
- **備註**：不 bump／不 commit。Offline 無 marker 仍靠 categoryTitle caption。

## [2026-08-11 10:27:31] 操作類型：修改
- **文件路徑**：docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：Backlog 鎖 D13 — 在 Issue #5 加 follow-on「Gateways + forward loot table Ask」（schema／loot JSON 正向索引／LootJS 疊加；Ask 不臆造）
- **遇到的問題**：
  - 問題1：Gateways 調查顯示無法展開「史莱姆随机战利品」／`entity_loot`／巢狀 loot
  - 解決方案：文件化為 #5b（非獨立 organ parser）；對齊 #5 LootJS；refs Shadows-of-Fire Gateways gist
  - 狀態：✅ 僅文件；無實作
- **備註**：不 bump／不 commit。不開 Issue #6 — loot 家在 #5。

## [2026-08-11 10:22:12] 操作類型：修改
- **文件路徑**：forge+neo：CraftPriority、RecipeCategoryPrefs、JeiRecipeCards、JeiLookup、QuestGuide、PackIndex、AskEngine；tests/check_craft_priority_generic.py、check_quest_priority.py、check_ask_ease_order.py；code_change_log.md
- **變更摘要**：Ask 取得路徑 ease-first — loot／合成優於任務書；FTB `can_repeat` 缺省＝不可重做→任務列表與 quest acquire 降權；設定拖曳順序改為 tie-break
- **遇到的問題**：
  - 問題1：自訂 category order 可把 Quests 排最前，吃掉 loot／合成卡位
  - 解決方案：`CraftPriority.askEaseBand` 作 Ask 卡／JEI get 主鍵；`RecipeCategoryPrefs.sortKey` 僅次級
  - 問題2：不可重做任務無索引訊號
  - 解決方案：FTB depth-1 `can_repeat: true`→`Hit.canRepeat`；缺省 false；match 次級排序＋輕扣分；acquire fact 內嵌 mark 後按 band 排
  - 狀態：✅ python mirrors OK；Forge jar→dist＋NFWC SHA256 FE5EC8FB…；Neo compileJava OK
- **備註**：不 bump／不 commit。不硬編碼 mysterious_trinket。避開 AiAssistantScreen caption 並行改動。**須重開 NFWC** 後 Ask 取得物驗：loot／合成卡在任務書前；不可重做任務較低。

## [2026-08-11 10:20:42] 操作類型：修改
- **文件路徑**：forge+neo：AiAssistantScreen.java；tests/check_ask_chat_spacing.py；code_change_log.md
- **變更摘要**：Ask UI 配方區段間距 — caption↔卡太貼、卡↔下一段太空、編號步驟空白過大
- **遇到的問題**：
  - 問題1：caption「配方：…」+ book 幾乎貼在白 JEI 卡上
  - 根因：caption 純文字 ChatLine；catalyst 畫在卡頂 `renderRecipeCardTitle`（ICON_SIZE+2）再畫 JEI → 視覺上 caption/書圖與白卡幾乎無縫
  - 解決方案：catalyst 併入 caption 列（icon+字）；卡頂不再畫 icon；`CAPTION_TO_CARD_GAP=4` 作 card extraPad
  - 問題2：卡1 底到下一段 caption 約整卡死白
  - 根因：`shapedBoundsHeight` 用 `layoutFitHeight`（含 OUTSIDE_DRAW_PAD=14）+ `recipeCardHeight` 尾 +8 + 卡後強制 EMPTY blank（~lineStride）疊加；Quests 無 clock 仍付滿 pad
  - 解決方案：chat stride 改 `height()`+overflow≤6；尾 pad `CARD_BODY_TAIL=4`；卡後改 `ensureChatBlankLine`（不雙空白）；單 `\n` 不插空行、`\n\n+` 只一空行；編號 extraPad 4→2
  - 狀態：✅ check_ask_chat_spacing OK；Forge jar→dist＋NFWC SHA256 BB4DD9AF2A9FB31452CB284A5EC9F6088D1511BAE80B6DF39D7A9ECEDD88236F；Neo compileJava OK
- **備註**：不 bump／不 commit。**須重開 NFWC** 後 Ask 多配方卡驗 caption→4px→卡→modest→caption；純文字 1/2/3 與【來源】。

## [2026-08-11 01:20:39] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：RecipeCardsMode、PackAiConfig、PackAiSettingsScreen、AskService、AskResult、AskReplyScrub、ReplyLang；lang en/zh_tw/zh_cn×2；tests/check_recipe_cards_mode.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：Ask 配方卡顯示時機可設（keywords／ai／always／never）；AI 模式靠 [[recipe_cards:on|off]] 標記決定是否掛卡並 scrub；離線 fallback keywords
- **遇到的問題**：
  - 問題1：AI 模式須在 finalize 前解析標記（async 不可 ThreadLocal）
  - 解決方案：標記留在 answer 至 withRecipeCards；AskService 先 parseMarker 再 resolve／scrub
  - 狀態：✅ python mirror OK；Forge jar→dist＋NFWC SHA256 DFB1B6DF…；Neo compileJava OK
- **備註**：config key `recipeCardsMode`；不 bump／不 commit。Offline／無雲端 key 時 AI→keywords（tooltip 說明）。**須重開 NFWC** → Settings → Recipe 驗 CycleButton。

## [2026-08-11 01:09:37] 操作類型：修改
- **文件路徑**：forge+neo：AiAssistantScreen.java；code_change_log.md
- **變更摘要**：Ask 多配方卡不再連貼 — 每卡前插入 JEI categoryTitle caption（packai.screen.recipe）；卡上標題文字移出，僅保留 catalyst 圖示列
- **遇到的問題**：
  - 問題1：使用者要的是「每卡前說明文字→視覺卡」區塊結構，不是空像素 gap；多卡同 item 時 RecipeEmbed 連發 Part.card，中間無 lead-in
  - 解決方案：ppendAssistantBody 加卡前 ppendRecipeCardCaption（僅 category metadata）；
enderRecipeCardTitle 不再畫重複標題字
  - 狀態：✅ Forge jar OK→dist＋NFWC SHA256 6A011AB6…；Neo compileJava OK
- **備註**：不 bump／不 commit。不改 Ask engine／卡數。Caption 來源＝RecipeCard.categoryTitle()，不臆造配方事實。**須重開 NFWC** 後 Ask oak_planks 驗「配方：…」→卡→「配方：…」→卡。

## [2026-08-11 00:59:17] 操作類型：修改
- **文件路徑**：forge+neo：AskService、JeiRecipeCards；tests/check_jei_focus_id_strict.py；code_change_log.md
- **變更摘要**：Ask 單焦點不再硬砍成 1 張配方卡（改尊 
ecipeCardsPerItem）；JEI focus／卡收集用 count=1，避免背包堆疊數污染卡面輸出量
- **遇到的問題**：
  - 問題1：設定「每物品配方卡數」=3，單物品 Ask 仍只 1 卡
  - 根因：AskService.collectAskRecipeCards 對 keys.size()<=1 做 Math.min(configured,1)（舊「導覽一卡」策略；測試亦鎖死）
  - 解決方案：雙樹改用 configured；更新 mirror 測試期望；JeiRecipeCards.forItem 正規化 focus count=1
  - 問題2：卡面輸出顯示 64、底部「已選：1」→ 使用者疑選中數被當產量
  - 分析：harvest 路徑用 JEI 槽位 stack.copy()，非 prefer.getCount()；「已選」=選中物品數。若仍見 64，多半是 pack JEI 配方產量；count=1 focus 防 drawable 焦點堆疊污染
  - 狀態：✅ 已解決（python mirror OK；Forge jar→dist＋NFWC SHA256 999EA7DC…；Neo compileJava OK）
- **備註**：不 bump／不 commit。產品：設定 UI／tooltip 優先於舊單卡 soft-cap。**須重開 NFWC** 後 Ask 單物品驗 ≤3 卡；輸出量對照 JEI。

## [2026-08-11 00:42:00] 操作類型：修改
- **文件路徑**：forge：WidgetCompat、AiAssistantScreen、PackAiSettingsScreen、WebSearchSettingsScreen、ModelPickerScreen、RecipeCategoryScreen、InvPickScreen；neo：InvPickScreen；lang en/zh_tw/zh_cn×2（invpick tip）；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：Issue #3 擴充 — 設定巢狀頁＋InvPick tip；Forge 根因修：Screen.render 不畫 TooltipAccessor → WidgetCompat.renderHoveredTips 掛所有 Pack AI screen
- **遇到的問題**：
  - 問題1：主設定／AiAssistant 已掛 tip key，但 Forge TipButton／CycleButton 無 tip（1.19.2 需 Options 式後繪）
  - 解決方案：Tip* 實作 TooltipAccessor；各 screen super.render 後呼叫 
enderHoveredTips；Forge 補網搜／模型／配方類別 tip；雙樹 InvPick tip
  - 狀態：✅ Forge jar→dist＋NFWC（SHA256 6C025E82…）；Neo compileJava OK；CUA：MC 在設定頁但舊 classpath — **須重開 NFWC** 後 hover 設定／巢狀頁／側欄驗 tip
- **備註**：不 bump／不 commit。跳過：配方／模型自繪列、InvPick 物品槽（原版 item tooltip）。Neo 原生 setTooltip 無需後繪。


## [2026-08-11 00:31:02] 操作類型：修改
- **文件路徑**：forge+neo：AiAssistantScreen；lang en/zh_tw/zh_cn×2；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：Issue #3 — AiAssistantScreen 側欄／搜尋／輸入框補齊 tooltip（packai.screen.tooltip.*）；設定頁語氣單行
- **遇到的問題**：
  - 問題1：僅 quest_next／next_step／Neo search 有 tip；其餘按鈕無 hover 說明
  - 解決方案：Forge 走 WidgetCompat TipButton／TipEditBox；Neo 走 Button.tooltip／EditBox.setTooltip；既有 tip 保留
  - 狀態：✅ 編譯雙樹通過；Forge jar→dist＋NFWC（SHA256 F5AAD82C…）；CUA：MC HWND 在但 PostMessage ; 未開 GUI、foreground 被 UIAccess 擋；且執行中仍為舊 classpath — **須重開 NFWC** 後手動 hover 驗 tip
- **備註**：不 bump／不 commit。觸控：send、regenerate、clear、pick、jump、settings、open_quest、quest_more、search、input。

﻿## [2026-08-10 21:32:15] 操作類型：修改
- **文件路徑**：README.md、docs/CURSEFORGE_DESCRIPTION.md
- **變更摘要**：README 改玩家優先（英＋繁短版）；CurseForge Description 對齊官方商店寫法精簡可貼
- **遇到的問題**：無
- **備註**：GitHub repo description 一併改；CF 頁需作者手動貼 Markdown


## [2026-08-10 21:24:58] 操作類型：修改
- **文件路徑**：forge+neo：AskPurposeContext、AskEngine、PackIndex、ReplyLang、lang×3；ItemCreateUseCheck／HeavyScriptChecks／RoadmapChecks；code_change_log.md
- **變更摘要**：通用 kubejs create().finishUsing/.use → script_use 進 PURPOSE（線上無物品事實罐頭注入；僅 prompt 回覆模式 pin）
- **遇到的問題**：
  - 問題1：A6E2100A 已 index script_use，Ask 仍用途未知／JEI無
  - 根因：`isPurposeGraphFact` 不認 `-[script_use]->` → AskEngine 丟 graphFacts，PURPOSE 空，模型只見 JEI 空
  - 解決方案：PURPOSE 認 script_use；formatInteractOrAcquireFact 通用格式化；CREATE_RANDOM_CALL 泛化 get*/random*；llm_style pin「有腳本事實勿因 JEI 無配方稱無用」；多 shape fixture
  - 狀態：✅ 已解決（ItemCreateUseCheck OK；forge jar→dist＋NFWC SHA256 B5B6A8BD169D06A50CE31A3CF786C90CE8B50528372B4A3D366BC5198B26F6E2）
- **備註**：產品規則：線上＝PURPOSE＋回覆模式（非物品事實罐頭）；offline 罐頭可。殘留（未擴）：`AskJeiHints.ensureQuestStatusVisible`／Tetra scroll 類 post-LLM 事實句仍線上存在。無 delivery 特例注入。**須重開 NFWC** 後 Ask 任意 create().finishUsing（含 random_delivery_agreement）。
## [2026-08-10 21:15:26] 操作類型：修改
- **文件路徑**：forge+neo：RecipeEmbed.java；tests/check_recipe_embed.py；code_change_log.md
- **變更摘要**：接受／scrub LLM 誤寫的 `[[recipe:mod:ns:path]]`（prompt 佔位符 `mod:id` 被字面複製）；殘留 orphan `[[recipe:` 一律剝除
- **遇到的問題**：
  - 問題1：Ask 異象之卷·巫術之錘回覆正文出現 raw `[[recipe:mod:tetra:scroll_rolled]]`
  - 根因：`RECIPE_MARKER` 只吃 `ns:path` 單冒號；模型跟 prompt 寫成 `mod:tetra:scroll_rolled` → regex 不命中 → strip／parts 都留原文
  - 解決方案：registry ref 允許多段；`normalizeRegistryRef` 剝字面 `mod:`；strip／tidy 再 orphan scrub
  - 狀態：✅ 已解決（check_recipe_embed OK；雙樹 compileJava；forge jar→dist＋NFWC SHA256 E5C42F44EFF700426EFF7B1892DEA735030E509791098EFDC41DB65368CB4097）
- **備註**：不 bump／不 commit。**須重開 NFWC** 後再 Ask；正文不應再出現 `[[recipe:`。配方卡仍由客戶端 attach。

## [2026-08-10 21:26:00] 操作類型：修改
- **文件路徑**：forge+neo：AskReplyScrub、AskResult、RecipeEmbed；AskReplyScrubCheck；tests/update_reply_prompts.py、check_reply_prompt_keys.py；lang×3×2；code_change_log.md
- **變更摘要**：post-LLM 強制 scrub PURPOSE 標籤（`[SCROLL_*]`／`[PURPOSE]` 等）；prompt 禁回聲；RecipeEmbed 文字塊再 scrub
- **遇到的問題**：
  - 問題1：換 LLM 後回覆露出 `[SCROLL_EFFECT]`／`[[recipe:mod:…]]` 等內部標記
  - 根因：PURPOSE 注入 bracket headers；prompt 寫「優先／引用 [SCROLL_*]」誘導弱模型原樣抄；AskResult 僅 scrub JEI absence／HTML marker，無標籤濾網
  - 解決方案：`AskReplyScrub.scrubPromptEcho` 接 AskResult／tidyChunk；prompt 硬性禁止 echo（rule 18）；保留 `[[item:]]`／`[[recipe:]]`／`{{item:}}` 給 UI（RecipeEmbed 消費）
  - 狀態：✅ 已解決（AskReplyScrubCheck OK forge+neo；check_reply_prompt_keys OK；forge jar→dist＋NFWC SHA256 0CCF61F4F2D3BFC4352E05554CA3D385F6D07D4077B67183228C8D5FA3E49BBA）
- **備註**：不 bump／不 commit。弱模型較易抄標籤＝指令服從差。**須重開 NFWC** 後 Ask 巫術之錘／卷軸 — 回覆不應見 `[SCROLL_*]`；`[[recipe:]]`／`{{item:}}` 仍由 UI 轉卡／圖示。

## [2026-08-10 20:30:39] 操作類型：修改
- **文件路徑**：forge+neo：PackIndex；neo GraphRetrieveFilterCheck、HeavyScriptChecks；forge ItemCreateUseCheck；code_change_log.md
- **變更摘要**：Ask 焦點物品可依完整 id／裸 path 命中 KubeJS `event.create`；解析 `.finishUsing`／`.use` 給物行為進 PURPOSE（`-[script_use]->`）；保留 nearby clips
- **遇到的問題**：
  - 問題1：`kubejs:random_delivery_agreement` Ask 回 JEI/loot/quests 未知
  - 根因：腳本為 `create('random_delivery_agreement')`＋`.finishUsing`→`getRandomWare()`／`give`；`bodyMentionsSeed` 只認完整 `ns:id`；`parseRightClickFacts` 只認 ItemEvents／onEvent，不認 registry finishUsing
  - 解決方案：seed 亦匹配引號裸 path；index `.create`；`parseItemCreateUseFacts`；clip needles 加裸 path
  - 狀態：✅ 已解決（ItemCreateUseCheck／GraphRetrieveFilterCheck `-ea` OK；forge jar→dist＋NFWC SHA256 A6E2100A8C29FAC3E7E0CFDFF3DD39BC7A5C1AD4534607D7CAF559F85AFC2973）
- **備註**：不 bump／不 commit。行為真相＝hold-use（bow）非 ItemEvents.rightClicked。**須重開 NFWC** 後 Ask `kubejs:random_delivery_agreement`／隨機交貨協議用途。getRandomWare 定義在 `kubejs/server_scripts/utils/wares_model.js`。

## [2026-08-10 20:25:00] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw、AiAssistantScreen；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：配方卡流體改回 JEI `IRecipeLayoutDrawable.drawRecipe`→`RecipeSlot`→`FluidTankRenderer`；移除 Pack AI `renderPlacedFluids` 自畫藍方；hover 用 `getSlotUnderMouse`＋槽位 getRect（非整卡 hitbox）
- **遇到的問題**：
  - 問題1：Item Mixing「混合釜」tank＝扁藍方＋白邊、tooltip「水/1000mB」飄在圓環中央
  - 根因：FCD7AD7D 為修 orphan 疊 `drawFluidSlot`（自創外觀）蓋住 JEI `FluidTankRenderer`；`itemUnderMouse` 用整張 layoutFit 當 hitbox → tooltip 不跟槽
  - 解決方案：含流體仍 skip FBO→pose（避免 scissor 關時漏畫），但只呼叫 JEI `drawRecipe`；`layoutHoverUnderMouse` 映射 mouse→JEI 座標後取槽 rect＋ITEM/FLUID ingredient
  - 狀態：✅ 已解決（check_recipe_card_layout OK；雙樹 compileJava OK；forge jar→dist＋NFWC SHA256 6C944F965E81491990E36E480BF1BCD98F1CA64C877EC560791B60A86FDAD738）
- **備註**：對齊 JEI 11.8.1：`RecipeLayout.drawRecipe`／`FluidTankRenderer`／`getSlotUnderMouse`／`ForgeTypes.FLUID_STACK`。圓環＝Create Item Mixing 原生；JEI `recipeBackground` 雙框若仍怪＝embed 限制。不 bump／不 commit。**須重開 NFWC** 後 Ask 異象之卷·巫術之錘 craft，對照 JEI 同配方畫面。

## [2026-08-10 20:09:30] 操作類型：修改
- **文件路徑**：forge+neo：RecipeCard、JeiRecipeLayoutCollector、JeiRecipeCards、JeiLayoutDraw、AiAssistantScreen；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：JEI 配方卡流體改畫在槽位座標內；含流體時跳過 FBO（避免 scissor 關閉時流體漏畫到螢幕角落）
- **遇到的問題**：
  - 問題1：Item Mixing 卡藍色流體方塊飄在螢幕左下，不在混合釜 tank 槽
  - 根因：FBO 路徑 disableScissor；JEI 流體 blit 用槽位本地座標當螢幕座標、寫入主 FB → 左下角孤兒；footer 另畫流體與 tank 脫節
  - 解決方案：收集 placedFluids(x/y/w/h)；JEI layout 後於卡內疊畫；有 placed 則不再 footer 畫流體；含流體跳過 FBO 走 pose（保留聊天 scissor）
  - 狀態：✅ 已解決（check_recipe_card_layout OK；雙樹 compileJava OK；forge jar→dist＋NFWC SHA256 FCD7AD7DAEC385694252739E73F79484E129CD3B66A9987E9D6FB95C536B7FEF）
- **備註**：不 bump／不 commit。須重開 NFWC 後 Ask 異象之卷·巫術之錘 craft → 流體只在卡內 tank。


## [2026-08-10 19:41:07] 操作類型：修改
- **文件路徑**：forge+neo：RecipeEmbed、TetraSchematicText、AskResult、AskService、AiAssistantScreen；tests/check_scroll_material_card.py、check_recipe_embed.py、update_reply_prompts.py；lang×3×2；code_change_log.md
- **變更摘要**：SCROLL_MATERIALS 改系統注入 `{{item:id×N}}` 內嵌圖示；聊天列 baseline 同行繪製；停用 materialStrip RecipeCard 上浮
- **遇到的問題**：
  - 問題1：材料圖示獨立 FLOW 卡／左欄 icon，看起來「上浮」或與內文脫節
  - 根因：`withScrollMaterialCards` prepend strip；`Part.ITEM` 走 ICON_COL 左欄非字間 glyph
  - 解決方案：Ask 後 inject inline markers；UI wrap+draw 16×16 於文字流；無材料仍純文字；不再 attach strip
  - 狀態：✅ 已解決（check_scroll_material_card／check_recipe_embed／check_tetra／check_reply_prompt_keys OK；雙樹 compileJava OK；forge jar→dist＋NFWC SHA256 924F13BF205930FB7AAFAA6B62F951369A70E65C5086C4D9CFD7755BD66657CE）
- **備註**：不 bump／不 commit。**須重開 NFWC** 後 Ask 卷軸材料：內文見圖示（非上方黃卡／右欄直排）；無材料仍純文字「無需材料」。

## [2026-08-10 19:03:47] 操作類型：修改
- **文件路徑**：forge+neo：PackIndex、QuestGuide、TetraSchematicLookup、TetraSchematicText、ItemVariantKeysText；neo GraphRetrieveFilterCheck；tests/check_tetra_schematic_facts.py、check_quest_title_prefer.py、check_ask_recipe_card_gate.py；code_change_log.md
- **變更摘要**：B) Ask 配方卡僅 craft/acquire 意圖才附；C) soft match 後 prefer 有標題＋quest SNBT 抽 schematic key；D) scroll key 無同名 JSON 時反查 locked requirement schematic（terra→cthulhu）
- **遇到的問題**：
  - 問題1：每 Ask 都出 JEI 卡（含「工作台放什麼」）
  - 根因：`shouldAttachAskRecipeCards` 非 code 問一律 true
  - 解決方案：只在 craft／acquire 意圖時 attach（YAGNI 不加新 config）
  - 問題2：任務鈕「scroll rolled相…」
  - 根因：兄弟 scroll_rolled 同分＋空 title 排序優先；variant 看不到 SNBT `key:`；displayTitle fallback relatedQuest(registry 名)
  - 解決方案：parse 抽 key/schematics；preferReadableTitleHits；displayTitle 拒 registry-path label
  - 問題3：異界遺物：大地無 install_items
  - 根因：無 `schematics/**/terra.json`；解鎖在 `shield/plate/cthulhu.json` locked:`tetra:terra`
  - 解決方案：直載失敗後掃 locked-by schematics；acceptKey 允 `terra` 無底線
  - 狀態：✅ 已解決（python checks OK；雙樹 compileJava OK；forge jar→dist＋NFWC SHA256 6A29A19676475CF01F31CF5E9289D3B94BF5314707593BAB9F2FE17433F03B5D）
- **備註**：不 bump／不 commit。**須重開 NFWC** 後手動：B 非合成問無卡；C 任務鈕見「能量瓶改造」；D 大地卷見 cyclops_eye／cthulhu module、勿亂附合成卷卡。能量瓶路徑應仍 OK。

## [2026-08-10 19:03:20] 操作類型：修改
- **文件路徑**：forge+neo：TetraSchematicText.java、TetraSchematicLookup.java；tests/check_tetra_schematic_facts.py、update_reply_prompts.py；lang×3×2（via update_reply_prompts）；code_change_log.md
- **變更摘要**：`install_items` 標「pick one／任選其一」；cap 8＋`… +N more in tetra:battery/`；prompt 釘 alternatives（擇一）勿暗示全列必備
- **遇到的問題**：
  - 問題1：能量瓶 Ask 列出幾乎全部 battery 材料，看起來像全要
  - 根因：cap 20 過長；前綴／prompt 未標 OR（Tetra folder materials＝擇一）
  - 解決方案：前綴 `install_items (pick one / 任選其一):`；MAX_INSTALL_ITEMS=8；overflow 保留 folder ref；prompt 明確 alternatives
  - 狀態：✅ 已解決（check_tetra_schematic_facts／check_reply_prompt_keys OK；雙樹 compileJava OK；forge jar→dist＋NFWC SHA256 CDAA0F27893E3159D7C371A4442081C2BCB7034F7089E65BF4849BE19818D2BE）
- **備註**：未 revert sibling locked-by／recipe-card；只改 materials 呈現＋prompt。不 bump／不 commit。**須重開 NFWC** 後 Ask 能量瓶材料應見「任選其一」短列。

## [2026-08-10 18:41:00] 操作類型：修改
- **文件路徑**：forge+neo：TetraSchematicText.java、TetraSchematicLookup.java；tests/check_tetra_schematic_facts.py、fixtures/tetra/materials/battery/*、update_reply_prompts.py；lang×3×2（via update_reply_prompts）；code_change_log.md
- **變更摘要**：`tetra:battery/` 等 materials 資料夾 ref → 掃 kubejs／datapacks `materials/<category>/*.json` 抽出 `material.items`，PURPOSE `[SCROLL_MATERIALS]` 追加 `install_items:`（保留資料夾行）
- **遇到的問題**：
  - 問題1：Ask 只見「需要電池」／`tetra:battery/`，不知工作台該放哪些具體物品
  - 根因：先前刻意不展開 folder；outcomes 只引用資料夾
  - 解決方案：purposeFromLoaded 後 expand；純函式 parse／format；cap 20 unique＋`… +N`；prompt 優先照抄 install_items
  - 狀態：✅ 已解決（check_tetra_schematic_facts／check_reply_prompt_keys OK；雙樹 compileJava OK；forge jar→dist＋NFWC SHA256 B15AC3B886E3BEB190FFBD414EBF547A2D54846101FCCF01D42D6E6A92747202）
- **備註**：保留 `tetra:battery/` 並追加 `install_items:`（cap 20＋`… +N`）。不 bump／不 commit。**須重開 NFWC** 後 Ask 能量瓶材料應見具體 ingot／shard 等。

## [2026-08-10 18:35:00] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：TetraSchematicText.java、TetraSchematicLookup.java、ItemVariantKeys(Text).java、AskService.java；tests/check_tetra_schematic_facts.py、fixtures/tetra/schematics/*、update_reply_prompts.py、check_reply_prompt_keys.py；lang×3×2；code_change_log.md
- **變更摘要**：卷軸 schematic key → 優先讀 `gameDir/kubejs/data/**/schematics/**`（再 datapacks／ResourceManager）；PURPOSE 注入 `[SCROLL_UNLOCK]`（module＋translation effect）／`[SCROLL_MATERIALS]`；保留 `[SCROLL_MECH]`
- **遇到的問題**：
  - 問題1：僅放置機制；Ask 不知解鎖模組／材料
  - 根因：energy_bottle 等在 NFWC kubejs datapack，非 tetra.jar
  - 解決方案：磁碟優先掃 schematics JSON；抽出 locked／module／translation／materials；缺漏標 (json unknown)
  - 狀態：✅ 已解決（check_tetra_schematic_facts／check_reply_prompt_keys OK；雙樹 compileJava OK；forge jar→dist＋NFWC）
- **備註**：不展開 `tetra:battery/` 全表；treatise craftingEffects 仍靠 [SCROLL_EFFECT] lang。不 bump／不 commit。**須重開 NFWC** 後 Ask 能量瓶「增加什麼／要用什麼材料」應見 [SCROLL_UNLOCK]／[SCROLL_MATERIALS]。

## [2026-08-10 18:30:00] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：TetraSchematicText.java、TetraSchematicLookup.java、ItemVariantKeys(Text).java、AskService.java；tests/check_tetra_schematic_facts.py、fixtures/tetra/schematics/*、update_reply_prompts.py、check_reply_prompt_keys.py；code_change_log.md
- **變更摘要**：Tetra 卷軸 schematic JSON → PURPOSE `[SCROLL_UNLOCK]`／`[SCROLL_OUTCOME]`（module／improvement／材料 items／tags／folder）；ResourceManager（優先 SP server）＋kubejs/data 備援；prompt 釘「增加什麼／要用什麼材料」只依事實
- **遇到的問題**：
  - 問題1：僅有 [SCROLL_MECH] 放置說明；使用者仍要效果＋工作台材料
  - 根因：未解析 datapack `data/*/schematics/**` outcomes；energy_bottle 在 NFWC kubejs 非 tetra.jar
  - 解決方案：鍵匹配載入 schematic JSON；抽出 locked／slots／moduleKey／improvements／material；缺 JSON 標 unknown、禁捏造
  - 狀態：✅ 已由 18:35 條取代（改 SCROLL_MATERIALS＋kubejs 優先）
- **備註**：treatise `craftingEffects` 仍走既有 [SCROLL_EFFECT] lang；不展開 battery 資料夾全表（只報 `tetra:battery/`）。不 bump／不 commit。

## [2026-08-10 18:15:00] 操作類型：修改
- **文件路徑**：forge+neo：ItemVariantKeys(Text).java、AskService.java；lang×3×2（tetra_scroll_mech＋update_reply_prompts）；tests/check_item_variant_keys.py、update_reply_prompts.py、check_reply_prompt_keys.py；code_change_log.md
- **變更摘要**：Tetra 卷軸用法真相釘 — PURPOSE 注入 [SCROLL_MECH]（放置工作台附近解鎖，非 RMB 學藍圖）；prompt 禁捏造「右鍵學習」；優先 tooltip／[SCROLL_MECH]／[SCROLL_EFFECT]
- **遇到的問題**：
  - 問題1：Ask 異象之卷·能量瓶仍說右鍵卷軸學藍圖；真實 tooltip＝「图纸」／解鎖附近工作台／5x5x5（中心高 2）
  - 根因：LLM／網搜把「shift+rmb read more」（僅開詳情 UI）誤當 learn；既有 [SCROLL_EFFECT] 講效果、未釘放置機制
  - 解決方案：detect tetra scroll → 注入 tetra lang range/schematics/effects＋packai canonical pin；fact_check 13c／llm_style 禁 RMB learn
  - 狀態：✅ 已解決（check_item_variant_keys／check_reply_prompt_keys OK；雙樹 compileJava OK；forge jar→dist＋NFWC SHA 6A32164D…）
- **備註**：NFWC tetra zh_tw 缺 scroll.* keys（fallback en）；packai `tetra_scroll_mech` 中文 pin 補洞。不 bump／不 commit。**須重開 NFWC** 後 Ask 能量瓶「怎麼用」應見 [SCROLL_MECH]／放置工作台，勿右鍵學習。

## [2026-08-10 17:50:02] 操作類型：修改
- **文件路徑**：forge+neo：ItemVariantKeys(Text).java、AskService.java、AskEngine.java、TooltipCapture.java；lang×3×2（via update_reply_prompts）；tests/check_item_variant_keys.py、update_reply_prompts.py；code_change_log.md
- **變更摘要**：P1 Tetra 卷軸效果 — 接受無冒號 key（fabric_expertise／energy_bottle）、抽 craftingEffects；PURPOSE 注入 item.tetra.scroll.*.description（+extended）；prompt 禁捏造卷軸數值；questFactLines 嚴格 variant（按鈕 soft 不動）
- **遇到的問題**：
  - 問題1：P0 後 Ask「卷軸增加什麼」仍時對時錯；treatise key 無 `/`:` → VARIANT 漏；LLM 標模型推論
  - 解決方案：放寬 acceptKey；craftingEffects；I18n description 進 PURPOSE；fact_check 禁捏造；LLM facts 嚴格過濾
  - 狀態：✅ 已解決（check_item_variant_keys／check_reply_prompt_keys OK；雙樹 compileJava OK；forge jar→dist＋NFWC）
- **備註**：資料源優先＝Shift tooltip＋lang description；schematic outcomes／improvement JSON 僅備援（未建全索引）。不 bump／不 commit。**須重開 NFWC** 後手動：Ask 布料專長／能量瓶／巫術錘「增加什麼」應見 [SCROLL_EFFECT]／tooltip 原文，勿「模型推論」數值；側欄按鈕 soft 兄弟標題可殘。
## [2026-08-10 17:25:10] 操作類型：修改
- **文件路徑**：forge+neo：PackIndex.java、AskEngine.java、ReplyLang.java、lang×3×2、AcquireFactsCheck.java；code_change_log.md
- **變更摘要**：P0 — Ask acquire／PURPOSE 對 schematic 變體改嚴格：有 variantTokens 時只保留 task slice 提到 token 的 quest_*；否則不列兄弟任務＋可選誠實 caution；matchResult／soft prefer 不動
- **遇到的問題**：
  - 問題1：`acquireFactsFor(heldItemId)` 只看裸 id → `tetra:scroll_rolled` 把「真正的重錘」等兄弟任務當取得路徑；PURPOSE 雖 soft-prefer，無命中時仍 fallback 全 id siblings
  - 解決方案：`acquireFactsFor(..., variantTokens)`；emit／ensureFocus 嚴格過濾 taskSlice；清舊 id quest edge 再 pin；PURPOSE 改 3-arg `mentionsFocusItem`（無 soft fallback）；ReplyLang caution
  - 狀態：✅ 已解決（AcquireFactsCheck forge+neo OK；check_reply_prompt_keys OK；雙樹 jar→dist；forge→NFWC SHA 914AF15D…）
- **備註**：不 bump／不 commit；不碰 Issue #3/#1；無 TetraJS／無全 schematic indexer。**須重開 NFWC** 後手動：Ask 鍍金／能量瓶卷「怎麼來」不可列錯任務；任務按鈕 soft 仍可。
## [2026-08-10 14:57:41] 操作類型：修改
- **文件路徑**：forge+neo：ItemVariantKeys.java、ItemSearch.java；tests/check_item_variant_keys.py、check_item_search.py；docs/plans/four-issue-backlog.md（#2 checklist）；code_change_log.md
- **變更摘要**：Issue #2 — Tetra nested schematic：allowlist 走 `BlockEntityTag`/`data` 抽 key／schematics（cap）；ItemSearch 於 consider ingest 一次 `schematicTokens`，score 只比對快取 token
- **遇到的問題**：
  - 問題1：既有 `schematicsFromTag` 只看 root `s`／`schematics`；真實 Tetra 卷為 `BlockEntityTag.data[].schematics`／`key`（`hone/gild_2` 無冒號）→ 搜尋／VARIANT 漏巢狀
  - 解決方案：D8 sample-first allowlist 巢狀 walk＋MAX_DEPTH／MAX_SCHEMATICS／MAX_LIST_SCAN；`key` 接受 `:` 或 `/`；D10 search 不在 score 內重走 NBT
  - 狀態：✅ 已解決（check_item_variant_keys／check_item_search OK；雙樹 compileJava OK；forge jar→dist＋NFWC SHA 082DFCC1…）
- **備註**：不 bump／不 commit；**須重開 NFWC** 後手動：搜 Tetra schematic token（見計畫 Issue 2）；普通物無 NBT 行為不變。Allowlist 漏新路徑時擴 `NEST_COMPOUNDS`／`NEST_LISTS`。

## [2026-08-10 12:55:16] 操作類型：修改
- **文件路徑**：forge+neo：PackIndex.java、AskEngine.java、PackAiConfig.java、PackAiSettingsScreen.java、lang×3×2、QuestGuideIdCheck.java、AcquireFactsCheck.java；code_change_log.md
- **變更摘要**：Anti-spoiler 漏標題 — 每 Ask 清 session graphFacts；acquireFactsFor／ensureFocus 同 retrieve 做 redact；showHidden 切換 save＋invalidate PackIndex；kr.snbt 形狀回歸
- **遇到的問題**：
  - 問題1：顯示隱藏關＋章節 hide_quest_details 時 Ask 藍花美耳草仍見任務「深埋的信」／QUEST_STATUS／open_book（latest.log 11:54–12:45）
  - 根因：QuestGuide／emit 在 filterHidden=true 時已擋（離線 probe OK）；漏點＝(a) PackIndex 跨 Ask 累積 graphFacts，曾揭露之 quest_submit 邊殘留；(b) acquireFactsFor 讀 raw SNBT 未 redact（僅靠 shouldSuppress）；(c) forge setShowHiddenQuests 未 SPEC.save／未清 index
  - 解決方案：beginAskSession 清 graph；acquire／ensureFocus redact；forge save＋GUI invalidateIndexes；測試 kr 形 hide+hide_details+azure
  - 狀態：✅ 已解決（QuestGuideIdCheck／AcquireFactsCheck OK；NFWC offline probe acquire=0 matchHits=0 LEAK=false；forge jar→dist＋NFWC SHA B0366940…）
- **備註**：不 bump／不 commit；**須重開 NFWC**（現 javaw 11:51 起、jar 12:57 換 — 舊 classpath 無效）。重開後 Ask 藍花美耳草：不應見深埋的信／【任務】該標題／open_book 738DAD…

## [2026-08-10 10:48:53] 操作類型：修改
- **文件路徑**：forge+neo：QuestGuide.java、PackIndex.java、AskJeiHints.java、ReplyLang.java、lang×3×2、AskJeiHintCheck.java、AcquireFactsCheck.java、QuestGuideIdCheck.java；code_change_log.md
- **變更摘要**：Anti-spoiler — hide_details／hide_until_deps（quest→chapter→file inherit）不發 quest_submit/obtain、不注入裸【任務】；可廣告時 canonical 必帶任務標題
- **遇到的問題**：
  - 問題1：Azure Bluet／「深埋的信」仍注入「【任務】須繳交物品完成任務」無標題；章節「绽放与溺亡」`hide_quest_details_until_startable: true`＋任務 `hide_details_until_startable` 未擋 PackIndex edge
  - 解決方案：SNBT — quest `hide_details_until_startable`／`hide_until_deps_*`／`secret`；chapter `hide_quest_details_until_startable`／`hide_quest_until_deps_visible`；inherit 同 consume；suppress facts＋inject；有標題才 `【任務】{title}：…`
  - 狀態：⚠️ 部分（edge suppress OK；跨 Ask 殘留／acquire raw 仍可漏 — 見 12:55 條）
- **備註**：不 bump／不 commit；NFWC 重開後 Ask 藍花美耳草／azure_bluet：不應見裸【任務】須繳交；章節 hide-details 整章任務不提

## [2026-08-10 01:54:46] 操作類型：修改
- **文件路徑**：forge+neo：AskJeiHints.java、AskEngine.java、ReplyLang.java、AskJeiHintCheck.java；lang en/zh_tw/zh_cn ×2（quest_status_*）；tests/update_reply_prompts.py、check_reply_prompt_keys.py；code_change_log.md
- **變更摘要**：系統注入 canonical 任務狀態行（obtain／submit）；post-LLM ensureVisible 式強制貼上；scrub 改 allowlist 模板替換，停同義詞打地鼠
- **遇到的問題**：
  - 問題1：禁詞／scrub 同義詞仍被 LLM 改寫（兌換→轉換→放入…）
  - 解決方案：Java 建固定【任務】行；FACT 標 copy-verbatim；回覆缺行或任務句含錯動詞 → 強制插入／整行換成模板
  - 狀態：✅ 已解決（AskJeiHintCheck OK forge+neo；check_reply_prompt_keys OK；jar→dist＋NFWC SHA 3465F146…）
- **備註**：不 bump／不 commit；權威＝post inject；NFWC 須重開後 Ask 奧秘·回憶

## [2026-08-10 01:36:15] 操作類型：修改
- **文件路徑**：forge+neo：AskJeiHints.java、AskJeiHintCheck.java、AcquireFactsCheck.java；lang en/zh_tw/zh_cn ×2；tests/update_reply_prompts.py、check_reply_prompt_keys.py；code_change_log.md
- **變更摘要**：hold-only 再禁「轉換／換成／換取／兌／convert」；scrub 任務書＋幣句改正例「背包持有即可完成」；prompt 加正例
- **遇到的問題**：
  - 問題1：兌換已 scrub，LLM 改寫「JEI…任務書中**轉換**為下界合金幣」仍暗示兌換
  - 解決方案：rule17＋llm_style 擴禁詞＋正例；任務句 轉換／換成／換取／兌換→取得，任務書＋幣整句改持有模板
  - 狀態：✅ 已解決（check_reply_prompt_keys OK；AskJeiHintCheck OK 含轉換句；forge jar→dist＋NFWC SHA 627988A0…）
- **備註**：不 bump／不 commit；NFWC 需重開實例後 Ask 奧秘·回憶：應見「背包持有即可完成／取得」，勿轉換／兌換

## [2026-08-10 01:24:47] 操作類型：修改
- **文件路徑**：forge+neo：AskJeiHints.java、AskEngine.java、AskJeiHintCheck.java；lang en/zh_tw/zh_cn ×2；tests/update_reply_prompts.py、check_reply_prompt_keys.py；code_change_log.md
- **變更摘要**：hold-only FTB（quest_obtain 無 quest_submit）禁 LLM「兌換／繳交／提交」——強化 fact_check rule17＋llm_style；scoped scrub 任務句 兌換→取得
- **遇到的問題**：
  - 問題1：rule17 已禁交易／臆測繳交，LLM 仍對奧秘·回憶說「兌換」幣經任務書
  - 解決方案：明示 forbid 兌換／exchange／redeem；僅 submit 用繳交；obtain＝取得／持有／任務偵測；任務行輕 scrub
  - 狀態：✅ 已解決（python checks OK；AskJeiHintCheck／AcquireFactsCheck OK；forge jar→dist＋NFWC SHA CCA5EF18…）
- **備註**：不 bump／不 commit；手動 Ask 奧秘·回憶：應見取得／持有／偵測，勿兌換

## [2026-08-10 01:18:10] 操作類型：修改
- **文件路徑**：forge+neo PackIndex.java；lang en/zh_tw/zh_cn ×2；AcquireFactsCheck.java；tests/check_human_acquire_label.py；tests/check_reply_prompt_keys.py；code_change_log.md
- **變更摘要**：#4 回歸 — mystery_disasters／奧秘·災難 誤標繳交：\MAX_GRAPH\ 滿後 focus quest edge 丟棄，LLM 臆測繳交。\ensureFocusQuestAcquireEdges\+\ddFactForced\ 繞 cap；fact_check rule17 禁無 quest_submit 臆測繳交
- **遇到的問題**：
  - 問題1：NFWC SNBT \default_consume_items:false\＋task 無 consume → resolve=obtain，但 retrieve 先填滿 200 facts，acquireFactsFor 再 ingest 仍被 addFact 擋
  - 解決方案：ask focus 強制寫入 quest_submit/obtain；prefer null over wrong submit；fixture 220 fill + mystery_disasters
  - 狀態：✅ 已解決（python checks OK；AcquireFactsCheck OK 含 cap fixture；待 jar→NFWC）
- **備註**：不 bump／不 commit；SNBT 見 chapters/56647A42675FB930.snbt tasks mystery_disasters
## [2026-08-10 00:54:31] 操作類型：修改
- **文件路徑**：forge+neo：ReplyLang.java、PackIndex.java；lang en_us/zh_tw/zh_cn；AcquireFactsCheck.java；tests/check_human_acquire_label.py；tests/check_reply_prompt_keys.py；docs/plans/four-issue-backlog.md（#4）；code_change_log.md
- **變更摘要**：Issue #4 — quest submit/obtain ≠ 交易：`humanAcquireLabel` else 改 packData；FTB `consume_items` inherit（task→chapter→file）；ingest `quest_submit`/`quest_obtain`；ambiguous 不標；fact_check 輕 pin
- **遇到的問題**：
  - 問題1：舊 else fallback 一律 `tradeKind`，kubejs／quest 路徑被標成交易
  - 解決方案：path 分類 + kind 參數；D7 inherit；prefer null over wrong label
  - 狀態：✅ 已解決（python check_human_acquire_label／check_reply_prompt_keys OK；neo AcquireFactsCheck OK；雙樹 compileJava OK）
- **備註**：不 bump／不 CurseForge／不 commit；手動測見計畫 Issue 4（Prism／runClient：繳交／取得／交易／kubejs≠交易）

## [2026-08-09 22:50:00] 操作類型：修改
- **文件路徑**：gradle.properties；neoforge/1.21.1/gradle.properties；forge/1.19.2/gradle.properties；code_change_log.md
- **變更摘要**：鎖步 bump `mod_version` 0.1.3→0.1.4；建 jar＋上傳 CurseForge 1643097；commit `chore(release): 0.1.4`；merge PR#6
- **遇到的問題**：
  - 問題1：AUTHOR_TOKEN 直連 `minecraft.curseforge.com` 200
  - 解決方案：`CURSEFORGE_AUTHOR_TOKEN`＋gameVersions Client+loader+MC；JEI optionalDependency
  - 狀態：✅ 已解決（Forge file **8609732**；NeoForge **8609733**）
- **備註**：merge `1cfc0ca`；dist `packai-0.1.4+mc1.19.2-forge.jar` / `packai-0.1.4+mc1.21.1-neoforge.jar`；Prism AI_test_NFWC_DIM（forge）+ ATM10(1)（neo）；changelog＝Machine brief／hidden catalysts／BlockItem gate／soft auto tip／quest-tool FP／【機器】polish

## [2026-08-09 22:35:10] 操作類型：修改
- **文件路徑**：forge+neo：JeiLookup、RecipeGetMarks、ReplyLang、AiAssistantScreen、PackKnowledge；lang en/zh_tw/zh_cn；tests/check_machine_brief.py；code_change_log.md
- **變更摘要**：Machine brief UX polish——標題改【機器】＋聊天上色；JEI dump 縮成分類名＋≤2 例 a→b；自動化 tip 改「不一定」語氣；LLM 已寫漏斗時 post-inject 去 tip 去重
- **遇到的問題**：
  - 問題1：## 機器 在無 Markdown 聊天顯示醜；furnace 隱藏配方仍傾倒「機器X：a→b」牆；tip 與怎麼用漏斗句重複
  - 解決方案：`【機器】`／`[Machine]`；`machineBrief` 專用 compact（MAX_CATS=3／EXAMPLES=2）；`replyMentionsAutomation` 時 strip tip；UI `isSectionHeader` 剝 ## 並 SUGGEST_COLOR
  - 狀態：✅ check_machine_brief／check_pack_knowledge OK；雙樹 jar→dist；Prism AI_test_NFWC_DIM＋ATM10 已覆寫（forge hash 869AD574…）
- **備註**：未 bump；未 merge；edge 仍 BlockItem＋isNonMachineCategory（syringe／quest NO；DNA Analyzer YES via icon）；須重開 client 驗 polish

## [2026-08-09 22:12:00] 操作類型：修改
- **文件路徑**：forge+neo：JeiLookup.java、JeiRecipeCards.java、PackKnowledge.java；tests/check_machine_brief.py；code_change_log.md
- **變更摘要**：furnace catalyst=false 真因——JEI `isCategoryHidden` 在「分類有催化但可見 recipe=0」時把 Smelting 當 hidden；CATALYST focus／`n>0` 雙雙 miss。改 includeHidden＋typeLookup 不要求 recipe count；log path=focus|typeLookup|icon
- **遇到的問題**：
  - 問題1：NFWC latest.log `catalyst=false briefChars=0`；jar 已載；per-cat try/catch 不夠
  - 解決方案：category/catalyst lookup `.includeHidden()`；workstation 認 type catalyst／icon 即收（不靠可見 recipe）；`recipeTypeCatalysts` includeHidden；PackKnowledge log `path=`
  - 狀態：✅ 雙樹 jar→dist；Prism AI_test_NFWC_DIM 已覆寫（hash 6EF11193…）；check_machine_brief OK；push pending
- **備註**：未 bump；未 merge；syringe／horn／quests 仍擋；預期 `path=focus` 或 `path=typeLookup` 且 briefChars>0

## [2026-08-09 22:05:00] 操作類型：修改
- **文件路徑**：forge+neo：JeiLookup.java、PackKnowledge.java、ReplyLang.java、JeiRecipeCards.java；tests/check_machine_brief.py；code_change_log.md
- **變更摘要**：furnace Ask 仍無 Machine——根因改為 workstation 全分類掃描遇壞 JEI category 整段 abort；逐分類 try/catch＋catalyst 失敗 stub；ReplyLang 載入 zh_cn（簡體 `## 机器`）
- **遇到的問題**：
  - 問題1：Prism jar hash＝dist（123b41f）；latest.log graphFacts 僅 `## 怎麼來`/`## 怎麼用`，無 Machine；ensureVisible 無 section 可插
  - 解決方案：workstationCategories／catalystFocusCategories 每分類隔離錯誤；brief 空但已認工作站時仍輸出分類名 stub；PackKnowledge INFO `machine brief focus=… catalyst=…`；tr() 簡體走 zh_cn
  - 狀態：❌ 未解決（用戶 22:05:09 仍 catalyst=false；見上則 includeHidden）
- **備註**：未 bump；未 merge；**須重開 client** 後 Ask furnace；PASS 見回覆

## [2026-08-09 21:30:00] 操作類型：修改
- **文件路徑**：forge+neo：JeiLookup.java、RecipeGetMarks.java；tests/check_machine_brief.py；code_change_log.md
- **變更摘要**：修 furnace／blast furnace 無 `## 機器`——JEI type-catalyst 分類 focus 有、但 recipe limitFocus(CATALYST) 常 0；改認分類即可＋CATALYST 不跑 layout roleMatchesFocus；ensureVisible 不再因 soft-auto 句略過標題
- **遇到的問題**：
  - 問題1：Prism jar＝latest（hash 同 dist machine-brief），Ask furnace 僅 get/use；rolling_mill 有 soft auto；log 無 Machine facts
  - 解決方案：isUsedAsCatalyst 以非 spam／非 quest 的 CATALYST category focus 為準（不要求 recipe count）；appendSection CATALYST 改 unfocused dump＋跳過 layout match；replyAlreadyHasMachine 只認 section／`## 機器` header
  - 狀態：❌ 未解決（用戶「same」；見上則）
- **備註**：BlockItem＋isNonMachineCategory 仍擋 syringe／quests；未 bump

## [2026-08-09 21:05:30] 操作類型：修改
- **文件路徑**：forge+neo：JeiUniversalSpam.java、JeiLookup.java；lang en+zh_tw+zh_cn；PackKnowledge（已閘 BlockItem）；tests/check_machine_brief.py；code_change_log.md
- **變更摘要**：Machine 再收斂——排除 Quests／任務／ftbquests／heracles／information／ponder 等非機台 JEI 分類；自動化建議改謹慎句；非 BlockItem 不進 Machine
- **遇到的問題**：
  - 問題1：任務書 JEI「Quests」分類 icon＋假 recipe 布局被當機器
  - 解決方案：`isNonMachineCategory`；isUsedAsCatalyst／workstationCategories／CATALYST appendSection 皆跳過
  - 狀態：✅ 雙樹 jar→dist；Prism AI_test_NFWC_DIM 已覆寫 `packai-machine-brief+mc1.19.2-forge.jar`；check_machine_brief OK
- **備註**：未 bump；未 merge；branch feat/machine-brief；重開 client 驗 syringe／horn／任務書 NO Machine；DNA Analyzer／furnace YES + soft auto line（後驗 furnace 仍缺 Machine → 見上則）

## [2026-08-09 21:03:55] 操作類型：修改
- **文件路徑**：forge+neo：JeiLookup.java、PackKnowledge.java、ReplyLang.java；lang en_us+zh_tw+zh_cn；tests/check_machine_brief.py；code_change_log.md
- **變更摘要**：Machine 收斂——category icon／type-catalyst 僅 BlockItem；非方塊手持催化跳過 Machine；自動化建議改謹慎通用句（不硬編碼漏斗面）
- **遇到的問題**：
  - 問題1：注射器／崩壞號角等 JEI 分類 icon／工具催化被當成機器並建議漏斗 I/O
  - 解決方案：workstation fallback 與 PackKnowledge 出口皆要求 `instanceof BlockItem`；DNA Analyzer 仍可；furnace／Create 走 CATALYST focus＋BlockItem
  - 問題2：真機器也不一定接受漏斗上下進出
  - 解決方案：`machine_auto_suggest` 改為「可能可用漏斗／管道／傳送帶，以 JEI／說明為準」；不主張特定面
  - 狀態：✅ 併入同批（見上則）
- **備註**：未 bump；未 merge；branch feat/machine-brief

## [2026-08-09 20:48:26] 操作類型：修改
- **文件路徑**：forge+neo：JeiLookup.java；tests/check_machine_brief.py；code_change_log.md
- **變更摘要**：Machine 偵測擴到 JEI recipe-type catalyst（createRecipeCatalystLookup）＋ category icon（DrawableIngredient）；補 Unusual Prehistory DNA Analyzer 僅設 getIcon、未 addRecipeCatalyst 的洞
- **遇到的問題**：
  - 問題1：isUsedAsCatalyst 只靠 RecipeIngredientRole.CATALYST focus；UP Analyzer 無 registerRecipeCatalysts，JEI 仍以 icon 顯示「分析仪」
  - 解決方案：保留 CATALYST focus；另掃 type catalysts／icon ItemStack；machineBrief focus 空時改 unfocused category recipes；dirt/ingot 僅 INPUT 不命中
  - 狀態：✅ 雙樹 compile+jar；dist 已更新；本機 `%APPDATA%\PrismLauncher\instances` 不存在故未覆寫 NFWC；check_machine_brief OK
- **備註**：未 bump；未 merge；ensureVisibleInReply 不變；branch feat/machine-brief；Prism 需手動拷 `dist/packai-1.19.2-forge.jar` 若 instance 路徑異地

## [2026-08-09 20:22:09] 操作類型：修改
- **文件路徑**：forge+neo：RecipeGetMarks / AskEngine / AskService；tests/check_machine_brief.py；code_change_log.md
- **變更摘要**：Machine brief 線上路徑 post-LLM 強制插入（llm_style 禁 Markdown # 會剝 ## 機器）；機器段不再綁 attachCards；hasMachine 時 facts 提前
- **遇到的問題**：
  - 問題1：Ask millstone/furnace 有 get+use／漏斗白話，但無獨立 ## 機器／固定自動化 disclaimer
  - 解決方案：根因＝LLM 被禁 # 故 paraphrases 掉 section；改 RecipeGetMarks.ensureVisibleInReply 在 ReplySources 前插入；AskService 只要 shouldQueryJei 就打 MACHINE_MARK
  - 狀態：⏳ 編譯／jar／push PR#6
- **備註**：未 bump；未 merge；branch feat/machine-brief
## [2026-08-09 19:50:00] 操作類型：新增
- **文件路徑**：forge+neo：PackKnowledge / JeiLookup / AskService / AskEngine / ReplyLang / RecipeGetMarks / PackIndex；lang en_us+zh_tw+zh_cn；tests/check_machine_brief.py；tests/check_pack_knowledge.py；code_change_log.md
- **變更摘要**：薄 P5 Machine brief — JEI catalyst 焦點時 Ask 多 ## Machine（JEI I/O）+ 一行漏斗自動化建議；經 PackKnowledge 出口；非機器焦點不變
- **遇到的問題**：
  - 問題1：無
  - 解決方案：—
  - 狀態：✅ 實作中（編譯／CUA 待驗）
- **備註**：未 bump；不做 EMI adapter／RecipeBackend 階層／agent；branch `feat/machine-brief`

## [2026-08-09 18:55:00] 操作類型：修改
- **文件路徑**：gradle.properties；neoforge/1.21.1/gradle.properties；forge/1.19.2/gradle.properties；code_change_log.md
- **變更摘要**：鎖步 bump `mod_version` 0.1.2→0.1.3；建 jar＋上傳 CurseForge 1643097；commit `chore(release): 0.1.3`
- **遇到的問題**：
  - 問題1：AUTHOR_TOKEN 直連 `minecraft.curseforge.com` 200
  - 解決方案：`CURSEFORGE_AUTHOR_TOKEN`＋gameVersions Client+loader+MC；JEI optionalDependency
  - 狀態：✅ 已解決（Forge file **8608401**；NeoForge **8608402**）
  - 問題2：初 commit `5f0e912` 漏納 gradle `mod_version`（工作樹曾被還原成 0.1.2）
  - 解決方案：補 bump＋follow-up commit 推 main；CF jar 已於 bump 後建置，無需重傳
  - 狀態：✅ 已解決
- **備註**：dist `packai-0.1.3+mc1.19.2-forge.jar` / `packai-0.1.3+mc1.21.1-neoforge.jar`；Prism AI_test_NFWC_DIM（forge）+ ATM10(1)（neo）；changelog＝JEI layout FBO＋drawHoverOverlays slot highlight（main since 0.1.2：1df4e0d／900d675／fcfeea4）

## [2026-08-09 18:16:33] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw.java；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：JEI 槽位 hover 高亮：線上確認高亮在 `drawOverlays`／`drawHoverOverlays`，不在 `drawRecipe`；改 `getSlotUnderMouse`＋`drawHoverOverlays`（避開完整 `drawOverlays` 的 JEI tooltip）
- **遇到的問題**：
  - 問題1：FBO／直接路徑只呼叫 `drawRecipe`，JEI API 註解寫明 recipe 不含 overlays；故「still no」原生高亮
  - 解決方案：查 JEI `RecipeLayout.drawOverlays`→`drawHoverOverlays`→`drawHighlight(0x80FFFFFF)`；Pack AI 三路徑（1:1／FBO／pose fallback）皆畫 slot hover；tooltip 仍 Pack AI
  - 狀態：✅ 編譯 OK；check_recipe_card_layout OK；jar→dist；Prism `AI_test_NFWC_DIM` 已覆寫 forge jar（需重開 client 驗 hover）；ATM10(1) neo jar 已覆寫；PR#5 已 push `900d675`；未 merge
- **備註**：來源 https://github.com/mezz/JustEnoughItems/blob/d4ea796e/Library/src/main/java/mezz/jei/library/gui/recipes/RecipeLayout.java ；未 bump

## [2026-08-09 17:08:54] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw.java；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：縮放 JEI layout 改 offscreen `TextureTarget` FBO（1:1 `drawRecipe(mouse)`→scaled blit），恢復原生槽位高亮；失敗回退 pose-scale＋`-1,-1`；tooltip 仍走 `itemUnderMouse`
- **遇到的問題**：
  - 問題1：pose.scale 時 JEI 內建 hover 與 hit-test 座標空間不一致，先前關高亮只靠 Pack AI tooltip
  - 解決方案：scale&lt;1 時 bind FBO、ortho、native mouse、`drawRecipe`、回主 FB blit；GL scissor save/restore；`MAX_FBO_EDGE=512`
  - 狀態：⚠️ 編譯 OK；jar→dist＋Prism mods；CUA 進世界＋packai JEI 註冊，但 instance `key.packai.open`=semicolon 經 SendInput 未觸發 `consumeClick`（需本機按 `;` 驗 hover）。截圖留 `dist/cua_jei_fbo_*.png`（非完整 hover）
- **備註**：未 bump；branch `fix/jei-layout-fbo`；天花板＝邏輯 GUI 像素 FBO（非 guiScale×）、超大 layout 被 edge cap；FBO 失敗回退 pose-scale

## [2026-08-09 16:20:00] 操作類型：修改
- **文件路徑**：gradle.properties；neoforge/1.21.1/gradle.properties；forge/1.19.2/gradle.properties；code_change_log.md
- **變更摘要**：鎖步 bump `mod_version` 0.1.1→0.1.2；建 jar＋上傳 CurseForge 1643097；commit `chore(release): 0.1.2`
- **遇到的問題**：
  - 問題1：AUTHOR_TOKEN 直連 `minecraft.curseforge.com` 200
  - 解決方案：`CURSEFORGE_AUTHOR_TOKEN`＋gameVersions Client+loader+MC；JEI optionalDependency
  - 狀態：✅ 已解決（Forge file **8607732**；NeoForge **8607733**）
- **備註**：dist `packai-0.1.2+mc1.19.2-forge.jar` / `packai-0.1.2+mc1.21.1-neoforge.jar`；Prism AI_test_NFWC_DIM（forge）+ ATM10(1)（neo）各一 jar；changelog＝JEI card pad／search clamp／recommended dedupe

## [2026-08-09 16:04:58] 操作類型：修改
- **文件路徑**：forge+neo：ItemResolver.java；tests/check_suggest_dedupe.py；RoadmapChecks（neo）；code_change_log.md
- **變更摘要**：側欄「推荐物品」同圖示出現兩次 — `extractIds` 對 `id|name` 與裸 `id` 未去重（marker 內 id 被二次掃描）
- **遇到的問題**：
  - 問題1：`<!--packai:items=mod:id|顯示名-->` 先入 named ref，全文 ID regex 再從同一 marker 抓裸 `mod:id`；LinkedHashSet 只比完整字串 → 兩筆；SuggestIcons 解析後圖示相同（含 NBT tooltip 看起來一樣）
  - 解決方案：`addSuggestionRef` 以 registry id 去重、保留 named／多變體；ID 掃描改跑 strip 後正文（避免 marker 自咬）。`[[item:id]]` 與 named marker 同 id 亦併一
  - 狀態：✅ 已解決（`check_suggest_dedupe` OK；neo+forge `compileJava` OK；CUA 略）
- **備註**：未 bump／未 commit；掛 PR#4 分支 `fix/jei-layout-residuals`（非另開 `fix/dedupe-recommended`）

## [2026-08-09 15:35:00] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw、AiAssistantScreen；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：JEI layout 小殘差 — `layoutFit*` 含 placed∪`OUTSIDE_DRAW_PAD` 防時鐘／火焰被 footer 蓋掉；搜尋 overlay 依 searchBox 上方空間縮減列數；FBO 仍 deferred
- **遇到的問題**：
  - 問題1：卡身高度只吃 `getRect()`，category.draw 略出界的 clock/extras 被 soft footer／下一行蓋掉
  - 解決方案：`layoutFitWidth/Height`＝rect∪placed+16px＋`OUTSIDE_DRAW_PAD`；scale／body／footer 間距改用 fit；hover 框同步
  - 問題2：搜尋 hit 多時 `top=max(chatTop, searchBoxY-boxH)` 會往下蓋住 searchBox
  - 解決方案：先算 avail 高度再 clamp `n`
  - 問題3：縮放時 JEI 原生高亮仍需 offscreen FBO
  - 解決方案：不修；PR#3 tooltip mapping 已夠用
  - 狀態：✅ 已解決（check_recipe_card_layout OK；neo jar 479110／forge 471476 → dist＋Prism `packai-1.19.2-forge.jar`；CUA `dist/cua_residuals_packai.png`：`]` 開 Pack AI 見 crafting 卡。overflow pad 需重啟 client 才載入新 jar — 本次未重啟驗證時鐘像素）
- **備註**：未 bump mod_version；branch `fix/jei-layout-residuals`；FBO／縮放 JEI 原生高亮仍 deferred

## [2026-08-09 12:50:28] 操作類型：修改
- **文件路徑**：gradle.properties；neoforge/1.21.1/gradle.properties；forge/1.19.2/gradle.properties；code_change_log.md
- **變更摘要**：鎖步 bump `mod_version` 0.1.0→0.1.1；建 jar＋上傳 CurseForge 1643097；commit `chore(release): 0.1.1`
- **遇到的問題**：
  - 問題1：上傳曾需 cookie warm；本次 AUTHOR_TOKEN 直連 `minecraft.curseforge.com` 即 200
  - 解決方案：`CURSEFORGE_AUTHOR_TOKEN`＋gameVersions Client+loader+MC；未用 broken `CURSEFORGE_TOKEN` 做 upload
  - 狀態：✅ 已解決（Forge file **8606898**；NeoForge **8606899**）
- **備註**：dist `packai-0.1.1+mc1.19.2-forge.jar` / `packai-0.1.1+mc1.21.1-neoforge.jar`；changelog＝quest demote／search sidebar／JEI layout drawable／crafting tooltips

## [2026-08-09 11:35:00] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw、AiAssistantScreen；JeiRecipeCards／AskService（既有 crafting attach）；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：JEI layout 配方卡 hover 無 tooltip — 補 `itemUnderMouse`＋`registerJeiLayoutItemHovers`（含 CRAFTING_3X3 grid）
- **遇到的問題**：
  - 問題1：Crafting JEI drawable 已顯示，但槽位無 tooltip
  - 解決方案：根因＝`tryRenderJeiRecipeLayout` 成功後跳過 harvest `addItemHover`；scaled `drawRecipe(-1,-1)` 關掉 JEI 內建 hover；CRAFTING_3X3 無 `placedInputs`。改 `mapScreenMouseToJei`＋`getItemStackUnderMouse`，並註冊 grid／placed／output hover
  - 問題2：Crafting 先前仍 harvest `->`（見下條）— attach／prefer SHAPED 已在同分支
  - 狀態：⏳ 編譯／CUA `dist/cua_recipe_tooltip.png`
- **備註**：未 bump mod_version；branch `fix/crafting-jei-layout`

## [2026-08-09 10:25:00] 操作類型：修改
- **文件路徑**：forge+neo：JeiRecipeCards、JeiLayoutDraw；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：Crafting 仍 harvest（文字 `->`）— 改走 SHAPED+JEI drawable（同烹饪），並補 vanilla attach
- **遇到的問題**：
  - 問題1：先前 CUA 鐵錠／方解石仍見文字 `->`（假綠）；`tryCrafting`／CRAFTING_3X3 smash 優先於 JEI xy，`preferMultiRolePanel` 刻意排除 crafting → 與烹饪 SHAPED+drawable 分岔；`fromVanillaCrafting` 亦未 attach
  - 解決方案：collect 先 `fromLayout`；`fromLayout` 先 SHAPED（含 crafting multi-role panel）；CRAFTING_3X3 僅 coords 無用時 fallback；`attachJeiCraftingLayout`＋`upgradeCraftingLayouts`；neo `createRecipeLayoutDrawableOrShowError`／forge IFocus overload 加強 attach
  - 狀態：✅ 已解決（log `CRAFTING_3X3 jeiDrawable=true`；UI 見 JEI crafting layout）
- **備註**：未 bump mod_version；branch `fix/crafting-jei-layout`

## [2026-08-09 08:27:45] 操作類型：修改
- **文件路徑**：forge+neo：JeiLayoutDraw、JeiRecipeCards、AiAssistantScreen；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：B→全部配方卡種類：`JeiLayoutDraw.attach` 不再限 SHAPED；CRAFTING_3X3／FLOW／SHAPED 凡 JEI 能 `createRecipeLayoutDrawable` 即優先畫官方 layout，失敗回退旧 slot harvest
- **遇到的問題**：
  - 問題1：先前 attach／UI 僅 SHAPED（烹饪迷你面板），原版合成／FLOW 機器卡仍無 JEI 背景／箭頭／火焰
  - 解決方案：去掉 `layout()!=SHAPED` 門檻；`tryRenderJeiRecipeLayout` 統一先畫 drawable＋soft/fluid footer；attach 傳 Ask output focus（失敗再 empty）；高度 `hasLayout` 用 drawable 尺寸；null／Optional.empty／draw 失敗維持 harvest
  - 狀態：✅ 已解決（check_recipe_card_layout OK；forge reobf 467802／neo 475597 → dist＋Prism；CUA `dist/cua_crafting_iron.png` 鐵錠 Crafting 卡、`cua_crafting_calcite.png` 方解石 3×3、`cua_cooking_still_ok.png` 烹饪 JEI drawable）
- **備註**：未 bump mod_version；PR fix/ask-residuals；JEI `createRecipeLayoutDrawable` 回 empty 的 category 仍 fallback-only

## [2026-08-09 08:10:00] 操作類型：修改
- **文件路徑**：forge+neo：RecipeCard、JeiLayoutDraw、JeiRecipeCards、JeiRecipeLayoutCollector、AiAssistantScreen；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：B) SHAPED 配方卡掛 JEI `createRecipeLayoutDrawable`，畫 category 背景／火焰／時鐘等 extras（非 slot harvest、無自建 FBO）
- **遇到的問題**：
  - 問題1：烹饪卡有槽位 XY／type catalyst，仍缺 JEI 火焰／時鐘粒子
  - 解決方案：收集時 `IRecipeManager.createRecipeLayoutDrawable` → `RecipeCard.jeiLayout`；UI SHAPED 優先 `drawRecipe`+`tick`；失敗回退 slot harvest；`ponytail:` 天花板＝無 offscreen FBO／縮放時 JEI 內建 hover 高亮可能失準（改用 placed 槽 hover）
  - 狀態：✅ 已解決（check_recipe_card_layout OK；forge jar 467103／neo 474895 → dist；Prism AI_test_NFWC_DIM 覆寫 packai-0.1.0.jar 後重啟；CUA `dist/cua_cooking_bg_fire.png`：奇迹牛奶烹饪卡見 JEI 火焰＋廚鍋佈局，非純 slot 橫條）
- **備註**：未 bump mod_version；PR fix/ask-residuals；殘差：縮放時 JEI 內建高亮／個別 category 時鐘粒子若未進 layout drawable extras 仍可能弱

## [2026-08-09 00:40:54] 操作類型：修改
- **文件路徑**：forge+neo：AskEngine、ReplyLang、AiAssistantScreen；lang en/zh_tw/zh_cn；tests/update_reply_prompts.py、check_reply_prompt_keys.py、check_item_search.py、check_quest_demote_when_jei.py；code_change_log.md
- **變更摘要**：A) JEI 有焦點合成時降級任務正文為可選獎勵備註（勿當主要取得／用途）；C) 搜尋結果改錨在側欄 searchBox 上方，不再蓋住聊天
- **遇到的問題**：
  - 問題1：`create:wrench` 正確掛「第一台机器!」（真 reward）後 LLM 仍把任務書解鎖步驟當主要怎麼獲得
  - 解決方案：`demoteQuestNarrative`（hasRecipeGet∧prefer≠quest∧!override）→ `questOptionalRewardNote` 僅標題；略過 purposeQuests 全文嵌入；prompt #16＋craft_pref.craft 強化
  - 問題2：P4 Search 結果畫在 `panelLeft`（聊天區）蓋住對話
  - 解決方案：改 `sideLeft`／`searchBoxY` 錨點
  - 狀態：✅ 已解決（python checks OK；forge jar 464297／neo 472077 → dist；Prism AI_test_NFWC_DIM mods 已覆寫；CUA：`]` 未開 UI，但 `/ai create:wrench how to get` 觸發 Ask — latest.log 見 demote 後 prompt 含 rule 16＋可選任務備註；回覆步驟以 JEI 合成為主，任務「第一台机器!」標非主要取得）
- **備註**：B) JEI 背景 drawable（火焰／時鐘）仍 deferred（需 category.draw／FBO，非 slot harvest）；未 bump mod_version；PR fix/ask-residuals
## [2026-08-09 00:34:00] 操作類型：刪除
- **文件路徑**：forge/1.19.2/code_change_log.md（刪）；.gitignore；code_change_log.md
- **變更摘要**：移除未追蹤空檔 stray forge 日誌副本；gitignore `forge/**/code_change_log.md` 與 `neoforge/**/code_change_log.md`，避免 agent 再寫錯位置
- **遇到的問題**：無
- **備註**：真日誌僅 repo root；neoforge 無同檔

## [2026-08-08 22:40:17] 操作類型：新增
- **文件路徑**：docs/CURSEFORGE_DESCRIPTION.md；docs/PUBLISH.md；code_change_log.md
- **變更摘要**：撰寫 CurseForge 商店用雙語完整 Description（EN＋繁中台灣用語），並在 PUBLISH 指向該檔供 About 貼上
- **遇到的問題**：
  - 問題1：CurseForge 專案頁被 Cloudflare 擋，無法抓現有 About 原文比對
  - 解決方案：依 README／VERSIONS／mods.toml 事實重寫；未自動上傳 CF（無可靠 API 流程／未驗證 token）
  - 狀態：✅ 已解決（文件就緒；CF 頁需手動貼上）
- **備註**：project id 1643097 / slug pack-ai-assistant-paia；未 commit

## [2026-08-08 21:27:52] 操作類型：新增
- **文件路徑**：docs/RELEASE.md；docs/PUBLISH.md；docs/VERSIONS.md；.cursor/rules/mod-version-bump.mdc；code_change_log.md
- **變更摘要**：寫入社群對齊的 soft-lockstep `mod_version` 政策（RELEASE 專節＋Cursor alwaysApply 規則）；PUBLISH／VERSIONS 交叉連結；不 bump 版本
- **遇到的問題**：無
- **備註**：仍為 0.1.0；未 commit（使用者未要求）

## [2026-08-08 19:55:11] 操作類型：修改
- **文件路徑**：README.md
- **變更摘要**：加上 CurseForge 下載連結（pack-ai-assistant-paia）
- **遇到的問題**：無
- **備註**：暫不上 Modrinth；推 GitHub


## [2026-08-08 14:34:25] 操作類型：修改
- **文件路徑**：forge+neo：QuestGuide.java；tests/check_quest_strip_icons.py；code_change_log.md
- **變更摘要**：任務匹配略過 FTB `icon` 欄（裝飾用 registry id 不當 task／reward）
- **遇到的問題**：
  - 問題1：c90f25a heldScore 門檻後 CUA 仍 FAIL：`create:wrench` Ask 側欄／來源仍掛「压力发条扳手」
  - 解決方案：根因非模糊名——`tetra_2.snbt` 該任務 `icon: "create:wrench"` 而 task 是 `create:precision_mechanism`；`itemsInRange` 把 icon 當 items → heldScore+10 誤 admit。`stripQuestIcons` 後再抽 id
  - 狀態：✅ 已解決（python checks OK；forge jar 463458／neo 471206 → dist；已覆寫 Prism `AI_test_NFWC_DIM\minecraft\mods\packai-0.1.0.jar`；CUA `dist/cua_wrench_quest_fix2.png`：側欄改「第一台机器!」（該任務 rewards 真列 create:wrench），不再掛「压力发条扳手」）
- **備註**：殘差：LLM 仍可能強調任務書獎勵路徑（動力辊壓機+置物台）— 那是真 reward，非壓力發條誤配；與本次 FAIL 標題無關

## [2026-08-08 14:15:20] 操作類型：修改
- **文件路徑**：forge+neo：QuestGuide；tests/check_quest_match_extras.py、check_quest_focus_id_prefer.py、update_reply_prompts.py、check_reply_prompt_keys.py；lang fact_check；code_change_log.md
- **變更摘要**：Ask 有具體 focus registry id 時，任務匹配必須引用該 id（tasks/rewards／全文 id）；禁僅靠顯示名／標題模糊（扳手）掛上無關任務；fact_check 禁止把異名且未列 focus id 的任務正文當成焦點物說明
- **遇到的問題**：
  - 問題1：`create:wrench` Ask 附上「压力发条扳手」（精密構件／震顫）— 同名子串「扳手」，非同一物
  - 解決方案：matchResult 有 held id 時 admit 需 heldScore>0（items 列 id 或 blob 含完整 id）；soft-prefer `preferFocusIdHits`（有列 id 者優先）；prompt #15
  - 狀態：❌ 未解決（門檻正確但漏掉 icon→items；見 14:34:25）
- **備註**：commit+push；CUA 後仍 FAIL（icon 假命中）

## [2026-08-08 14:15:46] 操作類型：修改
- **文件路徑**：forge+neo：QuestGuide.java；tests/check_quest_match_extras.py、update_reply_prompts.py；lang en/zh_tw/zh_cn（fact_check／llm_style）；code_change_log.md
- **變更摘要**：有 registry id 焦點時任務匹配必須硬命中 task／reward／正文 id；prompt 禁止把同名無關任務當成取得焦點物指南
- **遇到的問題**：
  - 問題1：Ask `create:wrench` 正確給 Create 合成，卻把任務「压力发条扳手」（精密構件／Tetra 路徑）當取得該扳手的指引（僅共享「扳手」）
  - 解決方案：`matchResult` 在 held 含 `:` 時若 heldScore=0（未列在 quest items／正文 id）直接丟棄，禁止純 question token／顯示名軟匹配；id 僅在正文（+6）時提升到門檻；fact_check #15＋llm_style 任務條：無 focus registry id 於 tasks／rewards 不得宣稱該任務教你取得焦點物
  - 狀態：⏳ 編譯／CUA 進行中
- **備註**：共用邏輯非單任務黑名單；雙樹對齊

## [2026-08-08 11:54:48] 操作類型：修改
- **文件路徑**：forge+neo：JeiRecipeCards、JeiLookup、AiAssistantScreen；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：從 JEI recipe-type catalyst API 補收機器（廚鍋），卡頭顯示機器圖示，標題／jeiSummary 帶機器名
- **遇到的問題**：
  - 問題1：miracle milk 烹饪卡有原料佈局但缺 Cooking Pot（JEI 有 category icon + 左下廚鍋）
  - 解決方案：根因是廚鍋屬 `IRecipeManager.createRecipeCatalystLookup`，不在 `setRecipe` CATALYST 槽；合併 type catalysts；標題 `titleWithMachine`；UI 卡頭畫機器；SHAPED footer 不重畫（header 已顯）；crafting3x3 判定仍只看 layout catalysts
  - 狀態：✅ 已解決（check_recipe_card_layout OK；forge+neo compile+jar → dist）
- **備註**：無 popup／CUA；commit+push
## [2026-08-08 11:43:01] 操作類型：修改
- **文件路徑**：forge+neo：JeiRecipeLayoutCollector、JeiRecipeCards、RecipeCard、AiAssistantScreen；tests/check_recipe_card_layout.py；code_change_log.md
- **變更摘要**：非合成（烹饪／機器）配方卡改用 JEI 多角色槽位 XY 畫 SHAPED 迷你面板，避免誤導式 FLOW 原料橫條
- **遇到的問題**：
  - 問題1：miracle milk「烹饪」卡物品大致對，但缺熱源／時間／湯勺／空瓶結構，看起來像亂排合成
  - 解決方案：`placedVisibleItemStacks` 收 INPUT+CATALYST+OUTPUT+RENDER_ONLY；非 vanilla crafting 且 ≥2 槽（或 span≥18）→ SHAPED；UI 依 SlotKind 上色；已入面板的 catalyst／output 不重畫 footer
  - 狀態：✅ 已解決（check_recipe_card_layout OK；forge+neo compile+jar → dist）
- **備註**：JEI 背景 drawable（火焰動畫、時鐘粒子）無法從 slot harvest — residual；無 popup／CUA；commit+push
## [2026-08-08 10:50:31] 操作類型：修改
- **文件路徑**：forge+neo：JeiFocusMatch、AskService、AiAssistantScreen、JeiRecipeCards；tests/check_jei_focus_id_strict.py、check_item_variant_keys.py、check_jei_focus_nbt_output.py；code_change_log.md
- **變更摘要**：A) OUTPUT 嚴格 registry id，禁跨模組顯示名（扳手）誤配；B) 單焦點 Ask 每物只 1 張主配方卡＋卡前後空白／步驟加距
- **遇到的問題**：
  - 問題1：`create:wrench` Ask 第一張卡卻是他模藍色扳手
  - 解決方案：JeiFocusMatch 移除跨 item 的 display-name match；OUTPUT／craftingResult 必須同 registry id（變體規則仍限同 item）；JeiRecipeCards 硬拒錯 output id
  - 問題2：單焦點仍 dump 多張 Crafting → 無「一段字＋一張圖」
  - 解決方案：`collectAskRecipeCards` 唯一焦點時 perItem=1；卡前後空行；編號步驟 pad 加大
  - 狀態：✅ 已解決（python checks OK；forge+neo jar → dist）
- **備註**：無 popup／CUA；commit+push PR

## [2026-08-08 10:05:00] 操作類型：修改
- **文件路徑**：forge+neo：AiAssistantScreen、RecipeEmbed；tests/update_reply_prompts.py、check_reply_prompt_keys.py；lang en/zh_tw/zh_cn；code_change_log.md
- **變更摘要**：A) Pack AI 聊天最新 AI 回覆下可點「任務：{title}」→ QuestBookOpener（僅 lastQuests 非空；側欄保留）；B) 聚焦回覆改短步驟 1.2.3.＋卡前斷行／步驟行距
- **遇到的問題**：
  - 問題1：任務入口僅側欄按鈕 → 聊天內不易發現
  - 解決方案：ChatLine 加 clickAction；render 建 QuestClickRect＋底線；mouseClicked 命中開書；多任務跟 questIndex
  - 問題2：配方回覆易成文字牆、卡貼正文
  - 解決方案：prompt 要求短編號步驟；appendWrappedText 步驟 extraPad；卡前空行；RecipeEmbed.tidyChunk 拆黏步驟
  - 狀態：✅ 已解決（python checks OK；forge+neo jar → dist）
- **備註**：無 popup／CUA；不做攻略截圖／原版 chat 掛件

## [2026-08-08 09:18:12] 操作類型：修改
- **文件路徑**：forge+neo：AskService、PackIndex、AskEngine；lang en/zh_tw/zh_cn；tests/update_reply_prompts.py、check_reply_prompt_keys.py、check_packindex_nearby_clip.py；neo GraphRetrieveFilterCheck；code_change_log.md
- **變更摘要**：A) code/script/行為問略過配方卡與重 JEI get；B) prompt 改允許摘要包內腳本事實、禁止自稱無法讀源碼；code ask 保留 kubejs clips
- **遇到的問題**：
  - 問題1：AskService JEI on 一律 collectAskRecipeCards → 「check it's code」仍出配方卡
  - 解決方案：`PackIndex.shouldAttachAskRecipeCards`／`isCodeOrBehaviorQuestion`；無 craft/acquire 意圖則跳過 cards+summarize+extras JEI
  - 問題2：`llm_style` 硬禁「KubeJS／腳本」→ 模型拒用 PackIndex 腳本事實自稱無法讀源碼
  - 解決方案：改禁裸路徑／完整 JS；要求用 pack-local script／index 白話說明；fact_check #14；`shouldSkipSnippets` 對 code ask 永不清 clips；AskEngine code=purpose 區塊優先
  - 狀態：✅ 已解決（python checks OK；GraphRetrieveFilterCheck OK；forge jar 453913／neo jar 461230 → dist）
- **備註**：無 popup／CUA（邏輯+prompt；需拷 jar 進世界才驗 Ask）

## [2026-08-08 09:10:00] 操作類型：修改
- **文件路徑**：forge+neo：PackAiConfig、PackAiSettingsScreen；lang en_us/zh_tw/zh_cn；code_change_log.md
- **變更摘要**：Ask 設定分頁加 `logFullPrompt` 開關（預設關）；開後 Ask 寫 `Pack AI LLM full prompt` 進 latest.log
- **遇到的問題**：
  - 問題1：`logFullPrompt` 僅 toml、預設 false → 使用者開 Ask 卻看不到完整 prompt 日誌
  - 解決方案：設定 UI CycleButton + `setLogFullPrompt`（SPEC.save）；tooltip 警告日誌巨大／隱私
  - 狀態：✅ 已解決
- **備註**：開後需存設定／重進世界再 Ask；搜尋 `Pack AI LLM full prompt`。無 popup／CUA

## [2026-08-08 08:56:36] 操作類型：修改
- **文件路徑**：forge+neo：AiAssistantScreen.contextStack、AskService.askBlocking；tests/check_strip_focus_stable.py、check_inv_pick_focus.py；code_change_log.md
- **變更摘要**：High1：contextStack 先 pin／pending／lastAskFocus，bare resolveStable 同 id 不壓 NBT；High2：askBlocking 接受 stripFocus 鏡像 runAsk
- **遇到的問題**：
  - 問題1：draft 含 `mod:id` 時 resolveStable 先回裸 stack → Tetra scroll 等 pending sample NBT 被洗掉
  - 解決方案：pin → pending/lastAsk rich focus；stable 僅在無 rich 或 registry id 不同（使用者改打別的 id）時勝出
  - 問題2：askBlocking 固定 `resolveAskTarget(..., EMPTY)` → jeiTarget 空、PURPOSE/JEI 偏掉
  - 解決方案：加 stripFocus 參數（舊 4-arg overload 傳 EMPTY）；現無其他呼叫端
  - 狀態：✅ 已解決（python checks；雙樹 compile+jar → dist；commit+push）
- **備註**：無 CUA

## [2026-08-08 00:33:00] 操作類型：修改
- **文件路徑**：forge+neo：AskService、AiAssistantScreen、ItemSearch、InvPickScreen；tests/check_item_search.py；code_change_log.md
- **變更摘要**：修 Bugbot+P4 Search：recipe cards／contextStack／InvPick 用 selectionKey；ItemSearch 全掃+bounded heap、path-only id 分、變體 dedupe；applySearchHit pin 後 focus 聊天框
- **遇到的問題**：
  - 問題1：collectAskRecipeCards／InvPick／contextStack 以裸 registry id 去重／匹配 → Tetra scroll_rolled 第二變體丟卡或錯焦點
  - 解決方案：統一 AskService.selectionKey（改 public）；cards／InvPick／contextStack／strip 皆用同 key
  - 問題2：ItemSearch 滿 80 即 break + id startsWith 讓 `m` 命中全部 minecraft:*；dedupe `id|label` 壓掉 NBT 兄弟
  - 解決方案：取消 early-break、JEI+registry merge、bounded 替換最差；id 比對僅 path（或完整 `ns:`）；dedupe 用 selectionKey
  - 問題3：搜尋點選後焦點留在 searchBox
  - 解決方案：非 askNow 時 setFocused(input)
  - 狀態：✅ 已解決（check_item_search OK；Forge+Neo compileJava+jar；dist jars；commit+push）
- **備註**：其他 Bugbot High（stable resolve 壓掉 pending NBT、PURPOSE 用 jeiTarget）僅記錄不擋；無 CUA

## [2026-08-08 00:25:00] 操作類型：新增
- **文件路徑**：forge+neo：ItemSearch.java、PackKnowledge.java、AiAssistantScreen.java；lang en/zh_tw/zh_cn；tests/check_item_search.py；code_change_log.md
- **變更摘要**：Design P4 最小 Search UI — 側欄搜尋名稱／id（JEI 原料表優先）→ 點選設 focus（pin+pending）或 Shift/右鍵一鍵 Ask（同 hold-Y get+use）
- **遇到的問題**：
  - 問題1：無既有 substring 搜尋 API，僅 SuggestIcons 精確顯示名
  - 解決方案：新增 ItemSearch（JEI soft-dep + registry fallback）經 PackKnowledge.searchItems；結果列表 cap 10；不重寫 RecipeEmbed／EMI
  - 狀態：✅ 已解決（check_item_search OK；雙樹 compileJava+jar；dist jars）
- **備註**：CUA 略過（使用者要求非必要不開）；手動 checklist：] 開助手 → 側欄搜尋 → 左鍵目標 → Ask／Targeted next

## [2026-08-08 00:06:29] 操作類型：修改
- **文件路徑**：neoforge/1.21.1/src/main/resources/assets/packai/lang/zh_cn.json；code_change_log.md
- **變更摘要**：補齊 Neo 簡中設定 UI 缺漏的 6 個 key（與 Forge zh_cn／Neo en_us／zh_tw 對齊）
- **遇到的問題**：
  - 問題1：Neo `zh_cn.json` 缺 `recipe_cards_per_item` 與 4 個 settings tab tooltip → 簡中設定 fallback／空白
  - 解決方案：從 Forge zh_cn 抄入相同文案；en_us／zh_tw 已齊，無需 gen 腳本（非 reply keys）
  - 狀態：✅ 已解決（key set Forge↔Neo 三語 diff 後僅 zh_cn 差這 6 個）
- **備註**：Medium residual #3；僅 JSON，未重編 jar；commit+push 同 branch

## [2026-08-08 00:01:11] 操作類型：修改
- **文件路徑**：forge+neo：QuestGuide.java、QuestGuideIdCheck.java；code_change_log.md
- **變更摘要**：Heracles `parseLooseFallback` 改用 `questBodyText`，收集完整 description[]（跳過空白／`{image:}`），不再單字串 DESC 只取首行
- **遇到的問題**：
  - 問題1：FTB `questBodyText` 已修全正文，Heracles loose fallback 仍 `DESC` 單 `"…"` → 等同 description[0]
  - 解決方案：fallback 直接呼叫 `questBodyText(text)`；刪未用 `DESC`；IdCheck 加 heracles 多行 desc 回歸
  - 狀態：✅ 已解決（Neo+Forge compile OK；QuestGuideIdCheck heracles ok）
- **備註**：Medium residual #2 only（不碰 zh_cn）；commit+push 同 branch

## [2026-08-07 23:49:26] 操作類型：修改
- **文件路徑**：forge+neo：PackIndex.java；neoforge GraphRetrieveFilterCheck.java；tests/check_packindex_nearby_clip.py；code_change_log.md
- **變更摘要**：`shouldSkipSnippets` 不再因單一弱 graph fact 清空 nearby KubeJS clips；非 PURPOSE 需 >=2 facts，或合成向問＋recipe_needs 才 skip
- **遇到的問題**：
  - 問題1：`SNIPPET_SKIP_WHEN_FACTS=1` → 一般問只要任一 related fact 就丟 drink/use 腳本上下文
  - 解決方案：門檻改 2；單 fact 僅在 `isCraftOrientedQuestion` 且 `hasCraftShapedFact` 時 skip；PURPOSE 薄 facts 仍 keep clips
  - 狀態：✅ 已解決（`check_packindex_nearby_clip` OK；GraphRetrieveFilterCheck OK；forge jar 441505／neo jar 448593 → dist）
- **備註**：Medium residual #1 only（不做 Heracles／zh_cn）；commit+push 同 branch

## [2026-08-07 20:20:21] 操作類型：修改
- **文件路徑**：forge+neo：AskEngine、AskService、PackKnowledge；neo JeiFocusMatch／JeiLookup／JeiRecipeCards；tests/check_pack_knowledge.py、check_inv_pick_focus.py
- **變更摘要**：修 6 項 High/Medium：PURPOSE quest soft-prefer variantTokens、purpose/questFactLines 去重、askBlocking 鏡像 JEI hint、Neo craftingInputsAccept tag fallback、emi pref 仍查 JEI、multi-select variant-aware selectionKey
- **遇到的問題**：
  - 問題1：PURPOSE 只用 mentionsFocusItem(id) → Tetra scroll_rolled 兄弟卷錯注入；purpose 又疊 questFactLines 重複
  - 解決方案：preferMentioning + 從 questFactLines 剝已嵌 PURPOSE 行
  - 問題2：recipeBackend=emi 且 JEI 在場仍回 EMI_STUB → 無配方卡
  - 解決方案：emi pref 時 jei 優先；EMI_STUB 僅 EMI 且無 JEI
  - 問題3：Neo JeiFocusMatch 缺 Ingredient#test → 雲杉／tag 槽位匹配失敗
  - 解決方案：移植 craftingInputsAccept；JeiLookup／Cards 傳 recipe
  - 狀態：✅ 已解決（python checks OK；forge jar 441039／neo jar 448104 → dist）
- **備註**：無 commit。CUA 未跑（需拷 dist 重開後可驗）。

## [2026-08-07 19:55:00] 操作類型：修改
- **文件路徑**：forge+neo：PackIndex、AskEngine、JarLightIndex、ModScanners；GraphRetrieveFilterCheck（neo+forge 若有）
- **變更摘要**：Ask grounding 懶讀：有焦點物品時 retrieve 不再靠 focusMods 掃整棵 kubejs；腳本須正文含 seed item id 才 ingest／clip；JarLight scan 從 warmup 延到首次 Ask；facts 仍按 held item id 過濾
- **遇到的問題**：
  - 問題1：focusMods 含 kubejs／mod id 時，路徑 `kubejs/` 一律 +3 → 最多讀 40 個無關腳本並 ingestGraph
  - 解決方案：有 seed item 時不擴 cand／不加 focusMods 路徑分；pack script 須 body 含完整 seed id；warmup 不呼叫 JarLightIndex.ensure
  - 狀態：✅ 已解決（編譯／測試待跑）
- **備註**：無 decompiler；無 commit。startup 仍 build PackIndex（kubejs/scripts 路徑索引）；jar zip 掃描改 Ask-time。

## [2026-08-07 19:45:00] 操作類型：修改
- **文件路徑**：forge+neo：ItemVariantKeys、JeiFocusMatch、JeiRecipeCards、AskEngine、ReplyLang、ReplySources；lang en/zh_tw/zh_cn；tests/check_item_variant_keys.py、check_reply_prompt_keys.py、check_jei_focus_nbt_output.py
- **變更摘要**：有 schematic／VARIANT 時不把 JEI 當同 id 唯一真相：prompt／truth ladder 軟化、配方卡 soft-prefer 變體／顯示名、facts 警告 JEI may mix NBT variants、來源標 JEI (NBT variants may mix)
- **遇到的問題**：
  - 問題1：前次修了名稱碰撞，但 JEI 仍可能對 `tetra:scroll_rolled` 回傳兄弟卷配方；LLM 仍把 JEI 當最高真理
  - 解決方案：variant 時硬擋「他名卷」裸同 id；卡收集 soft-prefer schematic token／完整顯示名；fact_check＋llm_style 註記；AskEngine 注入 jei_variant_caution；ReplySources.softenJeiForVariant
  - 問題2：preferTokens 若併入顯示名單字會把 `scroll` 當命中 → 兄弟卷誤過
  - 解決方案：有 schematic 時 preferTokens 只用 schematic 展開；recipe/card 另比對完整 focus 顯示名
  - 狀態：✅ 已解決（python checks OK；forge jar 439467／neo jar 445648 → dist）
- **備註**：無 commit。CUA：現跑 1.19.2 MP 停標題畫面且 classpath 舊；`]` 未開 Pack AI；變體 distrust 需拷 dist jar 進包並進世界後再驗鏡面卷 Ask。

## [2026-08-07 19:35:00] 操作類型：修改
- **文件路徑**：forge+neo：ItemVariantKeys、JeiFocusMatch、JeiRecipeCards、AskEngine、ReplyLang、ReplySources；lang en/zh_tw/zh_cn；tests/check_item_variant_keys.py、check_reply_prompt_keys.py、check_jei_focus_nbt_output.py
- **變更摘要**：有 schematic／VARIANT 時不把 JEI 當同 id 唯一真相：prompt／truth ladder 軟化、配方卡 soft-prefer 變體／顯示名、facts 警告 JEI may mix NBT variants、同 id 裸匹配降級
- **遇到的問題**：
  - 問題1：前次修了名稱碰撞，但 JEI 仍可能對 `tetra:scroll_rolled` 回傳兄弟卷配方；LLM 仍把 JEI 當最高真理
  - 解決方案：variant 時硬擋「他名卷」裸同 id；卡收集 soft-prefer token／顯示名；fact_check＋llm_style 註記；AskEngine 注入 jei_variant_caution；來源仍列 JEI
  - 狀態：🔄 進行中
- **備註**：無 commit；完成後雙樹 compile／jar→dist；CUA 驗 Pack AI Ask 鏡面卷

## [2026-08-07 19:13:25] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：ItemVariantKeys、ItemVariantKeysText、QuestGuide、AskEngine、AskService、JeiFocusMatch、LlmClient；lang en/zh_tw/zh_cn；tests/check_item_variant_keys.py、check_jei_focus_nbt_output.py、check_reply_prompt_keys.py
- **變更摘要**：同 registry id 的 NBT 變體（Tetra `scroll_rolled` schematic）不再互串：PURPOSE 注入 `[VARIANT]`、任務 soft-prefer schematic／顯示名、JEI OUTPUT 有用名稱時不跨變體、fact_check 禁止挪用他變體任務
- **遇到的問題**：
  - 問題1：Ask／Quest／PURPOSE 只用 `held.id()`（`tetra:scroll_rolled`），JeiFocusMatch OUTPUT 同 item 即過 → 鏡面卷混入能量瓶／劍鞘等任務敘述
  - 解決方案：從 NBT `s` 等抽出 schematic；quest soft-prefer 命中變體 token；JEI 僅在名稱不具辨識力時保留同 type fallback；LLM heldItem 附 schematics
  - 狀態：✅ 已解決（python checks OK；forge+neo `jar` → dist）
- **備註**：殘餘＝任務檔若只寫裸 id、正文無 schematic／顯示名差異，soft-prefer 無法分流。其他模組同 id+NBT 變體同路徑受益。無 Architectury／無 commit。CUA：當前 MP 1.19.2 為舊 classpath，需拷 dist jar 重開後再驗鏡面卷 Ask。

## [2026-08-07 18:45:00] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：PackAiConfig、PackIndex、PackAiSettingsScreen；lang en/zh_tw/zh_cn；tests/check_packindex_nearby_clip.py；code_change_log.md
- **變更摘要**：PackIndex clip 行半徑改可配置 `ui.packIndexClipRadius`（預設 30，clamp 5–100）；Ask 設定 UI cycle 10/20/30/40/50；retrieve 讀 config
- **遇到的問題**：
  - 問題1：無
  - 解決方案：`clipNearMatch(text,needles,radius)` 過載；retrieve 傳 `PackAiConfig.packIndexClipRadius()`；測試預設 30
  - 狀態：✅ 已解決（`check_packindex_nearby_clip` OK；forge+neo `jar` → dist 428635／434837）
- **備註**：無 commit。CUA：現跑 1.19.2 MP 舊 classpath，新 UI 需拷 jar 重開後驗 Ask 分頁「腳本裁切半徑」

## [2026-08-07 18:39:00] 操作類型：修改
- **文件路徑**：forge+neo：PackIndex.java；tests/check_packindex_nearby_clip.py；code_change_log.md
- **變更摘要**：`CLIP_LINES_RADIUS` 20→30（雙樹＋鏡像測試）
- **遇到的問題**：
  - 問題1：無
  - 解決方案：常數對齊；GraphRetrieveFilterCheck 未硬編 20，無需改
  - 狀態：✅ 已解決
- **備註**：無 commit／無 jar

## [2026-08-07 17:20:00] 操作類型：修改
- **文件路徑**：forge+neo：PackIndex.java；neoforge GraphRetrieveFilterCheck.java；code_change_log.md
- **變更摘要**：PackIndex retrieve clip 改為命中 item-id／hint 附近（±20 行／~1100 chars），非檔頭；PURPOSE／用途問或 purpose 事實薄時仍保留 kubejs／script snippets（不再因任一弱 graph fact 清空）
- **遇到的問題**：
  - 問題1：kubejs 命中後仍取 file start ~600 chars；`SNIPPET_SKIP_WHEN_FACTS=1` 有任一 fact 就丟 raw script，PURPOSE 缺 drink／use 邏輯
  - 解決方案：`clipNearMatch`；skip 僅在 craft 路徑且有 related facts，或 seed 已有 desc／right_click／on: purpose 覆蓋；來源仍用既有 PACK（localScripts）
  - 狀態：🔄 實作中
- **備註**：無 Architectury／無 commit。驗：Ask 奇迹牛奶 + logFullPrompt，PURPOSE／facts 應見 nearby kubejs

## [2026-08-07 16:50:00] 操作類型：修改
- **文件路徑**：forge+neo：QuestGuide.java、AskEngine.java；AskPurposeContext（Food gap 提 quest）；QuestGuideIdCheck；code_change_log.md
- **變更摘要**：FTB 任務 `description[]` 收齊非空正文（跳過空行／`{image:}`）；焦點物品相關任務描述注入 PURPOSE／facts；PURPOSE 問法任務事實緊接用途段
- **遇到的問題**：
  - 問題1：奇迹牛奶為 KubeJS；效果寫在任務書 description，Pack AI 來源有「任務書」卻說效果未標明
  - 解決方案：根因 `firstDescriptionLine` 只取陣列第一行（常為 `""`／圖片）→ `questBodyText`；AskEngine 把 item-linked quest desc 併入 purposeLines，purpose 問法提前 questFactLines
  - 狀態：🔄 實作中
- **備註**：KubeJS script scrape 暫不做；FoodProperties gap 仍保留但加「查任務書」

## [2026-08-07 16:45:53] 操作類型：修改
- **文件路徑**：forge/1.19.2 與 neoforge/1.21.1：AskPurposeContext.java；tests/check_ask_purpose_context.py、update_reply_prompts.py、check_reply_prompt_keys.py；lang en/zh_tw/zh_cn（雙樹）；RoadmapChecks（neo）
- **變更摘要**：Drinkable／Edible 但 FoodProperties 無 effects 時 PURPOSE 加明確缺口行；另抽 potion contents／MAINHAND AttributeModifiers；fact_check 禁捏造喝下效果
- **遇到的問題**：
  - 問題1：奇蹟牛奶已標 Drinkable，LLM 仍寫「效果並未標明」；使用者問能否查 code 補效果
  - 解決方案：`getEffects()` 早已接線 — 空＝自訂 finishUsing，Ask 時不反編譯 jar。補 gap 行＋potion／屬性；prompt 規則 13 禁臆造靈魂／魔力
  - 狀態：🔄 實作中
- **備註**：repo／`forge/1.19.2/run/mods` 無奇迹牛奶定義；殘差＝自訂喝效果除非 tooltip／KubeJS desc／Patchouli 有文案

## [2026-08-07 16:36:15] 操作類型：修改
- **文件路徑**：forge+neo：PackAiConfig、LlmClient；code_change_log.md
- **變更摘要**：新增 gated `llm.logFullPrompt`（預設 false）；開啟時 Ask 送訊前把完整 messages JSON（system+history+user）寫入 latest.log，分塊不截斷
- **遇到的問題**：
  - 問題1：無既有 full-prompt log／debug 旗標（僅 `Pack AI LLM mode=…`）
  - 解決方案：對齊 unpackStoredItems 模式加 BooleanValue＋getter；LlmClient 在 HTTP 前 `logFullPromptIfEnabled`；chunk 6000 避單行過長
  - 狀態：✅ 已解決（forge+neo `compileJava` OK；jar → `dist/packai-1.19.2-forge.jar` 422243、`dist/packai-1.21.1-neoforge.jar` 428194）
- **備註**：無 Settings UI（toml 即可）；無 API key 進 log；無 commit／無 CUA（非 GUI 行為）

## [2026-08-07 16:25:03] 操作類型：修改
- **文件路徑**：forge+neo：AskJeiHints、AskService、AskJeiHintCheck；tests/update_reply_prompts.py、check_reply_prompt_keys.py；lang en/zh_tw/zh_cn（雙樹）
- **變更摘要**：有配方卡時禁止「JEI 沒列出合成」自相矛盾 — scrub 擴 paraphrases、有卡必 prepend cards hint、summarize 對齊 cardFocus、fact_check 規則 8
- **遇到的問題**：
  - 問題1：GREASE满装瓶 UI 有 Crafting 卡，正文卻寫「JEI 目前沒有列出它的合成配方」且來源仍 JEI
  - 解決方案：`looksLikeAbsenceClaim` 補「沒有列出／does not list／no crafting recipe」；`chooseJeiSummaryText` 有卡時一律 prepend `jei_recipe_cards_hint`；AskService `summarize(cardFocus)` 與卡同源；prompt 規則 8 明示禁「沒有列出合成配方」
  - 狀態：⏳ 編譯／檢查中
- **備註**：無 Architectury／無 RecipeEmbed 改寫；殘差＝LLM 用更冷門改寫且單行 scrub 未命中時仍可能漏，需靠提示＋卡上材料

## [2026-08-07 13:09:12] 操作類型：修改
- **文件路徑**：forge+neo：JeiRecipeCards、JeiLookup、AiAssistantScreen、lang en/zh_tw/zh_cn；tests/check_recipe_card_layout.py
- **變更摘要**：Create 9×9 動力合成卡：槽位 cap 48→81、SHAPED 高 120→168、JEI 文字標籤 40→81；截斷時標題誠實標 truncated；縮放預覽加「開 JEI」提示
- **遇到的問題**：
  - 問題1：`golden_age:god_block` 等 81 槽配方，Pack AI 卡只顯示部分格、JEI 完整 9×9
  - 解決方案：`MAX_FLOW_INPUT_SLOTS=81`（Create 上限）；`MAX_SHAPED_CARD_H=168` 讓 9×9 近 1:1；`titleLargeGrid` 當 shown&lt;total 加 truncated；UI scale&lt;1 畫 `recipe_grid_preview`；neo `fromLayout` 改真 slot 計數（勿用截後 size）
  - 狀態：🔄 實作中
- **備註**：81 槽與 JEI 像素級同位仍可能因聊天寬度縮放（Preview gap）；文字 Name×N 完整為主。不擋 PURPOSE／牛奶 agent。無 Architectury。

## [2026-08-07 13:06:44] 操作類型：修改
- **文件路徑**：forge/1.19.2 與 neoforge/1.21.1：AskPurposeContext.java；tests/check_ask_purpose_context.py、update_reply_prompts.py、check_reply_prompt_keys.py；lang en/zh_tw/zh_cn（雙樹）；RoadmapChecks（neo）
- **變更摘要**：Ask `[PURPOSE]` 補 FoodProperties／UseAnim 飲食事實（Drinkable／Edible＋nutrition／effects）；fact_check 禁捏造「無直接使用」
- **遇到的問題**：
  - 問題1：奇蹟牛奶等可喝物，PURPOSE 僅 tooltip＋燃料／工具＋JEI U → LLM 臆造「本身沒有直接使用效果」
  - 解決方案：`itemBehaviorLines` 讀 UseAnim.DRINK/EAT＋FoodProperties（效果 cap 8）；prompt 規則 12 禁僅憑 [AS_INGREDIENT] 宣稱無用途
  - 狀態：✅ 已解決（`check_ask_purpose_context`／`check_reply_prompt_keys` OK；forge+neo `compileJava` OK；neo `compileTestJava` OK）
- **備註**：自訂 `finishUsingItem`／非 FoodProperties／非 UseAnim.DRINK|EAT 能力仍可能漏細節；CUA 可選 — 重開 instance 後 Ask 奇蹟牛奶，PURPOSE 應見 Drinkable／food 或至少不再臆造「無直接使用」

## [2026-08-07 12:56:32] 操作類型：修改 | 刪除 | 新增
- **文件路徑**：`.gitignore`；`docs/SOURCE_MAP.md`；`docs/VERSIONS.md`；`README.md`；`bridge/README.md`；`common/shared/README.md`；`.cursor/rules/cua-verify-after-finish.mdc`；刪 `mezz/**`、`META-INF/MANIFEST.MF`、`neoforge/1.21.1/runRoadmapTmp.gradle`
- **變更摘要**：Option B repo hygiene — 擴充 gitignore、清本地垃圾、文件「去哪找碼」、bridge 不搬只標 LEGACY、common/shared 強調禁止未核准抽 shared；追蹤 CUA rule
- **遇到的問題**：
  - 問題1：`bridge/` 是否搬到 `legacy/bridge/`
  - 解決方案：README／VERSIONS／lang／日誌多處引用 `bridge/` → **不搬**，加 `bridge/README.md` + 文件標 LEGACY
  - 狀態：✅ 已解決
- **備註**：無 dual-tree merge／無 Architectury／無 RecipeEmbed 改寫；**無 commit**

## [2026-08-07 10:35:23] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：PackAiConfig、PackKnowledge、AskService、ReplySources、ReplyLang、lang、mods.toml、PackAiSettingsScreen、AskEngine；tests/check_pack_knowledge.py；docs eng-review report
- **變更摘要**：PackKnowledge minimal（scope A）— truth ladder、recipeBackend、EMI detect stub、client PackKnowledge、get+use reply shape
- **遇到的問題**：
  - 問題1：design 把 PackKnowledge 放 logic/ 會撞 client-only JeiLookup
  - 解決方案：放 client.knowledge；AskEngine 只吃組好的字串／來源旗標
  - 狀態：✅ 已解決
  - 問題2：CUA smoke（Pack AI UI）
  - 解決方案：bring_to_front + foreground click 搶焦點後 `]`；截圖 dist/cua_packknowledge_packai.png 見「整合包 AI 助手」
  - 狀態：✅ 已解決（CUA PASS）
- **備註**：eng-review scope A；不抽 RecipeBackend 階層；eng report gates Architecture→Tests→Perf 已關；執行中 client 可能仍載舊 jar，本輪 UI 開屏已驗證

## [2026-08-05 18:15:26] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：PackAiConfig、AskService、PackAiSettingsScreen、ContainedItems*、lang en/zh_tw/zh_cn；README；tests ContainedItemsCheck
- **變更摘要**：Ask 設定 `unpackStoredItems`（預設 false）；開時 PURPOSE 附加 `[CONTAINED]`（shulker/bundle/常見 NBT，~20 行 cap）
- **遇到的問題**：
  - 問題1：Helper／zh_cn 字串已在，缺 config／UI／en／zh_tw／AskService 接線與 README
  - 解決方案：對齊 scanModJars 模式補 boolean＋getter/setter；purposeTooltipFor 閘門呼叫 ContainedItems.summarize；extras 經同一路徑
  - 狀態：✅ 已解決（待 compile／jar）
- **備註**：無 commit／無 CUA；forge jar → dist + Prism AI_test_NFWC_DIM（若路徑存在）

# 代碼變更與問題日誌

## [2026-08-08 19:55:11] 操作類型：修改
- **文件路徑**：README.md
- **變更摘要**：加上 CurseForge 下載連結（pack-ai-assistant-paia）
- **遇到的問題**：無
- **備註**：暫不上 Modrinth；推 origin/main


## [2026-07-28 15:25:31] 操作類型：修改
- **文件路徑**：README.md、code_change_log.md
- **變更摘要**：README Curios 過時文案：L27／可選依賴改為 Forge＋Neo soft-dep 已接 API（鏡 Forge 說法）
- **遇到的問題**：無
- **備註**：隨 fuel／ToolAction PURPOSE、Neo Curios、GuideME、jar docs 一併 commit／push

## [2026-07-28 15:15:27] 操作類型：修改
- **文件路徑**：README.md、docs/PACK_AUTHOR.md、code_change_log.md
- **變更摘要**：文件補 light jar index：`scanModJars` **預設 off**、開啟方式、快取 `config/packai/jar-cache/`、中央目錄指紋說明
- **遇到的問題**：
  - 問題1：日誌／CodeGraph 確認 Forge+Neo `.define("scanModJars", false)`；README／PACK_AUTHOR 原先無此項
  - 解決方案：純文件；不翻預設、不改 Java（YAGNI；config comment 已含指紋／cache）
  - 狀態：✅ 已解決
- **備註**：無 compile／CUA／commit；巨大 jar 整檔 skip 未加（已有 entry／per-jar cap）

## [2026-07-28 15:07:10] 操作類型：新增 | 修改
- **文件路徑**：neoforge/1.21.1：GuideMeBridge(+Impl)、GuideMePageScan、GuideMeGuideLookup、AskService、build.gradle、gradle.properties、neoforge.mods.toml；tests/check_guideme_page_scan.py；RoadmapChecks
- **變更摘要**：Neo GuideME soft-dep：焦點物品→書頁明文，併入 Ask `[GUIDE]`（與 Patchouli 並存）
- **遇到的問題**：
  - 問題1：Forge 1.19.2／NFWC 無 GuideME 合理 API（releases 僅 1.20.1+；1.21.1＝v21.1.17）
  - 解決方案：僅 Neo 1.21.1 實作；Forge 跳過
  - 問題2：`ParsedGuidePage.source` 無 public getter
  - 解決方案：Impl 反射讀 `source`；缺模組／失敗 soft-fail；另掃 `guides/**/*.md` frontmatter `item_ids`
  - 狀態：✅ 已解決（`check_guideme_page_scan` OK；neo `compileJava`／`compileTestJava` OK；Forge 1.19 跳過）
- **備註**：compileOnly `guideme:21.1.17:api`；無 runtime／CUA／jar／commit；Ask `purposeGuideFor` 合併 Patchouli＋GuideME 後 `joinCapped`；`ParsedGuidePage.source` 反射；資源掃 `guides/**/*.md`

## [2026-07-28 15:01:32] 操作類型：修改 | 新增
- **文件路徑**：neoforge/1.21.1：CuriosBridge.java、CuriosBridgeImpl.java、build.gradle、gradle.properties、neoforge.mods.toml；tests/check_curios_bridge_neo.py
- **變更摘要**：NeoForge Curios soft-dep 實作（取代 stub）：InvPick 可列／讀 accessories，鏡 Forge Class.forName 橋
- **遇到的問題**：
  - 問題1：先前 Neo stub `isLoaded=false`，有 Curios 也不顯示 accessory 列（日誌 2026-07-26 刻意 stub）
  - 解決方案：`CuriosBridge` + `CuriosBridgeImpl`（`CuriosApi.getCuriosInventory`）；compileOnly `curios-neoforge:9.5.1+1.21.1:api`；缺模組 soft-fail
  - 狀態：✅ 已解決（`check_curios_bridge_neo` OK；neo compile／jar 356449 → dist）
- **備註**：無硬依賴、無 localRuntime Curios；無 CUA（InvPick 列行為依賴有裝 Curios）；不 commit

## [2026-07-28 14:55:29] 操作類型：修改 | 新增
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：AskPurposeContext.java、AskService.java；tests/check_ask_purpose_context.py；RoadmapChecks（neo）
- **變更摘要**：Ask `[PURPOSE]` 補 Forge／Neo 真實物品行為：爐燃料 burn time + ToolAction／ItemAbility 列表
- **遇到的問題**：
  - 問題1：v1 PURPOSE 僅 tooltip／interact／Patchouli，未含燃料／工具能力 → 問「用途」時缺爐燃料與斧鋤等事實
  - 解決方案：AskService 焦點 ItemStack 上讀 burn time（Forge `ForgeHooks.getBurnTime`／Neo `ItemStack.getBurnTime`）與 `canPerformAction` 掃已註冊 actions；soft-fail；併入 purposeTooltip → `[PURPOSE]`
  - 狀態：✅ 已解決（`check_ask_purpose_context` OK；雙樹 compile／jar；forge jar 380328 → dist；neo jar 353413 → dist）
- **備註**：AskService `purposeTooltipFor` 併 tooltip+behavior；無 GUI／CUA；不開 jar index 預設；不 commit

## [2026-07-28 14:28:41] 操作類型：修改
- **文件路徑**：README.md、docs/PACK_AUTHOR.md、code_change_log.md
- **變更摘要**：文件補充可選模組 Untranslated Items（`untranslateditems`）相容說明：Pack AI 用 getHoverName() OK；中文主語系建議 `replaceItemNames=false`
- **遇到的問題**：無
- **備註**：無硬依賴、無 Java／設定開關變更；純文件，未編譯／CUA

## [2026-07-28 14:18:08] 操作類型：新增 | 修改
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：JarLightIndex、PackAiConfig、AskEngine、AskService、PackAiSettingsScreen、ReplyLang、ReplySources；lang en/zh_tw/zh_cn；tests/check_jar_light_index.py；RoadmapChecks（neo）
- **變更摘要**：可選 light jar index（`scanModJars` **預設 off**）：背景掃 `mods/*.jar` 的 recipes／loot_tables → `config/packai/jar-cache/`；Ask 焦點物品注入短 [JAR] 提示
- **遇到的問題**：
  - 問題1：NFWC 等超大包全量掃 jar 可能慢／占磁碟
  - 解決方案：預設關閉；僅 Zip 條目（不反編譯）；指紋＝zip 中央目錄 SHA-256；每 jar／每 item 有 cap；壞 jar／缺 mods 目錄 soft-skip
  - 問題2：誤把 neo `ReplyLang.current()` 拷到 forge → `LanguageInfo` 編譯錯
  - 解決方案：forge 維持 Object／反射讀 language code
  - 狀態：✅ 已解決（`check_jar_light_index` OK；雙樹 compile；forge jar 378808 → dist；neo jar 351914 → dist；已覆寫 Prism `AI_test_NFWC_DIM` + 現跑 `No_Flesh_Within_Chest-1.0.2-DIM` mods）
- **備註**：Ask 設定頁「掃描模組 jar」；開啟後 warmupAsync 掃。跳過 Untranslated／Vineflower；lang 條目可選未做。CUA 需重開 instance 才見新開關（現跑舊 classpath）

## [2026-07-28 14:05:00] 操作類型：新增 | 修改
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：PatchouliEntryScan、PatchouliBridge(+Impl)、PatchouliGuideLookup、AskPurposeContext、AskEngine、AskService、mods.toml／neoforge.mods.toml、build.gradle、gradle.properties；lang en/zh_tw/zh_cn；tests/check_patchouli_entry_scan.py；RoadmapChecks（neo）
- **變更摘要**：Patchouli soft-dep：依焦點物品查書頁文字，併入 Ask `user.purpose` 的 `[GUIDE]`（不取代 tooltip／PURPOSE）
- **遇到的問題**：
  - 問題1：公開 PatchouliAPI 無 item→entry 查詢
  - 解決方案：有模組時用 `BookContents.getEntryForStack`（recipeMappings）；否則／補強掃 ResourceManager `patchouli_books/**/entries/*.json`（icon／extra_recipe_mappings／spotlight‧crafting item）
  - 狀態：✅ 已解決（`check_patchouli_entry_scan` OK；雙樹 compile；forge jar 366118 → dist；neo jar 339170 → dist；本機無 Prism/NFWC 路徑可覆寫）
- **備註**：跳過 GuideME／Ponder／jar light index；Ask `user.purpose` = [PURPOSE]+tooltip/interact + optional [GUIDE]

## [2026-07-28 13:51:45] 操作類型：新增 | 修改
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：AskPurposeContext.java、AskEngine.java、LlmClient.java、AskService.java、ReplyLang lang en/zh_tw/zh_cn；tests/check_ask_purpose_context.py；RoadmapChecks（neo）；tests/gen_reply_lang_json.py
- **變更摘要**：Ask 用途接地：tooltip＋KubeJS 互動進 `[PURPOSE]`／user.purpose；JEI U 改標 `[AS_INGREDIENT]`（作為材料）；prompt 禁止把 JEI U 當主用途
- **遇到的問題**：
  - 問題1：玩家問「用途」時模型把 JEI 按 U 合成輸入表當功能說明
  - 解決方案：AskService TooltipCapture → AskEngine 組 purpose block；interact/desc graph 進 PURPOSE；lang llm_style／fact_check／jei_section_uses 拆用途 vs 作為材料
  - 狀態：✅ 已解決（`check_ask_purpose_context` OK；雙樹 compile；forge jar 353376 → dist + NFWC；neo jar 326516 → dist；CUA 可選／需重開 instance）
- **備註**：v1 未加 burn time／ToolAction；Patchouli 未做；Ask context 新增 user.purpose=`[PURPOSE]`+tooltip+interact/desc

## [2026-07-27 12:42:00] 操作類型：修改 | 新增
- **文件路徑**：forge/1.19.2 GuiGraphics.java；tests/check_forge_tooltip_remap.py
- **變更摘要**：Forge GuiGraphics item/text tooltip 改直接呼叫 Screen public API（可 remap），不再用字串反射
- **遇到的問題**：
  - 問題1：Pack AI GUI 物品圖示無 tooltip（strip／chat／recipe／InvPick）；按鈕 tip 正常
  - 解決方案：根因＝`invokeScreen("renderTooltip")` 在 reobf/NFWC 對 SRG 名靜默失敗；改 `getTooltipFromItem` + `renderComponentTooltip`。Neo 1.21.1 用原生 GuiGraphics，無此洞；strip 後繪＋反向命中仍在
  - 狀態：✅ 已解決（`check_forge_tooltip_remap` OK；forge jar 350750 → dist + NFWC `No_Flesh`/`AI_test` mods；reobf 見 `Screen.m_96555_`/`m_96597_`，無 `ldc "renderTooltip"`；CUA：重開後 `;` 開助手見 strip／recipe 圖示；guiScale=4 hover 座標難校準，完整 tip 外觀請本機確認）
- **備註**：MDK runClient（mapped 名）反射會「看起來正常」，正式 jar 才爆；WidgetCompat 按鈕 tip 本來就走編譯期 remap 所以一直正常

﻿# 代碼變更與問題日誌

## [2026-07-28 15:25:31] 操作類型：修改
- **文件路徑**：README.md、code_change_log.md
- **變更摘要**：README Curios 過時文案：L27／可選依賴改為 Forge＋Neo soft-dep 已接 API（鏡 Forge 說法）
- **遇到的問題**：無
- **備註**：隨 fuel／ToolAction PURPOSE、Neo Curios、GuideME、jar docs 一併 commit／push

## [2026-07-26 16:22:13] 操作類型：修改 | 新增
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：JeiLookup.java、ReplyLang.java、lang en_us/zh_tw/zh_cn；tests/check_jei_list_cap.py；RoadmapChecks（neo）
- **變更摘要**：JEI 摘要每類別最多列 3 條配方（短者優先）＋「另有 N 條—開 JEI」；fact_check／llm_style 強制精簡、禁止展開截斷列表
- **遇到的問題**：
  - 問題1：問 cursed ingot 等時 catalyst（Dark Altar）把整牆儀式配方餵進 LLM → 回覆「show too much」
  - 解決方案：capListedDetails + packai.reply.jei_cat_more；prompt 禁止列盡／展開 truncated
  - 狀態：✅ 已解決（雙樹 compile；forge jar 351261 → dist + NFWC；neo jar 323883 → dist；`check_jei_list_cap` OK；CUA 開助手 `dist/cua_jei_cap_prompt.png` — 現跑舊 classpath，重開 NFWC 才吃新 jar）
- **備註**：before＝每類列全部 unique；after＝≤3 + more；模型亦被告知勿補齊省略項

## [2026-07-26 16:11:29] 操作類型：修改 | 新增
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：JeiTargetResolver.java、AiAssistantScreen.java、AskService.java；tests/check_strip_focus_stable.py
- **變更摘要**：助手開啟時 strip／contextStack 改用穩定焦點（pin／draft id／lastAskFocus），不再吃 JEI 原料列表 live hover；Ask 成功後鎖 lastAskFocus；清聊天清 pin+lastAskFocus；關畫面只清 pin
- **遇到的問題**：
  - 問題1：問 cursed_ingot 時 JEI 旁 hover 黑暗祭壇 → strip「目標」黏到錯誤物品
  - 解決方案：`resolveStable` 略過 hover；`lastAskFocus` 在 startAsk 寫入；Clear chat／onClose 依規格清
  - 狀態：✅ 已解決（雙樹 compile；forge jar 350071 → dist + NFWC；neo jar 322743 → dist；`check_strip_focus_stable` OK；CUA：現跑 instance 仍舊 classpath — 需重開 NFWC 才吃新 jar；`dist/cua_strip_focus_stable.png`）
- **備註**：清 hover≠清 last-ask — hover 開助手時根本不進 strip；Clear chat 清 lastAskFocus+pin；關畫面只清 pin；AskService 空 stripFocus 亦 resolveStable

## [2026-07-26 15:39:17] 操作類型：修改 | 新增
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：AiAssistantScreen.java；lang en_us/zh_tw/zh_cn；tests/check_next_step_focus.py
- **變更摘要**：目標下一步／任務下一步不再預灌熱鍵欄；僅用 strip focus／pending；無目標時 toast；strip 圖示 tooltip 改後繪＋反向命中；按鈕 tooltip 說明新行為
- **遇到的問題**：
  - 問題1：askNextStep 刻意 clear+prefill hotbar+held → 側欄「目標下一步」把整排熱鍵送進 pending，AI 又偏第一件
  - 解決方案：改成與 Ask 相同 — pending 有就送 pending，否則只靠 contextStack／JEI；兩者皆空則 `packai.status.need_target`；quest_next 本來就不灌 hotbar，僅加 tooltip
  - 問題2：strip `addItemHover` 先註冊，之後 chat 面板／捲動提示蓋住圖示區，hover 命中不穩
  - 解決方案：strip 改在 chat 之後繪製；`renderHoverTooltip` 由後往前找命中；捲動提示上移到 chatBottom 上方
  - 狀態：✅ 已解決（雙樹 compile；forge jar 349834 → dist + NFWC；`check_next_step_focus` OK；CUA：重開 NFWC 後 `;` 開助手；任務下一步未灌熱鍵（目標仍空）；兩鈕 tooltip 見「不會送出／灌入快捷欄」；`dist/cua_next_toast.png`／`cua_next_need_target.png`）
- **備註**：此 instance `key.packai.open` 綁 semicolon 非 `]`；目標下一步空目標 toast 為 action bar，助手 GUI 可能蓋住

## [2026-07-26 15:30:00] 操作類型：修改 | 新增
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：AskService.java、AiAssistantScreen.java、ReplyLang.java、AskJeiHints.java、lang en/zh_tw/zh_cn；AskJeiHintCheck.java
- **變更摘要**：Ask 焦點與 strip `contextStack()` 統一（傳入 ItemStack，不再用完整 question 重解）；template/Y-hold/regen pin 對齊；有 recipe cards 時不注入 jei_no_recipes／假「無配方」摘要
- **遇到的問題**：
  - 問題1：strip 用 `resolve(draft/hover)`，AskService 用 `resolve(question)`（含 mod:id）→ 文字與卡片／預覽可對不同物品；forge JEI 空摘要仍可 `fromVanillaCrafting` 出卡 → 文字說無配方、卡顯示合成
  - 解決方案：`askAsync(..., stripFocus)`；`AskJeiHints.chooseJeiSummaryText` 有卡則替換 absence；template arg1 為 id 時 pin
  - 狀態：✅ 已解決（雙樹 compile；AskJeiHintCheck OK；forge jar 349651 → dist + NFWC mods）
- **備註**：MC 已在跑舊 classpath — 需重開 instance 才吃新 jar；CUA 完整煙測可選
## [2026-07-26 15:20:00] 操作類型：修改
- **文件路徑**：README.md
- **變更摘要**：同步近期功能（雙線、四頁籤設定、選物品／Picked、隱藏升級配方、zh_cn、Curios soft-dep、去掉自動 held／hotbar）
- **遇到的問題**：
  - 問題1：README 仍寫「單一 NeoForge」與手上熱鍵欄舊行為
  - 解決方案：改寫玩家／行為／相容／設定表
  - 狀態：✅ 已解決
- **備註**：commit 後 push origin/main

## [2026-07-26 14:23:25] 操作類型：修改 | 新增
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：AiAssistantScreen.java；lang en_us/zh_tw/zh_cn（`packai.screen.picked_n`）
- **變更摘要**：輸入列 strip 顯示 InvPick `pendingItems` 多圖示（最多 8）＋`Picked: N`；JEI focus 若不在 pending 則前置圖示並保留 `Targeted: X`
- **遇到的問題**：
  - 問題1：ItemRef 僅 id+displayName，strip 圖示需 `ItemResolver.stackFromId`（NBT 損失可接受）
  - 解決方案：pending 用 stackFromId；focus 用 contextStack 完整 stack；已在 pending 的 focus 不重複畫
  - 狀態：✅ 已解決
- **備註**：compile 雙樹；forge jar 347399 → dist + NFWC；CUA PASS `dist/cua_picked_14_strip.png`（三圖示 + Picked: 3）

## [2026-07-26 12:58:28] 操作類型：新增 | 修改

- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：JeiFocusMatch、JeiRecipeCards、JeiLookup、PackAiConfig、PackAiSettingsScreen、lang en_us/zh_tw/zh_cn；tests/check_jei_upgrade_filter.py
- **變更摘要**：JEI 配方收集預設隱藏「焦點物品同 registry id 同時出現在 INPUT 與 OUTPUT」的升級型配方；設定 `hideUpgradeRecipes` 預設 true
- **遇到的問題**：
  - 問題1：Arcane anvil 等升級配方干擾 Ask 卡／JEI 摘要
  - 解決方案：`JeiFocusMatch.focusAppearsAsInputAndOutput` 以槽位角色判定，非標題字串；config 可關
  - 狀態：✅ 已解決
- **備註**：compile 雙樹；forge jar → dist + NFWC；CUA 視可行

## [2026-07-26 12:43:14] 操作類型：診斷（無代碼變更）

- **文件路徑**：forge/1.19.2 `assets/packai/lang/zh_cn.json`、`AiAssistantScreen.java`、NFWC `mods/packai-1.19.2-forge.jar`、`ReplyLang.java`
- **變更摘要**：診斷 zh_cn 遊戲語系下 Pack AI 側欄仍英文（Ask／Clear chat／Pack AI Assistant 等）
- **遇到的問題**：
  - 問題1：側欄／標題英文與 en_us 完全一致
  - 解決方案／結論：非 hardcode、非 ReplyLang UI 強制英文；`zh_cn.json` 已是簡體（提問／清除对话／选物品…）且與 zh_tw 對應、非 en_us 拷貝；畫面用 `Component.translatable`；NFWC jar 含同內容 `assets/packai/lang/zh_cn.json`（與 src SHA 一致）。根因是選 zh_cn 時若缺該檔，MC 只回落 en_us（不會用 zh_tw）——今日 11:45 已補檔並於 11:54 部署 jar。CUA 在已選「简体中文」時 tooltip 仍見英文 Hold y…（`dist/cua_zhcn_ui_check.png`），若重開／F3+T 後仍英，再查 ModernFix PathResourcePack＋lightspeed-cache。
  - 狀態：✅ 語系檔內容已正確，本次不改檔；剩餘為執行期資源套用確認
- **備註**：options.txt `lang:zh_cn`；ReplyLang.tr 僅回覆字串把所有 zh_* 指到 zh_tw bundle，不影響側欄 UI

## [2026-07-26 11:45:00] 操作類型：新增 | 修改
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：AiClientCommands.java、forge QuestBookOpener.java、lang en_us/zh_tw/zh_cn
- **變更摘要**：硬編碼稽核 — `/ai` 與 quest fallback 改 lang；新增簡體 `zh_cn.json`（312 keys，與 zh_tw/en 齊）
- **遇到的問題**：
  - 問題1：GUI InvPick／Targeted／Pick items 等已走 `Component.translatable`；殘留 `[Pack AI] …` 與 forge quest fallback 字面量
  - 解決方案：新 key `packai.command.thinking`／`reply`、`packai.status.quest_cmd_fallback`；zh_cn 由 zh_tw 轉簡體＋大陸用詞
  - 狀態：✅ 已解決
- **備註**：`mod/` 無 lang 樹可略；CUA 跳過。Forge jar 335693 → dist + NFWC mods；Neo compileJava+processResources OK。URL hint／數字 CycleButton／動態回覆本體保留 literal

## [2026-07-26 09:30:00] 操作類型：修改
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：lang en_us/zh_tw
- **變更摘要**：側欄「Held next」改 Targeted next／目標下一步；ask.held_next 問句同步（key 名不變）
- **遇到的問題**：
  - 問題1：strip 已 Targeted，側欄仍 Held next，語意矛盾
  - 解決方案：改 next_step／next_step_short／ask.held_next 文案；不動 AskEngine held* API
  - 狀態：✅ 已解決
- **備註**：純 lang；forge processResources+jar → dist/packai-1.19.2-forge.jar（326049）並覆寫 NFWC mods；neo processResources。CUA strip PASS `dist/cua_targeted_30_before.png`／`32_reopen.png` 見 Targeted: Coarse Dirt；側欄仍 Held next（runClient 記憶體 lang，F3+T 未進；重開 runClient／NFWC 才見 Targeted next）

## [2026-07-26 09:05:00] 操作類型：修改
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：AiAssistantScreen.java、JeiTargetResolver.java、lang en_us/zh_tw
- **變更摘要**：輸入列狀態由 Held 改為 Targeted（JEI pin／hover／問題內 id）；resolve 不再自動回落主手
- **遇到的問題**：
  - 問題1：UI 顯示 Held: (empty)，與 Ask 的 pin／勾選焦點語意不一致
  - 解決方案：contextStack 只走 JeiTargetResolver；去掉 resolve 的 held fallback；新 key packai.screen.targeted_item；空狀態沿用 held_empty
  - 狀態：✅ 已解決
- **備註**：未恢復 sendHeld；CUA PASS `dist/cua_targeted_11_strip.png` 見 Targeted: Coarse Dirt；NFWC jar 已覆寫，需重開 instance

## [2026-07-26 09:01:17] 操作類型：修改
- **文件路徑**：forge/1.19.2：InvPickScreen.java、GuiGraphics.java、PackAiSettingsScreen.java、PackAiConfig.java
- **變更摘要**：Forge 對齊 Neo 三小缺口 — InvPick 數量 overlay、quest_match_hotbar tooltip、setQuestMatchHotbar SPEC.save()
- **遇到的問題**：
  - 問題1：InvPickScreen 未顯式 renderItemDecorations；設定按鈕缺 tooltip；setter 未 save
  - 解決方案：GuiGraphics 加薄 wrapper；CycleButton.withTooltip（1.19.2 回傳 List FormattedCharSequence）；setter 後 SPEC.save()
  - 狀態：✅ 已解決
- **備註**：lang key 已存在且與 Neo 一致；純 cosmetic／設定持久化，可跳 CUA

## [2026-07-26 04:35:00] 操作類型：新增 | 修改 | 刪除
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：InvPickScreen、ChatSession、AskService、AiAssistantScreen、PackAiConfig、PackAiSettingsScreen、lang；tests/check_inv_pick_focus.py
- **變更摘要**：背包多選（熱鍵／主背包／盔甲／副手）取代 sendHeld／sendHotbar；Ask 只用勾選物品＋JEI pin；不動 Y／ThinkHold
- **遇到的問題**：
  - 問題1：自動送 held／hotbar 易拉無關任務
  - 解決方案：pendingItems 多選；空選＝只問題／JEI；「下一步」預勾熱鍵欄＋手持
  - 狀態：✅ 已解決
- **備註**：cap 8；questMatchHotbar 改對「勾選 extras」計分

## [2026-07-26 03:20:00] 操作類型：修改 | 新增
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：PackAiConfig、PackAiSettingsScreen、AskEngine、AskService、AiAssistantScreen、QuestGuide、lang；tests/check_quest_match_extras.py
- **變更摘要**：相關任務不再因 hotbar 單獨 +8 誤配；設定可選是否送持物／快捷欄、是否附加相關任務、任務是否比對快捷欄
- **遇到的問題**：
  - 問題1：配方答覆下出現 coin gold／chestopener 等無關任務（hotbar item ∈ quest.items → score 8）
  - 解決方案：純 extras 命中丟棄；預設 questMatchHotbar=false；GUI 四開關（sendHeld／sendHotbar／attachRelatedQuests／questMatchHotbar）
  - 狀態：✅ 已解決
- **備註**：「下一步」仍強制 includeHotbar=true；一般送出跟 sendHotbar。設定 GUI 壓密＋Done 旁放 Quests←hotbar（Done 離開時存 key/url）。CUA：`dist/cua_quest_ctx_settings_final.png` 見四開關預設。Prism `AI_test_NFWC_DIM` 已覆寫 jar，重開 instance 才吃到

## [2026-07-26 02:57:00] 操作類型：修改
- **文件路徑**：docs/examples/packai_AGENTS.md、docs/PACK_AUTHOR.md
- **變更摘要**：進度 A — 把 ITEM_SOURCE_LOOKUP §9（＋§6 輸出提示一句）節錄進範例 AGENTS；PACK_AUTHOR 加連結提醒作者可抄 §9
- **遇到的問題**：
  - 問題1：無（純文件切片）
  - 解決方案：N/A
  - 狀態：✅ 已解決
- **備註**：未做 B/C 引擎／無 Java 變更；整檔仍遠低於 PackAuthorAgents MAX_CHARS=4000

## [2026-07-26 02:50:00] 操作類型：修改 | 新增
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：JeiRecipeLayoutCollector、JeiRecipeCards、JeiLookup、IngredientReqHints、ReplyLang、lang；tests/check_jei_alt_collapse.py
- **變更摘要**：JEI tag／多選槽不再展平成多個 AND 輸入 — 每槽只顯示一個樣本 + `#tag`／「任選其一 (N)」
- **遇到的問題**：
  - 問題1：flow 卡／文字把 `#kubejs:mrqx_cpu` 等 tag 槽的全部 alternatives 列成必要材料
  - 解決方案：Forge layout 每 slot 取一樣本；NeoForge 對 flat list 依共用 tag 摺疊；IngredientReqHints 標 tag／any-of；focus match 仍用全量 stacks
  - 狀態：✅ 已解決
- **備註**：crafting 3×3 仍走 Ingredient 格點，不受影響

## [2026-07-26 02:42:29] 操作類型：新增 | 修改
- **文件路徑**：neoforge/1.21.1 與 forge/1.19.2：QuestGuide.java、PackAiConfig.java、PackAiSettingsScreen.java、PackIndex.java、lang en_us/zh_tw；QuestGuideIdCheck.java；docs/VERSIONS.md
- **變更摘要**：Anti-spoiler — 預設不揭露 FTB hide/invisible/deps-gated（及 Heracles hidden）任務；設定 `showHiddenQuests`（GUI 可切）
- **遇到的問題**：
  - 問題1：QuestGuide 索引無 hide 過濾，會劇透猜測包隱藏任務
  - 解決方案：解析 depth-1 旗標（hide、invisible、hide_until_deps_*、hide_quest_until_deps_visible、invisible_until_tasks、hidden）；章節 hide_quest_until_deps_visible 時略過有 dependencies 的任務；lang 合併後再依 spoilerIds 剔除；PackIndex snippet redact；設定預設 false
  - 狀態：✅ 已解決
- **備註**：勿過濾 hide_dependency_lines / hide_text_until_complete（非整任務隱藏）

## [2026-07-26 01:30:00] 操作類型：修改
- **文件路徑**：forge/1.19.2/build.gradle；forge/1.19.2/gradle.properties
- **變更摘要**：修 Prism `NoSuchFieldError: EMPTY`（ThinkHoldTracker clinit）— 根因是 `jar` 產出未 reobf（Mojmap `ItemStack.EMPTY`），Forge 1.19.2 runtime 要 SRG；`jar` finalizedBy `reobfJar`，forge 依賴範圍改 `[43,)`
- **遇到的問題**：
  - 問題1：crash-2026-07-25_17.11.51-fml — `ThinkHoldTracker.<clinit>:15` → `NoSuchFieldError: EMPTY`（`ItemStack.EMPTY`）
  - 解決方案：源碼常數正確；javap 證實 mods jar 仍為 Mojmap `EMPTY`；改強制 reobfJar，重裝 dist + Prism mods
  - 狀態：✅ 已解決
  - 問題2：後續 crash 曾報 `requires forge 43.4.0`（instance 一度 43.3.5）
  - 解決方案：`forge_version_range=[43,)`；compile 仍用 43.4.0
  - 狀態：✅ 已解決
- **備註**：取消「再開 Prism 測」敘事直到本 jar 裝上；請重啟 instance 驗證載入

## [2026-07-25 22:53:00] 操作類型：修改
- **文件路徑**：forge/1.19.2/src/main/java/com/skps9/packai/client/jei/JeiLookup.java
- **變更摘要**：對齊 NeoForge 1.21.1 JEI text dump caps：MAX_SCAN_PER_CAT 200→2000，移除 MAX_LINES_PER_SECTION=24，unique 行全印到 maxJeiChars
- **遇到的問題**：
  - 問題1：Forge dump 每 section 只 24 行、每 cat 只掃 200，與 1.21.1 不符
  - 解決方案：常數與 appendSection 輸出迴圈對齊 NeoForge；保留 spam/universal skip
  - 狀態：✅ 已解決
- **備註**：配方卡仍 3 張（AskService）；docs/VERSIONS.md 未提 line caps 故不改

## [2026-07-25 22:15:00] 操作類型：修改 | 新增
- **文件路徑**：forge/1.19.2（AskEngine、JeiRecipeCards、JeiFocusMatch、JeiLookup、IngredientReqHints、build.gradle、lang）；neoforge/1.21.1（AskEngine、JeiLookup、IngredientReqHints、lang）；tests/check_focus_label_prefer.py
- **變更摘要**：A+B+C — JEI dump 補 LLM tip／runClient 傳 PACKAI_API_KEY；配方卡 layout 失敗仍 tryCrafting＋vanilla fallback；uses 用 Ingredient.test＋prefer-focus 標籤（修 oak 代 spruce）
- **遇到的問題**：
  - 問題1：無 LLM 時整段 JEI 原文當 AI 回覆，像沒有 1.21.1 對話感
  - 解決方案：hasJei fallback 附加 tipNeedLlm；gradle run 透傳 env key；文案提 PACKAI_API_KEY
  - 狀態：✅ 已解決
  - 問題2：layout collect 失敗直接 continue，卡片永遠空
  - 解決方案：失敗仍 tryCrafting；JEI 空則 vanilla RecipeManager crafting 卡
  - 狀態：✅ 已解決
  - 問題3：#planks 用途列成 Oak Planks
  - 解決方案：roleMatchesFocus 加 crafting Ingredient.test；labelForIngredient(prefer) 用 focus 顯示名
  - 狀態：✅ 已解決
- **備註**：需 `build-jdk17.bat jar` 後重開 1.19.2 驗證

## [2026-07-25 15:00:00] 操作類型：新增 | 修改
- **文件路徑**：forge/1.19.2/src（gui shim、全屏 UI、JEI flow、mixin、QuestBook）、docs/VERSIONS.md
- **變更摘要**：Gap 全開：UI 對齊 1.21.1（GuiGraphics shim）、flow 卡、gate、任務書指令、ScreenMixin、Seasons/Psi
- **遇到的問題**：
  - 問題1：1.19.2 無 Mojang GuiGraphics／JEI IIngredientSupplier
  - 解決方案：自製 GuiGraphics；JEI 改走 `IRecipeLayoutBuilder` collector；Quest 改用 LocalPlayer command + packet fallback；mixin 強制展開 tooltip
  - 狀態：✅ 已解決（`.\build-jdk17.bat compileJava`／`.\build-jdk17.bat jar` 成功；`dist/packai-1.19.2-forge.jar`）
- **備註**：保持與 neoforge/1.21.1 同一版面結構；Forge fluid sprite 由 shim 走 tint fallback

## [2026-07-25 14:20:00] 操作類型：新增 | 修改
- **文件路徑**：forge/1.19.2（JEI11／tooltip／ClientSetup／build.gradle）、docs/VERSIONS.md、docs/RELEASE.md、docs/PUBLISH.md
- **變更摘要**：Parity：JEI11 hold-Y、R/U 摘要、配方卡 best-effort；矩陣標 Supported＋gaps；文件 jar 命名
- **遇到的問題**：
  - 問題1：DataComponents／GuiGraphics／RecipeHolder 屬 1.20+／1.21
  - 解決方案：NBT tags 比較；IngredientReqHints 精簡；配方卡文字／簡圖；PoseStack UI
  - 狀態：✅ 已解決（JEI11 compile／jar 綠；R/U＋crafting 卡＋hold-Y；`dist/packai-1.19.2-forge.jar`）
- **備註**：gaps 寫進 docs/VERSIONS.md；RELEASE/PUBLISH jar 命名 `+mc…-forge/neoforge`

## [2026-07-25 14:05:00] 操作類型：新增 | 修改 | 刪除
- **文件路徑**：forge/1.19.2/src/**、forge/1.19.2/gradle.properties、docs/VERSIONS.md
- **變更摘要**：MinPlay Preview：1.19.2 助手/設定/Ask；JEI/mixin/重 GUI  stub 或砍掉（Parity 再補）
- **遇到的問題**：
  - 問題1：1.19.2 無 GuiGraphics／DataComponents；全量 copy 編譯不過
  - 解決方案：PoseStack 最小 GUI；JEI stub；mixin 延後
  - 狀態：✅ 已解決（`compileJava`／`jar` 綠；`dist/packai-1.19.2-forge.jar` ~194KB Preview）
- **備註**：不抽 common/shared；JEI／hold-Y／配方卡 = Parity

## [2026-07-25 13:10:00] 操作類型：新增 | 修改
- **文件路徑**：settings.gradle、build.gradle、gradle/、neoforge/1.21.1/、forge/1.19.2/、docs/VERSIONS.md、props/、common/shared/README.md、README.md；`mod/` 若仍在則為鎖檔殘留
- **變更摘要**：Skeleton monorepo：根編 NeoForge 1.21.1；Forge 1.19.2 hello（獨立 Gradle 7.6.4+JDK17）
- **遇到的問題**：
  - 問題1：`mod/` Move-Item 被程序鎖；改 Copy 到 neoforge/1.21.1
  - 解決方案：根建置指向新路徑；`mod/MOVED.md`；解鎖後刪 `mod/`
  - 狀態：✅ 已解決（daemon stop 後刪除 `mod/`）
  - 問題2：FG5 不支援 Gradle 8+／Java21 跑 daemon
  - 解決方案：forge 用 Gradle 7.6.4 + `build-jdk17.bat`
  - 狀態：✅ 已解決
- **備註**：見 docs/VERSIONS.md；不抽 common/shared；根 `.gitignore` 改跟新目錄

## [2026-07-25 12:22:16] 操作類型：新增
- **文件路徑**：docs/ITEM_SOURCE_LOOKUP.md
- **變更摘要**：新增通用「整合包物品取得途徑」檔案追查流程（給人／給 Pack AI／給 Agent）
- **遇到的問題**：無
- **備註**：從 No Flesh Within Chest 查异象石的實作經驗抽象而來

## [2026-07-25 09:29:00] 操作類型：新增
- **文件路徑**：CLAUDE.md
- **變更摘要**：新增 gstack Skill routing 規則（開發時用，不進 mod jar）
- **遇到的問題**：無
- **備註**：office-hours 設定；未 commit

## [2026-07-23 07:18:53] 操作類型：修改
- **文件路徑**：PackIndex、ReplyLang、AskEngine、lang、tests/check_script_interact.py
- **變更摘要**：擴大腳本互動：左/右鍵、破壞、實體互動、食用、舊版 onEvent；via 標籤
- **遇到的問題**：
  - 問題1：via:right_click 被 isNoiseItemId 濾掉
  - 解決方案：afterKey 對 via 允許非 item id
  - 狀態：✅ 已解決
- **備註**：仍需腳本裡有可辨識的 give/目標 id

## [2026-07-23 07:05:33] 操作類型：新增 | 修改
- **文件路徑**：PackIndex、AskEngine、ReplyLang、zh_tw/en_us、tests/check_script_interact.py、RoadmapChecks
- **變更摘要**：解析 KubeJS BlockEvents/ItemEvents.rightClicked，抽成手持+方塊→產物事實給 AI
- **遇到的問題**：無
- **備註**：需 held+block+give/類型式；非任意 JS 邏輯

## [2026-07-23 01:31:49] 操作類型：新增 | 修改
- **文件路徑**：PackAiConfig、IngredientReqHints、JeiLookup、PackAiSettingsScreen、zh_tw/en_us、tests/check_ingredient_req_hints.py
- **變更摘要**：通用 ingredientNbtPolicy（auto/always/never）：Ingredient.test(裸堆)通過則不附樣品 NBT；可設 skip 樣式；預設不採 tooltip 當門檻
- **遇到的問題**：
  - 問題1：JEI 樣品 tooltip（儲能／Eterna）被當成硬性合成條件
  - 解決方案：bare Ingredient.test + 可設 skip + tooltipAsReq 預設 false
  - 狀態：✅ 已解決
- **備註**：不綁模組品牌；skip 可在 toml 調

## [2026-07-23 00:43:20] 操作類型：新增 | 修改
- **文件路徑**：RecipeEmbed.java、AiAssistantScreen.java、zh_tw.json、en_us.json、tests/check_recipe_embed.py
- **變更摘要**：回覆中插入 JEI 配方卡（{{RECIPE}}／{{RECIPE:n}}；無標記則插在第一段後、來源前）
- **遇到的問題**：無
- **備註**：選項 C；標記不顯示給玩家

## [2026-07-23 00:17:05] ???????
- **????**?README.md
- **????**??????????????????NBT?? id ???ReplyLang ??????????
- **?????**??
- **??**????????????? GitHub


## [2026-07-23 00:30:00] ??????? | ??
- **????**?ReplyLang.java?zh_tw.json?en_us.json?tests/gen_reply_lang_json.py
- **????**?ReplyLang ?????? lang JSON?packai.reply.*?????? Java ?????? pick
- **?????**??
- **??**?classpath ???????????????? gen_reply_lang_json.py ???


## [2026-07-23 00:20:00] ???????
- **????**?CraftPriority?ReplyLang.llmStyle?JeiUniversalSpam?RoadmapChecks?tests/check_craft_priority_generic.py
- **????**???????????Create?Mekanism?????????? spam ???????prompt ? NBT????????
- **?????**??
- **??**?????????????????????


## [2026-07-23 00:15:00] ???????
- **????**?IngredientReqHints?ReplyLang?tests/check_ingredient_req_hints.py
- **????**????????? NBT????tooltip ???prompt ?????? NBT????????
- **?????**??
- **??**?NBT ????? key?value ???????? component


## [2026-07-23 00:10:00] ???????
- **????**?IngredientReqHints?ReplyLang?tests/check_ingredient_req_hints.py
- **????**?JEI ?????????????DataComponents.ENCHANTMENTS?STORED?
- **?????**??
- **??**??? 4 ?????? component ??? tooltip ????


## [2026-07-23 00:05:00] ??????? | ??
- **????**?IngredientReqHints?JeiLookup?ReplyLang?tests/check_ingredient_req_hints.py
- **????**?JEI ????????????????? NBT ? tooltip???? AI ??
- **?????**?
  - ??1????? hoverName???????? bladeState?tooltip ???
  - ?????richLabel ?? RepairCounter?kill?proud ? tooltip ????? jei ? LLM
  - ???? ???
- **??**????????? stack????????????? LLM ??


## [2026-07-22 23:50:00] ??????? | ??
- **????**?JeiTargetResolver?JeiFocusMatch?JeiLookup?JeiRecipeCards?ThinkHoldTracker?ReplyLang?tests/check_jei_focus_name.py
- **????**??? SlashBlade ? id??? NBT ?? JEI ??????AI ? JEI ??
- **?????**?
  - ??1???? slashblade:slashblade ??? NBT ?? stack ? JEI?????????LLM ?????
  - ?????????????????? stack?????????components ????????? JEI???
  - ???? ???
- **??**?????? pin ?? stack???????????????


## [2026-07-22 23:40:00] ??????? | ??
- **????**?WebSearchSettingsScreen?PackAiSettingsScreen?PackAiConfig?lang?README
- **????**??????????????? + Tavily?Serper ??
- **?????**??
- **??**????????????????


## [2026-07-22 23:35:00] ???????
- **????**?AskEngine?WebSearch?ReplyLang?PackAiConfig?README?tests/check_web_local_only_allow.py
- **????**?local_only????????????????????????
- **?????**??
- **??**???? policy=local_only ?? WebSearch


## [2026-07-22 23:25:00] ???????
- **????**?PackAiSettingsScreen?RecipeCategoryScreen?ModelPickerScreen?zh_tw.json?en_us.json
- **????**????????????? tooltip??????
- **?????**??
- **??**?CycleButton ? withTooltip?Button?EditBox ? Tooltip.create
## [2026-07-22 21:55:00] ??????? | ??
- **????**?PackAiConfig?RecipeCategoryPrefs?JeiCategoryCatalog?RecipeCategoryScreen?JeiLookup?JeiRecipeCards?PackAiSettingsScreen?lang?README?tests/check_recipe_category_prefs.py
- **????**??????? JEI ???????????????????????????
- **?????**??
- **??**???????????????????????? preferObtain ???

## [2026-07-22 19:40:00] ???????
- **????**?PackAiConfig?JeiLookup?ChatSession?AskEngine?LlmClient?PackAiSettingsScreen?lang?README
- **????**??? token ?????maxJeiChars?historyTurns?maxFacts?toml + Mods ????
- **?????**??
- **??**?????????12000?8?24????????

## [2026-07-22 19:30:00] ???????
- **????**?WebSearch.java?PackAiConfig.java?README.md
- **????**?? Tavily/Serper key ?????? Modrinth + Minecraft Wiki ???allowWebSearch ????
- **?????**??
- **??**?? key ??? Tavily?Serper?Modrinth ? User-Agent

## [2026-07-22 19:25:00] ??????? | ??
- **????**?ItemResolver?ReplyLang?SuggestIcons?AiAssistantScreen?lang?code_change_log.md
- **????**??????????????JEI ???????? `mod:id|????`???? id ???? NBT
- **?????**?
  - ??1?????? id ? richHint ????????????
  - ??????????????JEI ???????LLM ??? id|???
  - ???? ???
- **??**????????? id ??

## [2026-07-22 19:15:00] ??????? | ??
- **????**?RecipeCard.java?JeiLookup.java?AskResult.java?ChatMessage.java?ChatSession.java?AskService.java?AiAssistantScreen.java?lang?code_change_log.md
- **????**?????? JEI ????????? 3?3?????????????
- **?????**??
- **??**????? 3 ???????? JEI ?????? FLOW ??

## [2026-07-22 19:02:00] ???????
- **????**?ChatMessage.java?ChatSession.java?AiAssistantScreen.java
- **????**?????????? ItemStack ?????????? id ???? SlashBlade ?????????
- **?????**?
  - ??1?????????????????????????????
  - ?????user ??? heldIcon=stack.copy()????? id ? pin?held ???????
  - ???? ???
- **??**???????? id ??????????????LLM ?? registry id?

## [2026-07-22 18:55:00] ???????
- **????**?AiAssistantScreen.java
- **????**????? scissor ????????????? ? ????????????????
- **?????**?
  - ??1???????????iconRow ????????????????
  - ????????? renderItem???????????????
  - ???? ???
- **??**???????? stride????????

## [2026-07-22 18:50:00] ???????
- **????**?AiAssistantScreen.java
- **????**????????? scissor ?? + ????????????????
- **?????**?
  - ??1??????? GUI ???? 16?16??????????????????
  - ???????? enableScissor(16?16)????? stride ?? 18????? ICON_COL
  - ???? ???
- **??**?? jar ????

## [2026-07-22 18:10:00] ???????
- **????**?PonderStyle.java?PackAiTooltipHandler.java?ClientSetup.java
- **????**?????? tooltip ?????????? Ponder????????
- **?????**??
- **??**?NeoForge `RenderTooltipEvent.Color` setBorderStart/End?? Ponder ???

## [2026-07-22 10:30:00] ???????
- **????**?ThinkHoldTracker?PackAiTooltipHandler?PonderStyle?ClientSetup?AiAssistantScreen?ChatSession?lang
- **????**?busy ????????????tooltip????? partial-tick lerp??? GUI ?? chatLines
- **?????**?
  - ??1?AI ??????? Y????????? wrap?ItemResolver ????
  - ?????busy ?? hold??? Ponder ? prev/current lerp?ChatSession generation ????
  - ???? ???
- **??**?Ponder ? LerpedFloat + getPartialTicksUI?????? lerp?? jar ????

## [2026-07-22 10:22:00] ???????
- **????**?ClientSetup.java?AiAssistantScreen.java
- **????**?AI ????? Y ????? GUI??????????????????
- **?????**?
  - ??1?? GUI ? AI ? busy ????? Y ? toast?????????
  - ?????busy ??? setScreen(AiAssistantScreen)?openAndAskAbout ???????????
  - ???? ???
- **??**?? jar ??????

## [2026-07-21 22:40:00] ??????? | ??
- **????**?ThinkHoldTracker?PonderStyle?PackAiTooltipHandler?ClientSetup?ThinkProgressBar????README.md
- **????**?????? Create Ponder ?????? hint ? `|` ????deferred tick ? tooltip ?????
- **?????**??
- **??**??? https://github.com/Creators-of-Create/Ponder `PonderTooltipHandler.java`

## [2026-07-21 22:25:00] ???????
- **????**?ThinkHoldTracker.java?PackAiTooltipHandler.java?ClientSetup.java
- **????**????? Y ???????? GatherComponents ???? bar?hover ??????????? registry id
- **?????**?
  - ??1?????? GatherComponents ? gatherBarAdded ?????? bar?? hint ????
  - ??????? tick ? gather??????? bar?hint?JEI hover ?? sticky target
  - ???? ???
- **??**?? jar ??????

## [2026-07-21 21:15:00] ???????
- **????**?GameContextCollector?AskService?JeiTargetResolver?JeiLookup?LlmClient?ReplyLang?QuestGuide
- **????**??? AI ?????????+registry id ? LLM????? mod:id ?????????? fact-check???????????
- **?????**?
  - ??1??? tooltip ? displayName?fact-check ???????????? token ??????
  - ??????? name?tooltip?heldItem ? id+name?JEI ??? id????????????quest score?8
  - ???? ???
- **??**??????????????????????????

## [2026-07-21 21:10:00] ???????
- **????**?ChatSession.java?AiAssistantScreen.java?ReplyLang.java
- **????**????????????????????LLM ???????????????
- **?????**?
  - ??1?????????????????????????
  - ?????LastAsk ? templateKey?regen ???? lang ??????????????
  - ???? ???
- **??**??????????????? LLM??????????

## [2026-07-21 21:00:00] ???????
- **????**?ReplyLang.java?AskEngine.java?QuestGuide.java?AskService.java?AiAssistantScreen.java?SeasonContext.java?PsiHelper.java?PackIndex.java?WebSearch.java?JeiLookup.java?JeiUniversalSpam.java?CraftPriority.java?LlmClient.java?en_us.json?zh_tw.json
- **????**?? Pack AI ??????????LLM ???? ReplyLang ? lang ???????JEI???????????????
- **?????**??
- **??**?????? preset ???????????????

## [2026-07-21 20:55:00] ??????? | ??
- **????**?ReplyLang.java?ReplySources.java?QuestGuide.java?AskEngine.java?LlmClient.java?Plainify.java?RoadmapChecks.java?QuestDisplayNameCheck.java
- **????**?????????????????????????????????en ? related quest / [Sources]?
- **?????**?
  - ??1???????????? en_us?? displayTitle ??????????ReplySources ?????????
  - ??????? ReplyLang??????????? replyLang????????
  - ???? ???
- **??**????????Beeshelf ???????????????

## [2026-07-21 17:00:00] ???????
- **????**?AiAssistantScreen.java
- **????**?????????????????????????????
- **?????**?
  - ??1??????????????????????
  - ?????? `rows` ? 2 ?? 3?????????????
  - ???? ???
- **??**?`compileJava` ???

## [2026-07-21 16:57:00] ???????
- **????**?PackAiTooltipHandler.java?PonderStyle.java
- **????**??? Y ??????? Pack AI ?????????? tooltip
- **?????**??
- **??**?GatherComponents ?? hint ?????? hint ??????

## [2026-07-21 16:26:19] ???????
- **????**?SeasonContext.java?ModScanners.java?PsiHelper.java?AskService.java?RoadmapChecks.java?README.md
- **????**??????? mod ?????? `sereneseasons` ?????????????????Psi ??? modIds
- **?????**??
- **??**?????????????? Farmer's Delight ????

## [2026-07-21 16:22:00] ???????
- **????**?AiAssistantScreen?ClientSetup?README.md?code_change_log.md
- **????**???????JEI ????????? Y ???
- **?????**??
- **??**?JEI ????????/JEI tooltip ?? Y

## [2026-07-21 16:20:00] ???????
- **????**?PonderStyle?PackAiTooltipHandler?lang?code_change_log.md
- **????**?Ponder ?????????? Y ? tooltip ?????????????? tooltip?
- **?????**??
- **??**?JEI ??? GatherComponents ? Pre ?????? tooltip

## [2026-07-21 16:15:00] ???????
- **????**?README.md?code_change_log.md
- **????**??? README?Y ??????????????????????????
- **?????**??
- **??**?? git push ????

## [2026-07-21 16:10:00] ???????
- **????**?ThinkProgressBar?PackAiTooltipHandler?ThinkHoldTracker?ClientSetup?lang?code_change_log.md
- **????**?????? tooltip ???????JEI ????????? Y ????????
- **?????**?
  - ??1?JEI tooltip ?? GatherComponents???????
  - ?????RenderTooltipEvent.Post ???????
  - ???? ???
- **??**????????? Y ?? JEI ??

## [2026-07-21 16:05:00] ???????
- **????**?ClientSetup?JeiTargetResolver?TooltipHover?PackAiTooltipHandler?lang?code_change_log.md
- **????**??? Y ?????tooltip ?????????? Y ??????? JEI ??????????? toast ??
- **?????**?
  - ??1???? Y ? JEI API ??????????
  - ?????TooltipHover ?? + consumeClick ???? + ????
  - ???? ???
- **??**??? GUI???/JEI???????????

## [2026-07-21 16:00:00] ??????? | ??
- **????**?ReplySources.java?AskEngine.java?LlmClient.java?RoadmapChecks.java?code_change_log.md
- **????**??? AI ???????????LLM ????? JEI?????????????????
- **?????**??
- **??**?API ???????????????

## [2026-07-21 15:56:00] ???????
- **????**?ClientSetup.java?code_change_log.md
- **????**?JEI????????????? F ?? Y
- **?????**??
- **??**?tooltip ????????????

## [2026-07-21 15:52:00] ??????? | ??
- **????**?ThinkHoldTracker?ThinkProgressTooltip?PackAiTooltipHandler?ClientSetup?JeiTargetResolver?AiAssistantScreen?AskService?lang?code_change_log.md
- **????**?Create Ponder ????? tooltip??? F??? + ?????????????????
- **?????**?
  - ??1?TooltipComponent ???? ItemTooltipEvent ????
  - ??????? RenderTooltipEvent.GatherComponents + RegisterClientTooltipComponentFactoriesEvent
  - ???? ???
- **??**?????JEI ????????????? jar ??????

## [2026-07-21 15:35:00] ??????? | ??
- **????**?AiAssistantScreen?ChatSession?ChatMessage?AskResult?ItemResolver?JeiTargetResolver?CraftPriority?SeasonContext?PsiHelper?AskService?JeiLookup?LlmClient?ClientSetup?lang?RoadmapChecks?code_change_log.md
- **????**???????????????????????/JEI ???????????????Psi ????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?DPS ??????Psi ? LLM ??????? CAD

## [2026-07-21 15:24:00] ??????? | ??
- **????**?JeiUniversalSpam.java?JeiLookup.java?JeiSpamFilterCheck.java?code_change_log.md
- **????**??? JEI ???????ae2:facade?framedblocks:framed_*?cover?camo?disguise ???? integrateddynamics facade
- **?????**?
  - ??1??????? facade?framed?cover ?????????
  - ??????? JeiUniversalSpam ???????????80% ??? spam ?????
  - ???? ???
- **??**?chipped ??????????

## [2026-07-21 15:21:00] ??????? | ??
- **????**?JeiLookup.java?JeiSpamFilterCheck.java?code_change_log.md
- **????**????? Facade ????? integrateddynamics:facade ??????????????
- **?????**?
  - ??1?JEI ??????? facade ??????????????
  - ?????? item id?category uid ???????????????????
  - ???? ???
- **??**?path ? facade?*_facade?facade/*

## [2026-07-21 15:17:55] ???????
- **????**?JeiLookup.java?code_change_log.md
- **????**?JEI ??????????????R?U??????????? LLM?????? 2000???? 12k ????
- **?????**?
  - ??1?????????????????????
  - ??????? per-cat ???????????????????
  - ???? ???
- **??**?????? 2000 ??????????????????

## [2026-07-21 15:16:00] ???????
- **????**?JeiLookup.java?LlmClient.java?code_change_log.md
- **????**?JEI ???? CATALYST??????????????????????????? Environmental Accumulator ?????
- **?????**?
  - ??1??? INPUT?OUTPUT?????? JEI ?????AI ?????????
  - ??????? asCatalyst ??????????? 8 ??prompt ??????
  - ???? ???
- **??**??????????????? JEI ?????

## [2026-07-21 15:06:00] ???????
- **????**?ChatMessage.java?ChatSession.java?AiAssistantScreen.java?code_change_log.md
- **????**?????????????????????????????? [??]????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?ChatMessage ?? heldItemLabel?heldItemId

## [2026-07-21 15:03:00] ???????
- **????**?AiAssistantScreen.java?ChatSession.java?lang/*.json?code_change_log.md
- **????**???????????????????????????????????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**??????????

## [2026-07-21 14:59:13] ???????
- **????**?AiAssistantScreen.java?code_change_log.md
- **????**????????????????????????????????????????????????????????????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**??? ?????????????????

## [2026-07-21 14:54:25] ??????? | ??
- **????**?ChatSession.java?AiAssistantScreen.java?ReplyNotifier.java?QuestBookOpener.java?AiClientCommands.java?lang/*.json?code_change_log.md
- **????**???????? GUI ? ??? toast??????????? session?????????????
- **?????**?
  - ??1??????????? Screen?questLinks ??
  - ?????busy?lastQuests ? ChatSession?? GUI ? ReplyNotifier?/packai quest ?????
  - ???? ???
- **??**?????????? jar?????? ZLIB EOF?

## [2026-07-21 14:41:00] ??????? | ??
- **????**?QuestGuide.java?AskEngine.java?QuestLocalePreferCheck.java?code_change_log.md
- **????**??????? Minecraft ????????????? FTB lang??????????????
- **?????**?
  - ??1??????? es_* ? en_* ???betterTitle ????? ? ????
  - ?????? client language ????????????en_*?AskEngine ?? replyLang
  - ???? ???
- **??**??????????????

## [2026-07-21 14:40:00] ??????? | ??
- **????**?QuestGuide.java?AiAssistantScreen.java?AskEngine.java?LlmClient.java?QuestDisplayNameCheck.java?code_change_log.md
- **????**????????????displayTitle????? hex ?? ID ???
- **?????**?
  - ??1?????? quest id ????????
  - ?????displayTitle ?? lang???????????LLM ?????? ID
  - ???? ???
- **??**?open_book ???? questId???????

## [2026-07-21 14:35:00] ???????
- **????**?AskEngine.java?WebSearch.java?LlmClient.java?PackAiConfig.java?PartialPackPolicyCheck.java?README?code_change_log.md
- **????**?????????????????????????????? mixed ??
- **?????**?
  - ??1???? kubejs ??? local_only????????????
  - ?????isHeldLocallyTouched???? ? mixed?online_ok ????
  - ???? ???
- **??**???????? remove??????snippet ????

## [2026-07-21 14:30:00] ??????? | ??
- **????**?WebSearch.java?PackAiConfig.java?AskEngine.java?LlmClient.java?WebSearchCheck.java?README?code_change_log.md
- **????**?mod ????Tavily?Serper????? Minecraft mod ?????? online_ok ???
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**??? Maven ???local_only?offline ????? mod ??????

## [2026-07-21 14:25:00] ???????
- **????**?PackIndex.java?AskEngine.java?LlmClient.java?AcquireFactsCheck.java?code_change_log.md
- **????**????????????????????????????????? AI
- **?????**?
  - ??1??????????????
  - ??????? recipe_needs ? unit?storage ???????? LLM ???????? prompt
  - ???? ???
- **??**????????????????

## [2026-07-21 14:15:00] ???????
- **????**?PackIndex.java?AskEngine.java?LlmClient.java?AcquireFactsCheck.java?README?code_change_log.md
- **????**????????? loot?fishing?fisherman???????????? LLM
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**???? fishing ?? -[fish]->??????????

## [2026-07-21 14:10:00] ??????? | ??
- **????**?PackIndex.java?AskEngine.java?LlmClient.java?AcquireFactsCheck.java?README?code_change_log.md
- **????**?JEI ????????loot table?villager ???? + shaped ????acquireFactsFor ? LLM
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**??? mod jar?openloader/data ????

## [2026-07-21 14:00:00] ???????
- **????**?LlmClient.java?code_change_log.md
- **????**?system prompt ????????????????????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?????????????????????? emoji?Markdown

## [2026-07-21 13:58:00] ??????? | ??
- **????**?AskService.java?AskEngine.java?LlmClient.java?ReplyLangCheck.java?code_change_log.md
- **????**?LLM ???? Minecraft ?????zh_tw?en_us ???????????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?AskService ? LanguageManager.getSelected()?system prompt + user.replyLanguage

## [2026-07-21 13:50:00] ??????? | ??
- **????**?QuestGuide.java?QuestGuideIdCheck.java?code_change_log.md
- **????**??????????????? quests ???lang ? quest.HEX.title ????? ID???? nearestId ?? task?reward????
- **?????**?
  - ??1??????? open_book ??????????
  - ??????? `quests: [` ????-1 ? id?lang ? `quest.HEX.title`?? ID ????? zh/en ??
  - ???? ???
- **??**?ATM10 ?? smoke?Andesite Alloys ? 0F16498769DFB3B0?QuestGuideIdCheck OK

## [2026-07-21 13:40:00] ??????? | ??
- **????**?ChatMessage.java?ChatSession.java?AiAssistantScreen.java?AskService/AskEngine/LlmClient?ClientSetup????README
- **????**????? UI??????????????? 8 ??? LLM??????????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?????waiting ? replaceLastAssistant

## [2026-07-21 13:26:00] ??????? | ??
- **????**?ModelPickerScreen.java?AiAssistantScreen.java?PackAiSettingsScreen.java?zh_tw.json?en_us.json
- **????**??????????? GUI???????????????? CycleButton
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**???????????????

## [2026-07-21 13:21:59] ??????? | ??
- **????**?Plainify.java?AskResult.java?LlmClient.java?MinecraftUiTextCheck.java
- **????**???????? emoji ??? Markdown?????? emoji?Markdown?MC ???????
- **?????**?
  - ??1?AI ??? emoji?## ** ???????????
  - ?????forMinecraftUi ???AskResult ??????
  - ???? ???
- **??**?jar ???

## [2026-07-21 13:14:30] ???????
- **????**?ModelCatalog.java?PackAiSettingsScreen.java?AiAssistantScreen.java
- **????**???????????? refresh?rebuildWidgets?init ??????????????
- **?????**?
  - ??1??????? refreshAsync ????? onClientDone?rebuild?init?? refresh
  - ????????????? callback??????? refresh ???autoRefreshScheduled?
  - ???? ???
- **??**????????? force ??

## [2026-07-21 13:11:00] ???????
- **????**?LlmClient.java?ModelCatalog.java?PackAiSettingsScreen.java?README.md
- **????**?normalizeApiBaseUrl ??????? /chat/completions??? OpenRouter ?????? 404
- **?????**?
  - ??1????? apiBaseUrl ?? https://openrouter.ai/api/v1/chat/completions????? /chat/completions ? HTTP 404
  - ?????????????? base???? hint ????? /v1
  - ???? ???
- **??**?OpenRouter ?? id ??? deepseek/? ?? deepseek-v4-pro

## [2026-07-21 13:05:00] ???????
- **????**?README.md
- **????**??????JEI R/U??? tooltip??????????????????? LLM??? Mixin
- **?????**?
  - ??1?? README ???? Mixin?? JEI????
  - ???????????????????
  - ???? ???
- **??**??

## [2026-07-21 13:02:30] ???????
- **????**?AiAssistantScreen.java?PackAiSettingsScreen.java?ModelCatalog.java?zh_tw.json?en_us.json
- **????**????????????????????????? Cloud?Ollama ????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?offline ?????

## [2026-07-21 12:58:54] ??????? | ??
- **????**?ModelCatalog.java?AiAssistantScreen.java?PackAiSettingsScreen.java?ModelCatalogCheck.java
- **????**?????????? Cloud `/models` ? Ollama `/api/tags` ????? 5 ?????????? fallback
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?? GUI?? key?? mode ? refresh??? embedding ??????

## [2026-07-21 12:47:17] ??????? | ??
- **????**?JeiLookup.java?PackAiJeiPlugin.java?AskService/AskEngine/LlmClient?build.gradle?neoforge.mods.toml?gradle.properties
- **????**????? JEI?????????R?????U???????? LLM
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?JEI optional????? JEI runtime ????????jar: packai-0.1.0.jar

## [2026-07-21 12:27:19] ??????? | ??
- **????**?TooltipCapture.java?ScreenMixin.java?packai.mixins.json?neoforge.mods.toml?GameContextCollector.java?AskService.java?ItemRef.java
- **????**?????????? tooltip??? Shift/Ctrl/Alt????????????????????? LLM
- **?????**?
  - ??1?????? Screen.hasShiftDown() ???????? hoverName ??
  - ?????Mixin ? TooltipCapture ???? true????? collect ?? async ask
  - ???? ???
- **??**???????? GLFW ?????????????? 900 ?

## [2026-07-21 12:24:39] ???????
- **????**?ItemRef.java?LlmClient.java?GameContextCollector.java?AskService.java?ItemRefCheck.java
- **????**?LLM ???????? hover ???heldItem?hotbar ?????? tags?categories
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**??????? registry id?jar ???

## [2026-07-21 12:22:37] ??????? | ??
- **????**?ItemRef.java?GameContextCollector.java?AskService.java?AskEngine.java?LlmClient.java?PackIndex.java?ItemRefCheck.java
- **????**????? hover ?? + item tags???????? LLM????? registry id ??
- **?????**?
  - ??1?? id ? NBT???????? AI ????
  - ?????ItemRef(id, displayName, tags)?LLM ? name+categories?retrieve ? hintTokens
  - ???? ???
- **??**?????? Data Components???????????????jar: packai-0.1.0.jar

## [2026-07-21 12:09:16] ???????
- **????**?mod/src/main/java/com/skps9/packai/logic/AskEngine.java
- **????**?? offline ??????????? LLM????????? API????????????
- **?????**?
  - ??1???????????+ raw key?????? API ??
  - ?????? AskEngine?`!questHits.isEmpty() && !override` ??? return formatGuide???? offline ????online ??? llm.ask
  - ???? ???
- **??**?API ???????????????????????????????

## [2026-07-21 11:57:30] ???????
- **????**?mod/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java?mod/src/test/java/.../AssistantLayoutCheck.java
- **????**????????inputY ???????????????????????????
- **?????**?
  - ??1??????? 3 ??????????????????????
  - ?????questStrip ??????qy ? inputY-questStrip ???inputY ?? bottomStack ??
  - ???? ???
- **??**???????????????????????????

## [2026-07-21 11:52:40] ???????
- **????**?mod/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java
- **????**???????????????????????????? bottomStack ???
- **?????**?
  - ??1??? questBlock ???? bottomStack??????????
  - ?????bottomStack ????+4 ????questBlock ?? answerBottom
  - ???? ???
- **??**??? 11:51 ?????

## [2026-07-21 11:51:22] ???????
- **????**?mod/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java
- **????**?????????? 3 ?????????? GUI ? questBlock ???????
- **?????**?
  - ??1??????????? rebuildWidgets ?? questCount ?? bottomStack???????????
  - ?????MAX_QUEST_SLOTS=3 ??????????????????????
  - ???? ???
- **??**?compileJava SUCCESS

## [2026-07-20 21:05:02] ???????
- **????**?mod/?bridge/?code_change_log.md?????
- **????**??????? Pack AI ???Bridge ???? mod list ????/???????
- **?????**?
  - ??1?????? Earth Online App ?????
  - ????????????????????
  - ???? ???
- **??**???????/ai ???NeoForge 1.21.1

## [2026-07-20 21:11:57] ???????
- **????**?mod/src/.../packai/**?bridge/**
- **????**??? client-only Pack AI???? modIds?Bridge ???????????????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?test_mod_filter.py ???gradle compileJava BUILD SUCCESSFUL

## [2026-07-20 21:25:13] ???????
- **????**?bridge/rag.py?mod_filter.py?main.py?llm.py?mod/.../GameContextCollector.java?AskService.java?ClientSetup.java?AiAssistantScreen.java?bridge/test_rag_eff.py
- **????**?????+??+?????fingerprint ?? context?????? LLM???? warmup
- **?????**?
  - ??1???? kubejs/datapacks ? pack-global ?? focus ??
  - ?????focus ???????? focus mod ? overrides/readme???????? pack tree
  - ???? ???
- **??**?test_mod_filter / test_rag_eff ???compileJava SUCCESS

## [2026-07-20 21:37:18] ???????
- **????**?bridge/pack_modified.py?web_search.py??? main.py?llm.py?.env.example?README.md?test_pack_modified.py
- **????**??????? local_only / online_ok?Tavily/Serper ?????????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**?test_pack_modified / test_mod_filter / test_rag_eff ??

## [2026-07-21 01:55:44] ???????
- **????**?bridge/plainify.py?quests.py?pack_graph.py?main.py?llm.py?rag.py?mod ClientSetup/AskService/AiAssistantScreen?tests?README
- **????**??????FTB????????override????override???3???Pack Graph?token??warmup?????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**??? bridge ?????compileJava SUCCESS

## [2026-07-21 07:30:56] ??????? | ??
- **????**?mod/src/main/java/com/skps9/packai/logic/*?AskService.java?PackAiConfig.java?README.md
- **????**???????? JAR?AskEngine ????/??/plainify/??? LLM???? Python Bridge ???
- **?????**?
  - ??1???compileJava BUILD SUCCESSFUL?
  - ?????N/A
  - ???? ???
- **??**?bridge/ ????????? mods ? jar + ?? API key ? Ollama

## [2026-07-21 08:01:14] ???????
- **????**?mod/.../LlmClient.java?PackAiConfig.java?README.md
- **????**?curl ????? 401??? apiKey???/Bearer/?????? PACKAI_API_KEY?401 ??? toml
- **?????**?
  - ??1????????? key ? curl ??? key ??????
  - ?????sanitize + env ?? + ????? packai-client.toml
  - ???? ???
- **??**??????? jar ?? toml ??????

## [2026-07-21 08:07:40] ???????
- **????**?mod/.../AiAssistantScreen.java
- **????**????????????????????? 6 ????
- **?????**?
  - ??1????? maxLines=6 ?? ?
  - ?????scissor + scrollOffset????????
  - ???? ???
- **??**????? jar

## [2026-07-21 08:14:43] ???????
- **????**?mod/.../PackAiConfig.java?LlmClient.java?AskEngine.java?README.md
- **????**??? llm.mode?auto/cloud/ollama/offline????????
- **?????**?
  - ??1??
  - ?????N/A
  - ???? ???
- **??**??? auto????? auto

## [2026-07-21 08:16:46] ???????
- **????**?mod/.../AiAssistantScreen.java?PackAiConfig.java?lang/*.json?README.md
- **????**??? GUI ???? CycleButton?auto/cloud/ollama/offline??????
- **?????**?
  - ??1??????
  - ?????gradlew jar
  - ???? ????????
- **??**???????

## [2026-07-21 08:17:04] ???????
- **????**?mod/.../AiAssistantScreen.java?PackAiConfig.java?lang/*.json?README.md
- **????**??? GUI ???? CycleButton?auto/cloud/ollama/offline??????
- **?????**???jar BUILD SUCCESSFUL?
- **??**????????SPEC.save() ???

## [2026-07-21 08:23:37] ???????
- **????**?mod/.../AiAssistantScreen.java?PackAiConfig.java?lang/*.json?README.md
- **????**??? GUI ???? CycleButton???/Ollama ???????? MODEL / OLLAMA_MODEL
- **?????**??
- **??**?????????????????????

## [2026-07-21 09:28:56] ???????
- **????**?mod/.../AiAssistantScreen.java?PackAiConfig.java?LlmClient.java?lang?README
- **????**??? GUI ?? API Key ???max 512?+ ??????????? key
- **?????**??
- **??**?????????? packai-client.toml

## [2026-07-21 09:31:17] ??????? | ??
- **????**?PackAiSettingsScreen.java?PackAiMod.java?AiAssistantScreen.java?lang?README
- **????**?API key ?? Mods?Packai ??????max 512 ?????? GUI ?? key ?
- **?????**??
- **??**?IConfigScreenFactory ??????

## [2026-07-21 10:17:21] ???????
- **????**?mod/.../QuestGuide.java?AskEngine.java
- **????**?offline ??????????????/??/?????????????
- **?????**??
- **??**?rich formatGuide?matchForOffline

## [2026-07-21 10:19:42] ???????
- **????**?Plainify.java?QuestGuide.java?LlmClient.java
- **????**???????????ID?????????????????????????????
- **?????**??
- **??**?displayName + humanizeText

## [2026-07-21 10:44:22] ??????? | ??
- **????**?AskResult?QuestBookOpener?QuestGuide?AskEngine?AskService?AiAssistantScreen?lang
- **????**?????????????????????? /ftbquests open_book ??
- **?????**??
- **??**?? Mixin?Heracles ???????

## [2026-07-21 11:09:34] ???????
- **????**?QuestGuide.java?PackIndex.java
- **????**?????????? FTB reward_tables???????????
- **?????**??
- **??**?isRewardTablePath

## [2026-07-21 11:25:55] ???????
- **????**?AskEngine?AskService?QuestGuide?PackIndex?ModScanners?PackAiConfig?UI screens
- **????**??? hotbar ???totalHint?match ???auto ?????????????PackIndex ?
- **?????**??
- **??**??? push

## [2026-07-22 19:40:00] ??@?????G?s?W | ???
- **?????|**?GPackAiConfig.java?BJeiLookup.java?BChatSession.java?BAskEngine.java?BLlmClient.java?BPackAiSettingsScreen.java?Blang?BREADME.md
- **???K?n**?G?s?W `[token]` ?]?w?]maxJeiChars??historyTurns??maxFacts?^????? LLM ???|?P?]?w??
- **?J?????D**?G
  - ???D1?G?L
  - ??M???GN/A
  - ???A?G? ?w??M
- **???**?G?w?]????????Fjar ?s????\

## [2026-07-22 19:40:24] ??@?????G???
- **?????|**?GREADME.md
- **???K?n**?G??s??w?s???B?t??d??token??web ?]?w???A??P?]?w?????
- **?J?????D**?G?L
- **???**?GGitHub ?? Modpack-AI-Assistant

## [2026-07-22 19:52:24] ??@?????G???
- **?????|**?GPlainify.java?BAiAssistantScreen.java?BSuggestIcons.java?Btests/check_strip_mc_format.py
- **???K?n**?G?? ??/& ??X?A??K Font.split ???q???V??????
- **?J?????D**?G
  - ???D1?G???~?W?]?p SlashBlade?^?y?t?r??t ??6?AFont ??R???C??~????????r
  - ??M???GPlainify.stripMcFormat ?? UI????????|??
  - ???A?G? ?w??M
- **???**?G?? jar ???}?C??????

## [2026-07-22 20:03:16] ??@?????G??? | ?s?W
- **?????|**?GRecipeCard.java?BJeiRecipeCards.java?BAiAssistantScreen.java?Blang
- **???K?n**?G?t??d???G????D???~???A?????L tooltip
- **?J?????D**?G?i??
- **???**?G???g??x?A??N?X

## [2026-07-22 20:44:40] ??@?????G???
- **?????|**?GRecipeCard.java?BJeiRecipeCards.java?BAiAssistantScreen.java?BREADME.md
- **???K?n**?G?t??d??X?G??P?D???~??????s?F??L???~???G?????????? tooltip
- **?J?????D**?G
  - ???D1?GIIngredientHelper ?L getAmountWithUnits?Fraw Optional ???O???_????
  - ??M???G?????T Optional ???F????u???W??
  - ???A?G? ?w??M
- **???**?Gjar ?s????\?F??????????J?h?? Mekanism chemical?A?|?H ~ ???tooltip ???

## [2026-07-22 20:51:00] ??@?????G?s?W | ???
- **?????|**?GRecipeExtra.java?BJeiSoftIngredients.java?BRecipeCard.java?BJeiRecipeCards.java?BAiAssistantScreen.java?BChatSession.java?BREADME.md
- **???K?n**?G???????G?? Mekanism chemical ?H JEI ??V???e?b?t??d?A??? tooltip
- **?J?????D**?G?L
- **???**?G???w??? Mekanism?F?M??????M soft cache

## [2026-07-22 20:51:17] ??@?????G?s?W | ???
- **?????|**?GRecipeExtra.java?BJeiSoftIngredients.java?BRecipeCard.java?BJeiRecipeCards.java?BAiAssistantScreen.java?BChatSession.java?BREADME.md
- **???K?n**?G???????G?? Mekanism chemical ?H JEI ??V???e?b?t??d?A??? tooltip
- **?J?????D**?G?L
- **???**?G???w??? Mekanism?F?M??????M soft cache

## [2026-07-22 21:08:04] ??@?????G???
- **?????|**?GThinkHoldTracker.java?BClientSetup.java?BPackAiTooltipHandler.java
- **???K?n**?G???? Y ?i???? client tick ???i?A??K?i?@??? JEI/tooltip ?d?y??L?k???
- **?J?????D**?G
  - ???D1?G?i??u?b ItemTooltipEvent ???e??e?i?A?i?@?? JEI ??????? tooltip ????s???i????d??
  - ??M???Gtick ???i??Ftooltip ?u??s?a?????P???
  - ???A?G? ?w??M
- **???**?Gwarmup ???i???????? AI ?^?????C?A???????A????? Y

## [2026-07-22 21:15:13] ??@?????G??? | ?s?W
- **?????|**?GPackAiConfig.java?BPackAiSettingsScreen.java?BAiAssistantScreen.java?Blang?BREADME.md
- **???K?n**?G?U????s????F?]?w?i???????k??
- **?J?????D**?G?i??
- **???**?G???g??x?A??N?X

## [2026-07-22 21:17:46] ??@?????G??? | ?s?W
- **?????|**?GPackAiConfig.java?BPackAiSettingsScreen.java?BAiAssistantScreen.java?Blang?BREADME.md
- **???K?n**?G?U???@???s????F`[ui].sidebarSide` ?i?????k?F????u???@?????????
- **?J?????D**?G?L
- **???**?G?w?]?k???F?]?w???P?U???u?]?w?v?i??

## [2026-07-22 21:18:02] ??@?????G??? | ?s?W
- **?????|**?GPackAiConfig.java?BPackAiSettingsScreen.java?BAiAssistantScreen.java?Blang?BREADME.md
- **???K?n**?G?U???@???s????F`[ui].sidebarSide` ?i?????k?F????u???@?????????
- **?J?????D**?G
  - ???D1?GScreen.rebuildWidgets ?? protected?A?]?w???L?k?????I?s parent
  - ??M???GAiAssistantScreen.reloadLayout() ???}?]??
  - ???A?G? ?w??M
- **???**?G?w?]?k??

## [2026-07-22 21:18:22] ??@?????G???
- **?????|**?GThinkHoldTracker.java
- **???K?n**?G???????P Ponder deferredTick ???t???]??N?? client tick ??K JEI ?d???^
- **?J?????D**?G?L
- **???**?G??? Ponder 1.0.69?F???h?^ tooltip-only ?i??

## [2026-07-22 21:26:51] ??@?????G???
- **?????|**?GCraftPriority.java?BReplyLang.java?BAskEngine.java
- **???K?n**?G????X????N???????????????y?????C?u??
- **?J?????D**?G?i??
- **???**?G???g??x

## [2026-07-22 21:27:29] ??@?????G???
- **?????|**?GCraftPriority.java?BReplyLang.java?BAskEngine.java?Btests/check_quest_priority.py
- **???K?n**?G????X???????????????y?????C?u???]JEI???t??d????LLM ?????facts ????^
- **?J?????D**?G?L
- **???**?GcategoryTier=90?Fquest facts ??? web ????

## [2026-07-22 21:30:05] ??@?????G?s?W | ???
- **?????|**?GPackAiConfig.java?BCraftPriority.java?BReplyLang.java?BAskEngine.java?BPackAiSettingsScreen.java?Blang?BREADME.md?Btests/check_quest_priority.py
- **???K?n**?G?s?W `questObtainPriority` ?]?w?]last??normal??first?^?A?i?????????b?X?????????????
- **?J?????D**?G?L
- **???**?G?w?] last?F?]?w???P `[ui]` ?i??

## [2026-07-22 21:34:33] ??@?????G???
- **?????|**?GPackAiConfig.java?BCraftPriority.java?BReplyLang.java?BAskEngine.java?BPackAiSettingsScreen.java?Blang?BREADME.md
- **???K?n**?G?]?w???u?u??????~?|?vpreferObtain?]?X???????????????????^?A?D??????C?U
- **?J?????D**?G?L
- **???**?G?? last/first/normal ?O?W????e

## [2026-07-23 08:31:07] 操作類型：新增 | 修改
- **文件路徑**：super_minecraft_AI_player PackIndex graph retrieve + codegraph init
- **變更摘要**：Codegraph 思路：retrieve 只回 seed 鄰域 facts、有 facts 則略過 raw snippet；專案 codegraph init/index
- **遇到的問題**：無
- **備註**：.codegraph/ 已在 gitignore


## [2026-07-23 09:00:52] 操作類型：新增 | 修改
- **文件路徑**：ItemDescFacts、PackIndex、AskEngine、ReplyLang、lang、ItemDescFactsCheck
- **變更摘要**：通用物品說明／數值／觸發 facts（lang 解 key）；用途問題優先；block.set(air) 不誤當目標
- **遇到的問題**：無
- **備註**：脆骨症僅測例；非專做器官 API


## [2026-07-23 17:15:21] 操作類型：新增 | 修改
- **文件路徑**：ItemDescFacts、AskEngine、ReplyLang、lang、ItemDescFactsCheck
- **變更摘要**：Strategy 函式本體抽 gives/effect/becomes（map 內聯 + 同檔具名 fn）
- **遇到的問題**：無
- **備註**：待辦 #1；同檔解析，不做跨檔


## [2026-07-23 19:23:07] 操作類型：新增 | 修改
- **文件路徑**：ItemDescFacts、PackIndex、AskEngine、ReplyLang、HeavyScriptChecks、docs/PUBLISH.md、docs/RELEASE.md
- **變更摘要**：事件轉發 map 綁定、窄化 tick、動態 drops、hasTag、雷雨/階段；發布文件
- **遇到的問題**：無
- **備註**：CF/MR 需人工 token


## [2026-07-23 23:42:33] 操作類型：修改
- **文件路徑**：PackAiConfig、IngredientReqHints、JeiLookup、lang、tests/check_ingredient_req_hints.py
- **變更摘要**：auto 同時支援「樣品≠門檻」與「樣品=門檻」：裸堆通過時只保留 keep 樣式（擊殺／耀魂等）；skip 仍濾儲能樣品噪音
- **遇到的問題**：
  - 問題1：auto 在 Ingredient.test(裸堆)通過時整段省略 NBT，SlashBlade 等真門檻被吃掉
  - 解決方案：新增 ingredientNbtKeepPatterns；auto+acceptsBare（及無 Ingredient 的 auto）改為 KEEP_ONLY
  - 狀態：✅ 已解決
- **備註**：always=全部過濾後附加；never=僅名稱；keep/skip 可在 toml 調


## [2026-07-24 00:13:04] 操作類型：修改
- **文件路徑**：PackAiConfig、IngredientReqHints、tests/check_ingredient_req_hints.py
- **變更摘要**：脆骨症／胸腔 organData：keep 加入 chestcavity／器官等；NBT 掃描含 float／double 與非零（含負分）；skip 的 time 改為 timestamp 以免誤傷 times
- **遇到的問題**：
  - 問題1：器官分數多為 double／float，且可為負，原 walkInts 只收正整數
  - 解決方案：改 walkNumbers；v!=0；擴充 keep；修正 skip
  - 狀態：✅ 已解決
- **備註**：不綁 NFWC 品牌；通用 organ／chestcavity 樣式


## [2026-07-24 00:16:02] 操作類型：修改
- **文件路徑**：PackAiConfig、IngredientReqHints、lang、tests/check_ingredient_req_hints.py
- **變更摘要**：去掉拔刀／脆骨症品牌 keep；改為通用門檻語意（kill/soul/level/score/organ…）＋命名空間屬性鍵啟發式
- **遇到的問題**：
  - 問題1：keep 列表看起來像專做兩個包
  - 解決方案：語義化 keep + namespaced stat key 啟發式；品牌詞移除
  - 狀態：✅ 已解決
- **備註**：skip 仍負責樣品噪音；toml 可再加自訂 keep


## [2026-07-24 00:24:54] 操作類型：新增 | 修改
- **文件路徑**：RecipeIngredientGates.java、IngredientReqHints.java、JeiLookup.java、tests/check_recipe_ingredient_gates.py
- **變更摘要**：從原配方 Ingredient 反射讀取門檻（RequestDefinition：kill/proud_soul/refine/sword_type；DataComponent 樣品字串如 SpecialAttackType），不再只靠 keep 猜
- **遇到的問題**：
  - 問題1：熒光奇蹟原配方只要 refine≥100＋broken，樣品啟發式易漏／誤加
  - 解決方案：對照 amazing_shine.json；反射 SlashBladeIngredient.request 與通用 request 存取器
  - 狀態：✅ 已解決
- **備註**：無硬依賴 SlashBlade；JEI 無 Ingredient 時仍走樣品＋語意 keep


## [2026-07-24 00:40:01] 操作類型：修改
- **文件路徑**：zh_tw.json、en_us.json（packai settings tooltip）
- **變更摘要**：材料 NBT 設定提示加註：樣品 NBT 路徑可能含噪音
- **遇到的問題**：無
- **備註**：


## [2026-07-24 21:13:20] 操作類型：新增 | 修改
- **文件路徑**：PackAuthorAgents.java、AskEngine、LlmClient、ReplyLang、lang、docs/PACK_AUTHOR.md、docs/examples/packai_AGENTS.md、README、tests
- **變更摘要**：整合包作者可放 config/packai/AGENTS.md 自訂 AI 指引；warmup 載入並注入 system prompt（有長度上限）
- **遇到的問題**：無
- **備註**：與 Cursor AGENTS.md 概念類似，但是給遊戲內 Pack AI；衝突時仍以 JEI／本地事實為準


## [2026-07-26 13:20:45] 操作類型：新增 | 修改
- **文件路徑**：forge/neo PackAiSettingsScreen、WidgetCompat、InvPick、CuriosBridge、lang、mods.toml、build.gradle
- **變更摘要**：設定 4 分頁（Connection/Ask/Recipes/Quests）+ Forge 全控件 tooltip；Curios soft-dep 納入 InvPick
- **遇到的問題**：
  - 問題1：1.19.2 無 Tooltip.create／Button.builder.tooltip
  - 解決方案：WidgetCompat TipButton/TipEditBox + CycleButton.withTooltip(font.split)
  - 狀態：✅ 已解決
  - 問題2：Curios 缺模組時不可硬引用 API class
  - 解決方案：CuriosBridge + Class.forName(CuriosBridgeImpl)；Neo stub isLoaded=false
  - 狀態：✅ 已解決
- **備註**：#4 ITEM_SOURCE_LOOKUP 僅說明不實作完整 SOP


## [2026-07-26 13:23:35] 操作類型：新增
- **文件路徑**：neoforge/1.21.1/.../compat/CuriosBridge.java
- **變更摘要**：Neo Curios stub（isLoaded=false）；Forge 已 soft-dep 實作
- **遇到的問題**：
  - 問題1：Neo 1.21.1 curios-neoforge 座標／API 未在本批驗證
  - 解決方案：空橋接，InvPick 仍呼叫但無 curios 列
  - 狀態：✅ 已解決（刻意 stub）
- **備註**：無硬依賴、無 curios 不崩潰

## [2026-08-10 00:50:46] 操作類型：新增
- **文件路徑**：docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：eng review 鎖定四題計畫寫入 docs（A→B→C 無 D；D4–D11 決策；每片手動測步驟）
- **遇到的問題**：
  - 問題1：無
  - 解決方案：—
  - 狀態：✅ 已解決
- **備註**：未改業務碼；開工從 #4；手動測非 agent CUA



## [2026-08-10 01:04:02] 操作類型：新增 | 修改
- **文件路徑**：.cursor/rules/jar-to-nfwc.mdc；.cursor/rules/cua-verify-after-finish.mdc；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：永久 SOP：Forge playable 變更後必 jar→dist（versioned + 1.19.2 alias）→NFWC mods（單一 packai jar）；CUA 步驟含此流程且 NFWC 鍵 `;`；backlog Test policy 同步
- **遇到的問題**：
  - 問題1：無
  - 解決方案：—
  - 狀態：✅ 已解決
- **備註**：未 bump version；未 commit；Neo→ATM10 僅 Neo 有改時才抄

## [2026-08-10 11:02:45] 操作類型：修改 | 新增
- **文件路徑**：docs/CURSEFORGE_DESCRIPTION.md、docs/plans/full-item-index.md、code_change_log.md
- **變更摘要**：改寫 CF 簡介強調 JEI／任務／魔改門檻／雙線版本；另寫全物品索引規劃稿（尚未實作）
- **遇到的問題**：無
- **備註**：CF About 仍需人工貼上；不點名競品商標


## [2026-08-10 11:51:11] 操作類型：修改
- **文件路徑**：docs/plans/full-item-index.md
- **變更摘要**：依使用者回覆鎖定決策：A 已有→缺口是快取／前綴索引；建索引時機=首次＋modlist 變更；四題 backlog 優先；spike 以 NFWC 為主
- **遇到的問題**：無
- **備註**：澄清「預設開／opt-in」被誤解為建索引觸發條件


## [2026-08-10 19:22:12] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：TetraSchematicText、AskService、RecipeCard、AiAssistantScreen、QuestGuide、ReplyLang、lang×3×2；tests/check_tetra_schematic_facts.py、check_scroll_material_card.py、check_quest_title_prefer.py、update_reply_prompts.py；fixtures/tetra/schematics/no_mat_upgrade.json；code_change_log.md
- **變更摘要**：Tetra 工作台 install 材料以 RecipeCard FLOW 圖示+數量呈現；無材料時 PURPOSE/UI 明示「無需材料」；file-id 任務標題過濾
- **遇到的問題**：
  - 問題1：（進行中）材料只在 LLM 逗號文字、無槽位圖示
  - 解決方案：PURPOSE install_items／outcome items → FLOW material strip（獨立於 JEI craft 卡 gate）
  - 狀態：❌ 未解決（實作中）
- **備註**：不 bump／不 commit。完成后 jar→dist→NFWC。


## [2026-08-10 19:31:22] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：TetraSchematicText、RecipeCard、AskService、AiAssistantScreen、QuestGuide、lang×3×2；tests/check_tetra_schematic_facts.py、check_scroll_material_card.py、check_quest_title_prefer.py、update_reply_prompts.py、fixtures/tetra/schematics/no_mat_upgrade.json；code_change_log.md
- **變更摘要**：Tetra install 材料 → RecipeCard FLOW 圖示+數量；無材料 PURPOSE/UI「無需材料」；file-id 任務標題過濾
- **遇到的問題**：
  - 問題1：材料只在 LLM 逗號文字
  - 解決方案：PURPOSE install_items／outcome items → FLOW material strip（獨立 JEI craft gate）
  - 問題2：無材料 outcomes 不發 SCROLL_MATERIALS
  - 解決方案：none (no material required) + UI 標籤
  - 問題3：goldenagetetra file-id 標題仍當 readable
  - 解決方案：looksLikeQuestId 擴 file-id 風格
  - 狀態：✅ 已解決（python checks OK；雙樹 compileJava OK；forge jar→dist＋NFWC SHA256 BDC4D87C7D0A382008214EA3EB4C6FA6C14FEBBF8113587EEB44B998E3A11FE9）
- **備註**：不 bump／不 commit。**須重開 NFWC** 後手動：Ask 卷軸材料見槽位圖示+數量；無材料卷見「無需材料」；任務鈕勿再顯示 goldenagetetra 當標題（有可讀標題兄弟時）。


## [2026-08-11 10:57:02] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：RecipeCard、JeiReqNotes、FormatRequirements、JeiRecipeCards、AskService、AiAssistantScreen、Font/GuiGraphics mixin、ReplyLang/lang×3；tests/check_format_requirements.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：#1A — JEI 可見非槽位字（XP／時間／stress 等）收進 RecipeCard.reqNotes，經 formatRequirements 合併進 Ask REQUIREMENTS＋卡腳註；不收 refine/kill；純 3×3 無噪
- **遇到的問題**：
  - 問題1：JEI 11 draw 用 Font、JEI 19 多用 createRecipeExtras.addText
  - 解決方案：Forge Font mixin 捕獲 draw；Neo 捕 extras 文字＋GuiGraphics.drawString；失敗則空 notes（OK）
  - 狀態：🔄 實作中
- **備註**：不 bump；#1B/#1C 不碰；unlockGates 參數預留空 list


## [2026-08-11 14:00:06] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：RecipeUnlockGates、PackIndex、ReplyLang、lang×3×2；tests/check_recipe_unlock_gates.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：#1C — KubeJS `isAdvancementDone` + `event.cancel`／ritual handler 啟發式 → ADVANCEMENT（字面 id）或 UNKNOWN；不掃 mrqx 表
- **遇到的問題**：
  - 問題1：Neo 直接拷 Forge RecipeUnlockGates → BOM + 1.21 Recipe/Advancement API 不符
  - 解決方案：UTF-8 無 BOM 重寫；Neo 用 reflection（無 Recipe.getId / Advancement getters）
  - 狀態：✅ 已解決（python tests/check_recipe_unlock_gates.py OK；Forge+Neo compileJava OK；jar→dist+NFWC SHA256 55D285A80AB57220834095391E3427A03FD8959E9C072F7B8AD05D2D0C9E40BA）
- **備註**：不 bump。二次檢查：無 mrqx 表硬碼、無 organ parser、UNKNOWN≠假 stage 清單。**須重開 NFWC** 後 Ask 奧秘儀式 → Unlock: 未知成就閘門（或字面 advancement id）。下一片 #5。


## [2026-08-11 14:05:33] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：PackIndex（LootJS／interactConditions／food.eaten／dynamic give）；ItemCreateUseCheck；tests/check_kubejs_universal_scan.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：#5 — 通用 KubeJS obtain/use：LootJS→loot acquire、放寬 tick/give/randomGet、豐富 interactConditions、food().eaten→script_use；無 organ parser
- **遇到的問題**：
  - 問題1：無
  - 狀態：✅ python tests/check_kubejs_universal_scan.py OK；Forge+Neo compileJava OK；ItemCreateUseCheck 擴充（LootJS≠PURPOSE／food.eaten）；jar→dist+NFWC SHA256 DB739F1C0ADE164D8C377410E189716E9773CC3BA1B83EBEA89467E17F2555AC
- **備註**：二次檢查：無 organ／trinket 硬碼；loot=acquire、eaten=script_use PURPOSE。**須重開 NFWC**。下一片 #5b。


## [2026-08-11 14:11:52] 操作類型：新增 | 修改
- **文件路徑**：forge+neo：LootForwardIndex、PackIndex；tests/check_loot_forward_index.py；docs/plans/four-issue-backlog.md；code_change_log.md
- **變更摘要**：#5b — Gateways loot_table/entity_loot + loot JSON 正向索引；疊 LootJS；Ask 只列索引物品不捏造
- **遇到的問題**：
  - 問題1：Neo PackIndex 缺 isGatewayPath／ingestGraph LootForward 分支
  - 解決方案：補 static helpers + script/loot/gateway ingest
  - 狀態：✅ check_loot_forward_index OK；Forge+Neo compileJava OK；jar→dist+NFWC SHA256 45F5FAC924F51CF38636AF11D5003AC646A0440517C9B7C493E45A28A92EEA11
- **備註**：二次檢查：entity_loot 只解析表 id、不捏 slime_ball；疊 LootJS table；無 organ parser。**須完整重開 NFWC** 後驗 Ask。


## [2026-08-11 14:52:06] 操作類型：修改
- **文件路徑**：forge+neo：LootForwardIndex、PackIndex（pin）；tests/check_loot_forward_index.py；code_change_log.md
- **變更摘要**：#5b 補洞 — Gateways `stack`/`stack_list` 完成獎勵 → `item:X -[loot]-> gateway:…`，Ask 可找 挚友(b_a_d:friend) 取得路徑
- **遇到的問題**：
  - 問題1：#5b 只索引 loot_table/entity_loot；drowning 用 gateways:stack 直給 b_a_d:friend → Ask 找不到
  - 解決方案：parseFacts 加 stack/stack_list；pinLootContains 接受 `-[loot]-> gateway:`
  - 狀態：✅ 索引補洞已落地；humanize 誤讀另見後續條目
- **備註**：FACT 來源 NFWC drowning.json L40-48；珍珠儀式 summoning_rituals.js ritual_pearl。不 bump。
## [2026-08-11 15:15:50] 操作類型：修改
- **文件路徑**：forge+neo：Plainify、ReplyLang、PackIndex、AskEngine、LlmClient；lang en/zh_tw/zh_cn ×2；GatewayHumanizeCheck.java；code_change_log.md
- **變更摘要**：通用 humanize — `item:X -[loot]-> gateway:`／reward_stack／table／entity 邊種分詞；路徑葉不再被當生物掉落；fact_check #19 只准用 FACT 列、禁從 id 子字串捏造
- **遇到的問題**：
  - 問題1：`gateway:ns/.../drowning` 經 ITEM_ID strip 變成裸 `drowning` + 「掉落：」→ LLM 捏溺亡 mob
  - 解決方案：`Plainify.humanizeGraphFact` + gateway 佔位保護；acquire／LLM 走邊種模板（%s=gateway id）；無物品硬碼
  - 狀態：✅ GatewayHumanizeCheck OK；Forge+Neo compileJava OK；jar→dist+NFWC SHA256 6A2EC9B7C8362B1AEA5DEACF9491C2A18D6653D75CEE28608098728D986C98C2
- **備註**：不 bump。**須完整重開 NFWC** 後 Ask。before→after：`掉落：drowning` → `Gateways 挑戰完成獎勵（珍珠／完成閘道 kubejs:pack/drowning）— 非生物掉落`

