# R2 反方 review — packai「真機自動測試 harness」計畫 **v5（Plan B：build-flag jar ＋ 沙盒 instance）**

> 被審：`docs/plans/2026-09-19-in-game-autotest-harness.md`（md5 `a412ca68c12a3095ee726192e84942ff`，89 行，HEAD `6af531c`）
> 角色：**R2 反方**。目標＝盡最大努力證明佢**仍未達標**。**禁令**：只讀唔改（本檔為唯一輸出）。
> 樹：`forge/1.19.2`（primary）、MC **1.19.2** ＋ Forge **43.x**；`neoforge` PAUSED。契約：主 `AGENTS.md` ＋ 專案 `AGENTS.md`。
> 標記：**【已核實】**＝本輪有工具輸出（file:line／真 jar bytecode／真檔）；**【未能核實】**＝查唔到，明講。
> ⚠️ 證據基準：`git status --porcelain | wc -l` = **116**（R1 時 118 → 樹已再變）。凡「現有行為」claim 要 pin 樹狀態。

## 載重決定（LD）存活表 — 比分由表計出

| LD | 內容（plan 行） | 反方判定 | 一句理由 |
|---|---|---|---|
| LD1 | 隔離＝沙盒 instance（junction → 獨立副本）＋原 instance 零寫入（§1 L16、§2.3 L40） | **存活** | 實測 `packai_dev_game` 係**真副本**（`isjunction=False`、inode 同 live 唔同）；live jar sha256 `06b5b129a114a233`／mtime 09-18 07:12 對得上 plan ✅ |
| LD2 | 入口＝真玩家入口 `openAndAskAbout(stack)`（§2.2 L34） | **存活** | `public static void openAndAskAbout(ItemStack)`（`AiAssistantScreen.java:108`），真 `JeiTargetResolver.pin`（`JeiTargetResolver.java:35`）＋真問；caller `ClientSetup.java:174` ✅ |
| LD3 | 入世界＝標題畫面＋`createWorldOpenFlows().loadLevel(screen, worldFolder)`（§2.2 L33） | **死** | 真機必然彈互動框（見 F2／N1）；harness 會卡死 → A3/A4/A5 全部量唔到 |
| LD4 | build flag「零殘留」：冇 flag ⇒ harness code path 完全唔存在（§0 L10、§2.1 L27） | **死** | 唯一新檔放 `src/main/java` ⇒ class **一定**入 release jar；§0 同 A1 第二半自相矛盾（見 N2） |
| LD5 | 成本＝harness 內 ≤20＋timeout，driver 只印預估（§2.2 L36、§2.4 L51、A7 L63） | **半死** | 上限喺**被測物自身**內，driver 側無獨立閘；`config/packai-usage.json` 明明可以做到（見 N9） |
| LD6 | 沙盒啟動＝`prismlauncher.exe -l <instance>`（§2.3 L41） | **存活** | CLI 真存在（`-l, --launch <instance>` by **instance ID**）；portable root 確認（`portable.txt`＋`prismlauncher.cfg`；`%APPDATA%\PrismLauncher` 唔存在）✅ |
| LD7 | 清理／還原＝driver `finally` ＋ §4 `build.gradle.bak`（§2.4 L50、§4 L66） | **半死** | `finally` 設計真；但**還原點係舊 revision**，而且「md5 核對一致」已可證偽（見 N7） |
| LD8 | A1 靜態檢查：flag 資源唔存在＋harness class 唔入 jar（A1 L57） | **死** | 第二半物理上做唔到；照抄現有先例會變成恆綠（見 N2／N8） |

→ **3 存活 ＋ 2 半死 ＋ 3 死**。兩個「死」係**閘死整條 pipeline**（LD3 令一條 case 都跑唔到；LD8 令自己嘅驗收閘唔可能綠）→ **正方 4 : 反方 6**（見文末）。

---

## F1 —【真解決】`-w` no-op 已刪，改 `-l <instance>`

