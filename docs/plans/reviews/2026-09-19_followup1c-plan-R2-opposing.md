# Follow-up #1c（卡走位量度儀器）— 反方 review **R2**（v2）

> 審：`docs/plans/2026-09-19-followup1c-card-placement-instrument-plan.md`
> **md5 = 770bb592bca38489ec37cc961615e49f**（64 行；本報告只評此版本）
> R1 ＝ 正方 3 : 反方 7（`docs/plans/reviews/2026-09-19_followup1c-plan-R1-opposing.md`）；v2 §0 逐條回應 O1–O3／O7。
> 只讀唔改（唯一寫入＝本報告）。行號一律指 `forge/1.19.2/src/main/java/com/skps9/packai/`。所有命令親跑。

## ① 驗過嘅事實（真／假／未核實）

**真（親跑／親讀）**

1. **插入點 `:769` 之後技術可行**：`:753 srcAt`／`:754 sourceParts`／`:760 blocks`／`:761 cardCount`／`:762 placed` 全部在作用域；方法簽名 `public static List<Part> interleaveEmissionCards(String, List<RecipeCard> cards) :749` ⇒ **static**，LOGGER 必須 static；`cards` 可為 null（`:761` 自己 null-guard）⇒ log 亦要 null-guard（plan 冇寫）。
2. **INFO 會入 `latest.log`（實證）**：`AskService:469-475 logDisplayBody` 用 `PackAiMod.LOGGER.info(...)`，而 `tests/check_ask_display_leak.py:46 MARKER="Pack AI display body ver="` 正是去 `<instance>/logs/latest.log`（同檔 `:40-45`）搵嗰行 ⇒ INFO 級別落地證實。另：`PackAiMod.java:25 public static final Logger LOGGER = LogUtils.getLogger()`，且 **`logic/` 已在用**（`logic/AskEngine.java:354 PackAiMod.LOGGER.info`）⇒ plan §2.2「該檔現無 LOGGER → 加 1 個」非必需（可以照跟 repo 慣例）。
3. **config 側行號／接點全對**：`.define("jsObtainDiagLog", false)` 在 `:466`（群組 `:463-466`）、accessor 在 `:962`；`b.push("ui")` `:359`／`b.pop()` `:549` ⇒ 新 define 加喺 `:466` 後會落 **`[ui]`**。真 instance toml `:79 [ui]`、`:239 jsObtainDiagLog = false`；**沙盒 toml 同行號**（`:79 [ui]`、`:239`）⇒ A2 手改 toml 可行，但 plan 由頭到尾冇寫死 table 名（應寫 `[ui]`）。
4. **白名單（3 檔、唔改 lang）正確**：`tests/check_settings_registry.py:45 DEFINE_RE` 抽 config 內 define，`:130-141` 檢查方向係 **registry → config**（新 define 唔入 registry 唔會紅），lang 檢查（`:141-146`）只針對 registry 有 entry 者 ⇒ 唔改 lang／唔改 `packai-client.toml` 文檔唔會新紅。
5. **A1 baseline 數字對（親跑）**：`tests/check_*.py` ＝ **122 檔 → PASS=121 / FAIL=1**（唯一紅 `tests/check_ask_display_leak.py`，缺真 `latest.log`）；`find forge/1.19.2/src/test -name '*.java' | wc -l` ＝ **49**。
6. **「連續 CARD run」係正確單位（v2 真修正）**：`AiAssistantScreen:880-910` 每個 CARD part → `ensureChatBlankLine` ＋ `appendRecipeCardCaption` ＋ `ChatLine.recipe(card)` ＋ `ensureChatBlankLine` ⇒ 兩個相鄰 CARD part ＝ UI 上兩張卡直接堆埋（中間冇 TEXT）。R1 O1 由 index 改 sequence run，方向實質正確。
7. **O3／O7 已真修**：mode 枚舉刪咗；`emissionSectionOf` 我核實在 **`:1121`**（`wantSec<0` 分支 `:1131-1133`）。
8. **byRef 多卡「天然成 run」＝設計行為，真 trace 有引文**：`placeEmissionCardsByRef :819-823`（`insertAt = skipCardsAfter(blocks, i+1)` 後 `for (idx : toPlace) blocks.add(insertAt++, …)`）。真 trace `ask-20260917-200509-luna_flesh_reforged_dark_archotech_shard` 第 3 步原文：`3. 动力合成材料：…（动力合成器 26 格）[card:3] [card:4]。` ⇒ 一次插入兩張相鄰卡 ⇒ 最終 run=2，**唔係症狀**。
9. **`cardsAfterSources` / `lastIsCard` 在 plan 選嘅 log 點 ≈ 恆 0／false（幽靈）**：`:769 blocks.addAll(sourceParts)` 係最後一次 mutation（`:770 return`）；`sourceParts` 由 `:754-758` 剝自 `parts` 尾部，而 `:751 parts = parts(raw, List.of())` 永不產生 CARD（`Part.card` 全部產生點 `:366/:587/:594/:1361/:1475/:1529/:1576/:1586/:1628` 均被 `indexOfSources(...)==0` 擋：`:1610`、`:1622`，或把来源推返尾 `:1566-1581`）⇒ **【来源】永遠係 blocks 最尾元素** ⇒ 卡結構上唔可能出現喺来源之後。UI 層同理（`AiAssistantScreen:867 indexBeforeSources`）。
10. **真 trace 模擬（忠實轉錄 `splitTextIntoStepBlocks :848`／`placeEmissionCardsByRef :778`／`disperseUnplacedEmissionCards 五分支 :889-982`，needles 兩種假設）**：`ask-20260915-202603-golden_age_infinity_sword_organ`（4 卡、**emissionRefs=[]**）→ 最終 seq `TTTTCTTTCCCT`，**maxCardRun=3**（另一 needle 假設 `TTTTCTTTTCCC` 同樣 3）；`ask-20260916-133612`（3 卡、0 refs）→ run=1。⇒ **未改任何嘢，普通舊 trace 已經會亮 A3 判準**。
11. **A2 統計對**：n=6 ⇒ 1−0.05^(1/6) = **0.393** ⇒ plan「只可聲稱殘餘率 ≤39%」✓。
12. **A5 可測**：`grep 'cardplace:' latest.log` 即可（由事實 2，gated INFO 必入 latest.log），零成本。
13. **UI 層仲有一次卡插入，儀器完全睇唔到**：`AiAssistantScreen:846 strip = toolPartsStrip(tool)`（`:693-700`：`ModularToolScan.partItemStacks(tool)` 非空即有值）→ `:867-868 int at = cardRef? indexBeforeSources(parts): …; parts.add(at, Part.card(stripIdx))` ⇒ 呢張卡喺 `:769` log **之後**插入，緊貼任何已貼住【来源】嘅卡 ⇒ 可造出儀器睇唔到嘅 run。§1「最終 block 序列」係過度聲稱。
14. **空 TEXT part 可致假陰性**：`:815 blocks.set(i, Part.text(sb.toString()))` 當一個 block 只剩 ref token ⇒ 空 TEXT；`AiAssistantScreen:964-966` 空 chunk `continue`（唔畫）⇒ 卡中間夾空 TEXT 時 UI 仍相鄰但 run 唔計（實際發生率**未核實**）。

