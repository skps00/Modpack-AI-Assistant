# P2 phase plan — 反方 review（R1）｜正方 2 : 反方 8（未達 8:2，唔准開工）

> 受審檔：`docs/plans/2026-09-19-p2-policy-fix-phase-plan.md`（HEAD `eb72bb5`，md5 `7f7582a848be2049c90580d53c674cbd`）
> 母 plan：`docs/plans/2026-09-19-pack-content-awareness-full-plan.md`；上一輪：`docs/plans/reviews/2026-09-19_pack-content-awareness-plan-R3-opposing.md`
> 紀律：只讀唔改；本檔係唯一寫入。每項附 `檔案:行` ＋引文；未核實者明寫「未核實」。

## ① 驗過嘅事實（真／假）

| # | 聲稱 | 判定 | 證據（親跑） |
|---|---|---|---|
| F1 | plan:11「文字答『无法确定』（實錘：`ask-20260917-091302-...jsonl`）」 | **假** | 該檔 `display.body.final` 逐字＝「1. 锻造台：用钻石背包＋下界合金锭升级而成…[card:1]」「2. 本包索引没有收录它的掉落、宝箱、钓鱼、交易或任务等取得路径，所以除上面的锻造外**无法再断言**别的来源。」；全檔 6 次「无法确定」**全部喺 `send.system` 提示詞**（「不可装傻说无法确定」），答案本體 0 次 |
| F2 | plan:10「`send.facts.jei` 逐字含配方」 | **真** | rec23/35/48 `"0 | role=output | 锻造台 | 钻石背包, 下界合金锭 → 下界合金背包"` — 但佢證明「文字路徑早已正確」，同 plan:11 相反 |
| F3 | plan:15 `AskEngine.java:944`＝`obtainFill` 起點 | **真** | `944 String obtainFill = acquire.isEmpty()` |
| F4 | plan:16 `AskEngine.java:957`＝`hasLocalFact = (acquire…` | **真但誤導** | 真身 3 行：`:957` `boolean hasLocalFact = (acquire != null && !acquire.isEmpty())` `:958 || !jeiInfo.isEmpty()` `:959 || (questHits != null && !questHits.isEmpty());` — plan 用「…」截走咗 `jeiInfo`／`questHits` 兩項 |
| F5 | plan:14 `AcquireAskTool.java:29`＝`[TOOL_MISS] acquire empty …` | **真** | `sed -n '29p'` 逐字命中（該行係**餵模型嘅提示字串**，唔係覆蓋邏輯本體；覆蓋邏輯喺 `PackIndex.java:1217 acquireFactsDetailed`） |
| F6 | plan:21 用現成 `extractJeiPlayerLines` 「只留帶 `→` 嘅配方行」 | **假** | 我照抄 Java 規則寫 mirror（`AskMissFallback.java:64-110`＋`AskReplyScrub.java:1522-1536`）實跑：SB 行 `0 | role=output | 锻造台 | 钻石背包, 下界合金锭 → 下界合金背包` → `isPlayerSafeLine=False`（`AskReplyScrub.java:157` unsafe marker `"role="`，case-insensitive contains）→ **回 `[]`**；09-19 真機 A3 行 `0 | role=output | Crafting | 花岗岩×2, 木棍×2 → 花岗岩 锤` 同樣 `[]`；只有 Java test 自製行（`AskMissNoticeCheck.java:23` 無 `role=`）抽得到 |
| F7 | plan:17「JEI 配方唔被當成本地事實 ⇒ 政策答無法確定」 | **假** | `hasLocalFact` 唯一 consumer＝`AskEngine.java:960-961 if (hasLocalFact) { body = JeiInfoFacts.stripUnspecifiedMiss(body); }`（刪「未標明」行）——**唔控制 denial**；`obtainFill`(944) 經 `AskReplyScrub.java:985-995`，missLine **只喺 `!hasObtainCards` 才插**，而 SB 案例 catalog 有 `role=output` ⇒ `hasObtainRecipes`（`AskEngine.java:1211-1222`、regex `:1252`）＝true ⇒ 根本唔會插 miss 句 |
| F8 | plan:1 症狀「明明 JEI 有配方，文字卻答无法确定」 | **假（零 artifact）** | 掃 38 條 trace（`instances/packai_sandbox/…/trace`）：含 denial 字嘅 **7 條**，其 `send.facts.jei` 嘅 `^\d+\s*\|\s*role=output` 行數 **全部＝0**；反過來，有 `role=output` 行嘅 **6 條** trace，`display.body.final` **全部冇** denial ⇒ 無一條 trace 符合 plan 症狀 |
| F9 | plan:38 A3「122 閘 ≥121 綠」 | **真（但未指名）** | 親跑：`TOTAL=122 FAILCOUNT=1`；紅＝`tests/check_ask_display_leak.py` RC=2「NO LOG LINES (need real-machine smoke)」（非邏輯失敗，plan 冇寫明） |
| F10 | plan:39 A4「49 Java 測試全綠」 | **半真／未核實** | `find forge/1.19.2/src/test -name '*.java' \| wc -l` ＝ **49** ✓；**未跑 gradle** ⇒「全綠」未核實 |
| F11 | plan:46「復原＝`git checkout` 該批檔」 | **假（危險）** | `AskEngine.java`＝` M`（未 commit、146 insertions／12 deletions，內含 17 處 frame-standard＝fix A）；`AskMissFallback.java`＝`??` **untracked**（`git ls-files` 空）⇒ checkout 對 untracked **唔可行**、對 AskEngine 會**連 fix A 一齊消滅**（全樹 122 dirty／54 untracked） |
| F12 | plan:31 白名單＝3 Java 檔＋1 新閘 | **假（漏檔）** | `tests/check_summon_entity_recipes.py:65 assert "AskReplyScrub.playerSafeFacts(acquire)" in engine`（`SIDES`(:6-9) 含 forge **同** neoforge）⇒ 改 `obtainFill` 表達式即打紅一條**唔喺白名單**嘅閘 |
| F13 | 跨版本 | **真（分歧）** | `neoforge/1.21.1/…/AskEngine.java:873/886` 同政策邏輯；`AcquireAskTool.java:29` 兩樹同一句 ⇒ P2 只改 forge＝雙樹政策分歧（`neoforge/README_PAUSED.md` 允許，但要明文記錄） |
| F14 | plan:52「約 4 萬 tokens／條」 | **未核實** | 沙盒只有日總數 `<sandbox>/config/packai-usage.json`：`"2026-09-19":580731`；**冇 per-case 數**（VERDICT.md 自己都寫「未隔離」） |
| F15 | plan 有冇回應上一輪 R3？ | **無** | plan 全文零提及 `…-plan-R3-opposing.md`；而 R3:17 已用**同一個 trace** 打死同一根因：「模型**冇**答『無法確定配方』…trace 支持嘅係『文字路徑早已正確』」 |