**【已核實】**`prismlauncher.exe --help`：`-l, --launch <instance> Launch the specified instance (by instance ID)`；`-w` 仍在但 plan 唔用。Portable 偵測：portable 目錄有 `portable.txt`(301B)＋`prismlauncher.cfg`，`$APPDATA/PrismLauncher` 不存在 ⇒ `-l packai_sandbox` 會解析到 `instances/packai_sandbox`（資料夾名 == `instance.cfg` `name=` == `packai_sandbox`，兩種語義都命中）。**反方此擊落空**。

## F2 —【表面解決 → 新 CRITICAL】API 真存在、hook 改對，但**互動框完全冇處理**：`loadLevel` 對付呢個存檔**一定**彈確認畫面

**【已核實，bytecode 級】**真 jar `forge-1.19.2-43.4.0_mapped_official_1.19.2.jar`（`javap -p -c`）：
- `Minecraft.createWorldOpenFlows()` ✅（回 `net.minecraft.client.gui.screens.**worldselection**.WorldOpenFlows`，唔係 plan 暗示嘅 `…screens`）。
- `WorldOpenFlows.loadLevel(Screen, String)` ✅ → `iconst_0; iconst_1; doLoadLevel(Screen,String,ZZ)V`；4-arg 再 `doLoadLevel(…,ZZZ)`（p5=false）。即 **p4=true**。
- 5-arg `doLoadLevel` 分流（offset 156–203）：`var12 = (worldData instanceof PrimaryLevelData) && hasConfirmedExperimentalWarning()`；靜默路徑條件＝`var12!=0 || p4==0`，之後仲要 `!isOldCustomizedWorld && lifecycle==stable`；否則 → **`askForBackup(...)`（offset 292）或 `ForgeHooksClient.createWorldConfirmationScreen(Runnable)`（offset 307，回傳 void）**。
- 沙盒世界 `packai_dev_game/saves/新的世界 (1)/level.dat`：NBT key **`confirmedExperimentalSettings`**（唔係 field 名）值 = **0**（`\x01\x00\x1dconfirmedExperimentalSettings\x00`＝TAG_Byte 0）⇒ `hasConfirmedExperimentalWarning()==false` ⇒ **`var12` false ＋ `p4` true ⇒ 一定入互動分支**（該世界非 old-customized ⇒ 落到 `createWorldConfirmationScreen`）。
- 另有第三條互動路徑：datapack 載入失敗 → `Minecraft.setScreen(new DatapackLoadFailureScreen(...))`（offset 107）。

**後果**：無人答嘅 Forge 實驗性設定確認畫面 → harness 永遠卡住 → driver 20 分鐘 timeout → A3 永遠紅，而 plan 會誤診為「cases.json／入口寫錯」。**R1 F2 flip condition 第三項（「列出互動 prompt 路徑點處理」）v5 一個字都冇答。**

## F3 —【真解決（新增規格洞）】入口真存在、真 auto-ask；但 cases.json `question?` 入口收唔到

**【已核實】**`AiAssistantScreen.java:108` `public static void openAndAskAbout(ItemStack stack)`：null／empty 早退 → 取／開 `AiAssistantScreen` → **`if (ChatSession.isBusy()) return;`**（`ChatSession.java:29` `private static volatile boolean busy`、`:223`）→ `JeiTargetResolver.pin(stack)` → `screen.askAboutStack(stack)`。`askAboutStack`（**private**，`:339`）＝`ChatSession.setPendingItems(List.of(AskService.fromStack(stack)))` ＋ `askTemplate("packai.ask.item_about_id", name, id, false)`；lang 值（`zh_tw.json:232`）＝「「%s」（%s）在這個整合包有什麼用途、配方和取得方式？」。
⇒ ① 入口係「玩家真入口」✅；② **問題由固定 lang 模板決定，冇任何參數可注入** ⇒ plan §2.2 嘅 `{id, item, nbt?, **question?**}` **用呢個入口實現唔到**（見 N3）；③ `isBusy()` 早退係**靜默**，harness 要有等待／重試（plan 只有 180s timeout）。

## F4 —【表面解決】成本上限搬咗入被測物，driver 側仍然冇獨立閘

