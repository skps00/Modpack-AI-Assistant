# 反方 review R3（最後一輪）— packai「Pack 內容感知 + 來源誠實」全計劃 **v4**

> 被 review：`docs/plans/2026-09-19-pack-content-awareness-full-plan.md`（**v4**；commit **`0201ace`**；實測 `md5sum` 頭 12 ＝ **`3d8f223ea480`**）
> 前一輪：R1 判 **正方 3 : 反方 7**（O1–O18，`…-R1-opposing.md`）；R2 判 **正方 4 : 反方 6**（N1–N8，`…-R2-opposing.md`）
> 立場：**反方（opposing）**。目標＝盡最大努力證明 v4 仍未達 8:2 閘。
> 方法：**只讀**。逐條開真檔核（檔名＋行號＋原文）＋ python stdlib／`zipfile` 唯讀量測 ＋ 親手讀真機 trace／jar。**冇跑 gradle／build**；**冇改任何 repo 檔**（唯一寫入＝本報告）。
> 日期：2026-09-19。

---

## 0. 開場結論（人話）

v4 做咗一件**實質嘅好事**：佢用真機 trace **主動撤回** R1／R2 一直爭論緊嘅舊根因（`extractIngredients` 漏 key），並照 R2 嘅 flip condition 改設計（檔案級 attribution、`both` 態、cache 版本化）。兩條卡死嘅 SK 決定亦**冇藏**——明白寫喺 §8 同 §10。誠實度合格。

但**本輪仍然唔可以開工**，而且今次問題落喺 v4 排最前、自稱「幾行代碼、直接修一個已驗收失敗嘅實錘、成本最低、可即時回報」嘅 **P2**：

1. **P2 嘅新根因，用佢自己引嘅同一個 trace 都證唔到。** 我逐字讀咗 `ask-20260917-091302-sophisticatedbackpacks_netherite_backpack.jsonl`：模型**冇**答「無法確定配方」。佢係**先正確答出鍛造台配方**（`model.reply.final`：`1. 锻造台：用钻石背包＋下界合金锭升级而成…[card:1]`），然後只把「無法再斷言」**限於取得途徑**（`2. 本包索引没有收录它的掉落、宝箱、钓鱼、交易或任务等取得路径…`）。即係 trace 支持嘅係「文字路徑早已正確」，**唔支持**「政策無視手上已有嘅 JEI 配方」。
2. **真正嗰句錯話，冇任何 phase 去修。** 上面第 2 句對 SB 背包係**假**：pack 自己喺 `kubejs/server_scripts/utils/wares_model.js:137` 用 1 個 `lightmanscurrency:coin_diamond` 賣 `sophisticatedbackpacks:netherite_backpack`。而全 Java 樹（`JsObtainSites.java` 全部 pattern；`PackIndex.isTradePath`）**都冇**處理 KubeJS 腳本定義嘅 `SimpleWares` 交易 → 呢條通道係**隱形**。v4 七個 phase（P0–P6）**冇一個**覆蓋佢（P6 只寫「`kubejs/data` 內容解析（loot／advancement／worldgen）」，係 datapack JSON，唔係 `server_scripts/*.js`）。
3. **P2 提出嘅修法方向，會令第 2 點嗰句假話變成「冇提示嘅假話」。** 「acquire 空但有 JEI 配方 ⇒ 唔准答無法確定」＝禁止模型講「我搵唔到取得途徑」。但嗰句唯一嘅價值，正正就係**提示索引唔完整**。禁咗之後，玩家只會睇到「鍛造台升級」而唔會知道索引漏咗交易通道 —— 同本計劃 §0 目標「資料不足時**老實講**」相反。P2 亦**冇負控**（唯一負控擺咗喺 P5）。

比分：**正方 4 : 反方 6**（未達 8:2 閘；本輪＝第 3 輪 → 依 repo 契約**停手問 SK**，見 §6）。

---

## 1. 任務點名三項核實

### 1.1 v4 對 trace 嘅引述 —— **引文準確，但結論推過頭**

v4 §10 原文：

> **決定性證據（2026-09-19 我親自讀 trace）**：`ask-20260917-091302-sophisticatedbackpacks_netherite_backpack.jsonl` 嘅 `send.facts` 內 `jei` 欄位逐字係
> `0 | role=output | 锻造台 | 钻石背包, 下界合金锭 → 下界合金背包`

**我逐字核（python `json.loads` 逐行解析，唯讀）：**

| v4 聲稱 | 我嘅實測 | 判定 |
|---|---|---|
| `send.facts` 內 `jei` 欄位逐字有 `0 \| role=output \| 锻造台 \| 钻石背包, 下界合金锭 → 下界合金背包` | **命中，逐字一致**（`send.facts` → `content` → `jei`） | **✅ 準確** |
| （v4 只引第 0 行） | 實際有 **兩行**：`0 \| role=output \|…` 同 `1 \| role=output \|…`，**兩行內容完全相同**（同一條配方出兩次） | ⚠️ 引文無錯但**唔完整**；「卡重複」本身係現有已知現象，唔算 v4 嘅錯 |
| ⇒ 文字路徑冇漏材料，模型當時**已經收到**正確配方 | ✅ 成立。`send.facts` 嘅 `jei` 區塊（含 `role=output` 卡行）係**每一輪都送**（第 22／24／36／49 行），而 `model.reply.final`（第 51 行）已寫出正確材料 | **✅ 成立** |
| ⇒ 所以 v4 撤回舊 P2 根因（「`extractIngredients` 漏 `base/addition/template` → SB 文字 miss」） | ✅ 撤回正確（R2 N3 講對） | **✅ 成立** |
| ⇒ **真兇係另一層：acquire（掉落／任務／腳本）空 ⇒ 政策就寫「無法確定」，無視手上已有嘅 JEI 配方** | ⚠️ **半對**：確實有「acquire 空 ⇒ 叫模型講 unknown」嘅代碼路徑（見 §2.3），但 **trace 顯示模型冇「無視配方」，亦冇對配方講無法確定**；佢只係對**取得途徑**講無法斷言 | **❌ 結論推過頭（本輪新問題 N-R3-1）** |

