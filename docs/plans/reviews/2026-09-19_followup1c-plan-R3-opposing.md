# Follow-up #1c（**卡走位量度儀器**）— 反方 review **R3**（v3 ＝ 離線語料儀器）

> 審：`docs/plans/2026-09-19-followup1c-card-placement-instrument-plan.md`
> **md5 = 8651c9fb75c260dea948fc53ab8f8db3**（58 行；本報告只評此版本）
> 逐輪：v1 **正方 3 : 反方 7**（R1）→ v2 **4 : 6**（R2）→ v3 本輪。
> 只讀唔改（唯一寫入＝本報告）。行號一律指 `forge/1.19.2/src/main/java/com/skps9/packai/`。所有命令親跑。
> ⚠️ AGENTS.md〈Plan Review 上限 3–4 輪〉：本輪＝第 3 輪仍未達 8:2 ⇒ 文末附**停手報告**（四要素）。

## ① 驗過嘅事實（真／假／未核實）

**真（親跑／親讀）**

1. **RecipeCard 硬約束對**：`:20 public record RecipeCard(`；`:63 public RecipeCard {`；`:275 public static RecipeCard crafting3x3(... ItemStack output)`；`:301 public static RecipeCard flow(`；`src/test/.../AskCardPlacementCheck.java:9`「Does not construct RecipeCard (needs Minecraft registry)」⇒ headless 建唔到卡，計劃書三個行號**全對**。
2. **UI 消費點 `:839` 對**：`AiAssistantScreen:839 parts = new ArrayList<>(RecipeEmbed.interleaveEmissionCards(cleaned, cards));`；`:835 if (cardStrip)` / `:841` 非-strip 走 `RecipeEmbed.parts`。`:839` 之後 `parts` 確在作用域。
3. **config 接點對**：`PackAiConfig:466 .define("jsObtainDiagLog", false)`、`:962 public static boolean jsObtainDiagLog()` ⇒ v3 §2.2 引「`:466/962`」正確。`cardPlacementDiagLog` 全 repo **0 個 hit**（未存在，符合預期）。
4. **A5 baseline 數字對**：`tests/check_*.py` ＝ **122 檔 → PASS=121 / FAIL=1**（唯一紅 `check_ask_display_leak.py`）；`find forge/1.19.2/src/test -name '*.java' | wc -l` ＝ **49**。
5. **語料檔數對、field 齊**：`docs/research/artifacts/*/*.jsonl` ＝ **7 檔**（`2026-09-19-autotest-run` 3 ＋ `-v2` 4），**7/7** 同時有 `display.body.final` ＋ `render.cards.final` ＋ `[card:N]`。`docs/research/artifacts/2026-09-19-cardplace-corpus/` **未存在**（新交付物，符合預期）。
6. **`srcStart` 公式成立**：`RecipeEmbed:753-759` 剝走 `sourceParts`、`:769 blocks.addAll(sourceParts)`（最後一次 mutation）⇒ `srcStart = len(blocks) - len(sourceParts)` 確為首個【来源】part 之 index。v3 呢條**算對**。
7. **跳卡函數真身對得上**：`:1112-1118 skipCardsAfter`、`:1121-1136 emissionSectionOf`（`isUpgrade→2`／`isInputUse→1`／`isMaintenance→-1`／其餘 `→0`）、`:1010-1042 sectionLastAfter` ⇒ v3 §2.1「分支行號逐一對應」**可讀為真**。
8. **5 條 disperse 分支存在**（`:907-909 absEnd`／`:919-925 secTail`／`:954-957 needle`／`:960-967 secTail`／`:969-971 clamp`）⇒ v3 `cause` 四標籤（`byRef-multi`／`secTail-dump`／`maint-absEnd`／`needle-miss`）**對映得到**（R2 Fc2 收貨）。
9. **真 trace 語料遠比 7 檔多**：真 instance `…/AI_test_NFWC_DIM/minecraft/packai/trace/` ＝ **39 檔**（38 條 `ask-*.jsonl` ＋ `index.jsonl`），**38/38 有 `display.body.final`**；`cardsOut ≥2` **9 條**（4/4/3/6/2/4/2/**8**/4），`[card:N]` ≥1 **10 條**。**全部唔在 repo 內**（未被 commit）。R2 自己引嘅 `ask-20260915-202603-golden_age_infinity_sword_organ`（4 卡、run=3）／`ask-20260916-133612`（3 卡）**都喺呢 38 條內**。
10. **v3 語料（7 檔）嘅卡數分佈**：`render.cards.final.cardsOut` ＝ **1／1／5／1／1／1／2** ⇒ **5／7 只有 1 張卡**（單卡結構上做唔出「連續 ≥2 卡」）；`[card:N]` token 數 ＝ 1／0／2／1／0／1／1。7 檔對應 **6 個不同物品**（`tetra:modular_double` 出現 2 次＝同一 case fix 前／後）。
11. **trace 內其實有「逐卡」資料，但 plan 冇抽**：每條 7 檔都有 `check.cards` event，內含逐卡 `category`／`primaryOutputId`／`sourceItemId`／`reason:"role=OUTPUT|INPUT"`／`placement:"catalog_collect|tool_emit"`；另有 `render.markers` 內 `"emissionRefs":[1,2]`（真 tool emission whitelist）＋`"recipe_card_markers":[]`。例：`ask-20260919-154224-minecraft_stone_axe` `check.cards` 有 2 個 `role=OUTPUT` ＋ **3 個 `role=INPUT`**；`cardsOut=5`。
12. **`render.cards.final` 嘅 `role` 係「第一張卡」一個值，唔係逐卡**：`AskService:513-530` 只取 `firstCard`，`o.addProperty("role", firstCard.promptRole())`；`:506/507/522 cardsOut = shown.recipeCards().size()`。⇒ plan §2.1 寫「抽 `render.cards.final` 卡數／role」＝**天然只拿到 1 個 role**，套落 stone_axe 會把 5 張卡全當 OUTPUT（實情 3 張 INPUT）。
13. **`emissionMatchNeedles` 需要 ItemStack hover name ⇒ 語料結構上重現唔到**：`RecipeEmbed:1143-1204`——`:1170 card.catalysts()` ⇒ `s.getHoverName()`（`:1175`）、`:1184 card.outputs()` ⇒ `getHoverName()`（`:1189`）、另加 `card.mentionKeys()`。trace **任何 event 都冇** hover name／catalysts 清單（只有 `primaryOutputId` ＝ 英文 id）。
14. **`cardsInTail` 定義喺錯誤一邊（幽靈第三次）**：兩條生產路徑嘅卡都**只可插喺**【来源】**之前**——`:707-710 indexBeforeSources`、`:1280-1304 insertObtainClusterAt`（`:1286 int limit = srcAt >= 0 ? srcAt : parts.size()`）、`:769 addAll(sourceParts)` 最後。⇒ 以 `srcStart` 為界嘅 `cardsInTail` **結構上恆 0**。R2 Fc1 要嘅係「卡貼住【来源】**頂**」＝`blocks.get(srcStart-1).isCard()`，v3 量咗相反一邊。
15. **UI log 落點 `:839`「之後」≠ 最終畫面**：`:846 strip = toolPartsStrip(tool)`；`:857-869 if (strip != null) { … :861 RecipeEmbed.splitTrailingSources(parts) … :867 int at = cardStrip ? indexBeforeSources(parts) : insertObtainClusterAt(parts); :868 parts.add(at, Part.card(stripIdx)); }` ⇒ **`:839` 之後 parts 仍會被改**（插一張 tool-parts 卡）。真機 log 實證呢條路徑**唔罕**：`packai_sandbox/minecraft/logs/*.gz` grep `Pack AI toolParts path=` ＝ **14 行、全部 `strip stripDrawn=true`**（09-18）。
16. **v3 §0 講 `:867-868` 係「`cardStrip` 路徑」＝錯**：`:867` 用 `cardStrip` 只係揀 index 函數；`:857 if (strip != null)` 係工具零件路徑，**cardStrip true／false 都會行**。呢個錯 label 掩蓋咗事實 15。
17. **`AiAssistantScreen` 本身已有 ungated INFO**：`:849-856 PackAiMod.LOGGER.info("Pack AI toolParts path=…")`。⇒ 「1 行 gated log」可行（LOGGER 已在用），但「`parts` 最終序列」唔可以只 log 一次就算。

