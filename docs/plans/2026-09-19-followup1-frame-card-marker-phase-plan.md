# Follow-up #1 phase plan — 框架卡冇內聯 `[card:N]` 標記（孤兒卡）

> 來源：fix A 真機驗收時發現（2026-09-19）；母 plan §2 P0 尾項
> 版本：**只適用 MC 1.19.2 + Forge 43.x**
> 狀態：計畫（未實作）

## 1. 症狀（真 trace 證據，非推論）
`docs/research/artifacts/2026-09-19-autotest-run-v2/ask-20260919-154158-tetra_modular_sword.jsonl`：
```
model.reply.final   「怎么来: 1. 工作台：… → 切石器…合成。 [card:1]」      ← LLM 有寫標記
check.scrub #1      【來源】前標記仍在（只剝 <!--packai:items=…-->）
check.scrub #2      「怎么来:合成台（無序合成）：切石器 + 木棍 -> modular sword。这是空白模组框架合成版本。」← 標記冇咗
render.cards.final  cardsOut=1
render.markers      recipe_card_markers=[]  ← 孤兒卡（有卡、內文冇引用）
```
對照：`minecraft:stone_axe` 同一機制**有** 5 個標記（`[card:1]…[card:5]`）⇒ 差異只喺「標準框架確定性改寫」路徑。

## 2. 根因（定位到行）
- `logic/HonestMiss.frameStandardRecipeLine(lang, stdRecipe)`（用 lang key `packai.reply.frame_standard_recipe{,_shaped}`）產生確定性行
- 由 `logic/AskEngine.java:978` 呼叫、經 `HonestMiss.insertLineBeforeSources(...)`（`HonestMiss.java:137-199` 一帶）改寫「怎么来」段
- 該行**只寫文字**，唔會帶 `[card:N]`；而原本 LLM 寫嘅段被換走 ⇒ 標記連帶消失
- `AskMarkerRepair.repair()` 只能**修補已存在**標記，唔會無中生有 ⇒ 冇 fallback

## 3. 修法（最小 diff；二選一，review 定）
- **A（建議）**：`frameStandardRecipeLine` 產出時附上已發出卡嘅 ref＝由呼叫處傳入 `emissionRefs`（`render.cards.final` 已有 `cardsOut`／`AskService.frameStandardDisplayRefs`）→ 行尾加 ` [card:N]`；N 用該卡 refId（唔准重新編號，見 `AskService.java:531` B9 註釋）
- **B**：`AskMarkerRepair` 加「插入」步驟——若 body 冇任何 `[card:N]` 但呢件物品有已發卡，就喺首個候選步尾補 ` [card:1]`
- A 較準（知道 ref 對應）；B 較泛（連其他路徑都救）。**建議 A ＋ B 嘅窄版（只喺 frame-standard 路徑）**

## 4. 檔案白名單
- `logic/HonestMiss.java`（frameStandardRecipeLine 簽名／加參數）
- `logic/AskEngine.java`（`:978` 呼叫處傳 refs）
- `client/service/AskService.java`（如需把 refs 傳入，`:2694-2712` 已有相關資料）
- **新增** `tests/check_frame_card_marker.py`（新閘）
- **唔准改**：`neoforge/**`、lang 字串（A 方案唔需要新字串；如需 → 另議）

## 5. 驗收標準
| # | 檢查 | 判準 |
|---|---|---|
| A1 | 真機（harness）`tetra:modular_double` ＋ `tetra:modular_sword` | `render.markers.recipe_card_markers` **非空**；標記 N 對得上 `cardsOut` 嘅卡 |
| A2 | 對照組 `minecraft:stone_axe` | 仍然 5 張卡 5 個標記（**唔准**退化） |
| A3 | 負控 | 冇卡時**唔准**加標記（`cardsOut=0` ⇒ markers 必須空） |
| A4 | 現有 122 閘＋新閘 | ≥121 綠（＋新閘）、0 新紅 |
| A5 | 49 Java 測試 | 全綠 |
| A6 | 文字重複檢查 | 內文唔准同時有兩條「怎么来」行（改寫唔可以有殘留） |

## 6. 風險／還原
- 標記號錯（refId 對唔上）→ 會出錯卡；驗收 A1 要**逐個 N 對卡面**核
- 特製版（非標準框架）路徑唔准受影響（母 plan 規定：特製版老實講未收錄）
- 還原：3 個 Java 檔（工作樹未 commit）→ 實作前 `%TEMP%\fu1_backup_<ts>\` timestamped copy ＋ md5

## 7. 流程
1. 本 plan → 反方 R1 review（≥8:2）
2. cursor 實作 → Hermes 親驗（compile／49／122＋新閘／負控）
3. 真機 harness（2 條 case＋1 對照，約 10-15 萬 tokens）
4. 通過 → 同 fix A／harness 一齊出