## ② 問題清單（severity ＋ 證據）

- **O1 CRITICAL — 旗艦症狀冇 runtime artifact**（F1/F8/F15）：plan 唯一實錘（SB trace）答得**正確**；38 條 trace 內「有 `role=output` 行 ＋ 文字 denial」＝ **0 條**。上一輪 R3 已經用同一支 trace 判死同一根因，本 plan 冇吸收、冇撤回清單。
- **O2 CRITICAL — 根因靠截斷引文＋因果錯位**（F4/F7）：`:957` 真身已含 `|| !jeiInfo.isEmpty()`；而 `hasLocalFact` 只餵 `stripUnspecifiedMiss`，唔產生 denial。改佢＝**改一個唔喺 denial 路徑上嘅布林**。
- **O3 CRITICAL — 修法機制喺旗艦個案抽到 0 行＝no-op**（F6）：plan 自己引嘅行帶 `role=`，而 `extractJeiPlayerLines` 第一關就過濾 `role=`；要抽到就要「重排版成 plan:22 嘅示例格式」，而 plan 冇寫呢個 reformatter（亦唔喺白名單）。
- **O4 HIGH — A1 假綠（零鑑別力）**（F1＋09-19 真機）：`docs/research/artifacts/2026-09-19-autotest-run/` A4 已 PASS，body 逐字含「钻石背包 + 下界合金锭 -> 下界合金背包」、冇「无法确定」，`cardsOut=1` ⇒ **今日已綠**；且 A1 要嘅 token（`+`）同機制輸出（`,`＋`→`）唔一致。
- **O5 HIGH — 還原方案錯，會吞咗 fix A**（F11）：untracked＋`git checkout` 矛盾；要逐檔改「timestamped copy ＋ md5」。
- **O6 HIGH — 白名單漏既有閘**（F12）：A3「0 新紅」必然違反，或實作者被迫越界改白名單外檔。
- **O7 MED — 語言無關**（plan:23）：用 `looksLikeDenial`（`AskMissFallback.java:29-58`，zh/en 硬編字串清單）做政策閘 ⇒ 非 zh/en 語言（fr/de/ja…）**零覆蓋**，同 SK 2026-09-17 硬約束「**所有語言都可以用**」對唔上。
- **O8 MED — plan §3.1 同 §6 互相排斥**（F6）：§6 要求新行經 `isPlayerSafeLine`，但 §3.1 想用嘅行**永遠過唔到**該 predicate；另 `tests/check_ask_display_leak.py:54` 明文禁 display body 出 `"role="`。
- **O9 MED — 負控／guard 冇 pin**（plan:37/40）：A2 未指名「冇配方」物品；A5 若用真 catalog 行做「有配方行」fixture，實測會抽到空（F6）⇒ guard 反而證明機制失效。
- **O10 LOW — §3.4 來源標註**：`【來源】JEI（配方卡）` 現成已有（09-19 trace），若真係要改字則要動 `ReplySources.java`＋lang key，兩者都唔喺白名單（plan:31 明文「唔准改 lang」）⇒ 自相矛盾或 no-op。