**假（親證）**

- **假**：§0「現有 mirror `tools/card_placement_test.py`（repo 已有）→ 擴充成語料掃描器，唔另起一套」。真身：該檔 `:42 import check_ask_card_fallback as m`、`:150 out = m.ensure_cards(reply, cards)`，mirror 嘅係 **`ensureCards`（KEYWORDS／ALWAYS／offline 路徑）**；檔頭 `:7-10` 明寫「AI + llmExpected mode **no longer runs ensureCards** — cards come from render_recipe_cards tool emissions (AskResult.cardStrip). … **Do not expect AI-path strip behavior here**」。而 `grep -rn "splitTextIntoStepBlocks\|placeEmissionCardsByRef\|disperseUnplacedEmissionCards\|interleaveEmissionCards" tools/ tests/` ⇒ 只有 `tests/check_*.py` 嘅**源碼字串斷言**，**0 個 Python mirror**。⇒ v3 §2.1 唔係「擴充」，係 **由零重寫**（`parts()`／`splitTrailingSources`／`splitTextIntoStepBlocks`／`placeEmissionCardsByRef`／`disperseUnplacedEmissionCards`／`stripCardRefTokens` 全部無既有 Python 版可對）。
- **假**：§2.2「log 出 `parts` 最終序列 ⇒ 畫面真正睇到嘅次序」。`:839` 之後仍有 `:861`（會 split 一個 TEXT part）同 `:868`（插卡）⇒ 唔係最終（事實 15/16）。
- **假**：§2.1「抽 `display.body.final` 原文、`render.cards.final` 卡數／role、`[card:N]` 出現集合」足以餵 mirror ⇒ 缺逐卡 role／category／emissionRefs（事實 11/12），且 needles 結構上缺失（事實 13）。

