# R3 反方 review（最後一輪）— packai「真機自動測試 harness」計畫 **v6**

> 被審：`docs/plans/2026-09-19-in-game-autotest-harness.md`（md5 `11a2ddce9caed7220bc764511ebd9075`，114 行，HEAD `af577f5`）
> 角色：**R3 反方**（本輪＝第 3 輪，AGENTS.md 3–4 輪硬上限內最後一輪）。禁令：只讀唔改，本檔為唯一輸出。
> 樹：`forge/1.19.2`（primary）、MC **1.19.2** ＋ Forge **43.x**；`neoforge` PAUSED。`git status --porcelain | wc -l` = **116**。
> 標記：**【已核實】**＝本輪有工具輸出；**【未能核實】**＝查唔到，明講。
> 逐輪軌跡：R1 **正方 2 : 反方 8** → R2 **4 : 6** → R3 **5 : 5**（見文末）。

## 0. 本輪只核 v6 新加嘅 §7（R2 FC1–FC4）＋ 由新文字引入嘅矛盾

v6 = v5 ＋ 25 行（`git diff 6af531c af577f5`）；改動只有 §7.1–§7.5 五段，**其餘全文（§0/§2/§3/§4/A1–A7）一字未動**。故 R2 判「死」而 v6 冇改嘅項，本輪直接沿用並重新核實。

## 1. LD 存活表（比分由表計出）

| LD | v6 內容 | R2 | **R3** | 一句理由（本輪證據） |
|---|---|---|---|---|
| LD1 | 隔離＝沙盒 junction 副本、原 instance 零寫入 | 存活 | **存活** | 沙盒真存在（`instances/packai_sandbox/`，`minecraft` → `Documents/packai_dev_game`）；live `AI_test_NFWC_DIM/minecraft` **非** junction（`os.path.isjunction=False`）✅ |
| LD2 | 入口＝`openAndAskAbout(stack)` | 存活 | **存活** | `AiAssistantScreen.java:108 public static void openAndAskAbout(ItemStack)` ✅ |
| LD3 | 開世界＋互動框程序化確認（§7.1） | **死** | **半死** | 機制核到**真**（見 §2.1），但 class 名錯＋backup 分支漏＋mitigation 打錯靶 |
| LD4 | 零殘留（冇 flag ⇒ harness code path 完全唔存在） | 死 | **死** | v6 冇撤回 §0/L10 同 A1 第二半（見 §3.1） |
| LD5 | 成本閘（driver 側獨立上限，§7.3） | 半死 | **半死** | delta 機制可行，但依賴 **untracked** class ＋無數值門檻（見 §2.3） |
| LD6 | 沙盒啟動＝`prismlauncher.exe -l` | 存活 | **存活** | CLI 真存在（R2 已核）；**實跑至今零次**（見 §5） |
| LD7 | 清理／還原（`finally` ＋ §4 backup） | 半死 | **半死** | `finally` 設計真；§4 `build.gradle.bak` 對唔上 HEAD（R2 N7 未修，v6 冇提） |
| LD8 | A1 靜態閘（flag 資源無＋harness class 唔入 jar） | 死 | **死** | 第二半物理上做唔到；v6 未動 A1 |
| **LD9（新）** | A3 驗收面（`check.cards`） | — | **死** | `check.cards` 喺 cap **之前**發出（`:126` vs `:161-167`）⇒ 照寫必紅（見 §2.2） |
| **LD10（新）** | NBT 樣本來源（§7.2 改用 JEI） | — | **存活** | 前提**核到真**：Tetra 有 10 條 JEI 可見 `crafting_shaped` 出 `tetra:modular_double`＋NBT（見 §2.2） |

→ **4 存活 ＋ 3 半死 ＋ 3 死**（10 項，加權 55%；R2 為 3/2/3＝50%）。

## 2. v6 五段逐條判定（真／表面／未解決＋證據）