**【已核實】**`DailyTokenUsage` 落 `<gameDir>/config/packai-usage.json`（`DailyTokenUsage.java:23/27/39-40`），**沙盒真係有** `packai_dev_game/config/packai-usage.json` ⇒ driver 完全有能力跑前／跑後 assert delta ≤ N。v5 A7 只寫「印預估」。另：沙盒 `config/packai-client.toml`（15632 B）**含真 apiKey**（只驗 key 存在，冇讀內容）⇒ 20 條＝**20 次真付費 call**。R1 F4 flip（「唔靠玩家 config 嘅 driver 側上限」）＝**未做**。

## F5 —【真解決 → 反方此項落空】trace／log 輪替**打唔到**現有綠閘

**【已核實】**`tests/check_ask_display_leak.py` 硬編 `PRISM_LOG = …/instances/**AI_test_NFWC_DIM**/minecraft/logs/latest.log`、`--trace` 預設 `""`；沙盒寫嘅係 `packai_dev_game/{packai/trace,logs}` ⇒ **live 綠閘嘅輸入零影響**。（沙盒自身 39 檔 + 20 條 = 59 > `AskTrace.DEFAULT_KEEP_FILES=50`（`AskTrace.java:32`）仍會輪替，但只喺可棄副本內。）R1 F5 嘅「換走綠閘輸入」**已被沙盒設計真正解掉**，老實報。

## F6 —【真解決（細節未釘）】vbs 已刪

**【已核實】**`%LOCALAPPDATA%\hermes\scripts\bg_launch.py` 存在；plan L84 明寫唔用 vbs、改用 `-l`＋開完獨立量度前景。殘留：**冇指名用邊個工具／命令**做獨立量度，而 MC 窗屬 java 子進程（`bg_launch` 自報 `focus_stolen` 對子進程有盲區——R1 原文）⇒ 要寫死「用 `GetForegroundWindow` 對 **MC 窗 title** 量」先算釘實（LOW-MED）。

## F7 —【真解決】清理路徑齊，而且「改 config」呢一步整條消失

**【已核實】**driver step 6 `finally` 刪 `cases.json` ＋ taskkill 沙盒 java（§2.4 L50）；§2.2「永不重複跑（見 DONE 即停）」。更關鍵：v5 改用 **build flag**，**完全唔再改 `config/packai-client.toml`** ⇒ R1 講嘅「TOML 含 API key ＋ `SPEC.save()` 重寫全檔」風險**結構性消失**。✅

## F8 —【半解決】白名單仍然漏「hook 落點」檔案；而新 `check_autotest_flag.py` 唔喺任何既有清單

**【已核實】**v5 白名單（L86）＝`build.gradle`、`AutoTestHarness.java`、`tests/check_autotest_flag.py`、`docs/plans/**`、driver。但：① 現成 tick hook 喺 `ClientSetup.java:126 onClientTick`（註冊 `:64`），**唔喺白名單**；plan 又寫 harness 係「唯一新檔」⇒ 自我註冊定改 ClientSetup？**冇寫**。② repo 全域 `grep -rn EventBusSubscriber src/main/java` = **0 命中** ⇒ 冇先例可抄。③ `src/autotest/resources/packai-autotest.flag` 屬新建目錄（算覆蓋）；④ `src/autotest/` 實測**唔存在**（`ls -d src/autotest` → No such file）✅ plan 講「新建」正確。

## F9 —【真解決】唔自寫結果檔

**【已核實】**plan §2.2 L35 明寫答案落**現有** trace、harness 只寫 `status-<ts>.json`（進度／DONE）。R1 F9 flip 條件（「證明自家 JSONL 有 trace 冇嘅欄位」）唔再需要。✅

## F10 —【真解決（兩處小洞）】路徑統一、檔名帶時間戳

**【已核實】**`<gameDir>/packai/` 已存在（實測只有 `trace/`）⇒ 建 `autotest/` 可行。殘留：① **冇寫 driver 要先 mkdir** `<gameDir>/packai/autotest/`（harness 靠讀 cases.json，driver 冇建目錄就寫唔入）；② `status-*.json` **冇 retention**（每次跑留一個，同 repo 慣例相反）。LOW。