**假／未核實**

- **假**：§1 講「最終 block 序列」＝只係 `interleaveEmissionCards` 嘅輸出，唔等於 UI 最終序列（事實 13）。
- **未核實**：新 key 未存在於 toml 時 Forge correction 會唔會自動寫回（A2 靠手改 toml；若 table／鍵名寫錯 ⇒ 零輸出會被誤判成「儀器壞」，plan 冇防呆步驟）。
- **未核實**：A2 嘅木錘／石斧係唔係 modular tool（決定事實 13 喺 A2 會唔會發生）。

## ② 問題（severity）

- **O-R2-1 CRITICAL — 換咗一個新幽靈：「去咗最尾」判準結構上量唔到。** `cardsAfterSources>0`／`lastIsCard` 在 `:769` 之後 ≈ 恆 0／false（事實 9）⇒ A3 個「或」塌成只剩 `maxCardRun≥2`。而 SK 講嘅形態（卡貼住【来源】頂／最後一段之後冇 TEXT）**冇定義**。R1 O2 只係換位復發。
- **O-R2-2 CRITICAL — A3 判準＝base rate，冇鑑別力。** 同步驟多 ref 係 `:819-823` **設計**行為（事實 8 引文）；0-refs 真 trace 亦已有 run=3（事實 10）；no-needle 情況 `:958-967` 直接 `sectionLastAfter`（`:960`）＋`skipCardsAfter :1112-1118` 疊卡 ⇒ 同 section 多卡必然成 run。⇒「6 次入面 ≥1 次亮」幾乎必然亮。更差：§2.2 表寫「`byRef` 空＋`maxCardRun=1`＋`after=0` ＝ 正常」，已被真 trace 反證。
- **O-R2-3 HIGH — A3「變異判準」confounded。** 每次 LLM 文字唔同 ⇒ 同一物品多次之間 `pos` 分佈**必然**有差異 ⇒「已重現隨機」幾乎必然成立，唔係證據。要固定 reply 文本，或用 `AutoTestHarness`（`AutoTestHarness.java:43 MAX_CASES = 20`）。
- **O-R2-4 MED-HIGH — 儀器覆蓋面宣稱過大**（事實 13）：UI 層 `indexBeforeSources` 插入嘅 tool-parts 卡唔喺 log 內；A2 揀 tool 類物品時呢張卡正好會貼住卡群。
- **O-R2-5 MED — 新閘仍係 text-tripwire（R1 O6 未解）。** `tools/card_placement_test.py` 只 mirror `ensureCards`（檔頭明言「AI + llmExpected mode no longer runs ensureCards」），`grep` tests/tools **0 個** strip 路徑 python mirror ⇒ **唔可以**驗 `maxCardRun`／`cardsAfterSources` 行為。A1 三條負控（改 anchor／刪 log／gate 改 true）只證「字串在唔在」，且會因無害改名一齊紅。
- **O-R2-6 MED — `srcAt=<idx>` 欄位撞名陷阱。** `:753 srcAt` 係剝來源**之前**嘅 index，`:754-758` 之後已失效；實作者好易 log 舊值。應寫死 `srcStart = blocks.size() - sourceParts.size()`。
- **O-R2-7 MED — 沙盒／table 冇寫死。** v1 個錯 claim 已刪（好），但 §5.4 只寫「部署沙盒」冇命令；`--target packai` ＝真 instance（唔可以用），沙盒要 `--jar <built> --mods …/packai_sandbox/minecraft/mods`；`[ui]` table 亦未寫死。
- **O-R2-8 LOW —** LOGGER 非必要新增（事實 2）；空 TEXT 假陰性（事實 14，率未核實）。