**額外核實（v4 冇提、但同一 trace 檔內有）：** 同一檔第 21／33／46 行嘅歷史 turn，係一件 **`tetra:modular_sword`（擬態）** 嘅答案，佢寫「本包索引没有收录它的掉落表／钓鱼／交易／任务／脚本取得路径，**所以确切取得方式无法断言**」＋「【来源】… JEI（**无配方显示**）」。而 plan 自己 §2 P0 嘅驗收正正係「2 問（木錘／**擬態**）」→ 即係「文字 miss」嘅原始投訴**可能係擬態而唔係 SB 背包**。呢點我**未能證實**（需要 SK 當時原句），但佢直接影響 P2 方向係唔係修對層（見 §6 最貴未知 1）。

### 1.2 code 有冇一條「acquire 空 ⇒ 無法確定」嘅路徑？——**有，兩條**

| # | 檔名：行號 | 原文（節錄） | 作用 |
|---|---|---|---|
| A | `logic/AcquireAskTool.java:27-31` | `public String toolMissNote(String item) { … return "[TOOL_MISS] acquire empty — pack index has no loot/trade/quest/script path for '" + id + "'. Say unknown/obtain unknown; do not invent."; }` | acquire tool 回空 → 直接教模型「講 unknown」 |
| B | `logic/HonestMiss.java:22-38` | `shouldPinAcquireMiss(...)`：`if (acquire != null && !acquire.isEmpty()) return false; if (hasRecipeGet) return false; return isAcquireOrientedQuestion(question);` | acquire 空 **且無配方** 且問法係取得導向 → pin 一行固定 miss FACT（`logic/ReplyLang.java:1169 acquireIndexMiss`；`lang/zh_tw.json:476`：`"packai.reply.acquire_index_miss": "未索引：本包事實無此物的戰利品／閘道／任務／腳本取得路徑。請明說未知 — 禁止捏造掉落、stage 或成就 id 列表。"`） |

**回空注入機制**（trace 有鐵證）：`logic/AskToolLoop.java:487-489`

```java
String body = out == null || out.isBlank()
        ? LlmClient.toolMissNote(call.name(), resolvedItem, state.hadNonEmptyJeiDump())
        : out;
```
（`LlmClient.java:614-625` 分派到上面 A。）

**trace 實證：** 我把 `send.history` 逐 turn 印出，第 1 輪工具回覆**逐字**係
`[TOOL_MISS] acquire empty — pack index has no loot/trade/quest/script path for 'sophisticatedbackpacks:netherite_backpack'. Say unknown/obtain unknown; do not invent.`
（同輪 `jei_lookup` 另有 soft miss：`[TOOL_MISS] jei_lookup(INFO) empty — …唔准講「查唔到」…`）

**⇒ 判定：路徑確實存在（A＋B），v4 呢半句有 code 支撐**；但 v4 §10 **完全冇引任何檔名／行號**（唯一證據＝trace），違反 plan 自己 §1／§9 嘅舉證風格。

**修它會影響哪類題目（風險）—— 我嘅分析：**

1. **受影響面 = 所有「acquire tool 回空」嘅問答**（唔限有配方）。因為 A 係 tool 層，任何 `acquire(item=…)` 空結果都會注入「Say unknown」。
2. **「唔准答無法確定」會消滅唯一嘅不完整索引信號。** SB 個案：索引真係唔完整（黑市交易冇入），所以「無法再斷言別的來源」實質上係**提示系統有洞**。禁咗之後模型只會正面講「鍛造台升級」，玩家**唔會**知道索引漏通道 → 由「有保留嘅錯」變成「自信嘅錯」。
3. **會製造新嘅「捏造」壓力。** 禁答 unknown 但冇負控（P5 嘅負控「假 id 唔准編」唔喺 P2），模型面對空 acquire 時只剩兩條路：唔講（資訊缺失）或編（更差）。
4. **影響題目類型（具體）**：任何「取得途徑只存在於 pack 腳本／交易」嘅物品 —— 例如 `wares_model.js` 內全部 `SpecialWares`／`EggWares`（`tetra:forged_workbench`、`tetra:chthonic_extractor`、`dimdungeons:item_blank_theme_key`、各 spawn egg…），以及靠 KubeJS `give`／`addLoot` 之外嘅自定義交易取得嘅物品。
5. **同時會打紅現有測試嘅意圖**（未實作前唔會紅，但設計相反）：`src/test/java/com/skps9/packai/logic/HonestMissCheck.java:10-17` 明文 assert「acquire 空＋無配方＋取得問句 ⇒ 要 pin miss」；`FrameStandardRecipeLineCheck.java:250/274`、`ModularFrameStandardCheck.java:179-184` 都 assert `askMissAcquirePlayer` 嘅可見性。P2 一旦改成「唔准」，呢批 assert 同 `AskReplyScrub` 嘅 miss 處置要一齊重新設計 —— **v4 冇提**。

### 1.3 v4 有冇誠實標示兩個卡死嘅 SK 決定？——**有，冇藏**

