# Follow-up #1c（**卡走位量度儀器**）— 反方 review **R1（新交付物第一輪）**

> 審：`docs/plans/2026-09-19-followup1c-card-placement-instrument-plan.md`
> **md5 = 89d3af7c4162a5cecb08873b28f02e0c**（66 行；本報告只評此版本）
> 只讀唔改。行號一律指 `forge/1.19.2/src/main/java/com/skps9/packai/`。
> 前情：同線 v1–v6＋6 輪 review 未過（3:7→4:6→5:5→6:4→4:6→3:7）。本輪＝**新交付物（只儀器、唔修法）**，唔繼承舊比分。

## ① 驗過嘅事實（真／假／未核實）

**真（親跑／親讀）**

1. **落位行號大致對**：`placeEmissionCardsByRef :778`／插入 `:819 int insertAt = skipCardsAfter(blocks, i + 1);`；`stripCardRefTokens :828`；`splitTextIntoStepBlocks :848`；`disperseUnplacedEmissionCards :889-982`；`rebuildEmissionStepIndex :985`；`sectionLastAfter :1010`；`findEmissionInsertIndex :1062`。**但 plan 寫 `emissionSectionOf :906` 係「呼叫點」，定義在 `:1121`**（`:906 int wantSec = emissionSectionOf(c);`）。
2. **`findEmissionInsertIndex` 係死碼**：`grep -rn findEmissionInsertIndex forge/1.19.2/src` → 只有 `:1062` 定義、**0 caller**（neoforge 樹同樣）。佢唔係 plan 漏咗嘅落位路徑，但 plan「落位點只有 3–4 處」嘅隱含聲稱仍要靠全檔 insertion 點核（見 3、5）。
3. **落位 insertion 實際 ≥5 條 branch，唔係 plan 個 3 條**（全在 `disperseUnplacedEmissionCards`）：
   - `:907-909 if (wantSec < 0) { blocks.add(Part.card(i)); continue; }` → **絕對最尾**（係真 placed，唔係 UNPLACED）
   - `:919-922 cand.isEmpty()` → `sectionLastAfter`，`<0` 時 `insertAt = blocks.size()`（**第二條絕對最尾**）
   - `:954-957 bestScore > 0` → 插喺命中 needle 嘅 step 之後（plan 叫 NEEDLE）
   - `:960-965` 冇命中 → `sectionLastAfter`，`<0` 時 `blocks.size()`
   - `:969-971 clamp` → `blocks.size()`
   ⇒ plan 把 5 條併成 `NEEDLE／SECEND／UNPLACED`：**唔完備亦唔互斥**；「絕對最尾」（正正係 SK 講「去咗最尾」）**冇獨立 mode**。