## ③ 載重決定存活表 ＋ 比分 ＋ flip conditions

| LD | 載重決定 | 判定 |
|---|---|---|
| LD1 | 問題陳述／旗艦個案（SB 有配方卻 denial） | **死**（F1/F8/F15） |
| LD2 | 根因（acquire 空 ⇒ 政策答無法確定） | **死**（F4/F7） |
| LD3 | 修法機制（reuse `extractJeiPlayerLines`） | **死**（F6：flagship 抽 0 行） |
| LD4 | 驗收 A1 鑑別力 | **死**（F1＋09-19 真機已綠） |
| LD5 | 驗收 A2／A5（負控／guard） | **有保留**（O9：fixture 未 pin，且部分 fixture 必然空） |
| LD6 | 白名單／還原方案 | **死**（F11/F12） |
| LD7 | 唔碰 neoforge（符合 PAUSED） | **站得住**（F13，代價＝雙樹分歧要記錄） |

**比分：正方 2 : 反方 8**（LD7 站得住 ＋ F3/F9 行號同 baseline 數字對；其餘 5 條載重決定死、1 條有保留）⇒ 遠低於 8:2，**唔准開工**。

**Flip conditions（要咩證據才能反轉）**
- FC1：交出**至少一條真 trace**：`send.facts.jei` 有 `^\d+\s*\|\s*role=output` 行 **而** `display.body.final` 含「无法确定／查不到／没有配方」——今日 38 條＝0 條。（或撤回 plan:11 引文，改寫症狀。）
- FC2：指名 P2 實際讀**邊個變數**（`loop.jeiText()`／`recipeGetClean`／`jeiSummary`／`send.facts.jei` 等價物），並展示一個**真 production 格式** dump 經 `extractJeiPlayerLines` 抽到 ≥1 行（今日所有 catalog 行含 `role=`；`jei_lookup` 格式行只喺 4/38 trace 出現，且嗰啲 trace 全部無 denial）。
- FC3：交出「**邊一行**令 denial 唔再出現」嘅機械鏈（今日 `:957`／`:944` 兩條都唔在 denial 路徑上）；若係條件性，寫明條件同 fallback。
- FC4：A1 要證明「fix 前係紅」（附 fix 前 trace），否則要換一條今日真紅嘅 case 做驗收。
- FC5：還原方案逐檔核 tracking（`AskEngine.java`＝` M` 唔可以用 checkout；`AskMissFallback.java`＝`??` 要 copy＋md5），白名單加 `tests/check_summon_entity_recipes.py` 或明文保留該字面。
- FC6：寫死非 zh/en 語言點處理（或明示只覆蓋 zh/en 並記錄同 SK 硬約束嘅衝突）。
- ⛔ 唔准「換 prompt 再擲骰」當新一輪；每輪必須有實質修改（新 trace／改設計／縮範圍）。

## ④ 最貴嘅未知

1. **SK 到底喺邊次、邊件物品、邊個 instance 見到「无法确定」？** 現有 38 條 trace 冇一條符合 plan 症狀（F8），而 09-19 真機 A4 已 PASS（F1）。呢個係唯一未解嘅 empirical gap；解開方法＝SK 提供佢實際見到嗰次嘅物品／時間（或截圖），或者跑一批**新** case（重點揀「索引唔完整但 JEI 有配方」嘅形狀，例如交易／黑市／任務獎勵類），成本比再開一輪 review 低。
2. **要修嘅係「模型 prose 唔准講無法確定」定「code 唔准插 miss 句」？** 兩者要唔同閘、唔同驗收：前者係 prompt／輸出過濾（要 ≥29 樣本才驗得到、且語言相關），後者係 `AskReplyScrub.java:985-995` 條件式（今日 SB 案例根本唔觸發）。plan 混為一談。
3. **plan:52 真機驗收成本未核實**（F14）：per-case token 數冇 artifact，驗收預算無法核。