| 卡死決定 | v4 位置 | v4 原文 | 判定 |
|---|---|---|---|
| baseline SHA | §8 第 1 項（`:62`）＋§10「仍然卡住」第 1 項（`:89`） | 「33 個 `tests/` 檔＋工作樹 67 檔未 commit ⇒「零新增紅」今日無法客觀比較。要你批准**一次性 baseline commit**（或開 git worktree 快照）…（唔批准就只能用「相對自己跑」嘅軟基準，我會明文標示。）」 | **✅ 誠實**（並且明文寫咗唔批准時嘅後備講法） |
| pack-added predicate | §8 第 2 項（`:63`）＋§10「仍然卡住」第 2 項（`:90`） | 「① 凡喺 `kubejs/` 樹下＝本包（最簡單）② 逐檔比對（最準、最貴）③ 兩者都有就答「本包另有覆蓋」＋標 `unknown`（我建議 ③）」 | **✅ 誠實**（連建議都寫明係建議） |

**反方特別聲明（照規則，對正方有利嘅要照講）：** 呢兩條**冇**被假裝解決。R2 判 N8 為「未解決（推遲）」嘅主因係「清單未存在／baseline 無 SHA」，v4 今次**明確升級**成 SK 決定並寫入 plan —— 呢點係**實質修改**，唔係換 prompt 擲骰。**但仍然未解**（我實測 `git status --short` 116 行、`git diff --stat` 尾行仍係 `67 files changed, 4448 insertions(+), 1287 deletions(-)`，同 R1／R2 一模一樣 → O5 亦未動）。

---

## 2. 逐條判定 N1–N8

判定三級：**【真解決】**＝異議已解除（我已核）／**【表面解決】**＝方向寫咗但未 pin（冇檔名行號／冇驗收步驟／自相矛盾）／**【未解決】**＝只係聲明或推遲。

> **先講最重要嘅方法學事實**：v4 相對 v3 嘅 diff 我實跑（`git diff b157966 HEAD -- …full-plan.md`）＝**只有三處**：① §2 P2 一列改寫；② §8 由 4 項改成 5 項；③ 末尾新增「## 10」。**§1／§4／§5／§6 一個字都冇改**（`2 files changed, 265 insertions(+), 5 deletions(-)`，另一檔係 R2 報告）。所以任何「§10 講咗，但 §1／§4 冇跟」嘅落差，都係**同一份 plan 內兩段打架**，唔係我讀漏。