### 2.1 §7.1 互動框 —— **表面解決（半死）**【已核實，bytecode 級】

**核到真嘅部分**：`ConfirmScreen` 真存在（`net.minecraft.client.gui.screens.ConfirmScreen`）；`Screen.children()` 係 `public List<? extends GuiEventListener>` ✅ 可過濾 `Button`；`ConfirmScreen.addButtons` 第一粒 `Button` ＝ **yesButton**（`lambda$addButtons$0` → `callback.accept(true)`），第二粒 ＝ no（`accept(false)`）⇒ **「按第一粒」＝ 繼續，方向正確** ✅。
**實際會出嘅畫面**：`WorldOpenFlows.doLoadLevel`(5-arg) 真 bytecode：`186 iload12(hasConfirmed) → ifne 206(靜默)`；`191 iload4(p4) → ifeq 206`；`196 iload10(isOldCustomized) → ifne 273`；`201 iload11(!stable) → ifne 273`；`273 → 298 → invokeStatic ForgeHooksClient.createWorldConfirmationScreen` → 內部 `new ConfirmScreen(...GUI_PROCEED, GUI_CANCEL)` → `Minecraft.setScreen`。本包兩個存檔 `level.dat`（我寫 NBT parser 實讀）：`DataVersion=3120`(=1.19.2)、`Version.Name=1.19.2`、**`confirmedExperimentalSettings=0`**、**`forgeLifecycle="experimental"`** ⇒ !stable → **必定落到 298 → 一個純 `ConfirmScreen`** ⇒ plan 想按嘅框真係會出、真係可程序化按。

**三條仍然死嘅位**：
1. **class 名錯（本輪新捉）**：plan 寫 `ConfirmScreen` / **`BackupPromptScreen`** —— `BackupPromptScreen` **1.19.2 唔存在**（`unzip -l forge-43.4.0.jar | grep -i backup` 只有 `net/minecraft/client/gui/screens/BackupConfirmScreen.class`）。真名 `BackupConfirmScreen`，而且 **`extends Screen`（唔係 `ConfirmScreen`）** ⇒ 用 `instanceof ConfirmScreen` 過濾**永遠漏佢**；佢係 `askForBackup`（offset 292）嗰條分支専用（old-customized world），一入去就 hang。
2. **mitigation 打錯靶**：§7.1 尾句「世界優先揀同版本建立嘅…避免版本提示」——我實讀兩個存檔都係 **1.19.2 同版本**，但兩個都 `forgeLifecycle=experimental` ⇒ 觸發條件係 **experimental lifecycle**，唔係版本；換世界**完全無效**。
3. **冇 guard／冇負控**：plan 只寫「最多 3 次」。冇寫「入世界後停止按 Button」，而 `onClientTick` 每 tick 掃 `instanceof ConfirmScreen` 係**全局**嘅——ask 期間任何第三方 mod 彈嘅確認框都會被自動按「是」。呢個係行為風險，且 A-item 冇對應斷言。

### 2.2 §7.2 NBT 樣本 —— **半解決：前提真、機制寫唔到落白名單**【已核實】