**正方收貨點**：① 由 index metric 改 **最終序列 run** 係正確單位（事實 6 親核 UI 渲染）；② 刪 mode 枚舉、行號更正（事實 7）；③ 白名單／lang／registry 影響判斷正確（事實 3/4）；④ A1 baseline 數字我親跑對得上（事實 5）；⑤ A2 統計聲明正確（事實 11）、A5 零成本可測（事實 12）；⑥ log 只出 T/C/S 同 index，唔含物品名／secrets，唔會撞 `check_ask_display_leak` 嘅 FORBIDDEN 清單；⑦ log-only、默認 false ⇒ 還原成本極低（存活）。

## ③ 比分 ＋ flip conditions

**正方 4 ： 反方 6**（LD 表：LD1 判準單位→**半存**〔run 修對，但 §2.2 表被真 trace 反證〕／LD2「去最尾」→**死**〔幽靈換幽靈〕／LD3 mode 枚舉→**存**／LD4 零行為＋還原→**存**／LD5 現有閘唔新紅→**存**〔親跑 121/1〕／LD6 驗收鑑別力→**死**〔A3 兩條判準都近乎必然亮〕／LD7 部署沙盒→**半**〔錯 claim 已刪，但未寫死〕）⇒ 未達 8:2 ⇒ **唔建議開工（no-go）**。