## F11 —【表面解決】機制零依賴 ✅，但**證據基礎未 pin**

**【已核實】**harness 機制確實唔再用 `dailyTokenLimit`／`DailyTokenUsage` ⇒ 機制上零依賴成立。但 plan 全篇**冇寫死 run 嘅樹狀態**（commit／dirty 清單），而 `git status` 由 R1 嘅 118 變 **116**，且 A3 要驗嘅「fix A」本身就係未 commit 嘅改動 ⇒ 「現況 baseline」唔可重現，A3 嘅紅→綠唔可證偽。MED。

---

## 新問題（N1…N10）

- **N1【CRITICAL】**自動開世界**必然**彈互動框（`ForgeHooksClient.createWorldConfirmationScreen` ／ `askForBackup` ／ `DatapackLoadFailureScreen`）——證據：`javap -p -c` `WorldOpenFlows.doLoadLevel` offsets 156–203／292／307 ＋ 沙盒 `level.dat` `confirmedExperimentalSettings=0`。**plan §2.2 L33 冇處理任何一條**。⇒ 唔改就一條 case 都跑唔到。
- **N2【CRITICAL】**`AutoTestHarness.java` 放 `src/main/java`（§2.2 L31「唯一新檔」）⇒ **release jar 一定含 harness class**（`jar` 打包 `sourceSets.main`，`processResources` 只影響資源）。故 §0 L10「code path 完全唔存在（零殘留、零暴露）」係**假**，A1 L57「harness 相關 class 唔入 jar」**物理上唔可能**（唯一出路＝獨立 conditional source set，例如 `src/autotest/java` ＋ 條件 `sourceSets.main.java.srcDir`，並要解決「冇呢個 class 時邊個註冊 hook」——或用 `@Mod.EventBusSubscriber` 自我註冊，見 N6）。
- **N3【HIGH】**cases.json `question?` 入口收唔到：`openAndAskAbout(stack)` 無問題參數，問題由 `packai.ask.item_about_id` 模板寫死（`AiAssistantScreen.java:339-349`）。要麼刪此欄位，要麼換 API（但換 API 就唔係「真玩家入口」）。
- **N4【HIGH】**「帶 NBT 嘅 tetra 工具」（木錘）**冇來源、冇 API**：plan §2.2 只寫 `{item, nbt?}`，冇名任何 construction。現成可用的只有 `ItemResolver.stackFromId(String)`（`ItemResolver.java:182`，`:203` `TagParser.parseTag(snbt)` 支援 `id{snbt}`）同 `stackFromRef(ItemRef)`（`:226-238` 明文「Bare stackFromId loses Weapon Master／SlashBlade stats」）。而真 trace **只記 bare id**（`grep -oh '"item":[^,]*' *tetra*.jsonl` → `"item":"tetra:modular_sword"`，零 SNBT）⇒ 冇任何真樣本可重播；harness 自編 SNBT ＝ A3 量度一件**唔存在於真實世界**嘅物品（fix A 修好都可能照紅）。
- **N5【HIGH】**A3 驗收面選錯：plan 寫用 `trace check.cards` 斷言「恰 1 張合成台卡」，但 `check.cards` 喺 `RenderRecipeCardsAskTool.java:121-134` 發出，**早於** `ModularFrameCards.keepOnlyStandardRecipeCard`（`:135-140`）同 `PER_CALL_CAP=6`（`:23`／`:161-169`）⇒ 佢記嘅係**過濾前候選清單**，斷言「==1」**結構上永遠紅**。真 trace 實證：`ask-20260918-182231-tetra_modular_double.jsonl` → `check.cards` **22 條**（多條同 ts 同一 `primaryOutputId: tetra:modular_double`、`category: Crafting`）vs `render.cards` 2 條 vs `render.cards.final` 1 條。正解＝用 **`render.cards.final`**（`AskService.java:521`）或 `tool.result` digest 數 `[card:N]`。
- **N6【MED-HIGH】**白名單／hook 矛盾（見 F8）：要改嘅檔（`ClientSetup.java`）唔喺白名單；repo 零 `@Mod.EventBusSubscriber` 先例 ⇒ 實作者必然越界或自創註冊法（drift）。
- **N7【MED-HIGH】**還原點失效：`.hermes/backups/2026-09-19_dev_env/build.gradle.bak`（CRLF 正規化 md5 `0f6c21b5…`）**等於 `97cf3d7^:forge/1.19.2/build.gradle`**，而 HEAD／工作樹 = `c66bf2db…`（`git diff` 乾淨）⇒ §4 L66「（已建，md5 核對一致）」**對唔上**；還原佢會 **revert 已 commit 嘅 `-PpackaiDevGameDir` 功能**（commit `97cf3d7`）。
- **N8【MED-HIGH】**flag jar 同 release 同一個輸出路徑：`mod_version=0.2.3` ⇒ `gradlew jar -PpackaiAutotest` 覆寫 **`forge/1.19.2/build/libs/packai-0.2.3.jar`**（實測同 live instance 現行 jar **同一個 size 1246826**）。`mc_mod_deploy_jar.py --jar` 預設＝config `known_jars[0]`＝`build/libs/packai-0.2.1.jar`（stale），但「修完 fix A 正常部署」最自然嘅命令就係 `--jar build/libs/packai-0.2.3.jar` ⇒ **harness jar 一鍵入 live**。`mc_mod_jar_guard.py` 條 3（sha ∉ known_jars）今日仍會 catch（known_jars 只有 0.2.1／dist），但**一旦有人為 0.2.3 補 known_jars（自然動作）＋ 走官方腳本（有 backup）→ 兩條都靜默**。plan 冇要求 flag build 用獨立檔名／輸出目錄。
- **N9【MED】**成本無獨立上限（見 F4）；`packai-usage.json` 可得而未用；沙盒 toml 帶真 key。
- **N10【MED】**數字／版本 claim：(a) §0 L10「231 mod 原封不動」／§1 L15「全部 231 個真 mod」／A2 L58「231 個 mod」——實測 dev copy mods = **230 個 jar ＋ 1 個 `.index` 目錄 = 231 entries**（live = 231 jar）；(b) 沙盒 `mmc-pack.json` 釘 Forge **43.3.5**，compile 用 **43.4.0**（`gradle.properties:8`）⇒ 要寫明「harness jar 喺 43.3.5 runtime 上驗收」；(c) A6(b)「原 instance sha256 不變」✅ 已對得上（`06b5b129a114…`）。