- **前提核到真** ✅：`unzip -l tetra-1.19.2-5.6.0.jar` → `data/tetra/recipes/hammer/{oak,stone,acacia,birch,dark_oak,spruce,jungle,granite,diorite,andesite}.json`（10 條）；`hammer/oak.json` = `"type":"minecraft:crafting_shaped"`，`result.item="tetra:modular_double"`，`result.nbt` 帶 `double/head_left`＝`double/basic_hammer_left`、`double/basic_hammer_left_material`＝`basic_hammer/oak`、handle/stick 等 ⇒ **JEI 真有一條可查、且 output 天然帶 NBT 嘅 recipe**。
- **NBT 真會落到資料結構** ✅：`RecipeCard` 係 `record` 且含 `List<ItemStack> outputs()`；`JeiRecipeCards.fromLayout(...)` 內 `List<ItemStack> outputs = stacks(layout, RecipeIngredientRole.OUTPUT, 4, prefer)` ⇒ NBT 保留。
- **但 plan 寫嘅取法做唔到（新 HIGH）**：`JeiRecipeLayoutCollector` 係 `final class`（**package-private**）、`CollectedLayout` 係 `static final class`、`itemStacks(RecipeIngredientRole)`／`placedItemStacksOnePerSlot(...)` 全部**無 modifier ＝ package-private** ⇒ 一個新檔放 `client/autotest/` **無法**呼叫；而 §7.4 白名單只准 4 個檔（無 `client/jei/**`），亦無寫「改可見性」。真正可行嘅 public 路徑係**兩步**：bare `tetra:modular_double` → `JeiRecipeCards.forItem(stack)`（public）→ `card.outputs()`（public record accessor）→ 取帶 NBT 樣本 → 再 `openAndAskAbout(thatStack)`。plan 一個字都冇寫死呢條路 ⇒ 實作者必然自創或越界。
- **`question?` 未清（新矛盾）**：§7.2 明講入口收唔到 question，但 §2.2/L34 仍然寫 `{id, item, nbt?, question?}` ⇒ v6 自己留低一個前後矛盾（v6 冇改 §2.2）。
- **A3 斷言面仍然係 pre-filter（carry-over CRITICAL）**：`logic/RenderRecipeCardsAskTool.java:126` 發 `check.cards`，而 `PER_CALL_CAP=6` 喺 `:23`／套用喺 `:161-167`、`offerEmission` 喺 `:184` ⇒ `check.cards` 記嘅係**過濾前候選**（R2 真 trace：22 條 vs `render.cards.final` 1 條）。A3 仍寫「trace `check.cards` ⇒ 恰 1 張」＝**結構上永遠紅**。可用面已經存在：`AskService.java:521` 發 `render.cards.final`。

### 2.3 §7.3 usage delta 閘 —— **半死**【已核實】

- 檔案真存在：`packai_dev_game/config/packai-usage.json` = `{"2026-09-15":92151,"2026-09-16":314340,"2026-09-17":892293,"2026-09-18":352197}`（date→**當日累計**，mtime 09-18 18:47，**冇 09-19 條目**）。
- `logic/DailyTokenUsage.java`：`record()` 每次都即刻 `writeMap`（`:63-79`），key = `LocalDate.now(ZoneOffset.UTC)`（`:35`）⇒ 同一 UTC 日內 before/after 相減**可算** ✅（跨 UTC 日界會斷；plan 冇寫）。
- **兩個新洞**：① **冇數值門檻**——plan 只寫「delta 超預算 → 標記失敗」，全 plan 冇第二個數（A7 只有「≤20 條 ask」）⇒ predicate 未寫死，同 R2 打過嘅「冇寫死」同族。② **依賴未 commit 工作**：`git show HEAD:...AskService.java | grep DailyTokenUsage` → **零命中**；`git status --porcelain` → `?? logic/DailyTokenUsage.java`（**untracked**）、`M client/service/AskService.java`。即 §7.3 條閘依賴一份 HEAD 冇嘅 class，而 v5 §6 F11 白紙黑字寫「v5 **零依賴**未 commit 工作」——**v6 反駁咗自己嘅上一版，而該行未更新**。

### 2.4 §7.4 hook 落點同白名單 —— **真解決（連帶一個未撤回）**【已核實】