4. **`unplaced` 結構上恆 0**：`:899-905` 只 skip `c == null || c.isEmpty()`，之後每條 branch 都 `blocks.add(...)`（`:908/:972`）⇒ 非空卡**一定會被放**；`unplaced ≥1` 只可能來自「空卡」呢個同症狀無關嘅情形 ⇒ A3 該判準係幽靈。
5. **第二條落位家族完全未覆蓋**：`AiAssistantScreen:835-841`：`if (cardStrip) … interleaveEmissionCards` **else** `RecipeEmbed.parts(body, origCards)`；`RecipeEmbed.parts` 內 **9 個 `Part.card(` insertion 點**：`:366`（`sectionByOutputs :315`）、`:587/:594`（`fromMarkers :551`）、`:1361`（`attachCardsForOutput :1344`）、`:1475`（`fallback :1444`）、`:1529`（`fallbackByParagraphs :1498`）、`:1576/:1586`（`appendUnused :1549`）、`:1628`（`insertCardInSection :1591`）。**reachable**：`AskService:366 if (cardsMode == RecipeCardsMode.AI && RecipeCardsMode.llmExpected())` 為 false（例 `mode=offline`，`RecipeCardsMode:199-206`）→ 走 `:446 withRecipeCards(cardsOut)`（保留 `cardStrip=false`）。另 `AiAssistantScreen:867-868` tool-parts 卡亦係獨立落點。
6. **`maxSameBlock`（同一 blockIdx ≥2）唔等於「堆埋」**：每張卡都係 `blocks` 一個獨立 Part（`:821/:972 blocks.add(insertAt, Part.card(i))`）⇒ 若 `at` 係**最終 index**，兩卡結構上唔可能同 index（≥2 永不發生＝幽靈）；若係**插入時 index**（a）後一張選同一 `insertAt` 會撞號（假陽性）（b）真相鄰（5,6）反而唔同號（假陰性）；若係**anchor text-block index**（唯一有意義讀法）plan **冇定義**（MARKER 分支用 `:819 skipCardsAfter(blocks, i+1)`，anchor ≠ insertion index）。plan §2.2 只寫 `at=<blockIdx>`，三種讀法都冇排除。
7. **`atLastBlock` 唔等於「去咗最尾」，仲係假陽性來源**：`blocks.addAll(sourceParts)` 喺 `:769` 才 append 【来源】⇒ log 時「最後一個 block」係**正文最後一段**（多數係「怎么用」最後一步），唔係答案最尾。而 `wantSec<0` 卡（maintenance，`emissionSectionOf:1131-1133`；maintenance 卡真入 cardsOut）**設計上就係**落 `:908` ⇒ `atLastBlock ≥1` 會因**正常設計後果**亮燈。反過來，若 SK 講嘅「卡去咗最尾」指 【来源】之後／答案尾，儀器（append sources 前量）**結構上睇唔到**。
8. **gating 慣例對得上，但落點未 pin**：`jsObtainDiagLog` 真身 `PackAiConfig:463-466`（`b.push("ui")` `:359` → `b.pop()` `:549`）＋accessor `:962`（`try/catch` 回 false）；真 instance `packai-client.toml:79 [ui]` 之下 `:239 jsObtainDiagLog = false`。**但**（i）`jsObtainDiagLog` 係寫 **JSONL 檔案**（`JsObtainSites:589 diagEnabled()` → `diagFile()` = `packai/js-obtain-diag.jsonl`），**唔係 `latest.log`** ⇒ plan 講「跟 jsObtainDiagLog 慣例」但行為形態唔同；（ii）A2 要手改沙盒 toml `cardPlacementDiagLog=true`，plan **冇寫死 table 名**（應 `[ui]`）；（iii）既有 toml 冇新 key 時要等 Forge config correction 寫回，我**未核實**。
9. **現有閘唔會因新 log 行轉紅（親跑）**：`for f in tests/check_*.py` → **TOTAL_PASS=121 TOTAL_FAIL=1**（唯一紅 `tests/check_ask_display_leak.py rc=2`）⇒ plan A1 寫嘅 baseline **數字對**。`check_ask_display_leak.py:46 MARKER="Pack AI display body ver="`、`:48 ENSURE_PREFIX` ⇒ 只 parse 兩個前綴（`:281-296`）；`packai cardplace:` 唔中。`check_settings_registry.py:130-142` 係 **registry→config 單向**（新 define 唔入 registry 唔會紅；`jsObtainDiagLog` 亦冇 UI）；`check_settings_setters.py:154-162` 只 iterate 已存在 setter。
10. **源碼窗口式斷言有 margin**：`check_recipe_embed.py:524-526`、`check_card_tool_emission.py:235-237` 用 `ile = embed[embed.index("interleaveEmissionCards"):]` 再 assert `placeEmissionCardsByRef in ile[:2500]`、`disperseUnplacedEmissionCards in ile[:2500]`、`stripCardRefTokens in ile[:3500]`。實測距離 **676／824／896 字** ⇒ margin **1,600–2,600 字**（加幾行 log 安全，**前提係插點喺 `:764-768` 之後**；喺 `interleaveEmissionCards` 體入面插 >1.6k 字就會紅）。
11. **Java 測試面**：`find forge/1.19.2/src/test -name "*.java" | wc -l` → **49**（plan 數字對）。`RecipeEmbedCheck.java:17 RecipeEmbed.parts(sandwich, 2)` 證明**非-strip 路徑**可用純字串＋`int cardCount` 做 Java 測試；但 **strip 路徑冇 `int` arity 版本**（`:749 interleaveEmissionCards(String, List<RecipeCard>)` 唯一入口），而 `RecipeCard` 係含 `List<ItemStack>`／`FluidStack` 嘅 record，`isEmpty() :540`／`mentionKeys() :490` 要真 stack ⇒ 要 MC bootstrap（`AskCardPlacementCheck` 檔頭自認）。
12. **樣本量**：`AutoTestHarness.java:43 MAX_CASES = 20`、`:49 BUDGET_MS = 20 分鐘` ⇒ 一次 run 已能做到 20 case；plan A3 只用 3 次。

**假／未核實**