| # | R2 異議 | v4 點改（原文） | R3 判定 | 關鍵證據（本輪親手核） |
|---|---|---|---|---|
| N1 HIGH | P2 修完唔生效：jar-cache 冇解析器版本、`type` 被 24 字截斷、冇 python cross-check | §10 `:79`：「① jar-cache fingerprint **加 parser 版本**（升版即失效重建）；② 唔再截斷 `type`（或另存完整 type）；③ 新閘要有 python 鏡像同行為斷言」 | **表面解決** | 三個缺陷**全部仍然存在**：<br>• cache 無效化：`logic/JarLightIndex.java:314-327`（`if (fp.equals(prev.get("fp")… ) && prev.has("shard")) return false;`）；版本只寫唔讀 —— `:307 manifest.addProperty("v", 1);` 全檔唯一一次，**冇任何讀取**。<br>• 截斷：`:490-497 shortType()` → `if (s.length() > 24) s.substring(0, 24)`；呼叫點 `:206`。`sophisticatedbackpacks:smithing_backpack_upgrade` 25 字 → 出 `smithing_backpack_upgrad`。<br>• 鏡像假綠：我 `grep -c "\.java" tests/check_jar_light_index.py` ＝ **0**（冇 cross-check）。<br>**v4 未 pin**：parser 版本放邊（fp 輸入？manifest 欄位？）冇寫；§5 `:53` 只要求「**phase 內新增嘅** python 閘要有本機鏡像」→ **既有鏡像 `check_jar_light_index.py` 冇被指名要同步**；②留「或另存完整 type」＝**未揀**（either/or 唔係設計）；亦冇 R2 flip condition (b) 要求嘅「喺**已有 cache** 嘅機器上、唔洗手動清 cache 都測到生效」驗收步驟。 |
| N2 HIGH | 二元 ns 模型會把 pack 自加講成原廠；截斷會反向誤判（無聲） | §10 `:80`：「P1 改成 **檔案級**（記錄 `kubejs/data\|assets/<ns>/<path>` 實際路徑）；判定：只 jar＝原廠、只 kubejs＝pack、**兩者都有＝`both`（答「原廠有、本包另有覆蓋」或 `unknown`）**，唔准二選一」 | **表面解決＋新矛盾** | 方向＝R2 flip condition (a)+(b) 全採納，值得記分。**但**：<br>① **§2 同 §10 打架**：§2 roadmap P1 列（`:30`）**冇改**，仍寫「**命名空間**來源索引（擴充現有 `PackIndex`／mechanic-cache，唔另起爐灶）」。即係同一份 plan，規範表講 ns 級、回應表講檔案級。（diff 證實 P1 列零改動。）<br>② 冇列出 27 個 `both` ns（我實測：**kubejs-only 28／overlap 27**，同 v4 §8 一致；樣本含 `tetra`、`create`、`minecraft`、`wares`…）。<br>③ **`both` 嘅「答法」冇落地**：`答「原廠有、本包另有覆蓋」` 係**玩家可見文字** ⇒ 要 lang key ⇒ 撞 §2 P4 列**未改**嘅「**零新 lang key**」＋ O1 嘅 parity／「唔碰 neoforge」兩難（R2 §2.3 已證：`displaySrc` 零 UI consumer，只有 `AskService.java:406/448/510` 嘅 log／trace）。<br>④ 截斷方向未 pin：N7 只寫「唔准部分結果當完整」，**冇寫**「partial ⇒ 唔准答『本包自加』（只可答未知）」（R2 明寫要求）。 |
| N3 MED-HIGH | P2 根因缺 runtime artifact 證據 | §10 `:81`：「已用真 trace 推翻原假設（見上）；P2 先寫可重現 harness case」 | **真解決（就異議本身）＋衍生新問題** | **異議本身解除**：我核實引文逐字準確（§1.1）＋v4 主動撤回舊因果 ✅。**順帶更正 R2 一個算術**：R2 判 v3 嘅「96 條 smithing」為「✗ 對唔上（實測 95）」。我實測：`type` 含 `smithing` 嘅 recipe ＝ **96**（`minecraft:smithing` 95 ＋ `sophisticatedbackpacks:smithing_backpack_upgrade` **1**）；而 SB 自己條 recipe 正正係嗰 1 條（`sophisticatedbackpacks-1.19.2-3.20.2.1035.jar → data/sophisticatedbackpacks/recipes/netherite_backpack.json`，keys＝`['type','addition','base','result']`、`addition={'item':'minecraft:netherite_ingot'}`）。⇒ v4 §1 嘅「96」**在明示 predicate 下站得住**，R2 嘅「錯數字」指控**predicate-dependent、過強**。（我用同一標準撤回自己人一條。）<br>**但衍生新問題 N-R3-1**：新根因（「政策無視已有配方」）用**同一個 trace 證唔到** —— trace 顯示配方已正確送達且已正確作答，`無法確定` 只出現喺**取得途徑**語境。 |
| N4 MED | 更大缺口係 output key 唔係材料 key | §10 `:82`：「P2 次要修擴到『材料 key ＋ output key 都要對』；先量測覆蓋率再決定」 | **表面解決** | ① R2 指嘅**問題陳述不對稱**冇修：**§1 diff 零改動**，`:18` 仍寫「`extractIngredients`（`:522-534`）只收 `ingredient/ingredients/key/input/inputs` ⇒ smithing 類（jar 內 96 條）只有 result 冇材料＝SB 文字 miss 真因」——**因果已被 v4 自己撤回，但 §1 原文冇刪**（同一份 plan 自相矛盾：§1 講「真因」，§10 講「唔成立」）。R2 flip condition 要求嘅「已知未覆蓋清單（附條數）」亦冇加。<br>② 「先量測覆蓋率再決定」＝**deferral 冇閘值**（覆蓋率幾多才做？誰量？）<br>③ 常量仍在（`JarLightIndex.java:55-58`：`MAX_RECIPES_PER_JAR`／`MAX_LOOT_PER_JAR`／`MAX_FACTS_PER_ITEM=8`／`MAX_INGS=6`）。 |
| N5 MED | P4 無顯示路徑（玩家睇唔到） | §10 `:83`：「P4 明確二選一：(a) 指定 UI consumer（卡片／答案尾註）並寫明要唔要 neoforge lang；(b) 只入 trace／log 並**明文寫「玩家睇唔到」**」 | **表面解決** | 把「二選一」寫成「要二選一」＝**仍然未揀**。而 §2 P4 列（`:33`）**冇改**，仍寫「來源標籤（`AskResult.provenance` 欄位；**零新 lang key**）」→ 規範表仍然係「玩家睇唔到」嗰支。我重核 UI consumer：`grep -rn displaySrc forge/.../client/` ＝ 只有 `AskService.java:406`（`logDisplayBody`）、`:448`（同）、`:510`（trace `src`）；**冇 GUI** ⇒ (a) 支要新開消費者，成本未估。 |
| N6 LOW-MED | `collectItems` 40 件上限、插入次序未 pin | §10 `:84`：「加 harness 斷言：同一輸入 → 同一輸出集合與次序」 | **表面解決（而且斷言揀錯咗）** | **提出嘅斷言同風險正交**：R2 講嘅風險係「新 key 若**前置**，會擠走原本頭 6 位材料 → shaped 配方材料**變少**（回歸）」。而「同一輸入→同一輸出（決定性）」**改前改後都成立**（決定性係本來就有），**驗唔到回歸**。R2 flip condition 三件（append 落尾／同步鏡像／負控「改前改後同一條 shaped 配方材料清單唔准變短」）**一件都冇寫**。常量：`:537`／`:550` `out.size() >= 40`；`:215` `subList(0, min(size, MAX_INGS))`；`:58 MAX_INGS=6`。 |
| N7 LOW | §4 未定義超限／超時行為 | §10 `:85`：「明文：截斷即停該來源＋log `contentIndex: truncated=<source>`；唔准部分結果當完整」 | **表面解決** | ① **規範位置冇改**：§4 config 表（`:43-50`）**零改動**，仍然冇任何 key／旗標描述超限行為 → §10 講嘅規則**唔係規範文本**（`grep -n "truncated" plan` 只命中 `:85` 一行）。<br>② 缺 R2 要求嘅**方向 pin**：「唔准部分結果當完整」冇講降級去邊（**必須明文「partial ⇒ 只可答未知，唔准答本包自加」**，因為錯方向＝主動講假話，唔止 coverage 少）。 |
| N8 LOW | P0 清單未存在；`tests/check_*.py` baseline 冇 SHA | §10 `:86`：「P0 產出清單檔（路徑寫入 plan）；baseline 要求：**有 commit SHA 或 worktree 快照**（需 SK 決定，見 §8）」 | **表面解決** | ① **「路徑寫入 plan」未有**：我 `grep -n "清單\|歸屬" plan` 只命中 `:29`（「逐檔 md5 清單交 SK」）、`:56`（「P0 前置清單要 SK 確認」）、`:65`、`:101` —— **冇任何檔名／路徑**。<br>② baseline 部分**誠實推遲**（見 §1.3，記分）。<br>③ 我重核：`git status --short`＝116 行、`git diff --stat`＝`67 files changed, 4448 insertions(+), 1287 deletions(-)` ⇒ **同 R1／R2 一模一樣，工作樹零變**。 |