- `ClientSetup.java:126 private static void onClientTick(TickEvent.ClientTickEvent event)` **真**；註冊喺 `:64 MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick)` **真**；handler 唔要求 `mc.player != null`（世界／標題畫面都 fire，`else` 分支只 reset tracker）⇒ **加一行 `AutoTestHarness.tick()` 可行**，一眼 review 成立 ✅。白名單加咗 `ClientSetup.java` ✅。`grep -rn EventBusSubscriber src/main/java | wc -l` = **0** ⇒ plan「repo 零先例」claim 正確 ✅。
- **未撤回 §0/A1**：`AutoTestHarness.java`（§2.2 寫 `client/autotest/…`、§7.4 寫新檔）一定會被 `jar {}`（`build.gradle:77`，`finalizedBy 'reobfJar'`）收進 release jar ⇒ §0/L10「冇 flag ⇒ code path 完全唔存在（零殘留、零暴露）」同 A1「harness 相關 class 唔入 jar」**仍然係假**。v6 只加白名單、冇明文撤回（R2 FC2b 只做到一半）。
- 白名單漏一個新檔：`src/autotest/resources/packai-autotest.flag`（`ls -d src/autotest` → 唔存在，屬新建；LOW）。

### 2.5 §7.5 焦點量度 —— **真解決**【已核實】

v6 指名 `powershell GetForegroundWindow`／`GetWindowRect`（對子進程無盲區，補到 R2 盲點）⇒ F6 殘留收口 ✅。殘留：未寫死 window title 選擇器同完整命令（LOW）。

## 3. v6 自帶嘅新矛盾（N-series，本輪最值錢）

- **M1【HIGH】§2.2/L34 `question?` vs §7.2「入口收唔到 question」**——同一概念兩處唔同講法；實作者跟 §2.2 寫一個永遠讀唔到嘅欄位。
- **M2【HIGH】§7.2 取樣機制喺白名單內**結構上**做唔到**（jei 套件 package-private）——見 §2.2；要麼放寬白名單（加 `client/jei/**`）＋寫死兩步路徑，要麼刪 §7.2 第二點，兩者必揀。
- **M3【HIGH】§7.1 引用唔存在嘅 class ＋ 真 backup 分支會漏 ＋ 「同版本世界」無效 mitigation ＋ 冇「入世界後停止按」guard**；另 `BackupConfirmScreen` 第一粒 Button 會觸發**真世界備份寫入**（plan 冇寫代價）。
- **M4【MED-HIGH】§7.3 依賴 untracked `DailyTokenUsage` ＋未 commit `AskService`，與 v5 §6 F11「零依賴未 commit 工作」互相矛盾**；且冇數值門檻。
- **M5【MED-HIGH】沙盒同 dev client 共用同一個 gameDir**：`sandbox/minecraft` → `packai_dev_game`，而 `build.gradle:19-21` `-PpackaiDevGameDir` 就係設 dev client 嘅 workingDirectory ⇒ 同一份 `packai/trace`、`logs/`、`config/`（含 `packai-usage.json`）、`mods/`、`saves/`。並行跑 dev client 會污染 A2／A3／A6 同 delta 閘；plan 冇寫互斥。（實測今日 12:35–12:38 就**有 9 次** dev 啟動寫入同一個 gameDir。）
- **M6【MED】A3 baseline 物件喺沙盒唔存在**：`ls mods | wc -l` = 230（全部 `.jar`，另有隱藏 `.index`）＝ **零 packai jar** ⇒ A2 寫「231 個 mod」同今日實況唔一致（231 = 230 jar ＋ 1 `.index` 目錄），而「fix A 紅→綠」要靠 harness jar 帶住未 commit 嘅 fix 入去。
- **M7【MED】flag build 覆寫 release 輸出**：`gradle.properties:8,12` → `mod_version=0.2.3`；`build/libs/packai-0.2.3.jar` 實測 **1246826 B ＝ live `packai-0.2.3+mc1.19.2-forge.jar` 同 size** ⇒ `gradlew jar -PpackaiAutotest` 會蓋同一個檔，最自然嘅部署命令會一鍵把 harness jar 入 live（R2 N8／FC3 未修）。
- **M8【LOW】版本紀律**：檔頭 H1 仍寫「**v5**」，內容已係 v6（commit `af577f5` message 寫 v6）＋ §4/L66「（已建，md5 核對一致）」仍然對唔上 HEAD（R2 N7 未修）；driver 未寫「先 `mkdir <gameDir>/packai/autotest/`」（`packai/` 今日只有 `trace/`）＋ `status-*.json` 無 retention（R2 F10 殘留）。