**未核實**

- 7 條語料 run 期間有無行 `strip != null` 分支：09-19 sandbox `.gz` grep `Pack AI toolParts path=` ＝ **0 行**（09-18 有 14 行）⇒ **傾向「呢 7 條冇中**」，但 `latest.log` 已被 rotate、唔敢寫死。
- `cardsInTail`／`maxAdjacentCards` 嘅 plan 精確定義（§2.1 只有一句）——係「srcStart 之後」定「貼住 srcStart 之前」，**字面讀係前者（幽靈）**。
- mirror 要唔要一齊 mirror `parts(String,List)`（`:127` 起，含 `{{item:id}}`→ITEM part）同 `RecipeCardsMode.scrubMarker`（`:185-192` 會 collapse `[ \t]+\n`／`\n{3,}`）：v3 §2.1 **冇提**，但兩者都改變 `blocks` 內容／index（`sectionLastAfter:1021-1025` 對非-TEXT part 有感）。

## ② 問題（severity）

- **O-R3-1 CRITICAL — mirror 嘅「鑑別分支」結構上重現唔到 ⇒ base rate 有方向性偏誤，唔止係雜訊。** `emissionMatchNeedles` 靠 ItemStack hover name（事實 13）／`mentionKeys`。語料只有英文 id，中文 step 文（「石斧」）永遠 match 唔到中文 hover name ⇒ mirror 系統性跑 `:958-960 needle-miss → sectionLastAfter` ⇒ **mirror 會偏向製造「同 section 尾堆埋」**。即 A3 個 base rate 會**高估症狀**，令「亮燈」更加冇意義。（R2 O-R2-2 未解，且升級。）
- **O-R3-2 CRITICAL — plan 抽錯 field：唯一有逐卡資料嘅 event 冇抽。** `check.cards`（逐卡 `category`／`role`／`primaryOutputId`／`placement`）＋`render.markers.emissionRefs`（事實 11）**完全冇入 §2.1 清單**；而抽錯嘅 `render.cards.final.role` 只有第一張卡一個值（事實 12）⇒ mirror 連 `emissionSectionOf(card)`（決定 GET/USE/UPGRADE/maint 四條唔同插入路徑）都建唔正。n=1 張卡時兩者巧合一致，故單卡 case 睇唔出問題、多卡 case 全錯。
- **O-R3-3 CRITICAL — 語料量同選擇都唔夠，仲丟棄咗反例。** v3 用 7 檔（5 檔單卡、2 檔同一 case 前後、全部同一日同一沙盒）；repo 外有 **38 條**真 trace、**9 條 `cardsOut≥2`**（最大 8 卡）（事實 9/10）。R2 用嚟證「base rate ≠ 0」嘅兩條 trace **正好被排除**⇒ v3 連自己上一輪嘅反例都跑唔到。n=7（其中只有 2 條「有資格」亮燈）嘅 base rate 無統計意義，只可寫「7 例觀察」。
- **O-R3-4 HIGH — 真值 log 落點寫得唔夠死，且描述錯。** §2.2 只寫「`:839` 之後」；正確落點＝`:869` 之後、`:870 if (parts.isEmpty())` 之前（事實 15）。§0 把 `:867-868` 錯標為「cardStrip 路徑」（事實 16）。兩者一齊令「最終畫面」宣稱**唔成立**；若照字面插喺 `:839` 正下方，Log 出嘅序列**唔係畫面**。
- **O-R3-5 HIGH — `cardsInTail` 幽靈化（第三次換位）。** 量咗 srcStart 嘅另一邊 ⇒ 恆 0（事實 14）。R2 Fc1 要嘅 `tailIsCard = blocks.get(srcStart-1).isCard()` 冇採納。
- **O-R3-6 HIGH — mirror 忠實度零驗證步。** plan 由頭到尾**冇一步**證明 Python mirror ＝ Java 行為：無 golden case、無對 `AskCardPlacementCheck` 交叉核、無同一 body 對推。呢個係 R2 O-R2-5 同族復發（上次係「閘只驗字串」，今次係「mirror 只自證」）。**合成負控（A3①）只證 mirror 自洽**——mirror 一錯，負控一樣「亮燈」，係自證循環。
- **O-R3-7 MED-HIGH — base rate 用途仍未定義「正常 vs 症狀」。** 就算算出數字：① 38 條真 trace **冇一條有 SK 親眼 label**；② byRef 多卡成 run 係 `:819-823` **設計**（R2 事實 8）；③ mirror 有方向性偏誤（O-R3-1）⇒ 三者夾埋，數字**唔可以**區分正常／症狀。v3 加 `cause` 標籤係對方向，但標籤本身靠唔可信嘅 needle 分數 ⇒ **標籤都驗唔到**。關鍵漏觀測：**唯一可以用嚟 label 一條 case 嘅係真值層 log**，而 plan 把它排到最後（§5 步 5）。
- **O-R3-8 MED — 白名單／還原／命令。** ① §6「備份 2 個 Java 檔（md5）」但 §3 白名單寫咗 **1 個 Java 檔＋1 個 config 檔＝2 個檔**，一致，但**冇寫死 back 位**；② 沙盒部署命令（`--jar … --mods …/packai_sandbox/minecraft/mods`）＋禁 `--target packai` 由 R1/R2 追到今輪**仍然冇寫**；③ `AiAssistantScreen`「+1 gated log 行」實際要包 `cardStrip`／`strip != null`／逐 part index ⇒ 唔止 1 行；④ A5 寫「122 閘」但新增 `tests/check_cardplace_corpus.py` 後應係 123。
- **O-R3-9 LOW —** §2.1 步驟表漏咗 `:751 parts(raw, List.of())` 同 `:752 splitTrailingSources`（講「忠實重寫序列」但序列頭兩步冇列）。