**小結：N1–N8 → 真解決 1（N3）、表面解決 7（N1／N2／N4／N5／N6／N7／N8）、未解決 0。**
（對比 R1→v3：真 9／表面 7／未 2；本輪係**全部 8 條都「有答但未落地」**——呢個形態本身係最危險嘅一種：唔似 v3 有硬錯，但每一條都停喺「方向正確、pin 唔到」。）

---

## 3. v4 新引入／延續嘅問題（severity ＋ 攻擊 ＋ 證據 ＋ flip condition）

### N-R3-1 【HIGH】**P2 嘅新根因未證；修法同「來源誠實」目標相反；真兇渠道冇 phase 覆蓋**

**（a）新根因未證（見 §1.1 表末行）。** v4 講「政策就寫『無法確定』，**無視手上已有嘅 JEI 配方**」——但 trace 顯示模型**冇**無視：`model.reply.final` 第 1 步就係鍛造台配方＋`[card:1]`，`check.jei` 亦已 dump OUTPUT（`hasCards:true`）。所謂「無法確定」只出現喺「除上面嘅鍛造外無法再斷言別的來源」＝**取得途徑語境**。⇒ v4 指控嘅症狀（政策無視配方）**冇 runtime artifact**，即係換咗一個同樣未證嘅根因。

**（b）真兇喺另一層，而且冇 phase 修。** 上面嗰句對 SB 係事實錯誤：pack 有黑市交易。

```
kubejs/server_scripts/utils/wares_model.js:137
    new SimpleWares([Item.of('lightmanscurrency:coin_diamond').withCount(1)], [Item.of('sophisticatedbackpacks:netherite_backpack', '{inventorySlots:180, upgradeSlots:5}')], 2),
```
而三層 parser 全部唔會認到佢：
- `logic/JsObtainSites.java`（KubeJS obtain-site 解析器）嘅 sink pattern 只有 `GIVE`（`.give|giveInHand|addItem|insertItem|addToInventory`，`:38-41`）、`LOOT`（`addLoot|LootEntry|dropItem|spawnItem`，`:42-45`）、`SETSLOT`（`:46-49`）。我 `grep -in "wares\|trade\|shop\|coin"` **該檔 0 命中**；`new SimpleWares([Item.of(...)],[Item.of(...)],2)` 亦唔匹配任何一條。
- `logic/PackIndex.java:2690-2695 isTradePath()` 要求路徑含 `villager`／`/trade`／`trades`／`wandering_trader` —— `…/utils/wares_model.js` **一個都冇**（`wares` ≠ `trade`）。
- 全 repo `grep -rln "wares_model\|SimpleWares" tests/ forge/1.19.2/src/` ＝ **0**（冇 fixture、冇測試）。
⇒ 即係「pack 自加取得通道」最典型嘅一種（腳本交易）**完全隱形**，而 v4 §2 roadmap P6 只寫「`kubejs/data` 內容解析（loot／advancement／worldgen）」＝datapack JSON，**唔覆蓋 `server_scripts/*.js` 交易**。P2 嘅唯一驗收「1 問（SB 背包）」**大概率先照樣 miss**，然後被誤讀成「政策修唔 work」。

**（c）修法反效果。** 見 §1.2 風險 1–4：禁答 unknown ⇒ 由「有保留嘅錯」變「自信嘅錯」；而且 P2 冇負控。

**Flip condition**：
1. 提供**第二條真 trace**（或新跑一次），證明「acquire 空 ＋ `send.facts.jei` 已有 `role=output` 卡」之下，模型真係答「無法確定配方／冇配方／JEI 冇列」——即係 R2 N3 講嘅 miss 真身；**或**撤回「政策無視配方」呢句因果，改成「acquire 空 ⇒ 模型把**索引缺失**講成**事實缺失**」（呢個講法 trace 支持）。
2. P2 明寫：新政策**只改措辭唔准消滅不完整信號**——例如「acquire 空 ⇒ 必須寫『本包索引未收錄此取得途徑』＋**唔准**進一步斷言『只能靠 X 取得』」，並附**負控**（一個索引真係冇通道嘅物品：唔准升級成肯定句）。
3. 把「KubeJS 腳本交易通道」（`SimpleWares` 類）寫入 roadmap 某 phase（P1 或 P6），或明文列為已知未覆蓋＋影響物品清單；否則 SB 驗收同「pack 內容感知」目標唔一致。

### N-R3-2 【MED】**§2 同 §10 打架（P1 級別；P4 未定案）** —— 見 §2 表 N2①、N5

規範表（§2）冇跟 §10 改，導致「邊句係 binding」唔清：
- §2 P1 ＝「**命名空間**來源索引」 vs §10 ＝「P1 改成**檔案級**」。
- §2 P4 ＝「`AskResult.provenance` 欄位；**零新 lang key**」 vs §10 ＝「二選一（a）指定 UI consumer＋要唔要 neoforge lang」。
**風險**：phase plan 作者（cursor-agent）照 §2 實作 → 出嚟嘅嘢同 §10 嘅承諾唔同 → R1／R2 打過嘅洞（二元 ns、玩家睇唔到）**原封不動回歸**。
**Flip condition**：v4 改到 §2 表同 §10 逐條一致（或明文寫「§10 覆寫 §2 相應列」並更新 §2）。

### N-R3-3 【MED】**新根因衍生嘅「兩個真因並存」** —— §1 未同步