## 反方攻擊落空項（老實報，唔准砌）

1. 隔離係**真**：`packai_dev_game` 非 junction（`os.path.isjunction=False`、inode 別於 live），sandbox 只係 `minecraft` 一層 junction；SK 原 instance 一個 byte 唔碰成立。
2. `-l` 對 1.19.2 可行（CLI 明文 by instance ID）；portable root 確認；`instance.cfg` `ManagedPack=true` + 空 ID 同 live 一樣 ⇒ 唔會令 Prism 唔認得沙盒。
3. 入口 `openAndAskAbout` 真係 public static 真 auto-ask；`ClientSetup.java:174` 真係 JEI hold-Y／ThinkHold 同一入口。
4. `createWorldOpenFlows()`／`loadLevel(Screen,String)`／`Minecraft.stop()`／`TitleScreen`（`net.minecraft.client.gui.screens.TitleScreen`）**全部真存在**（1.19.2）——R1 呢半句成立。
5. F5（綠閘被換輸入）、F9（自寫結果檔）、F10 路徑一致性、F11 機制依賴：**反方原打算打死，查完撤回／降級**。
6. 專案 baseline 數字對得上：`ls tests/check_*.py | wc -l` = **121**、`ls tests/check_autotest*.py` → 不存在（plan 講「新建」正確）、沙盒 `saves/` 恰兩個世界（`新的世界`、`新的世界 (1)`）。