## 4. 反方攻擊落空項（老實報）

1. 隔離（LD1）、真玩家入口（LD2）、`-l` CLI（LD6）、hook 落點可行性＋白名單加 `ClientSetup.java`（§7.4）、焦點量度（§7.5）、以及 **§7.2 嘅核心前提（JEI 有帶 NBT 嘅 hammer recipe）** —— 本輪全部核到**真**，反方唔打。
2. `ConfirmScreen` 存在、「children() 第一粒 Button ＝ 繼續」——R2 只質疑冇處理，本輪 bytecode 證實**處理路徑本身係啱嘅**（錯嘅係另一條分支嘅 class 名）。

## 5. 必須真跑才解（無法由檔案／bytecode 解決）

1. **沙盒能否起機**：230 jar ＋ harness jar 喺 Prism（`mmc-pack.json` 釘 Forge **43.3.5**，compile 用 **43.4.0**）→ 零 crash？`Loaded N mods` 幾多？**至今零次實跑**（實證：`sandbox/` 資料夾 mtime 09-19 12:41；`packai_dev_game/logs` 冇任何 12:41 之後寫入）。
   ⚠️ 唯一相關實測係**反證唔到嘅**：今日 12:35–12:38 有 **9 次**啟動全部 crash，但 log 開頭係 `--launchTarget forgeclientuserdev`／`--gameDir .`／`forge-43.4.0_mapped_official`（＝**gradle dev**，唔係 Prism），致命錯係 `Mixin apply failed untranslateditems.mixins.json:TranslationTextComponentMixin -> @Shadow field f_237499_ was not located`（SRG 名喺 official mapping 下搵唔到）⇒ 呢個係 **dev-runtime 專屬**問題，**唔可以**推論沙盒（SRG runtime）同樣 crash，亦**唔可以**當沙盒已驗證。
2. `prismlauncher.exe -l packai_sandbox` 實跑（會否要 account 互動／搶焦點／`-l` 喺 portable root 真命中）。
3. 真機 `loadLevel` 實際彈邊個框（bytecode 推導＝`ConfirmScreen`；未跑）。
4. 沙盒實跑時 JEI（11.8.1.1031…，`jei_version=11.8.1.1035`）＋ Tetra 5.6.0 對 `tetra:modular_double` 嘅 focus 結果、`check.cards` 真條數 ⇒ 決定 A3 修完之後係唔係真能紅→綠。
5. delta 閘實測（同 UTC 日 before/after 真數；跨日／並行 dev client 行為）。

## 6. Flip conditions（＝達標前嘅 acceptance checklist；全部屬「寫死／撤回」級，零設計變更）