- **假**：plan §5.4「（唯一批准路徑）`mc_mod_deploy_jar.py --target packai` **唔涉及真 instance**」。真身：`~/AppData/Local/hermes/state/mc_mod_jar_guard.json` 只有一個 target `packai` → `mods = …/instances/AI_test_NFWC_DIM/minecraft/mods`（**真 instance**）。沙盒係另一 instance `packai_sandbox`（231 mods、自有 `config/packai-client.toml`、mods 內係 `packai-autotest-dev.jar`）⇒ 去沙盒要 `--jar <path> --mods <sandbox>/mods`（ad-hoc）或新增 guard target；`--target packai` 照字面會覆寫真 instance（`jar_glob=packai-*.jar` 亦唔 match 沙盒嗰個 jar 名）。
- **未核實**：新 config key 喺既有 toml 會唔會自動寫回（未開 game 驗 Forge correction）；`[ui]` table 未由 plan 寫死。

## ② 問題（severity ＋ 證據）

- **O1 CRITICAL — 儀器量唔到「堆埋」。** `maxSameBlock` 三種讀法兩種無效、第三種 plan 冇定義（事實 6）。真身「堆埋」= 兩張卡之間冇 TEXT（`skipCardsAfter :1112-1118` 令第二張卡被推去第一張之後）⇒ 要量**最終 `blocks` 內連續卡 run**（或「≥2 張卡掛同一 anchor」），單位唔係 `blockIdx` 相等。
- **O2 CRITICAL — 儀器量唔到「去咗最尾」，兼會假陽性。** 「最後一個 block」係正文末段（`:769` 後才 append sources）＋`wantSec<0` 卡設計上落絕對最尾（`:907-909`）⇒ `atLastBlock ≥1` 可由**正常輸出**觸發（事實 7）。要量 SK 症狀需記 `anchor ∈ {step:<n>, secTail, absEnd}` 同「卡係唔係喺 【来源】 之後」。
- **O3 HIGH — mode 枚舉唔完備／唔互斥，兼漏一整條家族。** 事實 3（5 條 branch）＋事實 5（非-strip 路徑 9 個 insertion 點、`mode=offline` 可達）。`UNPLACED` 係錯 label（真身 `:907-909` 係「放咗、喺絕對最尾」）⇒ A3 多一個幽靈判準（事實 4）。
- **O4 HIGH — A3「重現判定」係假陽性友好，會令 plan「成功」而畫面一模一樣。** `unplaced≥1` 恆 0（O3）、`atLastBlock≥1` 多條正常路徑觸發（O2）、`maxSameBlock≥2` 結構上唔會出現（O1）⇒ 三個 predicate 冇一個真係鑑別到症狀；A3 寫「至少 1 次出現」＝任何一個亮就當重現。呢個係 v6 R1 O1/O6（判準唔等價、假綠）換一層復發。
- **O5 HIGH — 沙盒／部署聲稱錯（外部工具行為 claim）。** 照 plan §5.4 字面跑會部署入**真 instance**，同「唔涉及真 instance／零 GUI 干擾」矛盾；沙盒路徑（`--jar --mods`）plan 冇寫。
- **O6 MED-HIGH — 新閘冇鑑別力（同 v6 O9 同族）。** `tests/check_cardplace_instrument.py` 只係**源碼文字閘**：分到「有 log 字串／冇」，**分唔到「log 邏輯正確」**（唔可能跑 `interleaveEmissionCards`）。plan 冇寫負控係「改壞邊個行為、期望邊條紅」。更好嘅閘：(a) 加 `interleaveEmissionCards(String, int)` overload（形狀照 `parts(String,int) :132`）→ 用 `RecipeEmbedCheck` 式 harness 直接 assert 最終 `blocks` 內卡嘅 anchor／連續性（純字串、唔需 MC registry）；或 (b) 對已可測嘅非-strip 路徑加 case。呢個係**唯一**能證「log 對唔對」嘅閘，成本 1 個 overload。
- **O7 MED — 樣本量。** n=3：0 失敗時 95% 上界 p≤0.632；SK 症狀係**隨機**（「first time bug, second time normal」）⇒ 唔夠分辨真率；`AutoTestHarness` 一次可 20 case（`:43`）卻冇用。另 A3 冇任何「同一物品多次之間**有差異**」嘅 variance predicate。
- **O8 LOW-MED — 行號／命名。** `emissionSectionOf` 應寫 `:1121`；`item=<itemId>` 未寫死 accessor（`RecipeCard.primaryOutputId()` vs `sourceItemId`，v5 O3／v6 O7 同一坑）；`section=` 值域（含 `2`／`-1`／maint）未寫死。