§1 `:18` 仍然用肯定句寫舊根因（「…＝**SB 文字 miss 真因**」），而 §10 `:73` 寫「**唔成立**」。同一份 plan 兩句相反（diff 證 §1 零改動）。另外 §1 `:22` 嘅「**222 個** `golden_age:` id」我重測**唔到**（predicate：`.create('golden_age:X'` unique ＝ **109**；引號內 `golden_age:*` unique ＝ **406**；全部裸字面 unique ＝ **407**）⇒ R2 呢條**成立**，v4 未改。
**Flip condition**：§1 加「（已由 §10 撤回）」註記＋把 222 改成可重跑 predicate 嘅數字。

### N-R3-4 【MED】**新功能嘅驗收面冇覆蓋新增風險**

§5 `:53` 只要求「compile／harness 全綠（新增 ≥1）／`tests/check_*.py` 零新增紅／parity 綠／兩樹 lang key 數一致／新增 CJK＝0／每 phase ≤2 問」。但：
- N1 嘅修（cache 版本、type 不截斷）**冇對應驗收**（冇「同一 jar，改前改後 fact 唔同」嘅斷言）。
- N2 嘅 `both` 態**冇驗收**（冇「27 個 overlap ns 逐個輸出來源」嘅斷言；R2 flip (c) 要求示範 P3 六層逐層來源）。
- §5 嘅「**兩棵樹 lang key 數一致**」係**自設**約束（我 grep `tests/*.py` 冇任何閘強制，同 R2 一致）——佢係 P4「玩家睇唔到」兩難嘅來源，v4 冇處理。
**Flip condition**：每 phase 嘅驗收加一條「針對該異議」嘅行為斷言（cach invalidate／`both` 輸出／truncation 降級），或撤回自設嘅 lang key 數一致約束（改用 forge-only 新 key）。

### N-R3-5 【LOW-MED】**文件自我識別錯亂（可致下游 agent 做錯）**

- 標題 `:1` 仍寫「**v3（roadmap ＋ 分拆式實作閘）**」；`:10` §1 標題仍「稽核（**v3** 修正數字）」。
- Section 次序 **8 → 10 → 9**（`:61`→`:68`→`:93`）；`:93` 標題仍「R1 十八條 → **v3** 逐條回應」。
- §8（`:62-63`）同 §10「仍然卡住」（`:89-90`）**重複**列同一批卡死決定（兩份文本要同步維護）。
**風險**：plan 係派給 cursor-agent 嘅契約；版本號／章節亂會令實作者引用舊段（尤其 §2 vs §10 已打架，見 N-R3-2）。
**Flip condition**：標題改 v4、章節排序修正、卡死決定單一來源（§8 指去 §10 或反之）。

---

## 4. 載重決定存活表（比分由表計出）

| LD | 載重決定 | R2 | R3 判定 | 依據 |
|---|---|---|---|---|
| LD1 | 交付物拆分：roadmap＋每 phase 獨立 plan／review／部署 | 存活 | **存活** | §2 標題／§7；R1 O7 已真解決 |
| LD2 | P2 ＝「便宜、即時回報」嘅第一個 phase | **死** | **仍然死（換咗死法）** | R2：cache／type／鏡像。R3：根因未證（N-R3-1a）＋真兇渠道冇 phase（N-R3-1b）＋修法反效果（N-R3-1c） |
| LD3 | ns→provider 索引足以分出「pack 自加 vs 原廠」 | 死 | **活一半→仍半死** | §10 已改檔案級＋`both`（方向對，記分），但 §2 P1 未改（矛盾）＋`both` 答法冇顯示路徑＋未列 27 ns ⇒ 未可實作 |
| LD4 | P4 用 `AskResult.provenance` 繞過 parity 閘（零新 lang key） | 死（機制活、交付物死） | **半死** | 認可「玩家睇唔到」係一支選項（誠實度↑），但**未揀**＋§2 P4 列仍寫死「零新 lang key」 |
| LD5 | §1／§4 數字同上限「全部親手重測」 | 半死 | **半死（略改善）** | 改善：`96` 我 repro 到（predicate 明示下成立）、28／27／3,390 我全部 repro ✅、`timeBudgetMs=2000` R2 已撤回攻擊。未改：`222`（repro 唔到）、`maxMb=4` 無依據、`packContentLang` 4000／8MB 仍抄常數、**§1 因果句未撤** |
| LD6 | 驗收設計：每 phase ≤2 問＋零新增紅＋每新閘有 python 鏡像 | 半死 | **半死** | 拆問 ✅；但 baseline 仍無 SHA（O5／N8）、既有鏡像冇 cross-check（N1）、truncation／`both`／cache 冇對應斷言（N-R3-4） |
| LD7（新） | P2 修法＝「acquire 空＋有配方時唔准答無法確定」 | — | **死** | N-R3-1c：消滅不完整索引信號＝同「來源誠實」目標相反；且無負控 |

**載重決定 7 條：存活 1（LD1）、死 3（LD2／LD7＋LD3 半）、半死 3（LD3／LD4／LD5／LD6）。**

### 反方估計比分：**正方 4 : 反方 6**（未達 8:2 閘）