- **FC1**：§7.1 改成寫死**真實 class 集合**（`ConfirmScreen`；另列 `BackupConfirmScreen`（`extends Screen`）為第二類，並寫明 `instanceof ConfirmScreen` **唔覆蓋**佢）＋ 寫死「**入世界後即停**按鈕」＋ 負控（非 ConfirmScreen 嘅按鈕框唔准被按）＋ 刪「同版本世界」呢個無效 mitigation。
- **FC2**：明文**撤回** §0/L10「零殘留、零暴露」同 A1 第二半（或改成 conditional source set `src/autotest/java`＋條件 `sourceSets.main.java.srcDir`＋自我註冊）；白名單補 `src/autotest/resources/packai-autotest.flag`。
- **FC3**：A1 寫死「跑邊條命令＋驗邊個 artifact＋正／負控」，只驗**自己 build 出嘅兩個 jar**；flag build 用**獨立檔名／輸出目錄**（禁覆寫 `build/libs/packai-0.2.3.jar`）。
- **FC4**：A3 斷言面改 `render.cards.final`（`AskService.java:521`）或 `tool.result` digest 嘅 `[card:N]` 計數；刪 §2.2/L34 `question?`。
- **FC5**：§7.2 寫死取樣機制：**兩步 public 路徑**（bare id → `JeiRecipeCards.forItem` → `card.outputs()` → 帶 NBT stack）**或**把 `client/jei/**` 加入白名單並寫明要改嘅可見性；二擇一，唔准留空。
- **FC6**：§7.3 寫死**數值預算**（例如 delta ≤ N tokens）＋ 註明依賴 `DailyTokenUsage`（untracked，需先 commit 或明文標「依賴未 commit 工作」）＋ 寫明 UTC 日界行為。
- **FC7**：§4 還原點**逐檔重做**並對 HEAD 核 md5；driver 加 `mkdir <gameDir>/packai/autotest/`＋`status-*.json` retention；數字由 231 改成「230 jar（＋harness jar 後 231）」。
- **FC8**：明文寫「沙盒同 dev client 共用 gameDir ⇒ 跑 autotest 期間禁止開 dev client」（或加檔案鎖），否則 A2／A3／A6 同 delta 閘唔可證偽。

## 7. 停手報告（第 3 輪硬上限觸發，交 SK）

1. **逐輪比分**：R1 **正方 2 : 反方 8** → R2 **4 : 6** → R3 **5 : 5**（未達 8:2）。
2. **卡死嘅載重決定**：LD4（零殘留／A1 第二半物理上做唔到）、LD8（同）、LD9（A3 用 `check.cards` ⇒ 永遠紅）、LD7（§4 還原點失效）——四項由 R1 帶到 R3，v6 一次都冇碰。
3. **最貴嘅未知**：沙盒**能否起機**（230 jar ＋ harness jar）＋ 真機 `loadLevel` 實際框 ＋ JEI 對木錘嘅 focus 結果。全部要真跑一次；成本 ≈ 一次 20 分鐘無人值守啟動。
4. **建議（3 選 1，唔准我自己開第 4 輪）**：
   - **(1) 批准「把 FC1–FC8 逐條寫死之後」當達標開工**——本輪餘項全部屬「冇寫死／撤回聲稱／改數字」，**冇一條要改設計**（唯二涉及 scope 嘅係 FC2 嘅 conditional source set 同 FC5 嘅白名單二擇一）。寫死後可**直接引用本表**當 acceptance。
   - **(2) 明示破上限**，再派一輪**只核 FC1–FC8**（明寫唔准加新要求；全部 RESOLVED 且無新自相矛盾即給 ≥8:2）。
   - **(3) 叫停**：改為先用 **Plan A 路線以外嘅最便宜實測**收口（單獨跑一次沙盒起機，唔做 harness），用真 log 決定值唔值得繼續。

## 8. 裁決

方向（真機自動化、零人手打字）＋ 兩個最硬嘅基建聲稱（隔離、真玩家入口）本輪**獨立核到真**；v6 亦真係把 R2 CRITICAL「開世界彈框」由**死**推上**半死**（機制、bytecode、存檔 NBT 三重核實）。但 v6 只改 §7 五段，**四個由 R1 帶落嚟嘅載重項一個都冇碰**（零殘留／A1／A3 斷言面／還原點），而新寫嘅 §7 自己帶咗 **4 條新矛盾**（`question?`、`BackupPromptScreen`、jei 套件不可達、依賴 untracked `DailyTokenUsage`）。**反方喺「新引入矛盾」上贏、正方喺「LD3 機制核到真」上贏，淨值持平。**

**正方 5 : 反方 5**（未達 8:2 門檻 → **唔批准開工**；必修＝FC1–FC8）