**正方收貨點**：① 儀器方向係唯一可行路線（`grep -c LOGGER RecipeEmbed.java` → **0**；log 係量落位嘅唯一方法）；② 只加 log、默認 false、唔觸碰落位邏輯 ⇒ **還原成本極低**（事實 9/10 支持「唔會新紅」）；③ A1 baseline 數字我親跑對得上（121/1、49 Java、122 閘）；④ `[ui]` 新 key 有先例、settings 閘單向 ⇒ 加 key 唔撞閘；⑤ 「先量度、後修法」分階段正確（避免再 6 輪）。

## ③ 比分 ＋ flip conditions

**正方 3 ： 反方 7**（LD 存活表：LD1 判準／metric **死**、LD2 落位枚舉 **死**、LD3 gating **半**、LD4 零行為＋還原 **存活**、LD5 現有閘唔新紅 **存活**、LD6 驗收鑑別力 **死**、LD7 部署／沙盒 **死** ⇒ 4/7 死）

Flip conditions（下一輪 checklist；全部可即場寫死）

- **Fc1（O1）**：metric 改成 **anchor 化**——每張卡記 `anchor=<step:<blockIdx>|secTail|absEnd|beforeSources>` ＋ `adjCards`（同一 anchor 上連續卡數，**由最終 `blocks` 數**）；`堆埋` predicate 寫死為 `adjCards ≥ 2` 或同 anchor ≥2 張。明文刪走「同一 blockIdx ≥2」定義。
- **Fc2（O2）**：`去咗最尾` predicate 寫死為 `anchor == absEnd` **或**「卡出現喺 【来源】 之後」；明文寫「`atLastBlock`（正文最後一段）**唔算**」；`wantSec<0`（maintenance，`:1131-1133`）明文剔出並另標 `sec=maint`。
- **Fc3（O3）**：mode 逐一對映 5 條 branch（`:907-909 absEnd`／`:919-925 secTail`／`:954-957 needle`／`:960-967 secTail`／`:969-971 clampEnd`）＋ `UNPLACED` 改名 `EMPTY_SKIPPED`（只計 `c.isEmpty()`）＋明文「非-strip 家族（`RecipeEmbed.parts` 9 個 insertion 點）本 plan 唔覆蓋」並寫低觸發條件（`cardsMode != ai`／`!llmExpected()`）。
- **Fc4（O5）**：部署命令寫死為沙盒專用（`--jar <build jar> --mods …/instances/packai_sandbox/minecraft/mods`），明寫「**唔准**用 `--target packai`（該 target = 真 instance）」，並寫死沙盒 jar 名＋覆蓋後 sha 記錄法。
- **Fc5（O6）**：閘二選一寫死——(a) 加 `interleaveEmissionCards(String, int)`（或同形狀）令 harness 能 assert 最終 `blocks` 嘅 anchor／連續卡數；或 (b) 明文承認係「**源碼形狀閘**（唔驗行為）」＋列 3 條負控（改壞 anchor 選擇／刪 log／關 gate 各應紅）。冇 (a) 或 (b) 就唔算閘。
- **Fc6（O7）**：A2/A3 樣本量寫死 n 同理由（≥20 用 `AutoTestHarness MAX_CASES`，或明文「n=3 ⇒ 只可聲稱殘餘率 ≤63%」）；加一條**變異**判準（同一物品多次之間 anchor 分佈有差異 ⇒ 已重現隨機症狀）。
- **Fc7（O8）**：`item=` 寫死 accessor；`section=` 寫死值域（含 `2`／`-1`／`maint`）；`emissionSectionOf` 行號更正為 `:1121`。

## ④ 最貴未知

- **U1（最貴）**：**「去咗最尾」今日到底有冇實例？由邊條 branch 出？**（v6 R1 U-a：7 份真 trace 冇實例。）我抽最新真 trace `ask-20260918-184737-instantblocks_wand_netherite.jsonl` → `render.cards.final cardsOut=0`（冇卡可比）⇒ 現有真 trace 冇一條直接答得到。唯一便宜解：Fc1/Fc2 落地後**跑 1 次**就知（唔使等 20 次）⇒ 次序應係**先做 Fc1–Fc3 再擴樣本**。
- **U2**：新 config key 喺**既有** toml 會唔會自動寫回（Forge correction）＋`[ui]` 是否正確 table（未核實；錯 table ⇒ A2「零輸出」而誤判儀器壞）。
- **U3**：`packai_sandbox` 能否載入新 jar 並真跑 ask（現時係 `packai-autotest-dev.jar`、231 mods；未驗沙盒起機）。
- **U4**：`atLastBlock ≥1` 喺真 corpus 嘅自然出現率（若高 ⇒ A3 必然假綠；可用 Fc2 落地後嘅 log 一次量清）。