## 最貴嘅未知（要咩數據才解得開）

1. **有冇可程式化路徑繞過 loadLevel 互動框**（唯一能否定 N1 嘅東西）：最便宜解＝把沙盒世界 `level.dat` 嗰個 `confirmedExperimentalSettings` byte 由 `00`→`01`（先 backup），開一次沙盒 dry-run，睇 `latest.log` 有冇直接 `Loaded`；或者核 `WorldOpenFlows.loadWorldStem(LevelStorageAccess,boolean)`（public）＋ `createLevelFromExistingSettings(...)`（public）可否取代 `loadLevel`。
2. **沙盒真機起機**：Forge 43.3.5 + 230 jar ＋ harness jar → 零 crash？`Loaded N mods` = 幾多？（A2 至今零實測；冇人開過 `packai_sandbox`。）
3. **`prismlauncher.exe -l packai_sandbox` 實跑**：會否搶焦點／要 account 互動；`-l` 喺 portable root 下係唔係真命中（本輪只核 CLI 文字，未跑 game）。
4. **木錘真 NBT 由邊嚟**（N4）：真 trace 只記 bare id，`ItemRef.hasSample()` 要有 InvPick／pin 樣本——決定 A3 可唔可以有意義咁紅→綠。

## flip conditions（＝下一版 acceptance checklist，要 SK 認可嘅方向）

- **FC1**：改寫 §2.2「開世界」段——**或者**（a）提供實測 log 證明該存檔 `loadLevel` 唔會彈互動框（例如 pre-seed `confirmedExperimentalSettings=1b` 後 dry-run），**或者**（b）harness 在 tick 內偵測 `ConfirmExperimentalSettingsScreen`／`BackupConfirmScreen`／`DatapackLoadFailureScreen` 並自動確認，並把該偵測列入 acceptance。**唔准**只寫「loadLevel 真存在」。
- **FC2**：決定 harness 檔案落點並寫死：**或者**（a）獨立 conditional source set（`src/autotest/java`）＋ 自我註冊（`@Mod.EventBusSubscriber`，要寫明）＋ conditional `processResources`，令「冇 flag ⇒ 零 class」真成立；**或者**（b）明文撤回 §0「code path 完全唔存在」同 A1 第二半，並把 `ClientSetup.java` 加入白名單。
- **FC3**：A1 寫死「跑邊條命令＋驗邊個 artifact＋正／負控」——檢查對象＝**自己 build 出嘅兩個 jar**（release／flag），**唔准**用 `tests/check_jar_contains_fix.py` 式「live instance 最新 `packai*.jar`」（今日 0.2.3 本來就冇 harness ⇒ 恆綠、零鑑別力）；同時 flag build 必須用**獨立檔名／獨立輸出目錄**（防覆寫 `build/libs/packai-0.2.3.jar`，見 N8）。
- **FC4**：A3 改用 `render.cards.final`（`AskService.java:521`）／`tool.result` digest 作卡數斷言面；cases.json 每條要寫死 **ItemStack 真來源**（SNBT 由邊度嚟）；刪走入口收唔到嘅 `question?`；driver 加**獨立**成本閘（跑前後比 `config/packai-usage.json` delta ≤ N）。

## 裁決

方向（真機自動化、零人手打字）**仍然站得住**，而且 v5 有實質進步：隔離策略（LD1）同玩家入口（LD2）本輪都用真 artifact 驗到**真**，F1／F3／F5／F6／F7／F9／F10 係真修。但兩個**閘死整條 pipeline** 嘅載重決定仍然死：**LD3**（自動開世界必然彈無人可答嘅確認框——bytecode ＋ `level.dat` 雙重實證）同 **LD4/LD8**（「零殘留」同 A1 第二半物理上做唔到）。另外 A3 嘅斷言面揀咗**過濾前候選流**（今日真 trace 22 vs 1），即係「照 plan 寫好都永遠紅」。

**正方 4 : 反方 6**（未達 8:2 門檻 → 唔批准開工；必修見 FC1–FC4）