**正方攞到嘅（照規則照講）：**
- **主動撤回錯根因**（新證據）＝真嘅實質修改，唔係換 prompt 擲骰。R2 嘅 N3 由「缺證據」變成「證據到位」。
- **採納 R2 flip condition 嘅設計**：檔案級 attribution、`both` 第三態、cache parser 版本、truncation log 字串 —— 方向全部對。
- **誠實標示兩條卡死決定**，連「唔批准就用軟基準」同「建議 ③」都寫明（§1.3）。
- **我主動更正自己人一條**：R2 指 v3「96 條 smithing 錯」，我實測 **96 成立**（95 `minecraft:smithing` ＋ 1 `sophisticatedbackpacks:smithing_backpack_upgrade`）→ v4 §1 呢個數**過關**。同時我 repro 到 v4 §8 嘅 `tetra` **3,390 檔**（`kubejs/{data,assets}/tetra` 合計）✅、`28 kubejs-only／27 overlap` ✅。
- **撤回一條預備攻擊**：N7 嘅「log `contentIndex: truncated=<source>`」格式我認同（只係位置錯，見 N7 判定①），唔另加罪。

**反方攞到嘅：**
- **8 條 N 異議：真解決 1、表面解決 7** —— 每一條都停喺「方向寫咗但 pin 唔到」，而 v4 相對 v3 只改咗 P2 一列、§8、＋§10（diff 實證），§1／§4／§5／§6 零改。
- **新 HIGH**（N-R3-1）：v4 排最前嘅 phase，新根因用自己引嘅 trace 證唔到；真實缺陷（腳本交易通道）**七個 phase 冇一個覆蓋**；新政策方向同 §0「老實講」目標相反。
- **新矛盾**（N-R3-2／3）：§2 與 §10 打架（P1 級別、P4 未定案）；§1 舊因果未撤、`222` 未改。
- **兩個 HIGH（N1／N2）未實作**：cache 版本／type 截斷／鏡像 cross-check 三件仍在；`both` 冇顯示路徑。

**未達 8:2 嘅原因（一句）：** v4 令「方向」幾乎全部正確，但**規範文本互相打架、無一項 pin 到可實作／可驗收**，而 P2（唯一想即刻開工嘅 phase）嘅新方向係**修錯層**。要過閘，最低限度要：(i) P2 攞到真 trace 支持嘅因果（或改成「索引缺失 ≠ 事實缺失」講法）＋負控；(ii) §2／§10 統一（P1 檔案級、P4 揀定）；(iii) baseline SHA（SK 一句）。

---

## 5. 停手報告（依 repo 契約：plan review 上限 3–4 輪，第 3 輪未達 8:2 → 停手問 SK）

> 依 `AGENTS.md`「Plan／Idea Review 上限 3–4 輪」：**到第 3 輪仍未達 8:2 → 立即停手，唔准再開新一輪，直接問 SK**。以下四項齊備。

**① 逐輪比分**
- R1（v1）：**正方 3 : 反方 7**（18 條 O1–O18；CRITICAL：新 label 必破 parity 閘＋「唔碰 neoforge」死鎖）
- R2（v3）：**正方 4 : 反方 6**（8 條 N1–N8；HIGH：P2 修完唔生效／二元 ns 模型講錯話）
- R3（v4）：**正方 4 : 反方 6**（本輪；8 條 N 全部「表面解決」，新增 1 條 HIGH：P2 修錯層、真兇渠道冇 phase）

**② 卡死嘅載重決定（邊幾條、點解）**
1. **P2 修邊層**（LD2／LD7）：要「acquire 空」時嘅正確行為，前提係**知 SK 當時投訴邊一句**；現有 trace 反而顯示配方早已正確、錯嘅係「取得途徑」一句，而嗰句錯係因為**索引漏咗 pack 腳本交易通道**（`wares_model.js:137`）。呢個決定唔可以靠更多 review 收口。
2. **P1 粒度＋`both` 顯示路徑**（LD3／LD4）：v4 已改檔案級＋`both`，但「`both` 點同玩家講」同「P4 要唔要 neoforge lang」係**同一條決定**，而 §2 同 §10 各寫一半。
3. **`§5`「零新增紅」嘅 baseline**（LD6）：33 個 `tests/` 檔＋工作樹 67 檔未 commit（我本輪重測：`67 files changed, +4448/−1287`，同 R1 一致）⇒ 唔可重跑。

**③ 最貴嘅未知（要咩數據才解得開）**
1. **SK 當時「SB 背包文字 miss」嘅原句／截圖**（成本：一句；影響：P2 整個 phase 係唔係修對層）。注意：同一個 trace 檔內仲有一件 **`tetra:modular_sword`（擬態）** 嘅答案寫「**無法斷言**」＋「JEI（**无配方显示**）」，而 plan §2 P0 驗收正正包括「擬態」——**投訴對象可能係擬態而唔係 SB 背包**（我未能證實）。
2. **一次性 baseline commit 或 worktree 快照嘅 SHA**（成本：SK 一句批准；影響：所有 phase 嘅「零新增紅」由不可驗證變成可驗證）。
3. **「pack 自加」判定 predicate 粒度**（成本：SK 一句；影響：P1 檔案級實作、`both` 答法、P3 六層歸屬、P4 標籤文字）。

**④ 我嘅建議（收窄／拆細／換方案）**
- **收窄 P2**：唔好改「唔准答無法確定」（消滅信號）；改成「**索引缺失 ≠ 事實缺失**」＋必須寫「本包索引未收錄」＋負控。先寫可由 trace 重現嘅 harness case（v4 已提，值得做）。
- **拆細**：P2（政策措辭＋負控）同「腳本交易通道索引」（`SimpleWares` 類）**分開**；後者唔係細改，應另開 phase plan（P1.5 或 P6 擴充），並附影響物品清單。
- **換方案（P4）**：建議**撤回** §5 自設嘅「兩棵樹 lang key 數一致」（冇閘強制），改用 forge-only 新 lang key → P4 即刻有玩家可見交付物，唔需要動 neoforge。
- **可以即刻做、零 SK 依賴**：§2／§10 統一（N-R3-2）、§1 加撤回註記＋改 `222`（N-R3-3）、標題／章節修正（N-R3-5）。