**正方收貨點**：① 判準由 index／`maxSameBlock` 改 **最終 run**、刪 mode 枚舉、`srcStart` 公式正確、`cause` 四標籤對映到真身 5 條分支（事實 6/7/8）＝ R2 Fc1/Fc2 方向實質修正；② 承認離線層只係「假設生成器」、真值另設 **UI 層 1 行 gated log**＝首次有真真值來源（R1 U1／R2 O-R2-4 方向正確）；③ 變異判準（R2 O-R2-3）由設計上移除（無 LLM）＝真解；④ 明文「唔准 deploy 真 instance／開 game」＋「唔碰 `RecipeEmbed.java`」＋ log-only 默認 false ⇒ 還原成本極低；⑤ A5 baseline 我親跑對得上（121/1、49、122）；⑥ `RecipeCard` 硬約束三行號全對（事實 1）。

## ③ 比分 ＋ go/no-go ＋ flip conditions

**正方 4 ： 反方 6**（LD 存活表：LD1 判準單位／`srcStart` → **半**〔run 方向對，`cardsInTail` 量錯邊〕／LD2 真值來源（UI log）→ **半**〔方向對，落點寫錯＋本 plan 唔驗〕／LD3 mirror 忠實度 → **死**〔前提假＋needles 結構缺失＋零驗證步＋偏誤方向性〕／LD4 語料充足性 → **死**〔n=7、5 單卡、漏 `check.cards`、棄 38 條〕／LD5 零行為＋還原 → **存**／LD6 現有閘唔新紅 → **存**〔親跑 121/1〕／LD7 驗收鑑別力 A3 → **死**〔合成負控＝自證；base rate 偏誤＋無 label〕／LD8 白名單／部署邊界 → **半**〔禁真 instance 好，命令／table／行數未寫死〕）⇒ **未達 8:2 ⇒ 唔建議照 v3 開工（no-go）**。