Flip conditions（下一輪 checklist；全部可即場寫死）

- **Fc1（O-R2-1）**：刪 `cardsAfterSources`／`lastIsCard` 作為判準（或明文寫「結構上恆 0，只作 sanity」）；改量 **`srcStart = blocks.size() - sourceParts.size()`** ＋ `tailIsCard = blocks.get(srcStart-1).isCard()`（＝卡貼住【来源】頂，呢個先係 SK 講嘅形態），並明文禁 log stale `srcAt`。
- **Fc2（O-R2-2）**：`maxCardRun` 必須加**成因標籤**（`byRef-multi`／`secTail-dump`／`maint-absEnd`／`needle-miss`，逐一對映 `:819-823`／`:960-965`／`:907-908`／`:958-967`），A3 判準改為「出現 run **且** 成因屬非預期路徑」；同時把事實 8/10（真 trace 已 run=2／run=3）寫入 plan 做 base rate 對照，明文「單憑 run≥2 唔算症狀」。
- **Fc3（O-R2-3）**：變異判準要**固定 reply 文本**（同一 reply × n 次）或用 `AutoTestHarness`（`MAX_CASES=20`）；唔可以「每次重新問 LLM」再比 `pos`。
- **Fc4（O-R2-4）**：明文寫「UI `AiAssistantScreen:867-868` tool-parts 卡唔喺儀器範圍」，並寫死 A2 物品唔可以揀 modular tool（或加第二個 log 點覆蓋）。
- **Fc5（O-R2-5）**：明文承認新閘係「**源碼形狀閘（唔驗行為）**」＋逐條寫「改邊個字串 ⇒ 應紅」；或加 `interleaveEmissionCards` 行為閘（Java harness 需 MC bootstrap；若成本太高就照前者寫死）。
- **Fc6（O-R2-7／table）**：寫死 `[ui]` table ＋沙盒部署命令（`--jar`／`--mods`），明文禁 `--target packai`（＝真 instance）；加一步「改完 toml **重讀**確認 key 存在」防呆，否則 A2 零輸出分唔清「儀器關」定「key 寫錯」。
- **Fc7（LOW）**：LOGGER 用 `PackAiMod.LOGGER`（跟 `logic/AskEngine:354` 慣例）；`cards` / `blocks` 加 null-guard。

## ④ 最貴未知

- **U1（最貴）＝冇 ground truth。** 至今冇一個「SK 親眼見到卡堆埋」嘅對照樣本（邊個物品／邊條路徑）。我已證 base rate ≠ 0（0-refs 真 trace 都 run=3；同步驟兩 ref 真 trace run=2），所以**唔知 A3 量到嘅係症狀定 base rate**——任何 n 都答唔到。最便宜解法：攞 SK 原話例子（木錘／石斧）即場重現一次、人手記低畫面，或者先落地 Fc2 嘅成因標籤＋base rate 對照，再擴 n。
- **U2**：A2 嘅木錘／石斧會唔會走 UI tool-parts 卡路徑（事實 13；未核實）——會就直接污染 A2 數據。
- **U3**：新 key 未存在於 toml 時 Forge 會唔會寫回（影響 A2 流程，未核實）。
- **U4**：`maxCardRun≥2` 喺真 corpus 嘅自然出現率（我用 20 條有 ref 嘅 trace 只量到 byRef 部分：1/20 亮；0-refs 嘅 disperse 路徑未全量摸清）——若高，儀器訊噪比低。