---

## 6. 方法限制與本輪跑過嘅命令（誠實聲明）

**做咗（全部唯讀）**
- 讀真機 trace：`ask-20260917-091302-sophisticatedbackpacks_netherite_backpack.jsonl` —— 用 python `json.loads` **逐行解析全部 56 行**（唔係肉眼），統計 event 種類、抽出 `send.facts.content.jei` 全文、抽出最後一輪 `send.history` 內**所有 tool turn 原文**（含兩條 `[TOOL_MISS]`）。
- 讀 code：`logic/AcquireAskTool.java`（全）、`logic/HonestMiss.java`（全）、`logic/AskGrounding.java`（全）、`logic/AskMissFallback.java`（全）、`logic/JsObtainSites.java`（pattern 區）、`logic/JarLightIndex.java:290-350`、`logic/AskEngine.java:300-369`／`1180-1256`、`logic/LlmClient.java:600-626`、`logic/AskToolLoop.java:470-520`、`logic/PackIndex.java:1755-1814`／`2670-2714`、`tests/check_dual_tree_sync` 相關；grep：`server_scripts|startup_scripts|client_scripts`、`wares|SimpleWares`、`displaySrc`、`packai.reply.acquire_index_miss`。
- 量測（python stdlib／`zipfile`，唯讀；scratch 只落 `%LOCALAPPDATA%\Temp`，實際全程 heredoc 無落檔）：231 jar／**179,114** entries／列舉 **0.46 s**；jar ns 227、kubejs ns 55、**kubejs-only 28、overlap 27**（謂詞＝`data|assets/<seg>` 首段，與 R2／v4 嘅 28／27 一致；我嘅 jar ns 227 vs R2 224＝我無濾「p[1] 是否目錄」）；`kubejs/data/tetra` ＝ **2,420** 檔、`kubejs/{data,assets}/tetra` ＝ **3,390** 檔（＝v4 §8 嘅數 ✅）；`type` 含 smithing 嘅 recipe ＝ **96**（95＋1）＋逐條 key；SB jar 條 recipe keys＝`['type','addition','base','result']`；`dlc_template_item_register.js` 三個 predicate（109／406／407，**冇 222**）。
- git：`git log --oneline -3`（HEAD＝`0201ace` v4）、`git status --short`（116 行）、`git diff --stat`（67 files, +4448/−1287）、`git diff --stat b157966 HEAD`（＝plan＋R2 報告 2 檔、+265/−5）、`git diff b157966 HEAD -- …full-plan.md`（**逐行核 v3→v4 改動範圍**）、`md5sum` plan＝`3d8f223ea480…`。
- 讀 runtime artifact：`kubejs/server_scripts/utils/wares_model.js`（頭 20 行＋第 125-145 行，含 `:137-138` 黑市交易原文）。

**冇做（唔當事實）**
- **冇跑 gradle／build／harness／任何 `tests/check_*.py`**（禁令；亦因 `check_ask_display_leak.py`／`check_jar_contains_fix.py` 會寫檔）。
- 冇改任何 repo 檔（唯一寫入＝本報告）；冇 commit、冇部署、冇開遊戲。
- 「SK 當時投訴嘅原句」「`both` 想點講」「更大 pack 耗時」屬**未證實**（§5）。
- 引文一律用本輪工具輸出；凡「我推論」已標明（例如「修它會影響哪類題目」嘅第 4 點係由 pattern 分析推出嘅**推論**，唔係 runtime 實測）。

---

## 7. 反方對「正方可能反駁」嘅預判（先寫落，等正方打）

| 正方會講 | 反方回應 |
|---|---|
| 「trace 你引嘅都對，我哋一致」 | 一致嘅只係**引文**。你要答嘅係：同一份 trace 邊一句顯示「政策無視手上已有嘅 JEI 配方」？我逐 turn 印過 tool turn 同 final answer，見唔到。請指出行號（trace 檔行號 51 係 final）或補第二條 trace。 |
| 「P2 係改 `AcquireAskTool.toolMissNote` 一句字，唔算大改」 | 咁更應該 pin：請寫死「改邊個方法、改成咩字、負控係咩」。而且「一句字」嘅效果係**決定玩家會唔會知道索引有洞**——唔係排版問題。 |
| 「黑市交易係 KubeJS 腳本，唔屬 pack 內容感知範圍」 | plan §0 目標係「分得清 pack 自加內容」＋「答得出 pack 自加物品取得鏈」。`wares_model.js` 就係 pack 自加內容（且係 SK 旗艦 pack 嘅特色）。你若認為唔喺範圍，請明文列為已知未覆蓋＋附影響清單（R2 N4 同一要求）。 |
| 「`both` 我已經寫咗兩種答法」 | 「答『原廠有、本包另有覆蓋』」係玩家文字 ⇒ 要 lang key ⇒ 撞 §2 P4 未改嘅「零新 lang key」＋O1 兩難。請二選一並改 §2，或者改 `AskResult` 欄位＋指明 UI consumer（附 class＋行號）。 |
| 「N1–N8 都已經在 §10 逐條回應」 | 「逐條回應」≠「逐條落地」：本輪 **8 條：真解決 1、表面解決 7**；而 v3→v4 嘅 diff 只有 3 處（P2 一列／§8／＋§10），§1／§4／§5／§6 一個字都冇改。 |
| 「R2 講嘅 96 條你唔係都推翻咗？」 | 係，我**主動**更正自己人（96 成立）。同一標準：請你亦更正 §1 嘅 `222`（我 repro 唔到）同撤回 §1 已被你自己 §10 推翻嘅因果句。 |