Flip conditions（全部可即場寫死；做完第 1–4 條才值得再評）

- **Fc1（O-R3-2＋O-R3-1）**：語料抽取清單改為 **`check.cards`（逐卡 category／role／primaryOutputId／placement）＋`render.markers.emissionRefs`＋`display.body.final`＋`render.cards.final`**；並明文承認「**needles（hover name）結構上不在 trace 內**」——7 條 case 一律**人手標 needles**（每條 ≤ 5 分鐘），或明文寫「mirror 對 needle 分支採 X 假設，故 base rate 只可作**上界**」。
- **Fc2（O-R3-3）**：語料擴大至**全部 38 條真 instance trace**（唯讀、離線、零成本；`2026-09-19` 前後一樣得），並把 `cardsOut≥2` 嘅 9 條列成必跑子集；A1 由「7 個 case」改寫成「**N 條（含 ≥9 條多卡）**」。若堅持只用 commit 內 7 檔，就要明文寫「n=7 且 5 條單卡 ⇒ 只作 7 例觀察，**唔聲稱 base rate**」。
- **Fc3（O-R3-6）**：加**忠實度關卡**（三揀一，寫死命令）：(a) 同一 `display.body.final` 手推 2 條 case 對 mirror（照 R2 做法）；(b) 加 Java `interleaveEmissionCards(String, int)`-形狀 overload 做 golden（純字串、免 MC registry，形狀照 `RecipeEmbed:132 parts(String,int)`）；(c) **反轉次序**——先落地 Fc4 真值 log、跑 1 次沙盒、用真 log 對 mirror。**(c) 成本最低且同時令本 plan 有真產出。**
- **Fc4（O-R3-4）**：log 落點寫死為 **`:869`（`parts.add(at, Part.card(stripIdx));`）之後、`:870` 之前**；一行內容寫死包含 `cardStrip=`／`stripDrawn=`／`path=`／逐 part `T/C/S`＋card index；並刪走 §0「`:867-868` 係 cardStrip 路徑」呢句錯描述。
- **Fc5（O-R3-5）**：刪 `cardsInTail`（以 srcStart 為界），改 `tailIsCard = blocks.get(srcStart-1).isCard()` ＋ `adjCards`（同一 anchor 連續卡數，由最終 `blocks` 數）；明文「srcStart 之後恆 0，只作 sanity」。
- **Fc6（O-R3-7）**：明文寫「本 plan **唔會**區分正常／症狀；只出 (i) 每 case 序列 (ii) 逐 case 成因標籤 (iii) **7＋N 例觀察**」，並要求真值層 log 落地後，**用 log 對 mirror 嘅同 body** 做一次逐 case diff（有 diff 就係 mirror 唔忠實，唔係症狀）。
- **Fc7（O-R3-8）**：寫死備份路徑／md5 記錄法；寫死沙盒命令 `--jar <built.jar> --mods …/instances/packai_sandbox/minecraft/mods` ＋明文禁 `--target packai`；A5 數字改「新增後 123 閘、0 新紅（baseline 121/1）」。
- **Fc8（O-R3-9）**：§2.1 序列補上 `parts(raw, List.of())`（`:751`）＋`splitTrailingSources`（`:752`）＋ 明文 `RecipeCardsMode.scrubMarker`（`:185-192`）都屬 mirror 範圍。

## ④ 最貴未知

- **U1（最貴）＝ need 唔 need 真值層就係答案？** 事實 13 令我認為**離線 mirror 永遠答唔到**「卡有無堆埋」（needle 分支結構上唔可重建、且偏誤方向係「製造症狀」）。若成立，整條「離線語料」路線嘅價值＝**只有 base rate 對照**，唔係量度。最便宜解法：先做 Fc4（1 行 log @`:869`）＋1 次沙盒 run（SK 唔打機時），**用真 log 反推 mirror 要唔要續做**。次序掉轉，成本≈0。
- **U2**：7 條（或 38 條）真 trace 內，`strip != null` 工具零件路徑佔幾多（事實 15 傾向 09-19 ＝ 0，但 log 已 rotate）——決定 `:868` 對量度嘅污染率，**未核實**。
- **U3**：`emissionSectionOf` 四值喺真語料嘅分佈（`check.carts` 係有 `role=OUTPUT|INPUT`，但 upgrade／maint 未見）——決定 mirror 可唔可以至少建正 section。**未核實**。
- **U4**：`maxCardRun≥2` 喺 38 條真語料嘅自然出現率（R2 只量到 byRef 部分 1/20；disperse 路徑未全量摸清）——若高，儀器訊噪比低。**未核實**。

## ⑤ 停手報告（AGENTS.md〈上限 3–4 輪〉：第 3 輪仍未達 8:2）

- **逐輪比分**：v1 **3:7** → v2 **4:6** → v3 **4:6**（本輪 3 個新 CRITICAL：mirror 鑑別分支不可重建、語料抽錯 field、語料量／選擇不足）。
- **卡死嘅載重決定**：① **LD3 mirror 忠實度**（needles 需 ItemStack hover name，trace 冇）——唔改語料或唔加 golden 關卡就永遠卡住；② **LD4 語料**（棄 38 條真 trace 用 7 條、5 條單卡）；③ **LD7 A3 鑑別力**（合成負控＝自證）。
- **最貴未知**：U1（離線 mirror 到底可唔可以答原問題）。
- **建議（需 SK 揀）**：**(A) 掉轉次序、只做真值**：只實作 Fc4 嗰 1 行 gated log（`:869` 後），沙盒跑 1 次，用真 log 睇有無堆埋——**成本最低、直接答原問題**，離線鏡頭暫時唔做。**(B) 離線＋真值並行**：照 Fc1–Fc3 修好語料／抽 `check.cards`／擴到 38 條／加 golden 關，再一次過落地。**(C) 放棄本線**：卡走位改由真機 autotest（`AutoTestHarness MAX_CASES=20`）直接量，唔建離線層。
- 我嘅傾向：**(A)**——v3 唯一「真值」成份就係嗰行 log，其餘離線層目前係一個會系統性製造症狀嘅假生成器。
